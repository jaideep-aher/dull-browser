package com.github.jaideepaher.slatebrowser.adblock.siteblock

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

/**
 * Tests for [DomainMatcher].
 */
class DomainMatcherTest {

    private val matcher = DomainMatcher(setOf("youtube.com", "instagram.com", "bbc.co.uk"))

    @Test
    fun `exact domain is blocked`() {
        assertThat(matcher.matches("youtube.com")).isTrue()
    }

    @Test
    fun `subdomains are blocked`() {
        assertThat(matcher.matches("m.youtube.com")).isTrue()
        assertThat(matcher.matches("music.youtube.com")).isTrue()
        assertThat(matcher.matches("a.b.c.instagram.com")).isTrue()
    }

    @Test
    fun `unrelated domains are allowed`() {
        assertThat(matcher.matches("wikipedia.org")).isFalse()
        assertThat(matcher.matches("example.com")).isFalse()
    }

    @Test
    fun `suffix lookalikes are not blocked`() {
        // These merely end with the same text, they are not subdomains.
        assertThat(matcher.matches("notyoutube.com")).isFalse()
        assertThat(matcher.matches("myinstagram.com")).isFalse()
    }

    @Test
    fun `multi part suffixes work`() {
        assertThat(matcher.matches("news.bbc.co.uk")).isTrue()
        assertThat(matcher.matches("bbc.co.uk")).isTrue()
    }

    @Test
    fun `matching is case and trailing dot insensitive`() {
        assertThat(matcher.matches("M.YouTube.CoM")).isTrue()
        assertThat(matcher.matches("youtube.com.")).isTrue()
    }

    @Test
    fun `bare public suffix in the list cannot block everything`() {
        // A list containing a lone TLD must not take the whole web down.
        val sloppy = DomainMatcher(setOf("com"))
        assertThat(sloppy.matches("example.com")).isFalse()
    }

    @Test
    fun `empty host is allowed`() {
        assertThat(matcher.matches("")).isFalse()
    }
}
