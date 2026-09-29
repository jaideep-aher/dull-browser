package com.github.jaideepaher.slatebrowser.focus

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.net.IDN
import java.net.URI
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * What a person types when naming a site, reduced to the domain the lists store.
 */
object SiteAddress {

    /**
     * `https://WWW.Example.com/path` becomes `example.com`. Returns null for anything that is not a
     * plain domain name: IP addresses, single words, spaces, or invalid labels.
     */
    fun normalize(input: String): String? {
        var text = input.trim().lowercase()
        if (text.isEmpty() || text.any { it.isWhitespace() }) return null
        if (!text.contains("://")) text = "https://$text"
        var host = runCatching { URI(text).host }.getOrNull()?.lowercase()
            ?: hostByHand(text)
            ?: return null
        host = host.trimEnd('.')
        if (host.startsWith("www.")) host = host.removePrefix("www.")
        host = runCatching { IDN.toASCII(host) }.getOrNull()?.lowercase() ?: return null
        return host.takeIf(::isDomain)
    }

    /** [URI] rejects hosts with underscores and similar; those are not domains anyway. */
    private fun hostByHand(text: String): String? {
        val afterScheme = text.substringAfter("://")
        val authority = afterScheme.substringBefore('/').substringBefore('?').substringBefore('#')
        val host = authority.substringAfterLast('@').substringBefore(':')
        return host.takeIf { it.isNotEmpty() }
    }

    fun isDomain(host: String): Boolean {
        if (host.length > 253) return false
        val labels = host.split('.')
        if (labels.size < 2) return false
        for (label in labels) {
            if (label.length !in 1..63) return false
            if (!label.all { it.isAsciiLetterOrDigit() || it == '-' }) return false
            if (label.first() == '-' || label.last() == '-') return false
        }
        val tld = labels.last()
        return tld.startsWith("xn--") || (tld.length >= 2 && tld.all { it in 'a'..'z' })
    }

    private fun Char.isAsciiLetterOrDigit() = this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9'
}

/**
 * Friendly names for the sites people try most, so the blocked page can say "YouTube".
 */
object SiteName {

    private val known = mapOf(
        "youtube.com" to "YouTube", "youtu.be" to "YouTube", "instagram.com" to "Instagram",
        "facebook.com" to "Facebook", "tiktok.com" to "TikTok", "x.com" to "X", "twitter.com" to "Twitter",
        "reddit.com" to "Reddit", "snapchat.com" to "Snapchat", "netflix.com" to "Netflix", "twitch.tv" to "Twitch",
        "pinterest.com" to "Pinterest", "linkedin.com" to "LinkedIn", "threads.net" to "Threads",
        "bbc.com" to "BBC", "bbc.co.uk" to "BBC", "cnn.com" to "CNN", "nytimes.com" to "The New York Times",
        "amazon.com" to "Amazon", "ebay.com" to "eBay", "etsy.com" to "Etsy", "temu.com" to "Temu",
        "shein.com" to "Shein", "aliexpress.com" to "AliExpress", "walmart.com" to "Walmart",
        "target.com" to "Target", "bestbuy.com" to "Best Buy", "espn.com" to "ESPN", "tmz.com" to "TMZ",
    )

    fun display(domain: String): String {
        val name = domain.lowercase().removePrefix("www.")
        return known[name] ?: name
    }
}

/**
 * ISO 8601 instants in whole seconds, the same text iOS writes.
 */
object InstantSerializer : KSerializer<Instant> {
    override val descriptor = PrimitiveSerialDescriptor("Instant", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Instant) =
        encoder.encodeString(value.truncatedTo(ChronoUnit.SECONDS).toString())

    override fun deserialize(decoder: Decoder): Instant = Instant.parse(decoder.decodeString())
}
