package com.github.jaideepaher.slatebrowser.di

import com.github.jaideepaher.slatebrowser.AppTheme
import com.github.jaideepaher.slatebrowser.adblock.AdBlocker
import com.github.jaideepaher.slatebrowser.adblock.BloomFilterAdBlocker
import com.github.jaideepaher.slatebrowser.adblock.NoOpAdBlocker
import com.github.jaideepaher.slatebrowser.adblock.siteblock.SiteBlocker
import com.github.jaideepaher.slatebrowser.browser.ui.TabConfiguration
import com.github.jaideepaher.slatebrowser.concurrency.AppCoroutineScope
import com.github.jaideepaher.slatebrowser.concurrency.CoroutineDispatcherProvider
import com.github.jaideepaher.slatebrowser.concurrency.CoroutineDispatchers
import com.github.jaideepaher.slatebrowser.device.BuildInfo
import com.github.jaideepaher.slatebrowser.device.BuildType
import com.github.jaideepaher.slatebrowser.extensions.preferredLocale
import com.github.jaideepaher.slatebrowser.favicon.FaviconCleanup
import com.github.jaideepaher.slatebrowser.html.ListPageReader
import com.github.jaideepaher.slatebrowser.html.bookmark.BookmarkCleanup
import com.github.jaideepaher.slatebrowser.html.bookmark.BookmarkPageReader
import com.github.jaideepaher.slatebrowser.html.download.DownloadCleanup
import com.github.jaideepaher.slatebrowser.html.history.HistoryCleanup
import com.github.jaideepaher.slatebrowser.html.homepage.HomeCleanup
import com.github.jaideepaher.slatebrowser.html.homepage.HomePageReader
import com.github.jaideepaher.slatebrowser.js.InvertPage
import com.github.jaideepaher.slatebrowser.js.TextReflow
import com.github.jaideepaher.slatebrowser.js.ThemeColor
import com.github.jaideepaher.slatebrowser.log.AndroidLogger
import com.github.jaideepaher.slatebrowser.log.Logger
import com.github.jaideepaher.slatebrowser.log.NoOpLogger
import com.github.jaideepaher.slatebrowser.migration.Cleanup
import com.github.jaideepaher.slatebrowser.preference.UserPreferencesDataStore
import com.github.jaideepaher.slatebrowser.search.suggestions.RequestFactory
import com.github.jaideepaher.slatebrowser.theme.ThemeProvider
import com.github.jaideepaher.slatebrowser.utils.FileUtils
import com.github.jaideepaher.slatebrowser.utils.ThreadSafeFileProvider
import android.app.ActivityManager
import android.app.Application
import android.app.DownloadManager
import android.app.NotificationManager
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.ShortcutManager
import android.content.res.AssetManager
import android.net.ConnectivityManager
import android.net.TrafficStats
import androidx.core.content.getSystemService
import androidx.webkit.WebViewAssetLoader.InternalStoragePathHandler
import com.anthonycr.mezzanine.mezzanine
import dagger.Module
import dagger.Provides
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import java.io.File
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.Locale
import javax.inject.Named
import javax.inject.Provider
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

@Module
class AppModule {

    @Provides
    fun provideContext(application: Application): Context = application.applicationContext

    @Provides
    fun providesLocale(application: Application): Locale = application.preferredLocale

    @Provides
    fun providesAssetManager(application: Application): AssetManager = application.assets

    @Provides
    fun providesClipboardManager(application: Application) =
        application.getSystemService<ClipboardManager>()!!

    @Provides
    fun providesDownloadManager(application: Application) =
        application.getSystemService<DownloadManager>()!!

    @Provides
    fun providesConnectivityManager(application: Application) =
        application.getSystemService<ConnectivityManager>()!!

    @Provides
    fun providesNotificationManager(application: Application) =
        application.getSystemService<NotificationManager>()!!


    @Provides
    fun providesShortcutManager(application: Application) =
        application.getSystemService<ShortcutManager>()!!

    @Provides
    fun providesActivityManager(application: Application) =
        application.getSystemService<ActivityManager>()!!

    @Singleton
    @Provides
    fun providesSuggestionsCacheControl(): CacheControl =
        CacheControl.Builder().maxStale(1.days).build()

    @Singleton
    @Provides
    fun providesSuggestionsRequestFactory(cacheControl: CacheControl): RequestFactory =
        object : RequestFactory {
            override fun createSuggestionsRequest(httpUrl: HttpUrl, encoding: String): Request {
                return Request.Builder().url(httpUrl)
                    .addHeader("Accept-Charset", encoding)
                    .cacheControl(cacheControl)
                    .build()
            }
        }

    private fun createInterceptorWithMaxCacheAge(maxCacheAge: Duration) = Interceptor { chain ->
        chain.proceed(chain.request()).newBuilder()
            .header(
                name = "cache-control",
                value = "max-age=${maxCacheAge.inWholeSeconds}, max-stale=${maxCacheAge.inWholeSeconds}"
            )
            .build()
    }

    @Singleton
    @Provides
    fun providesTrafficStatsEventListener(): EventListener = object : EventListener() {
        // Credit to https://github.com/jaredsburrows/android-gif-search/blob/19ea35435e0962cd7d419a4ee02b05f5cebdb6e6/app/src/main/java/com/burrowsapps/gif/search/di/NetworkModule.kt#L90
        override fun connectStart(
            call: Call,
            inetSocketAddress: InetSocketAddress,
            proxy: Proxy
        ) {
            TrafficStats.setThreadStatsTag(0xACAB)
        }

        override fun connectEnd(
            call: Call,
            inetSocketAddress: InetSocketAddress,
            proxy: Proxy,
            protocol: Protocol?
        ) {
            TrafficStats.clearThreadStatsTag()
        }
    }

    @Singleton
    @Provides
    @NoCacheClient
    fun providesNoCacheHttpClient(
        appCoroutineScope: AppCoroutineScope,
        coroutineDispatchers: CoroutineDispatchers,
        eventListener: EventListener
    ): Deferred<OkHttpClient> = appCoroutineScope.async(coroutineDispatchers.io) {
        OkHttpClient.Builder()
            .eventListener(eventListener)
            .build()
    }

    @Singleton
    @Provides
    @SuggestionsClient
    fun providesSuggestionsCoroutineHttpClient(
        application: Application,
        appCoroutineScope: AppCoroutineScope,
        coroutineDispatchers: CoroutineDispatchers,
        eventListener: EventListener,
    ): Deferred<OkHttpClient> = appCoroutineScope.async(coroutineDispatchers.io) {
        val suggestionsCache = File(application.cacheDir, "suggestion_responses")

        OkHttpClient.Builder()
            .eventListener(eventListener)
            .cache(Cache(suggestionsCache, FileUtils.megabytesToBytes(1)))
            .addNetworkInterceptor(createInterceptorWithMaxCacheAge(1.days))
            .build()
    }

    @Singleton
    @Provides
    @HostsClient
    fun providesHostsHttpClient(
        application: Application,
        appCoroutineScope: AppCoroutineScope,
        coroutineDispatchers: CoroutineDispatchers,
        eventListener: EventListener,
    ): Deferred<OkHttpClient> = appCoroutineScope.async(coroutineDispatchers.io) {
        val suggestionsCache = File(application.cacheDir, "hosts_cache")

        OkHttpClient.Builder()
            .eventListener(eventListener)
            .cache(Cache(suggestionsCache, FileUtils.megabytesToBytes(5)))
            .addNetworkInterceptor(createInterceptorWithMaxCacheAge(365.days))
            .build()
    }

    @Provides
    @Singleton
    fun provideLogger(buildInfo: BuildInfo): Logger = if (buildInfo.buildType == BuildType.DEBUG) {
        AndroidLogger()
    } else {
        NoOpLogger()
    }

    @Provides
    fun providesListPageReader(): ListPageReader = mezzanine()

    @Provides
    fun providesHomePageReader(): HomePageReader = mezzanine()

    @Provides
    fun providesBookmarkPageReader(): BookmarkPageReader = mezzanine()

    @Provides
    fun providesTextReflow(): TextReflow = mezzanine()

    @Provides
    fun providesThemeColor(): ThemeColor = mezzanine()

    @Provides
    fun providesInvertPage(): InvertPage = mezzanine()

    @Provides
    fun providesCleanupList(
        faviconCleanup: FaviconCleanup,
        bookmarkCleanup: BookmarkCleanup,
        downloadCleanup: DownloadCleanup,
        historyCleanup: HistoryCleanup,
        homeCleanup: HomeCleanup
    ): List<@JvmSuppressWildcards Cleanup.Action> =
        listOf(faviconCleanup, bookmarkCleanup, downloadCleanup, historyCleanup, homeCleanup)

    @Singleton
    @FilesDir
    @Provides
    fun providesFilesDir(
        application: Application,
        threadSafeFileProviderFactory: ThreadSafeFileProvider.Factory
    ): ThreadSafeFileProvider = threadSafeFileProviderFactory.create {
        application.filesDir
    }

    @Singleton
    @DataDir
    @Provides
    fun providesDataDir(
        application: Application,
        threadSafeFileProviderFactory: ThreadSafeFileProvider.Factory
    ): ThreadSafeFileProvider = threadSafeFileProviderFactory.create {
        application.dataDir
    }

    @Singleton
    @FaviconCacheDir
    @Provides
    fun providesFaviconCacheDir(
        application: Application,
        threadSafeFileProviderFactory: ThreadSafeFileProvider.Factory,
        @IncognitoMode isIncognitoMode: Boolean,
    ): ThreadSafeFileProvider = threadSafeFileProviderFactory.create {
        val suffix = if (isIncognitoMode) {
            "-incognito"
        } else {
            ""
        }
        File(application.cacheDir, "favicon-cache$suffix").apply {
            mkdirs()
        }
    }

    @Singleton
    @PreviewCacheDir
    @Provides
    fun providesPreviewCacheDir(
        application: Application,
        threadSafeFileProviderFactory: ThreadSafeFileProvider.Factory,
        @IncognitoMode isIncognitoMode: Boolean,
    ): ThreadSafeFileProvider = threadSafeFileProviderFactory.create {
        val suffix = if (isIncognitoMode) {
            "-incognito"
        } else {
            ""
        }
        File(application.cacheDir, "preview-cache$suffix").apply {
            mkdirs()
        }
    }

    @Singleton
    @GeneratedHtmlDir
    @Provides
    fun providesGeneratedHtmlDir(
        application: Application,
        threadSafeFileProviderFactory: ThreadSafeFileProvider.Factory,
        @IncognitoMode isIncognitoMode: Boolean,
    ): ThreadSafeFileProvider = threadSafeFileProviderFactory.create {
        val suffix = if (isIncognitoMode) {
            "-incognito"
        } else {
            ""
        }
        File(application.filesDir, "generated-html$suffix").apply {
            mkdirs()
        }
    }

    @Singleton
    @FaviconCacheDir
    @Provides
    fun providesFaviconStorageHandler(
        application: Application,
        @FaviconCacheDir faviconCacheDirThreadSafeFileProvider: ThreadSafeFileProvider,
        appCoroutineScope: AppCoroutineScope,
        coroutineDispatchers: CoroutineDispatchers,
    ): Deferred<InternalStoragePathHandler> = appCoroutineScope.async(coroutineDispatchers.io) {
        InternalStoragePathHandler(
            application,
            faviconCacheDirThreadSafeFileProvider.file()
        )
    }

    @Singleton
    @GeneratedHtmlDir
    @Provides
    fun providesHtmlStorageHandler(
        application: Application,
        @GeneratedHtmlDir generatedHtmlDirThreadSafeFileProvider: ThreadSafeFileProvider,
        appCoroutineScope: AppCoroutineScope,
        coroutineDispatchers: CoroutineDispatchers,
    ): Deferred<InternalStoragePathHandler> = appCoroutineScope.async(coroutineDispatchers.io) {
        InternalStoragePathHandler(
            application,
            generatedHtmlDirThreadSafeFileProvider.file()
        )
    }

    @Singleton
    @OptIn(DelicateCoroutinesApi::class)
    @Provides
    fun providesAppCoroutineScope(coroutineDispatchers: CoroutineDispatchers): AppCoroutineScope =
        AppCoroutineScope(CoroutineScope(coroutineDispatchers.main + SupervisorJob()))

    @Singleton
    @Provides
    fun providesDispatchers(): CoroutineDispatchers = CoroutineDispatcherProvider(
        main = Dispatchers.Main,
        io = Dispatchers.IO,
        default = Dispatchers.Default
    )

    @Named("theme")
    @Singleton
    @Provides
    fun providesAppThemeStateFlow(
        themeProvider: ThemeProvider,
        appCoroutineScope: AppCoroutineScope,
    ): StateFlow<AppTheme?> = themeProvider.appThemeValues()
        .stateIn(appCoroutineScope, SharingStarted.Eagerly, null)

    @Named("tab")
    @Singleton
    @Provides
    fun providesTabConfigurationStateFlow(
        userPreferencesDataStore: UserPreferencesDataStore,
        appCoroutineScope: AppCoroutineScope,
    ): StateFlow<TabConfiguration?> = userPreferencesDataStore.tabConfiguration.values()
        .stateIn(appCoroutineScope, SharingStarted.Eagerly, null)

    @Named("black_status")
    @Singleton
    @Provides
    fun providesBlackStatusBarStateFlow(
        userPreferencesDataStore: UserPreferencesDataStore,
        appCoroutineScope: AppCoroutineScope
    ): StateFlow<Boolean?> = userPreferencesDataStore.useBlackStatusBar.values()
        .combine(userPreferencesDataStore.tabConfiguration.values()) { a, b ->
            a || b == TabConfiguration.DESKTOP
        }.stateIn(appCoroutineScope, SharingStarted.Eagerly, null)

    @Singleton
    @Provides
    fun providesAdBlocker(
        appCoroutineScope: AppCoroutineScope,
        userPreferencesDataStore: UserPreferencesDataStore,
        bloomFilterAdBlocker: Provider<BloomFilterAdBlocker>,
        noOpAdBlocker: NoOpAdBlocker
    ): Deferred<AdBlocker> = appCoroutineScope.async {
        if (userPreferencesDataStore.adBlockEnabled.get()) {
            bloomFilterAdBlocker.get()
        } else {
            noOpAdBlocker
        }
    }

    /**
     * Unlike the ad blocker this has no enabled/disabled preference: the site blocker is always
     * constructed. Reading the packaged list is disk work, so it is kept off the main dispatcher
     * that [AppCoroutineScope] otherwise runs on.
     */
    @Singleton
    @Provides
    fun providesSiteBlocker(
        appCoroutineScope: AppCoroutineScope,
        coroutineDispatchers: CoroutineDispatchers,
        siteBlocker: Provider<SiteBlocker>
    ): Deferred<SiteBlocker> = appCoroutineScope.async(coroutineDispatchers.io) {
        siteBlocker.get()
    }
}

@Qualifier
@Retention(AnnotationRetention.SOURCE)
annotation class NoCacheClient

@Qualifier
@Retention(AnnotationRetention.SOURCE)
annotation class SuggestionsClient

@Qualifier
@Retention(AnnotationRetention.SOURCE)
annotation class HostsClient

@Qualifier
@Retention(AnnotationRetention.SOURCE)
annotation class FilesDir

@Qualifier
@Retention(AnnotationRetention.SOURCE)
annotation class DataDir

@Qualifier
@Retention(AnnotationRetention.SOURCE)
annotation class FaviconCacheDir

@Qualifier
@Retention(AnnotationRetention.SOURCE)
annotation class PreviewCacheDir

@Qualifier
@Retention(AnnotationRetention.SOURCE)
annotation class GeneratedHtmlDir
