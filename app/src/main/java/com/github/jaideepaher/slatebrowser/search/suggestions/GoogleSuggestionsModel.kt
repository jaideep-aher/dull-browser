package com.github.jaideepaher.slatebrowser.search.suggestions

import com.github.jaideepaher.slatebrowser.R
import com.github.jaideepaher.slatebrowser.concurrency.CoroutineDispatchers
import com.github.jaideepaher.slatebrowser.constant.UTF8
import com.github.jaideepaher.slatebrowser.database.SearchSuggestion
import com.github.jaideepaher.slatebrowser.di.SuggestionsClient
import com.github.jaideepaher.slatebrowser.log.Logger
import com.github.jaideepaher.slatebrowser.resources.ResourceProvider
import kotlinx.coroutines.Deferred
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.util.Locale
import javax.inject.Inject

/**
 * Search suggestions provider for Google search engine.
 */
class GoogleSuggestionsModel @Inject constructor(
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

    // https://suggestqueries.google.com/complete/search?output=toolbar&hl={language}&q={query}
    override fun createQueryUrl(query: String, language: String): HttpUrl = HttpUrl.Builder()
        .scheme("https")
        .host("suggestqueries.google.com")
        .encodedPath("/complete/search")
        .addQueryParameter("output", "toolbar")
        .addQueryParameter("hl", language)
        .addEncodedQueryParameter("q", query)
        .build()

    @Throws(Exception::class)
    override fun parseResults(responseBody: ResponseBody): List<SearchSuggestion> {
        parser.setInput(responseBody.byteStream(), UTF8)

        val suggestions = mutableListOf<SearchSuggestion>()
        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && "suggestion" == parser.name) {
                val suggestion = parser.getAttributeValue(null, "data")
                suggestions.add(SearchSuggestion("$searchSubtitle \"$suggestion\"", suggestion))
            }
            eventType = parser.next()
        }

        return suggestions
    }

    companion object {

        // Converting to a lambda results in pulling the newInstance call out of the lazy.
        @Suppress("ConvertLambdaToReference")
        private val parser by lazy {
            XmlPullParserFactory.newInstance().apply {
                isNamespaceAware = true
            }.newPullParser()
        }

    }
}
