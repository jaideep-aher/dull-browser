package com.github.jaideepaher.slatebrowser.browser

import com.github.jaideepaher.slatebrowser.IncognitoBrowserActivity
import com.github.jaideepaher.slatebrowser.browser.cleanup.ExitCleanup
import com.github.jaideepaher.slatebrowser.concurrency.AppCoroutineScope
import com.github.jaideepaher.slatebrowser.di.IncognitoMode
import com.github.jaideepaher.slatebrowser.download.FileDownloader
import com.github.jaideepaher.slatebrowser.download.PendingDownload
import com.github.jaideepaher.slatebrowser.extensions.copyToClipboard
import com.github.jaideepaher.slatebrowser.log.Logger
import com.github.jaideepaher.slatebrowser.settings.activity.SettingsActivity
import com.github.jaideepaher.slatebrowser.shortcuts.ShortcutGenerator
import com.github.jaideepaher.slatebrowser.utils.IntentUtils
import android.app.ActivityManager
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Bitmap
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The navigator implementation.
 */
class BrowserNavigator @Inject constructor(
    private val activity: FragmentActivity,
    private val clipboardManager: ClipboardManager,
    private val logger: Logger,
    private val exitCleanup: ExitCleanup,
    @IncognitoMode private val incognitoMode: Boolean,
    private val activityManager: ActivityManager,
    private val appCoroutineScope: AppCoroutineScope,
    private val fileDownloader: FileDownloader,
    private val intentUtils: IntentUtils,
    private val shortcutGenerator: ShortcutGenerator,
) : BrowserContract.Navigator {

    override fun openSettings() {
        activity.startActivity(Intent(activity, SettingsActivity::class.java))
    }

    override fun sharePage(url: String, title: String?) {
        intentUtils.shareUrl(url, title)
    }

    override fun copyPageLink(url: String) {
        clipboardManager.copyToClipboard(url)
    }

    override suspend fun closeBrowser() {
        exitCleanup.cleanUp()
        if (incognitoMode) {
            activityManager.appTasks
                .first { it.taskInfo?.topActivity?.className == IncognitoBrowserActivity::class.java.name }
                .finishAndRemoveTask()
        } else {
            activity.finish()
        }
    }

    override fun addToHomeScreen(url: String, title: String, favicon: Bitmap?): Boolean {
        logger.log(TAG, "Creating shortcut: $title $url")
        return shortcutGenerator.createShortcut(url, title, favicon)
    }

    override fun download(pendingDownload: PendingDownload) {
        appCoroutineScope.launch {
            fileDownloader.download(pendingDownload)
        }
    }

    override fun backgroundBrowser() {
        if (incognitoMode) {
            appCoroutineScope.launch {
                exitCleanup.cleanUp()
                activityManager.appTasks
                    .first { it.taskInfo?.topActivity?.className == IncognitoBrowserActivity::class.java.name }
                    .finishAndRemoveTask()
            }
        } else {
            activity.moveTaskToBack(true)
        }
    }

    override fun launchIncognito(url: String?) {
        IncognitoBrowserActivity.launch(activity, url)
    }

    companion object {
        private const val TAG = "BrowserNavigator"
    }

}
