package de.handyzeitvertreib.app.ui

import com.google.common.truth.Truth.assertThat
import de.handyzeitvertreib.app.core.model.AppUsage
import de.handyzeitvertreib.app.data.management.LocalDataExport
import de.handyzeitvertreib.app.data.db.AppLimitEntity
import de.handyzeitvertreib.app.data.db.GroupLimitEntity
import de.handyzeitvertreib.app.data.db.GroupMemberEntity
import de.handyzeitvertreib.app.ui.insights.InsightsAggregation
import de.handyzeitvertreib.app.usage.DayUsage
import org.junit.Test
import java.time.LocalDate

class InsightsAggregationTest {
    private val day = LocalDate.of(2026, 9, 20)

    @Test
    fun uncapturedDaysAreNotCountedAsZeroUsage() {
        val days =
            listOf(
                DayUsage(day, captured = true, apps = listOf(AppUsage("a", 60_000, 2, null), AppUsage("launcher", 99_000, 1, null))),
                DayUsage(day.plusDays(1), captured = false, apps = emptyList()),
                DayUsage(day.plusDays(2), captured = true, apps = listOf(AppUsage("a", 30_000, 1, null), AppUsage("b", 30_000, 1, null))),
            )
        val (bars, total, apps) = InsightsAggregation.build(days, excluded = setOf("launcher"), labels = mapOf("a" to "Alpha"))
        assertThat(bars.map { it.captured }).containsExactly(true, false, true).inOrder()
        assertThat(total).isEqualTo(120_000)
        assertThat(apps.first().label).isEqualTo("Alpha")
        assertThat(apps.first().durationMs).isEqualTo(90_000)
        assertThat(apps.first().launchCount).isEqualTo(3)
        assertThat(apps.map { it.packageName }).doesNotContain("launcher")
        assertThat(apps.first().share).isWithin(0.001f).of(0.75f)
    }

    @Test
    fun exportEscapesAndContainsNoUnexpectedFields() {
        val json =
            LocalDataExport.toJson(
                snapshots = emptyList(),
                appLimits = listOf(AppLimitEntity(1, "pkg.a", 30, true, 0, 0)),
                groups = listOf(GroupLimitEntity(7, "Soziale \"Netze\"\n", 45, true, 0, 0)),
                members = listOf(GroupMemberEntity(7, "pkg.b"), GroupMemberEntity(7, "pkg.a")),
                events = emptyList(),
            )
        assertThat(json).contains("\"formatVersion\": 1")
        assertThat(json).contains("Soziale \\\"Netze\\\"\\n")
        assertThat(json).contains("[\"pkg.a\", \"pkg.b\"]")
        assertThat(LocalDataExport.quote("\u0001")).isEqualTo("\"\\u0001\"")
    }
}
