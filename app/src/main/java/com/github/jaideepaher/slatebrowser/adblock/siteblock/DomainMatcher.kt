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
    fun matches(host: String): Boolean {
        val normalized = host.lowercase().trimEnd('.')
        if (normalized.isEmpty()) return false

        var index = 0
        while (index < normalized.length) {
            val candidate = normalized.substring(index)
            // Stop before the final label: a stray "com" in the list must not block the web.
            if (!candidate.contains('.')) return false
            if (candidate in domains) return true
            index = normalized.indexOf('.', index) + 1
        }
        return false
    }
}
