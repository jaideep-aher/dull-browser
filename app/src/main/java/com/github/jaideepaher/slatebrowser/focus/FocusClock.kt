package com.github.jaideepaher.slatebrowser.focus

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/**
 * The wall clock the focus features use, so tests can move time.
 */
interface FocusClock {

    fun now(): Instant

    fun zone(): ZoneId

    fun today(): LocalDate = now().atZone(zone()).toLocalDate()

    fun zonedNow(): ZonedDateTime = now().atZone(zone())
}

@javax.inject.Singleton
class SystemFocusClock @Inject constructor() : FocusClock {

    override fun now(): Instant = Instant.now()

    override fun zone(): ZoneId = ZoneId.systemDefault()
}

/**
 * Local calendar days as `yyyy-MM-dd`, the key used for stats and countdowns.
 */
object DayKey {

    private val format = DateTimeFormatter.ISO_LOCAL_DATE

    fun string(date: LocalDate): String = date.format(format)

    fun date(key: String): LocalDate? = runCatching { LocalDate.parse(key, format) }.getOrNull()

    fun adding(days: Long, key: String): String? = date(key)?.plusDays(days)?.let(::string)

    /** Whole calendar days from [from] to [to]; negative when [to] is earlier. */
    fun days(from: String, to: String): Long? {
        val start = date(from) ?: return null
        val end = date(to) ?: return null
        return ChronoUnit.DAYS.between(start, end)
    }
}
