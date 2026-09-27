package de.handyzeitvertreib.app.regulation

import com.google.common.truth.Truth.assertThat
import de.handyzeitvertreib.app.core.model.ExtensionPolicy
import org.junit.Test

class ExtensionRulesTest {
    private val policy = ExtensionPolicy(enabled = true, extensionMinutes = 10, maxExtensionsPerDay = 2, allowDeviceCredential = false)

    @Test
    fun availableWhenAllowedAndAuthenticationWorks() {
        assertThat(ExtensionRules.availability(policy, 0, AuthCapability.AVAILABLE))
            .isEqualTo(ExtensionAvailability.Available(10, 2))
        assertThat(ExtensionRules.availability(policy, 1, AuthCapability.AVAILABLE))
            .isEqualTo(ExtensionAvailability.Available(10, 1))
    }

    @Test
    fun disabledPolicyNeverOffersExtension() {
        assertThat(ExtensionRules.availability(policy.copy(enabled = false), 0, AuthCapability.AVAILABLE))
            .isEqualTo(ExtensionAvailability.DisabledByUser)
    }

    @Test
    fun dailyCapIsEnforced() {
        assertThat(ExtensionRules.availability(policy, 2, AuthCapability.AVAILABLE))
            .isEqualTo(ExtensionAvailability.DailyCapReached(2))
    }

    @Test
    fun missingAuthenticationBlocksExtension() {
        AuthCapability.entries.filter { it != AuthCapability.AVAILABLE }.forEach { capability ->
            assertThat(ExtensionRules.availability(policy, 0, capability))
                .isEqualTo(ExtensionAvailability.AuthenticationUnavailable(capability))
        }
    }

    @Test
    fun sanitizeRejectsOutOfRangeValues() {
        val sanitized = ExtensionRules.sanitize(policy.copy(extensionMinutes = 240, maxExtensionsPerDay = 50))
        assertThat(sanitized.extensionMinutes).isEqualTo(ExtensionPolicy.DEFAULT.extensionMinutes)
        assertThat(sanitized.maxExtensionsPerDay).isEqualTo(ExtensionPolicy.DEFAULT.maxExtensionsPerDay)
        assertThat(ExtensionRules.sanitize(policy)).isEqualTo(policy)
    }
}
