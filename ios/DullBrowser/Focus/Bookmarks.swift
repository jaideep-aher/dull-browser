import Foundation

struct Bookmark: Codable, Equatable, Identifiable {
    var id = UUID()
    var title: String
    var url: URL
    var added = Date()
}

/// A flat list. A bookmark to a blocked site can be kept, but opening it still shows the closed page.
@MainActor
final class Bookmarks: ObservableObject {
    static let shared = Bookmarks()
    static let storageKey = "bookmarks.v1"
    static let quickLinksKey = "bookmarks.quickLinks"
    static let maxQuickLinks = 8
    static let defaultQuickLinks = 4

    @Published private(set) var items: [Bookmark]
    @Published var quickLinkCount: Int {
        didSet {
            let clamped = min(max(quickLinkCount, 0), Self.maxQuickLinks)
            if clamped != quickLinkCount { quickLinkCount = clamped }
            defaults.set(clamped, forKey: Self.quickLinksKey)
        }
    }

    private let defaults: UserDefaults

    init(defaults: UserDefaults = BrowserPreferences.defaults) {
        self.defaults = defaults
        items = StoredJSON.load([Bookmark].self, key: Self.storageKey, from: defaults) ?? []
        let stored = defaults.object(forKey: Self.quickLinksKey) as? Int ?? Self.defaultQuickLinks
        quickLinkCount = min(max(stored, 0), Self.maxQuickLinks)
    }

    var quickLinks: [Bookmark] { Array(items.prefix(quickLinkCount)) }

    func contains(_ url: URL?) -> Bool { bookmark(for: url) != nil }

    func bookmark(for url: URL?) -> Bookmark? {
        guard let url else { return nil }
        let key = Self.key(url)
        return items.first { Self.key($0.url) == key }
    }

    func add(_ url: URL, title: String) {
        guard !contains(url), let scheme = url.scheme?.lowercased(), scheme == "http" || scheme == "https" else { return }
        items.append(Bookmark(title: Self.title(title, for: url), url: url))
        save()
    }

    func toggle(_ url: URL, title: String) {
        if let existing = bookmark(for: url) { remove(existing.id) } else { add(url, title: title) }
    }

    /// Returns false when the new address is not a web address.
    @discardableResult
    func update(_ id: UUID, title: String, address: String) -> Bool {
        guard let index = items.firstIndex(where: { $0.id == id }) else { return false }
        guard let url = BrowserInput.webAddress(address) else { return false }
        items[index].url = url
        items[index].title = Self.title(title, for: url)
        save()
        return true
    }

    func remove(_ id: UUID) {
        items.removeAll { $0.id == id }
        save()
    }

    func move(from source: IndexSet, to destination: Int) {
        items.move(fromOffsets: source, toOffset: destination)
        save()
    }

    static func title(_ title: String, for url: URL) -> String {
        let trimmed = title.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? (url.host ?? url.absoluteString) : String(trimmed.prefix(120))
    }

    private static func key(_ url: URL) -> String {
        var text = url.absoluteString.lowercased()
        if text.hasSuffix("/") { text.removeLast() }
        return text
    }

    private func save() {
        StoredJSON.save(items, key: Self.storageKey, to: defaults)
    }
}
