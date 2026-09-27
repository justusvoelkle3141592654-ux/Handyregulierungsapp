package de.handyzeitvertreib.app.ui.apps

import java.text.Collator
import java.util.Locale

data class AppListItem(
    val packageName: String,
    val label: String,
    /** Null when usage access is missing: unknown, not zero. */
    val todayMs: Long?,
    val launchCount: Int?,
    val limitMinutes: Int?,
    val limitEnabled: Boolean,
    val installed: Boolean,
)

enum class AppSort { USAGE, NAME, OPENS }

enum class AppFilter { ALL, WITH_LIMIT, USED_TODAY }

object AppListFilter {
    fun apply(
        items: List<AppListItem>,
        query: String,
        sort: AppSort,
        filter: AppFilter,
        locale: Locale = Locale.getDefault(),
    ): List<AppListItem> {
        val needle = query.trim().lowercase(locale)
        val collator = Collator.getInstance(locale).apply { strength = Collator.PRIMARY }
        val byName = Comparator<AppListItem> { a, b -> collator.compare(a.label, b.label) }
        val filtered =
            items.filter { item ->
                val matchesQuery =
                    needle.isEmpty() ||
                        item.label.lowercase(locale).contains(needle) ||
                        item.packageName.lowercase(locale).contains(needle)
                val matchesFilter =
                    when (filter) {
                        AppFilter.ALL -> true
                        AppFilter.WITH_LIMIT -> item.limitMinutes != null
                        AppFilter.USED_TODAY -> (item.todayMs ?: 0L) > 0L
                    }
                matchesQuery && matchesFilter
            }
        val comparator =
            when (sort) {
                AppSort.NAME -> byName
                AppSort.USAGE -> compareByDescending<AppListItem> { it.todayMs ?: -1L }.then(byName)
                AppSort.OPENS -> compareByDescending<AppListItem> { it.launchCount ?: -1 }.then(byName)
            }
        return filtered.sortedWith(comparator)
    }
}
