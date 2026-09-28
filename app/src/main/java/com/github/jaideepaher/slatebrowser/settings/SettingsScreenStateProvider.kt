package com.github.jaideepaher.slatebrowser.settings

import com.github.jaideepaher.slatebrowser.settings.framework.SettingsFrameworkState
import com.github.jaideepaher.slatebrowser.settings.navigation.SettingsNavigation
import com.github.jaideepaher.slatebrowser.settings.screens.AboutSettingsScreen
import com.github.jaideepaher.slatebrowser.settings.screens.AdBlockSettingsScreen
import com.github.jaideepaher.slatebrowser.settings.screens.AdvancedSettingsScreen
import com.github.jaideepaher.slatebrowser.settings.screens.BookmarkSettingsScreen
import com.github.jaideepaher.slatebrowser.settings.screens.DebugSettingsScreen
import com.github.jaideepaher.slatebrowser.settings.screens.DisplaySettingsScreen
import com.github.jaideepaher.slatebrowser.settings.screens.GeneralSettingsScreen
import com.github.jaideepaher.slatebrowser.settings.screens.PrivacySettingsScreen
import com.github.jaideepaher.slatebrowser.settings.screens.RootSettingsScreen
import javax.inject.Inject

class SettingsScreenStateProvider @Inject constructor(
    private val rootSettingsScreen: RootSettingsScreen,
    private val aboutSettingsScreen: AboutSettingsScreen,
    private val adBlockSettingsScreen: AdBlockSettingsScreen,
    private val advancedSettingsScreen: AdvancedSettingsScreen,
    private val bookmarkSettingsScreen: BookmarkSettingsScreen,
    private val debugSettingsScreen: DebugSettingsScreen,
    private val displaySettingsScreen: DisplaySettingsScreen,
    private val generalSettingsScreen: GeneralSettingsScreen,
    private val privacySettingsScreen: PrivacySettingsScreen,
) {

    fun provideState(
        settingsNavigation: SettingsNavigation
    ): SettingsFrameworkState = when (settingsNavigation) {
        SettingsNavigation.ROOT -> rootSettingsScreen.createSettingsFrameworkState()
        SettingsNavigation.ADBLOCK -> adBlockSettingsScreen.createSettingsFrameworkState()
        SettingsNavigation.GENERAL -> generalSettingsScreen.createSettingsFrameworkState()
        SettingsNavigation.BOOKMARK -> bookmarkSettingsScreen.createSettingsFrameworkState()
        SettingsNavigation.DISPLAY -> displaySettingsScreen.createSettingsFrameworkState()
        SettingsNavigation.PRIVACY -> privacySettingsScreen.createSettingsFrameworkState()
        SettingsNavigation.ADVANCED -> advancedSettingsScreen.createSettingsFrameworkState()
        SettingsNavigation.ABOUT -> aboutSettingsScreen.createSettingsFrameworkState()
        SettingsNavigation.LICENSES -> error("Unsupported")
        SettingsNavigation.DEBUG -> debugSettingsScreen.createSettingsFrameworkState()
    }
}
