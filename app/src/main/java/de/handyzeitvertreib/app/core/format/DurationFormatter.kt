package de.handyzeitvertreib.app.core.format

/** Localised templates used to render durations; supplied from string resources. */
data class DurationLabels(
    val hoursMinutes: String,
    val hoursOnly: String,
    val minutesOnly: String,
    val lessThanMinute: String,
)

object DurationFormatter {
    private const val MS_PER_MINUTE = 60_000L

    /** Splits into whole hours and remaining whole minutes. Negative input is treated as zero. */
    fun split(durationMs: Long): Pair<Long, Long> {
        val totalMinutes = durationMs.coerceAtLeast(0) / MS_PER_MINUTE
        return totalMinutes / 60 to totalMinutes % 60
    }

    fun format(
        durationMs: Long,
        labels: DurationLabels,
    ): String {
        val safe = durationMs.coerceAtLeast(0)
        if (safe in 1 until MS_PER_MINUTE) return labels.lessThanMinute
        val (hours, minutes) = split(safe)
        return when {
            hours == 0L -> labels.minutesOnly.format(minutes)
            minutes == 0L -> labels.hoursOnly.format(hours)
            else -> labels.hoursMinutes.format(hours, minutes)
        }
    }

    fun minutesToMs(minutes: Int): Long = minutes.toLong() * MS_PER_MINUTE
}

/** Adds two non-negative durations without overflowing. */
fun saturatingAdd(
    a: Long,
    b: Long,
): Long {
    val result = a + b
    return if (((a xor result) and (b xor result)) < 0) Long.MAX_VALUE else result
}
