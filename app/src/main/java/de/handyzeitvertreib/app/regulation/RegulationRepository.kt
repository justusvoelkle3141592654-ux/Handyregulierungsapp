package de.handyzeitvertreib.app.regulation

import de.handyzeitvertreib.app.core.format.DurationFormatter
import de.handyzeitvertreib.app.core.model.LimitKey
import de.handyzeitvertreib.app.core.model.LimitType
import de.handyzeitvertreib.app.core.model.RegulationAction
import de.handyzeitvertreib.app.core.model.RegulationEvent
import de.handyzeitvertreib.app.core.time.AppClock
import de.handyzeitvertreib.app.data.db.RegulationEventDao
import de.handyzeitvertreib.app.data.db.RegulationEventEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

class RegulationRepository(
    private val dao: RegulationEventDao,
    private val clock: AppClock,
) {
    private val mutex = Mutex()

    fun observeDay(date: LocalDate): Flow<List<RegulationEvent>> = dao.observeForDay(date.toEpochDay()).map { list -> list.mapNotNull { it.toModel() } }

    fun observeRecent(limit: Int = 50): Flow<List<RegulationEvent>> = dao.observeRecent(limit).map { list -> list.mapNotNull { it.toModel() } }

    suspend fun eventsFor(date: LocalDate): List<RegulationEvent> = dao.forDay(date.toEpochDay()).mapNotNull { it.toModel() }

    suspend fun record(
        key: LimitKey,
        packageName: String?,
        action: RegulationAction,
        extensionMinutes: Int = 0,
    ): Long {
        val now = clock.nowMs()
        return dao.insert(
            RegulationEventEntity(
                packageName = packageName,
                limitType = key.type.name,
                limitId = key.id,
                dayEpoch = clock.today().toEpochDay(),
                occurredAt = now,
                action = action.name,
                extensionMinutes = extensionMinutes,
            ),
        )
    }

    /**
     * Records "limit reached" at most once per limit and day.
     * @return true when this call created the event (callers notify only then)
     */
    suspend fun recordLimitReachedOnce(
        key: LimitKey,
        packageName: String?,
    ): Boolean =
        mutex.withLock {
            val already = eventsFor(clock.today()).any { it.limitKey == key && it.action == RegulationAction.LIMIT_REACHED }
            if (!already) record(key, packageName, RegulationAction.LIMIT_REACHED)
            !already
        }

    suspend fun deleteBefore(date: LocalDate) = dao.deleteBefore(date.toEpochDay())

    companion object {
        fun extensionsMs(events: List<RegulationEvent>): Map<LimitKey, Long> =
            events
                .filter { it.action == RegulationAction.EXTENSION_GRANTED }
                .groupBy { it.limitKey }
                .mapValues { (_, list) -> list.sumOf { DurationFormatter.minutesToMs(it.extensionMinutes) } }

        fun extensionsGranted(
            events: List<RegulationEvent>,
            key: LimitKey,
        ): Int = events.count { it.limitKey == key && it.action == RegulationAction.EXTENSION_GRANTED }

        fun limitReachedAt(
            events: List<RegulationEvent>,
            key: LimitKey,
        ): Long? = events.lastOrNull { it.limitKey == key && it.action == RegulationAction.LIMIT_REACHED }?.occurredAt
    }
}

private fun RegulationEventEntity.toModel(): RegulationEvent? {
    val type = LimitType.entries.firstOrNull { it.name == limitType } ?: return null
    val parsedAction = RegulationAction.entries.firstOrNull { it.name == action } ?: return null
    return RegulationEvent(id, packageName, LimitKey(type, limitId), dayEpoch, occurredAt, parsedAction, extensionMinutes)
}
