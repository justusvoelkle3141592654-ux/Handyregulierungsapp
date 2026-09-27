package de.handyzeitvertreib.app.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.handyzeitvertreib.app.AppContainer
import de.handyzeitvertreib.app.core.model.ExtensionPolicy
import de.handyzeitvertreib.app.core.model.InstalledApp
import de.handyzeitvertreib.app.core.model.LimitRules
import de.handyzeitvertreib.app.core.model.UserPreferences
import de.handyzeitvertreib.app.permissions.PermissionSnapshot
import de.handyzeitvertreib.app.regulation.AuthCapability
import de.handyzeitvertreib.app.usage.TodayUsageState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class OnboardingStep {
    WELCOME,
    PURPOSE,
    PRIVACY,
    USAGE_ACCESS,
    NOTIFICATIONS,
    ENFORCEMENT,
    ACCOUNT,
    STARTER_APPS,
    EXTENSION,
    DONE,
}

data class OnboardingDraft(
    val step: OnboardingStep = OnboardingStep.WELCOME,
    val retentionDays: Int = UserPreferences.DEFAULT.retentionDays,
    val selectedApps: Set<String> = emptySet(),
    val starterMinutes: Int = LimitRules.DEFAULT_MINUTES,
    val extensionPolicy: ExtensionPolicy = ExtensionPolicy.DEFAULT,
    val finishing: Boolean = false,
)

data class OnboardingUiState(
    val draft: OnboardingDraft,
    val permissions: PermissionSnapshot,
    val starterApps: List<InstalledApp>,
    val authCapability: AuthCapability,
) {
    val step: OnboardingStep get() = draft.step
    val stepIndex: Int get() = step.ordinal
    val stepCount: Int get() = OnboardingStep.entries.size
}

class OnboardingViewModel(
    private val container: AppContainer,
) : ViewModel() {
    private val draft = MutableStateFlow(OnboardingDraft())
    private val apps = MutableStateFlow<List<InstalledApp>>(emptyList())

    val state: StateFlow<OnboardingUiState> =
        combine(draft, container.permissionMonitor.state, apps) { d, permissions, list ->
            OnboardingUiState(d, permissions, list, container.authenticator.capability(d.extensionPolicy.allowDeviceCredential))
        }.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            OnboardingUiState(OnboardingDraft(), container.permissionMonitor.state.value, emptyList(), AuthCapability.UNKNOWN),
        )

    fun next() {
        val current = draft.value.step
        if (current == OnboardingStep.USAGE_ACCESS) loadStarterApps()
        if (current.ordinal < OnboardingStep.entries.lastIndex) {
            draft.value = draft.value.copy(step = OnboardingStep.entries[current.ordinal + 1])
        }
    }

    fun back() {
        val current = draft.value.step
        if (current.ordinal > 0) draft.value = draft.value.copy(step = OnboardingStep.entries[current.ordinal - 1])
    }

    fun setRetention(days: Int) {
        if (days in UserPreferences.RETENTION_CHOICES) draft.value = draft.value.copy(retentionDays = days)
    }

    fun toggleApp(packageName: String) {
        val selected = draft.value.selectedApps
        draft.value = draft.value.copy(selectedApps = if (packageName in selected) selected - packageName else selected + packageName)
    }

    fun setStarterMinutes(minutes: Int) {
        draft.value = draft.value.copy(starterMinutes = LimitRules.normalize(minutes))
    }

    fun setExtensionPolicy(policy: ExtensionPolicy) {
        draft.value = draft.value.copy(extensionPolicy = policy)
    }

    fun refreshPermissions() {
        container.permissionMonitor.refresh()
    }

    /** Suggests the most-used apps first when usage access is already granted. */
    private fun loadStarterApps() {
        viewModelScope.launch {
            val installed = container.usageRepository.installedApps()
            val usage = container.usageRepository.refresh() as? TodayUsageState.Ready
            val rank =
                usage
                    ?.apps
                    ?.mapIndexed { index, app -> app.packageName to index }
                    ?.toMap()
                    .orEmpty()
            apps.value =
                installed.sortedWith(compareBy<InstalledApp> { rank[it.packageName] ?: Int.MAX_VALUE }.thenBy { it.label.lowercase() })
        }
    }

    fun finish(onFinished: () -> Unit) {
        val current = draft.value
        if (current.finishing) return
        draft.value = current.copy(finishing = true)
        viewModelScope.launch {
            val prefs = container.preferencesRepository
            prefs.setRetentionDays(current.retentionDays)
            prefs.setExtensionPolicy(current.extensionPolicy)
            current.selectedApps.forEach { container.limitRepository.saveAppLimit(it, current.starterMinutes) }
            prefs.setOnboardingCompleted(true)
            container.usageRepository.refresh()
            onFinished()
        }
    }
}
