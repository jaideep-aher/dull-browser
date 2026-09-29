package com.github.jaideepaher.slatebrowser.focus

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wires the focus stores together and applies work that should run when Dull opens.
 */
@Singleton
class FocusCoordinator @Inject constructor(
    val pauseList: PauseList,
    val stats: Stats,
    val bookmarks: Bookmarks,
    val readLater: ReadLater,
    val countdowns: Countdowns,
    val customBlocklist: CustomBlocklist,
    val store: KeyValueStore,
) {

    init {
        pauseList.onLoosened = { stats.recordLoosening() }
        pauseList.applyDueRemovals()
        stats.recordUse()
    }

    var blockedNote: String
        get() = store.string(BLOCKED_NOTE_KEY).orEmpty()
        set(value) = store.putString(BLOCKED_NOTE_KEY, value)

    fun becomeActive() {
        pauseList.applyDueRemovals()
        stats.recordUse()
    }

    companion object {
        const val BLOCKED_NOTE_KEY = "blockedPageNote"
    }
}
