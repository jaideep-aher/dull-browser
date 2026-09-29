package com.github.jaideepaher.slatebrowser.di

import com.github.jaideepaher.slatebrowser.adblock.allowlist.AllowListModel
import com.github.jaideepaher.slatebrowser.adblock.allowlist.SessionAllowListModel
import com.github.jaideepaher.slatebrowser.adblock.source.HostsDataSourceProvider
import com.github.jaideepaher.slatebrowser.adblock.source.PreferencesHostsDataSourceProvider
import com.github.jaideepaher.slatebrowser.database.adblock.HostsDatabase
import com.github.jaideepaher.slatebrowser.database.adblock.HostsRepository
import com.github.jaideepaher.slatebrowser.database.allowlist.AdBlockAllowListDatabase
import com.github.jaideepaher.slatebrowser.database.allowlist.AdBlockAllowListRepository
import com.github.jaideepaher.slatebrowser.database.bookmark.BookmarkDatabase
import com.github.jaideepaher.slatebrowser.database.bookmark.BookmarkRepository
import com.github.jaideepaher.slatebrowser.database.downloads.DownloadsDatabase
import com.github.jaideepaher.slatebrowser.database.downloads.DownloadsRepository
import com.github.jaideepaher.slatebrowser.database.history.HistoryDatabase
import com.github.jaideepaher.slatebrowser.database.history.HistoryRepository
import com.github.jaideepaher.slatebrowser.download.DelegatingFileDownloader
import com.github.jaideepaher.slatebrowser.download.FileDownloader
import com.github.jaideepaher.slatebrowser.resources.DefaultNumberFormatter
import com.github.jaideepaher.slatebrowser.resources.DefaultResourceProvider
import com.github.jaideepaher.slatebrowser.resources.NumberFormatter
import com.github.jaideepaher.slatebrowser.resources.ResourceProvider
import com.github.jaideepaher.slatebrowser.settings.adblock.DefaultHostsFileUpdater
import com.github.jaideepaher.slatebrowser.settings.adblock.HostsFileUpdater
import com.github.jaideepaher.slatebrowser.settings.licenses.DefaultDependenciesRepository
import com.github.jaideepaher.slatebrowser.settings.licenses.DependenciesRepository
import com.github.jaideepaher.slatebrowser.settings.navigation.DefaultSettingsNavigator
import com.github.jaideepaher.slatebrowser.settings.navigation.SettingsNavigator
import com.github.jaideepaher.slatebrowser.ssl.SessionSslWarningPreferences
import com.github.jaideepaher.slatebrowser.ssl.SslWarningPreferences
import com.github.jaideepaher.slatebrowser.focus.FocusClock
import com.github.jaideepaher.slatebrowser.focus.FocusDataStore
import com.github.jaideepaher.slatebrowser.focus.KeyValueStore
import com.github.jaideepaher.slatebrowser.focus.SystemFocusClock
import com.github.jaideepaher.slatebrowser.theme.DefaultThemeProvider
import com.github.jaideepaher.slatebrowser.theme.ThemeProvider
import com.github.jaideepaher.slatebrowser.useragent.DefaultUserAgentProvider
import com.github.jaideepaher.slatebrowser.useragent.UserAgentProvider
import dagger.Binds
import dagger.Module

/**
 * Dependency injection module used to bind implementations to interfaces.
 */
@Module
interface AppBindsModule {

    @Binds
    fun bindsBookmarkModel(bookmarkDatabase: BookmarkDatabase): BookmarkRepository

    @Binds
    fun bindsDownloadsModel(downloadsDatabase: DownloadsDatabase): DownloadsRepository

    @Binds
    fun bindsHistoryModel(historyDatabase: HistoryDatabase): HistoryRepository

    @Binds
    fun bindsAdBlockAllowListModel(adBlockAllowListDatabase: AdBlockAllowListDatabase): AdBlockAllowListRepository

    @Binds
    fun bindsAllowListModel(sessionAllowListModel: SessionAllowListModel): AllowListModel

    @Binds
    fun bindsSslWarningPreferences(sessionSslWarningPreferences: SessionSslWarningPreferences): SslWarningPreferences

    @Binds
    fun bindsHostsRepository(hostsDatabase: HostsDatabase): HostsRepository

    @Binds
    fun bindsHostsDataSourceProvider(preferencesHostsDataSourceProvider: PreferencesHostsDataSourceProvider): HostsDataSourceProvider

    @Binds
    fun bindsResourceProvider(defaultResourceProvider: DefaultResourceProvider): ResourceProvider

    @Binds
    fun bindsHostsFileUpdater(hostsFileUpdater: DefaultHostsFileUpdater): HostsFileUpdater

    @Binds
    fun bindsNumberFormatter(defaultNumberFormatter: DefaultNumberFormatter): NumberFormatter

    @Binds
    fun bindsThemeProvider(themeProvider: DefaultThemeProvider): ThemeProvider

    @Binds
    fun bindsFileDownloader(delegatingFileDownloader: DelegatingFileDownloader): FileDownloader

    @Binds
    fun bindsUserAgentProvider(defaultUserAgentProvider: DefaultUserAgentProvider): UserAgentProvider

    @Binds
    fun bindsSettingsNavigator(defaultSettingsNavigator: DefaultSettingsNavigator): SettingsNavigator

    @Binds
    fun bindsDependenciesRepository(defaultDependenciesRepository: DefaultDependenciesRepository): DependenciesRepository

    @Binds
    fun bindsKeyValueStore(focusDataStore: FocusDataStore): KeyValueStore

    @Binds
    fun bindsFocusClock(systemFocusClock: SystemFocusClock): FocusClock
}
