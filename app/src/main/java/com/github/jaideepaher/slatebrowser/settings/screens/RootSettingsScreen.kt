package com.github.jaideepaher.slatebrowser.settings.screens

import com.github.jaideepaher.slatebrowser.R
import com.github.jaideepaher.slatebrowser.adblock.siteblock.SiteBlocker
import com.github.jaideepaher.slatebrowser.device.BuildInfo
import com.github.jaideepaher.slatebrowser.device.BuildType
import com.github.jaideepaher.slatebrowser.resources.ResourceProvider
import com.github.jaideepaher.slatebrowser.settings.framework.ClickableOnClick
import com.github.jaideepaher.slatebrowser.settings.framework.ClickableState
import com.github.jaideepaher.slatebrowser.settings.framework.SettingsFrameworkState
import com.github.jaideepaher.slatebrowser.focus.Feature
import com.github.jaideepaher.slatebrowser.focus.FocusCoordinator
import com.github.jaideepaher.slatebrowser.settings.framework.SettingsBottomSheetInputState
import com.github.jaideepaher.slatebrowser.settings.navigation.SettingsNavigation
import kotlinx.coroutines.Deferred
import java.text.NumberFormat
import javax.inject.Inject

class RootSettingsScreen @Inject constructor(
    private val resourceProvider: ResourceProvider,
    private val buildInfo: BuildInfo,
    private val siteBlocker: Deferred<@JvmSuppressWildcards SiteBlocker>,
    private val focusCoordinator: FocusCoordinator,
) {
    fun createSettingsFrameworkState(): SettingsFrameworkState = SettingsFrameworkState(
        title = resourceProvider.stringResource(R.string.settings),
        content = listOf(
            ClickableState(
                title = resourceProvider.stringResource(R.string.site_blocking),
                summary = {
                    resourceProvider.stringResource(
                        R.string.site_blocking_summary,
                        NumberFormat.getIntegerInstance().format(siteBlocker.await().blockedDomainCount)
                    )
                },
                onClick = if (Feature.CUSTOM_BLOCKLIST.isUnlocked) {
                    ClickableOnClick.Navigate(SettingsNavigation.ADDED_SITES)
                } else {
                    ClickableOnClick.Action {}
                },
            ),
            ClickableState(
                title = resourceProvider.stringResource(R.string.settings_blocked_note),
                summary = { focusCoordinator.blockedNote.ifBlank { resourceProvider.stringResource(R.string.settings_blocked_note_hint) } },
                onClick = ClickableOnClick.Input(
                    produceState = {
                        SettingsBottomSheetInputState(
                            title = resourceProvider.stringResource(R.string.settings_blocked_note),
                            hint = resourceProvider.stringResource(R.string.settings_blocked_note_hint),
                            currentValue = focusCoordinator.blockedNote,
                        )
                    },
                    onValueUpdated = { value ->
                        ClickableOnClick.Action { focusCoordinator.blockedNote = value }
                    }
                )
            ),
            ClickableState(
                title = resourceProvider.stringResource(R.string.settings_pause),
                onClick = ClickableOnClick.Navigate(SettingsNavigation.PAUSE),
            ),
            ClickableState(
                title = resourceProvider.stringResource(R.string.settings_countdowns),
                onClick = ClickableOnClick.Navigate(SettingsNavigation.COUNTDOWNS),
            ),
            ClickableState(
                title = resourceProvider.stringResource(R.string.settings_stats),
                onClick = ClickableOnClick.Navigate(SettingsNavigation.STATS),
            ),
            ClickableState(
                title = resourceProvider.stringResource(R.string.settings_read_later),
                onClick = ClickableOnClick.Navigate(SettingsNavigation.READ_LATER),
            ),
            ClickableState(
                title = resourceProvider.stringResource(R.string.settings_passwords),
                summary = { resourceProvider.stringResource(R.string.settings_passwords_summary) },
                onClick = ClickableOnClick.Navigate(SettingsNavigation.PASSWORDS),
            ),
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
