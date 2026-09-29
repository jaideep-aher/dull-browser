import SwiftUI
import UIKit

struct BrowserBar: View {
    @ObservedObject var model: BrowserModel
    let tabCount: Int
    let showTabs: () -> Void
    let showSettings: () -> Void
    var showPanel: (Panel) -> Void = { _ in }

    @ObservedObject private var bookmarks = Bookmarks.shared
    @ObservedObject private var readLater = ReadLater.shared
    @State private var text = ""
    @FocusState private var editing: Bool

    var body: some View {
        VStack(spacing: 0) {
            GeometryReader { proxy in
                Rectangle()
                    .fill(Theme.ink)
                    .frame(width: proxy.size.width * model.progress, height: 2)
                    .opacity(model.isLoading ? 1 : 0)
            }
            .frame(height: 2)

            HStack(spacing: 2) {
                barButton("chevron.left", label: "Back", enabled: model.canStepBack) { model.goBack() }
                barButton("chevron.right", label: "Forward", enabled: model.canGoForward) { model.goForward() }

                if !model.showingNewTab {
                    TextField("Search or address", text: $text)
                        .focused($editing)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .keyboardType(.webSearch)
                        .submitLabel(.go)
                        .multilineTextAlignment(editing ? .leading : .center)
                        .onSubmit {
                            model.open(text)
                            editing = false
                        }
                        .font(.system(size: 15))
                        .foregroundStyle(Theme.ink)
                        .padding(.leading, 12)
                        .padding(.trailing, editing ? 12 : 34)
                        .frame(height: 38)
                        .background(RoundedRectangle(cornerRadius: 10).stroke(Theme.ink.opacity(0.25), lineWidth: 1))
                        .accessibilityIdentifier("addressField")
                        .overlay(alignment: .trailing) {
                            if !editing { pageMenu }
                        }

                    barButton(model.isLoading ? "xmark" : "arrow.clockwise",
                              label: model.isLoading ? "Stop" : "Reload",
                              enabled: model.blockedHost == nil) { model.reloadOrStop() }
                } else {
                    Spacer()
                }
                Button(action: showTabs) {
                    Text("\(tabCount)")
                        .font(.system(size: 13, weight: .semibold))
                        .frame(minWidth: 23, minHeight: 25)
                        .overlay(RoundedRectangle(cornerRadius: 5).stroke(lineWidth: 1.5))
                        .frame(width: 44, height: 44)
                }
                .accessibilityLabel("Tabs")
                .accessibilityValue("\(tabCount)")
                .accessibilityIdentifier("tabsButton")
                barButton("gearshape", label: "Settings", enabled: true, action: showSettings)
            }
            .padding(.horizontal, 6)
            .padding(.vertical, 6)
        }
        .background(Theme.paper)
        .overlay(alignment: .top) {
            if !model.showingNewTab {
                Rectangle().fill(Theme.hairline).frame(height: 0.5)
            }
        }
        .onAppear { text = model.addressForDisplay }
        .onChange(of: model.addressForDisplay) { _, value in
            if !editing { text = value }
        }
        .onChange(of: editing) { _, isEditing in
            text = isEditing ? model.addressForEditing : model.addressForDisplay
            if isEditing {
                DispatchQueue.main.async {
                    UIApplication.shared.sendAction(#selector(UIResponder.selectAll(_:)), to: nil, from: nil, for: nil)
                }
            }
        }
    }

    private var pageMenu: some View {
        Menu {
            if let page = model.pageURL {
                if Feature.bookmarks.isUnlocked {
                    let saved = bookmarks.contains(page)
                    Button {
                        bookmarks.toggle(page, title: model.title)
                    } label: {
                        Label(saved ? "Remove bookmark" : "Add bookmark", systemImage: saved ? "star.fill" : "star")
                    }
                    .accessibilityIdentifier("menuBookmark")
                }
                if Feature.readLater.isUnlocked {
                    Button {
                        readLater.add(page, title: model.title)
                    } label: {
                        Label(readLater.contains(page) ? "Saved for later" : "Save for later", systemImage: "clock")
                    }
                    .disabled(readLater.contains(page))
                    .accessibilityIdentifier("menuSaveForLater")
                }
                ShareLink(item: page) { Label("Share", systemImage: "square.and.arrow.up") }
                Divider()
            }
            if Feature.bookmarks.isUnlocked {
                Button { showPanel(.bookmarks) } label: { Label("Bookmarks", systemImage: "book") }
                    .accessibilityIdentifier("menuBookmarks")
            }
            if Feature.readLater.isUnlocked {
                Button { showPanel(.readLater) } label: { Label("Read later", systemImage: "tray") }
                    .accessibilityIdentifier("menuReadLater")
            }
            if Feature.stats.isUnlocked {
                Button { showPanel(.stats) } label: { Label("Stats", systemImage: "chart.bar") }
                    .accessibilityIdentifier("menuStats")
            }
        } label: {
            Image(systemName: bookmarks.contains(model.pageURL) ? "star.fill" : "ellipsis")
                .font(.system(size: 14, weight: .medium))
                .foregroundStyle(Theme.muted)
                .frame(width: 34, height: 38)
                .contentShape(Rectangle())
        }
        .accessibilityLabel("Page menu")
        .accessibilityIdentifier("pageMenu")
    }

    private func barButton(_ symbol: String, label: String, enabled: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: symbol)
                .font(.system(size: 17, weight: .regular))
                .frame(width: 44, height: 44)
        }
        .foregroundStyle(Theme.ink)
        .disabled(!enabled)
        .opacity(enabled ? 1 : 0.3)
        .accessibilityLabel(label)
    }
}
