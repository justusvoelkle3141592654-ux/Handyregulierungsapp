package de.handyzeitvertreib.app.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import de.handyzeitvertreib.app.core.model.ExtensionPolicy
import de.handyzeitvertreib.app.core.model.ThemeMode
import de.handyzeitvertreib.app.core.model.UserPreferences
import de.handyzeitvertreib.app.regulation.ExtensionRules
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

/** Small user settings stored with DataStore. Nothing here leaves the device. */
class PreferencesRepository(
    private val dataStore: DataStore<Preferences>,
) {
    val preferences: Flow<UserPreferences> =
        dataStore.data
            .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
            .map(::toModel)

    val lastRefreshAt: Flow<Long?> = dataStore.data.catch { emit(emptyPreferences()) }.map { it[Keys.LAST_REFRESH] }

    suspend fun current(): UserPreferences = preferences.first()

    suspend fun setOnboardingCompleted(completed: Boolean) = dataStore.edit { it[Keys.ONBOARDING_DONE] = completed }

    suspend fun setThemeMode(mode: ThemeMode) = dataStore.edit { it[Keys.THEME] = mode.name }

    suspend fun setDynamicColor(enabled: Boolean) = dataStore.edit { it[Keys.DYNAMIC_COLOR] = enabled }

    suspend fun setReduceMotion(enabled: Boolean) = dataStore.edit { it[Keys.REDUCE_MOTION] = enabled }

    suspend fun setRetentionDays(days: Int) {
        require(days in UserPreferences.RETENTION_CHOICES) { "Unsupported retention" }
        dataStore.edit { it[Keys.RETENTION_DAYS] = days }
    }

    suspend fun setLimitNotificationsEnabled(enabled: Boolean) = dataStore.edit { it[Keys.LIMIT_NOTIFICATIONS] = enabled }

    suspend fun setExtensionPolicy(policy: ExtensionPolicy) {
        val safe = ExtensionRules.sanitize(policy)
        dataStore.edit {
            it[Keys.EXT_ENABLED] = safe.enabled
            it[Keys.EXT_MINUTES] = safe.extensionMinutes
            it[Keys.EXT_MAX] = safe.maxExtensionsPerDay
            it[Keys.EXT_DEVICE_CREDENTIAL] = safe.allowDeviceCredential
        }
    }

    suspend fun setExcludedFromTotals(
        packageName: String,
        excluded: Boolean,
    ) = dataStore.edit {
        val current = it[Keys.EXCLUDED] ?: emptySet()
        it[Keys.EXCLUDED] = if (excluded) current + packageName else current - packageName
    }

    suspend fun setLastRefreshAt(timestampMs: Long) = dataStore.edit { it[Keys.LAST_REFRESH] = timestampMs }

    /** Resets everything, including onboarding. Used by "delete all local data". */
    suspend fun clear() = dataStore.edit { it.clear() }

    private fun toModel(prefs: Preferences): UserPreferences {
        val defaults = UserPreferences.DEFAULT
        return UserPreferences(
            onboardingCompleted = prefs[Keys.ONBOARDING_DONE] ?: defaults.onboardingCompleted,
            themeMode = prefs[Keys.THEME]?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } } ?: defaults.themeMode,
            dynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: defaults.dynamicColor,
            reduceMotion = prefs[Keys.REDUCE_MOTION] ?: defaults.reduceMotion,
            retentionDays = prefs[Keys.RETENTION_DAYS]?.takeIf { it in UserPreferences.RETENTION_CHOICES } ?: defaults.retentionDays,
            limitNotificationsEnabled = prefs[Keys.LIMIT_NOTIFICATIONS] ?: defaults.limitNotificationsEnabled,
            extensionPolicy =
                ExtensionRules.sanitize(
                    ExtensionPolicy(
                        enabled = prefs[Keys.EXT_ENABLED] ?: defaults.extensionPolicy.enabled,
                        extensionMinutes = prefs[Keys.EXT_MINUTES] ?: defaults.extensionPolicy.extensionMinutes,
                        maxExtensionsPerDay = prefs[Keys.EXT_MAX] ?: defaults.extensionPolicy.maxExtensionsPerDay,
                        allowDeviceCredential = prefs[Keys.EXT_DEVICE_CREDENTIAL] ?: defaults.extensionPolicy.allowDeviceCredential,
                    ),
                ),
            excludedFromTotals = prefs[Keys.EXCLUDED] ?: defaults.excludedFromTotals,
        )
    }

    private object Keys {
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_completed")
        val THEME = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val REDUCE_MOTION = booleanPreferencesKey("reduce_motion")
        val RETENTION_DAYS = intPreferencesKey("retention_days")
        val LIMIT_NOTIFICATIONS = booleanPreferencesKey("limit_notifications")
        val EXT_ENABLED = booleanPreferencesKey("extension_enabled")
        val EXT_MINUTES = intPreferencesKey("extension_minutes")
        val EXT_MAX = intPreferencesKey("extension_max_per_day")
        val EXT_DEVICE_CREDENTIAL = booleanPreferencesKey("extension_device_credential")
        val EXCLUDED = stringSetPreferencesKey("excluded_from_totals")
        val LAST_REFRESH = longPreferencesKey("last_refresh_at")
    }
}
