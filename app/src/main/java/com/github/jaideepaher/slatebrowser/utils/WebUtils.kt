package com.github.jaideepaher.slatebrowser.utils

import com.github.jaideepaher.slatebrowser.concurrency.CoroutineDispatchers
import com.github.jaideepaher.slatebrowser.database.history.HistoryRepository
import com.github.jaideepaher.slatebrowser.di.FaviconCacheDir
import com.github.jaideepaher.slatebrowser.di.GeneratedHtmlDir
import com.github.jaideepaher.slatebrowser.di.PreviewCacheDir
import com.github.jaideepaher.slatebrowser.focus.TabThumbnails
import android.app.Activity
import android.app.Application
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewDatabase
import kotlinx.coroutines.withContext
import javax.inject.Inject

class WebUtils @Inject constructor(
    private val application: Application,
    private val activity: Activity,
    private val coroutineDispatchers: CoroutineDispatchers,
    private val historyRepository: HistoryRepository,
    @FaviconCacheDir private val faviconCacheDirThreadSafeFileProvider: ThreadSafeFileProvider,
    @PreviewCacheDir private val previewCacheDirThreadSafeFileProvider: ThreadSafeFileProvider,
    @GeneratedHtmlDir private val generatedHtmlDirThreadSafeFileProvider: ThreadSafeFileProvider,
    private val tabThumbnails: TabThumbnails,
) {
    suspend fun clearCookies() = withContext(coroutineDispatchers.io) {
        CookieManager.getInstance().removeAllCookies(null)
    }

    suspend fun clearWebStorage() = withContext(coroutineDispatchers.io) {
        WebStorage.getInstance().deleteAllData()
    }

    suspend fun clearHistory() = withContext(coroutineDispatchers.io) {
        historyRepository.deleteHistory()
        val webViewDatabase = WebViewDatabase.getInstance(application)
        webViewDatabase.clearHttpAuthUsernamePassword()
        faviconCacheDirThreadSafeFileProvider.file().deleteRecursively()
        previewCacheDirThreadSafeFileProvider.file().deleteRecursively()
        generatedHtmlDirThreadSafeFileProvider.file().deleteRecursively()
        tabThumbnails.removeAll()
    }

    suspend fun clearCache() = withContext(coroutineDispatchers.io) {
        withContext(coroutineDispatchers.main) {
            val webView = WebView(activity)
            webView.clearCache(true)
            webView.destroy()
        }
        faviconCacheDirThreadSafeFileProvider.file().deleteRecursively()
        previewCacheDirThreadSafeFileProvider.file().deleteRecursively()
        tabThumbnails.removeAll()
    }
}
