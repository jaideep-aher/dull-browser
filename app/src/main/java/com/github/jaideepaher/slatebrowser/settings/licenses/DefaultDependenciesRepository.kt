package com.github.jaideepaher.slatebrowser.settings.licenses

import com.github.jaideepaher.slatebrowser.concurrency.CoroutineDispatchers
import android.content.res.AssetManager
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.serializer
import javax.inject.Inject

/**
 * Default implementation of [DependenciesRepository] backed by [AssetManager].
 */
class DefaultDependenciesRepository @Inject constructor(
    private val assetManager: AssetManager,
    private val coroutineDispatchers: CoroutineDispatchers
) : DependenciesRepository {

    private val serializer = Json.serializersModule.serializer<List<OpenSourceDependency>>()

    @OptIn(ExperimentalSerializationApi::class)
    override suspend fun readDependencies(): List<OpenSourceDependency> =
        withContext(coroutineDispatchers.io) {
            Json.decodeFromStream(serializer, assetManager.open("licensee/artifacts.json"))
        }
}
