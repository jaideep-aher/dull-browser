package com.github.jaideepaher.slatebrowser.focus

import com.github.jaideepaher.slatebrowser.concurrency.AppCoroutineScope
import com.github.jaideepaher.slatebrowser.concurrency.CoroutineDispatchers
import com.github.jaideepaher.slatebrowser.di.IncognitoMode
import android.app.Application
import androidx.datastore.core.DataStore
import androidx.datastore.core.MultiProcessDataStoreFactory
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesFileSerializer
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Small values the focus features keep on this device, read synchronously from memory.
 *
 * Reads never touch the disk after the first one, so the blocking and pause checks that run on
 * every navigation stay a set lookup.
 */
interface KeyValueStore {

    fun string(key: String): String?

    fun int(key: String): Int?

    fun putString(key: String, value: String)

    fun putInt(key: String, value: Int)

    /**
     * Emits when another process changed the stored values.
     */
    val externalChanges: Flow<Unit>
}

/** The JSON used for every stored document. Matches the iOS shapes in `ios/README.md`. */
val FocusJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
}

fun <T> KeyValueStore.load(key: String, serializer: KSerializer<T>): T? =
    string(key)?.let { runCatching { FocusJson.decodeFromString(serializer, it) }.getOrNull() }

fun <T> KeyValueStore.save(key: String, serializer: KSerializer<T>, value: T) =
    putString(key, FocusJson.encodeToString(serializer, value))

/**
 * A [KeyValueStore] backed by a multi-process Preferences DataStore.
 *
 * The incognito browser runs in its own process. It reads the same file, so sites added to the
 * block list and pauses apply there too, but it never writes: what happens in incognito is not
 * recorded, and the main process stays the only writer.
 */
@Singleton
class FocusDataStore @Inject constructor(
    application: Application,
    @IncognitoMode private val incognitoMode: Boolean,
    appCoroutineScope: AppCoroutineScope,
    coroutineDispatchers: CoroutineDispatchers,
) : KeyValueStore {

    private val dataStore: DataStore<Preferences> = MultiProcessDataStoreFactory.create(
        serializer = PreferencesFileSerializer,
        produceFile = { File(application.filesDir, "datastore/$FILE_NAME") }
    )

    private val values = ConcurrentHashMap<String, Any>()

    @Volatile
    private var loaded = false
    private val loadLock = Any()

    private val writes = Channel<(MutablePreferences) -> Unit>(Channel.UNLIMITED)

    override val externalChanges = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    init {
        appCoroutineScope.launch(coroutineDispatchers.io) {
            ensureLoaded()
            if (incognitoMode) {
                dataStore.data.collect { preferences ->
                    replaceWith(preferences)
                    externalChanges.tryEmit(Unit)
                }
            } else {
                for (write in writes) {
                    dataStore.edit { write(it) }
                }
            }
        }
    }

    private fun ensureLoaded() {
        if (loaded) return
        synchronized(loadLock) {
            if (loaded) return
            val preferences = runCatching { runBlocking { dataStore.data.first() } }.getOrNull()
            preferences?.let(::replaceWith)
            loaded = true
        }
    }

    private fun replaceWith(preferences: Preferences) {
        val next = preferences.asMap().mapKeys { it.key.name }
        values.keys.retainAll(next.keys)
        next.forEach { (key, value) -> values[key] = value }
    }

    override fun string(key: String): String? {
        ensureLoaded()
        return values[key] as? String
    }

    override fun int(key: String): Int? {
        ensureLoaded()
        return values[key] as? Int
    }

    override fun putString(key: String, value: String) {
        ensureLoaded()
        values[key] = value
        if (!incognitoMode) {
            writes.trySend { it[stringPreferencesKey(key)] = value }
        }
    }

    override fun putInt(key: String, value: Int) {
        ensureLoaded()
        values[key] = value
        if (!incognitoMode) {
            writes.trySend { it[intPreferencesKey(key)] = value }
        }
    }

    companion object {
        private const val FILE_NAME = "dull_focus.preferences_pb"
    }
}
