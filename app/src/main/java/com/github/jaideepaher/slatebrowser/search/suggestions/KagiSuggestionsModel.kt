package com.github.jaideepaher.slatebrowser.search.suggestions

import com.github.jaideepaher.slatebrowser.R
import com.github.jaideepaher.slatebrowser.concurrency.CoroutineDispatchers
import com.github.jaideepaher.slatebrowser.constant.UTF8
import com.github.jaideepaher.slatebrowser.database.SearchSuggestion
import com.github.jaideepaher.slatebrowser.di.SuggestionsClient
import com.github.jaideepaher.slatebrowser.log.Logger
import com.github.jaideepaher.slatebrowser.resources.ResourceProvider
import kotlinx.coroutines.Deferred
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonIgnoreUnknownKeys
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.serializer
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import java.util.Locale
import javax.inject.Inject

/**
 * Search suggestions for Kagi. Requires being logged into Kagi.
 */
class KagiSuggestionsModel @Inject constructor(
    @SuggestionsClient okHttpClient: Deferred<@JvmSuppressWildcards OkHttpClient>,
    requestFactory: RequestFactory,
    locale: Locale,
    resourceProvider: ResourceProvider,
    logger: Logger,
    coroutineDispatchers: CoroutineDispatchers,
) : BaseSuggestionsModel(
    okHttpClient,
    requestFactory,
    UTF8,
    locale,
    logger,
    coroutineDispatchers
) {
    private val searchSubtitle = resourceProvider.stringResource(R.string.suggestion)
    private val serializer = Json.serializersModule.serializer<List<KagiSuggestion>>()

    // https://kagi.com/autosuggest?q={query}
    override fun createQueryUrl(query: String, language: String): HttpUrl = HttpUrl.Builder()
        .scheme("https")
        .host("kagi.com")
        .encodedPath("/autosuggest")
        .addEncodedQueryParameter("q", query)
        .build()

    @OptIn(ExperimentalSerializationApi::class)
    override fun parseResults(responseBody: ResponseBody): List<SearchSuggestion> {
        return Json.decodeFromStream(serializer, responseBody.byteStream())
            .map { SearchSuggestion("$searchSubtitle \"${it.text}\"", it.text) }
    }

    @OptIn(ExperimentalSerializationApi::class)
    @JsonIgnoreUnknownKeys
    @Serializable
    data class KagiSuggestion(
        @SerialName("t")
        val text: String
    )
}
