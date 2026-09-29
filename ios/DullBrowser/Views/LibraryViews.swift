import SwiftUI

struct BookmarksView: View {
    let open: (URL) -> Void
    @ObservedObject private var bookmarks = Bookmarks.shared
    @State private var editing: Bookmark?
    @State private var adding = false

    var body: some View {
        List {
            if bookmarks.items.isEmpty {
                Text("Add a bookmark from the page menu next to the address, or here.")
                    .foregroundStyle(Theme.muted)
            }
            Section {
                ForEach(bookmarks.items) { bookmark in
                    Button { open(bookmark.url) } label: {
                        VStack(alignment: .leading, spacing: 3) {
                            Text(bookmark.title).foregroundStyle(Theme.ink).lineLimit(1)
                            Text(bookmark.url.absoluteString).font(.caption).foregroundStyle(Theme.muted).lineLimit(1)
                        }
                    }
                    .accessibilityIdentifier("bookmark_\(bookmark.title)")
                    .swipeActions(edge: .trailing) {
                        Button("Delete", role: .destructive) { bookmarks.remove(bookmark.id) }
                        Button("Edit") { editing = bookmark }
                    }
                    .contextMenu {
                        Button("Edit") { editing = bookmark }
                        Button("Delete", role: .destructive) { bookmarks.remove(bookmark.id) }
                    }
                }
                .onMove(perform: bookmarks.move)
            }
            Section {
                Stepper(value: $bookmarks.quickLinkCount, in: 0...Bookmarks.maxQuickLinks) {
                    LabeledContent("On the start page", value: bookmarks.quickLinkCount.formatted())
                }
                .accessibilityIdentifier("quickLinkCount")
            } footer: {
                Text("The first bookmarks show on the start page. A bookmark to a blocked site still opens the closed page.")
            }
        }
        .navigationTitle("Bookmarks")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarLeading) {
                Button { adding = true } label: { Label("Add bookmark", systemImage: "plus") }
                    .accessibilityIdentifier("addBookmark")
            }
        }
        .sheet(item: $editing) { bookmark in
            BookmarkEditor(title: bookmark.title, address: bookmark.url.absoluteString) { title, address in
                bookmarks.update(bookmark.id, title: title, address: address)
            }
        }
        .sheet(isPresented: $adding) {
            BookmarkEditor(title: "", address: "") { title, address in
                guard let url = BrowserInput.webAddress(address) else { return false }
                bookmarks.add(url, title: title)
                return true
            }
        }
    }
}

private struct BookmarkEditor: View {
    @State var title: String
    @State var address: String
    let save: (String, String) -> Bool
    @Environment(\.dismiss) private var dismiss
    @State private var invalid = false

    var body: some View {
        NavigationStack {
            Form {
                TextField("Title", text: $title).accessibilityIdentifier("bookmarkTitle")
                TextField("Address", text: $address)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .keyboardType(.URL)
                    .accessibilityIdentifier("bookmarkAddress")
                if invalid { Text("Enter a web address like example.com.").foregroundStyle(Theme.muted) }
            }
            .navigationTitle("Bookmark")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") { if save(title, address) { dismiss() } else { invalid = true } }
                        .accessibilityIdentifier("bookmarkSave")
                }
            }
        }
        .tint(Theme.ink)
    }
}

struct ReadLaterView: View {
    let open: (URL) -> Void
    @ObservedObject private var readLater = ReadLater.shared

    var body: some View {
        TimelineView(.everyMinute) { context in
            let isOpen = readLater.window.isOpen(at: context.date)
            List {
                if readLater.items.isEmpty {
                    Text("Save a page or a link for later from the page menu or by pressing on a link.")
                        .foregroundStyle(Theme.muted)
                }
                if !isOpen {
                    Text(ReadingWindow.waitLabel(readLater.window.timeUntilOpen(from: context.date)))
                        .foregroundStyle(Theme.muted)
                        .accessibilityIdentifier("readLaterLocked")
                }
                if !readLater.unread.isEmpty {
                    Section("To read") {
                        ForEach(readLater.unread) { item in row(item, isOpen: isOpen) }
                    }
                }
                if !readLater.read.isEmpty {
                    Section("Read") {
                        ForEach(readLater.read) { item in row(item, isOpen: isOpen) }
                    }
                }
                if Feature.readingWindow.isUnlocked {
                    Section {
                        Toggle("Only open during reading time", isOn: $readLater.window.enabled)
                            .accessibilityIdentifier("readingWindowEnabled")
                        if readLater.window.enabled {
                            Picker("From", selection: $readLater.window.start) { hourOptions }
                            Picker("Until", selection: $readLater.window.end) { hourOptions }
                        }
                    } footer: {
                        Text("Outside reading time, saved pages stay locked. Blocked and paused sites follow their usual rules when opened.")
                    }
                }
            }
        }
        .navigationTitle("Read later")
        .navigationBarTitleDisplayMode(.inline)
    }

    private var hourOptions: some View {
        ForEach(Array(stride(from: 0, to: 24 * 60, by: 30)), id: \.self) { minutes in
            Text(ReadingWindow.label(minutes: minutes)).tag(minutes)
        }
    }

    private func row(_ item: ReadLaterItem, isOpen: Bool) -> some View {
        Button {
            guard isOpen else { return }
            readLater.setRead(item.id, true)
            open(item.url)
        } label: {
            HStack {
                VStack(alignment: .leading, spacing: 3) {
                    Text(item.title).foregroundStyle(isOpen ? Theme.ink : Theme.muted).lineLimit(2)
                    Text(item.url.host ?? item.url.absoluteString).font(.caption).foregroundStyle(Theme.muted)
                }
                Spacer()
                if !isOpen { Image(systemName: "lock").foregroundStyle(Theme.muted) }
            }
        }
        .accessibilityIdentifier("readLater_\(item.url.absoluteString)")
        .accessibilityValue(isOpen ? "" : "Locked")
        .swipeActions(edge: .leading) {
            Button(item.readAt == nil ? "Mark read" : "Mark unread") {
                readLater.setRead(item.id, item.readAt == nil)
            }
        }
        .swipeActions(edge: .trailing) {
            Button("Remove", role: .destructive) { readLater.remove(item.id) }
        }
    }
}
