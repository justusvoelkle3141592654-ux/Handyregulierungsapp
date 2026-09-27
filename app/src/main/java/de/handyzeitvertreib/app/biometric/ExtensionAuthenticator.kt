package de.handyzeitvertreib.app.biometric

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import de.handyzeitvertreib.app.regulation.AuthCapability
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

sealed interface AuthResult {
    data object Success : AuthResult

    data object Cancelled : AuthResult

    data class LockedOut(
        val permanent: Boolean,
    ) : AuthResult

    data object NotAvailable : AuthResult

    data class Failed(
        val errorCode: Int,
    ) : AuthResult
}

data class AuthRequest(
    val title: String,
    val subtitle: String,
    val negativeButton: String,
    val allowDeviceCredential: Boolean,
)

/**
 * Confirms an extension. The only production implementation uses Android's
 * [BiometricPrompt]; the app never sees or stores biometric data, only the outcome.
 */
interface ExtensionAuthenticator {
    fun capability(allowDeviceCredential: Boolean): AuthCapability

    suspend fun authenticate(
        activity: FragmentActivity,
        request: AuthRequest,
    ): AuthResult
}

object BiometricMapping {
    /**
     * Class 2 ("weak") biometrics include fingerprint and most face unlock implementations.
     * Device credential (PIN/pattern/password) is added only when the user allowed it.
     */
    fun authenticators(allowDeviceCredential: Boolean): Int = if (allowDeviceCredential) BIOMETRIC_WEAK or DEVICE_CREDENTIAL else BIOMETRIC_WEAK

    fun capability(canAuthenticateResult: Int): AuthCapability =
        when (canAuthenticateResult) {
            BiometricManager.BIOMETRIC_SUCCESS -> AuthCapability.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> AuthCapability.NONE_ENROLLED
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> AuthCapability.NO_HARDWARE
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> AuthCapability.HARDWARE_UNAVAILABLE
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> AuthCapability.SECURITY_UPDATE_REQUIRED
            BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED -> AuthCapability.UNSUPPORTED
            else -> AuthCapability.UNKNOWN
        }

    fun error(errorCode: Int): AuthResult =
        when (errorCode) {
            BiometricPrompt.ERROR_USER_CANCELED,
            BiometricPrompt.ERROR_NEGATIVE_BUTTON,
            BiometricPrompt.ERROR_CANCELED,
            -> AuthResult.Cancelled

            BiometricPrompt.ERROR_LOCKOUT -> AuthResult.LockedOut(permanent = false)

            BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> AuthResult.LockedOut(permanent = true)

            BiometricPrompt.ERROR_NO_BIOMETRICS,
            BiometricPrompt.ERROR_HW_NOT_PRESENT,
            BiometricPrompt.ERROR_HW_UNAVAILABLE,
            BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL,
            -> AuthResult.NotAvailable

            else -> AuthResult.Failed(errorCode)
        }
}

class AndroidExtensionAuthenticator(
    private val context: Context,
) : ExtensionAuthenticator {
    override fun capability(allowDeviceCredential: Boolean): AuthCapability =
        BiometricMapping.capability(
            BiometricManager.from(context).canAuthenticate(BiometricMapping.authenticators(allowDeviceCredential)),
        )

    override suspend fun authenticate(
        activity: FragmentActivity,
        request: AuthRequest,
    ): AuthResult =
        suspendCancellableCoroutine { continuation ->
            val callback =
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        if (continuation.isActive) continuation.resume(AuthResult.Success)
                    }

                    override fun onAuthenticationError(
                        errorCode: Int,
                        errString: CharSequence,
                    ) {
                        if (continuation.isActive) continuation.resume(BiometricMapping.error(errorCode))
                    }

                    // onAuthenticationFailed is a single rejected attempt; the system prompt stays open.
                }
            val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback)
            val info =
                BiometricPrompt.PromptInfo
                    .Builder()
                    .setTitle(request.title)
                    .setSubtitle(request.subtitle)
                    .setAllowedAuthenticators(BiometricMapping.authenticators(request.allowDeviceCredential))
                    .setConfirmationRequired(true)
                    .apply { if (!request.allowDeviceCredential) setNegativeButtonText(request.negativeButton) }
                    .build()
            continuation.invokeOnCancellation { prompt.cancelAuthentication() }
            prompt.authenticate(info)
        }
}
