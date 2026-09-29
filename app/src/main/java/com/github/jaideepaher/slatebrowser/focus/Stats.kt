package com.github.jaideepaher.slatebrowser.focus

import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

@Serializable
data class DayStats(
    val day: String,
    val blocked: Map<String, Int> = emptyMap(),
    val paused: Map<String, Int> = emptyMap(),
    val wentBack: Int = 0,
    val continued: Int = 0,
    val loosened: Boolean = false,
) {
    val blockedTotal: Int get() = blocked.values.sum()
}

@Serializable
data class Milestone(
    val days: Int,
    val reached: String,
)

@Serializable
data class StatsData(
    val days: List<DayStats> = emptyList(),
    val milestones: List<Milestone> = emptyList(),
    val noticeMilestone: Int? = null,
)

data class WeekSummary(
    val attempts: Int,
    val wentBack: Int,
    val continued: Int,
    val minutesSaved: Int,
    val topSites: List<Pair<String, Int>>,
)

/**
 * Counts kept on this device only. A day joins the streak when Dull was opened that day and no
 * pause was turned off that day. A day without Dull, or a day a pause removal took effect, ends it.
 */
@Singleton
class Stats @Inject constructor(
    private val store: KeyValueStore,
    private val clock: FocusClock,
) {

    private val lock = Any()

    @Volatile
    private var data: StatsData = store.load(STORAGE_KEY, serializer) ?: StatsData()

    val snapshot: StatsData
        get() = data

    val today: String
        get() = DayKey.string(clock.today())

    var minutesPerAttempt: Int
        get() {
            val stored = store.int(MINUTES_KEY)
            return if (stored != null && stored in MINUTE_OPTIONS) stored else DEFAULT_MINUTES
        }
        set(value) {
            if (value in MINUTE_OPTIONS) store.putInt(MINUTES_KEY, value)
        }

    fun day(key: String): DayStats? = data.days.lastOrNull { it.day == key }

    fun blockedToday(site: String): Int = day(today)?.blocked?.get(site) ?: 0

    fun pausesToday(site: String): Int = day(today)?.paused?.get(site) ?: 0

    fun recordUse() = update { it }

    fun recordBlocked(site: String) = update {
        it.copy(blocked = it.blocked + (site to (it.blocked[site] ?: 0) + 1))
    }

    fun recordPauseShown(site: String) = update {
        it.copy(paused = it.paused + (site to (it.paused[site] ?: 0) + 1))
    }

    fun recordPause(wentBack: Boolean) = update {
        if (wentBack) it.copy(wentBack = it.wentBack + 1) else it.copy(continued = it.continued + 1)
    }

    fun recordLoosening() = update { it.copy(loosened = true) }

    fun dismissMilestoneNotice() {
        synchronized(lock) {
            if (data.noticeMilestone == null) return
            data = data.copy(noticeMilestone = null)
            save()
        }
    }

    val currentStreak: Int
        get() = streak(data.days, today)

    fun week(): WeekSummary = summary(data.days, today, minutesPerAttempt)

    private fun update(change: (DayStats) -> DayStats) {
        synchronized(lock) {
            val key = today
            val days = data.days.toMutableList()
            val index = days.indexOfLast { it.day == key }
            if (index >= 0) {
                days[index] = change(days[index])
            } else {
                days += change(DayStats(day = key))
                days.sortBy { it.day }
                if (days.size > HISTORY_LIMIT) {
                    repeat(days.size - HISTORY_LIMIT) { days.removeAt(0) }
                }
            }
            data = checkMilestones(data.copy(days = days), key)
            save()
        }
    }

    private fun checkMilestones(current: StatsData, key: String): StatsData {
        val streak = streak(current.days, key)
        var next = current
        for (days in MILESTONE_DAYS) {
            if (streak >= days && next.milestones.none { it.days == days }) {
                next = next.copy(
                    milestones = next.milestones + Milestone(days, key),
                    noticeMilestone = days,
                )
            }
        }
        return next
    }

    private fun save() {
        store.save(STORAGE_KEY, serializer, data)
    }

    companion object {
        const val STORAGE_KEY = "stats.v1"
        const val MINUTES_KEY = "stats.minutesPerAttempt"
        const val HISTORY_LIMIT = 400
        const val DEFAULT_MINUTES = 8
        val MILESTONE_DAYS = listOf(7, 30, 100)
        val MINUTE_OPTIONS = listOf(3, 5, 8, 10, 15)

        private val serializer = StatsData.serializer()

        /** Consecutive clean days ending today, or yesterday if today has no record yet. */
        fun streak(days: List<DayStats>, today: String): Int {
            val byDay = days.associateBy { it.day }
            var cursor = today
            if (byDay[today] == null) {
                cursor = DayKey.adding(-1, today) ?: return 0
            }
            var count = 0
            while (true) {
                val record = byDay[cursor] ?: break
                if (record.loosened) break
                count += 1
                cursor = DayKey.adding(-1, cursor) ?: break
            }
            return count
        }

        /** The seven days ending on [endingOn], today included. */
        fun summary(days: List<DayStats>, endingOn: String, minutesPerAttempt: Int): WeekSummary {
            val window = (0 until 7).mapNotNull { DayKey.adding((-it).toLong(), endingOn) }.toSet()
            val recent = days.filter { it.day in window }
            val perSite = mutableMapOf<String, Int>()
            for (day in recent) {
                for ((site, count) in day.blocked) {
                    perSite[site] = (perSite[site] ?: 0) + count
                }
            }
            val attempts = recent.sumOf { it.blockedTotal }
            val wentBack = recent.sumOf { it.wentBack }
            val top = perSite.entries
                .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
                .take(5)
                .map { it.key to it.value }
            return WeekSummary(
                attempts = attempts,
                wentBack = wentBack,
                continued = recent.sumOf { it.continued },
                minutesSaved = (attempts + wentBack) * minutesPerAttempt,
                topSites = top,
            )
        }

        /** "~40m" under an hour, then whole hours: "~6h". */
        fun savedLabel(minutes: Int): String =
            if (minutes < 60) "~${minutes}m" else "~${(minutes / 60.0).roundToInt()}h"
    }
}
