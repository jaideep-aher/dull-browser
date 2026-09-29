package com.github.jaideepaher.slatebrowser.focus

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import java.util.concurrent.ConcurrentHashMap

class MemoryKeyValueStore : KeyValueStore {
    private val values = ConcurrentHashMap<String, Any>()
    override val externalChanges: Flow<Unit> = MutableSharedFlow()

    override fun string(key: String): String? = values[key] as? String

    override fun int(key: String): Int? = values[key] as? Int

    override fun putString(key: String, value: String) {
        values[key] = value
    }

    override fun putInt(key: String, value: Int) {
        values[key] = value
    }
}

class FakeFocusClock(
    var instant: java.time.Instant = java.time.Instant.parse("2026-09-28T12:00:00Z"),
    var zoneId: java.time.ZoneId = java.time.ZoneOffset.UTC,
) : FocusClock {
    override fun now(): java.time.Instant = instant
    override fun zone(): java.time.ZoneId = zoneId

    fun advance(days: Double = 0.0, hours: Double = 0.0) {
        instant = instant.plusSeconds((days * 86_400 + hours * 3_600).toLong())
    }
}
