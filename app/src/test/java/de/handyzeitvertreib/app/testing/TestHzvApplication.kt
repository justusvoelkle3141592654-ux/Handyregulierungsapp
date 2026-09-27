package de.handyzeitvertreib.app.testing

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import de.handyzeitvertreib.app.AppContainer
import de.handyzeitvertreib.app.HzvApplication
import de.handyzeitvertreib.app.account.UnconfiguredAccountRepository
import de.handyzeitvertreib.app.data.db.HzvDatabase
import de.handyzeitvertreib.app.data.prefs.PreferencesRepository
import de.handyzeitvertreib.app.enforcement.LimitNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

/** Robolectric application with in-memory database, temporary DataStore and fakes. */
class TestHzvApplication : HzvApplication() {
    val clock = FakeClock()
    val events = FakeUsageEventSource()
    val installedApps = FakeInstalledApps()
    val permissions = FakePermissions()
    val authenticator = FakeAuthenticator()

    override fun createContainer(): AppContainer {
        val database =
            Room
                .inMemoryDatabaseBuilder(this, HzvDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        val prefsFile = File(cacheDir, "test-${System.nanoTime()}.preferences_pb")
        val dataStore =
            PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + SupervisorJob())) { prefsFile }
        return AppContainer(
            clock = clock,
            database = database,
            preferencesRepository = PreferencesRepository(dataStore),
            permissionProvider = permissions,
            usageEventSource = events,
            installedAppsSource = installedApps,
            authenticator = authenticator,
            accountRepository = UnconfiguredAccountRepository(),
            limitNotifier = LimitNotifier(this),
            ioDispatcher = Dispatchers.IO,
        )
    }

    override fun startBackgroundWork() = Unit
}
