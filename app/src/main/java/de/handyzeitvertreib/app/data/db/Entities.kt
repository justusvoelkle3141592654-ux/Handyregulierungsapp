package de.handyzeitvertreib.app.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Aggregated foreground usage of one package on one local day. */
@Entity(tableName = "usage_snapshot", primaryKeys = ["dayEpoch", "packageName"])
data class UsageSnapshotEntity(
    val dayEpoch: Long,
    val packageName: String,
    val foregroundMs: Long,
    val launchCount: Int,
    val lastUsedAt: Long?,
)

/**
 * Marks that a day has been captured from the platform. Without a record the app does
 * not know anything about the day and must not show it as "zero usage".
 */
@Entity(tableName = "day_record")
data class DayRecordEntity(
    @PrimaryKey val dayEpoch: Long,
    val isComplete: Boolean,
    val capturedAt: Long,
    val zoneId: String,
)

/** Last known label of a package, so history stays readable after an app is uninstalled. */
@Entity(tableName = "known_app")
data class KnownAppEntity(
    @PrimaryKey val packageName: String,
    val label: String,
    val lastSeenAt: Long,
)

@Entity(tableName = "app_limit", indices = [Index(value = ["packageName"], unique = true)])
data class AppLimitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val dailyLimitMinutes: Int,
    val enabled: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "group_limit")
data class GroupLimitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val dailyLimitMinutes: Int,
    val enabled: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "group_member",
    primaryKeys = ["groupId", "packageName"],
    foreignKeys = [
        ForeignKey(
            entity = GroupLimitEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("packageName")],
)
data class GroupMemberEntity(
    val groupId: Long,
    val packageName: String,
)

/** Regulation events intentionally have no foreign key: history survives limit deletion. */
@Entity(tableName = "regulation_event", indices = [Index("dayEpoch")])
data class RegulationEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String?,
    val limitType: String,
    val limitId: Long,
    val dayEpoch: Long,
    val occurredAt: Long,
    val action: String,
    val extensionMinutes: Int,
)
