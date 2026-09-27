package de.handyzeitvertreib.app.enforcement

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import de.handyzeitvertreib.app.HzvApplication
import de.handyzeitvertreib.app.regulation.RegulationActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Optional, user-enabled regulation service.
 *
 * What it observes: only the package name of the window that comes to the foreground
 * (`TYPE_WINDOW_STATE_CHANGED`). It does not read window content, text input or
 * screen contents (`canRetrieveWindowContent=false`) and it stores nothing itself.
 *
 * What it does: when a package whose limit is reached comes to the foreground, it opens
 * the regulation screen. While a limited app stays in front, it re-checks shortly after
 * the remaining time runs out.
 */
class RegulationAccessibilityService : AccessibilityService() {
    private var scope: CoroutineScope = MainScope()
    private var checkJob: Job? = null
    private var foregroundPackage: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        scope = MainScope()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val packageName = event.packageName?.toString() ?: return
        if (packageName in ignoredPackages()) return
        if (packageName == foregroundPackage && checkJob?.isActive == true) return
        foregroundPackage = packageName
        startChecking(packageName)
    }

    private fun startChecking(packageName: String) {
        checkJob?.cancel()
        val container = (application as HzvApplication).container
        checkJob =
            scope.launch {
                while (isActive && foregroundPackage == packageName) {
                    val snapshot = container.regulationCoordinator.evaluateNow(minIntervalMs = MIN_REFRESH_INTERVAL_MS) ?: return@launch
                    val status = snapshot.evaluation.governingFor(packageName) ?: return@launch
                    if (status.isReached) {
                        container.regulationCoordinator.recordLimitReached(status.key, packageName)
                        startActivity(RegulationActivity.intent(this@RegulationAccessibilityService, status.key, packageName))
                        return@launch
                    }
                    delay((status.remainingMs + RECHECK_BUFFER_MS).coerceIn(MIN_DELAY_MS, MAX_DELAY_MS))
                }
            }
    }

    private fun ignoredPackages(): Set<String> {
        val inputMethods =
            getSystemService(InputMethodManager::class.java)
                ?.enabledInputMethodList
                ?.map { it.packageName }
                .orEmpty()
        return setOf(packageName, SYSTEM_UI) + inputMethods
    }

    override fun onInterrupt() {
        checkJob?.cancel()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val SYSTEM_UI = "com.android.systemui"
        const val MIN_REFRESH_INTERVAL_MS = 5_000L
        const val RECHECK_BUFFER_MS = 2_000L
        const val MIN_DELAY_MS = 5_000L
        const val MAX_DELAY_MS = 5 * 60_000L
    }
}
