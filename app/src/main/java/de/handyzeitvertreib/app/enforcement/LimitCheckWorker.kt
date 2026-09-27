package de.handyzeitvertreib.app.enforcement

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import de.handyzeitvertreib.app.HzvApplication
import de.handyzeitvertreib.app.R
import java.util.concurrent.TimeUnit

/**
 * Periodic fallback check. Android runs periodic work at most every 15 minutes and may
 * delay it further (Doze, battery saver, OEM restrictions), so this is not real time.
 */
class LimitCheckWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as HzvApplication).container
        val prefs = container.preferencesRepository.current()
        container.usageRepository.refresh(backfillDays = 1)
        container.usageRepository.applyRetention(prefs.retentionDays)
        container.regulationRepository.deleteBefore(container.clock.today().minusDays(prefs.retentionDays.toLong()))
        val snapshot = container.regulationCoordinator.evaluateNow() ?: return Result.success()
        val labels = container.usageRepository.installedApps().associate { it.packageName to it.label }
        for (status in snapshot.evaluation.reachedStatuses()) {
            val packageName = status.packageNames.singleOrNull()
            val isNew = container.regulationCoordinator.recordLimitReached(status.key, packageName)
            if (isNew && prefs.limitNotificationsEnabled) {
                val name = status.name ?: packageName?.let { labels[it] ?: it } ?: ""
                container.limitNotifier.notifyLimitReached(
                    status.key,
                    packageName,
                    applicationContext.getString(R.string.notification_limit_reached_title, name),
                )
            }
        }
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "limit-check"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<LimitCheckWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
