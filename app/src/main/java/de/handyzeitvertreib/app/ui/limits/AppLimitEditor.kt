package de.handyzeitvertreib.app.ui.limits

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import de.handyzeitvertreib.app.AppContainer
import de.handyzeitvertreib.app.R
import de.handyzeitvertreib.app.core.model.LimitRules
import de.handyzeitvertreib.app.ui.designsystem.AppIcon
import de.handyzeitvertreib.app.ui.designsystem.ConfirmDialog
import de.handyzeitvertreib.app.ui.designsystem.GlassCard
import de.handyzeitvertreib.app.ui.designsystem.HzvSpacing
import de.handyzeitvertreib.app.ui.designsystem.HzvTheme
import de.handyzeitvertreib.app.ui.designsystem.PrimaryButton
import de.handyzeitvertreib.app.ui.designsystem.SecondaryButton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class AppLimitEditorState(
    val loading: Boolean = true,
    val packageName: String = "",
    val label: String = "",
    val existingId: Long? = null,
    val minutes: Int = LimitRules.DEFAULT_MINUTES,
    val enabled: Boolean = true,
    val saved: Boolean = false,
)

class AppLimitEditorViewModel(
    private val container: AppContainer,
    private val packageName: String,
) : ViewModel() {
    private val mutableState = MutableStateFlow(AppLimitEditorState(packageName = packageName))
    val state: StateFlow<AppLimitEditorState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            val existing = container.limitRepository.currentAppLimits().firstOrNull { it.packageName == packageName }
            val labels = container.usageRepository.observeKnownLabels().first()
            val label =
                container.usageRepository.installedApps().firstOrNull { it.packageName == packageName }?.label
                    ?: labels[packageName]
                    ?: packageName
            mutableState.value =
                AppLimitEditorState(
                    loading = false,
                    packageName = packageName,
                    label = label,
                    existingId = existing?.id,
                    minutes = existing?.dailyLimitMinutes ?: LimitRules.DEFAULT_MINUTES,
                    enabled = existing?.enabled ?: true,
                )
        }
    }

    fun setMinutes(minutes: Int) {
        mutableState.value = mutableState.value.copy(minutes = LimitRules.normalize(minutes))
    }

    fun setEnabled(enabled: Boolean) {
        mutableState.value = mutableState.value.copy(enabled = enabled)
    }

    fun save() {
        val current = mutableState.value
        viewModelScope.launch {
            container.limitRepository.saveAppLimit(current.packageName, current.minutes, current.enabled)
            mutableState.value = current.copy(saved = true)
        }
    }

    fun delete() {
        val id = mutableState.value.existingId ?: return
        viewModelScope.launch {
            container.limitRepository.deleteAppLimit(id)
            mutableState.value = mutableState.value.copy(saved = true)
        }
    }
}

@Composable
fun AppLimitEditorRoute(
    viewModel: AppLimitEditorViewModel,
    onDone: () -> Unit,
    contentPadding: PaddingValues,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (state.saved) {
        androidx.compose.runtime.LaunchedEffect(Unit) { onDone() }
    }
    AppLimitEditorScreen(state, viewModel::setMinutes, viewModel::setEnabled, viewModel::save, viewModel::delete, contentPadding)
}

@Composable
fun AppLimitEditorScreen(
    state: AppLimitEditorState,
    onMinutesChange: (Int) -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    var confirmDelete by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(HzvSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(HzvSpacing.gap),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(state.packageName, state.label, size = 48.dp)
            Spacer(Modifier.padding(start = 12.dp))
            Column {
                Text(
                    stringResource(if (state.existingId == null) R.string.limit_editor_new_title else R.string.limit_editor_edit_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = HzvTheme.colors.textSecondary,
                )
                Text(state.label, style = MaterialTheme.typography.titleLarge, color = HzvTheme.colors.textPrimary)
            }
        }
        GlassCard(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.limit_editor_daily_limit), style = MaterialTheme.typography.titleMedium, color = HzvTheme.colors.textPrimary)
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.limit_editor_daily_limit_body), style = MaterialTheme.typography.bodyMedium, color = HzvTheme.colors.textSecondary)
            Spacer(Modifier.height(16.dp))
            DurationStepper(state.minutes, onMinutesChange)
        }
        GlassCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.limit_editor_enabled), style = MaterialTheme.typography.titleMedium, color = HzvTheme.colors.textPrimary)
                    Text(stringResource(R.string.limit_editor_enabled_body), style = MaterialTheme.typography.bodyMedium, color = HzvTheme.colors.textSecondary)
                }
                Switch(checked = state.enabled, onCheckedChange = onEnabledChange, modifier = Modifier.testTag("limit-enabled"))
            }
        }
        PrimaryButton(
            stringResource(R.string.action_save),
            onClick = onSave,
            enabled = !state.loading,
            icon = Icons.Outlined.Save,
            modifier = Modifier.fillMaxWidth().testTag("limit-save"),
        )
        if (state.existingId != null) {
            SecondaryButton(stringResource(R.string.action_delete_limit), onClick = { confirmDelete = true }, icon = Icons.Outlined.Delete, modifier = Modifier.fillMaxWidth())
        }
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.limit_delete_title),
            body = stringResource(R.string.limit_delete_body),
            confirmLabel = stringResource(R.string.action_delete),
            dismissLabel = stringResource(R.string.action_cancel),
            onConfirm = {
                confirmDelete = false
                onDelete()
            },
            onDismiss = { confirmDelete = false },
            destructive = true,
        )
    }
}
