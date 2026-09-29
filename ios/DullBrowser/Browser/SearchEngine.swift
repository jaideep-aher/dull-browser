import Foundation
import WebKit

enum BrowserPreferences {
    static var defaults: UserDefaults {
        #if DEBUG
        if ProcessInfo.processInfo.arguments.contains("-UITesting") {
            return UserDefaults(suiteName: uiTestingSuite)!
        }
        #endif
        return .standard
    }

    #if DEBUG
    private static let uiTestingSuite = "app.slate.browser.ios.ui-testing"

    /// UI tests start from default settings so one test's choices cannot leak into the next.
    static func resetForUITestingIfRequested() {
        guard ProcessInfo.processInfo.arguments.contains("-UITesting"),
              UserDefaults.standard.bool(forKey: "UITestResetPreferences") else { return }
        UserDefaults.standard.removePersistentDomain(forName: uiTestingSuite)
    }
    #endif

    static let showClockKey = "showClock"
    static let pageZoomKey = "pageZoom"
    static let pageZoomOptions: [Double] = [0.85, 1, 1.15, 1.3, 1.5]
    static let desktopSitesKey = "requestDesktopSites"
    static let javaScriptKey = "javaScriptEnabled"
    static let newWindowTabsKey = "newWindowLinksOpenTabs"
    static let startFreshKey = "startFreshOnLaunch"

    static func flag(_ key: String, default value: Bool, in defaults: UserDefaults = defaults) -> Bool {
        defaults.object(forKey: key) == nil ? value : defaults.bool(forKey: key)
    }

    /// Previous tabs, cookies and site data are dropped when the app process starts again.
    static var startsFresh: Bool { flag(startFreshKey, default: false) }

    /// Per-navigation page settings. Blocking does not depend on page scripts, so turning
    /// JavaScript off or asking for desktop layouts cannot reopen a listed site.
    @MainActor
    static func webpagePreferences(_ base: WKWebpagePreferences, defaults: UserDefaults = defaults) -> WKWebpagePreferences {
        base.preferredContentMode = flag(desktopSitesKey, default: false, in: defaults) ? .desktop : .mobile
        base.allowsContentJavaScript = flag(javaScriptKey, default: true, in: defaults)
        return base
    }

    /// Rewrites stored choices that are no longer offered, so an old engine becomes Google.
    static func migrate(_ defaults: UserDefaults = defaults) {
        if let engine = defaults.string(forKey: SearchEngine.preferenceKey), SearchEngine(rawValue: engine) == nil {
            defaults.set(SearchEngine.google.rawValue, forKey: SearchEngine.preferenceKey)
        }
        if let appearance = defaults.string(forKey: Appearance.preferenceKey), Appearance(rawValue: appearance) == nil {
            defaults.removeObject(forKey: Appearance.preferenceKey)
        }
        if defaults.object(forKey: pageZoomKey) != nil, !pageZoomOptions.contains(defaults.double(forKey: pageZoomKey)) {
            defaults.removeObject(forKey: pageZoomKey)
        }
    }
}

enum SearchEngine: String, CaseIterable, Identifiable {
    case google, duckDuckGo, bing

    static let preferenceKey = "searchEngine"
    var id: String { rawValue }

    static var current: SearchEngine { resolve(BrowserPreferences.defaults.string(forKey: preferenceKey)) }

    static func resolve(_ stored: String?) -> SearchEngine {
        stored.flatMap(SearchEngine.init(rawValue:)) ?? .google
    }

    var name: String {
        switch self {
        case .google: "Google"
        case .duckDuckGo: "DuckDuckGo"
        case .bing: "Bing"
        }
    }

    var searchPrefix: String {
        switch self {
        case .google: "https://www.google.com/search?q="
        case .duckDuckGo: "https://duckduckgo.com/?q="
        case .bing: "https://www.bing.com/search?q="
        }
    }
}
