package com.github.jaideepaher.slatebrowser.adblock.source

import com.github.jaideepaher.slatebrowser.preference.IntEnum

/**
 * The available hosts source options.
 */
enum class HostsSourcePreference(override val value: Int) : IntEnum {
    DEFAULT(0),
    LOCAL(1),
    REMOTE(2)
}
