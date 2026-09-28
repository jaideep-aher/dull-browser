package com.github.jaideepaher.slatebrowser.search

import com.github.jaideepaher.slatebrowser.preference.IntEnum

/**
 * The suggestion choices.
 */
enum class Suggestions(override val value: Int) : IntEnum {
    NONE(0),
    GOOGLE(1),
    DUCK(2),
}
