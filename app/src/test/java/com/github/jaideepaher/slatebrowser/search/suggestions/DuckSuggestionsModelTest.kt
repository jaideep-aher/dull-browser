package com.github.jaideepaher.slatebrowser.search.suggestions

import com.github.jaideepaher.slatebrowser.concurrency.FakeCoroutineDispatchers
import com.github.jaideepaher.slatebrowser.log.NoOpLogger
import com.github.jaideepaher.slatebrowser.resources.FakeResourceProvider
import com.github.jaideepaher.slatebrowser.unimplemented
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.util.Locale

class DuckSuggestionsModelTest {

    private val httpClient = CompletableDeferred(OkHttpClient.Builder().build())
    private val requestFactory = object : RequestFactory {
        override fun createSuggestionsRequest(httpUrl: HttpUrl, encoding: String) = unimplemented()
    }

    @Test
    fun `verify query url`() = runTest {
        val suggestionsModel = DuckSuggestionsModel(
            httpClient,
            requestFactory,
            Locale.ROOT,
            FakeResourceProvider(),
            NoOpLogger(),
            FakeCoroutineDispatchers(testScheduler)
        )

        (0..100).forEach {
            val result = "https://duckduckgo.com/ac/?q=$it"

            assertThat(
                suggestionsModel.createQueryUrl(it.toString(), "null")
            ).isEqualTo(result.toHttpUrlOrNull())
        }
    }
}
