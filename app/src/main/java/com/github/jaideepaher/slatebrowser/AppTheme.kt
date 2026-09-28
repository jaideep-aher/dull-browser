package com.github.jaideepaher.slatebrowser

import com.github.jaideepaher.slatebrowser.preference.IntEnum

/**
 * The available app themes.
 */
enum class AppTheme(override val value: Int) : IntEnum {
    LIGHT(0),
    DARK(1),
    SYSTEM(3)
}
