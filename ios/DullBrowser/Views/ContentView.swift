import SwiftUI
import WebKit

enum Panel: String, Identifiable {
    case bookmarks, readLater, stats
    var id: String { rawValue }
}

struct ContentView: View {
    @StateObject private var session = BrowserSession(
        restore: !UserDefaults.standard.bool(forKey: "UITestResetSession") && !BrowserPreferences.startsFresh)
    @AppStorage("introSeen", store: BrowserPreferences.defaults) private var introSeen = false
    @AppStorage(Appearance.preferenceKey, store: BrowserPreferences.defaults) private var appearance = Appearance.light.rawValue
    @Environment(\.scenePhase) private var scenePhase
    @State private var showingTabs = false
    @State private var showingSettings = false
    @State private var panel: Panel?

    private var scheme: ColorScheme? { Appearance.resolve(appearance).colorScheme }

    var body: some View {
        ZStack {
            Theme.paper.ignoresSafeArea()
            BrowserPage(model: session.activeTab, tabCount: session.tabs.count,
                        showTabs: showTabs, showSettings: { showingSettings = true },
                        showPanel: { panel = $0 })
                .id(session.selectedID)
            if !introSeen {
                FirstRunView { introSeen = true }
                    .transition(.opacity)
            }
        }
        .sheet(isPresented: $showingTabs) {
            TabSwitcherView(session: session).preferredColorScheme(scheme)
        }
        .sheet(isPresented: $showingSettings) {
            SettingsView(session: session, open: { url in
                showingSettings = false
                session.activeTab.load(url)
            })
            .preferredColorScheme(scheme)
        }
        .sheet(item: $panel) { panel in
            PanelSheet(panel: panel) { url in
                self.panel = nil
                session.activeTab.load(url)
            }
            .preferredColorScheme(scheme)
        }
        .preferredColorScheme(scheme)
        .onOpenURL { session.activeTab.openIncoming($0) }
        .onChange(of: scenePhase) { _, phase in
            switch phase {
            case .active:
                FocusLifecycle.becameActive()
            case .background:
                TabThumbnails.shared.capture(session.activeTab)
                session.save()
            default:
                session.save()
            }
        }
        .onAppear { Appearance.resolve(appearance).apply() }
        .onChange(of: appearance) { _, value in Appearance.resolve(value).apply() }
        .task {
            session.pruneThumbnails()
            #if DEBUG
            if let address = UserDefaults.standard.string(forKey: "DebugOpen") { session.activeTab.open(address) }
            #endif
        }
        .animation(.easeOut(duration: 0.2), value: introSeen)
        .tint(Theme.ink)
    }

    private func showTabs() {
        TabThumbnails.shared.capture(session.activeTab)
        showingTabs = true
    }
}

/// Wiring for the comfort features that has to run at launch and when the app returns.
enum FocusLifecycle {
    @MainActor
    static func start() {
        PauseList.shared.onLoosened = { Stats.shared.recordLoosening() }
        becameActive()
    }

    @MainActor
    static func becameActive() {
        PauseList.shared.applyDueRemovals()
        Stats.shared.recordUse()
    }
}

private struct BrowserPage: View {
    @ObservedObject var model: BrowserModel
    let tabCount: Int
    let showTabs: () -> Void
    let showSettings: () -> Void
    let showPanel: (Panel) -> Void
    @AppStorage(BrowserPreferences.showClockKey, store: BrowserPreferences.defaults) private var showClock = true
    @AppStorage(BrowserPreferences.pageZoomKey, store: BrowserPreferences.defaults) private var pageZoom = 1.0

    var body: some View {
        ZStack {
            VStack(spacing: 0) {
                if model.showingNewTab {
                    StartPage(showClock: showClock, open: { model.load($0) }, submit: { model.open($0) })
                } else {
                    ZStack {
                        if let host = model.blockedHost {
                            BlockedView(host: host, site: model.blockedSite,
                                        goBack: { model.goBack() },
                                        openReadLater: { showPanel(.readLater) },
                                        startPage: { model.newTab() })
                        } else if let error = model.loadError {
                            LoadErrorView(message: error)
                        } else {
                            WebViewHost(webView: model.webView, pageZoom: pageZoom)
                                .id(ObjectIdentifier(model.webView))
                        }
                    }
                }
                BrowserBar(model: model, tabCount: tabCount, showTabs: showTabs, showSettings: showSettings,
                           showPanel: showPanel)
            }
            if let pause = model.pause {
                PauseView(request: pause, goBack: { model.leavePause() }, proceed: { model.continuePause() })
                    .transition(.opacity)
            }
        }
        .animation(.easeOut(duration: 0.2), value: model.pause)
    }
}

/// Reads the countdown, bookmark and milestone stores so the web page view does not have to.
private struct StartPage: View {
    let showClock: Bool
    let open: (URL) -> Void
    let submit: (String) -> Void
    @ObservedObject private var countdowns = Countdowns.shared
    @ObservedObject private var bookmarks = Bookmarks.shared
    @ObservedObject private var stats = Stats.shared

    var body: some View {
        let milestone = Feature.milestones.isUnlocked ? stats.data.noticeMilestone : nil
        NewTabView(showClock: showClock,
                   countdown: Feature.countdowns.isUnlocked ? countdowns.nextLabel : nil,
                   quickLinks: Feature.bookmarks.isUnlocked ? bookmarks.quickLinks : [],
                   milestone: milestone,
                   dismissMilestone: { stats.dismissMilestoneNotice() },
                   openLink: open,
                   onSubmit: submit)
            .onDisappear { if milestone != nil { stats.dismissMilestoneNotice() } }
    }
}

private struct PanelSheet: View {
    let panel: Panel
    let open: (URL) -> Void
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            Group {
                switch panel {
                case .bookmarks: BookmarksView(open: open)
                case .readLater: ReadLaterView(open: open)
                case .stats: StatsView()
                }
            }
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }.accessibilityIdentifier("panelDone")
                }
            }
        }
        .tint(Theme.ink)
    }
}

struct WebViewHost: UIViewRepresentable {
    let webView: WKWebView
    let pageZoom: Double

    func makeUIView(context: Context) -> WKWebView { webView }

    func updateUIView(_ uiView: WKWebView, context: Context) {
        if uiView.pageZoom != pageZoom { uiView.pageZoom = pageZoom }
    }
}
