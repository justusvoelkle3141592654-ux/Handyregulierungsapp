package de.handyzeitvertreib.app

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import de.handyzeitvertreib.app.account.AccountRepository
import de.handyzeitvertreib.app.account.UnconfiguredAccountRepository
import de.handyzeitvertreib.app.biometric.AndroidExtensionAuthenticator
import de.handyzeitvertreib.app.biometric.ExtensionAuthenticator
import de.handyzeitvertreib.app.core.time.AppClock
import de.handyzeitvertreib.app.core.time.SystemAppClock
import de.handyzeitvertreib.app.data.db.HzvDatabase
import de.handyzeitvertreib.app.data.management.LocalDataManager
import de.handyzeitvertreib.app.data.prefs.PreferencesRepository
import de.handyzeitvertreib.app.enforcement.LimitNotifier
import de.handyzeitvertreib.app.limits.LimitRepository
import de.handyzeitvertreib.app.permissions.PermissionChecker
import de.handyzeitvertreib.app.permissions.PermissionMonitor
import de.handyzeitvertreib.app.permissions.PermissionProvider
import de.handyzeitvertreib.app.permissions.GrantState
import de.handyzeitvertreib.app.regulation.RegulationCoordinator
import de.handyzeitvertreib.app.regulation.RegulationRepository
import de.handyzeitvertreib.app.usage.AndroidInstalledAppsSource
import de.handyzeitvertreib.app.usage.AndroidUsageEventSource
import de.handyzeitvertreib.app.usage.InstalledAppsSource
import de.handyzeitvertreib.app.usage.UsageEventSource
import de.handyzeitvertreib.app.usage.UsageRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

private val Context.dataStore by preferencesDataStore(name = "settings")

/**
 * Manual dependency container. Every Android-facing dependency is an interface so tests
 * can pass fakes through [AppContainer.create] parameters.
 */
class AppContainer(
    val clock: AppClock,
    val database: HzvDatabase,
    val preferencesRepository: PreferencesRepository,
    permissionProvider: PermissionProvider,
    usageEventSource: UsageEventSource,
    installedAppsSource: InstalledAppsSource,
    val authenticator: ExtensionAuthenticator,
    val accountRepository: AccountRepository,
    val limitNotifier: LimitNotifier,
    ioDispatcher: CoroutineDispatcher,
) {
    val permissionMonitor = PermissionMonitor(permissionProvider)
    val usageRepository =
        UsageRepository(
            usageEventSource,
            installedAppsSource,
            database.usageDao(),
            database.knownAppDao(),
            preferencesRepository,
            clock,
            ioDispatcher,
        )
    val limitRepository = LimitRepository(database.limitDao(), clock)
    val regulationRepository = RegulationRepository(database.regulationEventDao(), clock)
    val regulationCoordinator = RegulationCoordinator(usageRepository, limitRepository, regulationRepository)
    val localDataManager = LocalDataManager(database, preferencesRepository, usageRepository, ioDispatcher)

    companion object {
        fun create(context: Context): AppContainer {
            val app = context.applicationContext
            val permissions = PermissionChecker(app)
            return AppContainer(
                clock = SystemAppClock,
                database = HzvDatabase.create(app),
                preferencesRepository = PreferencesRepository(app.dataStore),
                permissionProvider = permissions,
                usageEventSource = AndroidUsageEventSource(app) { permissions.usageAccess() == GrantState.GRANTED },
                installedAppsSource = AndroidInstalledAppsSource(app),
                authenticator = AndroidExtensionAuthenticator(app),
                accountRepository = UnconfiguredAccountRepository(),
                limitNotifier = LimitNotifier(app),
                ioDispatcher = Dispatchers.IO,
            )
        }
    }
}
