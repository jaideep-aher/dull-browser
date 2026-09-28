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

/**
 * Unit tests for [NaverSuggestionsModel].
 */
class NaverSuggestionsModelTest {

    private val httpClient = CompletableDeferred(OkHttpClient.Builder().build())
    private val requestFactory = object : RequestFactory {
        override fun createSuggestionsRequest(httpUrl: HttpUrl, encoding: String) = unimplemented()
    }

    @Test
    fun `verify query url`() = runTest {
        val model = NaverSuggestionsModel(
            httpClient,
            requestFactory,
            Locale.ROOT,
            FakeResourceProvider(),
            NoOpLogger(),
            FakeCoroutineDispatchers(testScheduler)
        )

        (0..100).forEach {
            val result =
                "https://ac.search.naver.com/nx/ac?q=$it&q_enc=UTF-8&st=100&frm=nv&r_format=json&r_enc=UTF-8&r_unicode=0&t_koreng=1&ans=2&run=2&rev=4&con=1"

            assertThat(
                model.createQueryUrl(it.toString(), "null")
            ).isEqualTo(result.toHttpUrlOrNull())
        }
    }
}
