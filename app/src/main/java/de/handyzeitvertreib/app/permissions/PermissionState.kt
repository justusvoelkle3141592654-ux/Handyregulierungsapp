package de.handyzeitvertreib.app.permissions

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import de.handyzeitvertreib.app.enforcement.RegulationAccessibilityService

enum class GrantState { GRANTED, DENIED, NOT_REQUIRED }

data class PermissionSnapshot(
    val usageAccess: GrantState,
    val notifications: GrantState,
    val accessibilityService: GrantState,
) {
    val canReadUsage: Boolean get() = usageAccess == GrantState.GRANTED
}

/** Pure mapping helpers, kept free of Android types so they can be unit tested. */
object PermissionMapping {
    /**
     * `MODE_DEFAULT` means the app op defers to the manifest permission, which some
     * devices report instead of `MODE_ALLOWED`.
     */
    fun usageAccess(
        appOpMode: Int,
        manifestPermissionGranted: Boolean,
    ): GrantState =
        when (appOpMode) {
            AppOpsManager.MODE_ALLOWED -> GrantState.GRANTED
            AppOpsManager.MODE_DEFAULT -> if (manifestPermissionGranted) GrantState.GRANTED else GrantState.DENIED
            else -> GrantState.DENIED
        }

    fun notifications(
        sdkInt: Int,
        runtimePermissionGranted: Boolean,
        notificationsEnabled: Boolean,
    ): GrantState =
        when {
            !notificationsEnabled -> GrantState.DENIED
            sdkInt >= Build.VERSION_CODES.TIRAMISU && !runtimePermissionGranted -> GrantState.DENIED
            else -> GrantState.GRANTED
        }

    /** Parses `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES` (colon-separated component names). */
    fun isAccessibilityServiceEnabled(
        enabledServicesSetting: String?,
        component: ComponentName,
    ): Boolean {
        if (enabledServicesSetting.isNullOrBlank()) return false
        return enabledServicesSetting.split(':').any { entry ->
            ComponentName.unflattenFromString(entry.trim()) == component
        }
    }
}

interface PermissionProvider {
    fun snapshot(): PermissionSnapshot
}

/** Holds the latest permission snapshot; refreshed whenever the app resumes. */
class PermissionMonitor(
    private val provider: PermissionProvider,
) {
    private val mutableState = kotlinx.coroutines.flow.MutableStateFlow(provider.snapshot())
    val state: kotlinx.coroutines.flow.StateFlow<PermissionSnapshot> = mutableState

    fun refresh(): PermissionSnapshot = provider.snapshot().also { mutableState.value = it }
}

class PermissionChecker(
    private val context: Context,
) : PermissionProvider {
    override fun snapshot(): PermissionSnapshot =
        PermissionSnapshot(
            usageAccess = usageAccess(),
            notifications = notifications(),
            accessibilityService = if (isAccessibilityServiceEnabled()) GrantState.GRANTED else GrantState.DENIED,
        )

    fun usageAccess(): GrantState {
        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return GrantState.DENIED
        val mode =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
            }
        val manifestGranted =
            context.checkCallingOrSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS) == PackageManager.PERMISSION_GRANTED
        return PermissionMapping.usageAccess(mode, manifestGranted)
    }

    fun notifications(): GrantState {
        val runtimeGranted =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return PermissionMapping.notifications(
            Build.VERSION.SDK_INT,
            runtimeGranted,
            NotificationManagerCompat.from(context).areNotificationsEnabled(),
        )
    }

    fun isAccessibilityServiceEnabled(): Boolean {
        val component = ComponentName(context, RegulationAccessibilityService::class.java)
        val setting = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        if (PermissionMapping.isAccessibilityServiceEnabled(setting, component)) return true
        val manager = context.getSystemService(AccessibilityManager::class.java) ?: return false
        return manager
            .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { it.resolveInfo?.serviceInfo?.let { info -> ComponentName(info.packageName, info.name) } == component }
    }
}
