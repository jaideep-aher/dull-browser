import UIKit
import WebKit
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

    func testGoogleIsDefaultAndKagiIsGone() {
        XCTAssertEqual(SearchEngine.resolve(nil), .google)
        XCTAssertEqual(SearchEngine.resolve("kagi"), .google)
        XCTAssertEqual(SearchEngine.allCases, [.google, .duckDuckGo, .bing])
        XCTAssertFalse(SearchEngine.allCases.contains { $0.searchPrefix.contains("kagi") })
    }
}

final class PreferenceMigrationTests: XCTestCase {
    private func defaults() -> UserDefaults {
        UserDefaults(suiteName: "DullBrowserTests.\(UUID())")!
    }

    func testSavedKagiBecomesGoogle() {
        let store = defaults()
        store.set("kagi", forKey: SearchEngine.preferenceKey)
        BrowserPreferences.migrate(store)
        XCTAssertEqual(store.string(forKey: SearchEngine.preferenceKey), "google")
    }

    func testValidChoicesAreKeptAndMissingOnesStayUnset() {
        let store = defaults()
        BrowserPreferences.migrate(store)
        XCTAssertNil(store.string(forKey: SearchEngine.preferenceKey))
        XCTAssertNil(store.string(forKey: Appearance.preferenceKey))
        store.set("bing", forKey: SearchEngine.preferenceKey)
        store.set("dark", forKey: Appearance.preferenceKey)
        store.set(1.3, forKey: BrowserPreferences.pageZoomKey)
        BrowserPreferences.migrate(store)
        XCTAssertEqual(store.string(forKey: SearchEngine.preferenceKey), "bing")
        XCTAssertEqual(store.string(forKey: Appearance.preferenceKey), "dark")
        XCTAssertEqual(store.double(forKey: BrowserPreferences.pageZoomKey), 1.3)
    }

    func testUnknownAppearanceAndZoomFallBackToDefaults() {
        let store = defaults()
        store.set("sepia", forKey: Appearance.preferenceKey)
        store.set(9.0, forKey: BrowserPreferences.pageZoomKey)
        BrowserPreferences.migrate(store)
        XCTAssertNil(store.object(forKey: Appearance.preferenceKey))
        XCTAssertNil(store.object(forKey: BrowserPreferences.pageZoomKey))
    }
}

final class AppearanceTests: XCTestCase {
    func testLightIsDefault() {
        XCTAssertEqual(Appearance.resolve(nil), .light)
        XCTAssertEqual(Appearance.resolve("nonsense"), .light)
        XCTAssertEqual(Appearance.allCases, [.light, .dark, .system])
    }

    func testEachChoiceMapsToWindowStyle() {
        XCTAssertEqual(Appearance.light.interfaceStyle, .light)
        XCTAssertEqual(Appearance.dark.interfaceStyle, .dark)
        XCTAssertEqual(Appearance.system.interfaceStyle, .unspecified)
    }

    func testLightIsWhiteAndDarkIsDark() {
        func white(_ color: UIColor, _ style: UIUserInterfaceStyle) -> CGFloat {
            var value: CGFloat = 0
            color.resolvedColor(with: UITraitCollection(userInterfaceStyle: style)).getWhite(&value, alpha: nil)
            return value
        }
        XCTAssertEqual(white(Theme.paperColor, .light), 1, accuracy: 0.001)
        XCTAssertLessThan(white(Theme.paperColor, .dark), 0.2)
        XCTAssertLessThan(white(Theme.inkColor, .light), 0.2)
        XCTAssertGreaterThan(white(Theme.inkColor, .dark), 0.8)
        XCTAssertLessThan(white(Theme.mutedColor, .light), white(Theme.paperColor, .light))
        XCTAssertGreaterThan(white(Theme.mutedColor, .dark), white(Theme.paperColor, .dark))
    }

    func testEachChoiceMapsToColorScheme() {
        XCTAssertEqual(Appearance.light.colorScheme, .light)
        XCTAssertEqual(Appearance.dark.colorScheme, .dark)
        XCTAssertNil(Appearance.system.colorScheme)
    }
}

@MainActor
final class PageSettingsTests: XCTestCase {
    private func defaults() -> UserDefaults {
        UserDefaults(suiteName: "DullBrowserTests.\(UUID())")!
    }

    func testDefaultsAreMobileWithJavaScript() {
        let prefs = BrowserPreferences.webpagePreferences(WKWebpagePreferences(), defaults: defaults())
        XCTAssertEqual(prefs.preferredContentMode, .mobile)
        XCTAssertTrue(prefs.allowsContentJavaScript)
        XCTAssertFalse(BrowserPreferences.flag(BrowserPreferences.startFreshKey, default: false, in: defaults()))
    }

    func testDesktopAndJavaScriptChoicesApply() {
        let store = defaults()
        store.set(true, forKey: BrowserPreferences.desktopSitesKey)
        store.set(false, forKey: BrowserPreferences.javaScriptKey)
        let prefs = BrowserPreferences.webpagePreferences(WKWebpagePreferences(), defaults: store)
        XCTAssertEqual(prefs.preferredContentMode, .desktop)
        XCTAssertFalse(prefs.allowsContentJavaScript)
    }

    func testNewWindowLinksOpenTabByDefaultOrStayWhenTurnedOff() {
        let model = BrowserModel()
        var opened: [URL] = []
        model.openInNewTab = { opened.append($0) }
        let target = URL(string: "https://example.com/new")!
        model.openNewWindowLink(target, defaults: defaults())
        XCTAssertEqual(opened, [target])
        XCTAssertTrue(model.showingNewTab)

        let store = defaults()
        store.set(false, forKey: BrowserPreferences.newWindowTabsKey)
        model.openNewWindowLink(target, defaults: store)
        XCTAssertEqual(opened, [target])
        XCTAssertFalse(model.showingNewTab)
        XCTAssertEqual(model.url, target)
        model.close()
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

    func testEveryWebViewHasPullToRefresh() {
        let model = BrowserModel()
        XCTAssertNotNil(model.webView.scrollView.refreshControl)
        model.newTab()
        XCTAssertNotNil(model.webView.scrollView.refreshControl)
        model.close()
    }

    func testReloadOnStartPageStaysOnStartPage() {
        let model = BrowserModel()
        model.reload()
        XCTAssertTrue(model.showingNewTab)
        XCTAssertNil(model.savedAddress)
        model.close()
    }

    func testReloadLoadsTabThatWasRestoredButNotYetOpened() {
        let model = BrowserModel(restoredURL: URL(string: "https://example.com/")!)
        XCTAssertTrue(model.showingNewTab)
        model.reload()
        XCTAssertFalse(model.showingNewTab)
        XCTAssertEqual(model.url?.absoluteString, "https://example.com/")
        model.close()
    }

    func testReloadAfterFailureRetriesRequestedAddress() {
        let model = BrowserModel()
        model.open("https://example.com/retry")
        model.webView.stopLoading()
        model.webView(model.webView, didFailProvisionalNavigation: nil,
                      withError: NSError(domain: NSURLErrorDomain, code: NSURLErrorNotConnectedToInternet))
        XCTAssertNotNil(model.loadError)
        model.reload()
        XCTAssertNil(model.loadError)
        XCTAssertEqual(model.url?.absoluteString, "https://example.com/retry")
        model.close()
    }

    func testClearingBrowsingDataLeavesOneFreshTab() async {
        let session = BrowserSession(defaults: UserDefaults(suiteName: "DullBrowserTests.\(UUID())")!, restore: false)
        session.activeTab.open("https://example.com/")
        session.addTab()
        await session.clearBrowsingData(in: .nonPersistent())
        XCTAssertEqual(session.tabs.count, 1)
        XCTAssertTrue(session.activeTab.showingNewTab)
        XCTAssertEqual(session.selectedID, session.activeTab.id)
        session.activeTab.close()
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
