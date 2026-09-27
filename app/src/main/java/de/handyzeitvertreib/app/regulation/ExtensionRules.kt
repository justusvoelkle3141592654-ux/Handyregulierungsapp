package de.handyzeitvertreib.app.regulation

import de.handyzeitvertreib.app.core.model.ExtensionPolicy

/** Whether the device can confirm an extension, derived from `BiometricManager`. */
enum class AuthCapability {
    AVAILABLE,
    NONE_ENROLLED,
    NO_HARDWARE,
    HARDWARE_UNAVAILABLE,
    SECURITY_UPDATE_REQUIRED,
    UNSUPPORTED,
    UNKNOWN,
}

sealed interface ExtensionAvailability {
    data class Available(
        val minutes: Int,
        val remainingToday: Int,
    ) : ExtensionAvailability

    data object DisabledByUser : ExtensionAvailability

    data class DailyCapReached(
        val maxPerDay: Int,
    ) : ExtensionAvailability

    data class AuthenticationUnavailable(
        val capability: AuthCapability,
    ) : ExtensionAvailability
}

object ExtensionRules {
    /** Extra time is only ever offered behind an authentication step; there is no silent path. */
    fun availability(
        policy: ExtensionPolicy,
        grantedToday: Int,
        capability: AuthCapability,
    ): ExtensionAvailability =
        when {
            !policy.enabled -> ExtensionAvailability.DisabledByUser
            grantedToday >= policy.maxExtensionsPerDay -> ExtensionAvailability.DailyCapReached(policy.maxExtensionsPerDay)
            capability != AuthCapability.AVAILABLE -> ExtensionAvailability.AuthenticationUnavailable(capability)
            else -> ExtensionAvailability.Available(policy.extensionMinutes, policy.maxExtensionsPerDay - grantedToday)
        }

    /** Coerces persisted values to the allowed choices so a corrupted preference cannot widen the policy. */
    fun sanitize(policy: ExtensionPolicy): ExtensionPolicy =
        policy.copy(
            extensionMinutes =
                policy.extensionMinutes.takeIf { it in ExtensionPolicy.ALLOWED_MINUTES }
                    ?: ExtensionPolicy.DEFAULT.extensionMinutes,
            maxExtensionsPerDay =
                policy.maxExtensionsPerDay.takeIf { it in ExtensionPolicy.ALLOWED_MAX_PER_DAY }
                    ?: ExtensionPolicy.DEFAULT.maxExtensionsPerDay,
        )
}
