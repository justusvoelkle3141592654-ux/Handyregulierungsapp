package de.handyzeitvertreib.app.ui.limits

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.GroupWork
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import de.handyzeitvertreib.app.AppContainer
import de.handyzeitvertreib.app.R
import de.handyzeitvertreib.app.core.model.LimitKey
import de.handyzeitvertreib.app.core.model.LimitType
import de.handyzeitvertreib.app.ui.common.LimitItem
import de.handyzeitvertreib.app.ui.common.LimitItems
import de.handyzeitvertreib.app.ui.common.formatMinutes
import de.handyzeitvertreib.app.ui.dashboard.LimitProgressRow
import de.handyzeitvertreib.app.ui.designsystem.AppIcon
import de.handyzeitvertreib.app.ui.designsystem.ChipTone
import de.handyzeitvertreib.app.ui.designsystem.EmptyState
import de.handyzeitvertreib.app.ui.designsystem.GlassCard
import de.handyzeitvertreib.app.ui.designsystem.HzvSpacing
import de.handyzeitvertreib.app.ui.designsystem.HzvTheme
import de.handyzeitvertreib.app.ui.designsystem.LimitStatusChip
import de.handyzeitvertreib.app.ui.designsystem.SecondaryButton
import de.handyzeitvertreib.app.ui.designsystem.SectionHeader
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One configured limit; [live] is null when disabled or when usage is unavailable. */
data class ConfiguredLimit(
    val key: LimitKey,
    val title: String,
    val packageName: String?,
    val memberCount: Int,
    val minutes: Int,
    val enabled: Boolean,
    val live: LimitItem?,
)

data class LimitsUiState(
    val loading: Boolean = true,
    val usageAvailable: Boolean = false,
    val appLimits: List<ConfiguredLimit> = emptyList(),
    val groupLimits: List<ConfiguredLimit> = emptyList(),
)

class LimitsViewModel(
    private val container: AppContainer,
) : ViewModel() {
    val state: StateFlow<LimitsUiState> =
        combine(
            container.limitRepository.appLimits,
            container.limitRepository.groupLimits,
            container.regulationCoordinator.observe(),
            container.usageRepository.observeKnownLabels(),
        ) { apps, groups, snapshot, labels ->
            val live = snapshot?.evaluation?.let { evaluation -> evaluation.statuses.associate { it.key to LimitItems.from(it, labels) } }.orEmpty()
            LimitsUiState(
                loading = false,
                usageAvailable = snapshot != null,
                appLimits =
                    apps
                        .map {
                            val key = LimitKey(LimitType.APP, it.id)
                            ConfiguredLimit(key, labels[it.packageName] ?: it.packageName, it.packageName, 1, it.dailyLimitMinutes, it.enabled, live[key])
                        }.sortedBy { it.title.lowercase() },
                groupLimits =
                    groups.map {
                        val key = LimitKey(LimitType.GROUP, it.id)
                        ConfiguredLimit(key, it.name, null, it.packageNames.size, it.dailyLimitMinutes, it.enabled, live[key])
                    },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LimitsUiState())

    fun setEnabled(
        limit: ConfiguredLimit,
        enabled: Boolean,
    ) {
        viewModelScope.launch {
            when (limit.key.type) {
                LimitType.APP -> container.limitRepository.setAppLimitEnabled(limit.key.id, enabled)
                LimitType.GROUP -> container.limitRepository.setGroupEnabled(limit.key.id, enabled)
            }
        }
    }
}

data class LimitsActions(
    val onAddAppLimit: () -> Unit = {},
    val onAddGroup: () -> Unit = {},
    val onEditAppLimit: (String) -> Unit = {},
    val onEditGroup: (Long) -> Unit = {},
)

@Composable
fun LimitsRoute(
    viewModel: LimitsViewModel,
    actions: LimitsActions,
    contentPadding: PaddingValues,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LimitsScreen(state, actions, viewModel::setEnabled, contentPadding)
}

@Composable
fun LimitsScreen(
    state: LimitsUiState,
    actions: LimitsActions,
    onToggle: (ConfiguredLimit, Boolean) -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
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
            Text(
                stringResource(R.string.limits_title),
                style = MaterialTheme.typography.headlineMedium,
                color = HzvTheme.colors.textPrimary,
                modifier = Modifier.semantics { heading() },
            )
        }
        if (!state.loading && !state.usageAvailable) {
            item { Text(stringResource(R.string.limits_usage_unavailable), style = MaterialTheme.typography.bodyMedium, color = HzvTheme.colors.warning) }
        }
        item { SectionHeader(stringResource(R.string.limits_app_section)) }
        if (state.appLimits.isEmpty()) {
            item {
                EmptyState(
                    Icons.Outlined.Timer,
                    stringResource(R.string.limits_no_app_limits_title),
                    stringResource(R.string.limits_no_app_limits_body),
                )
            }
        }
        items(state.appLimits, key = { "app-${it.key.id}" }) { limit -> ConfiguredLimitCard(limit, onToggle) { actions.onEditAppLimit(limit.packageName!!) } }
        item { SecondaryButton(stringResource(R.string.action_add_app_limit), actions.onAddAppLimit, Modifier.fillMaxWidth(), icon = Icons.Outlined.Add) }
        item { SectionHeader(stringResource(R.string.limits_group_section)) }
        item { Text(stringResource(R.string.limits_group_explanation), style = MaterialTheme.typography.bodyMedium, color = HzvTheme.colors.textSecondary) }
        if (state.groupLimits.isEmpty()) {
            item {
                EmptyState(
                    Icons.Outlined.GroupWork,
                    stringResource(R.string.limits_no_groups_title),
                    stringResource(R.string.limits_no_groups_body),
                )
            }
        }
        items(state.groupLimits, key = { "group-${it.key.id}" }) { limit -> ConfiguredLimitCard(limit, onToggle) { actions.onEditGroup(limit.key.id) } }
        item { SecondaryButton(stringResource(R.string.action_add_group), actions.onAddGroup, Modifier.fillMaxWidth(), icon = Icons.Outlined.Add) }
    }
}

@Composable
private fun ConfiguredLimitCard(
    limit: ConfiguredLimit,
    onToggle: (ConfiguredLimit, Boolean) -> Unit,
    onEdit: () -> Unit,
) {
    GlassCard(Modifier.fillMaxWidth(), onClick = onEdit, onClickLabel = stringResource(R.string.action_edit)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (limit.packageName != null) {
                AppIcon(limit.packageName, limit.title, size = 36.dp)
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(limit.title, style = MaterialTheme.typography.titleMedium, color = HzvTheme.colors.textPrimary, maxLines = 1)
                Text(
                    if (limit.packageName == null) {
                        stringResource(R.string.limits_group_summary, formatMinutes(limit.minutes), limit.memberCount)
                    } else {
                        stringResource(R.string.limits_app_summary, formatMinutes(limit.minutes))
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = HzvTheme.colors.textSecondary,
                )
            }
            Switch(checked = limit.enabled, onCheckedChange = { onToggle(limit, it) })
        }
        val live = limit.live
        when {
            !limit.enabled -> {
                Spacer(Modifier.height(8.dp))
                LimitStatusChip(stringResource(R.string.limit_status_paused), ChipTone.WARNING)
            }

            live != null -> {
                Spacer(Modifier.height(12.dp))
                LimitProgressRow(live)
            }
        }
    }
}
