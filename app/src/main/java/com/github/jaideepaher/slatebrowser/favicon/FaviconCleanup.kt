package com.github.jaideepaher.slatebrowser.favicon

import com.github.jaideepaher.slatebrowser.concurrency.CoroutineDispatchers
import com.github.jaideepaher.slatebrowser.migration.Cleanup
import android.app.Application
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * Cleans up the old storage space for cached favicons on versions 102 and under.
 */
class FaviconCleanup @Inject constructor(
    private val application: Application,
    private val coroutineDispatchers: CoroutineDispatchers,
) : Cleanup.Action {
    override val fixedInVersionCode: Int = 103

    override suspend fun execute(): Unit = withContext(coroutineDispatchers.io) {
        application.cacheDir.listFiles()
            ?.filter { it.extension == "png" }
            ?.forEach(File::delete)
    }
}
