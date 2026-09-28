package com.github.jaideepaher.slatebrowser.adblock.siteblock

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class NavigationHopsTest {

    @Test
    fun `amp link exposes the embedded host`() {
        val hops = NavigationHops.extract(
            "https://www.google.com/amp/s/www.youtube.com/watch?v=abc"
        )
        assertThat(hops).anyMatch { it.startsWith("https://www.youtube.com/") }
    }

    @Test
    fun `amp cdn link exposes the embedded host`() {
        val hops = NavigationHops.extract(
            "https://www-youtube-com.cdn.ampproject.org/v/s/www.youtube.com/watch?v=abc"
        )
        assertThat(hops).anyMatch { it.startsWith("https://www.youtube.com/") }
    }

    @Test
    fun `google share wrapper exposes the target`() {
        val hops = NavigationHops.extract(
            "https://www.google.com/url?q=https%3A%2F%2Fwww.youtube.com%2Fwatch%3Fv%3D1&sa=D"
        )
        assertThat(hops).anyMatch { it.startsWith("https://www.youtube.com/watch") }
    }

    @Test
    fun `facebook share wrapper exposes the target`() {
        val hops = NavigationHops.extract(
            "https://l.facebook.com/l.php?u=https%3A%2F%2Finstagram.com%2Fp%2F1"
        )
        assertThat(hops).anyMatch { it.startsWith("https://instagram.com/") }
    }

    @Test
    fun `intent handoff exposes the host and fallback`() {
        val hops = NavigationHops.extract(
            "intent://www.youtube.com/watch?v=1#Intent;scheme=https;package=com.google.android.youtube;S.browser_fallback_url=https%3A%2F%2Fwww.youtube.com%2Fwatch%3Fv%3D1;end"
        )
        assertThat(hops).anyMatch { it.startsWith("https://www.youtube.com/watch") }
    }

    @Test
    fun `google result redirect exposes the target`() {
        val hops = NavigationHops.extract(
            "https://www.google.com/url?sa=t&source=web&rct=j&url=https%3A%2F%2Fwww.bbc.com%2Fnews&ved=1"
        )
        assertThat(hops).anyMatch { it.startsWith("https://www.bbc.com/news") }
    }

    @Test
    fun `google ad redirect exposes the target`() {
        val hops = NavigationHops.extract(
            "https://www.google.com/aclk?sa=l&adurl=https%3A%2F%2Fwww.bbc.com%2F"
        )
        assertThat(hops).anyMatch { it.startsWith("https://www.bbc.com/") }
    }

    @Test
    fun `ordinary search query is left alone`() {
        val hops = NavigationHops.extract("https://kagi.com/search?q=https%3A%2F%2Fyoutube.com")
        assertThat(hops).containsExactly("https://kagi.com/search?q=https%3A%2F%2Fyoutube.com")
    }
}
