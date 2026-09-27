package de.handyzeitvertreib.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.handyzeitvertreib.app.AppContainer
import de.handyzeitvertreib.app.account.AccountState
import de.handyzeitvertreib.app.core.model.ExtensionPolicy
import de.handyzeitvertreib.app.core.model.ThemeMode
import de.handyzeitvertreib.app.core.model.UserPreferences
import de.handyzeitvertreib.app.enforcement.EnforcementCapabilities
import de.handyzeitvertreib.app.enforcement.EnforcementLevel
import de.handyzeitvertreib.app.permissions.PermissionSnapshot
import de.handyzeitvertreib.app.regulation.AuthCapability
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val preferences: UserPreferences,
    val permissions: PermissionSnapshot,
    val account: AccountState,
    val enforcementLevel: EnforcementLevel,
    val authCapability: AuthCapability,
)

enum class DataOperation { EXPORTED, EXPORT_FAILED, HISTORY_DELETED, EVENTS_DELETED, EVERYTHING_DELETED }

class SettingsViewModel(
    private val container: AppContainer,
) : ViewModel() {
    private val lastOperation = MutableStateFlow<DataOperation?>(null)
    val operation: StateFlow<DataOperation?> = lastOperation

    val state: StateFlow<SettingsUiState> =
        combine(
            container.preferencesRepository.preferences,
            container.permissionMonitor.state,
            container.accountRepository.state,
        ) { prefs, permissions, account ->
            SettingsUiState(
                preferences = prefs,
                permissions = permissions,
                account = account,
                enforcementLevel = EnforcementCapabilities.strongestLevel(permissions, prefs.limitNotificationsEnabled),
                authCapability = container.authenticator.capability(prefs.extensionPolicy.allowDeviceCredential),
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            SettingsUiState(
                UserPreferences.DEFAULT,
                container.permissionMonitor.state.value,
                container.accountRepository.state.value,
                EnforcementLevel.NONE,
                AuthCapability.UNKNOWN,
            ),
        )

    fun capabilityFor(policy: ExtensionPolicy): AuthCapability = container.authenticator.capability(policy.allowDeviceCredential)

    fun setThemeMode(mode: ThemeMode) = launch { container.preferencesRepository.setThemeMode(mode) }

    fun setDynamicColor(enabled: Boolean) = launch { container.preferencesRepository.setDynamicColor(enabled) }

    fun setReduceMotion(enabled: Boolean) = launch { container.preferencesRepository.setReduceMotion(enabled) }

    fun setNotifications(enabled: Boolean) = launch { container.preferencesRepository.setLimitNotificationsEnabled(enabled) }

    fun setRetention(days: Int) =
        launch {
            container.preferencesRepository.setRetentionDays(days)
            container.usageRepository.applyRetention(days)
        }

    fun saveExtensionPolicy(policy: ExtensionPolicy) = launch { container.preferencesRepository.setExtensionPolicy(policy) }

    fun refreshPermissions() {
        container.permissionMonitor.refresh()
    }

    fun signOut() = launch { container.accountRepository.signOut() }

    fun deleteHistory() =
        launch {
            container.localDataManager.deleteUsageHistory()
            container.usageRepository.refresh()
            lastOperation.value = DataOperation.HISTORY_DELETED
        }

    fun deleteEvents() =
        launch {
            container.localDataManager.deleteRegulationEvents()
            lastOperation.value = DataOperation.EVENTS_DELETED
        }

    fun deleteEverything() =
        launch {
            container.localDataManager.deleteEverything()
            lastOperation.value = DataOperation.EVERYTHING_DELETED
        }

    /** Writes the export through [write]; the caller owns the Storage Access Framework URI. */
    fun export(write: (String) -> Boolean) =
        launch {
            val json = container.localDataManager.exportJson()
            lastOperation.value = if (write(json)) DataOperation.EXPORTED else DataOperation.EXPORT_FAILED
        }

    fun clearOperation() {
        lastOperation.value = null
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}

/** True when [candidate] grants more or easier extra time than [current]. */
fun ExtensionPolicy.isLooserThan(current: ExtensionPolicy): Boolean =
    (enabled && !current.enabled) ||
        extensionMinutes > current.extensionMinutes ||
        maxExtensionsPerDay > current.maxExtensionsPerDay ||
        (allowDeviceCredential && !current.allowDeviceCredential)
