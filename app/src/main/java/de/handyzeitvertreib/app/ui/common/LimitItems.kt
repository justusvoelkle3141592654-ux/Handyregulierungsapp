package de.handyzeitvertreib.app.ui.common

import de.handyzeitvertreib.app.core.model.LimitKey
import de.handyzeitvertreib.app.core.model.LimitType
import de.handyzeitvertreib.app.limits.LimitEvaluation
import de.handyzeitvertreib.app.limits.LimitStatus

/** Display model of one evaluated limit, shared by dashboard and limits overview. */
data class LimitItem(
    val key: LimitKey,
    val title: String,
    val packageName: String?,
    val memberCount: Int,
    val usedMs: Long,
    val effectiveLimitMs: Long,
    val remainingMs: Long,
    val progress: Float,
    val reached: Boolean,
    val extended: Boolean,
) {
    val isGroup: Boolean get() = key.type == LimitType.GROUP

    /** "Almost reached" means less than 10 % or 5 minutes left, whichever is larger. */
    val nearlyReached: Boolean get() = !reached && remainingMs <= maxOf(effectiveLimitMs / 10, 5 * 60_000L)
}

object LimitItems {
    fun from(
        status: LimitStatus,
        labels: Map<String, String>,
    ): LimitItem {
        val packageName = if (status.key.type == LimitType.APP) status.packageNames.single() else null
        return LimitItem(
            key = status.key,
            title = status.name ?: packageName?.let { labels[it] ?: it }.orEmpty(),
            packageName = packageName,
            memberCount = status.packageNames.size,
            usedMs = status.usedMs,
            effectiveLimitMs = status.effectiveLimitMs,
            remainingMs = status.remainingMs,
            progress = status.progress,
            reached = status.isReached,
            extended = status.extensionMs > 0,
        )
    }

    /** Reached limits first, then by least remaining time. */
    fun sorted(
        evaluation: LimitEvaluation,
        labels: Map<String, String>,
    ): List<LimitItem> =
        evaluation.statuses
            .map { from(it, labels) }
            .sortedWith(compareByDescending<LimitItem> { it.reached }.thenBy { it.remainingMs }.thenBy { it.title.lowercase() })
}
