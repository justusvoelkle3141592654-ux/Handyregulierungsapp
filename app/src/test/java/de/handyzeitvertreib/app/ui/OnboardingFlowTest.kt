package de.handyzeitvertreib.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import de.handyzeitvertreib.app.MainActivity
import de.handyzeitvertreib.app.R
import de.handyzeitvertreib.app.permissions.GrantState
import de.handyzeitvertreib.app.permissions.PermissionSnapshot
import de.handyzeitvertreib.app.testing.TestHzvApplication
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OnboardingFlowTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val activity get() = compose.activity
    private val app get() = activity.application as TestHzvApplication

    private fun title(res: Int) = compose.onNodeWithText(activity.getString(res))

    private fun next() {
        compose.onNodeWithTag("onboarding-next").performClick()
        compose.waitForIdle()
    }

    @Test
    fun walksThroughAllStepsAndLandsOnDashboard() {
        compose.waitUntil(5_000) { runCatching { title(R.string.onboarding_welcome_title).assertExists() }.isSuccess }
        next()
        title(R.string.onboarding_purpose_title).assertIsDisplayed()
        next()
        title(R.string.onboarding_privacy_title).assertIsDisplayed()
        next()
        compose.onNodeWithTag("onboarding-title").assertExists()
        title(R.string.usage_access_what).assertExists()
        next()
        title(R.string.onboarding_notifications_body).assertExists()
        next()
        title(R.string.onboarding_enforcement_title).assertExists()
        next()
        title(R.string.onboarding_account_body).assertExists()
        next()
        title(R.string.onboarding_apps_title).assertExists()
        compose.waitUntil(5_000) { runCatching { compose.onNodeWithText("Chat").assertExists() }.isSuccess }
        compose.onNodeWithText("Chat").performClick()
        next()
        title(R.string.onboarding_extension_title).assertExists()
        next()
        title(R.string.onboarding_done_title).assertExists()
        compose.onNodeWithTag("onboarding-finish").performClick()

        compose.waitUntil(5_000) { runCatching { title(R.string.dashboard_today_total).assertExists() }.isSuccess }
        val limits = runBlocking { app.container.limitRepository.currentAppLimits() }
        assertThat(limits.map { it.packageName }).containsExactly("com.example.chat")
        assertThat(
            runBlocking {
                app.container.preferencesRepository
                    .current()
                    .onboardingCompleted
            },
        ).isTrue()
    }

    @Test
    fun backNavigationReturnsToPreviousStep() {
        compose.waitUntil(5_000) { runCatching { title(R.string.onboarding_welcome_title).assertExists() }.isSuccess }
        next()
        next()
        title(R.string.onboarding_privacy_title).assertIsDisplayed()
        compose.onNodeWithTag("onboarding-back").performClick()
        compose.waitForIdle()
        title(R.string.onboarding_purpose_title).assertIsDisplayed()
    }

    @Test
    fun onboardingStepSurvivesConfigurationChange() {
        compose.waitUntil(5_000) { runCatching { title(R.string.onboarding_welcome_title).assertExists() }.isSuccess }
        next()
        next()
        title(R.string.onboarding_privacy_title).assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        title(R.string.onboarding_privacy_title).assertIsDisplayed()
    }

    @Test
    fun missingUsageAccessShowsLaterInsteadOfNext() {
        app.permissions.snapshot = PermissionSnapshot(GrantState.DENIED, GrantState.DENIED, GrantState.DENIED)
        app.container.permissionMonitor.refresh()
        compose.waitUntil(5_000) { runCatching { title(R.string.onboarding_welcome_title).assertExists() }.isSuccess }
        repeat(3) { next() }
        title(R.string.onboarding_later).assertIsDisplayed()
        title(R.string.onboarding_usage_access_later).assertExists()
    }
}
