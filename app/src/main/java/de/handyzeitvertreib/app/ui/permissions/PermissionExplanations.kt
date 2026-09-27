package de.handyzeitvertreib.app.ui.permissions

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.handyzeitvertreib.app.R
import de.handyzeitvertreib.app.ui.designsystem.ChipTone
import de.handyzeitvertreib.app.ui.designsystem.GlassCard
import de.handyzeitvertreib.app.ui.designsystem.HzvTheme
import de.handyzeitvertreib.app.ui.designsystem.LimitStatusChip
import de.handyzeitvertreib.app.ui.designsystem.PrimaryButton

@Composable
fun BulletLine(
    text: String,
    positive: Boolean = true,
) {
    Row(Modifier.padding(vertical = 4.dp)) {
        Icon(
            if (positive) Icons.Outlined.Check else Icons.Outlined.Close,
            contentDescription = null,
            tint = if (positive) HzvTheme.colors.success else HzvTheme.colors.textSecondary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = HzvTheme.colors.textPrimary)
    }
}

@Composable
private fun StatusChip(granted: Boolean) {
    LimitStatusChip(
        stringResource(if (granted) R.string.permission_granted else R.string.permission_missing),
        if (granted) ChipTone.OK else ChipTone.WARNING,
    )
}

/** Education shown before sending the user to the usage-access system screen. */
@Composable
fun UsageAccessExplanation(
    granted: Boolean,
    onOpenSettings: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        GlassCard(Modifier.fillMaxWidth()) {
            StatusChip(granted)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.usage_access_what), style = MaterialTheme.typography.bodyLarge, color = HzvTheme.colors.textPrimary)
            Spacer(Modifier.height(8.dp))
            BulletLine(stringResource(R.string.usage_access_point_local))
            BulletLine(stringResource(R.string.usage_access_point_minimal))
            BulletLine(stringResource(R.string.usage_access_point_no_content), positive = false)
            BulletLine(stringResource(R.string.usage_access_point_no_upload), positive = false)
        }
        Spacer(Modifier.height(12.dp))
        GlassCard(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.usage_access_how_title), style = MaterialTheme.typography.titleMedium, color = HzvTheme.colors.textPrimary)
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.usage_access_how_body), style = MaterialTheme.typography.bodyMedium, color = HzvTheme.colors.textSecondary)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.usage_access_revoke), style = MaterialTheme.typography.bodyMedium, color = HzvTheme.colors.textSecondary)
        }
        Spacer(Modifier.height(12.dp))
        PrimaryButton(
            stringResource(if (granted) R.string.action_open_settings else R.string.action_open_usage_access),
            onClick = onOpenSettings,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Dedicated disclosure for the optional accessibility service. */
@Composable
fun AccessibilityExplanation(
    enabled: Boolean,
    onOpenSettings: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        GlassCard(Modifier.fillMaxWidth()) {
            StatusChip(enabled)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.accessibility_what), style = MaterialTheme.typography.bodyLarge, color = HzvTheme.colors.textPrimary)
            Spacer(Modifier.height(8.dp))
            BulletLine(stringResource(R.string.accessibility_point_observes))
            BulletLine(stringResource(R.string.accessibility_point_action))
            BulletLine(stringResource(R.string.accessibility_point_no_content), positive = false)
            BulletLine(stringResource(R.string.accessibility_point_no_storage), positive = false)
        }
        Spacer(Modifier.height(12.dp))
        GlassCard(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.accessibility_limits_title), style = MaterialTheme.typography.titleMedium, color = HzvTheme.colors.textPrimary)
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.accessibility_limits_body), style = MaterialTheme.typography.bodyMedium, color = HzvTheme.colors.textSecondary)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.accessibility_disable), style = MaterialTheme.typography.bodyMedium, color = HzvTheme.colors.textSecondary)
        }
        Spacer(Modifier.height(12.dp))
        PrimaryButton(
            stringResource(R.string.action_open_accessibility_settings),
            onClick = onOpenSettings,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
