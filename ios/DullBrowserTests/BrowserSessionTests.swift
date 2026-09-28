import XCTest
@testable import DullBrowser

final class SearchEngineTests: XCTestCase {
    func testEveryEngineEncodesQueriesWithoutLosingCharacters() throws {
        let query = "C++ & café #1? x=y"
        for engine in SearchEngine.allCases {
            let url = try XCTUnwrap(BrowserInput.url(for: query, searchEngine: engine))
            let components = try XCTUnwrap(URLComponents(url: url, resolvingAgainstBaseURL: false))
            XCTAssertEqual(components.queryItems?.first(where: { $0.name == "q" })?.value, query)
            XCTAssertEqual(url.host, URL(string: engine.searchPrefix)?.host)
            XCTAssertNil(url.fragment)
        }
    }

    func testEmptyInputDoesNotNavigate() {
        XCTAssertNil(BrowserInput.url(for: ""))
        XCTAssertNil(BrowserInput.url(for: "  \n  "))
    }

    func testChangingEngineDoesNotChangeTypedAddressesOrBlocking() throws {
        for engine in SearchEngine.allCases {
            let url = try XCTUnwrap(BrowserInput.url(for: "youtube.com", searchEngine: engine))
            XCTAssertEqual(url.absoluteString, "https://youtube.com")
            XCTAssertEqual(SiteBlocker.shared.listedHost(for: url), "youtube.com")
        }
    }
}

@MainActor
final class BrowserSessionTests: XCTestCase {
    private func defaults() -> UserDefaults {
        UserDefaults(suiteName: "DullBrowserTests.\(UUID())")!
    }

    func testNewTabsKeepExistingWebViewsAndSelection() {
        let session = BrowserSession(defaults: defaults(), restore: false)
        let first = session.activeTab
        let firstView = first.webView
        session.addTab()
        XCTAssertEqual(session.tabs.count, 2)
        XCTAssertNotEqual(session.selectedID, first.id)
        session.select(first.id)
        XCTAssertTrue(session.activeTab === first)
        XCTAssertTrue(session.activeTab.webView === firstView)
        session.tabs.forEach { $0.close() }
    }

    func testClosingBackgroundTabKeepsSelection() {
        let session = BrowserSession(defaults: defaults(), restore: false)
        let firstID = session.selectedID
        session.addTab()
        let selected = session.selectedID
        session.close(firstID)
        XCTAssertEqual(session.selectedID, selected)
        XCTAssertEqual(session.tabs.count, 1)
        session.activeTab.close()
    }

    func testClosingSelectedAndLastTabAlwaysLeavesUsableTab() {
        let session = BrowserSession(defaults: defaults(), restore: false)
        let firstID = session.selectedID
        session.addTab()
        session.close(session.selectedID)
        XCTAssertEqual(session.selectedID, firstID)
        session.close(firstID)
        XCTAssertEqual(session.tabs.count, 1)
        XCTAssertNotEqual(session.selectedID, firstID)
        XCTAssertTrue(session.activeTab.showingNewTab)
        session.activeTab.close()
    }

    func testSessionRestoresTabsAndSelectionWithoutLoadingBackgroundTabs() {
        let store = defaults()
        let session = BrowserSession(defaults: store, restore: false)
        session.activeTab.open("https://example.com/")
        let first = session.selectedID
        session.addTab()
        let selected = session.selectedID
        session.save()
        session.tabs.forEach { $0.close() }
        let restored = BrowserSession(defaults: store)
        XCTAssertEqual(restored.tabs.map(\.id), [first, selected])
        XCTAssertEqual(restored.selectedID, selected)
        XCTAssertEqual(restored.tabs[0].savedAddress, "https://example.com/")
        XCTAssertNil(restored.tabs[0].webView.url)
        XCTAssertTrue(restored.activeTab.showingNewTab)
        restored.tabs.forEach { $0.close() }
    }

    func testInvalidSelectionAndCloseDoNotLoseTabs() {
        let session = BrowserSession(defaults: defaults(), restore: false)
        let selected = session.selectedID
        session.select(UUID())
        session.close(UUID())
        XCTAssertEqual(session.selectedID, selected)
        XCTAssertEqual(session.tabs.count, 1)
        session.activeTab.close()
    }

    func testEmptySubmissionKeepsStartPage() {
        let model = BrowserModel()
        model.open("   ")
        XCTAssertTrue(model.showingNewTab)
        XCTAssertNil(model.savedAddress)
        model.close()
    }
}

@MainActor
final class BrowserRecoveryTests: XCTestCase {
    func testUnsupportedURLDisplaysErrorInsteadOfBlankPage() {
        let model = BrowserModel()
        model.webView(model.webView, didFailProvisionalNavigation: nil,
                      withError: NSError(domain: "WebKitErrorDomain", code: 101,
                                         userInfo: [NSLocalizedDescriptionKey: "Unsupported URL"]))
        XCTAssertEqual(model.loadError, "Unsupported URL")
        model.close()
    }

    func testCancelledAndPolicyStoppedLoadsDoNotDisplayErrors() {
        let model = BrowserModel()
        for error in [NSError(domain: NSURLErrorDomain, code: NSURLErrorCancelled),
                      NSError(domain: "WebKitErrorDomain", code: 102)] {
            model.webView(model.webView, didFailProvisionalNavigation: nil, withError: error)
            XCTAssertNil(model.loadError)
        }
        model.close()
    }

    func testLateFailureFromDiscardedWebViewDoesNotAffectNewPage() {
        let model = BrowserModel()
        let previous = model.webView
        model.newTab()
        model.webView(previous, didFailProvisionalNavigation: nil,
                      withError: NSError(domain: NSURLErrorDomain, code: NSURLErrorNotConnectedToInternet))
        XCTAssertTrue(model.showingNewTab)
        XCTAssertNil(model.loadError)
        model.close()
    }
}
