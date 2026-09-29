package com.github.jaideepaher.slatebrowser.focus

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class SiteAddressTest {

    @Test
    fun `typed sites are reduced to their domain`() {
        assertThat(SiteAddress.normalize("example.com")).isEqualTo("example.com")
        assertThat(SiteAddress.normalize("  HTTPS://WWW.Example.COM/some/path?q=1 ")).isEqualTo("example.com")
        assertThat(SiteAddress.normalize("news.example.co.uk.")).isEqualTo("news.example.co.uk")
        assertThat(SiteAddress.normalize("http://shop.example.org:8080")).isEqualTo("shop.example.org")
        assertThat(SiteAddress.normalize("münchen.de")).isEqualTo("xn--mnchen-3ya.de")
    }

    @Test
    fun `anything that is not a domain is rejected`() {
        for (input in listOf(
            "", "   ", "youtube", "com", "two words.com", "192.168.1.1", "localhost",
            "-bad.com", "bad-.com", "exa_mple.com", "example.c0m", "example..com",
            "javascript:alert(1)", "a".repeat(64) + ".com",
        )) {
            assertThat(SiteAddress.normalize(input)).`as`(input).isNull()
        }
    }

    @Test
    fun `friendly names fall back to the domain`() {
        assertThat(SiteName.display("youtube.com")).isEqualTo("YouTube")
        assertThat(SiteName.display("www.instagram.com")).isEqualTo("Instagram")
        assertThat(SiteName.display("example.org")).isEqualTo("example.org")
    }
}
