package de.handyzeitvertreib.app.limits

import de.handyzeitvertreib.app.core.format.DurationFormatter
import de.handyzeitvertreib.app.core.format.saturatingAdd
import de.handyzeitvertreib.app.core.model.AppLimit
import de.handyzeitvertreib.app.core.model.GroupLimit
import de.handyzeitvertreib.app.core.model.LimitKey
import de.handyzeitvertreib.app.core.model.LimitType

/** Evaluation result for one enabled limit on the current day. */
data class LimitStatus(
    val key: LimitKey,
    val name: String?,
    val packageNames: Set<String>,
    val limitMs: Long,
    val extensionMs: Long,
    val usedMs: Long,
) {
    val effectiveLimitMs: Long get() = saturatingAdd(limitMs, extensionMs)
    val remainingMs: Long get() = (effectiveLimitMs - usedMs).coerceAtLeast(0)
    val isReached: Boolean get() = usedMs >= effectiveLimitMs
    val progress: Float
        get() = if (effectiveLimitMs <= 0) 1f else (usedMs.toDouble() / effectiveLimitMs).coerceIn(0.0, 1.0).toFloat()
}

data class LimitEvaluation(
    val statuses: List<LimitStatus>,
) {
    /**
     * The limit that decides regulation for [packageName]: the one with the least
     * remaining time. On a tie the app-specific limit wins because it is easier to explain.
     */
    fun governingFor(packageName: String): LimitStatus? =
        statuses
            .filter { packageName in it.packageNames }
            .minWithOrNull(compareBy<LimitStatus> { it.remainingMs }.thenBy { if (it.key.type == LimitType.APP) 0 else 1 })

    fun isRegulated(packageName: String): Boolean = governingFor(packageName)?.isReached == true

    fun reachedStatuses(): List<LimitStatus> = statuses.filter { it.isReached }

    /** Every package covered by at least one enabled limit, each counted once. */
    val limitedPackages: Set<String> get() = statuses.flatMapTo(mutableSetOf()) { it.packageNames }
}

object LimitEvaluator {
    /**
     * @param usageMs today's foreground time per package
     * @param extensionsMs extra time granted today per limit
     */
    fun evaluate(
        usageMs: Map<String, Long>,
        appLimits: List<AppLimit>,
        groupLimits: List<GroupLimit>,
        extensionsMs: Map<LimitKey, Long> = emptyMap(),
    ): LimitEvaluation {
        val appStatuses =
            appLimits.filter { it.enabled }.map { limit ->
                val key = LimitKey(LimitType.APP, limit.id)
                LimitStatus(
                    key = key,
                    name = null,
                    packageNames = setOf(limit.packageName),
                    limitMs = DurationFormatter.minutesToMs(limit.dailyLimitMinutes),
                    extensionMs = extensionsMs[key] ?: 0L,
                    usedMs = usageMs[limit.packageName] ?: 0L,
                )
            }
        val groupStatuses =
            groupLimits.filter { it.enabled && it.packageNames.isNotEmpty() }.map { group ->
                val key = LimitKey(LimitType.GROUP, group.id)
                LimitStatus(
                    key = key,
                    name = group.name,
                    packageNames = group.packageNames,
                    limitMs = DurationFormatter.minutesToMs(group.dailyLimitMinutes),
                    extensionMs = extensionsMs[key] ?: 0L,
                    usedMs = group.packageNames.fold(0L) { acc, pkg -> saturatingAdd(acc, usageMs[pkg] ?: 0L) },
                )
            }
        return LimitEvaluation(appStatuses + groupStatuses)
    }

    /** Total time of all limited apps without counting an app twice when it is in several limits. */
    fun totalLimitedUsageMs(
        evaluation: LimitEvaluation,
        usageMs: Map<String, Long>,
    ): Long = evaluation.limitedPackages.fold(0L) { acc, pkg -> saturatingAdd(acc, usageMs[pkg] ?: 0L) }
}
