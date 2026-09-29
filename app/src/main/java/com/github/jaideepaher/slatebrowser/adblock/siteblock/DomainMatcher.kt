package com.github.jaideepaher.slatebrowser.adblock.siteblock

/**
 * Matches a host against a set of blocked domains.
 *
 * A host matches when it equals a listed domain or is a subdomain of one, so a single
 * "youtube.com" entry covers "m.youtube.com" and "music.youtube.com" too.
 */
class DomainMatcher(private val domains: Set<String>) {

    /**
     * Returns true if [host] or any of its parent domains is blocked.
     */
    fun matches(host: String): Boolean = match(host) != null

    /**
     * The listed domain that covers [host], or null if none does.
     */
    fun match(host: String): String? {
        val normalized = host.lowercase().trimEnd('.')
        if (normalized.isEmpty()) return null

        var index = 0
        while (index < normalized.length) {
            val candidate = normalized.substring(index)
            // Stop before the final label: a stray "com" in the list must not block the web.
            if (!candidate.contains('.')) return null
            if (candidate in domains) return candidate
            index = normalized.indexOf('.', index) + 1
        }
        return null
    }
}
