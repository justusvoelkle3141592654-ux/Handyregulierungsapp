package de.handyzeitvertreib.app.ui.settings

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.handyzeitvertreib.app.BuildConfig
import de.handyzeitvertreib.app.R
import de.handyzeitvertreib.app.account.AccountState
import de.handyzeitvertreib.app.account.AccountStateMapping
import de.handyzeitvertreib.app.core.model.ExtensionPolicy
import de.handyzeitvertreib.app.core.model.ThemeMode
import de.handyzeitvertreib.app.core.model.UserPreferences
import de.handyzeitvertreib.app.enforcement.EnforcementLevel
import de.handyzeitvertreib.app.permissions.GrantState
import de.handyzeitvertreib.app.ui.common.SystemSettings
import de.handyzeitvertreib.app.ui.designsystem.ChipTone
import de.handyzeitvertreib.app.ui.designsystem.ConfirmDialog
import de.handyzeitvertreib.app.ui.designsystem.GlassCard
import de.handyzeitvertreib.app.ui.designsystem.HzvSpacing
import de.handyzeitvertreib.app.ui.designsystem.HzvTheme
import de.handyzeitvertreib.app.ui.designsystem.LimitStatusChip
import de.handyzeitvertreib.app.ui.designsystem.PermissionStatusCard
import de.handyzeitvertreib.app.ui.designsystem.PrimaryButton
import de.handyzeitvertreib.app.ui.designsystem.SecondaryButton
import de.handyzeitvertreib.app.ui.designsystem.SectionHeader
import de.handyzeitvertreib.app.ui.permissions.AccessibilityExplanation
import de.handyzeitvertreib.app.ui.permissions.BulletLine
import de.handyzeitvertreib.app.ui.permissions.UsageAccessExplanation

enum class SettingsDestination { PERMISSIONS, ACCOUNT, PRIVACY, EXTENSION, DATA, HELP, USAGE_ACCESS, ACCESSIBILITY }

@Composable
private fun ScrollPage(
    contentPadding: PaddingValues,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = HzvSpacing.screen, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(HzvSpacing.gap),
        content = content,
    )
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = HzvTheme.colors.accent)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = HzvTheme.colors.textPrimary)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = HzvTheme.colors.textSecondary)
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = HzvTheme.colors.textSecondary)
    }
}

@Composable
fun enforcementLevelText(level: EnforcementLevel): String =
    stringResource(
        when (level) {
            EnforcementLevel.NONE -> R.string.enforcement_level_none
            EnforcementLevel.IN_APP_ONLY -> R.string.enforcement_level_in_app
            EnforcementLevel.NOTIFICATION -> R.string.enforcement_level_notification
            EnforcementLevel.SCREEN_ON_APP_OPEN -> R.string.enforcement_level_screen
        },
    )

@Composable
fun SettingsRoute(
    viewModel: SettingsViewModel,
    onNavigate: (SettingsDestination) -> Unit,
    contentPadding: PaddingValues,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val prefs = state.preferences
    ScrollPage(contentPadding) {
        Text(
            stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineMedium,
            color = HzvTheme.colors.textPrimary,
            modifier = Modifier.semantics { heading() },
        )
        GlassCard(Modifier.fillMaxWidth(), contentPadding = 12.dp) {
            SettingsRow(Icons.Outlined.Security, stringResource(R.string.settings_permissions), enforcementLevelText(state.enforcementLevel)) {
                onNavigate(SettingsDestination.PERMISSIONS)
            }
            HorizontalDivider(color = HzvTheme.colors.track)
            SettingsRow(Icons.Outlined.AccountCircle, stringResource(R.string.settings_account), accountSummary(state.account)) { onNavigate(SettingsDestination.ACCOUNT) }
            HorizontalDivider(color = HzvTheme.colors.track)
            SettingsRow(Icons.Outlined.Lock, stringResource(R.string.settings_privacy), stringResource(R.string.privacy_mode_local)) { onNavigate(SettingsDestination.PRIVACY) }
            HorizontalDivider(color = HzvTheme.colors.track)
            SettingsRow(
                Icons.Outlined.Fingerprint,
                stringResource(R.string.settings_extension),
                if (prefs.extensionPolicy.enabled) {
                    stringResource(R.string.settings_extension_on, prefs.extensionPolicy.extensionMinutes, prefs.extensionPolicy.maxExtensionsPerDay)
                } else {
                    stringResource(R.string.app_detail_extension_disabled)
                },
            ) { onNavigate(SettingsDestination.EXTENSION) }
            HorizontalDivider(color = HzvTheme.colors.track)
            SettingsRow(Icons.Outlined.Storage, stringResource(R.string.settings_data), stringResource(R.string.settings_data_subtitle)) { onNavigate(SettingsDestination.DATA) }
            HorizontalDivider(color = HzvTheme.colors.track)
            SettingsRow(Icons.AutoMirrored.Outlined.HelpOutline, stringResource(R.string.settings_help), stringResource(R.string.settings_help_subtitle)) {
                onNavigate(SettingsDestination.HELP)
            }
        }
        SectionHeader(stringResource(R.string.settings_notifications))
        GlassCard(Modifier.fillMaxWidth()) {
            SwitchRow(
                stringResource(R.string.settings_limit_notifications),
                stringResource(R.string.settings_limit_notifications_body),
                prefs.limitNotificationsEnabled,
                viewModel::setNotifications,
            )
            if (prefs.limitNotificationsEnabled && state.permissions.notifications != GrantState.GRANTED) {
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.settings_notifications_blocked), style = MaterialTheme.typography.bodySmall, color = HzvTheme.colors.warning)
            }
        }
        SectionHeader(stringResource(R.string.settings_appearance))
        GlassCard(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.titleMedium, color = HzvTheme.colors.textPrimary)
            Spacer(Modifier.height(8.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                ThemeMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = prefs.themeMode == mode,
                        onClick = { viewModel.setThemeMode(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size),
                    ) {
                        Text(
                            stringResource(
                                when (mode) {
                                    ThemeMode.SYSTEM -> R.string.theme_system
                                    ThemeMode.LIGHT -> R.string.theme_light
                                    ThemeMode.DARK -> R.string.theme_dark
                                },
                            ),
                        )
                    }
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Spacer(Modifier.height(16.dp))
                SwitchRow(stringResource(R.string.settings_dynamic_color), stringResource(R.string.settings_dynamic_color_body), prefs.dynamicColor, viewModel::setDynamicColor)
            }
            Spacer(Modifier.height(16.dp))
            SwitchRow(stringResource(R.string.settings_reduce_motion), stringResource(R.string.settings_reduce_motion_body), prefs.reduceMotion, viewModel::setReduceMotion)
        }
        Text(
            stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.bodySmall,
            color = HzvTheme.colors.textSecondary,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun accountSummary(state: AccountState): String =
    when (state) {
        AccountState.NotConfigured -> stringResource(R.string.account_summary_local)
        AccountState.SignedOut -> stringResource(R.string.account_summary_signed_out)
        AccountState.Loading -> stringResource(R.string.account_summary_loading)
        is AccountState.SignedIn -> state.displayName
        is AccountState.Error -> stringResource(R.string.account_summary_error)
    }

@Composable
fun PermissionsSettingsScreen(
    viewModel: SettingsViewModel,
    onNavigate: (SettingsDestination) -> Unit,
    contentPadding: PaddingValues,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permissions = state.permissions
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { viewModel.refreshPermissions() }
    ScrollPage(contentPadding) {
        GlassCard(Modifier.fillMaxWidth(), strong = true) {
            Text(stringResource(R.string.enforcement_current), style = MaterialTheme.typography.labelLarge, color = HzvTheme.colors.textSecondary)
            Text(enforcementLevelText(state.enforcementLevel), style = MaterialTheme.typography.titleMedium, color = HzvTheme.colors.textPrimary)
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.enforcement_no_guarantee), style = MaterialTheme.typography.bodySmall, color = HzvTheme.colors.textSecondary)
        }
        val grantedLabel = stringResource(R.string.permission_granted)
        val missingLabel = stringResource(R.string.permission_missing)
        PermissionStatusCard(
            title = stringResource(R.string.usage_access_title),
            body = stringResource(R.string.permission_usage_body),
            granted = permissions.canReadUsage,
            grantedLabel = grantedLabel,
            missingLabel = missingLabel,
            actionLabel = stringResource(R.string.action_details),
            onAction = { onNavigate(SettingsDestination.USAGE_ACCESS) },
        )
        PermissionStatusCard(
            title = stringResource(R.string.onboarding_notifications_title),
            body = stringResource(R.string.permission_notifications_body),
            granted = permissions.notifications == GrantState.GRANTED,
            grantedLabel = grantedLabel,
            missingLabel = missingLabel,
            actionLabel = stringResource(R.string.action_open_settings),
            onAction = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && permissions.notifications != GrantState.GRANTED) {
                    notificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    SystemSettings.openNotificationSettings(context)
                }
            },
        )
        PermissionStatusCard(
            title = stringResource(R.string.accessibility_title),
            body = stringResource(R.string.permission_accessibility_body),
            granted = permissions.accessibilityService == GrantState.GRANTED,
            grantedLabel = stringResource(R.string.permission_enabled),
            missingLabel = stringResource(R.string.permission_optional_off),
            actionLabel = stringResource(R.string.action_details),
            onAction = { onNavigate(SettingsDestination.ACCESSIBILITY) },
        )
    }
}

@Composable
fun UsageAccessSettingsScreen(
    viewModel: SettingsViewModel,
    contentPadding: PaddingValues,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    ScrollPage(contentPadding) { UsageAccessExplanation(state.permissions.canReadUsage) { SystemSettings.openUsageAccess(context) } }
}

@Composable
fun AccessibilitySettingsScreen(
    viewModel: SettingsViewModel,
    contentPadding: PaddingValues,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    ScrollPage(contentPadding) {
        AccessibilityExplanation(state.permissions.accessibilityService == GrantState.GRANTED) { SystemSettings.openAccessibility(context) }
    }
}

/**
 * Extension rules are edited as a draft and only take effect after an explicit save;
 * loosening them asks for a second confirmation.
 */
@Composable
fun ExtensionSettingsScreen(
    viewModel: SettingsViewModel,
    onDone: () -> Unit,
    contentPadding: PaddingValues,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val saved = state.preferences.extensionPolicy
    var draft by remember(saved) { mutableStateOf(saved) }
    var confirm by remember { mutableStateOf(false) }
    val context = LocalContext.current
    ScrollPage(contentPadding) {
        Text(stringResource(R.string.extension_settings_intro), style = MaterialTheme.typography.bodyMedium, color = HzvTheme.colors.textSecondary)
        ExtensionPolicyEditor(draft, viewModel.capabilityFor(draft), onChange = { draft = it })
        if (viewModel.capabilityFor(draft) == de.handyzeitvertreib.app.regulation.AuthCapability.NONE_ENROLLED) {
            SecondaryButton(stringResource(R.string.action_set_up_biometrics), { SystemSettings.openBiometricEnrollment(context) }, Modifier.fillMaxWidth())
        }
        PrimaryButton(
            stringResource(R.string.action_save),
            onClick = { if (draft.isLooserThan(saved)) confirm = true else viewModel.saveExtensionPolicy(draft).also { onDone() } },
            enabled = draft != saved,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    if (confirm) {
        ConfirmDialog(
            title = stringResource(R.string.extension_loosen_title),
            body = stringResource(R.string.extension_loosen_body),
            confirmLabel = stringResource(R.string.action_save),
            dismissLabel = stringResource(R.string.action_cancel),
            onConfirm = {
                confirm = false
                viewModel.saveExtensionPolicy(draft)
                onDone()
            },
            onDismiss = { confirm = false },
        )
    }
}

@Composable
fun AccountScreen(
    viewModel: SettingsViewModel,
    contentPadding: PaddingValues,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val account = state.account
    val actions = AccountStateMapping.actions(account)
    ScrollPage(contentPadding) {
        GlassCard(Modifier.fillMaxWidth(), strong = true) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.AccountCircle, contentDescription = null, tint = HzvTheme.colors.accent)
                Spacer(Modifier.width(12.dp))
                Text(accountSummary(account), style = MaterialTheme.typography.titleMedium, color = HzvTheme.colors.textPrimary, modifier = Modifier.weight(1f))
                if (account == AccountState.NotConfigured) LimitStatusChip(stringResource(R.string.account_not_configured_chip), ChipTone.NEUTRAL)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(if (account == AccountState.NotConfigured) R.string.account_not_configured_body else R.string.account_privacy_body),
                style = MaterialTheme.typography.bodyMedium,
                color = HzvTheme.colors.textSecondary,
            )
        }
        GlassCard(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.account_what_title), style = MaterialTheme.typography.titleMedium, color = HzvTheme.colors.textPrimary)
            Spacer(Modifier.height(4.dp))
            BulletLine(stringResource(R.string.account_what_identity))
            BulletLine(stringResource(R.string.account_what_no_usage), positive = false)
            BulletLine(stringResource(R.string.account_what_optin), positive = false)
        }
        if (actions.canSignOut) SecondaryButton(stringResource(R.string.action_sign_out), viewModel::signOut, Modifier.fillMaxWidth())
    }
}

@Composable
fun PrivacyScreen(
    viewModel: SettingsViewModel,
    onOpenData: () -> Unit,
    contentPadding: PaddingValues,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ScrollPage(contentPadding) {
        GlassCard(Modifier.fillMaxWidth(), strong = true) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.privacy_mode_local), style = MaterialTheme.typography.titleMedium, color = HzvTheme.colors.textPrimary, modifier = Modifier.weight(1f))
                LimitStatusChip(stringResource(R.string.privacy_mode_active), ChipTone.OK)
            }
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.privacy_mode_local_body), style = MaterialTheme.typography.bodyMedium, color = HzvTheme.colors.textSecondary)
        }
        GlassCard(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.privacy_stored_title), style = MaterialTheme.typography.titleMedium, color = HzvTheme.colors.textPrimary)
            BulletLine(stringResource(R.string.privacy_stored_usage))
            BulletLine(stringResource(R.string.privacy_stored_limits))
            BulletLine(stringResource(R.string.privacy_stored_events))
            BulletLine(stringResource(R.string.privacy_stored_settings))
            BulletLine(stringResource(R.string.privacy_no_network), positive = false)
            BulletLine(stringResource(R.string.privacy_no_tracking), positive = false)
        }
        GlassCard(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.privacy_retention), style = MaterialTheme.typography.titleMedium, color = HzvTheme.colors.textPrimary)
            Text(stringResource(R.string.privacy_retention_body), style = MaterialTheme.typography.bodyMedium, color = HzvTheme.colors.textSecondary)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                UserPreferences.RETENTION_CHOICES.forEach { days ->
                    FilterChip(
                        selected = state.preferences.retentionDays == days,
                        onClick = { viewModel.setRetention(days) },
                        label = { Text(stringResource(R.string.privacy_retention_days, days)) },
                    )
                }
            }
        }
        GlassCard(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.privacy_policy_title), style = MaterialTheme.typography.titleMedium, color = HzvTheme.colors.textPrimary)
            Text(stringResource(R.string.privacy_policy_placeholder), style = MaterialTheme.typography.bodyMedium, color = HzvTheme.colors.textSecondary)
        }
        SecondaryButton(stringResource(R.string.settings_data), onOpenData, Modifier.fillMaxWidth())
    }
}

@Composable
fun DataManagementScreen(
    viewModel: SettingsViewModel,
    onEverythingDeleted: () -> Unit,
    contentPadding: PaddingValues,
) {
    val context = LocalContext.current
    val operation by viewModel.operation.collectAsStateWithLifecycle()
    var confirm by rememberSaveable { mutableStateOf<String?>(null) }
    val exportLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri != null) {
                viewModel.export { json ->
                    runCatching {
                        context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) } != null
                    }.getOrDefault(false)
                }
            }
        }
    LaunchedEffect(operation) {
        if (operation == DataOperation.EVERYTHING_DELETED) {
            viewModel.clearOperation()
            onEverythingDeleted()
        }
    }
    ScrollPage(contentPadding) {
        GlassCard(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.data_export_title), style = MaterialTheme.typography.titleMedium, color = HzvTheme.colors.textPrimary)
            Text(stringResource(R.string.data_export_body), style = MaterialTheme.typography.bodyMedium, color = HzvTheme.colors.textSecondary)
            Spacer(Modifier.height(12.dp))
            SecondaryButton(stringResource(R.string.action_export), { exportLauncher.launch("handyzeitvertreib-export.json") }, Modifier.fillMaxWidth(), icon = Icons.Outlined.FileDownload)
        }
        GlassCard(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.data_delete_title), style = MaterialTheme.typography.titleMedium, color = HzvTheme.colors.textPrimary)
            Spacer(Modifier.height(12.dp))
            SecondaryButton(stringResource(R.string.data_delete_history), { confirm = "history" }, Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            SecondaryButton(stringResource(R.string.data_delete_events), { confirm = "events" }, Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            SecondaryButton(stringResource(R.string.data_delete_everything), { confirm = "all" }, Modifier.fillMaxWidth(), icon = Icons.Outlined.DeleteForever)
        }
        operation?.let {
            Text(
                stringResource(
                    when (it) {
                        DataOperation.EXPORTED -> R.string.data_result_exported
                        DataOperation.EXPORT_FAILED -> R.string.data_result_export_failed
                        DataOperation.HISTORY_DELETED -> R.string.data_result_history_deleted
                        DataOperation.EVENTS_DELETED -> R.string.data_result_events_deleted
                        DataOperation.EVERYTHING_DELETED -> R.string.data_result_everything_deleted
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = if (it == DataOperation.EXPORT_FAILED) HzvTheme.colors.warning else HzvTheme.colors.success,
            )
        }
    }
    confirm?.let { which ->
        ConfirmDialog(
            title = stringResource(R.string.data_confirm_title),
            body =
                stringResource(
                    when (which) {
                        "history" -> R.string.data_confirm_history
                        "events" -> R.string.data_confirm_events
                        else -> R.string.data_confirm_everything
                    },
                ),
            confirmLabel = stringResource(R.string.action_delete),
            dismissLabel = stringResource(R.string.action_cancel),
            onConfirm = {
                confirm = null
                when (which) {
                    "history" -> viewModel.deleteHistory()
                    "events" -> viewModel.deleteEvents()
                    else -> viewModel.deleteEverything()
                }
            },
            onDismiss = { confirm = null },
            destructive = true,
        )
    }
}

@Composable
fun HelpScreen(contentPadding: PaddingValues) {
    ScrollPage(contentPadding) {
        listOf(
            R.string.help_measure_title to R.string.help_measure_body,
            R.string.help_enforcement_title to R.string.help_enforcement_body,
            R.string.help_background_title to R.string.help_background_body,
            R.string.help_extension_title to R.string.help_extension_body,
            R.string.help_groups_title to R.string.help_groups_body,
            R.string.help_day_title to R.string.help_day_body,
            R.string.help_oem_title to R.string.help_oem_body,
        ).forEach { (title, body) ->
            GlassCard(Modifier.fillMaxWidth()) {
                Text(stringResource(title), style = MaterialTheme.typography.titleMedium, color = HzvTheme.colors.textPrimary)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(body), style = MaterialTheme.typography.bodyMedium, color = HzvTheme.colors.textSecondary)
            }
        }
    }
}
