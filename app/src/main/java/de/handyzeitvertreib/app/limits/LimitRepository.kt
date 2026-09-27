package de.handyzeitvertreib.app.limits

import de.handyzeitvertreib.app.core.model.AppLimit
import de.handyzeitvertreib.app.core.model.GroupLimit
import de.handyzeitvertreib.app.core.model.LimitRules
import de.handyzeitvertreib.app.core.time.AppClock
import de.handyzeitvertreib.app.data.db.AppLimitEntity
import de.handyzeitvertreib.app.data.db.GroupLimitEntity
import de.handyzeitvertreib.app.data.db.GroupMemberEntity
import de.handyzeitvertreib.app.data.db.LimitDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class LimitRepository(
    private val dao: LimitDao,
    private val clock: AppClock,
) {
    val appLimits: Flow<List<AppLimit>> = dao.observeAppLimits().map { list -> list.map { it.toModel() } }

    val groupLimits: Flow<List<GroupLimit>> =
        combine(dao.observeGroups(), dao.observeGroupMembers()) { groups, members -> toGroups(groups, members) }

    suspend fun currentAppLimits(): List<AppLimit> = dao.appLimits().map { it.toModel() }

    suspend fun currentGroupLimits(): List<GroupLimit> = toGroups(dao.groups(), dao.groupMembers())

    /** Creates or updates the single limit of [packageName]. Minutes are normalised to 5-minute steps. */
    suspend fun saveAppLimit(
        packageName: String,
        dailyLimitMinutes: Int,
        enabled: Boolean = true,
    ): Long {
        val now = clock.nowMs()
        val minutes = LimitRules.normalize(dailyLimitMinutes)
        val existing = dao.appLimitFor(packageName)
        return if (existing == null) {
            dao.insertAppLimit(AppLimitEntity(packageName = packageName, dailyLimitMinutes = minutes, enabled = enabled, createdAt = now, updatedAt = now))
        } else {
            dao.updateAppLimit(existing.id, minutes, enabled, now)
            existing.id
        }
    }

    suspend fun setAppLimitEnabled(
        id: Long,
        enabled: Boolean,
    ) = dao.setAppLimitEnabled(id, enabled, clock.nowMs())

    suspend fun deleteAppLimit(id: Long) = dao.deleteAppLimit(id)

    suspend fun saveGroup(
        id: Long,
        name: String,
        packageNames: Set<String>,
        dailyLimitMinutes: Int,
        enabled: Boolean,
    ): Long {
        require(LimitRules.isValidGroupName(name)) { "Invalid group name" }
        require(packageNames.isNotEmpty()) { "A group needs at least one app" }
        val now = clock.nowMs()
        val entity =
            GroupLimitEntity(
                id = id,
                name = name.trim(),
                dailyLimitMinutes = LimitRules.normalize(dailyLimitMinutes),
                enabled = enabled,
                createdAt = now,
                updatedAt = now,
            )
        return dao.saveGroup(entity, packageNames)
    }

    suspend fun setGroupEnabled(
        id: Long,
        enabled: Boolean,
    ) = dao.setGroupEnabled(id, enabled, clock.nowMs())

    suspend fun deleteGroup(id: Long) = dao.deleteGroup(id)

    private fun toGroups(
        groups: List<GroupLimitEntity>,
        members: List<GroupMemberEntity>,
    ): List<GroupLimit> {
        val byGroup = members.groupBy({ it.groupId }, { it.packageName })
        return groups.map {
            GroupLimit(it.id, it.name, byGroup[it.id].orEmpty().toSet(), it.dailyLimitMinutes, it.enabled, it.createdAt, it.updatedAt)
        }
    }

    private fun AppLimitEntity.toModel() = AppLimit(id, packageName, dailyLimitMinutes, enabled, createdAt, updatedAt)
}
