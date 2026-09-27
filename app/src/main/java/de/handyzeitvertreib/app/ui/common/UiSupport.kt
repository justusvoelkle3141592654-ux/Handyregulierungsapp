package de.handyzeitvertreib.app.ui.common

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.handyzeitvertreib.app.AppContainer
import de.handyzeitvertreib.app.R
import de.handyzeitvertreib.app.core.format.DurationFormatter
import de.handyzeitvertreib.app.core.format.DurationLabels
import java.util.Date

val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer not provided") }

@Composable
inline fun <reified VM : ViewModel> containerViewModel(
    key: String? = null,
    crossinline create: (AppContainer) -> VM,
): VM {
    val container = LocalAppContainer.current
    return viewModel(key = key, factory = viewModelFactory { initializer { create(container) } })
}

@Composable
fun durationLabels(): DurationLabels =
    DurationLabels(
        hoursMinutes = stringResource(R.string.duration_hours_minutes),
        hoursOnly = stringResource(R.string.duration_hours),
        minutesOnly = stringResource(R.string.duration_minutes),
        lessThanMinute = stringResource(R.string.duration_less_than_minute),
    )

@Composable
fun formatDuration(durationMs: Long): String = DurationFormatter.format(durationMs, durationLabels())

@Composable
fun formatMinutes(minutes: Int): String = formatDuration(DurationFormatter.minutesToMs(minutes))

fun formatTime(
    context: Context,
    timestampMs: Long,
): String = DateFormat.getTimeFormat(context).format(Date(timestampMs))

/** Intents to system settings. Some OEMs lack a screen, so every call has a fallback. */
object SystemSettings {
    fun openUsageAccess(context: Context) {
        val specific =
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) data = "package:${context.packageName}".toUri()
            }
        start(context, specific) || start(context, Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) || openAppDetails(context)
    }

    fun openAccessibility(context: Context) {
        start(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) || openAppDetails(context)
    }

    fun openNotificationSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        start(context, intent) || openAppDetails(context)
    }

    fun openBiometricEnrollment(context: Context) {
        val intent =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Intent(Settings.ACTION_BIOMETRIC_ENROLL)
            } else {
                Intent(Settings.ACTION_SECURITY_SETTINGS)
            }
        start(context, intent) || start(context, Intent(Settings.ACTION_SETTINGS))
    }

    fun goHome(context: Context) {
        start(context, Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
    }

    fun openAppDetails(context: Context): Boolean = start(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri()))

    private fun start(
        context: Context,
        intent: Intent,
    ): Boolean =
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
}
