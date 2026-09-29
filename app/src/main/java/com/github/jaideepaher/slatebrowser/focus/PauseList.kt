package com.github.jaideepaher.slatebrowser.focus

import com.github.jaideepaher.slatebrowser.adblock.siteblock.DomainMatcher
import com.github.jaideepaher.slatebrowser.concurrency.AppCoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sites that are not blocked but are easy to lose an hour to. Opening one shows a short,
 * calm pause first. Major news sites are not here because the built-in list already blocks them,
 * and the block list always wins over a pause.
 */
@Serializable
enum class PauseCategory(val title: String, val domains: List<String>) {
    @SerialName("shopping")
    SHOPPING(
        "Shopping",
        listOf(
            "amazon.com", "amazon.co.uk", "amazon.ca", "amazon.de", "amazon.in", "ebay.com", "ebay.co.uk",
            "etsy.com", "temu.com", "shein.com", "aliexpress.com", "walmart.com", "target.com", "bestbuy.com",
            "wish.com", "wayfair.com", "costco.com", "macys.com", "nordstrom.com", "kohls.com", "asos.com",
            "zara.com", "hm.com", "newegg.com", "poshmark.com", "mercari.com", "depop.com", "vinted.com",
            "zalando.com", "flipkart.com", "myntra.com", "ajio.com", "meesho.com",
        )
    ),
    @SerialName("news")
    NEWS(
        "News",
        listOf("npr.org", "nypost.com", "thehill.com", "breitbart.com", "dailymail.com", "express.co.uk")
    ),
    @SerialName("sports")
    SPORTS(
        "Sports",
        listOf(
            "espn.com", "espncricinfo.com", "cricbuzz.com", "bleacherreport.com", "si.com", "cbssports.com",
            "foxsports.com", "sports.yahoo.com", "theathletic.com", "nba.com", "nfl.com", "mlb.com", "nhl.com",
            "goal.com", "marca.com", "skysports.com",
        )
    ),
    @SerialName("celebrity")
    CELEBRITY(
        "Celebrity and gossip",
        listOf(
            "tmz.com", "people.com", "eonline.com", "pagesix.com", "usmagazine.com", "etonline.com",
            "justjared.com", "hollywoodlife.com", "radaronline.com", "x17online.com", "hellomagazine.com",
            "ok.co.uk", "buzzfeed.com",
        )
    );

    val rawValue: String
        get() = when (this) {
            SHOPPING -> "shopping"
            NEWS -> "news"
            SPORTS -> "sports"
            CELEBRITY -> "celebrity"
        }
}

/** A pause the person asked to turn off. It keeps applying until [effectiveAt]. */
@Serializable
data class ScheduledRemoval(
    val kind: Kind,
    val value: String,
    @Serializable(with = InstantSerializer::class)
    val effectiveAt: Instant,
) {
    @Serializable
    enum class Kind {
        @SerialName("category")
        CATEGORY,

        @SerialName("site")
        SITE,
    }

    val id: String get() = "${kind.name.lowercase()}:$value"
}

@Serializable
data class PauseSettings(
    val categories: Set<PauseCategory> = emptySet(),
    val sites: List<String> = emptyList(),
    val removals: List<ScheduledRemoval> = emptyList(),
)

object PauseRules {
    const val BASE_DELAY_SECONDS = 10.0
    const val GRACE_SECONDS = 5 * 60.0
    const val REMOVAL_DELAY_SECONDS = 24 * 60 * 60.0

    /** 10, 20, then 30 seconds for the first, second and later pauses on one site in a day. */
    fun delay(pausesEarlierToday: Int, base: Double = BASE_DELAY_SECONDS): Double =
        base * (minOf(maxOf(pausesEarlierToday, 0), 2) + 1)

    fun graceUntil(now: Instant): Instant = now.plusSeconds(GRACE_SECONDS.toLong())

    fun inGrace(until: Instant?, now: Instant): Boolean = until != null && until.isAfter(now)
}

data class PauseRequest(
    val url: String,
    val site: String,
    val delaySeconds: Double,
    val shownAt: Instant,
) {
    fun remaining(at: Instant): Double =
        maxOf(0.0, delaySeconds - Duration.between(shownAt, at).toMillis() / 1000.0)

    fun isReady(at: Instant): Boolean = remaining(at) <= 0
}

/**
 * Turning a pause on is immediate. Turning one off takes 24 hours and can be cancelled.
 */
@Singleton
class PauseList(
    private val store: KeyValueStore,
    private val clock: FocusClock,
    private val extraDomains: Set<String> = emptySet(),
    appCoroutineScope: AppCoroutineScope? = null,
) {

    @Inject
    constructor(
        store: KeyValueStore,
        clock: FocusClock,
        appCoroutineScope: AppCoroutineScope,
    ) : this(store, clock, emptySet(), appCoroutineScope)

    private val lock = Any()
    private val settingsFlow = MutableStateFlow(read())

    @Volatile
    private var matcher = DomainMatcher(emptySet())

    /** Called when a scheduled removal takes effect, which ends the day's streak. */
    var onLoosened: () -> Unit = {}

    val settings: PauseSettings
        get() = settingsFlow.value

    val settingsChanges: StateFlow<PauseSettings> = settingsFlow

    init {
        rebuild(settingsFlow.value)
        appCoroutineScope?.launch {
            store.externalChanges.collect {
                val next = read()
                synchronized(lock) {
                    settingsFlow.value = next
                    rebuild(next)
                }
            }
        }
    }

    /** The pause-list domain covering [host], or null if the host opens without a pause. */
    fun site(host: String?): String? {
        if (host.isNullOrEmpty() || exceptions.matches(host)) return null
        return matcher.match(host)
    }

    fun isOn(category: PauseCategory): Boolean = settings.categories.contains(category)

    fun pendingRemoval(kind: ScheduledRemoval.Kind, value: String): ScheduledRemoval? =
        settings.removals.firstOrNull { it.kind == kind && it.value == value }

    fun turnOn(category: PauseCategory) {
        synchronized(lock) {
            val current = settingsFlow.value
            commit(
                current.copy(
                    categories = current.categories + category,
                    removals = current.removals.filterNot {
                        it.kind == ScheduledRemoval.Kind.CATEGORY && it.value == category.rawValue
                    },
                )
            )
        }
    }

    sealed interface AddResult {
        data class Added(val domain: String) : AddResult
        data class AlreadyPaused(val domain: String) : AddResult
        data class Blocked(val domain: String) : AddResult
        data object Invalid : AddResult
    }

    fun addSite(input: String, isListed: (String) -> Boolean): AddResult {
        val domain = SiteAddress.normalize(input) ?: return AddResult.Invalid
        if (isListed(domain)) return AddResult.Blocked(domain)
        synchronized(lock) {
            val current = settingsFlow.value
            val withoutRemoval = current.copy(
                removals = current.removals.filterNot {
                    it.kind == ScheduledRemoval.Kind.SITE && it.value == domain
                }
            )
            if (withoutRemoval.sites.contains(domain)) {
                commit(withoutRemoval)
                return AddResult.AlreadyPaused(domain)
            }
            commit(withoutRemoval.copy(sites = withoutRemoval.sites + domain))
        }
        return AddResult.Added(domain)
    }

    fun scheduleRemoval(category: PauseCategory) {
        if (!isOn(category)) return
        schedule(ScheduledRemoval.Kind.CATEGORY, category.rawValue)
    }

    fun scheduleRemovalOfSite(domain: String) {
        if (!settings.sites.contains(domain)) return
        schedule(ScheduledRemoval.Kind.SITE, domain)
    }

    fun cancelRemoval(removal: ScheduledRemoval) {
        synchronized(lock) {
            val current = settingsFlow.value
            commit(current.copy(removals = current.removals.filterNot { it.id == removal.id }))
        }
    }

    /** Runs at launch, when the app becomes active and when pause settings open. */
    fun applyDueRemovals() {
        val date = clock.now()
        val loosened = synchronized(lock) {
            val current = settingsFlow.value
            val due = current.removals.filter { !it.effectiveAt.isAfter(date) }
            if (due.isEmpty()) return
            var categories = current.categories
            var sites = current.sites
            for (removal in due) {
                when (removal.kind) {
                    ScheduledRemoval.Kind.CATEGORY ->
                        PauseCategory.entries.find { it.rawValue == removal.value }?.let {
                            categories = categories - it
                        }
                    ScheduledRemoval.Kind.SITE ->
                        sites = sites.filterNot { it == removal.value }
                }
            }
            commit(
                current.copy(
                    categories = categories,
                    sites = sites,
                    removals = current.removals.filter { it.effectiveAt.isAfter(date) },
                )
            )
            true
        }
        if (loosened) onLoosened()
    }

    private fun schedule(kind: ScheduledRemoval.Kind, value: String) {
        synchronized(lock) {
            if (pendingRemoval(kind, value) != null) return
            val current = settingsFlow.value
            commit(
                current.copy(
                    removals = current.removals + ScheduledRemoval(
                        kind = kind,
                        value = value,
                        effectiveAt = clock.now().plusSeconds(PauseRules.REMOVAL_DELAY_SECONDS.toLong()),
                    )
                )
            )
        }
    }

    private fun commit(next: PauseSettings) {
        store.save(STORAGE_KEY, serializer, next)
        settingsFlow.value = next
        rebuild(next)
    }

    private fun rebuild(settings: PauseSettings) {
        val domains = settings.sites.toMutableSet()
        domains.addAll(extraDomains)
        for (category in settings.categories) domains.addAll(category.domains)
        matcher = DomainMatcher(domains)
    }

    private fun read(): PauseSettings =
        store.load(STORAGE_KEY, serializer) ?: PauseSettings()

    companion object {
        const val STORAGE_KEY = "pauseList.v1"

        val exceptions = DomainMatcher(
            setOf("aws.amazon.com", "developer.amazon.com", "sellercentral.amazon.com")
        )

        private val serializer = PauseSettings.serializer()
    }
}
