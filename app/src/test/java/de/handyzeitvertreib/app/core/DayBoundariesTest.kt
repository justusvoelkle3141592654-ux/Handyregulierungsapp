package de.handyzeitvertreib.app.core

import com.google.common.truth.Truth.assertThat
import de.handyzeitvertreib.app.core.time.DayBoundaries
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class DayBoundariesTest {
    private val berlin = ZoneId.of("Europe/Berlin")

    @Test
    fun regularDayIs24Hours() {
        val window = DayBoundaries.windowFor(LocalDate.of(2026, 6, 10), berlin)
        assertThat(window.durationMs).isEqualTo(24 * HOUR)
    }

    @Test
    fun springForwardDayIs23Hours() {
        val window = DayBoundaries.windowFor(LocalDate.of(2026, 3, 29), berlin)
        assertThat(window.durationMs).isEqualTo(23 * HOUR)
    }

    @Test
    fun fallBackDayIs25Hours() {
        val window = DayBoundaries.windowFor(LocalDate.of(2026, 10, 25), berlin)
        assertThat(window.durationMs).isEqualTo(25 * HOUR)
    }

    @Test
    fun instantJustBeforeMidnightBelongsToPreviousDay() {
        val beforeMidnight = ZonedDateTime.of(2026, 6, 10, 23, 59, 59, 0, berlin).toInstant().toEpochMilli()
        assertThat(DayBoundaries.dayOf(beforeMidnight, berlin)).isEqualTo(LocalDate.of(2026, 6, 10))
        assertThat(DayBoundaries.dayOf(beforeMidnight + 1_000, berlin)).isEqualTo(LocalDate.of(2026, 6, 11))
    }

    @Test
    fun sameInstantIsDifferentDayInOtherZone() {
        val instant = ZonedDateTime.of(2026, 6, 10, 23, 30, 0, 0, berlin).toInstant().toEpochMilli()
        assertThat(DayBoundaries.dayOf(instant, ZoneId.of("Europe/London"))).isEqualTo(LocalDate.of(2026, 6, 10))
        assertThat(DayBoundaries.dayOf(instant, ZoneId.of("Asia/Tokyo"))).isEqualTo(LocalDate.of(2026, 6, 11))
    }

    @Test
    fun elapsedWindowIsClampedToDay() {
        val date = LocalDate.of(2026, 6, 10)
        val full = DayBoundaries.windowFor(date, berlin)
        assertThat(DayBoundaries.elapsedWindow(date, berlin, full.endMs + 5 * HOUR)).isEqualTo(full)
        assertThat(DayBoundaries.elapsedWindow(date, berlin, full.startMs - HOUR).durationMs).isEqualTo(0)
    }

    @Test
    fun yesterdayComparisonCoversSameElapsedDuration() {
        val today = LocalDate.of(2026, 6, 10)
        val now = DayBoundaries.startOfDay(today, berlin) + 10 * HOUR
        val window = DayBoundaries.sameElapsedWindowYesterday(today, berlin, now)
        assertThat(window.startMs).isEqualTo(DayBoundaries.startOfDay(today.minusDays(1), berlin))
        assertThat(window.durationMs).isEqualTo(10 * HOUR)
    }

    @Test
    fun yesterdayComparisonAfterShortDayIsClamped() {
        val today = LocalDate.of(2026, 3, 30)
        val now = DayBoundaries.startOfDay(today, berlin) + 23 * HOUR + 30 * 60_000
        val window = DayBoundaries.sameElapsedWindowYesterday(today, berlin, now)
        assertThat(window.durationMs).isEqualTo(23 * HOUR)
    }

    private companion object {
        const val HOUR = 3_600_000L
    }
}
