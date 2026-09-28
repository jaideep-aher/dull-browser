package com.github.jaideepaher.slatebrowser.search

import com.github.jaideepaher.slatebrowser.preference.IntEnum

/**
 * The options available for performing searches with the search box.
 *
 * Values are persisted, so removed engines leave gaps rather than being renumbered.
 */
enum class SearchEngineChoice(override val value: Int) : IntEnum {
    GOOGLE(1),
    DUCK(7),
    BING(3),
}
