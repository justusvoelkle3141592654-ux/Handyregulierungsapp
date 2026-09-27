package de.handyzeitvertreib.app.ui.limits

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import de.handyzeitvertreib.app.core.model.InstalledApp
import de.handyzeitvertreib.app.core.model.LimitRules
import de.handyzeitvertreib.app.ui.designsystem.AppRow
import de.handyzeitvertreib.app.ui.designsystem.ConfirmDialog
import de.handyzeitvertreib.app.ui.designsystem.GlassCard
import de.handyzeitvertreib.app.ui.designsystem.HzvShapes
import de.handyzeitvertreib.app.ui.designsystem.HzvSpacing
import de.handyzeitvertreib.app.ui.designsystem.HzvTheme
import de.handyzeitvertreib.app.ui.designsystem.PrimaryButton
import de.handyzeitvertreib.app.ui.designsystem.SecondaryButton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class GroupEditorState(
    val loading: Boolean = true,
    val id: Long = 0,
    val name: String = "",
    val minutes: Int = 60,
    val enabled: Boolean = true,
    val selected: Set<String> = emptySet(),
    val apps: List<InstalledApp> = emptyList(),
    val query: String = "",
    val saved: Boolean = false,
) {
    val canSave: Boolean get() = !loading && LimitRules.isValidGroupName(name) && selected.isNotEmpty()
    val visibleApps: List<InstalledApp>
        get() = apps.filter { query.isBlank() || it.label.contains(query.trim(), ignoreCase = true) }
}

class GroupEditorViewModel(
    private val container: AppContainer,
    private val groupId: Long,
) : ViewModel() {
    private val mutableState = MutableStateFlow(GroupEditorState(id = groupId))
    val state: StateFlow<GroupEditorState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            val apps = container.usageRepository.installedApps()
            val group = container.limitRepository.currentGroupLimits().firstOrNull { it.id == groupId }
            mutableState.value =
                GroupEditorState(
                    loading = false,
                    id = group?.id ?: 0,
                    name = group?.name.orEmpty(),
                    minutes = group?.dailyLimitMinutes ?: 60,
                    enabled = group?.enabled ?: true,
                    selected = group?.packageNames.orEmpty(),
                    apps = apps,
                )
        }
    }

    fun setName(name: String) = update { copy(name = name.take(40)) }

    fun setMinutes(minutes: Int) = update { copy(minutes = LimitRules.normalize(minutes)) }

    fun setEnabled(enabled: Boolean) = update { copy(enabled = enabled) }

    fun setQuery(query: String) = update { copy(query = query) }

    fun toggle(packageName: String) = update { copy(selected = if (packageName in selected) selected - packageName else selected + packageName) }

    fun save() {
        val current = mutableState.value
        if (!current.canSave) return
        viewModelScope.launch {
            container.limitRepository.saveGroup(current.id, current.name, current.selected, current.minutes, current.enabled)
            update { copy(saved = true) }
        }
    }

    fun delete() {
        val id = mutableState.value.id
        if (id == 0L) return
        viewModelScope.launch {
            container.limitRepository.deleteGroup(id)
            update { copy(saved = true) }
        }
    }

    private fun update(block: GroupEditorState.() -> GroupEditorState) {
        mutableState.value = mutableState.value.block()
    }
}

@Composable
fun GroupEditorRoute(
    viewModel: GroupEditorViewModel,
    onDone: () -> Unit,
    contentPadding: PaddingValues,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (state.saved) LaunchedEffect(Unit) { onDone() }
    var confirmDelete by remember { mutableStateOf(false) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding =
            PaddingValues(
                start = HzvSpacing.screen,
                end = HzvSpacing.screen,
                top = contentPadding.calculateTopPadding() + 16.dp,
                bottom = contentPadding.calculateBottomPadding() + 24.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(HzvSpacing.gap),
    ) {
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = viewModel::setName,
                    label = { Text(stringResource(R.string.group_editor_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("group-name"),
                )
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.group_editor_daily_limit), style = MaterialTheme.typography.titleMedium, color = HzvTheme.colors.textPrimary)
                Text(stringResource(R.string.group_editor_daily_limit_body), style = MaterialTheme.typography.bodyMedium, color = HzvTheme.colors.textSecondary)
                Spacer(Modifier.height(12.dp))
                DurationStepper(state.minutes, viewModel::setMinutes)
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.limit_editor_enabled), style = MaterialTheme.typography.bodyLarge, color = HzvTheme.colors.textPrimary, modifier = Modifier.weight(1f))
                    Switch(checked = state.enabled, onCheckedChange = viewModel::setEnabled)
                }
            }
        }
        item {
            Text(
                stringResource(R.string.group_editor_apps, state.selected.size),
                style = MaterialTheme.typography.titleMedium,
                color = HzvTheme.colors.textPrimary,
            )
        }
        item {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                placeholder = { Text(stringResource(R.string.apps_search_hint)) },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
                shape = HzvShapes.pill,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        items(state.visibleApps, key = { it.packageName }) { app ->
            GlassCard(Modifier.fillMaxWidth(), contentPadding = 4.dp) {
                AppRow(
                    packageName = app.packageName,
                    label = app.label,
                    supporting = null,
                    onClick = { viewModel.toggle(app.packageName) },
                    trailing = { Checkbox(checked = app.packageName in state.selected, onCheckedChange = { viewModel.toggle(app.packageName) }) },
                )
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(HzvSpacing.gap)) {
                if (!state.canSave && !state.loading) {
                    Text(stringResource(R.string.group_editor_invalid), style = MaterialTheme.typography.bodyMedium, color = HzvTheme.colors.warning)
                }
                PrimaryButton(stringResource(R.string.action_save), onClick = viewModel::save, enabled = state.canSave, icon = Icons.Outlined.Save, modifier = Modifier.fillMaxWidth())
                if (state.id != 0L) {
                    SecondaryButton(stringResource(R.string.action_delete_group), onClick = { confirmDelete = true }, icon = Icons.Outlined.Delete, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.group_delete_title),
            body = stringResource(R.string.group_delete_body),
            confirmLabel = stringResource(R.string.action_delete),
            dismissLabel = stringResource(R.string.action_cancel),
            onConfirm = {
                confirmDelete = false
                viewModel.delete()
            },
            onDismiss = { confirmDelete = false },
            destructive = true,
        )
    }
}
