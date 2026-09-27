package de.handyzeitvertreib.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.handyzeitvertreib.app.AppContainer
import de.handyzeitvertreib.app.enforcement.EnforcementCapabilities
import de.handyzeitvertreib.app.enforcement.EnforcementLevel
import de.handyzeitvertreib.app.limits.LimitEvaluator
import de.handyzeitvertreib.app.ui.common.LimitItem
import de.handyzeitvertreib.app.ui.common.LimitItems
import de.handyzeitvertreib.app.usage.TodayUsageState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class UsageItem(
    val packageName: String,
    val label: String,
    val durationMs: Long,
    val launchCount: Int,
    val share: Float,
)

sealed interface DashboardUiState {
    data object Loading : DashboardUiState

    data object AccessRequired : DashboardUiState

    data object Error : DashboardUiState

    data class Ready(
        val totalMs: Long,
        val yesterdaySameTimeMs: Long?,
        val topApps: List<UsageItem>,
        val limits: List<LimitItem>,
        val limitedTotalMs: Long,
        val refreshedAt: Long,
        val enforcementLevel: EnforcementLevel,
    ) : DashboardUiState {
        val reachedLimits: List<LimitItem> get() = limits.filter { it.reached }
    }
}

class DashboardViewModel(
    private val container: AppContainer,
) : ViewModel() {
    val state: StateFlow<DashboardUiState> =
        combine(
            container.usageRepository.today,
            container.regulationCoordinator.observe(),
            container.permissionMonitor.state,
            container.preferencesRepository.preferences,
            container.usageRepository.observeKnownLabels(),
        ) { usage, snapshot, permissions, prefs, labels ->
            when (usage) {
                TodayUsageState.Loading -> DashboardUiState.Loading
                TodayUsageState.AccessRequired -> DashboardUiState.AccessRequired
                TodayUsageState.Error -> DashboardUiState.Error
                is TodayUsageState.Ready -> {
                    val excluded = container.usageRepository.excludedPackages()
                    val visible = usage.apps.filter { it.packageName !in excluded && it.foregroundMs > 0 }
                    val top = visible.take(TOP_APPS)
                    val evaluation = snapshot?.evaluation
                    DashboardUiState.Ready(
                        totalMs = usage.totalMs,
                        yesterdaySameTimeMs = usage.yesterdaySameTimeMs,
                        topApps =
                            top.map {
                                UsageItem(
                                    packageName = it.packageName,
                                    label = labels[it.packageName] ?: it.packageName,
                                    durationMs = it.foregroundMs,
                                    launchCount = it.launchCount,
                                    share = if (usage.totalMs > 0) it.foregroundMs.toFloat() / usage.totalMs else 0f,
                                )
                            },
                        limits = evaluation?.let { LimitItems.sorted(it, labels) }.orEmpty(),
                        limitedTotalMs = evaluation?.let { LimitEvaluator.totalLimitedUsageMs(it, usage.usageByPackage) } ?: 0L,
                        refreshedAt = usage.refreshedAt,
                        enforcementLevel = EnforcementCapabilities.strongestLevel(permissions, prefs.limitNotificationsEnabled),
                    )
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState.Loading)

    fun refresh() {
        viewModelScope.launch {
            container.permissionMonitor.refresh()
            container.usageRepository.refresh()
        }
    }

    private companion object {
        const val TOP_APPS = 5
    }
}
