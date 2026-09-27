package de.handyzeitvertreib.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import de.handyzeitvertreib.app.core.model.ExtensionPolicy
import de.handyzeitvertreib.app.core.model.LimitKey
import de.handyzeitvertreib.app.core.model.LimitType
import de.handyzeitvertreib.app.core.model.ThemeMode
import de.handyzeitvertreib.app.enforcement.EnforcementLevel
import de.handyzeitvertreib.app.permissions.GrantState
import de.handyzeitvertreib.app.permissions.PermissionSnapshot
import de.handyzeitvertreib.app.regulation.AuthCapability
import de.handyzeitvertreib.app.regulation.ExtensionAvailability
import de.handyzeitvertreib.app.regulation.RegulationActions
import de.handyzeitvertreib.app.regulation.RegulationScreen
import de.handyzeitvertreib.app.regulation.RegulationUiState
import de.handyzeitvertreib.app.testing.TestHzvApplication
import de.handyzeitvertreib.app.ui.common.LimitItem
import de.handyzeitvertreib.app.ui.dashboard.DashboardActions
import de.handyzeitvertreib.app.ui.dashboard.DashboardScreen
import de.handyzeitvertreib.app.ui.dashboard.DashboardUiState
import de.handyzeitvertreib.app.ui.dashboard.UsageItem
import de.handyzeitvertreib.app.ui.designsystem.GlassBackground
import de.handyzeitvertreib.app.ui.designsystem.HzvTheme
import de.handyzeitvertreib.app.ui.insights.DayUsageBar
import de.handyzeitvertreib.app.ui.insights.InsightsMode
import de.handyzeitvertreib.app.ui.insights.InsightsScreen
import de.handyzeitvertreib.app.ui.insights.InsightsUiState
import de.handyzeitvertreib.app.ui.limits.AppLimitEditorScreen
import de.handyzeitvertreib.app.ui.limits.AppLimitEditorState
import de.handyzeitvertreib.app.ui.limits.ConfiguredLimit
import de.handyzeitvertreib.app.ui.limits.LimitsActions
import de.handyzeitvertreib.app.ui.limits.LimitsScreen
import de.handyzeitvertreib.app.ui.limits.LimitsUiState
import de.handyzeitvertreib.app.ui.onboarding.OnboardingActions
import de.handyzeitvertreib.app.ui.onboarding.OnboardingDraft
import de.handyzeitvertreib.app.ui.onboarding.OnboardingScreen
import de.handyzeitvertreib.app.ui.onboarding.OnboardingStep
import de.handyzeitvertreib.app.ui.onboarding.OnboardingUiState
import de.handyzeitvertreib.app.ui.settings.SettingsRoute
import de.handyzeitvertreib.app.ui.settings.SettingsViewModel
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

/**
 * Renders key screens to PNG for visual review. Skipped unless `-Dhzv.screenshots=<dir>`
 * is passed (see README), so normal test runs stay fast and write nothing.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "de-w393dp-h851dp-xxhdpi")
class ScreenshotRenderTest {
    @get:Rule
    val compose = createComposeRule()

    private val outDir = System.getProperty("hzv.screenshots")?.takeIf { it.isNotBlank() }?.let(::File)

    private fun render(
        name: String,
        theme: ThemeMode = ThemeMode.LIGHT,
        content: @Composable () -> Unit,
    ) {
        assumeTrue(outDir != null)
        compose.setContent { HzvTheme(themeMode = theme) { GlassBackground { content() } } }
        compose.waitForIdle()
        outDir!!.mkdirs()
        File(outDir, "$name.png").outputStream().use {
            compose
                .onRoot()
                .captureToImage()
                .asAndroidBitmap()
                .compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private val m = 60_000L

    private fun limit(
        id: Long,
        title: String,
        pkg: String?,
        used: Long,
        limit: Long,
        members: Int = 1,
    ) = LimitItem(
        key = LimitKey(if (pkg == null) LimitType.GROUP else LimitType.APP, id),
        title = title,
        packageName = pkg,
        memberCount = members,
        usedMs = used,
        effectiveLimitMs = limit,
        remainingMs = (limit - used).coerceAtLeast(0),
        progress = (used.toFloat() / limit).coerceAtMost(1f),
        reached = used >= limit,
        extended = false,
    )

    private val dashboard =
        DashboardUiState.Ready(
            totalMs = 154 * m,
            yesterdaySameTimeMs = 171 * m,
            topApps =
                listOf(
                    UsageItem("com.example.video", "Video", 52 * m, 6, 0.34f),
                    UsageItem("com.example.chat", "Chat", 38 * m, 21, 0.25f),
                    UsageItem("com.example.news", "Nachrichten", 24 * m, 4, 0.16f),
                    UsageItem("com.example.maps", "Karten", 12 * m, 2, 0.08f),
                ),
            limits =
                listOf(
                    limit(1, "Video", "com.example.video", 52 * m, 45 * m),
                    limit(2, "Soziale Netzwerke", null, 50 * m, 60 * m, members = 3),
                    limit(3, "Chat", "com.example.chat", 38 * m, 90 * m),
                ),
            limitedTotalMs = 90 * m,
            refreshedAt = 0,
            enforcementLevel = EnforcementLevel.NOTIFICATION,
        )

    @Test
    fun dashboardLight() = render("01-dashboard-light") { DashboardScreen(dashboard, DashboardActions()) }

    @Test
    fun dashboardDark() = render("02-dashboard-dark", ThemeMode.DARK) { DashboardScreen(dashboard, DashboardActions()) }

    @Test
    fun dashboardNoAccess() = render("03-dashboard-no-access") { DashboardScreen(DashboardUiState.AccessRequired, DashboardActions()) }

    private val regulation =
        RegulationUiState.Ready(
            key = LimitKey(LimitType.APP, 1),
            packageName = "com.example.video",
            title = "Video",
            usedMs = 45 * m,
            limitMs = 45 * m,
            remainingMs = 0,
            reached = true,
            reachedAt = null,
            extension = ExtensionAvailability.Available(5, 1),
        )

    @Test
    fun regulationLight() = render("04-regulation-light") { RegulationScreen(regulation, RegulationActions()) }

    @Test
    fun regulationDark() = render("05-regulation-dark", ThemeMode.DARK) { RegulationScreen(regulation, RegulationActions()) }

    @Test
    fun limits() =
        render("06-limits") {
            LimitsScreen(
                LimitsUiState(
                    loading = false,
                    usageAvailable = true,
                    appLimits =
                        listOf(
                            ConfiguredLimit(LimitKey(LimitType.APP, 3), "Chat", "com.example.chat", 1, 90, true, dashboard.limits[2]),
                            ConfiguredLimit(LimitKey(LimitType.APP, 1), "Video", "com.example.video", 1, 45, true, dashboard.limits[0]),
                            ConfiguredLimit(LimitKey(LimitType.APP, 4), "Spiele", "com.example.games", 1, 30, false, null),
                        ),
                    groupLimits = listOf(ConfiguredLimit(LimitKey(LimitType.GROUP, 2), "Soziale Netzwerke", null, 3, 60, true, dashboard.limits[1])),
                ),
                LimitsActions(),
                onToggle = { _, _ -> },
                contentPadding = PaddingValues(),
            )
        }

    @Test
    fun insightsWeek() =
        render("07-insights-week") {
            val end = LocalDate.of(2026, 9, 27)
            InsightsScreen(
                InsightsUiState(
                    loading = false,
                    mode = InsightsMode.WEEK,
                    anchor = end,
                    capturedDays = 6,
                    totalMs = 900 * m,
                    averagePerCapturedDayMs = 150 * m,
                    days = (6 downTo 0).map { DayUsageBar(end.minusDays(it.toLong()), listOf(0L, 120, 180, 95, 160, 210, 135)[6 - it] * m, it != 6) },
                    apps = dashboard.topApps,
                ),
                onModeChange = {},
                onPrevious = {},
                onNext = {},
                onOpenApp = {},
            )
        }

    private fun onboarding(step: OnboardingStep) =
        OnboardingUiState(
            OnboardingDraft(step = step, extensionPolicy = ExtensionPolicy.DEFAULT),
            PermissionSnapshot(GrantState.DENIED, GrantState.DENIED, GrantState.DENIED),
            emptyList(),
            AuthCapability.AVAILABLE,
        )

    @Test
    fun onboardingWelcome() = render("08-onboarding-welcome") { OnboardingScreen(onboarding(OnboardingStep.WELCOME), OnboardingActions()) }

    @Test
    fun onboardingUsageAccess() = render("09-onboarding-usage-access") { OnboardingScreen(onboarding(OnboardingStep.USAGE_ACCESS), OnboardingActions()) }

    @Test
    fun onboardingExtension() = render("10-onboarding-extension", ThemeMode.DARK) { OnboardingScreen(onboarding(OnboardingStep.EXTENSION), OnboardingActions()) }

    @Test
    fun limitEditor() =
        render("11-limit-editor") {
            AppLimitEditorScreen(
                AppLimitEditorState(loading = false, packageName = "com.example.video", label = "Video", existingId = 1, minutes = 45),
                {},
                {},
                {},
                {},
            )
        }

    @Test
    fun settings() {
        val app = ApplicationProvider.getApplicationContext<TestHzvApplication>()
        val viewModel = SettingsViewModel(app.container)
        render("12-settings") { SettingsRoute(viewModel, onNavigate = {}, contentPadding = PaddingValues()) }
    }
}
