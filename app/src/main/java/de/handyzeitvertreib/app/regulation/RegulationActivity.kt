package de.handyzeitvertreib.app.regulation

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.handyzeitvertreib.app.HzvApplication
import de.handyzeitvertreib.app.MainActivity
import de.handyzeitvertreib.app.R
import de.handyzeitvertreib.app.biometric.AuthRequest
import de.handyzeitvertreib.app.core.model.LimitKey
import de.handyzeitvertreib.app.core.model.LimitType
import de.handyzeitvertreib.app.core.model.UserPreferences
import de.handyzeitvertreib.app.ui.common.SystemSettings
import de.handyzeitvertreib.app.ui.designsystem.HzvTheme
import kotlinx.coroutines.flow.map

/**
 * Full-screen regulation experience in its own task, so "back" cannot simply reveal the
 * limited app again. Leaving goes to the home screen.
 */
class RegulationActivity : FragmentActivity() {
    private val viewModel: RegulationViewModel by viewModels {
        viewModelFactory { initializer { RegulationViewModel((application as HzvApplication).container) } }
    }

    private val packageNameExtra: String? get() = intent.getStringExtra(EXTRA_PACKAGE)

    private fun bindIntent() {
        val key =
            LimitKey(
                LimitType.entries.getOrElse(intent.getIntExtra(EXTRA_TYPE, 0)) { LimitType.APP },
                intent.getLongExtra(EXTRA_ID, -1),
            )
        viewModel.bind(key, packageNameExtra)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = leave()
            },
        )
        bindIntent()
        val prefsFlow =
            (application as HzvApplication)
                .container.preferencesRepository.preferences
                .map<UserPreferences, UserPreferences?> { it }
        setContent {
            val prefs by prefsFlow.collectAsStateWithLifecycle(null)
            val state by viewModel.state.collectAsStateWithLifecycle()
            HzvTheme(
                themeMode = prefs?.themeMode ?: UserPreferences.DEFAULT.themeMode,
                dynamicColor = prefs?.dynamicColor ?: false,
                reduceMotion = prefs?.reduceMotion ?: false,
            ) {
                RegulationScreen(
                    state = state,
                    actions =
                        RegulationActions(
                            onLeave = ::leave,
                            onReviewLimits = {
                                startActivity(MainActivity.limitsIntent(this))
                                finish()
                            },
                            onRequestExtension = {
                                viewModel.requestExtension(this) { minutes ->
                                    AuthRequest(
                                        title = getString(R.string.auth_prompt_title, minutes),
                                        subtitle = getString(R.string.auth_prompt_subtitle),
                                        negativeButton = getString(R.string.action_cancel),
                                        allowDeviceCredential = false,
                                    )
                                }
                            },
                            onSetUpBiometrics = { SystemSettings.openBiometricEnrollment(this) },
                            onReturnToApp = ::returnToApp,
                        ),
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        bindIntent()
    }

    override fun onRestart() {
        super.onRestart()
        viewModel.load()
    }

    private fun leave() {
        viewModel.recordLeft()
        SystemSettings.goHome(this)
        finish()
    }

    private fun returnToApp() {
        val launch = packageNameExtra?.let { packageManager.getLaunchIntentForPackage(it) }
        if (launch != null) startActivity(launch)
        finish()
    }

    companion object {
        private const val EXTRA_TYPE = "limit_type"
        private const val EXTRA_ID = "limit_id"
        private const val EXTRA_PACKAGE = "package_name"

        fun intent(
            context: Context,
            key: LimitKey,
            packageName: String?,
        ): Intent =
            Intent(context, RegulationActivity::class.java)
                .putExtra(EXTRA_TYPE, key.type.ordinal)
                .putExtra(EXTRA_ID, key.id)
                .putExtra(EXTRA_PACKAGE, packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
}
