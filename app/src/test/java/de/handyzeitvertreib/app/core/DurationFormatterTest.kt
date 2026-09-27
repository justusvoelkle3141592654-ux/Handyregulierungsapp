package de.handyzeitvertreib.app.core

import com.google.common.truth.Truth.assertThat
import de.handyzeitvertreib.app.core.format.DurationFormatter
import de.handyzeitvertreib.app.core.format.DurationLabels
import de.handyzeitvertreib.app.core.format.saturatingAdd
import de.handyzeitvertreib.app.core.model.LimitRules
import org.junit.Test

class DurationFormatterTest {
    private val labels = DurationLabels("%1\$d Std. %2\$d Min.", "%1\$d Std.", "%1\$d Min.", "< 1 Min.")

    @Test
    fun formatsZero() = assertThat(DurationFormatter.format(0, labels)).isEqualTo("0 Min.")

    @Test
    fun formatsSubMinute() = assertThat(DurationFormatter.format(59_999, labels)).isEqualTo("< 1 Min.")

    @Test
    fun formatsMinutes() = assertThat(DurationFormatter.format(45 * 60_000L, labels)).isEqualTo("45 Min.")

    @Test
    fun formatsWholeHours() = assertThat(DurationFormatter.format(2 * 3_600_000L, labels)).isEqualTo("2 Std.")

    @Test
    fun formatsHoursAndMinutes() = assertThat(DurationFormatter.format(3_600_000L + 5 * 60_000L + 59_000, labels)).isEqualTo("1 Std. 5 Min.")

    @Test
    fun negativeIsTreatedAsZero() = assertThat(DurationFormatter.format(-10_000, labels)).isEqualTo("0 Min.")

    @Test
    fun hugeDurationDoesNotOverflow() {
        val (hours, _) = DurationFormatter.split(Long.MAX_VALUE)
        assertThat(hours).isGreaterThan(0)
    }

    @Test
    fun saturatingAddCapsAtMax() {
        assertThat(saturatingAdd(Long.MAX_VALUE - 1, 10)).isEqualTo(Long.MAX_VALUE)
        assertThat(saturatingAdd(2, 3)).isEqualTo(5)
    }

    @Test
    fun limitRulesSnapAndClamp() {
        assertThat(LimitRules.normalize(-20)).isEqualTo(LimitRules.MIN_MINUTES)
        assertThat(LimitRules.normalize(0)).isEqualTo(5)
        assertThat(LimitRules.normalize(32)).isEqualTo(30)
        assertThat(LimitRules.normalize(33)).isEqualTo(35)
        assertThat(LimitRules.normalize(100_000)).isEqualTo(LimitRules.MAX_MINUTES)
        assertThat(LimitRules.decrement(5)).isEqualTo(5)
        assertThat(LimitRules.increment(LimitRules.MAX_MINUTES)).isEqualTo(LimitRules.MAX_MINUTES)
    }

    @Test
    fun groupNameValidation() {
        assertThat(LimitRules.isValidGroupName("  ")).isFalse()
        assertThat(LimitRules.isValidGroupName("Social Media")).isTrue()
        assertThat(LimitRules.isValidGroupName("x".repeat(41))).isFalse()
    }
}
