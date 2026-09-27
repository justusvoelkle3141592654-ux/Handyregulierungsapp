package de.handyzeitvertreib.app.ui

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import de.handyzeitvertreib.app.R
import de.handyzeitvertreib.app.core.model.LimitKey
import de.handyzeitvertreib.app.core.model.LimitType
import de.handyzeitvertreib.app.core.model.ThemeMode
import de.handyzeitvertreib.app.enforcement.EnforcementLevel
import de.handyzeitvertreib.app.ui.common.LimitItem
import de.handyzeitvertreib.app.ui.dashboard.DashboardActions
import de.handyzeitvertreib.app.ui.dashboard.DashboardScreen
import de.handyzeitvertreib.app.ui.dashboard.DashboardUiState
import de.handyzeitvertreib.app.ui.dashboard.UsageItem
import de.handyzeitvertreib.app.ui.designsystem.HzvTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DashboardScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private val reachedLimit =
        LimitItem(
            key = LimitKey(LimitType.APP, 1),
            title = "Video",
            packageName = "com.example.video",
            memberCount = 1,
            usedMs = 50 * 60_000L,
            effectiveLimitMs = 45 * 60_000L,
            remainingMs = 0,
            progress = 1f,
            reached = true,
            extended = false,
        )

    private val ready =
        DashboardUiState.Ready(
            totalMs = 2 * 3_600_000L + 14 * 60_000L,
            yesterdaySameTimeMs = 2 * 3_600_000L,
            topApps = listOf(UsageItem("com.example.video", "Video", 50 * 60_000L, 3, 0.4f)),
            limits = listOf(reachedLimit),
            limitedTotalMs = 50 * 60_000L,
            refreshedAt = 0L,
            enforcementLevel = EnforcementLevel.NOTIFICATION,
        )

    private fun scrollTo(text: String) {
        compose.onNodeWithTag("dashboard-list").performScrollToNode(hasText(text))
        compose.onNodeWithText(text).assertIsDisplayed()
    }

    @Test
    fun missingPermissionShowsExplanationInsteadOfZero() {
        var setUpClicked = false
        compose.setContent {
            HzvTheme { DashboardScreen(DashboardUiState.AccessRequired, DashboardActions(onSetUpUsageAccess = { setUpClicked = true })) }
        }
        compose.onNodeWithText(context.getString(R.string.dashboard_access_required_title)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.duration_minutes, 0)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.action_set_up_usage_access)).performClick()
        assertThat(setUpClicked).isTrue()
    }

    @Test
    fun sampleDataShowsTotalComparisonReachedLimitAndTopApp() {
        var opened: Pair<LimitKey, String?>? = null
        compose.setContent {
            HzvTheme { DashboardScreen(ready, DashboardActions(onOpenRegulation = { key, pkg -> opened = key to pkg })) }
        }
        compose.onNodeWithText(context.getString(R.string.duration_hours_minutes, 2, 14)).assertIsDisplayed()
        compose
            .onNodeWithText(
                context.getString(R.string.dashboard_compare_more, context.getString(R.string.duration_minutes, 14)),
            ).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.dashboard_reached_title, "Video")).assertIsDisplayed()
        scrollTo(context.getString(R.string.dashboard_enforcement_hint_title))
        scrollTo(context.getString(R.string.action_view))
        compose.onNodeWithText(context.getString(R.string.action_view)).performClick()
        assertThat(opened).isEqualTo(reachedLimit.key to "com.example.video")
    }

    @Test
    fun grantedAccessWithoutDataShowsEmptyStates() {
        compose.setContent {
            HzvTheme {
                DashboardScreen(
                    ready.copy(totalMs = 0, yesterdaySameTimeMs = null, topApps = emptyList(), limits = emptyList(), limitedTotalMs = 0),
                    DashboardActions(),
                )
            }
        }
        compose.onNodeWithText(context.getString(R.string.dashboard_compare_unavailable)).assertIsDisplayed()
        scrollTo(context.getString(R.string.dashboard_no_limits_title))
        scrollTo(context.getString(R.string.dashboard_no_usage_title))
    }

    @Test
    fun darkThemeWithLargeFontStillRendersKeyContent() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                HzvTheme(themeMode = ThemeMode.DARK) { DashboardScreen(ready, DashboardActions()) }
            }
        }
        compose.onNodeWithText(context.getString(R.string.duration_hours_minutes, 2, 14)).assertIsDisplayed()
        compose.onNodeWithContentDescription(context.getString(R.string.action_refresh)).assertHasClickAction()
    }
}
