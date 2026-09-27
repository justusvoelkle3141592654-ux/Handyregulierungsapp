package de.handyzeitvertreib.app.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import de.handyzeitvertreib.app.data.db.HzvDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Opens the exported schema of every version with the current Room definitions.
 * When the version is bumped, add a migration test that migrates from the old schema.
 */
@RunWith(RobolectricTestRunner::class)
class DatabaseSchemaTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), HzvDatabase::class.java)

    @Test
    fun version1SchemaMatchesEntities() {
        helper.createDatabase(DB, 1).close()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db =
            Room
                .databaseBuilder(context, HzvDatabase::class.java, DB)
                .addMigrations(*HzvDatabase.MIGRATIONS)
                .allowMainThreadQueries()
                .build()
        runBlocking { assertThat(db.limitDao().appLimits()).isEmpty() }
        db.close()
    }

    private companion object {
        const val DB = "schema-test.db"
    }
}
