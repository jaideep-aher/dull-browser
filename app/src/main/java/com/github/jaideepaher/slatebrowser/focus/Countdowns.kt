package com.github.jaideepaher.slatebrowser.focus

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class Countdown(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    /** Local calendar day, `yyyy-MM-dd`. */
    val day: String,
)

/**
 * Named dates such as exams. The start page shows the nearest one that has not passed.
 */
@Singleton
class Countdowns @Inject constructor(
    private val store: KeyValueStore,
    private val clock: FocusClock,
) {

    private val lock = Any()

    @Volatile
    private var items: List<Countdown> = store.load(STORAGE_KEY, serializer).orEmpty()

    val all: List<Countdown>
        get() = items

    fun add(name: String, on: LocalDate) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        synchronized(lock) {
            items = (items + Countdown(name = trimmed.take(40), day = DayKey.string(on)))
                .sortedBy { it.day }
            save()
        }
    }

    fun remove(id: String) {
        synchronized(lock) {
            items = items.filterNot { it.id == id }
            save()
        }
    }

    /** Upcoming dates, nearest first. Past dates are hidden but kept until removed. */
    val upcoming: List<Pair<Countdown, Int>>
        get() {
            val today = DayKey.string(clock.today())
            return items.mapNotNull { item ->
                val days = DayKey.days(today, item.day) ?: return@mapNotNull null
                if (days < 0) null else item to days.toInt()
            }.sortedBy { it.second }
        }

    val nextLabel: String?
        get() = upcoming.firstOrNull()?.let { label(it.first.name, it.second) }

    private fun save() {
        store.save(STORAGE_KEY, serializer, items)
    }

    companion object {
        const val STORAGE_KEY = "countdowns.v1"
        private val serializer = kotlinx.serialization.builtins.ListSerializer(Countdown.serializer())

        /** "Finals in 12 days", "Finals tomorrow", "Finals today". */
        fun label(name: String, days: Int): String = "$name ${whenLabel(days)}"

        fun whenLabel(days: Int): String = when (days) {
            0 -> "today"
            1 -> "tomorrow"
            else -> "in $days days"
        }
    }
}
