package com.github.jaideepaher.slatebrowser.html.homepage

import com.github.jaideepaher.slatebrowser.preference.IntEnum

/**
 * How the new tab page shows the time.
 */
enum class NewTabClock(override val value: Int) : IntEnum {
    OFF(0),
    TWELVE_HOUR(1),
    TWENTY_FOUR_HOUR(2),
}
