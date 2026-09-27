package de.handyzeitvertreib.app.usage

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import de.handyzeitvertreib.app.core.model.InstalledApp

/** Reads raw usage events. Implementations must not persist or transmit them. */
interface UsageEventSource {
    fun hasAccess(): Boolean

    /** @throws SecurityException when usage access was revoked in the meantime. */
    fun queryEvents(
        fromMs: Long,
        toMs: Long,
    ): List<RawUsageEvent>
}

class AndroidUsageEventSource(
    private val context: Context,
    private val hasAccessCheck: () -> Boolean,
) : UsageEventSource {
    override fun hasAccess(): Boolean = hasAccessCheck()

    override fun queryEvents(
        fromMs: Long,
        toMs: Long,
    ): List<RawUsageEvent> {
        val manager = context.getSystemService(UsageStatsManager::class.java) ?: return emptyList()
        val events = manager.queryEvents(fromMs, toMs) ?: return emptyList()
        val result = ArrayList<RawUsageEvent>()
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val kind = mapEventType(event.eventType) ?: continue
            result += RawUsageEvent(event.packageName, event.className, event.timeStamp, kind)
        }
        return result
    }

    companion object {
        // Raw values keep this working on API 26-28, where some constants are not yet defined.
        private const val ACTIVITY_RESUMED = 1
        private const val ACTIVITY_PAUSED = 2
        private const val SCREEN_NON_INTERACTIVE = 16
        private const val ACTIVITY_STOPPED = 23
        private const val DEVICE_SHUTDOWN = 26
        private const val DEVICE_STARTUP = 27

        fun mapEventType(type: Int): UsageEventKind? =
            when (type) {
                ACTIVITY_RESUMED -> UsageEventKind.ACTIVITY_RESUMED
                ACTIVITY_PAUSED -> UsageEventKind.ACTIVITY_PAUSED
                ACTIVITY_STOPPED -> UsageEventKind.ACTIVITY_STOPPED
                SCREEN_NON_INTERACTIVE -> UsageEventKind.SCREEN_OFF
                DEVICE_SHUTDOWN -> UsageEventKind.DEVICE_SHUTDOWN
                DEVICE_STARTUP -> UsageEventKind.DEVICE_STARTUP
                else -> null
            }
    }
}

/** Lists launchable apps. Needs the `<queries>` launcher intent declared in the manifest. */
interface InstalledAppsSource {
    fun launchableApps(): List<InstalledApp>

    /** Packages of home-screen launchers; their time is excluded from totals. */
    fun homePackages(): Set<String>
}

class AndroidInstalledAppsSource(
    private val context: Context,
) : InstalledAppsSource {
    override fun launchableApps(): List<InstalledApp> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm
            .queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .mapNotNull { it.activityInfo?.applicationInfo }
            .distinctBy { it.packageName }
            .filter { it.packageName != context.packageName }
            .map { info ->
                InstalledApp(
                    packageName = info.packageName,
                    label = runCatching { pm.getApplicationLabel(info).toString() }.getOrDefault(info.packageName),
                    isSystemApp = info.flags and ApplicationInfo.FLAG_SYSTEM != 0,
                )
            }.sortedBy { it.label.lowercase() }
    }

    override fun homePackages(): Set<String> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return context.packageManager
            .queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            .mapNotNull { it.activityInfo?.packageName }
            .toSet()
    }
}
