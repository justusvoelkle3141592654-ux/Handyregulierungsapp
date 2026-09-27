package de.handyzeitvertreib.app.usage

import com.google.common.truth.Truth.assertThat
import de.handyzeitvertreib.app.core.time.TimeWindow
import de.handyzeitvertreib.app.usage.UsageEventKind.ACTIVITY_PAUSED
import de.handyzeitvertreib.app.usage.UsageEventKind.ACTIVITY_RESUMED
import de.handyzeitvertreib.app.usage.UsageEventKind.ACTIVITY_STOPPED
import de.handyzeitvertreib.app.usage.UsageEventKind.DEVICE_SHUTDOWN
import de.handyzeitvertreib.app.usage.UsageEventKind.DEVICE_STARTUP
import de.handyzeitvertreib.app.usage.UsageEventKind.SCREEN_OFF
import org.junit.Test

class UsageAggregatorTest {
    private val day = TimeWindow(0, 24 * H)

    private fun ev(
        pkg: String,
        t: Long,
        kind: UsageEventKind,
        cls: String = "Main",
    ) = RawUsageEvent(pkg, cls, t, kind)

    private fun summarize(
        events: List<RawUsageEvent>,
        now: Long = 24 * H,
        window: TimeWindow = day,
    ) = UsageAggregator.summarize(UsageAggregator.toSessions(events, now), window)

    @Test
    fun multipleIntervalsForSameAppAreSummed() {
        val usage =
            summarize(
                listOf(
                    ev("a", 1 * H, ACTIVITY_RESUMED),
                    ev("a", 1 * H + 10 * M, ACTIVITY_PAUSED),
                    ev("b", 1 * H + 10 * M, ACTIVITY_RESUMED),
                    ev("b", 1 * H + 20 * M, ACTIVITY_PAUSED),
                    ev("a", 2 * H, ACTIVITY_RESUMED),
                    ev("a", 2 * H + 5 * M, ACTIVITY_PAUSED),
                ),
            )
        assertThat(usage.getValue("a").foregroundMs).isEqualTo(15 * M)
        assertThat(usage.getValue("a").launchCount).isEqualTo(2)
        assertThat(usage.getValue("b").foregroundMs).isEqualTo(10 * M)
    }

    @Test
    fun switchingActivitiesInsideAppIsOneLaunchWithoutDoubleCounting() {
        val usage =
            summarize(
                listOf(
                    ev("a", 1 * H, ACTIVITY_RESUMED, "One"),
                    ev("a", 1 * H + 5 * M, ACTIVITY_RESUMED, "Two"),
                    ev("a", 1 * H + 5 * M, ACTIVITY_PAUSED, "One"),
                    ev("a", 1 * H + 9 * M, ACTIVITY_STOPPED, "Two"),
                ),
            )
        assertThat(usage.getValue("a").foregroundMs).isEqualTo(9 * M)
        assertThat(usage.getValue("a").launchCount).isEqualTo(1)
    }

    @Test
    fun adjacentPauseResumeInSameAppIsNotANewLaunch() {
        val usage =
            summarize(
                listOf(
                    ev("a", 1 * H, ACTIVITY_RESUMED, "One"),
                    ev("a", 1 * H + 5 * M, ACTIVITY_PAUSED, "One"),
                    ev("a", 1 * H + 5 * M, ACTIVITY_RESUMED, "Two"),
                    ev("a", 1 * H + 8 * M, ACTIVITY_PAUSED, "Two"),
                ),
            )
        assertThat(usage.getValue("a").foregroundMs).isEqualTo(8 * M)
        assertThat(usage.getValue("a").launchCount).isEqualTo(1)
    }

    @Test
    fun sessionCrossingMidnightIsSplitBetweenDays() {
        val sessions =
            UsageAggregator.toSessions(
                listOf(ev("a", 24 * H - 10 * M, ACTIVITY_RESUMED), ev("a", 24 * H + 20 * M, ACTIVITY_PAUSED)),
                nowMs = 30 * H,
            )
        val dayOne = UsageAggregator.summarize(sessions, day).getValue("a")
        val dayTwo = UsageAggregator.summarize(sessions, TimeWindow(24 * H, 48 * H)).getValue("a")
        assertThat(dayOne.foregroundMs).isEqualTo(10 * M)
        assertThat(dayOne.launchCount).isEqualTo(1)
        assertThat(dayTwo.foregroundMs).isEqualTo(20 * M)
        assertThat(dayTwo.launchCount).isEqualTo(0)
    }

    @Test
    fun screenOffClosesAllSessions() {
        val usage =
            summarize(
                listOf(ev("a", 1 * H, ACTIVITY_RESUMED), ev("android", 1 * H + 3 * M, SCREEN_OFF)),
            )
        assertThat(usage.getValue("a").foregroundMs).isEqualTo(3 * M)
    }

    @Test
    fun shutdownAndStartupCloseStaleSessions() {
        val usage =
            summarize(
                listOf(
                    ev("a", 1 * H, ACTIVITY_RESUMED),
                    ev("android", 1 * H + 2 * M, DEVICE_SHUTDOWN),
                    ev("b", 2 * H, ACTIVITY_RESUMED),
                    ev("android", 5 * H, DEVICE_STARTUP),
                ),
            )
        assertThat(usage.getValue("a").foregroundMs).isEqualTo(2 * M)
        assertThat(usage.getValue("b").foregroundMs).isEqualTo(3 * H)
    }

    @Test
    fun openSessionIsClosedAtNow() {
        val usage = summarize(listOf(ev("a", 1 * H, ACTIVITY_RESUMED)), now = 1 * H + 7 * M)
        assertThat(usage.getValue("a").foregroundMs).isEqualTo(7 * M)
    }

    @Test
    fun eventsAfterNowAreIgnored() {
        val usage = summarize(listOf(ev("a", 1 * H, ACTIVITY_RESUMED), ev("a", 3 * H, ACTIVITY_PAUSED)), now = 2 * H)
        assertThat(usage.getValue("a").foregroundMs).isEqualTo(1 * H)
    }

    @Test
    fun unsortedInputIsHandled() {
        val usage = summarize(listOf(ev("a", 2 * H, ACTIVITY_PAUSED), ev("a", 1 * H, ACTIVITY_RESUMED)))
        assertThat(usage.getValue("a").foregroundMs).isEqualTo(1 * H)
    }

    @Test
    fun pauseWithoutResumeIsIgnored() {
        assertThat(summarize(listOf(ev("a", 1 * H, ACTIVITY_PAUSED)))).isEmpty()
    }

    @Test
    fun splitScreenCountsBothVisibleApps() {
        val usage =
            summarize(
                listOf(
                    ev("a", 1 * H, ACTIVITY_RESUMED),
                    ev("b", 1 * H, ACTIVITY_RESUMED),
                    ev("a", 1 * H + 10 * M, ACTIVITY_PAUSED),
                    ev("b", 1 * H + 10 * M, ACTIVITY_PAUSED),
                ),
            )
        assertThat(usage.getValue("a").foregroundMs).isEqualTo(10 * M)
        assertThat(usage.getValue("b").foregroundMs).isEqualTo(10 * M)
    }

    @Test
    fun noEventsMeansNoEntries() {
        assertThat(summarize(emptyList())).isEmpty()
    }

    @Test
    fun windowWithoutSessionsIsEmpty() {
        val usage =
            summarize(
                listOf(ev("a", 1 * H, ACTIVITY_RESUMED), ev("a", 2 * H, ACTIVITY_PAUSED)),
                window = TimeWindow(5 * H, 6 * H),
            )
        assertThat(usage).isEmpty()
    }

    @Test
    fun totalRespectsExclusions() {
        val usage = summarize(listOf(ev("a", 0, ACTIVITY_RESUMED), ev("a", 1 * H, ACTIVITY_PAUSED), ev("launcher", 1 * H, ACTIVITY_RESUMED), ev("launcher", 2 * H, ACTIVITY_PAUSED)))
        assertThat(UsageAggregator.totalMs(usage.values)).isEqualTo(2 * H)
        assertThat(UsageAggregator.totalMs(usage.values, setOf("launcher"))).isEqualTo(1 * H)
    }

    private companion object {
        const val M = 60_000L
        const val H = 60 * M
    }
}
