package de.handyzeitvertreib.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.handyzeitvertreib.app.R
import de.handyzeitvertreib.app.core.model.ExtensionPolicy
import de.handyzeitvertreib.app.regulation.AuthCapability
import de.handyzeitvertreib.app.regulation.authUnavailableText
import de.handyzeitvertreib.app.ui.common.formatMinutes
import de.handyzeitvertreib.app.ui.designsystem.ChipTone
import de.handyzeitvertreib.app.ui.designsystem.GlassCard
import de.handyzeitvertreib.app.ui.designsystem.HzvTheme
import de.handyzeitvertreib.app.ui.designsystem.LimitStatusChip

@Composable
fun ExtensionPolicyEditor(
    policy: ExtensionPolicy,
    capability: AuthCapability,
    onChange: (ExtensionPolicy) -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassCard(modifier.fillMaxWidth()) {
        SwitchRow(
            title = stringResource(R.string.extension_enabled),
            body = stringResource(R.string.extension_enabled_body),
            checked = policy.enabled,
            onCheckedChange = { onChange(policy.copy(enabled = it)) },
        )
        if (policy.enabled) {
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.extension_duration), style = MaterialTheme.typography.titleSmall, color = HzvTheme.colors.textPrimary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExtensionPolicy.ALLOWED_MINUTES.forEach { minutes ->
                    FilterChip(
                        selected = policy.extensionMinutes == minutes,
                        onClick = { onChange(policy.copy(extensionMinutes = minutes)) },
                        label = { Text(formatMinutes(minutes)) },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.extension_max_per_day), style = MaterialTheme.typography.titleSmall, color = HzvTheme.colors.textPrimary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExtensionPolicy.ALLOWED_MAX_PER_DAY.forEach { count ->
                    FilterChip(
                        selected = policy.maxExtensionsPerDay == count,
                        onClick = { onChange(policy.copy(maxExtensionsPerDay = count)) },
                        label = { Text(stringResource(R.string.extension_times, count)) },
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            SwitchRow(
                title = stringResource(R.string.extension_device_credential),
                body = stringResource(R.string.extension_device_credential_body),
                checked = policy.allowDeviceCredential,
                onCheckedChange = { onChange(policy.copy(allowDeviceCredential = it)) },
            )
            Spacer(Modifier.height(12.dp))
            if (capability == AuthCapability.AVAILABLE) {
                LimitStatusChip(stringResource(R.string.auth_available), ChipTone.OK)
            } else {
                LimitStatusChip(stringResource(R.string.auth_not_available_short), ChipTone.WARNING)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(authUnavailableText(capability)), style = MaterialTheme.typography.bodySmall, color = HzvTheme.colors.textSecondary)
            }
        }
    }
}

@Composable
fun SwitchRow(
    title: String,
    body: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = HzvTheme.colors.textPrimary)
            if (body != null) Text(body, style = MaterialTheme.typography.bodyMedium, color = HzvTheme.colors.textSecondary)
        }
        Spacer(Modifier.height(0.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
