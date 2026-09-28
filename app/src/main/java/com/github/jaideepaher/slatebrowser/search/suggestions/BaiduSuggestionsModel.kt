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
 * The search suggestions provider for the Baidu search engine.
 */
class BaiduSuggestionsModel @Inject constructor(
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
    private val serializer = Json.serializersModule.serializer<BaiduSuggestion>()

    // see http://unionsug.baidu.com/su?wd={encodedQuery}
    // see http://suggestion.baidu.com/su?wd={encodedQuery}&json=2&cb=
    override fun createQueryUrl(query: String, language: String): HttpUrl = HttpUrl.Builder()
        .scheme("http")
        .host("suggestion.baidu.com")
        .encodedPath("/su")
        .addEncodedQueryParameter("wd", query)
        .addQueryParameter("json", "2")
        .addQueryParameter("cb", "")
        .build()


    @OptIn(ExperimentalSerializationApi::class)
    @Throws(Exception::class)
    override fun parseResults(responseBody: ResponseBody): List<SearchSuggestion> {
        return Json.decodeFromStream(serializer, responseBody.byteStream()).items.map {
            SearchSuggestion("$searchSubtitle \"$it\"", it)
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    @JsonIgnoreUnknownKeys
    @Serializable
    data class BaiduSuggestion(
        @SerialName("s")
        val items: List<String>
    )
}
