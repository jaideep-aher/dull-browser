package com.github.jaideepaher.slatebrowser.focus

/**
 * Everything that could one day sit behind a paid plan. All of it is unlocked today.
 *
 * A lock may only hide a way to add strictness or comfort. Rules the person already chose, such as
 * sites they added to the block list or a pause they turned on, keep applying when locked.
 */
enum class Feature {
    MINDFUL_PAUSE,
    CUSTOM_BLOCKLIST,
    COUNTDOWNS,
    STATS,
    WEEKLY_SHARE_CARD,
    MILESTONES,
    BLOCKED_PAGE_NOTE,
    READ_LATER,
    READING_WINDOW,
    BOOKMARKS,
    TAB_THUMBNAILS;

    val isUnlocked: Boolean
        get() = Entitlements.current.unlocks(this)
}

/**
 * The features the current install may use. Nothing is locked until a paid plan exists.
 */
data class Entitlements(val locked: Set<Feature> = emptySet()) {

    fun unlocks(feature: Feature): Boolean = feature !in locked

    companion object {
        @Volatile
        var current: Entitlements = Entitlements()
    }
}
