import SwiftUI

@main
struct DullBrowserApp: App {
    init() {
        // Read the list off the main thread before the first navigation needs it.
        Task.detached(priority: .userInitiated) { _ = SiteBlocker.shared }
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
