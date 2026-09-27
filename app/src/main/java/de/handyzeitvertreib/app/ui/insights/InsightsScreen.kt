package de.handyzeitvertreib.app.ui.insights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import de.handyzeitvertreib.app.AppContainer
import de.handyzeitvertreib.app.R
import de.handyzeitvertreib.app.core.format.saturatingAdd
import de.handyzeitvertreib.app.ui.common.formatDuration
import de.handyzeitvertreib.app.ui.dashboard.UsageItem
import de.handyzeitvertreib.app.ui.designsystem.AppRow
import de.handyzeitvertreib.app.ui.designsystem.BarChart
import de.handyzeitvertreib.app.ui.designsystem.BarDatum
import de.handyzeitvertreib.app.ui.designsystem.EmptyState
import de.handyzeitvertreib.app.ui.designsystem.GlassCard
import de.handyzeitvertreib.app.ui.designsystem.HzvSpacing
import de.handyzeitvertreib.app.ui.designsystem.HzvTheme
import de.handyzeitvertreib.app.ui.designsystem.ProgressBar
import de.handyzeitvertreib.app.ui.designsystem.SectionHeader
import de.handyzeitvertreib.app.ui.designsystem.TabularNumbers
import de.handyzeitvertreib.app.usage.DayUsage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

enum class InsightsMode { DAY, WEEK }

data class InsightsUiState(
    val loading: Boolean = true,
    val mode: InsightsMode = InsightsMode.DAY,
    val anchor: LocalDate = LocalDate.now(),
    val canGoForward: Boolean = false,
    val capturedDays: Int = 0,
    val totalMs: Long = 0,
    val averagePerCapturedDayMs: Long = 0,
    val days: List<DayUsageBar> = emptyList(),
    val apps: List<UsageItem> = emptyList(),
)

data class DayUsageBar(
    val date: LocalDate,
    val totalMs: Long,
    val captured: Boolean,
)

/** Pure aggregation for the insights screen; unavailable days are never counted as zero. */
object InsightsAggregation {
    fun build(
        days: List<DayUsage>,
        excluded: Set<String>,
        labels: Map<String, String>,
        topCount: Int = 10,
    ): Triple<List<DayUsageBar>, Long, List<UsageItem>> {
        val bars = days.map { DayUsageBar(it.date, if (it.captured) it.totalMs(excluded) else 0L, it.captured) }
        val total = bars.filter { it.captured }.fold(0L) { acc, bar -> saturatingAdd(acc, bar.totalMs) }
        val perApp = mutableMapOf<String, Long>()
        val launches = mutableMapOf<String, Int>()
        days.filter { it.captured }.flatMap { it.apps }.filter { it.packageName !in excluded }.forEach {
            perApp[it.packageName] = saturatingAdd(perApp[it.packageName] ?: 0L, it.foregroundMs)
            launches[it.packageName] = (launches[it.packageName] ?: 0) + it.launchCount
        }
        val apps =
            perApp.entries
                .filter { it.value > 0 }
                .sortedByDescending { it.value }
                .take(topCount)
                .map { (pkg, ms) -> UsageItem(pkg, labels[pkg] ?: pkg, ms, launches[pkg] ?: 0, if (total > 0) ms.toFloat() / total else 0f) }
        return Triple(bars, total, apps)
    }
}

class InsightsViewModel(
    private val container: AppContainer,
) : ViewModel() {
    private val selection = MutableStateFlow(InsightsMode.DAY to container.clock.today())

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<InsightsUiState> =
        selection
            .flatMapLatest { (mode, anchor) ->
                val from = if (mode == InsightsMode.DAY) anchor else anchor.minusDays(6)
                combine(
                    container.usageRepository.observeDays(from, anchor),
                    container.usageRepository.observeKnownLabels(),
                    container.preferencesRepository.preferences,
                ) { days, labels, _ ->
                    val excluded = container.usageRepository.excludedPackages()
                    val (bars, total, apps) = InsightsAggregation.build(days, excluded, labels)
                    val captured = bars.count { it.captured }
                    InsightsUiState(
                        loading = false,
                        mode = mode,
                        anchor = anchor,
                        canGoForward = anchor.isBefore(container.clock.today()),
                        capturedDays = captured,
                        totalMs = total,
                        averagePerCapturedDayMs = if (captured > 0) total / captured else 0,
                        days = bars,
                        apps = apps,
                    )
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InsightsUiState())

    fun setMode(mode: InsightsMode) {
        selection.value = mode to container.clock.today()
    }

    fun previous() {
        val (mode, anchor) = selection.value
        selection.value = mode to anchor.minusDays(if (mode == InsightsMode.DAY) 1 else 7)
    }

    fun next() {
        val (mode, anchor) = selection.value
        val today = container.clock.today()
        val candidate = anchor.plusDays(if (mode == InsightsMode.DAY) 1 else 7)
        selection.value = mode to if (candidate.isAfter(today)) today else candidate
    }
}

fun shortDayLabel(date: LocalDate): String = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())

private fun mediumDate(date: LocalDate): String = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault()))

@Composable
fun InsightsRoute(
    viewModel: InsightsViewModel,
    onOpenApp: (String) -> Unit,
    contentPadding: PaddingValues,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    InsightsScreen(state, viewModel::setMode, viewModel::previous, viewModel::next, onOpenApp, contentPadding)
}

@Composable
fun InsightsScreen(
    state: InsightsUiState,
    onModeChange: (InsightsMode) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onOpenApp: (String) -> Unit,
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
                stringResource(R.string.insights_title),
                style = MaterialTheme.typography.headlineMedium,
                color = HzvTheme.colors.textPrimary,
                modifier = Modifier.semantics { heading() },
            )
        }
        item {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                InsightsMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = state.mode == mode,
                        onClick = { onModeChange(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index, InsightsMode.entries.size),
                    ) { Text(stringResource(if (mode == InsightsMode.DAY) R.string.insights_day else R.string.insights_week)) }
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPrevious) {
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = stringResource(R.string.insights_previous))
                }
                Text(
                    if (state.mode == InsightsMode.DAY) {
                        mediumDate(state.anchor)
                    } else {
                        stringResource(R.string.insights_range, mediumDate(state.anchor.minusDays(6)), mediumDate(state.anchor))
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = HzvTheme.colors.textPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onNext, enabled = state.canGoForward) {
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = stringResource(R.string.insights_next))
                }
            }
        }
        if (!state.loading && state.capturedDays == 0) {
            item {
                EmptyState(
                    icon = Icons.Outlined.CloudOff,
                    title = stringResource(R.string.insights_no_data_title),
                    body = stringResource(R.string.insights_no_data_body),
                )
            }
            return@LazyColumn
        }
        item {
            GlassCard(Modifier.fillMaxWidth(), strong = true) {
                Text(
                    stringResource(if (state.mode == InsightsMode.DAY) R.string.insights_total_day else R.string.insights_total_week),
                    style = MaterialTheme.typography.labelLarge,
                    color = HzvTheme.colors.textSecondary,
                )
                Text(formatDuration(state.totalMs), style = MaterialTheme.typography.displaySmall.merge(TabularNumbers), color = HzvTheme.colors.textPrimary)
                if (state.mode == InsightsMode.WEEK) {
                    Text(
                        stringResource(R.string.insights_average, formatDuration(state.averagePerCapturedDayMs), state.capturedDays),
                        style = MaterialTheme.typography.bodyMedium,
                        color = HzvTheme.colors.textSecondary,
                    )
                }
            }
        }
        if (state.mode == InsightsMode.WEEK) {
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    val noData = stringResource(R.string.insights_no_data_short)
                    BarChart(
                        data =
                            state.days.map {
                                BarDatum(
                                    label = shortDayLabel(it.date),
                                    value = it.totalMs,
                                    accessibilityValue = if (it.captured) formatDuration(it.totalMs) else noData,
                                    available = it.captured,
                                    highlighted = it.date == state.anchor,
                                )
                            },
                        chartDescription = stringResource(R.string.insights_week_chart),
                    )
                    if (state.days.any { !it.captured }) {
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.insights_missing_days_hint), style = MaterialTheme.typography.bodySmall, color = HzvTheme.colors.textSecondary)
                    }
                }
            }
        }
        item { SectionHeader(stringResource(R.string.insights_app_breakdown)) }
        if (state.apps.isEmpty()) {
            item {
                EmptyState(Icons.Outlined.BarChart, stringResource(R.string.dashboard_no_usage_title), stringResource(R.string.insights_no_app_usage))
            }
        } else {
            item {
                GlassCard(Modifier.fillMaxWidth(), contentPadding = 12.dp) {
                    state.apps.forEach { app ->
                        AppRow(
                            packageName = app.packageName,
                            label = app.label,
                            supporting = stringResource(R.string.apps_row_supporting, formatDuration(app.durationMs), app.launchCount),
                            onClick = { onOpenApp(app.packageName) },
                            bottom = { ProgressBar(app.share) },
                        )
                    }
                }
            }
        }
    }
}
