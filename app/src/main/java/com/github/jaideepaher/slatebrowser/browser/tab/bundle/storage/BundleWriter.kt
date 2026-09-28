package com.github.jaideepaher.slatebrowser.browser.tab.bundle.storage

import com.github.jaideepaher.slatebrowser.concurrency.CoroutineDispatchers
import com.github.jaideepaher.slatebrowser.di.FilesDir
import com.github.jaideepaher.slatebrowser.log.Logger
import com.github.jaideepaher.slatebrowser.utils.ThreadSafeFileProvider
import android.os.Bundle
import android.os.Parcel
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException

/**
 * Reads and writes bundles to and from storage.
 */
class BundleWriter @AssistedInject constructor(
    private val coroutineDispatchers: CoroutineDispatchers,
    private val logger: Logger,
    @FilesDir private val filesDir: ThreadSafeFileProvider,
    @Assisted private val bundleFileName: String
) {

    @AssistedFactory
    interface Factory {
        fun create(bundleFileName: String): BundleWriter
    }

    /**
     * Writes a bundle to persistent storage in the files directory using the specified file name.
     *
     * @param bundle the bundle to store in persistent storage.
     */
    suspend fun writeToStorage(bundle: Bundle?) = withContext(coroutineDispatchers.io) {
        val outputFile = File(filesDir.file(), bundleFileName)
        try {
            FileOutputStream(outputFile).use { outputStream ->
                val parcel = Parcel.obtain()
                parcel.writeBundle(bundle)
                outputStream.write(parcel.marshall())
                outputStream.flush()
                parcel.recycle()
            }
        } catch (e: IOException) {
            logger.log(TAG, "Unable to write bundle to storage", e)
        }
    }

    /**
     * Use this method to delete the bundle with the specified name.
     */
    suspend fun deleteInStorage() = withContext(coroutineDispatchers.io) {
        val outputFile = File(filesDir.file(), bundleFileName)
        if (outputFile.exists()) {
            outputFile.delete()
        }
    }

    /**
     * Reads a bundle from the file with the specified name in the persistent storage files
     * directory.
     *
     * @return a valid Bundle loaded using the system class loader or null if the method was unable
     * to read the Bundle from storage.
     */
    suspend fun readFromStorage(): Bundle? = withContext(coroutineDispatchers.io) {
        val inputFile = File(filesDir.file(), bundleFileName)
        try {
            FileInputStream(inputFile).use { inputStream ->
                val parcel = Parcel.obtain()
                val data = ByteArray(inputStream.channel.size().toInt())

                inputStream.read(data, 0, data.size)
                parcel.unmarshall(data, 0, data.size)
                parcel.setDataPosition(0)
                val out = parcel.readBundle(ClassLoader.getSystemClassLoader())
                out!!.putAll(out)
                parcel.recycle()
                return@withContext out
            }
        } catch (e: FileNotFoundException) {
            logger.log(TAG, "Unable to read bundle from storage", e)
        } catch (e: IOException) {
            logger.log(TAG, "Unable to read bundle from storage", e)
        }
        return@withContext null
    }

    private companion object {
        private const val TAG = "BundleWriter"
    }
}
