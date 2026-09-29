import SwiftUI

struct SettingsView: View {
    @ObservedObject var session: BrowserSession
    var open: (URL) -> Void = { _ in }
    @Environment(\.dismiss) private var dismiss
    @AppStorage(BrowserPreferences.blockedNoteKey, store: BrowserPreferences.defaults) private var blockedNote = ""
    @AppStorage(SearchEngine.preferenceKey, store: BrowserPreferences.defaults) private var engine = SearchEngine.google.rawValue
    @AppStorage(Appearance.preferenceKey, store: BrowserPreferences.defaults) private var appearance = Appearance.light.rawValue
    @AppStorage(BrowserPreferences.showClockKey, store: BrowserPreferences.defaults) private var showClock = true
    @AppStorage(BrowserPreferences.pageZoomKey, store: BrowserPreferences.defaults) private var pageZoom = 1.0
    @AppStorage(BrowserPreferences.desktopSitesKey, store: BrowserPreferences.defaults) private var desktopSites = false
    @AppStorage(BrowserPreferences.javaScriptKey, store: BrowserPreferences.defaults) private var javaScript = true
    @AppStorage(BrowserPreferences.newWindowTabsKey, store: BrowserPreferences.defaults) private var newWindowTabs = true
    @AppStorage(BrowserPreferences.startFreshKey, store: BrowserPreferences.defaults) private var startFresh = false
    @State private var addedCount = SiteBlocker.shared.addedDomains.count
    @State private var confirmingClear = false
    @State private var cleared = false

    private var version: String {
        let info = Bundle.main.infoDictionary
        let short = info?["CFBundleShortVersionString"] as? String ?? "1.0"
        let build = info?["CFBundleVersion"] as? String ?? "1"
        return "\(short) (\(build))"
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    ForEach(SearchEngine.allCases) { option in
                        Button {
                            engine = option.rawValue
                        } label: {
                            HStack {
                                Text(option.name)
                                Spacer()
                                if SearchEngine.resolve(engine) == option {
                                    Image(systemName: "checkmark")
                                }
                            }
                            .foregroundStyle(Theme.ink)
                        }
                        .accessibilityIdentifier("searchEngine_\(option.rawValue)")
                        .accessibilityValue(SearchEngine.resolve(engine) == option ? "Selected" : "Not selected")
                    }
                } header: {
                    Text("Default search engine")
                } footer: {
                    Text("Used for searches from the start page and address bar.")
                }

                Section {
                    Picker("Appearance", selection: $appearance) {
                        ForEach(Appearance.allCases) { Text($0.name).tag($0.rawValue) }
                    }
                    .pickerStyle(.segmented)
                    .accessibilityIdentifier("appearance")
                } header: {
                    Text("Appearance")
                } footer: {
                    Text("Web pages that offer a dark version follow this setting.")
                }

                Section {
                    Toggle("Show clock on new tab", isOn: $showClock)
                        .accessibilityIdentifier("showClock")
                    Picker("Text size", selection: $pageZoom) {
                        ForEach(BrowserPreferences.pageZoomOptions, id: \.self) {
                            Text($0.formatted(.percent.precision(.fractionLength(0)))).tag($0)
                        }
                    }
                    .accessibilityIdentifier("textSize")
                    Toggle("Request desktop sites", isOn: $desktopSites)
                        .accessibilityIdentifier("desktopSites")
                    Toggle("JavaScript", isOn: $javaScript)
                        .accessibilityIdentifier("javaScript")
                    Toggle("Open new-window links in a new tab", isOn: $newWindowTabs)
                        .accessibilityIdentifier("newWindowTabs")
                } header: {
                    Text("Pages")
                } footer: {
                    Text("Page changes apply to pages you open or reload. Some sites need JavaScript to work.")
                }

                Section {
                    Toggle("Start fresh each launch", isOn: $startFresh)
                        .accessibilityIdentifier("startFresh")
                    Button(cleared ? "Browsing data cleared" : "Clear browsing data", role: .destructive) {
                        confirmingClear = true
                    }
                    .disabled(cleared)
                    .accessibilityIdentifier("clearBrowsingData")
                    .confirmationDialog("Clear browsing data?", isPresented: $confirmingClear, titleVisibility: .visible) {
                        Button("Clear and close all tabs", role: .destructive) {
                            Task {
                                await session.clearBrowsingData()
                                cleared = true
                            }
                        }
                        .accessibilityIdentifier("confirmClearBrowsingData")
                    } message: {
                        Text("Closes all tabs and removes cookies, cache, and site data.")
                    }
                } header: {
                    Text("Privacy")
                } footer: {
                    Text("Starting fresh closes the previous tabs and removes cookies and site data each time Dull opens again after being closed.")
                }

                Section("Site blocking") {
                    Label("Always on", systemImage: "lock.fill")
                    LabeledContent("Sites on the list", value: SiteBlocker.shared.matcher.domains.count.formatted())
                        .accessibilityIdentifier("blockedSiteCount")
                    Text("YouTube, social media, porn, and other listed sites stay blocked. There is no setting to allow them, with any search engine or setting here.")
                        .foregroundStyle(Theme.muted)
                    if Feature.customBlocklist.isUnlocked {
                        NavigationLink {
                            AddedSitesView()
                        } label: {
                            LabeledContent("Sites you added", value: addedCount.formatted())
                        }
                        .accessibilityIdentifier("addedSitesLink")
                        .onReceive(NotificationCenter.default.publisher(for: .blocklistGrew).receive(on: RunLoop.main)) { _ in
                            addedCount = SiteBlocker.shared.addedDomains.count
                        }
                    }
                    if Feature.blockedPageNote.isUnlocked {
                        TextField("A note to yourself on the blocked page", text: $blockedNote, axis: .vertical)
                            .lineLimit(1...4)
                            .accessibilityIdentifier("blockedNoteField")
                    }
                }

                Section {
                    if Feature.mindfulPause.isUnlocked {
                        NavigationLink("Pause before sites") { PauseSettingsView() }
                            .accessibilityIdentifier("pauseSitesLink")
                    }
                    if Feature.countdowns.isUnlocked {
                        NavigationLink("Countdowns") { CountdownsView() }
                            .accessibilityIdentifier("countdownsLink")
                    }
                } header: {
                    Text("Focus")
                } footer: {
                    Text("A pause asks before shopping, sports and gossip sites open. Countdowns show on the start page.")
                }

                Section("Library") {
                    if Feature.bookmarks.isUnlocked {
                        NavigationLink("Bookmarks") { BookmarksView(open: open) }
                            .accessibilityIdentifier("bookmarksLink")
                    }
                    if Feature.readLater.isUnlocked {
                        NavigationLink("Read later") { ReadLaterView(open: open) }
                            .accessibilityIdentifier("readLaterLink")
                    }
                    if Feature.stats.isUnlocked {
                        NavigationLink("Stats") { StatsView() }
                            .accessibilityIdentifier("statsLink")
                    }
                }

                Section {
                    Label("Handled by iOS", systemImage: "key")
                    Text("Saved passwords and passkeys stay in the iOS Passwords app or your password manager. Tap the key above the keyboard on a sign-in form to fill them. Dull never sees or stores them.")
                        .foregroundStyle(Theme.muted)
                    Button("Open AutoFill settings") { PasswordSettings.open() }
                        .accessibilityIdentifier("passwordSettings")
                } header: {
                    Text("Passwords")
                }

                Section("About") {
                    LabeledContent("Version", value: version)
                }
            }
            .navigationTitle("Settings")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                        .accessibilityIdentifier("settingsDone")
                }
            }
        }
        .tint(Theme.ink)
    }
}
