package de.handyzeitvertreib.app.regulation

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.handyzeitvertreib.app.AppContainer
import de.handyzeitvertreib.app.biometric.AuthRequest
import de.handyzeitvertreib.app.biometric.AuthResult
import de.handyzeitvertreib.app.core.model.LimitKey
import de.handyzeitvertreib.app.core.model.LimitType
import de.handyzeitvertreib.app.core.model.RegulationAction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class ExtensionOutcome { GRANTED, CANCELLED, LOCKED_OUT, LOCKED_OUT_PERMANENT, NOT_AVAILABLE, FAILED }

sealed interface RegulationUiState {
    data object Loading : RegulationUiState

    /** Usage data or the limit itself is not available (e.g. deleted limit, revoked access). */
    data object Unavailable : RegulationUiState

    data class Ready(
        val key: LimitKey,
        val packageName: String?,
        val title: String,
        val usedMs: Long,
        val limitMs: Long,
        val remainingMs: Long,
        val reached: Boolean,
        val reachedAt: Long?,
        val extension: ExtensionAvailability,
        val authenticating: Boolean = false,
        val outcome: ExtensionOutcome? = null,
    ) : RegulationUiState {
        val isGroup: Boolean get() = key.type == LimitType.GROUP
    }
}

class RegulationViewModel(
    private val container: AppContainer,
) : ViewModel() {
    private val mutableState = MutableStateFlow<RegulationUiState>(RegulationUiState.Loading)
    val state: StateFlow<RegulationUiState> = mutableState.asStateFlow()

    private var key: LimitKey? = null
    private var packageName: String? = null

    /** Sets the limit to show. Called for the launching intent and for every new intent. */
    fun bind(
        key: LimitKey,
        packageName: String?,
    ) {
        if (key != this.key || packageName != this.packageName) mutableState.value = RegulationUiState.Loading
        this.key = key
        this.packageName = packageName
        load()
    }

    fun load(outcome: ExtensionOutcome? = null) {
        val key = key ?: return
        val packageName = packageName
        viewModelScope.launch {
            val snapshot = container.regulationCoordinator.evaluateNow()
            val status = snapshot?.evaluation?.statuses?.firstOrNull { it.key == key }
            if (snapshot == null || status == null) {
                mutableState.value = RegulationUiState.Unavailable
                return@launch
            }
            val prefs = container.preferencesRepository.current()
            val labels = container.usageRepository.observeKnownLabels().first()
            val granted = RegulationRepository.extensionsGranted(snapshot.events, key)
            val capability = container.authenticator.capability(prefs.extensionPolicy.allowDeviceCredential)
            mutableState.value =
                RegulationUiState.Ready(
                    key = key,
                    packageName = packageName,
                    title = status.name ?: packageName?.let { labels[it] ?: it }.orEmpty(),
                    usedMs = status.usedMs,
                    limitMs = status.effectiveLimitMs,
                    remainingMs = status.remainingMs,
                    reached = status.isReached,
                    reachedAt = RegulationRepository.limitReachedAt(snapshot.events, key),
                    extension = ExtensionRules.availability(prefs.extensionPolicy, granted, capability),
                    outcome = outcome,
                )
        }
    }

    /** Runs the system authentication prompt; extra time is recorded only after success. */
    fun requestExtension(
        activity: FragmentActivity,
        request: (minutes: Int) -> AuthRequest,
    ) {
        val current = mutableState.value as? RegulationUiState.Ready ?: return
        val key = current.key
        val packageName = current.packageName
        val available = current.extension as? ExtensionAvailability.Available ?: return
        if (current.authenticating) return
        mutableState.value = current.copy(authenticating = true, outcome = null)
        viewModelScope.launch {
            val policy = container.preferencesRepository.current().extensionPolicy
            val result = container.authenticator.authenticate(activity, request(available.minutes).copy(allowDeviceCredential = policy.allowDeviceCredential))
            val outcome =
                when (result) {
                    AuthResult.Success -> {
                        container.regulationCoordinator.recordExtension(key, packageName, available.minutes)
                        ExtensionOutcome.GRANTED
                    }
                    AuthResult.Cancelled -> {
                        container.regulationCoordinator.recordOutcome(key, packageName, RegulationAction.EXTENSION_CANCELLED)
                        ExtensionOutcome.CANCELLED
                    }
                    is AuthResult.LockedOut -> {
                        container.regulationCoordinator.recordOutcome(key, packageName, RegulationAction.EXTENSION_DENIED)
                        if (result.permanent) ExtensionOutcome.LOCKED_OUT_PERMANENT else ExtensionOutcome.LOCKED_OUT
                    }
                    AuthResult.NotAvailable -> ExtensionOutcome.NOT_AVAILABLE
                    is AuthResult.Failed -> {
                        container.regulationCoordinator.recordOutcome(key, packageName, RegulationAction.EXTENSION_DENIED)
                        ExtensionOutcome.FAILED
                    }
                }
            load(outcome)
        }
    }

    fun recordLeft() {
        val key = key ?: return
        val packageName = packageName
        viewModelScope.launch { container.regulationCoordinator.recordOutcome(key, packageName, RegulationAction.LEFT_APP) }
    }
}
