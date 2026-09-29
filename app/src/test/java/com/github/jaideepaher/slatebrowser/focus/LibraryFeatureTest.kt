package com.github.jaideepaher.slatebrowser.focus

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class CountdownTest {

    @Test
    fun `nearest upcoming date is shown and past dates hide`() {
        val clock = FakeFocusClock()
        val list = Countdowns(MemoryKeyValueStore(), clock)
        assertThat(list.nextLabel).isNull()
        list.add("Finals", clock.today().plusDays(12))
        list.add("Essay", clock.today().plusDays(1))
        list.add("  ", clock.today())
        assertThat(list.all).hasSize(2)
        assertThat(list.nextLabel).isEqualTo("Essay tomorrow")
        clock.advance(days = 1.0)
        assertThat(list.nextLabel).isEqualTo("Essay today")
        clock.advance(days = 1.0)
        assertThat(list.nextLabel).isEqualTo("Finals in 10 days")
        clock.advance(days = 11.0)
        assertThat(list.nextLabel).isNull()
        assertThat(list.all).hasSize(2)
    }

    @Test
    fun `countdowns persist and can be removed`() {
        val store = MemoryKeyValueStore()
        val clock = FakeFocusClock()
        val list = Countdowns(store, clock)
        list.add("Thesis", clock.today().plusDays(3))
        val reloaded = Countdowns(store, clock)
        assertThat(reloaded.all.map { it.name }).containsExactly("Thesis")
        reloaded.remove(reloaded.all[0].id)
        assertThat(Countdowns(store, clock).all).isEmpty()
    }
}

class ReadingWindowTest {

    private val zone = ZoneOffset.UTC
    private fun at(time: String) = Instant.parse("2026-09-28T$time:00Z")

    @Test
    fun `off means always open`() {
        assertThat(ReadingWindow().isOpen(at("03:00"), zone)).isTrue()
    }

    @Test
    fun `evening window`() {
        val window = ReadingWindow(enabled = true, start = 20 * 60, end = 22 * 60)
        assertThat(window.isOpen(at("19:59"), zone)).isFalse()
        assertThat(window.isOpen(at("20:00"), zone)).isTrue()
        assertThat(window.isOpen(at("21:59"), zone)).isTrue()
        assertThat(window.isOpen(at("22:00"), zone)).isFalse()
        assertThat(window.timeUntilOpen(at("18:30"), zone)).isEqualTo(90 * 60)
        assertThat(window.timeUntilOpen(at("23:00"), zone)).isEqualTo(21 * 3_600)
        assertThat(window.timeUntilOpen(at("20:30"), zone)).isZero()
    }

    @Test
    fun `window across midnight`() {
        val window = ReadingWindow(enabled = true, start = 22 * 60, end = 60)
        assertThat(window.isOpen(at("23:30"), zone)).isTrue()
        assertThat(window.isOpen(at("00:30"), zone)).isTrue()
        assertThat(window.isOpen(at("01:00"), zone)).isFalse()
        assertThat(window.isOpen(at("12:00"), zone)).isFalse()
    }

    @Test
    fun `wait label`() {
        assertThat(ReadingWindow.waitLabel(25 * 60)).isEqualTo("Opens in 25m")
        assertThat(ReadingWindow.waitLabel(3 * 3_600 + 12 * 60)).isEqualTo("Opens in 3h 12m")
    }
}

class ReadLaterTest {

    @Test
    fun `save mark read remove and persist`() {
        val store = MemoryKeyValueStore()
        val clock = FakeFocusClock()
        val list = ReadLater(store, clock)
        list.add("https://example.com/essay", "An essay")
        list.add("https://example.com/essay", "Again")
        list.add("mailto:a@example.com", "Mail")
        assertThat(list.all).hasSize(1)
        assertThat(list.contains("https://example.com/essay")).isTrue()
        list.setRead(list.all[0].id, true)
        assertThat(list.read).hasSize(1)
        assertThat(list.contains("https://example.com/essay")).isFalse()
        list.window = ReadingWindow(enabled = true, start = 8 * 60, end = 10 * 60)

        val reloaded = ReadLater(store, clock)
        assertThat(reloaded.all.map { it.title }).containsExactly("An essay")
        assertThat(reloaded.all[0].readAt).isNotNull
        assertThat(reloaded.window.start).isEqualTo(8 * 60)
        reloaded.remove(reloaded.all[0].id)
        assertThat(ReadLater(store, clock).all).isEmpty()
    }
}

class BookmarkTest {

    @Test
    fun `add toggle edit remove and persist`() {
        val store = MemoryKeyValueStore()
        val clock = FakeFocusClock()
        val bookmarks = Bookmarks(store, clock)
        bookmarks.add("https://example.com/", "  ")
        assertThat(bookmarks.all.first().title).isEqualTo("example.com")
        assertThat(bookmarks.contains("https://EXAMPLE.com")).isTrue()
        bookmarks.add("https://example.com/", "Duplicate")
        assertThat(bookmarks.all).hasSize(1)

        val id = bookmarks.all[0].id
        assertThat(bookmarks.update(id, "Search", "not a url")).isFalse()
        assertThat(bookmarks.update(id, "Docs", "developer.apple.com/documentation")).isTrue()
        assertThat(bookmarks.all[0].url).isEqualTo("https://developer.apple.com/documentation")

        val reloaded = Bookmarks(store, clock)
        assertThat(reloaded.all.map { it.title }).containsExactly("Docs")
        reloaded.toggle(reloaded.all[0].url, "")
        assertThat(reloaded.all).isEmpty()
        assertThat(Bookmarks(store, clock).all).isEmpty()
    }

    @Test
    fun `blocked sites can be bookmarked but other schemes cannot`() {
        val bookmarks = Bookmarks(MemoryKeyValueStore(), FakeFocusClock())
        bookmarks.add("https://youtube.com/", "YouTube")
        bookmarks.add("javascript:alert(1)", "Script")
        assertThat(bookmarks.all.map { it.title }).containsExactly("YouTube")
    }

    @Test
    fun `quick links are capped at eight`() {
        val store = MemoryKeyValueStore()
        val bookmarks = Bookmarks(store, FakeFocusClock())
        assertThat(bookmarks.quickLinkCount).isEqualTo(4)
        repeat(10) { bookmarks.add("https://site$it.example.com/", "$it") }
        assertThat(bookmarks.quickLinks.map { it.title }).containsExactly("0", "1", "2", "3")
        bookmarks.quickLinkCount = 20
        assertThat(bookmarks.quickLinkCount).isEqualTo(8)
        assertThat(Bookmarks(store, FakeFocusClock()).quickLinkCount).isEqualTo(8)
        bookmarks.quickLinkCount = 0
        assertThat(bookmarks.quickLinks).isEmpty()
    }
}

class FeatureGateTest {

    @Test
    fun `everything is unlocked by default`() {
        assertThat(Feature.entries.all { it.isUnlocked }).isTrue()
    }
}
