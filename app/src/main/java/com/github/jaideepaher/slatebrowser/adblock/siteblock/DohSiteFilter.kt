package com.github.jaideepaher.slatebrowser.adblock.siteblock

import com.github.jaideepaher.slatebrowser.log.Logger
import android.util.LruCache
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Asks a content-filtering DNS resolver whether a host is adult content.
 *
 * The packaged blocklist is exact but finite, and new adult domains appear constantly. This covers
 * the long tail by resolving unknown hosts through Cloudflare's family resolver, which answers
 * 0.0.0.0 for anything it classifies as adult. It only sees hosts the local list did not already
 * decide on.
 *
 * The resolver is compiled in and has no corresponding setting, matching the rest of the blocker.
 */
@Singleton
class DohSiteFilter @Inject constructor(
    private val logger: Logger
) {

    private val client = OkHttpClient.Builder()
        .callTimeout(CALL_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .connectTimeout(CALL_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .readTimeout(CALL_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .build()

    private val cache = LruCache<String, Boolean>(CACHE_ENTRIES)

    /**
     * Returns true if the resolver says [host] is filtered.
     *
     * Network problems resolve to false. Failing open is deliberate: the packaged list still
     * applies, and failing closed would make the browser unusable on a flaky connection.
     */
    fun isFiltered(host: String): Boolean {
        cache.get(host)?.let { return it }

        val filtered = runCatching { query(host) }.getOrElse { error ->
            logger.log(TAG, "DNS lookup failed for $host", error)
            return false
        }

        cache.put(host, filtered)
        return filtered
    }

    private fun query(host: String): Boolean {
        val request = Request.Builder()
            .url("$RESOLVER?name=$host&type=A")
            .header("accept", DNS_JSON_MIME_TYPE)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return false
            val body = response.body?.string() ?: return false
            val answers = JSONObject(body).optJSONArray("Answer") ?: return false

            // A filtered name resolves to the null address instead of its real records.
            for (index in 0 until answers.length()) {
                val data = answers.optJSONObject(index)?.optString("data") ?: continue
                if (data == BLOCKED_ADDRESS_V4 || data == BLOCKED_ADDRESS_V6) return true
            }
            return false
        }
    }

    companion object {
        private const val TAG = "DohSiteFilter"

        /** Cloudflare for Families: blocks malware and adult content, no account needed. */
        private const val RESOLVER = "https://family.cloudflare-dns.com/dns-query"
        private const val DNS_JSON_MIME_TYPE = "application/dns-json"

        private const val BLOCKED_ADDRESS_V4 = "0.0.0.0"
        private const val BLOCKED_ADDRESS_V6 = "::"

        /** Kept short so a slow resolver cannot noticeably stall a page load. */
        private const val CALL_TIMEOUT_MS = 1_500L

        private const val CACHE_ENTRIES = 2_048
    }
}
