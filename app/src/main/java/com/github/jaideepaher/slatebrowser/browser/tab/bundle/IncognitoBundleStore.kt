package com.github.jaideepaher.slatebrowser.browser.tab.bundle

import com.github.jaideepaher.slatebrowser.browser.tab.FreezableInitializer
import com.github.jaideepaher.slatebrowser.browser.tab.TabModel

/**
 * A bundle store implementation that no-ops for incognito mode.
 */
object IncognitoBundleStore : BundleStore {
    override suspend fun save(tabs: List<TabModel>) = Unit

    override suspend fun retrieve(): List<FreezableInitializer> = emptyList()

    override suspend fun deleteAll() = Unit
}
