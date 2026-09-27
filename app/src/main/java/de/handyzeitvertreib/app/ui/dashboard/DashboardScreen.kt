package de.handyzeitvertreib.app.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DataUsage
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.handyzeitvertreib.app.R
import de.handyzeitvertreib.app.core.model.LimitKey
import de.handyzeitvertreib.app.enforcement.EnforcementLevel
import de.handyzeitvertreib.app.ui.common.LimitItem
import de.handyzeitvertreib.app.ui.common.formatDuration
import de.handyzeitvertreib.app.ui.common.formatTime
import de.handyzeitvertreib.app.ui.designsystem.AppRow
import de.handyzeitvertreib.app.ui.designsystem.ChipTone
import de.handyzeitvertreib.app.ui.designsystem.EmptyState
import de.handyzeitvertreib.app.ui.designsystem.ErrorState
import de.handyzeitvertreib.app.ui.designsystem.GlassCard
import de.handyzeitvertreib.app.ui.designsystem.HzvSpacing
import de.handyzeitvertreib.app.ui.designsystem.HzvTheme
import de.handyzeitvertreib.app.ui.designsystem.LimitStatusChip
import de.handyzeitvertreib.app.ui.designsystem.PrimaryButton
import de.handyzeitvertreib.app.ui.designsystem.ProgressBar
import de.handyzeitvertreib.app.ui.designsystem.SectionHeader
import de.handyzeitvertreib.app.ui.designsystem.TabularNumbers

data class DashboardActions(
    val onRefresh: () -> Unit = {},
    val onSetUpUsageAccess: () -> Unit = {},
    val onOpenApp: (String) -> Unit = {},
    val onOpenLimits: () -> Unit = {},
    val onOpenApps: () -> Unit = {},
    val onOpenRegulation: (LimitKey, String?) -> Unit = { _, _ -> },
    val onSetUpEnforcement: () -> Unit = {},
)

@Composable
fun DashboardRoute(
    viewModel: DashboardViewModel,
    actions: DashboardActions,
    contentPadding: PaddingValues,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    DashboardScreen(state, actions.copy(onRefresh = viewModel::refresh), contentPadding)
}

@Composable
fun DashboardScreen(
    state: DashboardUiState,
    actions: DashboardActions,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    LazyColumn(
        Modifier.fillMaxSize().testTag("dashboard-list"),
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.dashboard_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = HzvTheme.colors.textPrimary,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                IconButton(onClick = actions.onRefresh) {
                    Icon(Icons.Outlined.Refresh, contentDescription = stringResource(R.string.action_refresh), tint = HzvTheme.colors.textPrimary)
                }
            }
        }
        when (state) {
            DashboardUiState.Loading -> {
                item {
                    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
            }

            DashboardUiState.AccessRequired -> {
                item {
                    EmptyState(
                        icon = Icons.Outlined.DataUsage,
                        title = stringResource(R.string.dashboard_access_required_title),
                        body = stringResource(R.string.dashboard_access_required_body),
                        actionLabel = stringResource(R.string.action_set_up_usage_access),
                        onAction = actions.onSetUpUsageAccess,
                    )
                }
            }

            DashboardUiState.Error -> {
                item {
                    ErrorState(
                        title = stringResource(R.string.error_usage_title),
                        body = stringResource(R.string.error_usage_body),
                        retryLabel = stringResource(R.string.action_retry),
                        onRetry = actions.onRefresh,
                    )
                }
            }

            is DashboardUiState.Ready -> {
                readyContent(state, actions)
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.readyContent(
    state: DashboardUiState.Ready,
    actions: DashboardActions,
) {
    item { TodayHero(state) }
    state.reachedLimits.forEach { limit ->
        item(key = "reached-${limit.key}") { ReachedCard(limit, actions) }
    }
    if (state.limits.isNotEmpty() && state.enforcementLevel < EnforcementLevel.SCREEN_ON_APP_OPEN) {
        item { EnforcementHint(state.enforcementLevel, actions.onSetUpEnforcement) }
    }
    item {
        SectionHeader(stringResource(R.string.dashboard_active_limits)) {
            TextButton(onClick = actions.onOpenLimits) { Text(stringResource(R.string.action_show_all)) }
        }
    }
    if (state.limits.isEmpty()) {
        item {
            EmptyState(
                icon = Icons.Outlined.Timer,
                title = stringResource(R.string.dashboard_no_limits_title),
                body = stringResource(R.string.dashboard_no_limits_body),
                actionLabel = stringResource(R.string.action_add_limit),
                onAction = actions.onOpenLimits,
            )
        }
    } else {
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                state.limits.take(4).forEachIndexed { index, limit ->
                    if (index > 0) Spacer(Modifier.height(16.dp))
                    LimitProgressRow(limit)
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.dashboard_limited_total, formatDuration(state.limitedTotalMs)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = HzvTheme.colors.textSecondary,
                )
            }
        }
    }
    item {
        SectionHeader(stringResource(R.string.dashboard_most_used)) {
            TextButton(onClick = actions.onOpenApps) { Text(stringResource(R.string.action_show_all)) }
        }
    }
    if (state.topApps.isEmpty()) {
        item {
            EmptyState(
                icon = Icons.Outlined.DataUsage,
                title = stringResource(R.string.dashboard_no_usage_title),
                body = stringResource(R.string.dashboard_no_usage_body),
            )
        }
    } else {
        item {
            GlassCard(Modifier.fillMaxWidth(), contentPadding = 12.dp) {
                state.topApps.forEach { app ->
                    AppRow(
                        packageName = app.packageName,
                        label = app.label,
                        supporting = formatDuration(app.durationMs),
                        onClick = { actions.onOpenApp(app.packageName) },
                        bottom = { ProgressBar(app.share) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TodayHero(state: DashboardUiState.Ready) {
    val context = LocalContext.current
    GlassCard(Modifier.fillMaxWidth(), strong = true) {
        Text(stringResource(R.string.dashboard_today_total), style = MaterialTheme.typography.labelLarge, color = HzvTheme.colors.textSecondary)
        Text(
            formatDuration(state.totalMs),
            style = MaterialTheme.typography.displayMedium.merge(TabularNumbers),
            color = HzvTheme.colors.textPrimary,
        )
        Spacer(Modifier.height(4.dp))
        val yesterday = state.yesterdaySameTimeMs
        val comparison =
            when {
                yesterday == null -> stringResource(R.string.dashboard_compare_unavailable)
                state.totalMs > yesterday -> stringResource(R.string.dashboard_compare_more, formatDuration(state.totalMs - yesterday))
                state.totalMs < yesterday -> stringResource(R.string.dashboard_compare_less, formatDuration(yesterday - state.totalMs))
                else -> stringResource(R.string.dashboard_compare_same)
            }
        Text(comparison, style = MaterialTheme.typography.bodyMedium, color = HzvTheme.colors.textSecondary)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.dashboard_last_updated, formatTime(context, state.refreshedAt)),
            style = MaterialTheme.typography.labelMedium,
            color = HzvTheme.colors.textSecondary,
        )
    }
}

@Composable
private fun ReachedCard(
    limit: LimitItem,
    actions: DashboardActions,
) {
    GlassCard(Modifier.fillMaxWidth(), strong = true) {
        LimitStatusChip(stringResource(R.string.limit_status_reached), ChipTone.REACHED)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.dashboard_reached_title, limit.title),
            style = MaterialTheme.typography.titleMedium,
            color = HzvTheme.colors.textPrimary,
        )
        Spacer(Modifier.height(12.dp))
        PrimaryButton(stringResource(R.string.action_view), onClick = { actions.onOpenRegulation(limit.key, limit.packageName) }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun EnforcementHint(
    level: EnforcementLevel,
    onSetUp: () -> Unit,
) {
    GlassCard(Modifier.fillMaxWidth(), onClick = onSetUp) {
        Text(stringResource(R.string.dashboard_enforcement_hint_title), style = MaterialTheme.typography.titleSmall, color = HzvTheme.colors.textPrimary)
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(
                if (level == EnforcementLevel.NOTIFICATION) R.string.dashboard_enforcement_hint_notification else R.string.dashboard_enforcement_hint_in_app,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = HzvTheme.colors.textSecondary,
        )
    }
}

@Composable
fun LimitProgressRow(limit: LimitItem) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                limit.title,
                style = MaterialTheme.typography.bodyLarge,
                color = HzvTheme.colors.textPrimary,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            LimitStatusChip(limitChipText(limit), limitChipTone(limit))
        }
        Spacer(Modifier.height(6.dp))
        ProgressBar(limit.progress, reached = limit.reached)
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(
                if (limit.isGroup) R.string.limit_usage_of_group else R.string.limit_usage_of_app,
                formatDuration(limit.usedMs),
                formatDuration(limit.effectiveLimitMs),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = HzvTheme.colors.textSecondary,
        )
    }
}

@Composable
fun limitChipText(limit: LimitItem): String =
    when {
        limit.reached -> stringResource(R.string.limit_status_reached)
        else -> stringResource(R.string.limit_status_remaining, formatDuration(limit.remainingMs))
    }

fun limitChipTone(limit: LimitItem): ChipTone =
    when {
        limit.reached -> ChipTone.REACHED
        limit.nearlyReached -> ChipTone.WARNING
        else -> ChipTone.OK
    }
