package de.handyzeitvertreib.app.ui

import androidx.fragment.app.FragmentActivity
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import de.handyzeitvertreib.app.biometric.AuthRequest
import de.handyzeitvertreib.app.biometric.AuthResult
import de.handyzeitvertreib.app.core.model.LimitKey
import de.handyzeitvertreib.app.core.model.LimitType
import de.handyzeitvertreib.app.core.model.RegulationAction
import de.handyzeitvertreib.app.regulation.AuthCapability
import de.handyzeitvertreib.app.regulation.ExtensionAvailability
import de.handyzeitvertreib.app.regulation.ExtensionOutcome
import de.handyzeitvertreib.app.regulation.RegulationUiState
import de.handyzeitvertreib.app.regulation.RegulationViewModel
import de.handyzeitvertreib.app.testing.HOUR
import de.handyzeitvertreib.app.testing.MINUTE
import de.handyzeitvertreib.app.testing.TestHzvApplication
import de.handyzeitvertreib.app.testing.awaitCondition
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RegulationViewModelTest {
    private lateinit var app: TestHzvApplication
    private lateinit var activity: FragmentActivity
    private lateinit var key: LimitKey

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
        app.events.session("com.example.video", app.clock.startOfToday() + HOUR, 31 * MINUTE)
        key = LimitKey(LimitType.APP, runBlocking { app.container.limitRepository.saveAppLimit("com.example.video", 30) })
    }

    private fun readyViewModel(): RegulationViewModel {
        val viewModel = RegulationViewModel(app.container)
        viewModel.bind(key, "com.example.video")
        awaitCondition { viewModel.state.value is RegulationUiState.Ready }
        return viewModel
    }

    private fun ready(viewModel: RegulationViewModel) = viewModel.state.value as RegulationUiState.Ready

    private fun request(minutes: Int) = AuthRequest("t$minutes", "s", "cancel", allowDeviceCredential = false)

    private fun actions() =
        runBlocking {
            app.container.regulationRepository
                .eventsFor(app.clock.today())
                .map { it.action }
        }

    @Test
    fun successfulAuthenticationGrantsExactlyTheConfiguredMinutes() {
        val viewModel = readyViewModel()
        assertThat(ready(viewModel).reached).isTrue()
        assertThat(ready(viewModel).extension).isEqualTo(ExtensionAvailability.Available(5, 1))

        viewModel.requestExtension(activity, ::request)
        awaitCondition { ready(viewModel).outcome == ExtensionOutcome.GRANTED }

        assertThat(
            app.authenticator.requests
                .single()
                .title,
        ).isEqualTo("t5")
        assertThat(ready(viewModel).reached).isFalse()
        assertThat(ready(viewModel).remainingMs).isEqualTo(4 * MINUTE)
        assertThat(actions()).contains(RegulationAction.EXTENSION_GRANTED)
    }

    @Test
    fun dailyCapIsReachedAfterOneExtension() {
        val viewModel = readyViewModel()
        viewModel.requestExtension(activity, ::request)
        awaitCondition { ready(viewModel).outcome == ExtensionOutcome.GRANTED }
        app.clock.now += 10 * MINUTE
        app.events.session("com.example.video", app.clock.now - 5 * MINUTE, 5 * MINUTE)
        viewModel.load()
        awaitCondition { ready(viewModel).reached }
        assertThat(ready(viewModel).extension).isEqualTo(ExtensionAvailability.DailyCapReached(1))
    }

    @Test
    fun cancelledAuthenticationAddsNoTime() {
        app.authenticator.result = AuthResult.Cancelled
        val viewModel = readyViewModel()
        viewModel.requestExtension(activity, ::request)
        awaitCondition { ready(viewModel).outcome == ExtensionOutcome.CANCELLED }
        assertThat(ready(viewModel).reached).isTrue()
        assertThat(actions()).doesNotContain(RegulationAction.EXTENSION_GRANTED)
        assertThat(actions()).contains(RegulationAction.EXTENSION_CANCELLED)
    }

    @Test
    fun lockoutAndFailureAddNoTime() {
        app.authenticator.result = AuthResult.LockedOut(permanent = true)
        val viewModel = readyViewModel()
        viewModel.requestExtension(activity, ::request)
        awaitCondition { ready(viewModel).outcome == ExtensionOutcome.LOCKED_OUT_PERMANENT }
        app.authenticator.result = AuthResult.Failed(7)
        viewModel.requestExtension(activity, ::request)
        awaitCondition { ready(viewModel).outcome == ExtensionOutcome.FAILED }
        assertThat(ready(viewModel).reached).isTrue()
        assertThat(actions()).doesNotContain(RegulationAction.EXTENSION_GRANTED)
    }

    @Test
    fun unavailableBiometricsNeverPromptsAndNeverGrants() {
        app.authenticator.capability = AuthCapability.NONE_ENROLLED
        val viewModel = readyViewModel()
        assertThat(ready(viewModel).extension).isEqualTo(ExtensionAvailability.AuthenticationUnavailable(AuthCapability.NONE_ENROLLED))
        viewModel.requestExtension(activity, ::request)
        awaitCondition { true }
        assertThat(app.authenticator.requests).isEmpty()
        assertThat(actions()).doesNotContain(RegulationAction.EXTENSION_GRANTED)
    }

    @Test
    fun deletedLimitIsUnavailable() {
        runBlocking { app.container.limitRepository.deleteAppLimit(key.id) }
        val viewModel = RegulationViewModel(app.container)
        viewModel.bind(key, "com.example.video")
        awaitCondition { viewModel.state.value == RegulationUiState.Unavailable }
    }
}
