package com.github.jaideepaher.slatebrowser.adblock.siteblock

import com.github.jaideepaher.slatebrowser.focus.CustomBlocklist
import com.github.jaideepaher.slatebrowser.log.Logger
import android.app.Application
import android.net.Uri
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Blocks a fixed list of sites compiled into the APK.
 *
 * This is deliberately separate from [com.github.jaideepaher.slatebrowser.adblock.AdBlocker]. The ad blocker is
 * something the user turns on and off and can allow-list per site; this one has no preference, no
 * allow list and no way to refresh from the network. The packaged list only changes with a new APK
 * (edit `tools/extra-domains.txt` and regenerate the asset). The person can add sites of their own
 * through [CustomBlocklist], which only grows.
 */
@Singleton
class SiteBlocker @Inject constructor(
    application: Application,
    private val dohSiteFilter: DohSiteFilter,
    private val customBlocklist: CustomBlocklist,
    logger: Logger
) {

    /**
     * Bare, lowercase domains. A host matches if it equals an entry or is a subdomain of one.
     */
    private val blockedDomains: Set<String> = runCatching {
        application.assets.open(BLOCKLIST_ASSET).bufferedReader().useLines { lines ->
            lines.filter { it.isNotBlank() }.toCollection(HashSet(EXPECTED_SIZE))
        }
    }.getOrElse { error ->
        // An unreadable asset must not silently turn blocking off, but there is nothing useful to
        // fall back to either, so record it loudly and keep the small hard-coded floor below.
        logger.log(TAG, "Failed to read $BLOCKLIST_ASSET", error)
        FALLBACK_DOMAINS
    }

    private val matcher = DomainMatcher(blockedDomains)

    /**
     * How many domains the packaged list closes, subdomains not counted separately.
     */
    val blockedDomainCount: Int
        get() = blockedDomains.size

    init {
        logger.log(TAG, "Loaded ${blockedDomains.size} blocked domains")
    }

    /**
     * Returns true if [uri] points at a blocked site.
     *
     * [consultResolver] additionally asks the filtering DNS resolver about hosts the packaged list
     * does not know. That costs a network round trip, so callers only set it for top level
     * navigations - the page someone actually opened - and not for every sub-resource on it.
     */
    fun isBlocked(uri: Uri, consultResolver: Boolean = false): Boolean {
        val host = uri.host?.lowercase()?.trimEnd('.')?.takeIf { it.isNotEmpty() } ?: return false
        if (isListed(host)) return true
        return consultResolver && dohSiteFilter.isFiltered(host)
    }

    /**
     * True if [host] is on the packaged list or on the sites the person added.
     */
    fun isListed(host: String): Boolean = listedDomain(host) != null

    /**
     * The list entry that closes [host], such as "youtube.com" for "m.youtube.com".
     */
    fun listedDomain(host: String): String? =
        matcher.match(host) ?: customBlocklist.match(host)

    /**
     * Blocks another site for good. There is no matching remove.
     */
    fun addCustom(input: String): CustomBlocklist.AddResult =
        customBlocklist.add(input) { matcher.matches(it) }

    companion object {
        private const val TAG = "SiteBlocker"
        private const val BLOCKLIST_ASSET = "blocklist.txt"

        /** Sized to the generated asset so the set never rehashes while loading. */
        private const val EXPECTED_SIZE = 131_072

        /**
         * Used only if the packaged asset cannot be read at all.
         */
        private val FALLBACK_DOMAINS = setOf(
            "facebook.com",
            "instagram.com",
            "reddit.com",
            "snapchat.com",
            "tiktok.com",
            "twitter.com",
            "x.com",
            "youtube.com",
            "youtu.be"
        )
    }
}
