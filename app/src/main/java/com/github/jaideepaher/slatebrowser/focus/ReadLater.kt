package com.github.jaideepaher.slatebrowser.focus

import kotlinx.serialization.Serializable
import java.net.URI
import java.time.Instant
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ceil

@Serializable
data class ReadLaterItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val url: String,
    @Serializable(with = InstantSerializer::class)
    val added: Instant,
    @Serializable(with = InstantSerializer::class)
    val readAt: Instant? = null,
)

/**
 * Optional hours when saved pages can be opened, as minutes after local midnight.
 * A window whose end is before its start runs past midnight.
 */
@Serializable
data class ReadingWindow(
    val enabled: Boolean = false,
    val start: Int = 20 * 60,
    val end: Int = 22 * 60,
) {

    fun isOpen(at: Instant, zone: java.time.ZoneId): Boolean {
        if (!enabled || start == end) return true
        val local = at.atZone(zone)
        val minute = local.hour * 60 + local.minute
        return if (start < end) minute in start until end else minute >= start || minute < end
    }

    /** Zero while open. */
    fun timeUntilOpen(at: Instant, zone: java.time.ZoneId): Long {
        if (isOpen(at, zone)) return 0
        val zoned = at.atZone(zone)
        val midnight = zoned.toLocalDate().atStartOfDay(zone)
        var opening = midnight.plusMinutes(start.toLong())
        if (!opening.toInstant().isAfter(at)) opening = opening.plusDays(1)
        return DurationSeconds.between(at, opening.toInstant())
    }

    companion object {
        private val timeFormat = DateTimeFormatter.ofPattern("h:mm a")

        fun label(minutes: Int): String {
            val hour = minutes / 60
            val minute = minutes % 60
            return LocalTime.of(hour, minute).format(timeFormat)
        }

        fun waitLabel(seconds: Long): String {
            val minutes = ceil(seconds / 60.0).toInt()
            return if (minutes < 60) {
                "Opens in ${minutes}m"
            } else {
                "Opens in ${minutes / 60}h ${minutes % 60}m"
            }
        }
    }
}

private object DurationSeconds {
    fun between(from: Instant, to: Instant): Long =
        java.time.Duration.between(from, to).seconds.coerceAtLeast(0)
}

/**
 * Pages saved to read later. Opening one goes through the same blocking and pause rules.
 */
@Singleton
class ReadLater @Inject constructor(
    private val store: KeyValueStore,
    private val clock: FocusClock,
) {

    private val lock = Any()

    @Volatile
    private var items: List<ReadLaterItem> = store.load(STORAGE_KEY, serializer).orEmpty()

    var window: ReadingWindow
        get() = store.load(WINDOW_KEY, ReadingWindow.serializer()) ?: ReadingWindow()
        set(value) {
            store.save(WINDOW_KEY, ReadingWindow.serializer(), value)
        }

    val all: List<ReadLaterItem>
        get() = items

    val unread: List<ReadLaterItem>
        get() = items.filter { it.readAt == null }

    val read: List<ReadLaterItem>
        get() = items.filter { it.readAt != null }

    fun contains(url: String?): Boolean {
        if (url == null) return false
        return items.any { it.url == url && it.readAt == null }
    }

    fun add(url: String, title: String) {
        val parsed = WebAddress.parse(url) ?: return
        val scheme = runCatching { URI(parsed).scheme }.getOrNull()?.lowercase()
        if (scheme != "http" && scheme != "https" || contains(parsed)) return
        synchronized(lock) {
            items = listOf(
                ReadLaterItem(
                    title = Bookmarks.title(title, parsed),
                    url = parsed,
                    added = clock.now(),
                )
            ) + items
            save()
        }
    }

    fun setRead(id: String, read: Boolean, at: Instant = clock.now()) {
        synchronized(lock) {
            val index = items.indexOfFirst { it.id == id }
            if (index < 0) return
            items = items.toMutableList().also {
                it[index] = it[index].copy(readAt = if (read) at else null)
            }
            save()
        }
    }

    fun remove(id: String) {
        synchronized(lock) {
            items = items.filterNot { it.id == id }
            save()
        }
    }

    fun isOpenNow(): Boolean = window.isOpen(clock.now(), clock.zone())

    fun timeUntilOpen(): Long = window.timeUntilOpen(clock.now(), clock.zone())

    private fun save() {
        store.save(STORAGE_KEY, serializer, items)
    }

    companion object {
        const val STORAGE_KEY = "readLater.v1"
        const val WINDOW_KEY = "readLater.window.v1"
        private val serializer = kotlinx.serialization.builtins.ListSerializer(ReadLaterItem.serializer())
    }
}
