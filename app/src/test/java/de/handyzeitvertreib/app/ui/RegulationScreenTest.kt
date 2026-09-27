package de.handyzeitvertreib.app.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import de.handyzeitvertreib.app.R
import de.handyzeitvertreib.app.core.model.LimitKey
import de.handyzeitvertreib.app.core.model.LimitType
import de.handyzeitvertreib.app.regulation.AuthCapability
import de.handyzeitvertreib.app.regulation.ExtensionAvailability
import de.handyzeitvertreib.app.regulation.ExtensionOutcome
import de.handyzeitvertreib.app.regulation.RegulationActions
import de.handyzeitvertreib.app.regulation.RegulationScreen
import de.handyzeitvertreib.app.regulation.RegulationUiState
import de.handyzeitvertreib.app.ui.designsystem.HzvTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RegulationScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun state(
        extension: ExtensionAvailability,
        outcome: ExtensionOutcome? = null,
    ) = RegulationUiState.Ready(
        key = LimitKey(LimitType.APP, 1),
        packageName = "com.example.video",
        title = "Video",
        usedMs = 30 * 60_000L,
        limitMs = 30 * 60_000L,
        remainingMs = 0,
        reached = true,
        reachedAt = null,
        extension = extension,
        outcome = outcome,
    )

    @Test
    fun reachedLimitShowsCalmCopyUsageAndExactExtension() {
        var extendRequested = false
        var left = false
        compose.setContent {
            HzvTheme {
                RegulationScreen(
                    state(ExtensionAvailability.Available(minutes = 10, remainingToday = 1)),
                    RegulationActions(onRequestExtension = { extendRequested = true }, onLeave = { left = true }),
                )
            }
        }
        compose.onNodeWithText(context.getString(R.string.regulation_title)).assertIsDisplayed()
        val thirty = context.getString(R.string.duration_minutes, 30)
        compose.onNodeWithText(context.getString(R.string.regulation_used_app, thirty, thirty)).assertIsDisplayed()
        val ten = context.getString(R.string.duration_minutes, 10)
        compose.onNodeWithText(context.getString(R.string.regulation_extension_button, ten)).performScrollTo().performClick()
        assertThat(extendRequested).isTrue()
        compose.onNodeWithTag("regulation-leave").performScrollTo().performClick()
        assertThat(left).isTrue()
    }

    @Test
    fun disabledCapAndMissingBiometricsAreExplained() {
        val states =
            listOf(
                state(ExtensionAvailability.DisabledByUser) to context.getString(R.string.regulation_extension_disabled),
                state(ExtensionAvailability.DailyCapReached(2)) to context.getString(R.string.regulation_extension_cap, 2),
                state(ExtensionAvailability.AuthenticationUnavailable(AuthCapability.NO_HARDWARE)) to
                    context.getString(R.string.auth_no_hardware),
            )
        var current by mutableStateOf(states.first().first)
        compose.setContent { HzvTheme { RegulationScreen(current, RegulationActions()) } }
        states.forEach { (state, text) ->
            current = state
            compose.waitForIdle()
            compose.onNodeWithText(text).performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("regulation-extend").assertDoesNotExist()
        }
    }

    @Test
    fun noEnrolledBiometricOffersSetUp() {
        var setUp = false
        compose.setContent {
            HzvTheme {
                RegulationScreen(
                    state(ExtensionAvailability.AuthenticationUnavailable(AuthCapability.NONE_ENROLLED)),
                    RegulationActions(onSetUpBiometrics = { setUp = true }),
                )
            }
        }
        compose.onNodeWithText(context.getString(R.string.action_set_up_biometrics)).performScrollTo().performClick()
        assertThat(setUp).isTrue()
    }

    @Test
    fun outcomesAreAnnounced() {
        compose.setContent {
            HzvTheme {
                RegulationScreen(state(ExtensionAvailability.Available(5, 1), ExtensionOutcome.CANCELLED), RegulationActions())
            }
        }
        compose.onNodeWithText(context.getString(R.string.regulation_outcome_cancelled)).assertIsDisplayed()
    }
}
