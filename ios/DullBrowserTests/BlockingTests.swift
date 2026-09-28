import XCTest
@testable import DullBrowser

final class DomainMatcherTests: XCTestCase {
    private let matcher = DomainMatcher(domains: ["bbc.com", "bbc.co.uk", "com", "pornhub.com"])

    func testHostAndParentDomains() {
        XCTAssertTrue(matcher.matches("bbc.com"))
        XCTAssertTrue(matcher.matches("www.bbc.com"))
        XCTAssertTrue(matcher.matches("WWW.BBC.CO.UK."))
        XCTAssertTrue(matcher.matches("de.pornhub.com"))
    }

    func testDoesNotMatchLookalikesOrBareSuffix() {
        XCTAssertFalse(matcher.matches("notbbc.com"))
        XCTAssertFalse(matcher.matches("bbc.com.example.org"))
        XCTAssertFalse(matcher.matches("example.com"))
        XCTAssertFalse(matcher.matches(""))
    }
}

final class BlocklistTests: XCTestCase {
    func testBundledListIsTheAndroidAsset() {
        let domains = Blocklist.load(from: .main)
        XCTAssertGreaterThan(domains.count, 60_000)
        for listed in ["bbc.com", "bbc.co.uk", "pornhub.com", "xvideos.com", "xnxx.com", "xhamster.com"] {
            XCTAssertTrue(domains.contains(listed), listed)
        }
        for open in ["example.com", "duckduckgo.com", "google.com", "bing.com"] {
            XCTAssertFalse(domains.contains(open), open)
        }
    }
}

final class NavigationHopsTests: XCTestCase {
    private func hosts(_ url: String) -> [String] {
        NavigationHops.extract(url).compactMap(NavigationHops.hostOf)
    }

    func testGoogleWrapperIsUnwrapped() {
        XCTAssertEqual(hosts("https://www.google.com/url?sa=t&q=https%3A%2F%2Fwww.bbc.com%2Fnews&ved=x"),
                       ["www.google.com", "www.bbc.com"])
        XCTAssertTrue(hosts("https://www.google.co.uk/imgres?imgurl=https://x.org/a.jpg&imgrefurl=https://bbc.co.uk/")
            .contains("bbc.co.uk"))
    }

    func testOpaqueGotoTokenStaysAsIs() {
        XCTAssertEqual(hosts("https://www.google.com/goto?url=CAESAbcdef123"), ["www.google.com"])
    }

    func testSearchQueriesAreNotUnwrapped() {
        XCTAssertEqual(hosts("https://www.google.com/search?q=https://bbc.com"), ["www.google.com"])
        XCTAssertEqual(hosts("https://duckduckgo.com/?q=https%3A%2F%2Fbbc.com"), ["duckduckgo.com"])
    }

    func testAmp() {
        XCTAssertEqual(NavigationHops.extract("https://www.google.com/amp/s/www.bbc.com/news/1").last,
                       "https://www.bbc.com/news/1")
        XCTAssertEqual(NavigationHops.extract("https://www-bbc-com.cdn.ampproject.org/c/s/www.bbc.com/news/2").last,
                       "https://www.bbc.com/news/2")
    }

    func testShareWrappersAndIntents() {
        XCTAssertTrue(hosts("https://l.facebook.com/l.php?u=https%3A%2F%2Fbbc.com%2F&h=x").contains("bbc.com"))
        XCTAssertTrue(hosts("https://www.youtube.com/redirect?q=https://pornhub.com/").contains("pornhub.com"))
        XCTAssertTrue(hosts("intent://example.org/#Intent;scheme=https;S.browser_fallback_url=https%3A%2F%2Fwww.bbc.com;end")
            .contains("www.bbc.com"))
    }

    func testShortLinksAreNotPreUnwrapped() {
        XCTAssertEqual(hosts("https://t.co/abc123?url=https://bbc.com"), ["t.co"])
    }
}

final class SiteBlockerTests: XCTestCase {
    private let blocker = SiteBlocker(domains: ["bbc.com", "pornhub.com"])

    func testDirectAndHiddenHosts() throws {
        XCTAssertEqual(blocker.listedHost(for: try XCTUnwrap(URL(string: "https://www.bbc.com/"))), "www.bbc.com")
        XCTAssertEqual(
            blocker.listedHost(for: try XCTUnwrap(URL(string: "https://www.google.com/url?q=https://pornhub.com/view"))),
            "pornhub.com"
        )
        XCTAssertNil(blocker.listedHost(for: try XCTUnwrap(URL(string: "https://www.google.com/search?q=bbc"))))
        XCTAssertNil(blocker.listedHost(for: try XCTUnwrap(URL(string: "https://example.com/"))))
    }
}

final class BrowserInputTests: XCTestCase {
    func testAddressesAndSearches() {
        XCTAssertEqual(BrowserInput.url(for: "example.com", searchEngine: .google)?.absoluteString, "https://example.com")
        XCTAssertEqual(BrowserInput.url(for: "http://example.com/a", searchEngine: .google)?.absoluteString, "http://example.com/a")
        XCTAssertEqual(BrowserInput.url(for: "bbc", searchEngine: .google)?.absoluteString, "https://www.google.com/search?q=bbc")
        XCTAssertEqual(BrowserInput.url(for: "bbc news & more", searchEngine: .google)?.absoluteString,
                       "https://www.google.com/search?q=bbc%20news%20%26%20more")
    }
}

final class FamilyDNSTests: XCTestCase {
    func testNullAddressMeansFiltered() {
        let filtered = #"{"Status":0,"Answer":[{"name":"x.com","type":1,"data":"0.0.0.0"}]}"#
        let open = #"{"Status":0,"Answer":[{"name":"example.com","type":1,"data":"93.184.215.14"}]}"#
        XCTAssertEqual(FamilyDNS.isFilteredAnswer(Data(filtered.utf8)), true)
        XCTAssertEqual(FamilyDNS.isFilteredAnswer(Data(open.utf8)), false)
        XCTAssertEqual(FamilyDNS.isFilteredAnswer(Data(#"{"Status":3}"#.utf8)), false)
    }
}
