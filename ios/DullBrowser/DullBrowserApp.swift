import SwiftUI
import WebKit

@main
struct DullBrowserApp: App {
    init() {
        #if DEBUG
        BrowserPreferences.resetForUITestingIfRequested()
        #endif
        BrowserPreferences.migrate()
        FocusLifecycle.start()
        if BrowserPreferences.startsFresh {
            Task { @MainActor in
                await WKWebsiteDataStore.default().removeData(
                    ofTypes: WKWebsiteDataStore.allWebsiteDataTypes(), modifiedSince: .distantPast)
            }
        }
        // Read the list off the main thread before the first navigation needs it.
        Task.detached(priority: .userInitiated) { _ = SiteBlocker.shared }
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
