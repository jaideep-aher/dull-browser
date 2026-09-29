package com.github.jaideepaher.slatebrowser

import com.github.jaideepaher.slatebrowser.concurrency.AppCoroutineScope
import com.github.jaideepaher.slatebrowser.database.bookmark.BookmarkExporter
import com.github.jaideepaher.slatebrowser.database.bookmark.BookmarkRepository
import com.github.jaideepaher.slatebrowser.device.BuildInfo
import com.github.jaideepaher.slatebrowser.device.BuildType
import com.github.jaideepaher.slatebrowser.di.AppComponent
import com.github.jaideepaher.slatebrowser.di.DaggerAppComponent
import com.github.jaideepaher.slatebrowser.di.injector
import com.github.jaideepaher.slatebrowser.focus.FocusCoordinator
import com.github.jaideepaher.slatebrowser.migration.Cleanup
import com.github.jaideepaher.slatebrowser.utils.FileUtils
import com.github.jaideepaher.slatebrowser.utils.LeakCanaryUtils
import android.app.Application
import android.os.StrictMode
import android.webkit.WebView
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import kotlin.system.exitProcess

/**
 * The browser application.
 */
class BrowserApp : Application() {

    @Inject
    internal lateinit var leakCanaryUtils: LeakCanaryUtils

    @Inject
    internal lateinit var bookmarkModel: BookmarkRepository

    @Inject
    internal lateinit var buildInfo: BuildInfo

    @Inject
    internal lateinit var cleanup: Cleanup

    @Inject
    internal lateinit var appCoroutineScope: AppCoroutineScope

    @Inject
    internal lateinit var bookmarkExporter: BookmarkExporter

    @Inject
    internal lateinit var focusCoordinator: FocusCoordinator

    lateinit var applicationComponent: AppComponent

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .build()
            )
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .build()
            )
        }

        val isIncognito = getProcessName() == "$packageName:incognito"

        if (isIncognito) {
            File(dataDir, "app_webview_incognito").deleteRecursively()
            WebView.setDataDirectorySuffix("incognito")
        }

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, ex ->
            if (BuildConfig.DEBUG) {
                FileUtils.writeCrashToStorage(ex)
            }

            if (defaultHandler != null) {
                defaultHandler.uncaughtException(thread, ex)
            } else {
                exitProcess(2)
            }
        }

        applicationComponent = DaggerAppComponent.builder()
            .application(this)
            .buildInfo(createBuildInfo())
            .incognitoMode(isIncognito)
            .build()
        injector.inject(this)
        focusCoordinator.becomeActive()

        appCoroutineScope.launch {
            cleanup.cleanup()
        }

        appCoroutineScope.launch {
            if (bookmarkModel.count() == 0L) {
                val assetsBookmarks = bookmarkExporter.importBookmarksFromAssets()
                bookmarkModel.addBookmarkList(assetsBookmarks)
            }
        }

        if (buildInfo.buildType == BuildType.DEBUG) {
            leakCanaryUtils.setup()
        }

        if (buildInfo.buildType == BuildType.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true)
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        appCoroutineScope.cancel()
    }

    /**
     * Create the [BuildType] from the [BuildConfig].
     */
    private fun createBuildInfo() = BuildInfo(
        buildType = when {
            BuildConfig.DEBUG -> BuildType.DEBUG
            else -> BuildType.RELEASE
        },
        versionCode = BuildConfig.VERSION_CODE
    )

    companion object {
        private const val TAG = "BrowserApp"
    }
}
