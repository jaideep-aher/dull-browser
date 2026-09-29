import SwiftUI

struct TabSwitcherView: View {
    @ObservedObject var session: BrowserSession
    @ObservedObject private var thumbnails = TabThumbnails.shared
    @Environment(\.dismiss) private var dismiss

    private let columns = [GridItem(.flexible(), spacing: 14), GridItem(.flexible(), spacing: 14)]

    var body: some View {
        NavigationStack {
            ScrollView {
                LazyVGrid(columns: columns, spacing: 18) {
                    ForEach(session.tabs) { tab in
                        TabCard(tab: tab, selected: tab.id == session.selectedID,
                                image: Feature.tabThumbnails.isUnlocked ? thumbnails.images[tab.id] : nil) {
                            session.select(tab.id)
                            dismiss()
                        } close: {
                            session.close(tab.id)
                        }
                        .onAppear { if Feature.tabThumbnails.isUnlocked { thumbnails.loadIfNeeded(tab.id) } }
                    }
                }
                .padding(16)
            }
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

/// A preview when one was taken on leaving the tab, otherwise a plain card with the title.
private struct TabCard: View {
    @ObservedObject var tab: BrowserModel
    let selected: Bool
    let image: UIImage?
    let select: () -> Void
    let close: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            Color.clear
                .aspectRatio(1 / TabThumbnails.aspect, contentMode: .fit)
                .overlay { preview }
                .clipped()
            Rectangle().fill(Theme.hairline).frame(height: 0.5)
            Text(tab.tabTitle)
                .font(.system(size: 13, weight: .medium))
                .foregroundStyle(Theme.ink)
                .lineLimit(1)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 10)
                .padding(.trailing, 28)
                .frame(height: 34)
                .accessibilityHidden(true)
        }
        .background(Theme.paper)
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .overlay(RoundedRectangle(cornerRadius: 12).stroke(selected ? Theme.ink : Theme.hairline, lineWidth: selected ? 2 : 1))
        .overlay {
            Button(action: select) { Color.clear.contentShape(Rectangle()) }
                .buttonStyle(.plain)
                .accessibilityIdentifier("selectTab_\(tab.id)")
                .accessibilityLabel(tab.tabTitle)
                .accessibilityValue(selected ? "Selected" : "Not selected")
        }
        .overlay(alignment: .bottomTrailing) {
            Button(action: close) {
                Image(systemName: "xmark")
                    .font(.system(size: 12, weight: .semibold))
                    .frame(width: 34, height: 34)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .foregroundStyle(Theme.muted)
            .accessibilityIdentifier("closeTab_\(tab.id)")
            .accessibilityLabel("Close \(tab.tabTitle)")
        }
    }

    @ViewBuilder private var preview: some View {
        if tab.blockedHost != nil {
            placeholder { ClosedMark().stroke(Theme.muted, lineWidth: 2).frame(width: 36, height: 36) }
        } else if tab.pause != nil {
            placeholder { Image(systemName: "hourglass").font(.system(size: 24, weight: .light)) }
        } else if tab.showingNewTab, tab.savedAddress == nil {
            placeholder { Text("New tab").font(.system(size: 13)) }
        } else if let image {
            Image(uiImage: image)
                .resizable()
                .scaledToFill()
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
                .allowsHitTesting(false)
                .accessibilityLabel("Preview of \(tab.tabTitle)")
                .accessibilityIdentifier("tabThumbnail_\(tab.id)")
        } else {
            placeholder {
                VStack(spacing: 8) {
                    Image(systemName: "globe").font(.system(size: 22, weight: .light))
                    Text(tab.savedAddress.flatMap { URL(string: $0)?.host } ?? "")
                        .font(.system(size: 11))
                        .lineLimit(1)
                        .padding(.horizontal, 8)
                }
            }
        }
    }

    private func placeholder(@ViewBuilder _ content: () -> some View) -> some View {
        content()
            .foregroundStyle(Theme.muted)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(Theme.muted.opacity(0.06))
            .accessibilityHidden(true)
    }
}
