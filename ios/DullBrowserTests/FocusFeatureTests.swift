import WebKit
import XCTest
@testable import DullBrowser

private func isolatedDefaults() -> UserDefaults {
    UserDefaults(suiteName: "DullBrowserTests.\(UUID())")!
}

private let utc: Calendar = {
    var calendar = Calendar(identifier: .gregorian)
    calendar.timeZone = TimeZone(identifier: "UTC")!
    return calendar
}()

/// A clock the test moves by hand.
private final class Clock {
    var now: Date
    init(_ text: String = "2026-09-28T12:00:00Z") { now = ISO8601DateFormatter().date(from: text)! }
    func advance(days: Double = 0, hours: Double = 0) { now += days * 86_400 + hours * 3_600 }
}

// MARK: - Site names and add-only block list

final class SiteAddressTests: XCTestCase {
    func testTypedSitesAreReducedToTheirDomain() {
        XCTAssertEqual(SiteAddress.normalize("example.com"), "example.com")
        XCTAssertEqual(SiteAddress.normalize("  HTTPS://WWW.Example.COM/some/path?q=1 "), "example.com")
        XCTAssertEqual(SiteAddress.normalize("news.example.co.uk."), "news.example.co.uk")
        XCTAssertEqual(SiteAddress.normalize("http://shop.example.org:8080"), "shop.example.org")
        XCTAssertEqual(SiteAddress.normalize("münchen.de"), "xn--mnchen-3ya.de")
    }

    func testAnythingThatIsNotADomainIsRejected() {
        for input in ["", "   ", "youtube", "com", "two words.com", "192.168.1.1", "localhost", "-bad.com",
                      "bad-.com", "exa_mple.com", "example.c0m", "example..com", "javascript:alert(1)",
                      String(repeating: "a", count: 64) + ".com"] {
            XCTAssertNil(SiteAddress.normalize(input), input)
        }
    }

    func testDomainMatcherReportsTheCoveringEntry() {
        let matcher = DomainMatcher(domains: ["example.com", "bbc.co.uk"])
        XCTAssertEqual(matcher.match("a.b.example.com"), "example.com")
        XCTAssertEqual(matcher.match("WWW.BBC.CO.UK."), "bbc.co.uk")
        XCTAssertNil(matcher.match("notexample.com"))
    }

    func testFriendlyNamesFallBackToTheDomain() {
        XCTAssertEqual(SiteName.display("youtube.com"), "YouTube")
        XCTAssertEqual(SiteName.display("www.instagram.com"), "Instagram")
        XCTAssertEqual(SiteName.display("example.org"), "example.org")
    }
}

final class CustomBlocklistTests: XCTestCase {
    func testAddedSiteIsBlockedWithSubdomainsAndPersists() throws {
        let store = isolatedDefaults()
        let blocker = SiteBlocker(domains: ["youtube.com"])
        XCTAssertEqual(CustomBlocklist.add("https://www.Example.org/page", defaults: store, blocker: blocker), .added("example.org"))
        XCTAssertTrue(blocker.isListed(host: "example.org"))
        XCTAssertTrue(blocker.isListed(host: "m.example.org"))
        XCTAssertFalse(blocker.isListed(host: "notexample.org"))
        XCTAssertEqual(blocker.listedDomain(host: "m.example.org"), "example.org")
        XCTAssertEqual(blocker.listedHost(for: try XCTUnwrap(URL(string: "https://www.google.com/url?q=https://a.example.org/"))),
                       "a.example.org")
        XCTAssertEqual(CustomBlocklist.load(from: store), ["example.org"])

        let relaunched = SiteBlocker(domains: ["youtube.com"], added: CustomBlocklist.load(from: store))
        XCTAssertTrue(relaunched.isListed(host: "www.example.org"))
    }

    func testDuplicatesBuiltInSitesAndInvalidInputAreNotStored() {
        let store = isolatedDefaults()
        let blocker = SiteBlocker(domains: ["youtube.com"])
        XCTAssertEqual(CustomBlocklist.add("youtube.com", defaults: store, blocker: blocker), .alreadyBlocked("youtube.com"))
        XCTAssertEqual(CustomBlocklist.add("m.youtube.com", defaults: store, blocker: blocker), .alreadyBlocked("m.youtube.com"))
        XCTAssertEqual(CustomBlocklist.add("not a site", defaults: store, blocker: blocker), .invalid)
        XCTAssertEqual(CustomBlocklist.add("example.net", defaults: store, blocker: blocker), .added("example.net"))
        XCTAssertEqual(CustomBlocklist.add("WWW.EXAMPLE.NET", defaults: store, blocker: blocker), .alreadyBlocked("example.net"))
        XCTAssertEqual(CustomBlocklist.load(from: store), ["example.net"])
    }

    func testTheListOnlyGrowsAndIgnoresTamperedEntries() {
        let store = isolatedDefaults()
        let blocker = SiteBlocker(domains: [])
        CustomBlocklist.add("one.com", defaults: store, blocker: blocker)
        CustomBlocklist.add("two.com", defaults: store, blocker: blocker)
        XCTAssertEqual(CustomBlocklist.load(from: store), ["one.com", "two.com"])
        store.set(["three.com", "com", "not a domain"], forKey: CustomBlocklist.storageKey)
        XCTAssertEqual(CustomBlocklist.load(from: store), ["three.com"])
        // A shorter stored list cannot unblock what this launch already enforces.
        XCTAssertTrue(blocker.isListed(host: "one.com"))
    }

    func testAddingPostsSoOpenTabsCanClose() {
        let store = isolatedDefaults()
        let blocker = SiteBlocker(domains: [])
        let posted = expectation(forNotification: .blocklistGrew, object: blocker)
        CustomBlocklist.add("example.io", defaults: store, blocker: blocker)
        wait(for: [posted], timeout: 1)
    }
}

// MARK: - Mindful pause

@MainActor
final class PauseListTests: XCTestCase {
    private func makeList(_ store: UserDefaults = isolatedDefaults(), clock: Clock = Clock(),
                          extra: Set<String> = []) -> PauseList {
        PauseList(defaults: store, now: { clock.now }, extraDomains: extra)
    }

    func testNothingPausesUntilTurnedOn() {
        let list = makeList()
        XCTAssertNil(list.site(for: "www.amazon.com"))
        list.turnOn(.shopping)
        XCTAssertEqual(list.site(for: "www.amazon.com"), "amazon.com")
        XCTAssertEqual(list.site(for: "smile.amazon.co.uk"), "amazon.co.uk")
        XCTAssertNil(list.site(for: "amazonaws.com"))
        XCTAssertNil(list.site(for: "espn.com"))
    }

    func testWorkToolsOnAShopDomainAreNotPaused() {
        let list = makeList()
        list.turnOn(.shopping)
        XCTAssertNil(list.site(for: "aws.amazon.com"))
        XCTAssertNil(list.site(for: "docs.aws.amazon.com"))
    }

    func testUserSitesAreNormalizedAndBlockedSitesAreRefused() {
        let list = makeList()
        let blocker = SiteBlocker(domains: ["youtube.com"])
        XCTAssertEqual(list.addSite("https://www.Example.org/x", blocker: blocker), .added("example.org"))
        XCTAssertEqual(list.addSite("example.org", blocker: blocker), .alreadyPaused("example.org"))
        XCTAssertEqual(list.addSite("youtube.com", blocker: blocker), .blocked("youtube.com"))
        XCTAssertEqual(list.addSite("nope", blocker: blocker), .invalid)
        XCTAssertEqual(list.site(for: "shop.example.org"), "example.org")
        XCTAssertEqual(list.settings.sites, ["example.org"])
    }

    func testRemovalWaits24HoursThenApplies() {
        let clock = Clock()
        let store = isolatedDefaults()
        let list = makeList(store, clock: clock)
        var loosened = 0
        list.onLoosened = { loosened += 1 }
        list.turnOn(.sports)
        list.addSite("example.org", blocker: SiteBlocker(domains: []))
        list.scheduleRemoval(of: .sports)
        list.scheduleRemoval(ofSite: "example.org")
        XCTAssertEqual(list.settings.removals.count, 2)

        clock.advance(hours: 23.9)
        list.applyDueRemovals()
        XCTAssertEqual(list.site(for: "espn.com"), "espn.com")
        XCTAssertEqual(list.site(for: "example.org"), "example.org")
        XCTAssertEqual(loosened, 0)

        // A relaunch before the time keeps the pause and the schedule.
        let relaunched = makeList(store, clock: clock)
        XCTAssertTrue(relaunched.isOn(.sports))
        XCTAssertEqual(relaunched.settings.removals.count, 2)

        clock.advance(hours: 0.2)
        list.applyDueRemovals()
        XCTAssertNil(list.site(for: "espn.com"))
        XCTAssertNil(list.site(for: "example.org"))
        XCTAssertTrue(list.settings.removals.isEmpty)
        XCTAssertEqual(loosened, 1)
    }

    func testRemovalCanBeCancelledOrUndoneByTurningBackOn() throws {
        let clock = Clock()
        let list = makeList(clock: clock)
        list.turnOn(.celebrity)
        list.scheduleRemoval(of: .celebrity)
        list.scheduleRemoval(of: .celebrity)
        XCTAssertEqual(list.settings.removals.count, 1)
        let pending = try XCTUnwrap(list.pendingRemoval(.category, "celebrity"))
        XCTAssertEqual(pending.effectiveAt, clock.now + 86_400)
        list.cancelRemoval(pending)
        XCTAssertNil(list.pendingRemoval(.category, "celebrity"))

        list.scheduleRemoval(of: .celebrity)
        list.turnOn(.celebrity)
        XCTAssertNil(list.pendingRemoval(.category, "celebrity"))
        clock.advance(days: 2)
        list.applyDueRemovals()
        XCTAssertEqual(list.site(for: "tmz.com"), "tmz.com")
    }

    func testRemovingSomethingThatIsOffDoesNothing() {
        let list = makeList()
        list.scheduleRemoval(of: .news)
        list.scheduleRemoval(ofSite: "example.org")
        XCTAssertTrue(list.settings.removals.isEmpty)
    }

    func testDelayGrowsWithRepeatedOpensInADay() {
        XCTAssertEqual(PauseRules.delay(pausesEarlierToday: 0), 10)
        XCTAssertEqual(PauseRules.delay(pausesEarlierToday: 1), 20)
        XCTAssertEqual(PauseRules.delay(pausesEarlierToday: 2), 30)
        XCTAssertEqual(PauseRules.delay(pausesEarlierToday: 9), 30)
        XCTAssertEqual(PauseRules.delay(pausesEarlierToday: -1), 10)
        XCTAssertEqual(PauseRules.delay(pausesEarlierToday: 1, base: 2), 4)
        XCTAssertEqual(PauseRules.grace, 300)
        XCTAssertEqual(PauseRules.removalDelay, 86_400)
    }

    func testDefaultPauseSitesAreValidAndNotAlreadyBlocked() {
        var seen: Set<String> = []
        for category in PauseCategory.allCases {
            XCTAssertFalse(category.domains.isEmpty, category.title)
            for domain in category.domains {
                XCTAssertTrue(SiteAddress.isDomain(domain), domain)
                XCTAssertFalse(SiteBlocker.shared.matcher.matches(domain), "\(domain) is blocked; a pause would never show")
                XCTAssertTrue(seen.insert(domain).inserted, "\(domain) is listed twice")
            }
        }
    }

    func testPauseRequestCountsDown() {
        let start = Date()
        let request = PauseRequest(url: URL(string: "https://amazon.com")!, site: "amazon.com", delay: 10, shownAt: start)
        XCTAssertEqual(request.remaining(at: start + 4), 6)
        XCTAssertFalse(request.isReady(at: start + 9.9))
        XCTAssertTrue(request.isReady(at: start + 10))
    }
}

@MainActor
final class PauseNavigationTests: XCTestCase {
    private func waitFor(_ condition: @escaping () -> Bool, timeout: TimeInterval = 5) {
        let deadline = Date() + timeout
        while !condition(), Date() < deadline {
            RunLoop.main.run(until: Date() + 0.05)
        }
        XCTAssertTrue(condition())
    }

    func testPausedSiteWaitsThenGoBackCountsAsAWin() {
        let store = isolatedDefaults()
        let stats = Stats(defaults: store)
        let pauses = PauseList(defaults: store, extraDomains: ["pause-test.example"])
        let model = BrowserModel(pauseList: pauses, stats: stats)
        model.open("https://shop.pause-test.example/item")
        waitFor { model.pause != nil }
        XCTAssertEqual(model.pause?.site, "pause-test.example")
        XCTAssertEqual(model.pause?.delay, 10)
        XCTAssertNil(model.webView.url)
        XCTAssertNil(model.pageURL)

        model.continuePause(at: model.pause!.shownAt + 5)
        XCTAssertNotNil(model.pause, "Continue is not possible during the countdown")

        model.leavePause()
        XCTAssertNil(model.pause)
        XCTAssertTrue(model.showingNewTab)
        XCTAssertEqual(stats.day(stats.today)?.wentBack, 1)

        model.open("https://pause-test.example/")
        waitFor { model.pause != nil }
        XCTAssertEqual(model.pause?.delay, 20)
        XCTAssertEqual(stats.pausesToday(site: "pause-test.example"), 2)
        model.close()
    }

    func testContinueOpensWithoutAnotherPauseInThatTab() {
        let store = isolatedDefaults()
        let stats = Stats(defaults: store)
        let pauses = PauseList(defaults: store, extraDomains: ["pause-test.example"])
        let model = BrowserModel(pauseList: pauses, stats: stats)
        model.open("https://pause-test.example/")
        waitFor { model.pause != nil }
        let shown = model.pause!.shownAt
        model.continuePause(at: shown + 10)
        XCTAssertNil(model.pause)
        XCTAssertEqual(stats.day(stats.today)?.continued, 1)
        model.open("https://pause-test.example/other")
        RunLoop.main.run(until: Date() + 0.5)
        XCTAssertNil(model.pause)
        XCTAssertEqual(stats.pausesToday(site: "pause-test.example"), 1)

        // Another tab still pauses.
        let other = BrowserModel(pauseList: pauses, stats: stats)
        other.open("https://pause-test.example/")
        waitFor { other.pause != nil }
        XCTAssertEqual(other.pause?.delay, 20)
        model.close()
        other.close()
    }

    func testBlockListWinsOverPause() {
        let store = isolatedDefaults()
        let stats = Stats(defaults: store)
        let pauses = PauseList(defaults: store, extraDomains: ["youtube.com"])
        let model = BrowserModel(pauseList: pauses, stats: stats)
        model.open("https://www.youtube.com/")
        waitFor { model.blockedHost != nil }
        XCTAssertNil(model.pause)
        XCTAssertEqual(model.blockedSite, "youtube.com")
        XCTAssertEqual(stats.blockedToday(site: "youtube.com"), 1)
        XCTAssertEqual(stats.pausesToday(site: "youtube.com"), 0)
        model.close()
    }

    func testRepeatedBlockedAttemptsAreCountedPerSite() {
        let store = isolatedDefaults()
        let stats = Stats(defaults: store)
        let model = BrowserModel(pauseList: PauseList(defaults: store, extraDomains: []), stats: stats)
        for address in ["https://youtube.com/", "https://m.youtube.com/watch", "https://instagram.com/"] {
            model.open(address)
            waitFor { model.blockedHost != nil }
            model.goBack()
        }
        XCTAssertEqual(stats.blockedToday(site: "youtube.com"), 2)
        XCTAssertEqual(stats.blockedToday(site: "instagram.com"), 1)
        model.close()
    }

    func testRestoredBlockedTabIsNotANewAttempt() {
        let store = isolatedDefaults()
        let stats = Stats(defaults: store)
        let model = BrowserModel(restoredURL: URL(string: "https://youtube.com/")!,
                                 pauseList: PauseList(defaults: store, extraDomains: []), stats: stats)
        model.restoreIfNeeded()
        waitFor { model.blockedHost != nil }
        XCTAssertEqual(stats.blockedToday(site: "youtube.com"), 0)
        model.close()
    }
}

// MARK: - Stats, streaks and milestones

@MainActor
final class StatsTests: XCTestCase {
    private func days(_ keys: [String], loosened: Set<String> = []) -> [DayStats] {
        keys.map { DayStats(day: $0, loosened: loosened.contains($0)) }
    }

    func testEventsAggregatePerDayAndPersist() {
        let store = isolatedDefaults()
        let clock = Clock()
        let stats = Stats(defaults: store, now: { clock.now }, calendar: utc)
        stats.recordBlocked(site: "youtube.com")
        stats.recordBlocked(site: "youtube.com")
        stats.recordBlocked(site: "reddit.com")
        stats.recordPauseShown(site: "amazon.com")
        stats.recordPause(wentBack: true)
        clock.advance(days: 1)
        stats.recordBlocked(site: "youtube.com")
        stats.recordPause(wentBack: false)

        let reloaded = Stats(defaults: store, now: { clock.now }, calendar: utc)
        XCTAssertEqual(reloaded.data.days.map(\.day), ["2026-09-28", "2026-09-29"])
        XCTAssertEqual(reloaded.day("2026-09-28")?.blocked, ["youtube.com": 2, "reddit.com": 1])
        XCTAssertEqual(reloaded.day("2026-09-28")?.wentBack, 1)
        XCTAssertEqual(reloaded.day("2026-09-29")?.continued, 1)
        XCTAssertEqual(reloaded.blockedToday(site: "youtube.com"), 1)
        XCTAssertEqual(reloaded.pausesToday(site: "amazon.com"), 0)
    }

    func testStreakCountsConsecutiveCleanDays() {
        let today = "2026-09-28"
        XCTAssertEqual(Stats.streak(in: [], today: today, calendar: utc), 0)
        XCTAssertEqual(Stats.streak(in: days(["2026-09-26", "2026-09-27", today]), today: today, calendar: utc), 3)
        // Today not opened yet still shows the streak through yesterday.
        XCTAssertEqual(Stats.streak(in: days(["2026-09-26", "2026-09-27"]), today: today, calendar: utc), 2)
        // A day without Dull ends it.
        XCTAssertEqual(Stats.streak(in: days(["2026-09-24", "2026-09-26", "2026-09-27", today]), today: today, calendar: utc), 3)
        // So does a day a pause was turned off.
        XCTAssertEqual(Stats.streak(in: days(["2026-09-26", "2026-09-27", today], loosened: ["2026-09-27"]),
                                    today: today, calendar: utc), 1)
        XCTAssertEqual(Stats.streak(in: days(["2026-09-27", today], loosened: [today]), today: today, calendar: utc), 0)
        // Across a month boundary.
        XCTAssertEqual(Stats.streak(in: days(["2026-09-30", "2026-10-01"]), today: "2026-10-01", calendar: utc), 2)
    }

    func testMilestonesAreReachedOnceWithAOneTimeNotice() {
        let store = isolatedDefaults()
        let clock = Clock("2026-09-01T09:00:00Z")
        let stats = Stats(defaults: store, now: { clock.now }, calendar: utc)
        for _ in 0..<6 {
            stats.recordUse()
            clock.advance(days: 1)
        }
        XCTAssertTrue(stats.data.milestones.isEmpty)
        stats.recordUse()
        XCTAssertEqual(stats.currentStreak, 7)
        XCTAssertEqual(stats.data.milestones, [Milestone(days: 7, reached: "2026-09-07")])
        XCTAssertEqual(stats.data.noticeMilestone, 7)
        stats.dismissMilestoneNotice()
        stats.recordUse()
        XCTAssertNil(stats.data.noticeMilestone)
        XCTAssertEqual(stats.data.milestones.count, 1)

        // Losing the streak and earning it again does not repeat the badge.
        clock.advance(days: 2)
        for _ in 0..<7 {
            stats.recordUse()
            clock.advance(days: 1)
        }
        XCTAssertEqual(stats.data.milestones.count, 1)
        XCTAssertNil(stats.data.noticeMilestone)
    }

    func testThirtyDayMilestone() {
        let clock = Clock("2026-01-01T09:00:00Z")
        let stats = Stats(defaults: isolatedDefaults(), now: { clock.now }, calendar: utc)
        for _ in 0..<30 {
            stats.recordUse()
            clock.advance(days: 1)
        }
        XCTAssertEqual(stats.data.milestones.map(\.days), [7, 30])
        XCTAssertEqual(stats.data.milestones.last?.reached, "2026-01-30")
        XCTAssertEqual(stats.data.noticeMilestone, 30)
    }

    func testHistoryIsCapped() {
        let clock = Clock("2025-01-01T09:00:00Z")
        let stats = Stats(defaults: isolatedDefaults(), now: { clock.now }, calendar: utc)
        for _ in 0..<(Stats.historyLimit + 20) {
            stats.recordUse()
            clock.advance(days: 1)
        }
        XCTAssertEqual(stats.data.days.count, Stats.historyLimit)
        XCTAssertEqual(stats.data.days.last?.day, DayKey.string(for: clock.now - 86_400, calendar: utc))
    }

    func testWeekSummaryAndTimeSaved() {
        var list = days(["2026-09-20", "2026-09-22", "2026-09-28"])
        list[0].blocked = ["youtube.com": 50]
        list[1].blocked = ["youtube.com": 3, "reddit.com": 4]
        list[1].wentBack = 2
        list[2].blocked = ["reddit.com": 1]
        list[2].continued = 1
        let week = Stats.summary(of: list, endingOn: "2026-09-28", minutesPerAttempt: 8, calendar: utc)
        XCTAssertEqual(week.attempts, 8)
        XCTAssertEqual(week.wentBack, 2)
        XCTAssertEqual(week.continued, 1)
        XCTAssertEqual(week.minutesSaved, 80)
        XCTAssertEqual(week.topSites.map(\.site), ["reddit.com", "youtube.com"])
        XCTAssertEqual(Stats.savedLabel(minutes: 40), "~40m")
        XCTAssertEqual(Stats.savedLabel(minutes: 80), "~1h")
        XCTAssertEqual(Stats.savedLabel(minutes: 370), "~6h")
    }

    func testMinutesPerAttemptDefaultsToEight() {
        let store = isolatedDefaults()
        let stats = Stats(defaults: store)
        XCTAssertEqual(stats.minutesPerAttempt, 8)
        store.set(15, forKey: Stats.minutesKey)
        XCTAssertEqual(stats.minutesPerAttempt, 15)
        store.set(99, forKey: Stats.minutesKey)
        XCTAssertEqual(stats.minutesPerAttempt, 8)
    }

    func testShareLineHasTotalsOnly() {
        let week = WeekSummary(attempts: 214, wentBack: 0, continued: 0, minutesSaved: 360,
                               topSites: [("youtube.com", 200)])
        let line = ShareCard.line(week: week, streak: 9)
        XCTAssertEqual(line, "Dull blocked 214 attempts this week · 9-day streak · ~6h saved")
        XCTAssertFalse(line.contains("youtube"))
        XCTAssertEqual(ShareCard.line(week: WeekSummary(attempts: 1, wentBack: 0, continued: 0, minutesSaved: 0, topSites: []),
                                      streak: 0), "Dull blocked 1 attempt this week")
    }

    func testShareCardRendersInLightAndDark() throws {
        let week = WeekSummary(attempts: 3, wentBack: 1, continued: 0, minutesSaved: 32, topSites: [])
        XCTAssertNotNil(ShareCard.render(week: week, streak: 2, scheme: .light))
        XCTAssertNotNil(ShareCard.render(week: week, streak: 2, scheme: .dark))
    }

    func testStoredFormatIsPlainJSON() throws {
        let store = isolatedDefaults()
        let clock = Clock()
        let stats = Stats(defaults: store, now: { clock.now }, calendar: utc)
        stats.recordBlocked(site: "youtube.com")
        let data = try XCTUnwrap(store.data(forKey: Stats.storageKey))
        let json = try XCTUnwrap(String(data: data, encoding: .utf8))
        XCTAssertTrue(json.contains(#""day":"2026-09-28""#), json)
        XCTAssertTrue(json.contains(#""blocked":{"youtube.com":1}"#), json)
    }
}

// MARK: - Countdowns, read later and bookmarks

@MainActor
final class CountdownTests: XCTestCase {
    func testNearestUpcomingDateIsShownAndPastDatesHide() {
        let clock = Clock()
        let list = Countdowns(defaults: isolatedDefaults(), now: { clock.now }, calendar: utc)
        XCTAssertNil(list.nextLabel)
        list.add(name: "Finals", on: clock.now + 12 * 86_400)
        list.add(name: "Essay", on: clock.now + 86_400)
        list.add(name: "  ", on: clock.now)
        XCTAssertEqual(list.items.count, 2)
        XCTAssertEqual(list.nextLabel, "Essay tomorrow")
        clock.advance(days: 1)
        XCTAssertEqual(list.nextLabel, "Essay today")
        clock.advance(days: 1)
        XCTAssertEqual(list.nextLabel, "Finals in 10 days")
        clock.advance(days: 11)
        XCTAssertNil(list.nextLabel)
        XCTAssertEqual(list.items.count, 2)
    }

    func testCountdownsPersistAndCanBeRemoved() {
        let store = isolatedDefaults()
        let list = Countdowns(defaults: store)
        list.add(name: "Thesis", on: Date() + 3 * 86_400)
        let reloaded = Countdowns(defaults: store)
        XCTAssertEqual(reloaded.items.map(\.name), ["Thesis"])
        reloaded.remove(reloaded.items[0].id)
        XCTAssertTrue(Countdowns(defaults: store).items.isEmpty)
    }
}

final class ReadingWindowTests: XCTestCase {
    private func at(_ time: String) -> Date { ISO8601DateFormatter().date(from: "2026-09-28T\(time):00Z")! }

    func testOffMeansAlwaysOpen() {
        XCTAssertTrue(ReadingWindow().isOpen(at: at("03:00"), calendar: utc))
    }

    func testEveningWindow() {
        let window = ReadingWindow(enabled: true, start: 20 * 60, end: 22 * 60)
        XCTAssertFalse(window.isOpen(at: at("19:59"), calendar: utc))
        XCTAssertTrue(window.isOpen(at: at("20:00"), calendar: utc))
        XCTAssertTrue(window.isOpen(at: at("21:59"), calendar: utc))
        XCTAssertFalse(window.isOpen(at: at("22:00"), calendar: utc))
        XCTAssertEqual(window.timeUntilOpen(from: at("18:30"), calendar: utc), 90 * 60)
        XCTAssertEqual(window.timeUntilOpen(from: at("23:00"), calendar: utc), 21 * 3_600)
        XCTAssertEqual(window.timeUntilOpen(from: at("20:30"), calendar: utc), 0)
    }

    func testWindowAcrossMidnight() {
        let window = ReadingWindow(enabled: true, start: 22 * 60, end: 60)
        XCTAssertTrue(window.isOpen(at: at("23:30"), calendar: utc))
        XCTAssertTrue(window.isOpen(at: at("00:30"), calendar: utc))
        XCTAssertFalse(window.isOpen(at: at("01:00"), calendar: utc))
        XCTAssertFalse(window.isOpen(at: at("12:00"), calendar: utc))
    }

    func testWaitLabel() {
        XCTAssertEqual(ReadingWindow.waitLabel(25 * 60), "Opens in 25m")
        XCTAssertEqual(ReadingWindow.waitLabel(3 * 3_600 + 12 * 60), "Opens in 3h 12m")
    }
}

@MainActor
final class ReadLaterTests: XCTestCase {
    func testSaveMarkReadRemoveAndPersist() throws {
        let store = isolatedDefaults()
        let list = ReadLater(defaults: store)
        let url = try XCTUnwrap(URL(string: "https://example.com/essay"))
        list.add(url, title: "An essay")
        list.add(url, title: "Again")
        list.add(try XCTUnwrap(URL(string: "mailto:a@example.com")), title: "Mail")
        XCTAssertEqual(list.items.count, 1)
        XCTAssertTrue(list.contains(url))
        list.setRead(list.items[0].id, true)
        XCTAssertEqual(list.read.count, 1)
        XCTAssertFalse(list.contains(url))
        list.window = ReadingWindow(enabled: true, start: 8 * 60, end: 10 * 60)

        let reloaded = ReadLater(defaults: store)
        XCTAssertEqual(reloaded.items.map(\.title), ["An essay"])
        XCTAssertNotNil(reloaded.items[0].readAt)
        XCTAssertEqual(reloaded.window.start, 8 * 60)
        reloaded.remove(reloaded.items[0].id)
        XCTAssertTrue(ReadLater(defaults: store).items.isEmpty)
    }
}

@MainActor
final class BookmarkTests: XCTestCase {
    func testAddToggleEditRemoveAndPersist() throws {
        let store = isolatedDefaults()
        let bookmarks = Bookmarks(defaults: store)
        let url = try XCTUnwrap(URL(string: "https://example.com/"))
        bookmarks.add(url, title: "  ")
        XCTAssertEqual(bookmarks.items.first?.title, "example.com")
        XCTAssertTrue(bookmarks.contains(URL(string: "https://EXAMPLE.com")))
        bookmarks.add(url, title: "Duplicate")
        XCTAssertEqual(bookmarks.items.count, 1)

        let id = bookmarks.items[0].id
        XCTAssertFalse(bookmarks.update(id, title: "Search", address: "not a url"))
        XCTAssertTrue(bookmarks.update(id, title: "Docs", address: "developer.apple.com/documentation"))
        XCTAssertEqual(bookmarks.items[0].url.absoluteString, "https://developer.apple.com/documentation")

        let reloaded = Bookmarks(defaults: store)
        XCTAssertEqual(reloaded.items.map(\.title), ["Docs"])
        reloaded.toggle(reloaded.items[0].url, title: "")
        XCTAssertTrue(reloaded.items.isEmpty)
        XCTAssertTrue(Bookmarks(defaults: store).items.isEmpty)
    }

    func testBlockedSitesCanBeBookmarkedButOtherSchemesCannot() throws {
        let bookmarks = Bookmarks(defaults: isolatedDefaults())
        bookmarks.add(try XCTUnwrap(URL(string: "https://youtube.com/")), title: "YouTube")
        bookmarks.add(try XCTUnwrap(URL(string: "javascript:alert(1)")), title: "Script")
        XCTAssertEqual(bookmarks.items.map(\.title), ["YouTube"])
        XCTAssertEqual(SiteBlocker.shared.listedHost(for: bookmarks.items[0].url), "youtube.com")
    }

    func testQuickLinksAreCappedAtEight() throws {
        let store = isolatedDefaults()
        let bookmarks = Bookmarks(defaults: store)
        XCTAssertEqual(bookmarks.quickLinkCount, 4)
        for index in 0..<10 { bookmarks.add(try XCTUnwrap(URL(string: "https://site\(index).example.com/")), title: "\(index)") }
        XCTAssertEqual(bookmarks.quickLinks.map(\.title), ["0", "1", "2", "3"])
        bookmarks.quickLinkCount = 20
        XCTAssertEqual(bookmarks.quickLinkCount, 8)
        XCTAssertEqual(Bookmarks(defaults: store).quickLinkCount, 8)
        bookmarks.quickLinkCount = 0
        XCTAssertTrue(bookmarks.quickLinks.isEmpty)
    }
}

// MARK: - Feature gate and previews

@MainActor
final class FeatureGateAndPreviewTests: XCTestCase {
    func testEverythingIsUnlockedByDefault() {
        XCTAssertTrue(Feature.allCases.allSatisfy(\.isUnlocked))
    }

    func testLockingAFeatureNeverTouchesBlocking() {
        let saved = Entitlements.current
        defer { Entitlements.current = saved }
        Entitlements.current.locked = Set(Feature.allCases)
        XCTAssertFalse(Feature.customBlocklist.isUnlocked)
        XCTAssertEqual(SiteBlocker.shared.listedHost(for: URL(string: "https://youtube.com/")!), "youtube.com")
    }

    func testPreviewsAreRemovedFromDisk() throws {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        let thumbnails = TabThumbnails(directory: directory)
        let id = UUID()
        let file = directory.appendingPathComponent("\(id.uuidString).jpg")
        try Data([1, 2, 3]).write(to: file)
        thumbnails.remove(id)
        XCTAssertFalse(FileManager.default.fileExists(atPath: file.path))
    }

    /// Prints the cost so it can be read from the test log; asserts only generous bounds.
    func testPreviewCaptureStaysOffTheMainThreadAndSmall() throws {
        let window = try XCTUnwrap(UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
            .flatMap(\.windows).first, "No app window in this test host")
        let view = WKWebView(frame: window.bounds)
        window.addSubview(view)
        defer { view.removeFromSuperview() }
        let rows = (0..<200).map { "<p>Row \($0) with some text to paint.</p>" }.joined()
        view.loadHTMLString("<html><body><h1>Preview</h1>\(rows)</body></html>", baseURL: nil)
        let deadline = Date() + 10
        while view.isLoading || view.estimatedProgress < 1, Date() < deadline { RunLoop.main.run(until: Date() + 0.05) }
        RunLoop.main.run(until: Date() + 0.3)

        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        let thumbnails = TabThumbnails(directory: directory)
        let id = UUID()
        let started = CFAbsoluteTimeGetCurrent()
        thumbnails.capture(view, id: id)
        let blocking = (CFAbsoluteTimeGetCurrent() - started) * 1000
        while thumbnails.images[id] == nil, Date() < deadline + 5 { RunLoop.main.run(until: Date() + 0.01) }
        let total = (CFAbsoluteTimeGetCurrent() - started) * 1000
        let image = try XCTUnwrap(thumbnails.images[id])
        let bytes = try FileManager.default.attributesOfItem(atPath: directory.appendingPathComponent("\(id.uuidString).jpg").path)[.size] as? Int ?? 0
        print("Tab preview: \(String(format: "%.2f", blocking)) ms on the main thread, \(String(format: "%.0f", total)) ms until ready, "
              + "\(Int(image.size.width * image.scale))x\(Int(image.size.height * image.scale)) px, \(bytes) bytes on disk")
        XCTAssertLessThan(blocking, 16, "Starting a capture must not cost a frame")
        XCTAssertLessThanOrEqual(image.size.width * image.scale, 320)
        XCTAssertLessThan(bytes, 80_000)
    }

    func testStartPageAndBlankTabsAreNotCaptured() {
        let model = BrowserModel()
        XCTAssertFalse(model.hasCapturablePage)
        TabThumbnails.shared.capture(model)
        XCTAssertNil(TabThumbnails.shared.images[model.id])
        model.close()
    }
}
