package de.handyzeitvertreib.app.core.model

/** Validation rules for limit durations shared by UI and persistence. */
object LimitRules {
    const val STEP_MINUTES = 5
    const val MIN_MINUTES = 5
    const val MAX_MINUTES = 23 * 60 + 55
    const val DEFAULT_MINUTES = 30

    /** Clamps to the allowed range and snaps to the nearest 5-minute step. */
    fun normalize(minutes: Int): Int {
        val clamped = minutes.coerceIn(MIN_MINUTES, MAX_MINUTES)
        val snapped = ((clamped + STEP_MINUTES / 2) / STEP_MINUTES) * STEP_MINUTES
        return snapped.coerceIn(MIN_MINUTES, MAX_MINUTES)
    }

    fun increment(minutes: Int): Int = normalize(minutes + STEP_MINUTES)

    fun decrement(minutes: Int): Int = normalize(minutes - STEP_MINUTES)

    fun isValidGroupName(name: String): Boolean = name.isNotBlank() && name.length <= 40
}
