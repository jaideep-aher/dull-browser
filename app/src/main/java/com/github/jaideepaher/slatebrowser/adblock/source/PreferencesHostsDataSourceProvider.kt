package com.github.jaideepaher.slatebrowser.adblock.source

import com.github.jaideepaher.slatebrowser.preference.UserPreferencesDataStore
import dagger.Reusable
import javax.inject.Inject

/**
 * A [HostsDataSourceProvider] backed by [UserPreferencesDataStore].
 */
@Reusable
class PreferencesHostsDataSourceProvider @Inject constructor(
    private val userPreferencesDataStore: UserPreferencesDataStore,
    private val assetsHostsDataSource: AssetsHostsDataSource,
    private val fileHostsDataSourceFactory: FileHostsDataSource.Factory,
    private val urlHostsDataSourceFactory: UrlHostsDataSource.Factory
) : HostsDataSourceProvider {

    override suspend fun createHostsDataSource(): HostsDataSource =
        when (val source = userPreferencesDataStore.selectedHostsSource()) {
            HostsSourceType.Default -> assetsHostsDataSource
            is HostsSourceType.Local -> fileHostsDataSourceFactory.create(source.file)
            is HostsSourceType.Remote -> urlHostsDataSourceFactory.create(source.httpUrl)
        }
}
