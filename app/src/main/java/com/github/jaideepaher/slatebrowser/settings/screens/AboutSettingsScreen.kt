package com.github.jaideepaher.slatebrowser.settings.screens

import com.github.jaideepaher.slatebrowser.BuildConfig
import com.github.jaideepaher.slatebrowser.R
import com.github.jaideepaher.slatebrowser.resources.ResourceProvider
import com.github.jaideepaher.slatebrowser.settings.framework.ClickableOnClick
import com.github.jaideepaher.slatebrowser.settings.framework.ClickableState
import com.github.jaideepaher.slatebrowser.settings.framework.SettingsFrameworkState
import com.github.jaideepaher.slatebrowser.settings.navigation.SettingsNavigation
import javax.inject.Inject

class AboutSettingsScreen @Inject constructor(
    private val resourceProvider: ResourceProvider,
) {
    fun createSettingsFrameworkState(): SettingsFrameworkState = SettingsFrameworkState(
        title = resourceProvider.stringResource(R.string.settings_about),
        content = listOf(
            ClickableState(
                title = resourceProvider.stringResource(R.string.version),
                summary = { BuildConfig.VERSION_NAME },
                onClick = ClickableOnClick.Action {}
            ),
            ClickableState(
                title = resourceProvider.stringResource(R.string.licenses),
                onClick = ClickableOnClick.Navigate(SettingsNavigation.LICENSES)
            ),
            ClickableState(
                title = resourceProvider.stringResource(R.string.app_name),
                summary = { resourceProvider.stringResource(R.string.mpl_license) },
                onClick = ClickableOnClick.WebLink("http://www.mozilla.org/MPL/2.0/")
            ),
            ClickableState(
                title = resourceProvider.stringResource(R.string.android_open_source_project),
                summary = { resourceProvider.stringResource(R.string.apache) },
                onClick = ClickableOnClick.WebLink("http://www.apache.org/licenses/LICENSE-2.0")
            ),
            ClickableState(
                title = resourceProvider.stringResource(R.string.hphosts_ad_server_list),
                summary = { resourceProvider.stringResource(R.string.freeware) },
                onClick = ClickableOnClick.WebLink("http://hosts-file.net/")
            ),
        )
    )
}
