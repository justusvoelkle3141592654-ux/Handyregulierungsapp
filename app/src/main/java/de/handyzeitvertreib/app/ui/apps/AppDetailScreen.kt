package de.handyzeitvertreib.app.ui.apps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import de.handyzeitvertreib.app.AppContainer
import de.handyzeitvertreib.app.R
import de.handyzeitvertreib.app.core.model.ExtensionPolicy
import de.handyzeitvertreib.app.ui.common.LimitItem
import de.handyzeitvertreib.app.ui.common.LimitItems
import de.handyzeitvertreib.app.ui.common.formatDuration
import de.handyzeitvertreib.app.ui.common.formatMinutes
import de.handyzeitvertreib.app.ui.dashboard.LimitProgressRow
import de.handyzeitvertreib.app.ui.designsystem.AppIcon
import de.handyzeitvertreib.app.ui.designsystem.BarChart
import de.handyzeitvertreib.app.ui.designsystem.BarDatum
import de.handyzeitvertreib.app.ui.designsystem.GlassCard
import de.handyzeitvertreib.app.ui.designsystem.HzvSpacing
import de.handyzeitvertreib.app.ui.designsystem.HzvTheme
import de.handyzeitvertreib.app.ui.designsystem.PrimaryButton
import de.handyzeitvertreib.app.ui.designsystem.TabularNumbers
import de.handyzeitvertreib.app.ui.insights.shortDayLabel
import de.handyzeitvertreib.app.usage.TodayUsageState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class DayBar(
    val date: LocalDate,
    val durationMs: Long,
    val captured: Boolean,
)

data class AppDetailUiState(
    val loading: Boolean = true,
    val packageName: String = "",
    val label: String = "",
    val installed: Boolean = true,
    val todayMs: Long? = null,
    val todayLaunches: Int? = null,
    val week: List<DayBar> = emptyList(),
    val limitMinutes: Int? = null,
    val limitEnabled: Boolean = false,
    val governing: LimitItem? = null,
    val groupNames: List<String> = emptyList(),
    val countedInTotal: Boolean = true,
    val extensionPolicy: ExtensionPolicy = ExtensionPolicy.DEFAULT,
)

class AppDetailViewModel(
    private val container: AppContainer,
    private val packageName: String,
) : ViewModel() {
    private val installedLabel = MutableStateFlow<Pair<Boolean, String?>?>(null)
    private val today = container.clock.today()

    val state: StateFlow<AppDetailUiState> =
        combine(
            combine(installedLabel, container.usageRepository.observeKnownLabels(), container.usageRepository.today) { a, b, c -> Triple(a, b, c) },
            container.usageRepository.observeDays(today.minusDays(6), today),
            container.limitRepository.appLimits,
            container.limitRepository.groupLimits,
            combine(container.regulationCoordinator.observe(), container.preferencesRepository.preferences) { s, p -> s to p },
        ) { (installed, labels, usage), days, limits, groups, (snapshot, prefs) ->
            val ready = usage as? TodayUsageState.Ready
            val todayUsage = ready?.apps?.firstOrNull { it.packageName == packageName }
            val limit = limits.firstOrNull { it.packageName == packageName }
            AppDetailUiState(
                loading = installed == null,
                packageName = packageName,
                label = installed?.second ?: labels[packageName] ?: packageName,
                installed = installed?.first ?: true,
                todayMs = if (ready == null) null else todayUsage?.foregroundMs ?: 0L,
                todayLaunches = if (ready == null) null else todayUsage?.launchCount ?: 0,
                week =
                    days.map { day ->
                        val ms =
                            if (day.date == today && ready != null) {
                                todayUsage?.foregroundMs ?: 0L
                            } else {
                                day.apps.firstOrNull { it.packageName == packageName }?.foregroundMs ?: 0L
                            }
                        DayBar(day.date, ms, day.captured || (day.date == today && ready != null))
                    },
                limitMinutes = limit?.dailyLimitMinutes,
                limitEnabled = limit?.enabled == true,
                governing = snapshot?.evaluation?.governingFor(packageName)?.let { LimitItems.from(it, labels) },
                groupNames = groups.filter { packageName in it.packageNames }.map { it.name },
                countedInTotal = packageName !in prefs.excludedFromTotals,
                extensionPolicy = prefs.extensionPolicy,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppDetailUiState(packageName = packageName))

    init {
        viewModelScope.launch {
            val app = container.usageRepository.installedApps().firstOrNull { it.packageName == packageName }
            installedLabel.value = (app != null) to app?.label
        }
    }

    fun setCountedInTotal(counted: Boolean) {
        viewModelScope.launch {
            container.preferencesRepository.setExcludedFromTotals(packageName, excluded = !counted)
            container.usageRepository.refresh(backfillDays = 0)
        }
    }
}

@Composable
fun AppDetailRoute(
    viewModel: AppDetailViewModel,
    onEditLimit: (String) -> Unit,
    onOpenExtensionSettings: () -> Unit,
    contentPadding: PaddingValues,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AppDetailScreen(state, onEditLimit, onOpenExtensionSettings, viewModel::setCountedInTotal, contentPadding)
}

@Composable
fun AppDetailScreen(
    state: AppDetailUiState,
    onEditLimit: (String) -> Unit,
    onOpenExtensionSettings: () -> Unit,
    onCountedInTotalChange: (Boolean) -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(HzvSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(HzvSpacing.gap),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(state.packageName, state.label, size = 56.dp)
            Spacer(Modifier.width(16.dp))
            Column {
                Text(state.label, style = MaterialTheme.typography.headlineSmall, color = HzvTheme.colors.textPrimary)
                if (!state.installed) {
                    Text(stringResource(R.string.apps_not_installed), style = MaterialTheme.typography.bodyMedium, color = HzvTheme.colors.warning)
                }
            }
        }
        GlassCard(Modifier.fillMaxWidth(), strong = true) {
            Text(stringResource(R.string.app_detail_today), style = MaterialTheme.typography.labelLarge, color = HzvTheme.colors.textSecondary)
            if (state.todayMs == null) {
                Text(stringResource(R.string.apps_usage_unknown), style = MaterialTheme.typography.titleLarge, color = HzvTheme.colors.textPrimary)
            } else {
                Text(formatDuration(state.todayMs), style = MaterialTheme.typography.displaySmall.merge(TabularNumbers), color = HzvTheme.colors.textPrimary)
                Text(
                    stringResource(R.string.app_detail_opens, state.todayLaunches ?: 0),
                    style = MaterialTheme.typography.bodyMedium,
                    color = HzvTheme.colors.textSecondary,
                )
            }
        }
        GlassCard(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.app_detail_last_7_days), style = MaterialTheme.typography.titleMedium, color = HzvTheme.colors.textPrimary)
            Spacer(Modifier.height(12.dp))
            val noData = stringResource(R.string.insights_no_data_short)
            BarChart(
                data =
                    state.week.map {
                        BarDatum(
                            label = shortDayLabel(it.date),
                            value = it.durationMs,
                            accessibilityValue = if (it.captured) formatDuration(it.durationMs) else noData,
                            available = it.captured,
                            highlighted = it == state.week.lastOrNull(),
                        )
                    },
                chartDescription = stringResource(R.string.app_detail_last_7_days),
            )
            if (state.week.any { !it.captured }) {
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.insights_missing_days_hint), style = MaterialTheme.typography.bodySmall, color = HzvTheme.colors.textSecondary)
            }
        }
        GlassCard(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.app_detail_limit), style = MaterialTheme.typography.titleMedium, color = HzvTheme.colors.textPrimary)
            Spacer(Modifier.height(8.dp))
            when {
                state.limitMinutes == null -> {
                    Text(stringResource(R.string.app_detail_no_limit), style = MaterialTheme.typography.bodyMedium, color = HzvTheme.colors.textSecondary)
                }

                !state.limitEnabled -> {
                    Text(
                        stringResource(R.string.app_detail_limit_paused, formatMinutes(state.limitMinutes)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = HzvTheme.colors.textSecondary,
                    )
                }

                else -> {
                    Text(
                        stringResource(R.string.app_detail_limit_value, formatMinutes(state.limitMinutes)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = HzvTheme.colors.textSecondary,
                    )
                }
            }
            state.governing?.let { governing ->
                Spacer(Modifier.height(12.dp))
                if (governing.isGroup) {
                    Text(
                        stringResource(R.string.app_detail_governed_by_group, governing.title),
                        style = MaterialTheme.typography.bodySmall,
                        color = HzvTheme.colors.textSecondary,
                    )
                    Spacer(Modifier.height(6.dp))
                }
                LimitProgressRow(governing)
            }
            if (state.groupNames.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.app_detail_groups, state.groupNames.joinToString()),
                    style = MaterialTheme.typography.bodySmall,
                    color = HzvTheme.colors.textSecondary,
                )
            }
            Spacer(Modifier.height(12.dp))
            PrimaryButton(
                stringResource(if (state.limitMinutes == null) R.string.action_set_limit else R.string.action_edit_limit),
                onClick = { onEditLimit(state.packageName) },
                icon = Icons.Outlined.Timer,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        GlassCard(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.app_detail_extension), style = MaterialTheme.typography.titleMedium, color = HzvTheme.colors.textPrimary)
            Spacer(Modifier.height(4.dp))
            Text(
                if (state.extensionPolicy.enabled) {
                    stringResource(
                        R.string.app_detail_extension_summary,
                        formatMinutes(state.extensionPolicy.extensionMinutes),
                        state.extensionPolicy.maxExtensionsPerDay,
                    )
                } else {
                    stringResource(R.string.app_detail_extension_disabled)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = HzvTheme.colors.textSecondary,
            )
            TextButton(onClick = onOpenExtensionSettings) { Text(stringResource(R.string.action_change_in_settings)) }
        }
        GlassCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.app_detail_count_in_total), style = MaterialTheme.typography.titleMedium, color = HzvTheme.colors.textPrimary)
                    Text(stringResource(R.string.app_detail_count_in_total_body), style = MaterialTheme.typography.bodyMedium, color = HzvTheme.colors.textSecondary)
                }
                Switch(checked = state.countedInTotal, onCheckedChange = onCountedInTotalChange)
            }
        }
    }
}
