package de.handyzeitvertreib.app.data.management

import androidx.room.withTransaction
import de.handyzeitvertreib.app.data.db.AppLimitEntity
import de.handyzeitvertreib.app.data.db.GroupLimitEntity
import de.handyzeitvertreib.app.data.db.GroupMemberEntity
import de.handyzeitvertreib.app.data.db.HzvDatabase
import de.handyzeitvertreib.app.data.db.RegulationEventEntity
import de.handyzeitvertreib.app.data.db.UsageSnapshotEntity
import de.handyzeitvertreib.app.data.prefs.PreferencesRepository
import de.handyzeitvertreib.app.usage.UsageRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** Export and deletion of everything stored locally. */
class LocalDataManager(
    private val database: HzvDatabase,
    private val preferences: PreferencesRepository,
    private val usageRepository: UsageRepository,
    private val ioDispatcher: CoroutineDispatcher,
) {
    suspend fun deleteUsageHistory() =
        withContext(ioDispatcher) {
            database.withTransaction {
                database.usageDao().deleteAllSnapshots()
                database.usageDao().deleteAllDayRecords()
                database.knownAppDao().deleteAll()
            }
            usageRepository.resetInMemoryState()
        }

    suspend fun deleteRegulationEvents() = withContext(ioDispatcher) { database.regulationEventDao().deleteAll() }

    /** Removes usage history, events, limits and settings. The app returns to onboarding. */
    suspend fun deleteEverything() =
        withContext(ioDispatcher) {
            database.withTransaction {
                database.usageDao().deleteAllSnapshots()
                database.usageDao().deleteAllDayRecords()
                database.knownAppDao().deleteAll()
                database.regulationEventDao().deleteAll()
                database.limitDao().deleteAllAppLimits()
                database.limitDao().deleteAllGroups()
            }
            preferences.clear()
            usageRepository.resetInMemoryState()
        }

    suspend fun exportJson(): String =
        withContext(ioDispatcher) {
            val limitDao = database.limitDao()
            LocalDataExport.toJson(
                snapshots = database.usageDao().allSnapshots(),
                appLimits = limitDao.appLimits(),
                groups = limitDao.groups(),
                members = limitDao.groupMembers(),
                events = database.regulationEventDao().all(),
            )
        }
}

/** Minimal JSON writer so the export format stays explicit and dependency-free. */
object LocalDataExport {
    const val FORMAT_VERSION = 1

    fun toJson(
        snapshots: List<UsageSnapshotEntity>,
        appLimits: List<AppLimitEntity>,
        groups: List<GroupLimitEntity>,
        members: List<GroupMemberEntity>,
        events: List<RegulationEventEntity>,
    ): String {
        val membersByGroup = members.groupBy({ it.groupId }, { it.packageName })
        return buildString {
            append("{\n  \"formatVersion\": ").append(FORMAT_VERSION).append(",\n")
            append("  \"usageSnapshots\": ")
            array(snapshots) {
                obj(
                    "dayEpoch" to it.dayEpoch,
                    "packageName" to it.packageName,
                    "foregroundMs" to it.foregroundMs,
                    "launchCount" to it.launchCount,
                    "lastUsedAt" to it.lastUsedAt,
                )
            }
            append(",\n  \"appLimits\": ")
            array(appLimits) {
                obj("packageName" to it.packageName, "dailyLimitMinutes" to it.dailyLimitMinutes, "enabled" to it.enabled)
            }
            append(",\n  \"groupLimits\": ")
            array(groups) {
                obj(
                    "name" to it.name,
                    "dailyLimitMinutes" to it.dailyLimitMinutes,
                    "enabled" to it.enabled,
                    "packageNames" to membersByGroup[it.id].orEmpty().sorted(),
                )
            }
            append(",\n  \"regulationEvents\": ")
            array(events) {
                obj(
                    "packageName" to it.packageName,
                    "limitType" to it.limitType,
                    "occurredAt" to it.occurredAt,
                    "action" to it.action,
                    "extensionMinutes" to it.extensionMinutes,
                )
            }
            append("\n}\n")
        }
    }

    private fun <T> StringBuilder.array(
        items: List<T>,
        render: StringBuilder.(T) -> Unit,
    ) {
        append('[')
        items.forEachIndexed { index, item ->
            if (index > 0) append(',')
            append("\n    ")
            render(item)
        }
        if (items.isNotEmpty()) append("\n  ")
        append(']')
    }

    private fun StringBuilder.obj(vararg fields: Pair<String, Any?>) {
        append('{')
        fields.forEachIndexed { index, (key, value) ->
            if (index > 0) append(", ")
            append(quote(key)).append(": ").append(value(value))
        }
        append('}')
    }

    private fun value(value: Any?): String =
        when (value) {
            null -> "null"
            is Number, is Boolean -> value.toString()
            is List<*> -> value.joinToString(prefix = "[", postfix = "]") { value(it) }
            else -> quote(value.toString())
        }

    fun quote(text: String): String =
        buildString {
            append('"')
            for (char in text) {
                when {
                    char == '"' -> append("\\\"")
                    char == '\\' -> append("\\\\")
                    char == '\n' -> append("\\n")
                    char == '\r' -> append("\\r")
                    char == '\t' -> append("\\t")
                    char < ' ' -> append("\\u%04x".format(char.code))
                    else -> append(char)
                }
            }
            append('"')
        }
}
