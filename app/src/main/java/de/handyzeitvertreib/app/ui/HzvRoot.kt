package de.handyzeitvertreib.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.handyzeitvertreib.app.AppContainer
import de.handyzeitvertreib.app.core.model.UserPreferences
import de.handyzeitvertreib.app.ui.common.LocalAppContainer
import de.handyzeitvertreib.app.ui.common.containerViewModel
import de.handyzeitvertreib.app.ui.designsystem.GlassBackground
import de.handyzeitvertreib.app.ui.designsystem.HzvTheme
import de.handyzeitvertreib.app.ui.navigation.HzvMainScaffold
import de.handyzeitvertreib.app.ui.navigation.TopLevel
import de.handyzeitvertreib.app.ui.onboarding.OnboardingRoute
import de.handyzeitvertreib.app.ui.onboarding.OnboardingViewModel
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Root composable. Refreshes permissions and usage whenever the app returns to the
 * foreground, e.g. after the user granted usage access in system settings.
 */
@Composable
fun HzvRoot(
    container: AppContainer,
    startDestination: TopLevel = TopLevel.TODAY,
) {
    val prefsFlow = remember(container) { container.preferencesRepository.preferences.map<UserPreferences, UserPreferences?> { it } }
    val prefs by prefsFlow.collectAsStateWithLifecycle(null)
    val scope = rememberCoroutineScope()
    LifecycleResumeEffect(container) {
        container.permissionMonitor.refresh()
        scope.launch { container.usageRepository.refresh() }
        onPauseOrDispose { }
    }
    val current = prefs
    HzvTheme(
        themeMode = current?.themeMode ?: UserPreferences.DEFAULT.themeMode,
        dynamicColor = current?.dynamicColor ?: false,
        reduceMotion = current?.reduceMotion ?: false,
    ) {
        CompositionLocalProvider(LocalAppContainer provides container) {
            GlassBackground {
                when {
                    current == null -> Unit
                    !current.onboardingCompleted -> OnboardingRoute(containerViewModel(key = "onboarding") { OnboardingViewModel(it) }, onFinished = {})
                    else -> HzvMainScaffold(startDestination, onDataReset = {})
                }
            }
        }
    }
}
