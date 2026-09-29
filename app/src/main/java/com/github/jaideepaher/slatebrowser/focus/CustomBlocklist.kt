package com.github.jaideepaher.slatebrowser.focus

import com.github.jaideepaher.slatebrowser.adblock.siteblock.DomainMatcher
import com.github.jaideepaher.slatebrowser.concurrency.AppCoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sites the person chose to block, stored as a plain list of domains in the order added.
 *
 * There is deliberately no way to remove one: not in the UI, not in this API.
 */
@Singleton
class CustomBlocklist @Inject constructor(
    private val store: KeyValueStore,
    appCoroutineScope: AppCoroutineScope,
) {

    sealed interface AddResult {
        data class Added(val domain: String) : AddResult
        data class AlreadyBlocked(val domain: String) : AddResult
        data object Invalid : AddResult
        data object Full : AddResult
    }

    private val lock = Any()
    private val entriesFlow = MutableStateFlow(read())

    @Volatile
    private var matcher = DomainMatcher(entriesFlow.value.toSet())

    private val additionsFlow = MutableSharedFlow<String>(extraBufferCapacity = 16)

    /** The added domains, in the order added. */
    val entries: StateFlow<List<String>> = entriesFlow

    /** Emits each domain as it is added, here or in another process. */
    val additions: SharedFlow<String> = additionsFlow

    init {
        appCoroutineScope.launch {
            store.externalChanges.collect {
                val before = entriesFlow.value.toSet()
                val now = read()
                synchronized(lock) { publish(now) }
                (now - before).forEach { additionsFlow.tryEmit(it) }
            }
        }
    }

    fun match(host: String): String? = matcher.match(host)

    /**
     * Adding is immediate: the domain is blocked like a listed site as soon as this returns.
     *
     * @param isListed True for hosts the built-in list already closes.
     */
    fun add(input: String, isListed: (String) -> Boolean): AddResult {
        val domain = SiteAddress.normalize(input) ?: return AddResult.Invalid
        synchronized(lock) {
            if (isListed(domain) || matcher.matches(domain)) return AddResult.AlreadyBlocked(domain)
            val current = entriesFlow.value
            if (current.size >= LIMIT) return AddResult.Full
            val next = current + domain
            store.save(STORAGE_KEY, serializer, next)
            publish(next)
        }
        additionsFlow.tryEmit(domain)
        return AddResult.Added(domain)
    }

    private fun publish(domains: List<String>) {
        // Never shrink: a stale read from another process must not reopen a site.
        val merged = (entriesFlow.value + domains).distinct()
        matcher = DomainMatcher(merged.toSet())
        entriesFlow.value = merged
    }

    private fun read(): List<String> =
        store.load(STORAGE_KEY, serializer).orEmpty().filter(SiteAddress::isDomain)

    companion object {
        const val STORAGE_KEY = "customBlocklist.v1"
        const val LIMIT = 1_000
        private val serializer = ListSerializer(String.serializer())
    }
}
