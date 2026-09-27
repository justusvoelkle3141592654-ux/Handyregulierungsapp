package de.handyzeitvertreib.app.usage

import de.handyzeitvertreib.app.core.format.saturatingAdd
import de.handyzeitvertreib.app.core.model.AppUsage
import de.handyzeitvertreib.app.core.time.TimeWindow

/** Platform-independent subset of `UsageEvents.Event` types used for aggregation. */
enum class UsageEventKind {
    ACTIVITY_RESUMED,
    ACTIVITY_PAUSED,
    ACTIVITY_STOPPED,
    SCREEN_OFF,
    DEVICE_SHUTDOWN,
    DEVICE_STARTUP,
}

data class RawUsageEvent(
    val packageName: String,
    val className: String?,
    val timestampMs: Long,
    val kind: UsageEventKind,
)

/**
 * A continuous period in which at least one activity of [packageName] was resumed.
 * [isLaunch] is true when the previous foreground package was a different one (or the
 * screen had been off), which is how "opens" are counted.
 */
data class ForegroundSession(
    val packageName: String,
    val startMs: Long,
    val endMs: Long,
    val isLaunch: Boolean,
)

/**
 * Turns the usage event stream into foreground sessions and per-window totals.
 *
 * The event stream is not a live feed: Android writes events as they happen and the
 * app reads them when it refreshes. A session that is still running at [nowMs] is
 * closed at [nowMs]. Sessions that started before the queried event range are not
 * visible; callers query with a look-back margin to reduce that effect.
 */
object UsageAggregator {
    fun toSessions(
        events: List<RawUsageEvent>,
        nowMs: Long,
    ): List<ForegroundSession> {
        val sorted = events.sortedBy { it.timestampMs }
        val activeActivities = mutableMapOf<String, MutableSet<String>>()
        val openSessions = mutableMapOf<String, Pair<Long, Boolean>>()
        val sessions = mutableListOf<ForegroundSession>()
        var lastForegroundPackage: String? = null

        fun close(
            packageName: String,
            endMs: Long,
        ) {
            val (start, isLaunch) = openSessions.remove(packageName) ?: return
            if (endMs > start) sessions += ForegroundSession(packageName, start, endMs, isLaunch)
        }

        fun closeAll(endMs: Long) {
            openSessions.keys.toList().forEach { close(it, endMs) }
            activeActivities.clear()
            lastForegroundPackage = null
        }

        for (event in sorted) {
            if (event.timestampMs > nowMs) break
            val activityKey = event.className ?: ""
            when (event.kind) {
                UsageEventKind.ACTIVITY_RESUMED -> {
                    val active = activeActivities.getOrPut(event.packageName) { mutableSetOf() }
                    if (active.isEmpty() && event.packageName !in openSessions) {
                        val isLaunch = lastForegroundPackage != event.packageName
                        openSessions[event.packageName] = event.timestampMs to isLaunch
                    }
                    active += activityKey
                    lastForegroundPackage = event.packageName
                }
                UsageEventKind.ACTIVITY_PAUSED, UsageEventKind.ACTIVITY_STOPPED -> {
                    val active = activeActivities[event.packageName] ?: continue
                    if (active.remove(activityKey) && active.isEmpty()) {
                        close(event.packageName, event.timestampMs)
                    }
                }
                UsageEventKind.SCREEN_OFF,
                UsageEventKind.DEVICE_SHUTDOWN,
                UsageEventKind.DEVICE_STARTUP,
                -> closeAll(event.timestampMs)
            }
        }
        openSessions.keys.toList().forEach { close(it, nowMs) }
        return sessions.sortedBy { it.startMs }
    }

    /** Sums the overlap of every session with [window]. Launches count if they start inside it. */
    fun summarize(
        sessions: List<ForegroundSession>,
        window: TimeWindow,
    ): Map<String, AppUsage> {
        val result = mutableMapOf<String, AppUsage>()
        for (session in sessions) {
            val overlapStart = maxOf(session.startMs, window.startMs)
            val overlapEnd = minOf(session.endMs, window.endMs)
            if (overlapEnd <= overlapStart) continue
            val launched = session.isLaunch && window.contains(session.startMs)
            val previous = result[session.packageName]
            result[session.packageName] =
                AppUsage(
                    packageName = session.packageName,
                    foregroundMs = saturatingAdd(previous?.foregroundMs ?: 0L, overlapEnd - overlapStart),
                    launchCount = (previous?.launchCount ?: 0) + if (launched) 1 else 0,
                    lastUsedAt = maxOf(previous?.lastUsedAt ?: Long.MIN_VALUE, overlapEnd),
                )
        }
        return result
    }

    fun totalMs(
        usage: Collection<AppUsage>,
        excluded: Set<String> = emptySet(),
    ): Long =
        usage
            .filter { it.packageName !in excluded }
            .fold(0L) { acc, item -> saturatingAdd(acc, item.foregroundMs) }
}
