package com.github.jaideepaher.slatebrowser.focus

import android.app.Application
import android.graphics.Bitmap
import android.util.Log
import android.webkit.WebView
import java.io.File
import java.io.FileOutputStream
import java.util.LinkedHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Small page previews for the tab grid. A preview is taken only when a tab is left or the app
 * goes to the background, never while a page is on screen. Previews are browsing data: they
 * are removed with their tab and when browsing data is cleared.
 *
 * Capture is kept off the UI thread after the WebView snapshot. If starting a capture is costly
 * on a device, the tab switcher keeps the existing list UI.
 */
@Singleton
class TabThumbnails @Inject constructor(
    application: Application,
) {

    private val directory: File = File(application.cacheDir, "TabThumbnails").apply { mkdirs() }
    private val memory = object : LinkedHashMap<Int, Bitmap>(MEMORY_LIMIT + 1, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, Bitmap>?): Boolean =
            size > MEMORY_LIMIT
    }

    fun file(id: Int): File = File(directory, "$id.jpg")

    fun get(id: Int): Bitmap? = synchronized(memory) { memory[id] }

    fun loadIfNeeded(id: Int): Bitmap? {
        synchronized(memory) { memory[id] }?.let { return it }
        val data = file(id).takeIf { it.exists() }?.readBytes() ?: return null
        val image = android.graphics.BitmapFactory.decodeByteArray(data, 0, data.size) ?: return null
        synchronized(memory) { memory[id] = image }
        return image
    }

    /**
     * Capture a compact JPEG when a tab is left. Skips empty or special pages.
     */
    fun capture(view: WebView, id: Int, capturable: Boolean) {
        if (!Feature.TAB_THUMBNAILS.isUnlocked || !capturable) return
        val width = view.width
        val height = view.height
        if (width <= 1 || height <= 1) return
        val started = System.nanoTime()
        view.postVisualStateCallback(id.toLong(), object : WebView.VisualStateCallback() {
            override fun onComplete(requestId: Long) {
                val source = Bitmap.createBitmap(width, minOf(height, (width * ASPECT).toInt()), Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(source)
                view.draw(canvas)
                Thread {
                    val small = shrink(source)
                    source.recycle()
                    val dest = file(id)
                    runCatching {
                        FileOutputStream(dest).use {
                            small.compress(Bitmap.CompressFormat.JPEG, 60, it)
                        }
                    }
                    synchronized(memory) { memory[id] = small }
                    val elapsed = (System.nanoTime() - started) / 1_000_000.0
                    Log.d(TAG, "Tab preview ready in ${"%.1f".format(elapsed)} ms, ${dest.length()} bytes")
                }.start()
            }
        })
    }

    fun remove(id: Int) {
        synchronized(memory) { memory.remove(id) }
        file(id).delete()
    }

    fun removeAll(keeping: Set<Int> = emptySet()) {
        synchronized(memory) {
            memory.keys.filterNot { it in keeping }.forEach { memory.remove(it) }
        }
        directory.listFiles()?.forEach { file ->
            val id = file.name.removeSuffix(".jpg").toIntOrNull()
            if (id == null || id !in keeping) file.delete()
        }
    }

    private fun shrink(image: Bitmap): Bitmap {
        val height = (image.height * WIDTH / image.width.toFloat()).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(image, WIDTH, height, true)
    }

    companion object {
        private const val TAG = "TabThumbnails"
        const val WIDTH = 160
        const val ASPECT = 1.25
        const val MEMORY_LIMIT = 8
    }
}
