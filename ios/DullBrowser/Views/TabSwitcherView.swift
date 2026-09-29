import SwiftUI

struct TabSwitcherView: View {
    @ObservedObject var session: BrowserSession
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            List {
                ForEach(session.tabs) { tab in
                    TabRow(tab: tab, selected: tab.id == session.selectedID) {
                        session.select(tab.id)
                        dismiss()
                    } close: {
                        session.close(tab.id)
                    }
                    .listRowBackground(Theme.paper)
                }
            }
            .scrollContentBackground(.hidden)
            .background(Theme.paper)
            .navigationTitle("Tabs (\(session.tabs.count))")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button {
                        session.addTab()
                        dismiss()
                    } label: { Label("New tab", systemImage: "plus") }
                    .accessibilityIdentifier("addTab")
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                        .accessibilityIdentifier("tabsDone")
                }
            }
        }
        .tint(Theme.ink)
    }
}

private struct TabRow: View {
    @ObservedObject var tab: BrowserModel
    let selected: Bool
    let select: () -> Void
    let close: () -> Void

    var body: some View {
        HStack(spacing: 12) {
            Button(action: select) {
                HStack {
                    Image(systemName: tab.blockedHost == nil ? "globe" : "lock.fill")
                    VStack(alignment: .leading, spacing: 4) {
                        Text(tab.tabTitle).font(.headline).lineLimit(1)
                        if let address = tab.savedAddress {
                            Text(address).font(.caption).foregroundStyle(Theme.muted).lineLimit(1)
                        }
                    }
                    Spacer()
                    if selected { Image(systemName: "checkmark") }
                }
                .foregroundStyle(Theme.ink)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("selectTab_\(tab.id)")
            .accessibilityLabel(tab.tabTitle)
            .accessibilityValue(selected ? "Selected" : "Not selected")

            Button(action: close) {
                Image(systemName: "xmark").frame(width: 44, height: 44)
            }
            .buttonStyle(.plain)
            .foregroundStyle(Theme.muted)
            .accessibilityIdentifier("closeTab_\(tab.id)")
            .accessibilityLabel("Close \(tab.tabTitle)")
        }
        .padding(.vertical, 6)
    }
}
