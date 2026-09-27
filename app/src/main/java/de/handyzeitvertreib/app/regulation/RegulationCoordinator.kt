package de.handyzeitvertreib.app.regulation

import de.handyzeitvertreib.app.core.model.AppLimit
import de.handyzeitvertreib.app.core.model.GroupLimit
import de.handyzeitvertreib.app.core.model.LimitKey
import de.handyzeitvertreib.app.core.model.RegulationAction
import de.handyzeitvertreib.app.core.model.RegulationEvent
import de.handyzeitvertreib.app.limits.LimitEvaluation
import de.handyzeitvertreib.app.limits.LimitEvaluator
import de.handyzeitvertreib.app.limits.LimitRepository
import de.handyzeitvertreib.app.usage.TodayUsageState
import de.handyzeitvertreib.app.usage.UsageRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

/** Everything needed to show or enforce today's limits. */
data class RegulationSnapshot(
    val usage: TodayUsageState.Ready,
    val appLimits: List<AppLimit>,
    val groupLimits: List<GroupLimit>,
    val events: List<RegulationEvent>,
    val evaluation: LimitEvaluation,
)

/** Connects usage, limits and regulation events. Used by UI, worker and accessibility service. */
class RegulationCoordinator(
    private val usageRepository: UsageRepository,
    private val limitRepository: LimitRepository,
    private val regulationRepository: RegulationRepository,
) {
    /** Emits null while usage data is unavailable (no access, loading or error). */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(): Flow<RegulationSnapshot?> =
        usageRepository.today.flatMapLatest { usage ->
            if (usage !is TodayUsageState.Ready) {
                flowOf(null)
            } else {
                combine(
                    limitRepository.appLimits,
                    limitRepository.groupLimits,
                    regulationRepository.observeDay(usage.date),
                ) { apps, groups, events -> snapshot(usage, apps, groups, events) }
            }
        }

    /** Refreshes usage (throttled by [minIntervalMs]) and evaluates all limits once. */
    suspend fun evaluateNow(minIntervalMs: Long = 0): RegulationSnapshot? {
        val usage = usageRepository.refresh(backfillDays = 0, minIntervalMs = minIntervalMs)
        if (usage !is TodayUsageState.Ready) return null
        return snapshot(
            usage,
            limitRepository.currentAppLimits(),
            limitRepository.currentGroupLimits(),
            regulationRepository.eventsFor(usage.date),
        )
    }

    suspend fun recordLimitReached(
        key: LimitKey,
        packageName: String?,
    ): Boolean = regulationRepository.recordLimitReachedOnce(key, packageName)

    suspend fun recordExtension(
        key: LimitKey,
        packageName: String?,
        minutes: Int,
    ) {
        regulationRepository.record(key, packageName, RegulationAction.EXTENSION_GRANTED, minutes)
        usageRepository.refresh(backfillDays = 0)
    }

    suspend fun recordOutcome(
        key: LimitKey,
        packageName: String?,
        action: RegulationAction,
    ) {
        regulationRepository.record(key, packageName, action)
    }

    private fun snapshot(
        usage: TodayUsageState.Ready,
        apps: List<AppLimit>,
        groups: List<GroupLimit>,
        events: List<RegulationEvent>,
    ) = RegulationSnapshot(
        usage = usage,
        appLimits = apps,
        groupLimits = groups,
        events = events,
        evaluation = LimitEvaluator.evaluate(usage.usageByPackage, apps, groups, RegulationRepository.extensionsMs(events)),
    )
}
