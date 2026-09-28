package com.github.jaideepaher.slatebrowser.shortcuts

import com.github.jaideepaher.slatebrowser.R
import com.github.jaideepaher.slatebrowser.resources.ResourceProvider
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import androidx.core.net.toUri
import androidx.fragment.app.FragmentActivity
import javax.inject.Inject

/**
 * Creates a shortcut using the [ShortcutManager] if supported by the launcher.
 */
class ShortcutGenerator @Inject constructor(
    private val activity: FragmentActivity,
    private val shortcutManager: ShortcutManager,
    private val resourceProvider: ResourceProvider,
) {

    /**
     * Create a shortcut with the provided [url], [unsafeTitle], and [unsafeFavicon]. Returns true
     * if the launcher supports creating a shortcut, false otherwise.
     */
    fun createShortcut(
        url: String,
        unsafeTitle: String,
        unsafeFavicon: Bitmap?
    ): Boolean {
        val shortcutIntent = Intent(Intent.ACTION_VIEW).apply {
            data = url.toUri()
        }

        val title = unsafeTitle.takeIf { it.isNotEmpty() }
            ?: resourceProvider.stringResource(R.string.untitled)

        if (shortcutManager.isRequestPinShortcutSupported) {
            val pinShortcutInfo = ShortcutInfo.Builder(
                activity,
                "browser-shortcut-${url.hashCode()}"
            ).setIntent(shortcutIntent)
                .apply {
                    if (unsafeFavicon != null) {
                        setIcon(Icon.createWithBitmap(unsafeFavicon))
                    } else {
                        setIcon(Icon.createWithResource(activity, R.drawable.ic_webpage))
                    }
                }
                .setShortLabel(title)
                .build()

            return shortcutManager.requestPinShortcut(pinShortcutInfo, null)
        } else {
            return false
        }
    }
}
