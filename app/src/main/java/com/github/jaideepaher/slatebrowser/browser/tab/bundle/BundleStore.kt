package com.github.jaideepaher.slatebrowser.browser.tab.bundle

import com.github.jaideepaher.slatebrowser.browser.tab.FreezableInitializer
import com.github.jaideepaher.slatebrowser.browser.tab.TabModel

/**
 * Used to save tab data for future restoration when the browser goes into hibernation.
 */
interface BundleStore {

    /**
     * Save the tab data for the list of [tabs].
     */
    suspend fun save(tabs: List<TabModel>)

    /**
     * Synchronously previously stored tab data.
     */
    suspend fun retrieve(): List<FreezableInitializer>

    /**
     * Synchronously delete all stored tabs.
     */
    suspend fun deleteAll()
}
