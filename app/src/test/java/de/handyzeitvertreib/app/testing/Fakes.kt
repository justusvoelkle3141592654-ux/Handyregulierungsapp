package de.handyzeitvertreib.app.testing

import androidx.fragment.app.FragmentActivity
import de.handyzeitvertreib.app.biometric.AuthRequest
import de.handyzeitvertreib.app.biometric.AuthResult
import de.handyzeitvertreib.app.biometric.ExtensionAuthenticator
import de.handyzeitvertreib.app.core.model.InstalledApp
import de.handyzeitvertreib.app.core.time.AppClock
import de.handyzeitvertreib.app.core.time.DayBoundaries
import de.handyzeitvertreib.app.permissions.GrantState
import de.handyzeitvertreib.app.permissions.PermissionProvider
import de.handyzeitvertreib.app.permissions.PermissionSnapshot
import de.handyzeitvertreib.app.regulation.AuthCapability
import de.handyzeitvertreib.app.usage.InstalledAppsSource
import de.handyzeitvertreib.app.usage.RawUsageEvent
import de.handyzeitvertreib.app.usage.UsageEventKind
import de.handyzeitvertreib.app.usage.UsageEventSource
import java.time.LocalDate
import java.time.ZoneId

const val MINUTE = 60_000L
const val HOUR = 60 * MINUTE

class FakeClock(
    var zoneId: ZoneId = ZoneId.of("Europe/Berlin"),
    var now: Long = DayBoundaries.startOfDay(LocalDate.of(2026, 9, 21), ZoneId.of("Europe/Berlin")) + 14 * HOUR,
) : AppClock {
    override fun nowMs(): Long = now

    override fun zone(): ZoneId = zoneId

    fun startOfToday(): Long = DayBoundaries.startOfDay(today(), zoneId)
}

class FakeUsageEventSource(
    var access: Boolean = true,
) : UsageEventSource {
    val events = mutableListOf<RawUsageEvent>()
    var throwSecurity = false

    override fun hasAccess(): Boolean = access

    override fun queryEvents(
        fromMs: Long,
        toMs: Long,
    ): List<RawUsageEvent> {
        if (throwSecurity) throw SecurityException("revoked")
        return events.filter { it.timestampMs in fromMs..toMs }
    }

    fun session(
        packageName: String,
        startMs: Long,
        durationMs: Long,
    ) {
        events += RawUsageEvent(packageName, "Main", startMs, UsageEventKind.ACTIVITY_RESUMED)
        events += RawUsageEvent(packageName, "Main", startMs + durationMs, UsageEventKind.ACTIVITY_PAUSED)
    }
}

class FakeInstalledApps : InstalledAppsSource {
    var apps =
        listOf(
            InstalledApp("com.example.chat", "Chat", false),
            InstalledApp("com.example.video", "Video", false),
            InstalledApp("com.example.news", "News", false),
            InstalledApp("com.example.maps", "Maps", false),
        )
    var home = setOf("com.example.launcher")

    override fun launchableApps(): List<InstalledApp> = apps

    override fun homePackages(): Set<String> = home
}

class FakePermissions(
    var snapshot: PermissionSnapshot = PermissionSnapshot(GrantState.GRANTED, GrantState.GRANTED, GrantState.DENIED),
) : PermissionProvider {
    override fun snapshot(): PermissionSnapshot = snapshot
}

class FakeAuthenticator(
    var capability: AuthCapability = AuthCapability.AVAILABLE,
    var result: AuthResult = AuthResult.Success,
) : ExtensionAuthenticator {
    var requests = mutableListOf<AuthRequest>()

    override fun capability(allowDeviceCredential: Boolean): AuthCapability = capability

    override suspend fun authenticate(
        activity: FragmentActivity,
        request: AuthRequest,
    ): AuthResult {
        requests += request
        return result
    }
}
