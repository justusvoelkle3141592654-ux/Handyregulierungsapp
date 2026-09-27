package de.handyzeitvertreib.app.account

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface AccountState {
    /** No identity provider is integrated in this build. The app is fully usable locally. */
    data object NotConfigured : AccountState

    data object SignedOut : AccountState

    data object Loading : AccountState

    data class SignedIn(
        val displayName: String,
    ) : AccountState

    data class Error(
        val kind: AccountError,
    ) : AccountState
}

enum class AccountError { OFFLINE, SERVER_UNAVAILABLE, REJECTED, UNKNOWN }

/**
 * Boundary for a future identity provider. An account holds identity and, at most,
 * non-sensitive preferences. Raw usage history is never part of this interface.
 */
interface AccountRepository {
    val state: StateFlow<AccountState>

    suspend fun signIn(): AccountState

    suspend fun signOut()

    suspend fun deleteAccount()
}

/**
 * The only implementation shipped today. It never pretends to sign anyone in.
 * A real provider replaces this class in `AppContainer`; tokens must then be kept in
 * Android Keystore-backed storage and must never be logged.
 */
class UnconfiguredAccountRepository : AccountRepository {
    private val mutableState = MutableStateFlow<AccountState>(AccountState.NotConfigured)
    override val state: StateFlow<AccountState> = mutableState.asStateFlow()

    override suspend fun signIn(): AccountState = AccountState.NotConfigured

    override suspend fun signOut() = Unit

    override suspend fun deleteAccount() = Unit
}

/** What the account screen may offer for a given state. */
data class AccountActions(
    val canSignIn: Boolean,
    val canSignOut: Boolean,
    val canDelete: Boolean,
    val showRetry: Boolean,
)

object AccountStateMapping {
    fun actions(state: AccountState): AccountActions =
        when (state) {
            AccountState.NotConfigured -> AccountActions(canSignIn = false, canSignOut = false, canDelete = false, showRetry = false)
            AccountState.SignedOut -> AccountActions(canSignIn = true, canSignOut = false, canDelete = false, showRetry = false)
            AccountState.Loading -> AccountActions(canSignIn = false, canSignOut = false, canDelete = false, showRetry = false)
            is AccountState.SignedIn -> AccountActions(canSignIn = false, canSignOut = true, canDelete = true, showRetry = false)
            is AccountState.Error -> AccountActions(canSignIn = false, canSignOut = false, canDelete = false, showRetry = true)
        }
}
