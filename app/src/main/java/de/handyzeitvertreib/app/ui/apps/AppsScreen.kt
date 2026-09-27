package de.handyzeitvertreib.app.ui.apps

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.handyzeitvertreib.app.R
import de.handyzeitvertreib.app.ui.common.formatDuration
import de.handyzeitvertreib.app.ui.common.formatMinutes
import de.handyzeitvertreib.app.ui.designsystem.AppRow
import de.handyzeitvertreib.app.ui.designsystem.ChipTone
import de.handyzeitvertreib.app.ui.designsystem.EmptyState
import de.handyzeitvertreib.app.ui.designsystem.GlassCard
import de.handyzeitvertreib.app.ui.designsystem.HzvShapes
import de.handyzeitvertreib.app.ui.designsystem.HzvSpacing
import de.handyzeitvertreib.app.ui.designsystem.HzvTheme
import de.handyzeitvertreib.app.ui.designsystem.LimitStatusChip

@Composable
fun AppsRoute(
    viewModel: AppsViewModel,
    onOpenApp: (String) -> Unit,
    contentPadding: PaddingValues,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AppsScreen(state, viewModel::setQuery, viewModel::setSort, viewModel::setFilter, onOpenApp, contentPadding)
}

@Composable
fun AppsScreen(
    state: AppsUiState,
    onQueryChange: (String) -> Unit,
    onSortChange: (AppSort) -> Unit,
    onFilterChange: (AppFilter) -> Unit,
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
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(
                stringResource(R.string.apps_title),
                style = MaterialTheme.typography.headlineMedium,
                color = HzvTheme.colors.textPrimary,
                modifier = Modifier.semantics { heading() },
            )
        }
        item {
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth().testTag("app-search"),
                placeholder = { Text(stringResource(R.string.apps_search_hint)) },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
                shape = HzvShapes.pill,
            )
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppSort.entries.forEach { sort ->
                    FilterChip(
                        selected = state.sort == sort,
                        onClick = { onSortChange(sort) },
                        label = { Text(stringResource(sortLabel(sort))) },
                    )
                }
            }
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = state.filter == filter,
                        onClick = { onFilterChange(filter) },
                        label = { Text(stringResource(filterLabel(filter))) },
                    )
                }
            }
        }
        if (!state.loading && !state.usageAvailable) {
            item {
                Text(
                    stringResource(R.string.apps_usage_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = HzvTheme.colors.warning,
                )
            }
        }
        when {
            state.loading ->
                item { Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            state.items.isEmpty() ->
                item {
                    EmptyState(
                        icon = Icons.Outlined.SearchOff,
                        title = stringResource(R.string.apps_empty_title),
                        body = stringResource(R.string.apps_empty_body),
                    )
                }
            else ->
                items(state.items, key = { it.packageName }) { item ->
                    GlassCard(Modifier.fillMaxWidth(), contentPadding = 4.dp) { AppListRow(item, onOpenApp) }
                }
        }
    }
}

@Composable
private fun AppListRow(
    item: AppListItem,
    onOpenApp: (String) -> Unit,
) {
    val supporting =
        when {
            !item.installed -> stringResource(R.string.apps_not_installed)
            item.todayMs == null -> stringResource(R.string.apps_usage_unknown)
            else -> {
                val opens = item.launchCount ?: 0
                stringResource(R.string.apps_row_supporting, formatDuration(item.todayMs), opens)
            }
        }
    AppRow(
        packageName = item.packageName,
        label = item.label,
        supporting = supporting,
        onClick = { onOpenApp(item.packageName) },
        trailing =
            item.limitMinutes?.let { minutes ->
                {
                    LimitStatusChip(
                        if (item.limitEnabled) formatMinutes(minutes) else stringResource(R.string.limit_status_paused),
                        if (item.limitEnabled) ChipTone.NEUTRAL else ChipTone.WARNING,
                    )
                }
            },
    )
}

private fun sortLabel(sort: AppSort) =
    when (sort) {
        AppSort.USAGE -> R.string.apps_sort_usage
        AppSort.NAME -> R.string.apps_sort_name
        AppSort.OPENS -> R.string.apps_sort_opens
    }

private fun filterLabel(filter: AppFilter) =
    when (filter) {
        AppFilter.ALL -> R.string.apps_filter_all
        AppFilter.WITH_LIMIT -> R.string.apps_filter_with_limit
        AppFilter.USED_TODAY -> R.string.apps_filter_used_today
    }
