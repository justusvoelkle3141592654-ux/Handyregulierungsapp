package de.handyzeitvertreib.app.core.model

/** An installed, launchable application as reported by the package manager. */
data class InstalledApp(
    val packageName: String,
    val label: String,
    val isSystemApp: Boolean,
)

/** Foreground usage of one package inside one time window. */
data class AppUsage(
    val packageName: String,
    val foregroundMs: Long,
    val launchCount: Int,
    val lastUsedAt: Long?,
)

/** A daily time limit for a single app. */
data class AppLimit(
    val id: Long,
    val packageName: String,
    val dailyLimitMinutes: Int,
    val enabled: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

/** A combined daily time limit across several apps. */
data class GroupLimit(
    val id: Long,
    val name: String,
    val packageNames: Set<String>,
    val dailyLimitMinutes: Int,
    val enabled: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

enum class LimitType { APP, GROUP }

/** Stable identity of a limit across app and group limits. */
data class LimitKey(
    val type: LimitType,
    val id: Long,
)

enum class RegulationAction {
    LIMIT_REACHED,
    EXTENSION_GRANTED,
    EXTENSION_DENIED,
    EXTENSION_CANCELLED,
    LEFT_APP,
}

/** A locally recorded regulation event. Never leaves the device. */
data class RegulationEvent(
    val id: Long,
    val packageName: String?,
    val limitKey: LimitKey,
    val dayEpoch: Long,
    val occurredAt: Long,
    val action: RegulationAction,
    val extensionMinutes: Int,
)

/** User-controlled rules for requesting more time after a limit is reached. */
data class ExtensionPolicy(
    val enabled: Boolean,
    val extensionMinutes: Int,
    val maxExtensionsPerDay: Int,
    val allowDeviceCredential: Boolean,
) {
    companion object {
        val ALLOWED_MINUTES = listOf(5, 10, 15)
        val ALLOWED_MAX_PER_DAY = listOf(1, 2, 3)
        val DEFAULT = ExtensionPolicy(enabled = true, extensionMinutes = 5, maxExtensionsPerDay = 1, allowDeviceCredential = false)
    }
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class UserPreferences(
    val onboardingCompleted: Boolean,
    val themeMode: ThemeMode,
    val dynamicColor: Boolean,
    val reduceMotion: Boolean,
    val retentionDays: Int,
    val limitNotificationsEnabled: Boolean,
    val extensionPolicy: ExtensionPolicy,
    val excludedFromTotals: Set<String>,
) {
    companion object {
        val RETENTION_CHOICES = listOf(30, 90, 365)
        val DEFAULT =
            UserPreferences(
                onboardingCompleted = false,
                themeMode = ThemeMode.SYSTEM,
                dynamicColor = false,
                reduceMotion = false,
                retentionDays = 90,
                limitNotificationsEnabled = true,
                extensionPolicy = ExtensionPolicy.DEFAULT,
                excludedFromTotals = emptySet(),
            )
    }
}
