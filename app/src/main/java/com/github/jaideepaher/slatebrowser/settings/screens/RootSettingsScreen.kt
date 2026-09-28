package com.github.jaideepaher.slatebrowser.settings.screens

import com.github.jaideepaher.slatebrowser.R
import com.github.jaideepaher.slatebrowser.device.BuildInfo
import com.github.jaideepaher.slatebrowser.device.BuildType
import com.github.jaideepaher.slatebrowser.resources.ResourceProvider
import com.github.jaideepaher.slatebrowser.settings.framework.ClickableOnClick
import com.github.jaideepaher.slatebrowser.settings.framework.ClickableState
import com.github.jaideepaher.slatebrowser.settings.framework.SettingsFrameworkState
import com.github.jaideepaher.slatebrowser.settings.navigation.SettingsNavigation
import javax.inject.Inject

class RootSettingsScreen @Inject constructor(
    private val resourceProvider: ResourceProvider,
    private val buildInfo: BuildInfo,
) {
    fun createSettingsFrameworkState(): SettingsFrameworkState = SettingsFrameworkState(
        title = resourceProvider.stringResource(R.string.settings),
        content = listOf(
            ClickableState(
                title = resourceProvider.stringResource(R.string.settings_adblock),
                onClick = ClickableOnClick.Navigate(SettingsNavigation.ADBLOCK),
            ),
            ClickableState(
                title = resourceProvider.stringResource(R.string.settings_general),
                onClick = ClickableOnClick.Navigate(SettingsNavigation.GENERAL),
            ),
            ClickableState(
                title = resourceProvider.stringResource(R.string.bookmark_settings),
                onClick = ClickableOnClick.Navigate(SettingsNavigation.BOOKMARK),
            ),
            ClickableState(
                title = resourceProvider.stringResource(R.string.settings_display),
                onClick = ClickableOnClick.Navigate(SettingsNavigation.DISPLAY),
            ),
            ClickableState(
                title = resourceProvider.stringResource(R.string.settings_privacy),
                onClick = ClickableOnClick.Navigate(SettingsNavigation.PRIVACY),
            ),
            ClickableState(
                title = resourceProvider.stringResource(R.string.settings_advanced),
                onClick = ClickableOnClick.Navigate(SettingsNavigation.ADVANCED),
            ),
            ClickableState(
                title = resourceProvider.stringResource(R.string.settings_about),
                summary = { resourceProvider.stringResource(R.string.settings_about_explain) },
                onClick = ClickableOnClick.Navigate(SettingsNavigation.ABOUT),
            ),
        ) + if (buildInfo.buildType == BuildType.DEBUG) {
            listOf(
                ClickableState(
                    title = resourceProvider.stringResource(R.string.debug_title),
                    onClick = ClickableOnClick.Navigate(SettingsNavigation.DEBUG),
                )
            )
        } else {
            emptyList()
        }
    )
}
