package de.handyzeitvertreib.app.usage

import de.handyzeitvertreib.app.core.model.AppUsage
import de.handyzeitvertreib.app.core.model.InstalledApp
import de.handyzeitvertreib.app.core.time.AppClock
import de.handyzeitvertreib.app.core.time.DayBoundaries
import de.handyzeitvertreib.app.data.db.DayRecordEntity
import de.handyzeitvertreib.app.data.db.KnownAppDao
import de.handyzeitvertreib.app.data.db.KnownAppEntity
import de.handyzeitvertreib.app.data.db.UsageDao
import de.handyzeitvertreib.app.data.db.UsageSnapshotEntity
import de.handyzeitvertreib.app.data.prefs.PreferencesRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDate

sealed interface TodayUsageState {
    data object Loading : TodayUsageState

    /** Usage access is not granted: the app knows nothing, which is not the same as zero. */
    data object AccessRequired : TodayUsageState

    data object Error : TodayUsageState

    data class Ready(
        val date: LocalDate,
        val apps: List<AppUsage>,
        val totalMs: Long,
        /** Total of yesterday up to the same elapsed time; null when that day is not covered. */
        val yesterdaySameTimeMs: Long?,
        val refreshedAt: Long,
    ) : TodayUsageState {
        val usageByPackage: Map<String, Long> get() = apps.associate { it.packageName to it.foregroundMs }
    }
}

/** Usage of one day as stored locally. [captured] false means no data, not zero usage. */
data class DayUsage(
    val date: LocalDate,
    val captured: Boolean,
    val apps: List<AppUsage>,
) {
    fun totalMs(excluded: Set<String>): Long = UsageAggregator.totalMs(apps, excluded)
}

class UsageRepository(
    private val source: UsageEventSource,
    private val appsSource: InstalledAppsSource,
    private val usageDao: UsageDao,
    private val knownAppDao: KnownAppDao,
    private val preferences: PreferencesRepository,
    private val clock: AppClock,
    private val ioDispatcher: CoroutineDispatcher,
) {
    private val state = MutableStateFlow<TodayUsageState>(TodayUsageState.Loading)
    val today: StateFlow<TodayUsageState> = state.asStateFlow()

    private val mutex = Mutex()
    private var lastRefreshMs = 0L
    private var lastFullBackfillMs = 0L
    private var cachedApps: List<InstalledApp>? = null
    private var cachedAppsAt = 0L

    /**
     * Reads events from the platform, updates today's state and stores daily snapshots.
     * Previous days are written once they are fully covered by the platform's event history.
     *
     * @param minIntervalMs skips the platform query if the last refresh is more recent
     */
    suspend fun refresh(
        backfillDays: Int = BACKFILL_DAYS,
        minIntervalMs: Long = 0,
    ): TodayUsageState =
        withContext(ioDispatcher) {
            mutex.withLock {
                val now = clock.nowMs()
                val current = state.value
                if (current is TodayUsageState.Ready && now - lastRefreshMs in 0 until minIntervalMs) return@withLock current
                val result =
                    try {
                        if (!source.hasAccess()) {
                            TodayUsageState.AccessRequired
                        } else {
                            // A full 7-day backfill is only needed occasionally; yesterday is always re-read.
                            val recentlyBackfilled = now - lastFullBackfillMs in 0 until FULL_BACKFILL_INTERVAL_MS
                            val days = if (backfillDays > 1 && recentlyBackfilled) 1 else backfillDays
                            computeAndStore(now, days).also { if (days == BACKFILL_DAYS) lastFullBackfillMs = now }
                        }
                    } catch (_: SecurityException) {
                        TodayUsageState.AccessRequired
                    } catch (_: RuntimeException) {
                        TodayUsageState.Error
                    }
                if (result is TodayUsageState.Ready) {
                    lastRefreshMs = now
                    preferences.setLastRefreshAt(now)
                }
                state.value = result
                result
            }
        }

    private suspend fun computeAndStore(
        now: Long,
        backfillDays: Int,
    ): TodayUsageState.Ready {
        val zone = clock.zone()
        val today = DayBoundaries.dayOf(now, zone)
        val firstDay = today.minusDays(backfillDays.toLong())
        val queryStart = DayBoundaries.startOfDay(firstDay, zone) - LOOKBACK_MS
        val events = source.queryEvents(queryStart, now)
        val sessions = UsageAggregator.toSessions(events, now)
        val earliestEvent = events.minOfOrNull { it.timestampMs }
        val existing = usageDao.dayRecords(firstDay.toEpochDay(), today.toEpochDay()).associateBy { it.dayEpoch }

        var day = firstDay
        while (!day.isAfter(today)) {
            val window = DayBoundaries.windowFor(day, zone)
            val isToday = day == today
            val covered = earliestEvent != null && earliestEvent <= window.startMs
            val alreadyComplete = existing[day.toEpochDay()]?.isComplete == true
            if (isToday || (covered && !alreadyComplete)) {
                val usage = UsageAggregator.summarize(sessions, window)
                usageDao.replaceDay(
                    DayRecordEntity(day.toEpochDay(), isComplete = !isToday, capturedAt = now, zoneId = zone.id),
                    usage.values.map {
                        UsageSnapshotEntity(
                            day.toEpochDay(),
                            it.packageName,
                            it.foregroundMs,
                            it.launchCount,
                            it.lastUsedAt,
                        )
                    },
                )
            }
            day = day.plusDays(1)
        }

        rememberLabels(now)
        val excluded = excludedPackages()
        val todayUsage = UsageAggregator.summarize(sessions, DayBoundaries.elapsedWindow(today, zone, now))
        val yesterdayWindow = DayBoundaries.sameElapsedWindowYesterday(today, zone, now)
        val yesterdayCovered = earliestEvent != null && earliestEvent <= yesterdayWindow.startMs
        return TodayUsageState.Ready(
            date = today,
            apps = todayUsage.values.sortedByDescending { it.foregroundMs },
            totalMs = UsageAggregator.totalMs(todayUsage.values, excluded),
            yesterdaySameTimeMs =
                if (yesterdayCovered) {
                    UsageAggregator.totalMs(UsageAggregator.summarize(sessions, yesterdayWindow).values, excluded)
                } else {
                    null
                },
            refreshedAt = now,
        )
    }

    /** Launchable apps, cached briefly because the package manager query is not free. */
    suspend fun installedApps(forceReload: Boolean = false): List<InstalledApp> =
        withContext(ioDispatcher) {
            val now = clock.nowMs()
            val cached = cachedApps
            if (!forceReload && cached != null && now - cachedAppsAt < APP_CACHE_MS) return@withContext cached
            val apps = appsSource.launchableApps()
            cachedApps = apps
            cachedAppsAt = now
            apps
        }

    /** Home launchers and user-excluded apps; not counted in screen-time totals. */
    suspend fun excludedPackages(): Set<String> = withContext(ioDispatcher) { preferences.current().excludedFromTotals + appsSource.homePackages() }

    private suspend fun rememberLabels(now: Long) {
        val apps = installedApps()
        knownAppDao.upsert(apps.map { KnownAppEntity(it.packageName, it.label, now) })
    }

    /** Last known labels, including uninstalled apps. */
    fun observeKnownLabels(): Flow<Map<String, String>> =
        knownAppDao.observeAll().map { list ->
            list.associate { it.packageName to it.label }
        }

    fun observeDays(
        from: LocalDate,
        to: LocalDate,
    ): Flow<List<DayUsage>> =
        combine(
            usageDao.observeDayRecords(from.toEpochDay(), to.toEpochDay()),
            usageDao.observeSnapshots(from.toEpochDay(), to.toEpochDay()),
        ) { records, snapshots ->
            val recordDays = records.map { it.dayEpoch }.toSet()
            val byDay = snapshots.groupBy { it.dayEpoch }
            generateSequence(from) { it.plusDays(1) }
                .takeWhile { !it.isAfter(to) }
                .map { date ->
                    DayUsage(
                        date = date,
                        captured = date.toEpochDay() in recordDays,
                        apps =
                            byDay[date.toEpochDay()].orEmpty().map {
                                AppUsage(it.packageName, it.foregroundMs, it.launchCount, it.lastUsedAt)
                            },
                    )
                }.toList()
        }

    suspend fun applyRetention(retentionDays: Int) =
        withContext(ioDispatcher) {
            val cutoff = clock.today().minusDays(retentionDays.toLong()).toEpochDay()
            usageDao.deleteSnapshotsBefore(cutoff)
            usageDao.deleteDayRecordsBefore(cutoff)
        }

    /** Clears in-memory state after local data was deleted. */
    fun resetInMemoryState() {
        state.value = TodayUsageState.Loading
        lastRefreshMs = 0
        lastFullBackfillMs = 0
    }

    companion object {
        const val BACKFILL_DAYS = 7

        /** Sessions that started up to this long before the queried range are still found. */
        const val LOOKBACK_MS = 6 * 60 * 60 * 1000L
        private const val APP_CACHE_MS = 5 * 60 * 1000L
        private const val FULL_BACKFILL_INTERVAL_MS = 60 * 60 * 1000L
    }
}
