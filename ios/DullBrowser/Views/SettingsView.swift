import SwiftUI

struct SettingsView: View {
    @Environment(\.dismiss) private var dismiss
    @AppStorage(SearchEngine.preferenceKey, store: BrowserPreferences.defaults) private var engine = SearchEngine.kagi.rawValue

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
                                if engine == option.rawValue {
                                    Image(systemName: "checkmark")
                                }
                            }
                            .foregroundStyle(Theme.ink)
                        }
                        .accessibilityIdentifier("searchEngine_\(option.rawValue)")
                        .accessibilityValue(engine == option.rawValue ? "Selected" : "Not selected")
                    }
                } header: {
                    Text("Default search engine")
                } footer: {
                    Text("Used for searches from the start page and address bar. Kagi requires an account.")
                }
                Section("Site blocking") {
                    Label("Always on", systemImage: "lock.fill")
                    Text("YouTube, social media, porn, and other listed sites stay blocked. There is no setting to allow them, even when you change search engines.")
                        .foregroundStyle(Theme.muted)
                }
            }
            .scrollContentBackground(.hidden)
            .background(Theme.paper)
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
