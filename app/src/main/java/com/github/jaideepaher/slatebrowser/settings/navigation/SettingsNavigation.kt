package com.github.jaideepaher.slatebrowser.settings.navigation

/**
 * Navigation destinations.
 */
enum class SettingsNavigation(val parent: SettingsNavigation?) {
    ROOT(null),
    ADBLOCK(ROOT),
    GENERAL(ROOT),
    BOOKMARK(ROOT),
    DISPLAY(ROOT),
    PRIVACY(ROOT),
    ADVANCED(ROOT),
    ABOUT(ROOT),
    LICENSES(ABOUT),
    DEBUG(ROOT),
    PAUSE(ROOT),
    ADDED_SITES(ROOT),
    COUNTDOWNS(ROOT),
    STATS(ROOT),
    READ_LATER(ROOT),
    PASSWORDS(ROOT),
}
