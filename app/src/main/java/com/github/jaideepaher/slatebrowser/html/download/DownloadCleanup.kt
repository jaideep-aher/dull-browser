package com.github.jaideepaher.slatebrowser.html.download

import com.github.jaideepaher.slatebrowser.concurrency.CoroutineDispatchers
import com.github.jaideepaher.slatebrowser.migration.Cleanup
import android.app.Application
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * Cleans up the old storage space for the download pages on versions 102 and under.
 */
class DownloadCleanup @Inject constructor(
    private val application: Application,
    private val coroutineDispatchers: CoroutineDispatchers,
) : Cleanup.Action {
    override val fixedInVersionCode: Int = 103

    override suspend fun execute() {
        withContext(coroutineDispatchers.io) {
            application.filesDir.listFiles()
                ?.filter { it.endsWith(DownloadPageFactory.FILENAME) }
                ?.forEach(File::delete)
        }
    }

}
