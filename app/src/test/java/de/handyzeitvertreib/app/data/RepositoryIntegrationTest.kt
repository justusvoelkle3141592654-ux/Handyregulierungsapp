package de.handyzeitvertreib.app.data

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import de.handyzeitvertreib.app.AppContainer
import de.handyzeitvertreib.app.core.model.LimitKey
import de.handyzeitvertreib.app.core.model.LimitType
import de.handyzeitvertreib.app.core.model.RegulationAction
import de.handyzeitvertreib.app.testing.HOUR
import de.handyzeitvertreib.app.testing.MINUTE
import de.handyzeitvertreib.app.testing.TestHzvApplication
import de.handyzeitvertreib.app.usage.TodayUsageState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RepositoryIntegrationTest {
    private lateinit var app: TestHzvApplication
    private lateinit var container: AppContainer

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        container = app.container
    }

    @After
    fun tearDown() {
        container.database.close()
    }

    @Test
    fun missingAccessIsReportedAndNothingIsStored() =
        runBlocking {
            app.events.access = false
            val state = container.usageRepository.refresh()
            assertThat(state).isEqualTo(TodayUsageState.AccessRequired)
            val days = container.usageRepository.observeDays(app.clock.today().minusDays(7), app.clock.today()).first()
            assertThat(days.none { it.captured }).isTrue()
        }

    @Test
    fun revokedAccessDuringQueryIsAccessRequired() =
        runBlocking {
            app.events.throwSecurity = true
            assertThat(container.usageRepository.refresh()).isEqualTo(TodayUsageState.AccessRequired)
        }

    @Test
    fun refreshComputesTodayExcludingLauncherAndStoresSnapshots() =
        runBlocking {
            val start = app.clock.startOfToday()
            app.events.session("com.example.chat", start + 9 * HOUR, 20 * MINUTE)
            app.events.session("com.example.launcher", start + 10 * HOUR, 5 * MINUTE)
            app.events.session("com.example.chat", start + 11 * HOUR, 10 * MINUTE)
            val state = container.usageRepository.refresh() as TodayUsageState.Ready
            assertThat(state.totalMs).isEqualTo(30 * MINUTE)
            assertThat(state.usageByPackage["com.example.chat"]).isEqualTo(30 * MINUTE)
            val today = container.usageRepository.observeDays(app.clock.today(), app.clock.today()).first().single()
            assertThat(today.captured).isTrue()
            assertThat(today.apps.first { it.packageName == "com.example.chat" }.launchCount).isEqualTo(2)
        }

    @Test
    fun yesterdayIsCapturedOnlyWhenFullyCovered() =
        runBlocking {
            val start = app.clock.startOfToday()
            // Earliest event lies 30 hours back: yesterday is fully covered, the day before only partly.
            app.events.session("com.example.video", start - 30 * HOUR, 10 * MINUTE)
            app.events.session("com.example.video", start - 20 * HOUR, 15 * MINUTE)
            app.events.session("com.example.video", start + 1 * HOUR, 5 * MINUTE)
            val state = container.usageRepository.refresh() as TodayUsageState.Ready
            val days = container.usageRepository.observeDays(app.clock.today().minusDays(2), app.clock.today()).first()
            assertThat(days.map { it.captured }).containsExactly(false, true, true).inOrder()
            assertThat(days[1].totalMs(emptySet())).isEqualTo(15 * MINUTE)
            // Yesterday up to 14:00 includes the 04:00 session only.
            assertThat(state.yesterdaySameTimeMs).isEqualTo(15 * MINUTE)
        }

    @Test
    fun noEventsAtAllGivesZeroTodayButNoYesterdayComparison() =
        runBlocking {
            val state = container.usageRepository.refresh() as TodayUsageState.Ready
            assertThat(state.totalMs).isEqualTo(0)
            assertThat(state.yesterdaySameTimeMs).isNull()
        }

    @Test
    fun limitCrudAndEvaluation() =
        runBlocking {
            val start = app.clock.startOfToday()
            app.events.session("com.example.chat", start + 9 * HOUR, 40 * MINUTE)
            val limits = container.limitRepository
            val id = limits.saveAppLimit("com.example.chat", 32)
            assertThat(limits.currentAppLimits().single().dailyLimitMinutes).isEqualTo(30)

            var snapshot = container.regulationCoordinator.evaluateNow()!!
            assertThat(snapshot.evaluation.isRegulated("com.example.chat")).isTrue()

            limits.saveAppLimit("com.example.chat", 60)
            assertThat(limits.currentAppLimits().single().id).isEqualTo(id)
            snapshot = container.regulationCoordinator.evaluateNow()!!
            assertThat(snapshot.evaluation.governingFor("com.example.chat")!!.remainingMs).isEqualTo(20 * MINUTE)

            limits.setAppLimitEnabled(id, false)
            assertThat(container.regulationCoordinator.evaluateNow()!!.evaluation.statuses).isEmpty()

            limits.deleteAppLimit(id)
            assertThat(limits.currentAppLimits()).isEmpty()
        }

    @Test
    fun groupMembershipUpdatesReplaceMembers() =
        runBlocking {
            val limits = container.limitRepository
            val id = limits.saveGroup(0, "Social", setOf("com.example.chat", "com.example.video"), 45, true)
            limits.saveGroup(id, "Social ", setOf("com.example.chat"), 45, true)
            val group = limits.currentGroupLimits().single()
            assertThat(group.name).isEqualTo("Social")
            assertThat(group.packageNames).containsExactly("com.example.chat")
            limits.deleteGroup(id)
            assertThat(container.database.limitDao().groupMembers()).isEmpty()
        }

    @Test
    fun extensionAddsTimeAndLimitReachedIsRecordedOnce() =
        runBlocking {
            val start = app.clock.startOfToday()
            app.events.session("com.example.chat", start + 9 * HOUR, 31 * MINUTE)
            val id = container.limitRepository.saveAppLimit("com.example.chat", 30)
            val key = LimitKey(LimitType.APP, id)
            assertThat(container.regulationCoordinator.recordLimitReached(key, "com.example.chat")).isTrue()
            assertThat(container.regulationCoordinator.recordLimitReached(key, "com.example.chat")).isFalse()

            container.regulationCoordinator.recordExtension(key, "com.example.chat", 5)
            val snapshot = container.regulationCoordinator.evaluateNow()!!
            val status = snapshot.evaluation.governingFor("com.example.chat")!!
            assertThat(status.isReached).isFalse()
            assertThat(status.remainingMs).isEqualTo(4 * MINUTE)
            assertThat(snapshot.events.map { it.action }).containsExactly(RegulationAction.LIMIT_REACHED, RegulationAction.EXTENSION_GRANTED).inOrder()
        }

    @Test
    fun extensionsExpireWithTheDay() =
        runBlocking {
            val id = container.limitRepository.saveAppLimit("com.example.chat", 30)
            container.regulationCoordinator.recordExtension(LimitKey(LimitType.APP, id), "com.example.chat", 10)
            app.clock.now += 24 * HOUR
            val snapshot = container.regulationCoordinator.evaluateNow()!!
            assertThat(snapshot.evaluation.statuses.single().extensionMs).isEqualTo(0)
        }

    @Test
    fun retentionRemovesOldDays() =
        runBlocking {
            val start = app.clock.startOfToday()
            app.events.session("com.example.news", start - 30 * HOUR, 5 * MINUTE)
            app.events.session("com.example.news", start - 20 * HOUR, 5 * MINUTE)
            container.usageRepository.refresh()
            app.clock.now += 40 * 24 * HOUR
            container.usageRepository.applyRetention(30)
            assertThat(container.database.usageDao().allSnapshots()).isEmpty()
        }

    @Test
    fun deleteEverythingResetsDataAndOnboarding() =
        runBlocking {
            val start = app.clock.startOfToday()
            app.events.session("com.example.news", start + HOUR, 5 * MINUTE)
            container.usageRepository.refresh()
            container.limitRepository.saveAppLimit("com.example.news", 30)
            container.preferencesRepository.setOnboardingCompleted(true)
            val json = container.localDataManager.exportJson()
            assertThat(json).contains("com.example.news")

            container.localDataManager.deleteEverything()
            assertThat(container.database.usageDao().allSnapshots()).isEmpty()
            assertThat(container.limitRepository.currentAppLimits()).isEmpty()
            assertThat(container.preferencesRepository.current().onboardingCompleted).isFalse()
            assertThat(container.usageRepository.today.value).isEqualTo(TodayUsageState.Loading)
        }

    @Test
    fun offlineModeNeedsNoAccountOrNetwork() =
        runBlocking {
            assertThat(container.accountRepository.state.value).isEqualTo(de.handyzeitvertreib.app.account.AccountState.NotConfigured)
            container.accountRepository.signOut()
            app.events.session("com.example.chat", app.clock.startOfToday() + HOUR, MINUTE)
            assertThat(container.usageRepository.refresh()).isInstanceOf(TodayUsageState.Ready::class.java)
        }
}
