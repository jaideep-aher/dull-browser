import UIKit
import WebKit
import XCTest
@testable import DullBrowser

private func isolatedDefaults() -> UserDefaults {
    UserDefaults(suiteName: "DullBrowserTests.\(UUID())")!
}

private func searchQuery(_ url: URL?) -> String? {
    url.flatMap { URLComponents(url: $0, resolvingAgainstBaseURL: false) }?
        .queryItems?.first(where: { $0.name == "q" })?.value
}

// MARK: - Search engine (N01–N04)

final class SearchEngineChoiceTests: XCTestCase {
    /// N01
    func testEveryEngineHasNameAndHTTPSSearchHost() throws {
        XCTAssertEqual(SearchEngine.allCases.map(\.name), ["Google", "DuckDuckGo", "Bing"])
        for engine in SearchEngine.allCases {
            let prefix = try XCTUnwrap(URL(string: engine.searchPrefix))
            XCTAssertEqual(prefix.scheme, "https", engine.name)
            XCTAssertNotNil(prefix.host, engine.name)
        }
    }

    /// N02
    func testMigrationRewritesAnyUnknownEngineToGoogle() {
        for stored in ["yahoo", "", "Google", "kagi"] {
            let store = isolatedDefaults()
            store.set(stored, forKey: SearchEngine.preferenceKey)
            BrowserPreferences.migrate(store)
            XCTAssertEqual(store.string(forKey: SearchEngine.preferenceKey), "google", stored)
        }
    }

    /// N03
    func testMigrationIsIdempotentForValidChoice() {
        let store = isolatedDefaults()
        store.set("duckDuckGo", forKey: SearchEngine.preferenceKey)
        BrowserPreferences.migrate(store)
        BrowserPreferences.migrate(store)
        XCTAssertEqual(store.string(forKey: SearchEngine.preferenceKey), "duckDuckGo")
    }

    /// N04
    func testResolveMatchesStoredValuesExactly() {
        XCTAssertEqual(SearchEngine.resolve("bing"), .bing)
        XCTAssertEqual(SearchEngine.resolve("duckDuckGo"), .duckDuckGo)
        XCTAssertEqual(SearchEngine.resolve("BING"), .google)
        XCTAssertEqual(SearchEngine.resolve(" bing"), .google)
    }
}

// MARK: - Address or search (N05–N19)

final class AddressParsingTests: XCTestCase {
    private func url(_ text: String, _ engine: SearchEngine = .google) -> URL? {
        BrowserInput.url(for: text, searchEngine: engine)
    }

    private func assertSearch(_ text: String, file: StaticString = #filePath, line: UInt = #line) {
        let result = url(text)
        XCTAssertEqual(result?.host, "www.google.com", "\(text) should be a search", file: file, line: line)
        XCTAssertEqual(searchQuery(result), text.trimmingCharacters(in: .whitespacesAndNewlines), file: file, line: line)
    }

    /// N05
    func testIPv4AddressOpensDirectly() {
        XCTAssertEqual(url("192.168.1.1")?.absoluteString, "https://192.168.1.1")
    }

    /// N06
    func testIPv4WithPortAndPathOpensDirectly() {
        XCTAssertEqual(url("10.0.0.1:8080/status")?.absoluteString, "https://10.0.0.1:8080/status")
    }

    /// N07
    func testIncompleteIPAddressIsSearched() {
        assertSearch("1.2.3")
    }

    /// N08
    func testLocalhostWithPortOpensDirectly() {
        XCTAssertEqual(url("localhost:3000")?.absoluteString, "https://localhost:3000")
        XCTAssertEqual(url("LOCALHOST/admin")?.host?.lowercased(), "localhost")
        assertSearch("localhosts")
    }

    /// N09
    func testExplicitHTTPLocalhostIsKept() {
        XCTAssertEqual(url("http://localhost:8080/a?b=1")?.absoluteString, "http://localhost:8080/a?b=1")
    }

    /// N10
    func testHostWithPortQueryAndFragmentIsKept() {
        XCTAssertEqual(url("example.com:8443/path?x=1#frag")?.absoluteString, "https://example.com:8443/path?x=1#frag")
    }

    /// N11
    func testUppercaseSchemeIsAccepted() throws {
        let result = try XCTUnwrap(url("HTTPS://Example.com/Path"))
        XCTAssertEqual(result.scheme?.lowercased(), "https")
        XCTAssertEqual(result.host?.lowercased(), "example.com")
        XCTAssertEqual(result.path, "/Path")
    }

    /// N12
    func testSurroundingWhitespaceIsTrimmed() {
        XCTAssertEqual(url("  example.com \n")?.absoluteString, "https://example.com")
    }

    /// N13
    func testAddressFollowedByWordsIsSearched() {
        assertSearch("example.com is down")
    }

    /// N14
    func testUnsupportedSchemesBecomeSearches() {
        for text in ["ftp://example.com", "javascript:alert(1)", "file:///etc/passwd", "data:text/html,hi"] {
            let result = url(text)
            XCTAssertEqual(result?.host, "www.google.com", text)
            XCTAssertEqual(result?.scheme, "https", text)
        }
    }

    /// N15
    func testInternationalDomainOpensDirectly() throws {
        let result = try XCTUnwrap(url("münchen.de"))
        XCTAssertEqual(result.scheme, "https")
        XCTAssertNotEqual(result.host, "www.google.com")
        XCTAssertNil(searchQuery(result))
    }

    /// N16
    func testUnicodeSearchKeepsEveryCharacter() {
        for engine in SearchEngine.allCases {
            XCTAssertEqual(searchQuery(url("日本語 検索 🙂", engine)), "日本語 検索 🙂", engine.name)
        }
    }

    /// N17
    func testVeryLongInputIsSearchedWithoutLoss() {
        let text = String(repeating: "focus time ", count: 500).trimmingCharacters(in: .whitespaces)
        XCTAssertEqual(searchQuery(url(text)), text)
    }

    /// N18
    func testNumericTopLevelDomainIsSearched() {
        assertSearch("v1.2")
    }

    /// N19
    func testEmptyLabelsAreSearched() {
        assertSearch("example..com")
        assertSearch(".com")
    }
}

// MARK: - Blocking (N20–N24)

final class BlockingEdgeCaseTests: XCTestCase {
    private let blocker = SiteBlocker.shared

    private func listed(_ address: String) -> String? {
        URL(string: address).flatMap(blocker.listedHost(for:))
    }

    /// N20
    func testListedHostIgnoresCaseAndTrailingDot() {
        XCTAssertNotNil(listed("https://WWW.YouTube.COM./watch?v=1"))
        XCTAssertNotNil(listed("https://Instagram.com/"))
    }

    /// N21
    func testDeepSubdomainsOfListedSitesAreBlocked() {
        for address in ["https://m.youtube.com/", "https://a.b.c.instagram.com/x", "https://www.www.bbc.co.uk/"] {
            XCTAssertNotNil(listed(address), address)
        }
    }

    /// N22
    func testLookalikesAndPathsAreNotBlocked() {
        for address in ["https://notyoutube.com/", "https://youtube.com.example.org/", "https://example.com/youtube.com"] {
            XCTAssertNil(listed(address), address)
        }
    }

    /// N23
    func testListedHostIsBlockedOnAnySchemeOrPort() {
        XCTAssertNotNil(listed("http://youtube.com:8080/"))
        XCTAssertNotNil(listed("https://youtube.com:443/feed"))
    }

    /// N24
    func testNoStoredPreferenceCanTurnBlockingOff() {
        let keys = ["blockingEnabled", "siteBlocking", "allowList", "allowedSites"]
        for store in [UserDefaults.standard, BrowserPreferences.defaults] {
            store.set(false, forKey: "blockingEnabled")
            store.set(false, forKey: "siteBlocking")
            store.set(["youtube.com"], forKey: "allowList")
            store.set(["youtube.com"], forKey: "allowedSites")
        }
        defer {
            for store in [UserDefaults.standard, BrowserPreferences.defaults] {
                keys.forEach(store.removeObject(forKey:))
            }
        }
        BrowserPreferences.migrate()
        XCTAssertEqual(listed("https://youtube.com/"), "youtube.com")
        XCTAssertTrue(blocker.isListed(host: "instagram.com"))
    }
}

// MARK: - Tabs and sessions (N25–N30)

@MainActor
final class TabSessionEdgeCaseTests: XCTestCase {
    /// N25
    func testManyTabsCanBeOpenedAndClosed() {
        let session = BrowserSession(defaults: isolatedDefaults(), restore: false)
        for _ in 0..<20 { session.addTab() }
        XCTAssertEqual(session.tabs.count, 21)
        XCTAssertEqual(Set(session.tabs.map(\.id)).count, 21)
        for id in session.tabs.map(\.id) { session.close(id) }
        XCTAssertEqual(session.tabs.count, 1)
        XCTAssertTrue(session.activeTab.showingNewTab)
        session.activeTab.close()
    }

    /// N26
    func testClosingSelectedMiddleTabSelectsNextTab() {
        let session = BrowserSession(defaults: isolatedDefaults(), restore: false)
        session.addTab()
        let middle = session.selectedID
        session.addTab()
        let last = session.selectedID
        session.select(middle)
        session.close(middle)
        XCTAssertEqual(session.selectedID, last)
        XCTAssertEqual(session.tabs.count, 2)
        session.tabs.forEach { $0.close() }
    }

    /// N27
    func testSelectedTabIsRestored() {
        let store = isolatedDefaults()
        let session = BrowserSession(defaults: store, restore: false)
        let first = session.selectedID
        session.addTab()
        session.addTab()
        session.select(first)
        session.tabs.forEach { $0.close() }
        let restored = BrowserSession(defaults: store)
        XCTAssertEqual(restored.tabs.count, 3)
        XCTAssertEqual(restored.selectedID, first)
        restored.tabs.forEach { $0.close() }
    }

    /// N28
    func testCorruptSavedSessionStartsWithOneTab() {
        let store = isolatedDefaults()
        store.set(Data("not json".utf8), forKey: "browserSession.v1")
        let session = BrowserSession(defaults: store)
        XCTAssertEqual(session.tabs.count, 1)
        XCTAssertTrue(session.activeTab.showingNewTab)
        session.activeTab.close()
    }

    /// N29
    func testSavedSessionWithDuplicateTabIDsIsDiscarded() {
        let store = isolatedDefaults()
        let id = UUID().uuidString
        let json = #"{"tabs":[{"id":"\#(id)","address":"https://example.com/"},{"id":"\#(id)"}],"selectedID":"\#(id)"}"#
        store.set(Data(json.utf8), forKey: "browserSession.v1")
        let session = BrowserSession(defaults: store)
        XCTAssertEqual(session.tabs.count, 1)
        XCTAssertNotEqual(session.selectedID.uuidString, id)
        session.activeTab.close()
    }

    /// N30
    func testStartingFreshIgnoresSavedTabs() {
        let store = isolatedDefaults()
        store.set(true, forKey: BrowserPreferences.startFreshKey)
        let session = BrowserSession(defaults: store, restore: false)
        session.activeTab.open("https://example.com/")
        session.addTab()
        session.save()
        session.tabs.forEach { $0.close() }
        XCTAssertTrue(BrowserPreferences.flag(BrowserPreferences.startFreshKey, default: false, in: store))
        let fresh = BrowserSession(defaults: store, restore: false)
        XCTAssertEqual(fresh.tabs.count, 1)
        XCTAssertTrue(fresh.activeTab.showingNewTab)
        XCTAssertNil(fresh.activeTab.savedAddress)
        fresh.activeTab.close()
    }
}

// MARK: - Navigation (N31–N32)

@MainActor
final class NavigationEdgeCaseTests: XCTestCase {
    /// N31
    func testBackFromFailedFirstPageReturnsToStartPage() {
        let model = BrowserModel()
        model.open("https://example.com/unreachable")
        model.webView.stopLoading()
        model.webView(model.webView, didFailProvisionalNavigation: nil,
                      withError: NSError(domain: NSURLErrorDomain, code: NSURLErrorCannotConnectToHost))
        XCTAssertNotNil(model.loadError)
        XCTAssertTrue(model.canStepBack)
        model.goBack()
        XCTAssertTrue(model.showingNewTab)
        XCTAssertNil(model.loadError)
        XCTAssertFalse(model.canStepBack)
        model.close()
    }

    /// N32
    func testIncomingLinksOpenOnlyFromDullScheme() {
        let model = BrowserModel()
        model.openIncoming(URL(string: "https://example.com/?url=https%3A%2F%2Fexample.org")!)
        XCTAssertTrue(model.showingNewTab)
        model.openIncoming(URL(string: "dullbrowser://open-url")!)
        XCTAssertTrue(model.showingNewTab)
        model.openIncoming(URL(string: "dullbrowser://open-url?url=https%3A%2F%2Fexample.com%2Fpage")!)
        XCTAssertFalse(model.showingNewTab)
        XCTAssertEqual(model.url?.absoluteString, "https://example.com/page")
        model.close()
    }
}

// MARK: - Appearance and privacy (N33–N34)

@MainActor
final class AppearanceAndPrivacyTests: XCTestCase {
    /// N33
    func testAppearanceIsAppliedToAppWindows() throws {
        let windows = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.flatMap(\.windows)
        try XCTSkipIf(windows.isEmpty, "No app window in this test host")
        defer { Appearance.light.apply() }
        Appearance.dark.apply()
        XCTAssertTrue(windows.allSatisfy { $0.overrideUserInterfaceStyle == .dark })
        Appearance.system.apply()
        XCTAssertTrue(windows.allSatisfy { $0.overrideUserInterfaceStyle == .unspecified })
        Appearance.light.apply()
        XCTAssertTrue(windows.allSatisfy { $0.overrideUserInterfaceStyle == .light })
    }

    /// N34
    func testClearingBrowsingDataRemovesCookies() async throws {
        let store = WKWebsiteDataStore.nonPersistent()
        let cookie = try XCTUnwrap(HTTPCookie(properties: [
            .domain: "example.com", .path: "/", .name: "session", .value: "abc", .secure: "TRUE",
        ]))
        await store.httpCookieStore.setCookie(cookie)
        let before = await store.httpCookieStore.allCookies()
        XCTAssertEqual(before.map(\.name), ["session"])
        let session = BrowserSession(defaults: isolatedDefaults(), restore: false)
        await session.clearBrowsingData(in: store)
        let after = await store.httpCookieStore.allCookies()
        XCTAssertTrue(after.isEmpty)
        XCTAssertEqual(SiteBlocker.shared.listedHost(for: URL(string: "https://youtube.com/")!), "youtube.com")
        session.activeTab.close()
    }
}
