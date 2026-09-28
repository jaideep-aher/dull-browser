import SwiftUI
import WebKit

struct ContentView: View {
    @StateObject private var session = BrowserSession(restore: !UserDefaults.standard.bool(forKey: "UITestResetSession"))
    @AppStorage("introSeen", store: BrowserPreferences.defaults) private var introSeen = false
    @Environment(\.scenePhase) private var scenePhase
    @State private var showingTabs = false
    @State private var showingSettings = false

    var body: some View {
        ZStack {
            Theme.paper.ignoresSafeArea()
            BrowserPage(model: session.activeTab, tabCount: session.tabs.count,
                        showTabs: { showingTabs = true }, showSettings: { showingSettings = true })
                .id(session.selectedID)
            if !introSeen {
                FirstRunView { introSeen = true }
                    .transition(.opacity)
            }
        }
        .sheet(isPresented: $showingTabs) { TabSwitcherView(session: session) }
        .sheet(isPresented: $showingSettings) { SettingsView() }
        .onOpenURL { session.activeTab.openIncoming($0) }
        .onChange(of: scenePhase) { _, phase in
            if phase != .active { session.save() }
        }
        .task {
            #if DEBUG
            if let address = UserDefaults.standard.string(forKey: "DebugOpen") { session.activeTab.open(address) }
            #endif
        }
        .animation(.easeOut(duration: 0.2), value: introSeen)
        .preferredColorScheme(.light)
        .tint(Theme.ink)
    }
}

private struct BrowserPage: View {
    @ObservedObject var model: BrowserModel
    let tabCount: Int
    let showTabs: () -> Void
    let showSettings: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            if model.showingNewTab {
                NewTabView { model.open($0) }
            } else {
                ZStack {
                    if let host = model.blockedHost {
                        BlockedView(host: host)
                    } else if let error = model.loadError {
                        LoadErrorView(message: error)
                    } else {
                        WebViewHost(webView: model.webView)
                            .id(ObjectIdentifier(model.webView))
                    }
                }
            }
            BrowserBar(model: model, tabCount: tabCount, showTabs: showTabs, showSettings: showSettings)
        }
    }
}

struct WebViewHost: UIViewRepresentable {
    let webView: WKWebView

    func makeUIView(context: Context) -> WKWebView { webView }

    func updateUIView(_ uiView: WKWebView, context: Context) {}
}
