package com.github.jaideepaher.slatebrowser.focus

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class StatsTest {

    private fun days(keys: List<String>, loosened: Set<String> = emptySet()) =
        keys.map { DayStats(day = it, loosened = it in loosened) }

    @Test
    fun `events aggregate per day and persist`() {
        val store = MemoryKeyValueStore()
        val clock = FakeFocusClock()
        val stats = Stats(store, clock)
        stats.recordBlocked("youtube.com")
        stats.recordBlocked("youtube.com")
        stats.recordBlocked("reddit.com")
        stats.recordPauseShown("amazon.com")
        stats.recordPause(wentBack = true)
        clock.advance(days = 1.0)
        stats.recordBlocked("youtube.com")
        stats.recordPause(wentBack = false)

        val reloaded = Stats(store, clock)
        assertThat(reloaded.snapshot.days.map { it.day }).containsExactly("2026-09-28", "2026-09-29")
        assertThat(reloaded.day("2026-09-28")?.blocked).isEqualTo(mapOf("youtube.com" to 2, "reddit.com" to 1))
        assertThat(reloaded.day("2026-09-28")?.wentBack).isEqualTo(1)
        assertThat(reloaded.day("2026-09-29")?.continued).isEqualTo(1)
        assertThat(reloaded.blockedToday("youtube.com")).isEqualTo(1)
        assertThat(reloaded.pausesToday("amazon.com")).isZero()
    }

    @Test
    fun `streak counts consecutive clean days`() {
        val today = "2026-09-28"
        assertThat(Stats.streak(emptyList(), today)).isZero()
        assertThat(Stats.streak(days(listOf("2026-09-26", "2026-09-27", today)), today)).isEqualTo(3)
        assertThat(Stats.streak(days(listOf("2026-09-26", "2026-09-27")), today)).isEqualTo(2)
        assertThat(Stats.streak(days(listOf("2026-09-24", "2026-09-26", "2026-09-27", today)), today)).isEqualTo(3)
        assertThat(
            Stats.streak(days(listOf("2026-09-26", "2026-09-27", today), loosened = setOf("2026-09-27")), today)
        ).isEqualTo(1)
        assertThat(
            Stats.streak(days(listOf("2026-09-27", today), loosened = setOf(today)), today)
        ).isZero()
        assertThat(Stats.streak(days(listOf("2026-09-30", "2026-10-01")), "2026-10-01")).isEqualTo(2)
    }

    @Test
    fun `milestones are reached once with a one-time notice`() {
        val store = MemoryKeyValueStore()
        val clock = FakeFocusClock(java.time.Instant.parse("2026-09-01T09:00:00Z"))
        val stats = Stats(store, clock)
        repeat(6) {
            stats.recordUse()
            clock.advance(days = 1.0)
        }
        assertThat(stats.snapshot.milestones).isEmpty()
        stats.recordUse()
        assertThat(stats.currentStreak).isEqualTo(7)
        assertThat(stats.snapshot.milestones).containsExactly(Milestone(7, "2026-09-07"))
        assertThat(stats.snapshot.noticeMilestone).isEqualTo(7)
        stats.dismissMilestoneNotice()
        stats.recordUse()
        assertThat(stats.snapshot.noticeMilestone).isNull()
        assertThat(stats.snapshot.milestones).hasSize(1)

        clock.advance(days = 2.0)
        repeat(7) {
            stats.recordUse()
            clock.advance(days = 1.0)
        }
        assertThat(stats.snapshot.milestones).hasSize(1)
        assertThat(stats.snapshot.noticeMilestone).isNull()
    }

    @Test
    fun `thirty day milestone`() {
        val clock = FakeFocusClock(java.time.Instant.parse("2026-01-01T09:00:00Z"))
        val stats = Stats(MemoryKeyValueStore(), clock)
        repeat(30) {
            stats.recordUse()
            clock.advance(days = 1.0)
        }
        assertThat(stats.snapshot.milestones.map { it.days }).containsExactly(7, 30)
        assertThat(stats.snapshot.milestones.last().reached).isEqualTo("2026-01-30")
        assertThat(stats.snapshot.noticeMilestone).isEqualTo(30)
    }

    @Test
    fun `history is capped`() {
        val clock = FakeFocusClock(java.time.Instant.parse("2025-01-01T09:00:00Z"))
        val stats = Stats(MemoryKeyValueStore(), clock)
        repeat(Stats.HISTORY_LIMIT + 20) {
            stats.recordUse()
            clock.advance(days = 1.0)
        }
        assertThat(stats.snapshot.days).hasSize(Stats.HISTORY_LIMIT)
        assertThat(stats.snapshot.days.last().day)
            .isEqualTo(DayKey.string(clock.today().minusDays(1)))
    }

    @Test
    fun `week summary and time saved`() {
        val list = days(listOf("2026-09-20", "2026-09-22", "2026-09-28")).toMutableList()
        list[0] = list[0].copy(blocked = mapOf("youtube.com" to 50))
        list[1] = list[1].copy(blocked = mapOf("youtube.com" to 3, "reddit.com" to 4), wentBack = 2)
        list[2] = list[2].copy(blocked = mapOf("reddit.com" to 1), continued = 1)
        val week = Stats.summary(list, "2026-09-28", minutesPerAttempt = 8)
        assertThat(week.attempts).isEqualTo(8)
        assertThat(week.wentBack).isEqualTo(2)
        assertThat(week.continued).isEqualTo(1)
        assertThat(week.minutesSaved).isEqualTo(80)
        assertThat(week.topSites.map { it.first }).containsExactly("reddit.com", "youtube.com")
        assertThat(Stats.savedLabel(40)).isEqualTo("~40m")
        assertThat(Stats.savedLabel(80)).isEqualTo("~1h")
        assertThat(Stats.savedLabel(370)).isEqualTo("~6h")
    }

    @Test
    fun `minutes per attempt defaults to eight`() {
        val store = MemoryKeyValueStore()
        val stats = Stats(store, FakeFocusClock())
        assertThat(stats.minutesPerAttempt).isEqualTo(8)
        store.putInt(Stats.MINUTES_KEY, 15)
        assertThat(stats.minutesPerAttempt).isEqualTo(15)
        store.putInt(Stats.MINUTES_KEY, 99)
        assertThat(stats.minutesPerAttempt).isEqualTo(8)
    }

    @Test
    fun `share line has totals only`() {
        val week = WeekSummary(214, 0, 0, 360, listOf("youtube.com" to 200))
        val line = ShareCard.line(week, 9)
        assertThat(line).isEqualTo("Dull blocked 214 attempts this week · 9-day streak · ~6h saved")
        assertThat(line).doesNotContain("youtube")
        assertThat(
            ShareCard.line(WeekSummary(1, 0, 0, 0, emptyList()), 0)
        ).isEqualTo("Dull blocked 1 attempt this week")
    }

    @Test
    fun `stored format is plain JSON`() {
        val store = MemoryKeyValueStore()
        val stats = Stats(store, FakeFocusClock())
        stats.recordBlocked("youtube.com")
        val json = store.string(Stats.STORAGE_KEY)
        assertThat(json).contains("\"day\":\"2026-09-28\"")
        assertThat(json).contains("\"youtube.com\":1")
    }
}
