package com.github.jaideepaher.slatebrowser.useragent

import com.github.jaideepaher.slatebrowser.preference.IntEnum

/**
 * Potential user-agent values.
 */
enum class UserAgentChoice(override val value: Int) : IntEnum {
    DEFAULT(1),
    DESKTOP(2),
    MOBILE(3),
    CUSTOM(4),
}
