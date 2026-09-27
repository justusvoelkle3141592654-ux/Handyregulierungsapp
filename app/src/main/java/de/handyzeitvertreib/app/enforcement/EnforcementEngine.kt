package de.handyzeitvertreib.app.enforcement

import de.handyzeitvertreib.app.permissions.GrantState
import de.handyzeitvertreib.app.permissions.PermissionSnapshot

/**
 * How strongly the app can currently regulate. Android offers no guaranteed way for a
 * normal app to block other apps; each level depends on permissions the user grants.
 */
enum class EnforcementLevel {
    /** Usage access missing: limits cannot be measured at all. */
    NONE,

    /** Limits are measured and shown when Handyzeitvertreib is opened. */
    IN_APP_ONLY,

    /** Additionally a notification is posted within about 15 minutes after a limit is reached. */
    NOTIFICATION,

    /** The regulation screen opens when a limited app comes to the foreground. */
    SCREEN_ON_APP_OPEN,
}

/** One regulation mechanism with its own capability check. */
interface EnforcementEngine {
    val level: EnforcementLevel

    fun isAvailable(permissions: PermissionSnapshot): Boolean
}

object NotificationEnforcement : EnforcementEngine {
    override val level = EnforcementLevel.NOTIFICATION

    override fun isAvailable(permissions: PermissionSnapshot) = permissions.canReadUsage && permissions.notifications == GrantState.GRANTED
}

object AccessibilityEnforcement : EnforcementEngine {
    override val level = EnforcementLevel.SCREEN_ON_APP_OPEN

    override fun isAvailable(permissions: PermissionSnapshot) = permissions.canReadUsage && permissions.accessibilityService == GrantState.GRANTED
}

object EnforcementCapabilities {
    private val engines = listOf(AccessibilityEnforcement, NotificationEnforcement)

    fun strongestLevel(
        permissions: PermissionSnapshot,
        notificationsEnabledInApp: Boolean,
    ): EnforcementLevel {
        if (!permissions.canReadUsage) return EnforcementLevel.NONE
        return engines
            .filter { it.isAvailable(permissions) }
            .filter { it != NotificationEnforcement || notificationsEnabledInApp }
            .maxOfOrNull { it.level } ?: EnforcementLevel.IN_APP_ONLY
    }
}
