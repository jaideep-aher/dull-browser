package com.github.jaideepaher.slatebrowser.focus

import com.github.jaideepaher.slatebrowser.adblock.siteblock.DomainMatcher
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.io.File

class PauseListTest {

    private fun list(
        store: KeyValueStore = MemoryKeyValueStore(),
        clock: FakeFocusClock = FakeFocusClock(),
        extra: Set<String> = emptySet(),
    ) = PauseList(store, clock, extra)

    @Test
    fun `nothing pauses until turned on`() {
        val pauses = list()
        assertThat(pauses.site("www.amazon.com")).isNull()
        pauses.turnOn(PauseCategory.SHOPPING)
        assertThat(pauses.site("www.amazon.com")).isEqualTo("amazon.com")
        assertThat(pauses.site("smile.amazon.co.uk")).isEqualTo("amazon.co.uk")
        assertThat(pauses.site("amazonaws.com")).isNull()
        assertThat(pauses.site("espn.com")).isNull()
    }

    @Test
    fun `work tools on a shop domain are not paused`() {
        val pauses = list()
        pauses.turnOn(PauseCategory.SHOPPING)
        assertThat(pauses.site("aws.amazon.com")).isNull()
        assertThat(pauses.site("docs.aws.amazon.com")).isNull()
        assertThat(pauses.site("developer.amazon.com")).isNull()
        assertThat(pauses.site("sellercentral.amazon.com")).isNull()
    }

    @Test
    fun `user sites are normalized and blocked sites are refused`() {
        val pauses = list()
        val listed = DomainMatcher(setOf("youtube.com"))
        assertThat(pauses.addSite("https://www.Example.org/x", listed::matches))
            .isEqualTo(PauseList.AddResult.Added("example.org"))
        assertThat(pauses.addSite("example.org", listed::matches))
            .isEqualTo(PauseList.AddResult.AlreadyPaused("example.org"))
        assertThat(pauses.addSite("youtube.com", listed::matches))
            .isEqualTo(PauseList.AddResult.Blocked("youtube.com"))
        assertThat(pauses.addSite("nope", listed::matches)).isEqualTo(PauseList.AddResult.Invalid)
        assertThat(pauses.site("shop.example.org")).isEqualTo("example.org")
        assertThat(pauses.settings.sites).containsExactly("example.org")
    }

    @Test
    fun `removal waits 24 hours then applies`() {
        val clock = FakeFocusClock()
        val store = MemoryKeyValueStore()
        val pauses = list(store, clock)
        var loosened = 0
        pauses.onLoosened = { loosened += 1 }
        pauses.turnOn(PauseCategory.SPORTS)
        pauses.addSite("example.org") { false }
        pauses.scheduleRemoval(PauseCategory.SPORTS)
        pauses.scheduleRemovalOfSite("example.org")
        assertThat(pauses.settings.removals).hasSize(2)

        clock.advance(hours = 23.9)
        pauses.applyDueRemovals()
        assertThat(pauses.site("espn.com")).isEqualTo("espn.com")
        assertThat(pauses.site("example.org")).isEqualTo("example.org")
        assertThat(loosened).isZero()

        val relaunched = list(store, clock)
        assertThat(relaunched.isOn(PauseCategory.SPORTS)).isTrue()
        assertThat(relaunched.settings.removals).hasSize(2)

        clock.advance(hours = 0.2)
        pauses.applyDueRemovals()
        assertThat(pauses.site("espn.com")).isNull()
        assertThat(pauses.site("example.org")).isNull()
        assertThat(pauses.settings.removals).isEmpty()
        assertThat(loosened).isEqualTo(1)
    }

    @Test
    fun `removal can be cancelled or undone by turning back on`() {
        val clock = FakeFocusClock()
        val pauses = list(clock = clock)
        pauses.turnOn(PauseCategory.CELEBRITY)
        pauses.scheduleRemoval(PauseCategory.CELEBRITY)
        pauses.scheduleRemoval(PauseCategory.CELEBRITY)
        assertThat(pauses.settings.removals).hasSize(1)
        val pending = pauses.pendingRemoval(ScheduledRemoval.Kind.CATEGORY, "celebrity")
        assertThat(pending).isNotNull
        assertThat(pending!!.effectiveAt).isEqualTo(clock.now().plusSeconds(86_400))
        pauses.cancelRemoval(pending)
        assertThat(pauses.pendingRemoval(ScheduledRemoval.Kind.CATEGORY, "celebrity")).isNull()

        pauses.scheduleRemoval(PauseCategory.CELEBRITY)
        pauses.turnOn(PauseCategory.CELEBRITY)
        assertThat(pauses.pendingRemoval(ScheduledRemoval.Kind.CATEGORY, "celebrity")).isNull()
        clock.advance(days = 2.0)
        pauses.applyDueRemovals()
        assertThat(pauses.site("tmz.com")).isEqualTo("tmz.com")
    }

    @Test
    fun `removing something that is off does nothing`() {
        val pauses = list()
        pauses.scheduleRemoval(PauseCategory.NEWS)
        pauses.scheduleRemovalOfSite("example.org")
        assertThat(pauses.settings.removals).isEmpty()
    }

    @Test
    fun `delay grows with repeated opens in a day`() {
        assertThat(PauseRules.delay(0)).isEqualTo(10.0)
        assertThat(PauseRules.delay(1)).isEqualTo(20.0)
        assertThat(PauseRules.delay(2)).isEqualTo(30.0)
        assertThat(PauseRules.delay(9)).isEqualTo(30.0)
        assertThat(PauseRules.delay(-1)).isEqualTo(10.0)
        assertThat(PauseRules.delay(1, base = 2.0)).isEqualTo(4.0)
        assertThat(PauseRules.GRACE_SECONDS).isEqualTo(300.0)
        assertThat(PauseRules.REMOVAL_DELAY_SECONDS).isEqualTo(86_400.0)
    }

    @Test
    fun `grace lasts five minutes then the next open pauses again`() {
        val now = java.time.Instant.parse("2026-09-28T12:00:00Z")
        val until = PauseRules.graceUntil(now)
        assertThat(PauseRules.inGrace(until, now.plusSeconds(299))).isTrue()
        assertThat(PauseRules.inGrace(until, now.plusSeconds(300))).isFalse()
        assertThat(PauseRules.inGrace(null, now)).isFalse()
    }

    @Test
    fun `pause request counts down`() {
        val start = java.time.Instant.parse("2026-09-28T12:00:00Z")
        val request = PauseRequest("https://amazon.com", "amazon.com", 10.0, start)
        assertThat(request.remaining(start.plusSeconds(4))).isEqualTo(6.0)
        assertThat(request.isReady(start.plusMillis(9_900))).isFalse()
        assertThat(request.isReady(start.plusSeconds(10))).isTrue()
    }

    @Test
    fun `no default pause host is already blocked`() {
        val blocked = loadBlockedDomains()
        val seen = mutableSetOf<String>()
        for (category in PauseCategory.entries) {
            assertThat(category.domains).isNotEmpty
            for (domain in category.domains) {
                assertThat(SiteAddress.isDomain(domain)).`as`(domain).isTrue()
                assertThat(blocked.matches(domain))
                    .`as`("$domain is blocked; a pause would never show")
                    .isFalse()
                assertThat(seen.add(domain)).`as`("$domain is listed twice").isTrue()
            }
        }
    }

    private fun loadBlockedDomains(): DomainMatcher {
        val candidates = listOf(
            File("src/main/assets/blocklist.txt"),
            File("app/src/main/assets/blocklist.txt"),
            File("../tools/extra-domains.txt"),
            File("tools/extra-domains.txt"),
        )
        val domains = mutableSetOf<String>()
        for (file in candidates) {
            if (!file.exists()) continue
            file.bufferedReader().useLines { lines ->
                lines.map { it.trim().lowercase() }
                    .filter { it.isNotEmpty() && !it.startsWith("#") }
                    .forEach { domains += it }
            }
        }
        assertThat(domains).isNotEmpty
        return DomainMatcher(domains)
    }
}
