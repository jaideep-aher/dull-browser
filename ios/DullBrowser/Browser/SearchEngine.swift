import Foundation

enum BrowserPreferences {
    static var defaults: UserDefaults {
        #if DEBUG
        if ProcessInfo.processInfo.arguments.contains("-UITesting") {
            return UserDefaults(suiteName: "app.slate.browser.ios.ui-testing")!
        }
        #endif
        return .standard
    }
}

enum SearchEngine: String, CaseIterable, Identifiable {
    case kagi, google, duckDuckGo, bing

    static let preferenceKey = "searchEngine"
    var id: String { rawValue }

    static var current: SearchEngine {
        SearchEngine(rawValue: BrowserPreferences.defaults.string(forKey: preferenceKey) ?? "") ?? .kagi
    }

    var name: String {
        switch self {
        case .kagi: "Kagi"
        case .google: "Google"
        case .duckDuckGo: "DuckDuckGo"
        case .bing: "Bing"
        }
    }

    var searchPrefix: String {
        switch self {
        case .kagi: "https://kagi.com/search?q="
        case .google: "https://www.google.com/search?q="
        case .duckDuckGo: "https://duckduckgo.com/?q="
        case .bing: "https://www.bing.com/search?q="
        }
    }
}
