package de.handyzeitvertreib.app.limits

import com.google.common.truth.Truth.assertThat
import de.handyzeitvertreib.app.core.model.AppLimit
import de.handyzeitvertreib.app.core.model.GroupLimit
import de.handyzeitvertreib.app.core.model.LimitKey
import de.handyzeitvertreib.app.core.model.LimitType
import org.junit.Test

class LimitEvaluatorTest {
    private fun app(
        id: Long,
        pkg: String,
        minutes: Int,
        enabled: Boolean = true,
    ) = AppLimit(id, pkg, minutes, enabled, 0, 0)

    private fun group(
        id: Long,
        minutes: Int,
        vararg pkgs: String,
        enabled: Boolean = true,
    ) = GroupLimit(id, "G$id", pkgs.toSet(), minutes, enabled, 0, 0)

    @Test
    fun remainingTimeForIndividualLimit() {
        val evaluation = LimitEvaluator.evaluate(mapOf("a" to 10 * M), listOf(app(1, "a", 30)), emptyList())
        val status = evaluation.governingFor("a")!!
        assertThat(status.remainingMs).isEqualTo(20 * M)
        assertThat(status.isReached).isFalse()
        assertThat(status.progress).isWithin(0.001f).of(1f / 3)
    }

    @Test
    fun limitIsReachedExactlyAtBoundary() {
        val evaluation = LimitEvaluator.evaluate(mapOf("a" to 30 * M), listOf(app(1, "a", 30)), emptyList())
        assertThat(evaluation.isRegulated("a")).isTrue()
        assertThat(evaluation.governingFor("a")!!.remainingMs).isEqualTo(0)
    }

    @Test
    fun oneMillisecondBeforeLimitIsNotReached() {
        val evaluation = LimitEvaluator.evaluate(mapOf("a" to 30 * M - 1), listOf(app(1, "a", 30)), emptyList())
        assertThat(evaluation.isRegulated("a")).isFalse()
    }

    @Test
    fun disabledLimitsAreIgnored() {
        val evaluation =
            LimitEvaluator.evaluate(
                mapOf("a" to 90 * M),
                listOf(app(1, "a", 30, enabled = false)),
                listOf(group(2, 10, "a", enabled = false)),
            )
        assertThat(evaluation.statuses).isEmpty()
        assertThat(evaluation.isRegulated("a")).isFalse()
    }

    @Test
    fun groupSumsMemberUsage() {
        val evaluation =
            LimitEvaluator.evaluate(
                mapOf("a" to 20 * M, "b" to 25 * M, "c" to 99 * M),
                emptyList(),
                listOf(group(1, 60, "a", "b")),
            )
        val status = evaluation.statuses.single()
        assertThat(status.usedMs).isEqualTo(45 * M)
        assertThat(status.remainingMs).isEqualTo(15 * M)
    }

    @Test
    fun stricterGroupLimitGovernsOverAppLimit() {
        val evaluation =
            LimitEvaluator.evaluate(
                mapOf("a" to 20 * M, "b" to 40 * M),
                listOf(app(1, "a", 60)),
                listOf(group(2, 60, "a", "b")),
            )
        val governing = evaluation.governingFor("a")!!
        assertThat(governing.key).isEqualTo(LimitKey(LimitType.GROUP, 2))
        assertThat(evaluation.isRegulated("a")).isTrue()
    }

    @Test
    fun stricterAppLimitGovernsOverGroupLimit() {
        val evaluation =
            LimitEvaluator.evaluate(
                mapOf("a" to 20 * M),
                listOf(app(1, "a", 25)),
                listOf(group(2, 120, "a", "b")),
            )
        assertThat(evaluation.governingFor("a")!!.key).isEqualTo(LimitKey(LimitType.APP, 1))
    }

    @Test
    fun appLimitWinsTie() {
        val evaluation =
            LimitEvaluator.evaluate(mapOf("a" to 10 * M), listOf(app(1, "a", 30)), listOf(group(2, 30, "a")))
        assertThat(evaluation.governingFor("a")!!.key.type).isEqualTo(LimitType.APP)
    }

    @Test
    fun extensionAddsTimeOnlyToItsLimit() {
        val evaluation =
            LimitEvaluator.evaluate(
                mapOf("a" to 32 * M),
                listOf(app(1, "a", 30)),
                listOf(group(2, 30, "a")),
                extensionsMs = mapOf(LimitKey(LimitType.APP, 1) to 5 * M),
            )
        // The app limit is extended, but the group limit is still reached.
        assertThat(evaluation.statuses.first { it.key.type == LimitType.APP }.isReached).isFalse()
        assertThat(evaluation.isRegulated("a")).isTrue()
        assertThat(evaluation.governingFor("a")!!.key.type).isEqualTo(LimitType.GROUP)
    }

    @Test
    fun totalLimitedUsageDoesNotDoubleCount() {
        val usage = mapOf("a" to 10 * M, "b" to 20 * M, "c" to 5 * M)
        val evaluation = LimitEvaluator.evaluate(usage, listOf(app(1, "a", 30)), listOf(group(2, 60, "a", "b")))
        assertThat(LimitEvaluator.totalLimitedUsageMs(evaluation, usage)).isEqualTo(30 * M)
    }

    @Test
    fun unusedAppHasFullRemainingTime() {
        val evaluation = LimitEvaluator.evaluate(emptyMap(), listOf(app(1, "a", 30)), emptyList())
        assertThat(evaluation.governingFor("a")!!.remainingMs).isEqualTo(30 * M)
    }

    @Test
    fun emptyGroupIsIgnored() {
        val evaluation = LimitEvaluator.evaluate(emptyMap(), emptyList(), listOf(group(1, 30)))
        assertThat(evaluation.statuses).isEmpty()
    }

    @Test
    fun unlimitedAppIsNotRegulated() {
        val evaluation = LimitEvaluator.evaluate(mapOf("x" to 999 * M), listOf(app(1, "a", 30)), emptyList())
        assertThat(evaluation.governingFor("x")).isNull()
        assertThat(evaluation.isRegulated("x")).isFalse()
    }

    private companion object {
        const val M = 60_000L
    }
}
