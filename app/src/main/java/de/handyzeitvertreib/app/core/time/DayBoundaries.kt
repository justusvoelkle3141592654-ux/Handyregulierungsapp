package de.handyzeitvertreib.app.core.time

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Half-open time window `[startMs, endMs)` in epoch milliseconds. */
data class TimeWindow(
    val startMs: Long,
    val endMs: Long,
) {
    init {
        require(endMs >= startMs) { "Window end must not be before start" }
    }

    val durationMs: Long get() = endMs - startMs

    fun contains(timestampMs: Long): Boolean = timestampMs in startMs until endMs
}

/**
 * A "day" is the local calendar day in the device's current time zone, from local
 * midnight (or the first valid instant after a DST gap) to the next local midnight.
 * Days are therefore 23, 24 or 25 hours long around daylight-saving transitions.
 */
object DayBoundaries {
    fun dayOf(
        timestampMs: Long,
        zone: ZoneId,
    ): LocalDate = Instant.ofEpochMilli(timestampMs).atZone(zone).toLocalDate()

    fun startOfDay(
        date: LocalDate,
        zone: ZoneId,
    ): Long = date.atStartOfDay(zone).toInstant().toEpochMilli()

    fun windowFor(
        date: LocalDate,
        zone: ZoneId,
    ): TimeWindow = TimeWindow(startOfDay(date, zone), startOfDay(date.plusDays(1), zone))

    /** The part of [date] that has elapsed at [nowMs]; the whole day if it is already over. */
    fun elapsedWindow(
        date: LocalDate,
        zone: ZoneId,
        nowMs: Long,
    ): TimeWindow {
        val full = windowFor(date, zone)
        return TimeWindow(full.startMs, nowMs.coerceIn(full.startMs, full.endMs))
    }

    /**
     * The window of the previous day covering the same elapsed duration as today so far.
     * Used for "compared with yesterday at this time".
     */
    fun sameElapsedWindowYesterday(
        today: LocalDate,
        zone: ZoneId,
        nowMs: Long,
    ): TimeWindow {
        val elapsed = elapsedWindow(today, zone, nowMs).durationMs
        val yesterday = windowFor(today.minusDays(1), zone)
        return TimeWindow(yesterday.startMs, (yesterday.startMs + elapsed).coerceAtMost(yesterday.endMs))
    }
}

/** Source of the current time and zone, replaceable in tests. */
interface AppClock {
    fun nowMs(): Long

    fun zone(): ZoneId

    fun today(): LocalDate = DayBoundaries.dayOf(nowMs(), zone())
}

object SystemAppClock : AppClock {
    override fun nowMs(): Long = System.currentTimeMillis()

    override fun zone(): ZoneId = ZoneId.systemDefault()
}
