package de.handyzeitvertreib.app.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.handyzeitvertreib.app.R
import de.handyzeitvertreib.app.core.model.UserPreferences
import de.handyzeitvertreib.app.permissions.GrantState
import de.handyzeitvertreib.app.ui.common.SystemSettings
import de.handyzeitvertreib.app.ui.designsystem.AppRow
import de.handyzeitvertreib.app.ui.designsystem.ChipTone
import de.handyzeitvertreib.app.ui.designsystem.GlassBackground
import de.handyzeitvertreib.app.ui.designsystem.GlassCard
import de.handyzeitvertreib.app.ui.designsystem.HzvSpacing
import de.handyzeitvertreib.app.ui.designsystem.HzvTheme
import de.handyzeitvertreib.app.ui.designsystem.LimitStatusChip
import de.handyzeitvertreib.app.ui.designsystem.LocalReduceMotion
import de.handyzeitvertreib.app.ui.designsystem.PrimaryButton
import de.handyzeitvertreib.app.ui.limits.DurationStepper
import de.handyzeitvertreib.app.ui.permissions.AccessibilityExplanation
import de.handyzeitvertreib.app.ui.permissions.BulletLine
import de.handyzeitvertreib.app.ui.permissions.UsageAccessExplanation
import de.handyzeitvertreib.app.ui.settings.ExtensionPolicyEditor

data class OnboardingActions(
    val onNext: () -> Unit = {},
    val onBack: () -> Unit = {},
    val onRetentionChange: (Int) -> Unit = {},
    val onToggleApp: (String) -> Unit = {},
    val onStarterMinutesChange: (Int) -> Unit = {},
    val onExtensionPolicyChange: (de.handyzeitvertreib.app.core.model.ExtensionPolicy) -> Unit = {},
    val onOpenUsageAccess: () -> Unit = {},
    val onOpenAccessibility: () -> Unit = {},
    val onRequestNotifications: () -> Unit = {},
    val onFinish: () -> Unit = {},
)

@Composable
fun OnboardingRoute(
    viewModel: OnboardingViewModel,
    onFinished: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val notificationLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { viewModel.refreshPermissions() }
    OnboardingScreen(
        state,
        OnboardingActions(
            onNext = viewModel::next,
            onBack = viewModel::back,
            onRetentionChange = viewModel::setRetention,
            onToggleApp = viewModel::toggleApp,
            onStarterMinutesChange = viewModel::setStarterMinutes,
            onExtensionPolicyChange = viewModel::setExtensionPolicy,
            onOpenUsageAccess = { SystemSettings.openUsageAccess(context) },
            onOpenAccessibility = { SystemSettings.openAccessibility(context) },
            onRequestNotifications = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    SystemSettings.openNotificationSettings(context)
                }
            },
            onFinish = { viewModel.finish(onFinished) },
        ),
    )
}

@Composable
fun OnboardingScreen(
    state: OnboardingUiState,
    actions: OnboardingActions,
) {
    val reduceMotion = LocalReduceMotion.current
    GlassBackground {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            LinearProgressIndicator(
                progress = { (state.stepIndex + 1f) / state.stepCount },
                modifier = Modifier.fillMaxWidth().padding(horizontal = HzvSpacing.screen, vertical = 12.dp),
                color = HzvTheme.colors.accent,
                trackColor = HzvTheme.colors.track,
            )
            AnimatedContent(
                targetState = state.step,
                transitionSpec = {
                    if (reduceMotion) {
                        fadeIn(snap()) togetherWith fadeOut(snap())
                    } else {
                        fadeIn(tween(220)) togetherWith fadeOut(tween(160))
                    }
                },
                modifier = Modifier.weight(1f),
                label = "onboarding-step",
            ) { step ->
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = HzvSpacing.screen, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(HzvSpacing.gap),
                ) {
                    StepContent(step, state, actions)
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(HzvSpacing.screen),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (state.stepIndex > 0) {
                    TextButton(onClick = actions.onBack, modifier = Modifier.testTag("onboarding-back")) { Text(stringResource(R.string.action_back)) }
                }
                Spacer(Modifier.weight(1f))
                if (state.step == OnboardingStep.DONE) {
                    PrimaryButton(
                        stringResource(R.string.onboarding_finish),
                        onClick = actions.onFinish,
                        enabled = !state.draft.finishing,
                        modifier = Modifier.testTag("onboarding-finish"),
                    )
                } else {
                    PrimaryButton(stringResource(nextLabel(state)), onClick = actions.onNext, modifier = Modifier.testTag("onboarding-next"))
                }
            }
        }
    }
}

private fun nextLabel(state: OnboardingUiState): Int =
    when (state.step) {
        OnboardingStep.WELCOME -> R.string.onboarding_start
        OnboardingStep.USAGE_ACCESS -> if (state.permissions.canReadUsage) R.string.action_next else R.string.onboarding_later
        OnboardingStep.NOTIFICATIONS -> if (state.permissions.notifications == GrantState.GRANTED) R.string.action_next else R.string.onboarding_later
        OnboardingStep.ENFORCEMENT -> if (state.permissions.accessibilityService == GrantState.GRANTED) R.string.action_next else R.string.onboarding_skip
        OnboardingStep.STARTER_APPS -> if (state.draft.selectedApps.isEmpty()) R.string.onboarding_skip else R.string.action_next
        else -> R.string.action_next
    }

@Composable
private fun StepTitle(
    title: String,
    body: String? = null,
) {
    Spacer(Modifier.height(8.dp))
    Text(title, style = MaterialTheme.typography.headlineMedium, color = HzvTheme.colors.textPrimary, modifier = Modifier.semantics { heading() }.testTag("onboarding-title"))
    if (body != null) Text(body, style = MaterialTheme.typography.bodyLarge, color = HzvTheme.colors.textSecondary)
}

@Composable
private fun StepContent(
    step: OnboardingStep,
    state: OnboardingUiState,
    actions: OnboardingActions,
) {
    when (step) {
        OnboardingStep.WELCOME -> {
            Spacer(Modifier.height(48.dp))
            Icon(Icons.Outlined.HourglassEmpty, contentDescription = null, tint = HzvTheme.colors.accent, modifier = Modifier.size(72.dp))
            StepTitle(stringResource(R.string.onboarding_welcome_title), stringResource(R.string.onboarding_welcome_body))
        }
        OnboardingStep.PURPOSE -> {
            StepTitle(stringResource(R.string.onboarding_purpose_title))
            GlassCard(Modifier.fillMaxWidth()) {
                BulletLine(stringResource(R.string.onboarding_purpose_see))
                BulletLine(stringResource(R.string.onboarding_purpose_limit))
                BulletLine(stringResource(R.string.onboarding_purpose_pause))
                BulletLine(stringResource(R.string.onboarding_purpose_extend))
            }
            Text(stringResource(R.string.onboarding_purpose_honest), style = MaterialTheme.typography.bodyMedium, color = HzvTheme.colors.textSecondary)
        }
        OnboardingStep.PRIVACY -> {
            StepTitle(stringResource(R.string.onboarding_privacy_title), stringResource(R.string.onboarding_privacy_body))
            GlassCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Lock, contentDescription = null, tint = HzvTheme.colors.accent)
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.privacy_mode_local), style = MaterialTheme.typography.titleMedium, color = HzvTheme.colors.textPrimary, modifier = Modifier.weight(1f))
                    LimitStatusChip(stringResource(R.string.privacy_mode_active), ChipTone.OK)
                }
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.privacy_mode_local_body), style = MaterialTheme.typography.bodyMedium, color = HzvTheme.colors.textSecondary)
            }
            GlassCard(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.privacy_retention), style = MaterialTheme.typography.titleMedium, color = HzvTheme.colors.textPrimary)
                Text(stringResource(R.string.privacy_retention_body), style = MaterialTheme.typography.bodyMedium, color = HzvTheme.colors.textSecondary)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    UserPreferences.RETENTION_CHOICES.forEach { days ->
                        FilterChip(
                            selected = state.draft.retentionDays == days,
                            onClick = { actions.onRetentionChange(days) },
                            label = { Text(stringResource(R.string.privacy_retention_days, days)) },
                        )
                    }
                }
            }
        }
        OnboardingStep.USAGE_ACCESS -> {
            StepTitle(stringResource(R.string.usage_access_title))
            UsageAccessExplanation(state.permissions.canReadUsage, actions.onOpenUsageAccess)
            if (!state.permissions.canReadUsage) {
                Text(stringResource(R.string.onboarding_usage_access_later), style = MaterialTheme.typography.bodySmall, color = HzvTheme.colors.textSecondary)
            }
        }
        OnboardingStep.NOTIFICATIONS -> {
            val granted = state.permissions.notifications == GrantState.GRANTED
            StepTitle(stringResource(R.string.onboarding_notifications_title), stringResource(R.string.onboarding_notifications_body))
            GlassCard(Modifier.fillMaxWidth()) {
                LimitStatusChip(stringResource(if (granted) R.string.permission_granted else R.string.permission_missing), if (granted) ChipTone.OK else ChipTone.WARNING)
                Spacer(Modifier.height(8.dp))
                BulletLine(stringResource(R.string.onboarding_notifications_once))
                BulletLine(stringResource(R.string.onboarding_notifications_no_ads), positive = false)
                if (!granted) {
                    Spacer(Modifier.height(12.dp))
                    PrimaryButton(stringResource(R.string.action_allow_notifications), actions.onRequestNotifications, Modifier.fillMaxWidth())
                }
            }
        }
        OnboardingStep.ENFORCEMENT -> {
            StepTitle(stringResource(R.string.onboarding_enforcement_title), stringResource(R.string.onboarding_enforcement_body))
            AccessibilityExplanation(state.permissions.accessibilityService == GrantState.GRANTED, actions.onOpenAccessibility)
        }
        OnboardingStep.ACCOUNT -> {
            StepTitle(stringResource(R.string.onboarding_account_title), stringResource(R.string.onboarding_account_body))
            GlassCard(Modifier.fillMaxWidth()) {
                LimitStatusChip(stringResource(R.string.account_not_configured_chip), ChipTone.NEUTRAL)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.account_not_configured_body), style = MaterialTheme.typography.bodyMedium, color = HzvTheme.colors.textSecondary)
            }
        }
        OnboardingStep.STARTER_APPS -> {
            StepTitle(stringResource(R.string.onboarding_apps_title), stringResource(R.string.onboarding_apps_body))
            GlassCard(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.onboarding_apps_default_limit), style = MaterialTheme.typography.titleMedium, color = HzvTheme.colors.textPrimary)
                Spacer(Modifier.height(8.dp))
                DurationStepper(state.draft.starterMinutes, actions.onStarterMinutesChange)
            }
            GlassCard(Modifier.fillMaxWidth(), contentPadding = 8.dp) {
                if (state.starterApps.isEmpty()) {
                    Text(stringResource(R.string.onboarding_apps_loading), style = MaterialTheme.typography.bodyMedium, color = HzvTheme.colors.textSecondary, modifier = Modifier.padding(12.dp))
                }
                state.starterApps.take(STARTER_APP_COUNT).forEach { app ->
                    AppRow(
                        packageName = app.packageName,
                        label = app.label,
                        supporting = null,
                        onClick = { actions.onToggleApp(app.packageName) },
                        trailing = { Checkbox(checked = app.packageName in state.draft.selectedApps, onCheckedChange = { actions.onToggleApp(app.packageName) }) },
                    )
                }
            }
        }
        OnboardingStep.EXTENSION -> {
            StepTitle(stringResource(R.string.onboarding_extension_title), stringResource(R.string.onboarding_extension_body))
            ExtensionPolicyEditor(state.draft.extensionPolicy, state.authCapability, actions.onExtensionPolicyChange)
        }
        OnboardingStep.DONE -> {
            Spacer(Modifier.height(32.dp))
            StepTitle(stringResource(R.string.onboarding_done_title), stringResource(R.string.onboarding_done_body))
            GlassCard(Modifier.fillMaxWidth()) {
                BulletLine(stringResource(R.string.onboarding_done_usage), positive = state.permissions.canReadUsage)
                BulletLine(stringResource(R.string.onboarding_done_notifications), positive = state.permissions.notifications == GrantState.GRANTED)
                BulletLine(stringResource(R.string.onboarding_done_enforcement), positive = state.permissions.accessibilityService == GrantState.GRANTED)
                BulletLine(stringResource(R.string.onboarding_done_limits, state.draft.selectedApps.size), positive = state.draft.selectedApps.isNotEmpty())
            }
            Text(stringResource(R.string.onboarding_done_changeable), style = MaterialTheme.typography.bodyMedium, color = HzvTheme.colors.textSecondary)
        }
    }
}

private const val STARTER_APP_COUNT = 30
