package de.handyzeitvertreib.app.permissions

import android.app.AppOpsManager
import android.content.ComponentName
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import com.google.common.truth.Truth.assertThat
import de.handyzeitvertreib.app.account.AccountState
import de.handyzeitvertreib.app.account.AccountStateMapping
import de.handyzeitvertreib.app.account.UnconfiguredAccountRepository
import de.handyzeitvertreib.app.biometric.AuthResult
import de.handyzeitvertreib.app.biometric.BiometricMapping
import de.handyzeitvertreib.app.enforcement.EnforcementCapabilities
import de.handyzeitvertreib.app.enforcement.EnforcementLevel
import de.handyzeitvertreib.app.regulation.AuthCapability
import de.handyzeitvertreib.app.usage.AndroidUsageEventSource
import de.handyzeitvertreib.app.usage.UsageEventKind
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MappingTest {
    @Test
    fun usageAccessMapping() {
        assertThat(PermissionMapping.usageAccess(AppOpsManager.MODE_ALLOWED, false)).isEqualTo(GrantState.GRANTED)
        assertThat(PermissionMapping.usageAccess(AppOpsManager.MODE_IGNORED, true)).isEqualTo(GrantState.DENIED)
        assertThat(PermissionMapping.usageAccess(AppOpsManager.MODE_ERRORED, true)).isEqualTo(GrantState.DENIED)
        assertThat(PermissionMapping.usageAccess(AppOpsManager.MODE_DEFAULT, true)).isEqualTo(GrantState.GRANTED)
        assertThat(PermissionMapping.usageAccess(AppOpsManager.MODE_DEFAULT, false)).isEqualTo(GrantState.DENIED)
    }

    @Test
    fun notificationMapping() {
        assertThat(
            PermissionMapping.notifications(33, runtimePermissionGranted = false, notificationsEnabled = true),
        ).isEqualTo(GrantState.DENIED)
        assertThat(
            PermissionMapping.notifications(33, runtimePermissionGranted = true, notificationsEnabled = true),
        ).isEqualTo(GrantState.GRANTED)
        assertThat(
            PermissionMapping.notifications(30, runtimePermissionGranted = false, notificationsEnabled = true),
        ).isEqualTo(GrantState.GRANTED)
        assertThat(
            PermissionMapping.notifications(30, runtimePermissionGranted = true, notificationsEnabled = false),
        ).isEqualTo(GrantState.DENIED)
    }

    @Test
    fun accessibilitySettingParsing() {
        val component = ComponentName("de.handyzeitvertreib.app", "de.handyzeitvertreib.app.enforcement.RegulationAccessibilityService")
        assertThat(PermissionMapping.isAccessibilityServiceEnabled(null, component)).isFalse()
        assertThat(PermissionMapping.isAccessibilityServiceEnabled("", component)).isFalse()
        assertThat(
            PermissionMapping.isAccessibilityServiceEnabled(
                "com.other/.Service:de.handyzeitvertreib.app/.enforcement.RegulationAccessibilityService",
                component,
            ),
        ).isTrue()
        assertThat(PermissionMapping.isAccessibilityServiceEnabled("com.other/.Service", component)).isFalse()
    }

    @Test
    fun enforcementLevelFollowsPermissions() {
        fun snapshot(
            usage: GrantState,
            notifications: GrantState,
            accessibility: GrantState,
        ) = PermissionSnapshot(usage, notifications, accessibility)

        val g = GrantState.GRANTED
        val d = GrantState.DENIED
        assertThat(EnforcementCapabilities.strongestLevel(snapshot(d, g, g), true)).isEqualTo(EnforcementLevel.NONE)
        assertThat(EnforcementCapabilities.strongestLevel(snapshot(g, d, d), true)).isEqualTo(EnforcementLevel.IN_APP_ONLY)
        assertThat(EnforcementCapabilities.strongestLevel(snapshot(g, g, d), true)).isEqualTo(EnforcementLevel.NOTIFICATION)
        assertThat(EnforcementCapabilities.strongestLevel(snapshot(g, g, d), false)).isEqualTo(EnforcementLevel.IN_APP_ONLY)
        assertThat(EnforcementCapabilities.strongestLevel(snapshot(g, d, g), true)).isEqualTo(EnforcementLevel.SCREEN_ON_APP_OPEN)
    }

    @Test
    fun biometricCapabilityMapping() {
        assertThat(BiometricMapping.capability(BiometricManager.BIOMETRIC_SUCCESS)).isEqualTo(AuthCapability.AVAILABLE)
        assertThat(BiometricMapping.capability(BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED)).isEqualTo(AuthCapability.NONE_ENROLLED)
        assertThat(BiometricMapping.capability(BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE)).isEqualTo(AuthCapability.NO_HARDWARE)
        assertThat(
            BiometricMapping.capability(BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE),
        ).isEqualTo(AuthCapability.HARDWARE_UNAVAILABLE)
        assertThat(
            BiometricMapping.capability(BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED),
        ).isEqualTo(AuthCapability.SECURITY_UPDATE_REQUIRED)
        assertThat(BiometricMapping.capability(12345)).isEqualTo(AuthCapability.UNKNOWN)
    }

    @Test
    fun biometricErrorMapping() {
        assertThat(BiometricMapping.error(BiometricPrompt.ERROR_USER_CANCELED)).isEqualTo(AuthResult.Cancelled)
        assertThat(BiometricMapping.error(BiometricPrompt.ERROR_NEGATIVE_BUTTON)).isEqualTo(AuthResult.Cancelled)
        assertThat(BiometricMapping.error(BiometricPrompt.ERROR_LOCKOUT)).isEqualTo(AuthResult.LockedOut(permanent = false))
        assertThat(BiometricMapping.error(BiometricPrompt.ERROR_LOCKOUT_PERMANENT)).isEqualTo(AuthResult.LockedOut(permanent = true))
        assertThat(BiometricMapping.error(BiometricPrompt.ERROR_NO_BIOMETRICS)).isEqualTo(AuthResult.NotAvailable)
        assertThat(BiometricMapping.error(BiometricPrompt.ERROR_TIMEOUT)).isEqualTo(AuthResult.Failed(BiometricPrompt.ERROR_TIMEOUT))
    }

    @Test
    fun deviceCredentialOnlyWhenAllowed() {
        val withCredential = BiometricMapping.authenticators(allowDeviceCredential = true)
        val biometricOnly = BiometricMapping.authenticators(allowDeviceCredential = false)
        assertThat(withCredential and BiometricManager.Authenticators.DEVICE_CREDENTIAL).isNotEqualTo(0)
        assertThat(biometricOnly and BiometricManager.Authenticators.DEVICE_CREDENTIAL).isEqualTo(0)
    }

    @Test
    fun usageEventTypeMapping() {
        assertThat(AndroidUsageEventSource.mapEventType(1)).isEqualTo(UsageEventKind.ACTIVITY_RESUMED)
        assertThat(AndroidUsageEventSource.mapEventType(2)).isEqualTo(UsageEventKind.ACTIVITY_PAUSED)
        assertThat(AndroidUsageEventSource.mapEventType(23)).isEqualTo(UsageEventKind.ACTIVITY_STOPPED)
        assertThat(AndroidUsageEventSource.mapEventType(16)).isEqualTo(UsageEventKind.SCREEN_OFF)
        assertThat(AndroidUsageEventSource.mapEventType(5)).isNull()
    }

    @Test
    fun accountStateMapping() =
        runTest {
            assertThat(UnconfiguredAccountRepository().signIn()).isEqualTo(AccountState.NotConfigured)
            val local = AccountStateMapping.actions(AccountState.NotConfigured)
            assertThat(local.canSignIn || local.canSignOut || local.canDelete).isFalse()
            assertThat(AccountStateMapping.actions(AccountState.SignedOut).canSignIn).isTrue()
            val signedIn = AccountStateMapping.actions(AccountState.SignedIn("Kim"))
            assertThat(signedIn.canSignOut && signedIn.canDelete).isTrue()
            assertThat(
                AccountStateMapping.actions(AccountState.Error(de.handyzeitvertreib.app.account.AccountError.OFFLINE)).showRetry,
            ).isTrue()
        }
}
