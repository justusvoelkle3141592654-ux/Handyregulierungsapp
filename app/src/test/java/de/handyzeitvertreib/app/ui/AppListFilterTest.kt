package de.handyzeitvertreib.app.ui

import com.google.common.truth.Truth.assertThat
import de.handyzeitvertreib.app.core.model.ExtensionPolicy
import de.handyzeitvertreib.app.ui.apps.AppFilter
import de.handyzeitvertreib.app.ui.apps.AppListFilter
import de.handyzeitvertreib.app.ui.apps.AppListItem
import de.handyzeitvertreib.app.ui.apps.AppSort
import de.handyzeitvertreib.app.ui.settings.isLooserThan
import org.junit.Test
import java.util.Locale

class AppListFilterTest {
    private fun item(
        label: String,
        ms: Long?,
        opens: Int? = 0,
        limit: Int? = null,
    ) = AppListItem("pkg.${label.lowercase()}", label, ms, opens, limit, limit != null, installed = true)

    private val items =
        listOf(
            item("Zeitung", 10, opens = 1),
            item("Ärzte", 30, opens = 5, limit = 30),
            item("Browser", 0, opens = 0),
            item("chat", 20, opens = 9),
        )

    @Test
    fun sortsByUsageDescending() {
        val result = AppListFilter.apply(items, "", AppSort.USAGE, AppFilter.ALL, Locale.GERMAN)
        assertThat(result.map { it.label }).containsExactly("Ärzte", "chat", "Zeitung", "Browser").inOrder()
    }

    @Test
    fun sortsByNameLocaleAware() {
        val result = AppListFilter.apply(items, "", AppSort.NAME, AppFilter.ALL, Locale.GERMAN)
        assertThat(result.map { it.label }).containsExactly("Ärzte", "Browser", "chat", "Zeitung").inOrder()
    }

    @Test
    fun sortsByOpens() {
        val result = AppListFilter.apply(items, "", AppSort.OPENS, AppFilter.ALL, Locale.GERMAN)
        assertThat(result.first().label).isEqualTo("chat")
    }

    @Test
    fun searchMatchesLabelAndPackageCaseInsensitive() {
        assertThat(AppListFilter.apply(items, "CHA", AppSort.NAME, AppFilter.ALL).map { it.label }).containsExactly("chat")
        assertThat(AppListFilter.apply(items, "pkg.zeit", AppSort.NAME, AppFilter.ALL).map { it.label }).containsExactly("Zeitung")
        assertThat(AppListFilter.apply(items, "  ", AppSort.NAME, AppFilter.ALL)).hasSize(4)
    }

    @Test
    fun filters() {
        assertThat(AppListFilter.apply(items, "", AppSort.NAME, AppFilter.WITH_LIMIT).map { it.label }).containsExactly("Ärzte")
        assertThat(AppListFilter.apply(items, "", AppSort.NAME, AppFilter.USED_TODAY)).hasSize(3)
    }

    @Test
    fun unknownUsageSortsLastAndIsNotUsedToday() {
        val withUnknown = items + item("Unbekannt", null, opens = null)
        assertThat(AppListFilter.apply(withUnknown, "", AppSort.USAGE, AppFilter.ALL).last().label).isEqualTo("Unbekannt")
        assertThat(AppListFilter.apply(withUnknown, "", AppSort.USAGE, AppFilter.USED_TODAY).map { it.label }).doesNotContain("Unbekannt")
    }

    @Test
    fun looseningDetection() {
        val base = ExtensionPolicy(enabled = true, extensionMinutes = 5, maxExtensionsPerDay = 1, allowDeviceCredential = false)
        assertThat(base.copy(extensionMinutes = 10).isLooserThan(base)).isTrue()
        assertThat(base.copy(maxExtensionsPerDay = 2).isLooserThan(base)).isTrue()
        assertThat(base.copy(allowDeviceCredential = true).isLooserThan(base)).isTrue()
        assertThat(base.isLooserThan(base.copy(enabled = false))).isTrue()
        assertThat(base.copy(enabled = false).isLooserThan(base)).isFalse()
        assertThat(base.copy(extensionMinutes = 15).isLooserThan(base.copy(extensionMinutes = 15))).isFalse()
    }
}
