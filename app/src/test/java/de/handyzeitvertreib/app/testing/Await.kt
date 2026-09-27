package de.handyzeitvertreib.app.testing

import android.os.Looper
import org.robolectric.Shadows.shadowOf

/** Idles the Robolectric main looper until [condition] holds; database work runs on real threads. */
fun awaitCondition(
    timeoutMs: Long = 5_000,
    condition: () -> Boolean,
) {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline) {
        shadowOf(Looper.getMainLooper()).idle()
        if (condition()) return
        Thread.sleep(10)
    }
    shadowOf(Looper.getMainLooper()).idle()
    check(condition()) { "Condition not met within $timeoutMs ms" }
}

/** Like [awaitCondition] but also lets Compose recompose between checks. */
fun androidx.compose.ui.test.junit4.ComposeContentTestRule.awaitUi(
    timeoutMs: Long = 5_000,
    condition: () -> Boolean,
) {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline) {
        shadowOf(Looper.getMainLooper()).idle()
        waitForIdle()
        if (condition()) return
        Thread.sleep(10)
    }
    check(condition()) { "UI condition not met within $timeoutMs ms" }
}
