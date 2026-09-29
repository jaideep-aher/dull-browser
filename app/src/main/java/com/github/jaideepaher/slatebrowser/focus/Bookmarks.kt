package com.github.jaideepaher.slatebrowser.focus

import kotlinx.serialization.Serializable
import java.net.URI
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class Bookmark(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val url: String,
    @Serializable(with = InstantSerializer::class)
    val added: Instant,
)

/**
 * A typed address the person meant as a page, or null if it would be a search.
 */
object WebAddress {

    fun parse(input: String): String? {
        val text = input.trim()
        val lower = text.lowercase()
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            val host = runCatching { URI(text).host }.getOrNull()
            return text.takeIf { !host.isNullOrEmpty() }
        }
        if (text.isNotEmpty() && !text.contains(' ') && looksLikeHost(text)) {
            val url = "https://$text"
            return url.takeIf { runCatching { URI(url).host }.getOrNull() != null }
        }
        return null
    }

    private fun looksLikeHost(text: String): Boolean {
        val host = text.takeWhile { it != '/' && it != '?' && it != '#' }.substringBefore(':')
        if (host.lowercase() == "localhost") return true
        val labels = host.split('.')
        if (labels.size < 2 || labels.any { it.isEmpty() }) return false
        if (labels.all { it.all(Char::isDigit) }) return labels.size == 4
        val tld = labels.last()
        return tld.length >= 2 && tld.all { it.isLetter() }
    }
}

/**
 * A flat list. A bookmark to a blocked site can be kept, but opening it still shows the closed page.
 */
@Singleton
class Bookmarks @Inject constructor(
    private val store: KeyValueStore,
    private val clock: FocusClock,
) {

    private val lock = Any()

    @Volatile
    private var items: List<Bookmark> = store.load(STORAGE_KEY, serializer).orEmpty()

    val all: List<Bookmark>
        get() = items

    var quickLinkCount: Int
        get() {
            val stored = store.int(QUICK_LINKS_KEY) ?: DEFAULT_QUICK_LINKS
            return stored.coerceIn(0, MAX_QUICK_LINKS)
        }
        set(value) {
            store.putInt(QUICK_LINKS_KEY, value.coerceIn(0, MAX_QUICK_LINKS))
        }

    val quickLinks: List<Bookmark>
        get() = items.take(quickLinkCount)

    fun contains(url: String?): Boolean = bookmark(url) != null

    fun bookmark(url: String?): Bookmark? {
        if (url == null) return null
        val key = key(url)
        return items.firstOrNull { key(it.url) == key }
    }

    fun add(url: String, title: String) {
        val parsed = WebAddress.parse(url) ?: return
        val scheme = runCatching { URI(parsed).scheme }.getOrNull()?.lowercase()
        if (scheme != "http" && scheme != "https") return
        synchronized(lock) {
            if (contains(parsed)) return
            items = items + Bookmark(
                title = title(title, parsed),
                url = parsed,
                added = clock.now(),
            )
            save()
        }
    }

    fun toggle(url: String, title: String) {
        val existing = bookmark(url)
        if (existing != null) remove(existing.id) else add(url, title)
    }

    /** Returns false when the new address is not a web address. */
    fun update(id: String, title: String, address: String): Boolean {
        val parsed = WebAddress.parse(address) ?: return false
        synchronized(lock) {
            val index = items.indexOfFirst { it.id == id }
            if (index < 0) return false
            val current = items[index]
            items = items.toMutableList().also {
                it[index] = current.copy(url = parsed, title = title(title, parsed))
            }
            save()
        }
        return true
    }

    fun remove(id: String) {
        synchronized(lock) {
            items = items.filterNot { it.id == id }
            save()
        }
    }

    fun move(from: Int, to: Int) {
        synchronized(lock) {
            if (from !in items.indices) return
            val next = items.toMutableList()
            val item = next.removeAt(from)
            next.add(to.coerceIn(0, next.size), item)
            items = next
            save()
        }
    }

    private fun save() {
        store.save(STORAGE_KEY, serializer, items)
    }

    companion object {
        const val STORAGE_KEY = "bookmarks.v1"
        const val QUICK_LINKS_KEY = "bookmarks.quickLinks"
        const val MAX_QUICK_LINKS = 8
        const val DEFAULT_QUICK_LINKS = 4

        private val serializer = kotlinx.serialization.builtins.ListSerializer(Bookmark.serializer())

        fun title(title: String, url: String): String {
            val trimmed = title.trim()
            val fallback = runCatching { URI(url).host }.getOrNull() ?: url
            return if (trimmed.isEmpty()) fallback else trimmed.take(120)
        }

        fun key(url: String): String {
            var text = url.lowercase()
            if (text.endsWith("/")) text = text.dropLast(1)
            return text
        }
    }
}
