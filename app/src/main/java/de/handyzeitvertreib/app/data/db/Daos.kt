package de.handyzeitvertreib.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface UsageDao {
    @Upsert
    suspend fun upsertSnapshots(snapshots: List<UsageSnapshotEntity>)

    @Upsert
    suspend fun upsertDayRecord(record: DayRecordEntity)

    @Query("DELETE FROM usage_snapshot WHERE dayEpoch = :dayEpoch")
    suspend fun deleteSnapshotsForDay(dayEpoch: Long)

    /** Replaces a day's snapshots atomically so removed or renamed entries do not linger. */
    @Transaction
    suspend fun replaceDay(
        record: DayRecordEntity,
        snapshots: List<UsageSnapshotEntity>,
    ) {
        deleteSnapshotsForDay(record.dayEpoch)
        upsertSnapshots(snapshots)
        upsertDayRecord(record)
    }

    @Query("SELECT * FROM usage_snapshot WHERE dayEpoch BETWEEN :fromDay AND :toDay")
    fun observeSnapshots(
        fromDay: Long,
        toDay: Long,
    ): Flow<List<UsageSnapshotEntity>>

    @Query("SELECT * FROM day_record WHERE dayEpoch BETWEEN :fromDay AND :toDay")
    fun observeDayRecords(
        fromDay: Long,
        toDay: Long,
    ): Flow<List<DayRecordEntity>>

    @Query("SELECT * FROM day_record WHERE dayEpoch BETWEEN :fromDay AND :toDay")
    suspend fun dayRecords(
        fromDay: Long,
        toDay: Long,
    ): List<DayRecordEntity>

    @Query("DELETE FROM usage_snapshot WHERE dayEpoch < :beforeDay")
    suspend fun deleteSnapshotsBefore(beforeDay: Long): Int

    @Query("DELETE FROM day_record WHERE dayEpoch < :beforeDay")
    suspend fun deleteDayRecordsBefore(beforeDay: Long): Int

    @Query("DELETE FROM usage_snapshot")
    suspend fun deleteAllSnapshots()

    @Query("DELETE FROM day_record")
    suspend fun deleteAllDayRecords()

    @Query("SELECT * FROM usage_snapshot ORDER BY dayEpoch, packageName")
    suspend fun allSnapshots(): List<UsageSnapshotEntity>
}

@Dao
interface KnownAppDao {
    @Upsert
    suspend fun upsert(apps: List<KnownAppEntity>)

    @Query("SELECT * FROM known_app")
    fun observeAll(): Flow<List<KnownAppEntity>>

    @Query("DELETE FROM known_app")
    suspend fun deleteAll()
}

@Dao
interface LimitDao {
    @Query("SELECT * FROM app_limit ORDER BY packageName")
    fun observeAppLimits(): Flow<List<AppLimitEntity>>

    @Query("SELECT * FROM app_limit")
    suspend fun appLimits(): List<AppLimitEntity>

    @Query("SELECT * FROM app_limit WHERE packageName = :packageName")
    suspend fun appLimitFor(packageName: String): AppLimitEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAppLimit(limit: AppLimitEntity): Long

    @Query("UPDATE app_limit SET dailyLimitMinutes = :minutes, enabled = :enabled, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateAppLimit(
        id: Long,
        minutes: Int,
        enabled: Boolean,
        updatedAt: Long,
    )

    @Query("UPDATE app_limit SET enabled = :enabled, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setAppLimitEnabled(
        id: Long,
        enabled: Boolean,
        updatedAt: Long,
    )

    @Query("DELETE FROM app_limit WHERE id = :id")
    suspend fun deleteAppLimit(id: Long)

    @Query("SELECT * FROM group_limit ORDER BY name")
    fun observeGroups(): Flow<List<GroupLimitEntity>>

    @Query("SELECT * FROM group_limit")
    suspend fun groups(): List<GroupLimitEntity>

    @Query("SELECT * FROM group_member")
    fun observeGroupMembers(): Flow<List<GroupMemberEntity>>

    @Query("SELECT * FROM group_member")
    suspend fun groupMembers(): List<GroupMemberEntity>

    @Insert
    suspend fun insertGroup(group: GroupLimitEntity): Long

    @Query("UPDATE group_limit SET name = :name, dailyLimitMinutes = :minutes, enabled = :enabled, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateGroup(
        id: Long,
        name: String,
        minutes: Int,
        enabled: Boolean,
        updatedAt: Long,
    )

    @Query("UPDATE group_limit SET enabled = :enabled, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setGroupEnabled(
        id: Long,
        enabled: Boolean,
        updatedAt: Long,
    )

    @Query("DELETE FROM group_member WHERE groupId = :groupId")
    suspend fun deleteMembers(groupId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMembers(members: List<GroupMemberEntity>)

    @Query("DELETE FROM group_limit WHERE id = :id")
    suspend fun deleteGroup(id: Long)

    @Transaction
    suspend fun saveGroup(
        group: GroupLimitEntity,
        packageNames: Set<String>,
    ): Long {
        val id =
            if (group.id == 0L) {
                insertGroup(group)
            } else {
                updateGroup(group.id, group.name, group.dailyLimitMinutes, group.enabled, group.updatedAt)
                group.id
            }
        deleteMembers(id)
        insertMembers(packageNames.map { GroupMemberEntity(id, it) })
        return id
    }

    @Query("DELETE FROM app_limit")
    suspend fun deleteAllAppLimits()

    @Query("DELETE FROM group_limit")
    suspend fun deleteAllGroups()
}

@Dao
interface RegulationEventDao {
    @Insert
    suspend fun insert(event: RegulationEventEntity): Long

    @Query("SELECT * FROM regulation_event WHERE dayEpoch = :dayEpoch ORDER BY occurredAt")
    fun observeForDay(dayEpoch: Long): Flow<List<RegulationEventEntity>>

    @Query("SELECT * FROM regulation_event WHERE dayEpoch = :dayEpoch ORDER BY occurredAt")
    suspend fun forDay(dayEpoch: Long): List<RegulationEventEntity>

    @Query("SELECT * FROM regulation_event ORDER BY occurredAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<RegulationEventEntity>>

    @Query("SELECT * FROM regulation_event ORDER BY occurredAt")
    suspend fun all(): List<RegulationEventEntity>

    @Query("DELETE FROM regulation_event WHERE dayEpoch < :beforeDay")
    suspend fun deleteBefore(beforeDay: Long): Int

    @Query("DELETE FROM regulation_event")
    suspend fun deleteAll()
}
