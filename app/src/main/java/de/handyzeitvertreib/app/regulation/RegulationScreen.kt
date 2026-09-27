package de.handyzeitvertreib.app.regulation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.handyzeitvertreib.app.R
import de.handyzeitvertreib.app.ui.common.formatDuration
import de.handyzeitvertreib.app.ui.common.formatMinutes
import de.handyzeitvertreib.app.ui.common.formatTime
import de.handyzeitvertreib.app.ui.designsystem.AppIcon
import de.handyzeitvertreib.app.ui.designsystem.GlassBackground
import de.handyzeitvertreib.app.ui.designsystem.GlassCard
import de.handyzeitvertreib.app.ui.designsystem.HzvSpacing
import de.handyzeitvertreib.app.ui.designsystem.HzvTheme
import de.handyzeitvertreib.app.ui.designsystem.PrimaryButton
import de.handyzeitvertreib.app.ui.designsystem.ProgressRing
import de.handyzeitvertreib.app.ui.designsystem.SecondaryButton
import de.handyzeitvertreib.app.ui.designsystem.TabularNumbers

data class RegulationActions(
    val onLeave: () -> Unit = {},
    val onReviewLimits: () -> Unit = {},
    val onRequestExtension: () -> Unit = {},
    val onSetUpBiometrics: () -> Unit = {},
    val onReturnToApp: () -> Unit = {},
)

@Composable
fun RegulationScreen(
    state: RegulationUiState,
    actions: RegulationActions,
) {
    GlassBackground {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(HzvSpacing.screen),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(HzvSpacing.gap),
        ) {
            when (state) {
                RegulationUiState.Loading -> Box(Modifier.fillMaxWidth().padding(64.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                RegulationUiState.Unavailable -> UnavailableContent(actions)
                is RegulationUiState.Ready -> ReadyContent(state, actions)
            }
        }
    }
}

@Composable
private fun UnavailableContent(actions: RegulationActions) {
    Spacer(Modifier.height(48.dp))
    Text(stringResource(R.string.regulation_unavailable_title), style = MaterialTheme.typography.headlineSmall, color = HzvTheme.colors.textPrimary, textAlign = TextAlign.Center)
    Text(stringResource(R.string.regulation_unavailable_body), style = MaterialTheme.typography.bodyLarge, color = HzvTheme.colors.textSecondary, textAlign = TextAlign.Center)
    Spacer(Modifier.height(16.dp))
    PrimaryButton(stringResource(R.string.regulation_review_limits), actions.onReviewLimits, Modifier.fillMaxWidth())
}

@Composable
private fun ReadyContent(
    state: RegulationUiState.Ready,
    actions: RegulationActions,
) {
    val context = LocalContext.current
    Spacer(Modifier.height(24.dp))
    ProgressRing(progress = if (state.limitMs > 0) state.usedMs.toFloat() / state.limitMs else 1f, size = 120.dp, stroke = 10.dp, reached = state.reached) {
        if (state.packageName != null) AppIcon(state.packageName, state.title, size = 56.dp)
    }
    Text(state.title, style = MaterialTheme.typography.titleMedium, color = HzvTheme.colors.textSecondary, textAlign = TextAlign.Center)
    Text(
        stringResource(if (state.reached) R.string.regulation_title else R.string.regulation_not_reached_title),
        style = MaterialTheme.typography.headlineMedium,
        color = HzvTheme.colors.textPrimary,
        textAlign = TextAlign.Center,
        modifier = Modifier.semantics { heading() }.testTag("regulation-title"),
    )
    GlassCard(Modifier.fillMaxWidth(), strong = true) {
        Text(
            stringResource(
                if (state.isGroup) R.string.regulation_used_group else R.string.regulation_used_app,
                formatDuration(state.usedMs),
                formatDuration(state.limitMs),
            ),
            style = MaterialTheme.typography.bodyLarge.merge(TabularNumbers),
            color = HzvTheme.colors.textPrimary,
        )
        if (!state.reached) {
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.regulation_remaining, formatDuration(state.remainingMs)), style = MaterialTheme.typography.bodyMedium, color = HzvTheme.colors.textSecondary)
        }
        state.reachedAt?.let {
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.regulation_reached_at, formatTime(context, it)), style = MaterialTheme.typography.bodyMedium, color = HzvTheme.colors.textSecondary)
        }
        if (state.reached) {
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.regulation_choices), style = MaterialTheme.typography.bodyMedium, color = HzvTheme.colors.textSecondary)
        }
    }
    state.outcome?.let { OutcomeMessage(it) }

    PrimaryButton(
        stringResource(R.string.regulation_leave),
        onClick = actions.onLeave,
        icon = Icons.Outlined.Home,
        modifier = Modifier.fillMaxWidth().testTag("regulation-leave"),
    )
    SecondaryButton(stringResource(R.string.regulation_review_limits), onClick = actions.onReviewLimits, icon = Icons.Outlined.Tune, modifier = Modifier.fillMaxWidth())
    if (state.reached) {
        ExtensionSection(state, actions)
    } else if (state.outcome == ExtensionOutcome.GRANTED && state.packageName != null) {
        TextButton(onClick = actions.onReturnToApp) { Text(stringResource(R.string.regulation_return_to_app, state.title)) }
    }
}

@Composable
private fun ExtensionSection(
    state: RegulationUiState.Ready,
    actions: RegulationActions,
) {
    GlassCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.regulation_extension_title), style = MaterialTheme.typography.titleMedium, color = HzvTheme.colors.textPrimary)
        Spacer(Modifier.height(4.dp))
        when (val extension = state.extension) {
            is ExtensionAvailability.Available -> {
                Text(
                    stringResource(R.string.regulation_extension_body, formatMinutes(extension.minutes), extension.remainingToday),
                    style = MaterialTheme.typography.bodyMedium,
                    color = HzvTheme.colors.textSecondary,
                )
                Spacer(Modifier.height(12.dp))
                SecondaryButton(
                    stringResource(R.string.regulation_extension_button, formatMinutes(extension.minutes)),
                    onClick = actions.onRequestExtension,
                    enabled = !state.authenticating,
                    icon = Icons.Outlined.Fingerprint,
                    modifier = Modifier.fillMaxWidth().testTag("regulation-extend"),
                )
            }
            ExtensionAvailability.DisabledByUser ->
                Text(stringResource(R.string.regulation_extension_disabled), style = MaterialTheme.typography.bodyMedium, color = HzvTheme.colors.textSecondary)
            is ExtensionAvailability.DailyCapReached ->
                Text(
                    stringResource(R.string.regulation_extension_cap, extension.maxPerDay),
                    style = MaterialTheme.typography.bodyMedium,
                    color = HzvTheme.colors.textSecondary,
                )
            is ExtensionAvailability.AuthenticationUnavailable -> {
                Text(
                    stringResource(authUnavailableText(extension.capability)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = HzvTheme.colors.textSecondary,
                )
                if (extension.capability == AuthCapability.NONE_ENROLLED) {
                    TextButton(onClick = actions.onSetUpBiometrics) { Text(stringResource(R.string.action_set_up_biometrics)) }
                }
            }
        }
    }
}

@Composable
private fun OutcomeMessage(outcome: ExtensionOutcome) {
    val text =
        when (outcome) {
            ExtensionOutcome.GRANTED -> R.string.regulation_outcome_granted
            ExtensionOutcome.CANCELLED -> R.string.regulation_outcome_cancelled
            ExtensionOutcome.LOCKED_OUT -> R.string.regulation_outcome_locked
            ExtensionOutcome.LOCKED_OUT_PERMANENT -> R.string.regulation_outcome_locked_permanent
            ExtensionOutcome.NOT_AVAILABLE -> R.string.regulation_outcome_not_available
            ExtensionOutcome.FAILED -> R.string.regulation_outcome_failed
        }
    Text(
        stringResource(text),
        style = MaterialTheme.typography.bodyMedium,
        color = if (outcome == ExtensionOutcome.GRANTED) HzvTheme.colors.success else HzvTheme.colors.warning,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }.testTag("regulation-outcome"),
    )
}

fun authUnavailableText(capability: AuthCapability): Int =
    when (capability) {
        AuthCapability.NONE_ENROLLED -> R.string.auth_none_enrolled
        AuthCapability.NO_HARDWARE -> R.string.auth_no_hardware
        AuthCapability.HARDWARE_UNAVAILABLE -> R.string.auth_hw_unavailable
        AuthCapability.SECURITY_UPDATE_REQUIRED -> R.string.auth_security_update
        AuthCapability.UNSUPPORTED, AuthCapability.UNKNOWN, AuthCapability.AVAILABLE -> R.string.auth_unsupported
    }
