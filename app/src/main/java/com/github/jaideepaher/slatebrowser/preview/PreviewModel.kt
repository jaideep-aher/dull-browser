package com.github.jaideepaher.slatebrowser.preview

import com.github.jaideepaher.slatebrowser.concurrency.AppCoroutineScope
import com.github.jaideepaher.slatebrowser.concurrency.CoroutineDispatchers
import com.github.jaideepaher.slatebrowser.di.BrowserScope
import com.github.jaideepaher.slatebrowser.di.PreviewCacheDir
import com.github.jaideepaher.slatebrowser.extensions.safeUse
import com.github.jaideepaher.slatebrowser.ids.ViewIdGenerator
import com.github.jaideepaher.slatebrowser.log.Logger
import com.github.jaideepaher.slatebrowser.utils.ThreadSafeFileProvider
import android.graphics.Bitmap
import androidx.annotation.WorkerThread
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

/**
 * Reactive model that can store and retrieve previews from a disk cache.
 */
@BrowserScope
class PreviewModel @Inject constructor(
    private val logger: Logger,
    private val viewIdGenerator: ViewIdGenerator,
    @PreviewCacheDir private val previewCacheDirThreadSafeFileProvider: ThreadSafeFileProvider,
    private val coroutineDispatchers: CoroutineDispatchers,
    private val appCoroutineScope: AppCoroutineScope,
) {

    private val eventSharedFlow = MutableSharedFlow<Event>()

    init {
        appCoroutineScope.launch(coroutineDispatchers.io) {
            eventSharedFlow.collectLatest {
                when (it) {
                    is Event.Prune -> pruneInternal(viewIdGenerator.takenIds)
                }
            }
        }
    }

    /**
     * Retrieves the preview for an ID.
     */
    suspend fun previewForId(id: Int): String = withContext(coroutineDispatchers.io) {
        val cacheFolder = cacheDir()
        File(cacheFolder, "$id.png").path
    }

    /**
     * Caches a preview for a specific ID.
     *
     * @return an observable that notifies the consumer when it is complete.
     */
    suspend fun cachePreviewForId(
        id: Int,
        preview: Bitmap
    ): Unit = withContext(coroutineDispatchers.io) {
        val cacheFolder = cacheDir()
        logger.log(TAG, "Caching preview for tab: $id")
        FileOutputStream(getPreviewCacheFile(cacheFolder, id)).safeUse {
            preview.compress(Bitmap.CompressFormat.PNG, 100, it)
            preview.recycle()
            it.flush()
        }
    }

    /**
     * Prune the cache, releasing unused previews.
     */
    fun prune() {
        appCoroutineScope.launch {
            eventSharedFlow.emit(Event.Prune)
        }
    }

    private suspend fun pruneInternal(
        keepIds: Set<Int>
    ): Unit = withContext(coroutineDispatchers.io) {
        val cacheFolder = cacheDir()
        cacheFolder.listFiles()
            ?.filter { !keepIds.contains(it.name.split(".")[0].toInt()) }
            ?.forEach(File::delete)
    }

    private suspend fun cacheDir(): File = previewCacheDirThreadSafeFileProvider.file()

    /**
     * Creates the cache file for the preview image. File name will be in the form of "hash of URI host".png
     *
     * @param id The ID of the tab for which a preview will be cached.
     * @return a valid cache file.
     */
    @WorkerThread
    private fun getPreviewCacheFile(previewCache: File, id: Int): File {
        previewCache.mkdirs()
        return File(previewCache, "$id.png")
    }

    companion object {
        private const val TAG = "FaviconModel"
    }

    private sealed class Event {
        data object Prune : Event()
    }

}
