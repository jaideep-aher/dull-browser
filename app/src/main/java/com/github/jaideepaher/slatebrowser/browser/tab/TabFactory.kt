package com.github.jaideepaher.slatebrowser.browser.tab

import com.github.jaideepaher.slatebrowser.browser.tab.settings.TabSettings
import com.github.jaideepaher.slatebrowser.concurrency.CoroutineDispatchers
import com.github.jaideepaher.slatebrowser.concurrency.TabCoroutineScope
import com.github.jaideepaher.slatebrowser.di.FaviconCacheDir
import com.github.jaideepaher.slatebrowser.di.GeneratedHtmlDir
import com.github.jaideepaher.slatebrowser.pool.ObjectPool
import android.webkit.WebView
import androidx.webkit.WebViewAssetLoader.InternalStoragePathHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Constructs a [TabModel].
 */
class TabFactory @Inject constructor(
    private val webViewFactory: WebViewFactory,
    private val tabWebViewClientFactory: TabWebViewClient.Factory,
    private val tabAdapterFactory: TabAdapter.Factory,
    @FaviconCacheDir private val faviconStorageHandler: Deferred<@JvmSuppressWildcards InternalStoragePathHandler>,
    @GeneratedHtmlDir private val htmlStorageHandler: Deferred<@JvmSuppressWildcards InternalStoragePathHandler>,
    private val coroutineDispatchers: CoroutineDispatchers,
) {

    /**
     * Constructs a tab from the [webViewFactory] with the provided [tabInitializer].
     */
    suspend fun constructTab(
        id: Int,
        tabInitializer: TabInitializer,
        webViewPool: ObjectPool<WebView>,
        tabType: TabModel.Type,
        tabSettings: TabSettings,
        foreground: Boolean,
    ): TabModel = withContext(coroutineDispatchers.main) {
        val headers = webViewFactory.createRequestHeaders()
        val tabCoroutineScope = TabCoroutineScope(
            CoroutineScope(coroutineDispatchers.main + SupervisorJob())
        )
        tabAdapterFactory.create(
            id = id,
            tabInitializer = tabInitializer,
            webViewPool = webViewPool,
            requestHeaders = headers,
            tabWebViewClient = tabWebViewClientFactory.create(
                headers = headers,
                cacheStoragePathHandler = faviconStorageHandler.await(),
                filesStoragePathHandler = htmlStorageHandler.await(),
                tabCoroutineScope = tabCoroutineScope,
                tabSettings = tabSettings,
            ),
            tabType = tabType,
            tabCoroutineScope = tabCoroutineScope,
            priority = if (foreground) {
                TabAdapter.Priority.HIGH
            } else {
                TabAdapter.Priority.LOW
            }
        )
    }
}
