package com.github.jaideepaher.slatebrowser.browser.tab.settings

import com.github.jaideepaher.slatebrowser.preference.IntEnum

/**
 * The text sizes supported by the browser.
 */
enum class TextSize(override val value: Int) : IntEnum {
    X_SMALL(5),
    SMALL(4),
    MEDIUM(3),
    LARGE(2),
    X_LARGE(1),
    XX_LARGE(0)
}
