package de.handyzeitvertreib.app.ui.apps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.handyzeitvertreib.app.AppContainer
import de.handyzeitvertreib.app.core.model.InstalledApp
import de.handyzeitvertreib.app.usage.TodayUsageState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AppsUiState(
    val loading: Boolean = true,
    val usageAvailable: Boolean = false,
    val query: String = "",
    val sort: AppSort = AppSort.USAGE,
    val filter: AppFilter = AppFilter.ALL,
    val items: List<AppListItem> = emptyList(),
    val totalCount: Int = 0,
)

private data class Controls(
    val query: String,
    val sort: AppSort,
    val filter: AppFilter,
)

class AppsViewModel(
    private val container: AppContainer,
) : ViewModel() {
    private val installed = MutableStateFlow<List<InstalledApp>?>(null)
    private val controls = MutableStateFlow(Controls("", AppSort.USAGE, AppFilter.ALL))

    val state: StateFlow<AppsUiState> =
        combine(
            installed,
            container.usageRepository.today,
            container.limitRepository.appLimits,
            container.usageRepository.observeKnownLabels(),
            controls,
        ) { apps, usage, limits, labels, c ->
            if (apps == null) return@combine AppsUiState(query = c.query, sort = c.sort, filter = c.filter)
            val ready = usage as? TodayUsageState.Ready
            val usageByPkg = ready?.apps?.associateBy { it.packageName }.orEmpty()
            val limitByPkg = limits.associateBy { it.packageName }
            val installedPkgs = apps.map { it.packageName }.toSet()
            val all =
                apps.map { app ->
                    AppListItem(
                        packageName = app.packageName,
                        label = app.label,
                        todayMs = if (ready == null) null else usageByPkg[app.packageName]?.foregroundMs ?: 0L,
                        launchCount = if (ready == null) null else usageByPkg[app.packageName]?.launchCount ?: 0,
                        limitMinutes = limitByPkg[app.packageName]?.dailyLimitMinutes,
                        limitEnabled = limitByPkg[app.packageName]?.enabled == true,
                        installed = true,
                    )
                } +
                    limits.filter { it.packageName !in installedPkgs }.map { limit ->
                        AppListItem(
                            packageName = limit.packageName,
                            label = labels[limit.packageName] ?: limit.packageName,
                            todayMs = null,
                            launchCount = null,
                            limitMinutes = limit.dailyLimitMinutes,
                            limitEnabled = limit.enabled,
                            installed = false,
                        )
                    }
            AppsUiState(
                loading = false,
                usageAvailable = ready != null,
                query = c.query,
                sort = c.sort,
                filter = c.filter,
                items = AppListFilter.apply(all, c.query, c.sort, c.filter),
                totalCount = all.size,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppsUiState())

    init {
        viewModelScope.launch { installed.value = container.usageRepository.installedApps() }
    }

    fun setQuery(query: String) {
        controls.value = controls.value.copy(query = query)
    }

    fun setSort(sort: AppSort) {
        controls.value = controls.value.copy(sort = sort)
    }

    fun setFilter(filter: AppFilter) {
        controls.value = controls.value.copy(filter = filter)
    }
}
