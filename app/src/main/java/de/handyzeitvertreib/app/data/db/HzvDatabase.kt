package de.handyzeitvertreib.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Local database. Schema changes must bump [version] and ship a migration; the exported
 * schema JSON in `app/schemas` is the reference for migration tests.
 */
@Database(
    entities = [
        UsageSnapshotEntity::class,
        DayRecordEntity::class,
        KnownAppEntity::class,
        AppLimitEntity::class,
        GroupLimitEntity::class,
        GroupMemberEntity::class,
        RegulationEventEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class HzvDatabase : RoomDatabase() {
    abstract fun usageDao(): UsageDao

    abstract fun knownAppDao(): KnownAppDao

    abstract fun limitDao(): LimitDao

    abstract fun regulationEventDao(): RegulationEventDao

    companion object {
        const val NAME = "handyzeitvertreib.db"

        /** Registered migrations. Add new ones here; destructive fallback is deliberately not enabled. */
        val MIGRATIONS = emptyArray<androidx.room.migration.Migration>()

        fun create(context: Context): HzvDatabase =
            Room
                .databaseBuilder(context, HzvDatabase::class.java, NAME)
                .addMigrations(*MIGRATIONS)
                .build()
    }
}
