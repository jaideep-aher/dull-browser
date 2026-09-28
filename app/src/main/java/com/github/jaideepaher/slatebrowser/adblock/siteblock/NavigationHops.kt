package com.github.jaideepaher.slatebrowser.adblock.siteblock

import java.net.URLDecoder

/**
 * Hosts tucked into a link: AMP pages, share wrappers, intent handoffs.
 * Short links are left alone. The page that actually commits is checked on its own host.
 */
object NavigationHops {

    private val QUERY_KEYS = setOf(
        "url",
        "u",
        "q",
        "redirect",
        "redirect_url",
        "dest",
        "destination",
        "target",
        "to",
        "link",
        "href",
        "continue",
        "next",
        "adurl",
        "imgurl",
        "imgrefurl"
    )

    private val ampGoogle = Regex(
        """https?://(?:www\.)?google\.[^/]+/amp/(?:s/)?([^/?#]+)""",
        RegexOption.IGNORE_CASE
    )

    private val ampCdn = Regex(
        """https?://[^/]*cdn\.ampproject\.org/(?:[a-z]/)*s/([^/?#]+)""",
        RegexOption.IGNORE_CASE
    )

    /**
     * URLs this navigation can land on, including [rawUrl] itself.
     */
    fun extract(rawUrl: String): List<String> {
        val found = LinkedHashSet<String>()
        walk(rawUrl, depth = 0, into = found)
        return found.toList()
    }

    private fun walk(rawUrl: String, depth: Int, into: MutableSet<String>) {
        if (depth > 4) return
        val url = rawUrl.trim()
        if (url.isEmpty() || !into.add(url)) return

        unwrapIntent(url).forEach { walk(it, depth + 1, into) }
        unwrapAmp(url)?.let { walk(it, depth + 1, into) }
        if (isWrapper(url)) {
            queryDestinations(url).forEach { walk(it, depth + 1, into) }
        }
    }

    private fun isWrapper(url: String): Boolean {
        val host = hostOf(url) ?: return false
        val path = pathOf(url)
        val bare = host.removePrefix("www.")
        return when {
            bare == "google.com" || bare.endsWith(".google.com") || bare.startsWith("google.") ->
                path.startsWith("/url") ||
                    path.startsWith("/amp") ||
                    path.startsWith("/aclk") ||
                    path.startsWith("/goto") ||
                    path.startsWith("/link") ||
                    path.startsWith("/imgres")

            bare == "l.facebook.com" ||
                bare == "lm.facebook.com" ||
                bare == "l.instagram.com" ||
                bare == "l.messenger.com" -> true

            bare == "youtube.com" || bare == "m.youtube.com" || bare == "music.youtube.com" ->
                path.startsWith("/redirect")

            bare == "out.reddit.com" -> true
            path.startsWith("/l.php") -> true
            else -> false
        }
    }

    private fun queryDestinations(url: String): List<String> {
        val query = url.substringAfter('?', "").substringBefore('#')
        if (query.isEmpty()) return emptyList()
        return query.split('&').mapNotNull { pair ->
            val key = decode(pair.substringBefore('=')).lowercase()
            if (key !in QUERY_KEYS) return@mapNotNull null
            val value = decode(pair.substringAfter('=', ""))
            value.takeIf { it.startsWith("http://") || it.startsWith("https://") || it.startsWith("intent:") }
        }
    }

    private fun unwrapAmp(url: String): String? {
        val match = ampGoogle.find(url) ?: ampCdn.find(url) ?: return null
        val host = match.groupValues[1]
        if (!host.contains('.')) return null
        val rest = url.substring(match.range.last + 1)
        return "https://$host$rest"
    }

    private fun unwrapIntent(url: String): List<String> {
        if (!url.startsWith("intent:", ignoreCase = true)) return emptyList()
        val results = mutableListOf<String>()
        val beforeFragment = url.substringBefore('#')
        val hierarchical = beforeFragment.substringAfter(":", "")
        val fragment = url.substringAfter('#', "")
        val scheme = fragment
            .substringAfter("scheme=", "")
            .substringBefore(';')
            .ifBlank { "https" }
            .let(::decode)

        if (hierarchical.startsWith("//")) {
            val hostAndPath = hierarchical.removePrefix("//")
            if (hostAndPath.isNotEmpty()) {
                results += "$scheme://$hostAndPath"
            }
        }

        val fallbackKey = "S.browser_fallback_url="
        val fallbackIndex = fragment.indexOf(fallbackKey)
        if (fallbackIndex >= 0) {
            val encoded = fragment.substring(fallbackIndex + fallbackKey.length).substringBefore(';')
            val fallback = decode(encoded)
            if (fallback.startsWith("http://") || fallback.startsWith("https://")) {
                results += fallback
            }
        }
        return results
    }

    private fun hostOf(url: String): String? {
        val afterScheme = url.substringAfter("://", "")
        if (afterScheme.isEmpty()) return null
        val host = afterScheme
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore('#')
            .substringBefore(':')
            .lowercase()
        return host.takeIf { it.isNotEmpty() }
    }

    private fun pathOf(url: String): String {
        val afterScheme = url.substringAfter("://", url)
        val afterHost = afterScheme.substringAfter('/', missingDelimiterValue = "")
        val path = afterHost.substringBefore('?').substringBefore('#')
        return if (path.isEmpty()) "/" else "/$path"
    }

    private fun decode(value: String): String {
        var current = value
        repeat(2) {
            val decoded = runCatching { URLDecoder.decode(current, "UTF-8") }.getOrDefault(current)
            if (decoded == current) return current
            current = decoded
        }
        return current
    }
}
