import Foundation

struct ReadLaterItem: Codable, Equatable, Identifiable {
    var id = UUID()
    var title: String
    var url: URL
    var added = Date()
    var readAt: Date?
}

/// Optional hours when saved pages can be opened, as minutes after local midnight.
/// A window whose end is before its start runs past midnight.
struct ReadingWindow: Codable, Equatable {
    var enabled = false
    var start = 20 * 60
    var end = 22 * 60

    func isOpen(at date: Date, calendar: Calendar = .current) -> Bool {
        guard enabled, start != end else { return true }
        let parts = calendar.dateComponents([.hour, .minute], from: date)
        let minute = (parts.hour ?? 0) * 60 + (parts.minute ?? 0)
        return start < end ? (start..<end).contains(minute) : (minute >= start || minute < end)
    }

    /// Zero while open.
    func timeUntilOpen(from date: Date, calendar: Calendar = .current) -> TimeInterval {
        guard !isOpen(at: date, calendar: calendar) else { return 0 }
        let midnight = calendar.startOfDay(for: date)
        guard var opening = calendar.date(byAdding: .minute, value: start, to: midnight) else { return 0 }
        if opening <= date, let tomorrow = calendar.date(byAdding: .day, value: 1, to: opening) { opening = tomorrow }
        return opening.timeIntervalSince(date)
    }

    static func label(minutes: Int) -> String {
        let hour = minutes / 60, minute = minutes % 60
        var parts = DateComponents()
        parts.hour = hour
        parts.minute = minute
        let date = Calendar.current.date(from: parts) ?? Date()
        return date.formatted(date: .omitted, time: .shortened)
    }

    static func waitLabel(_ interval: TimeInterval) -> String {
        let minutes = Int((interval / 60).rounded(.up))
        return minutes < 60 ? "Opens in \(minutes)m" : "Opens in \(minutes / 60)h \(minutes % 60)m"
    }
}

/// Pages saved to read later. Opening one goes through the same blocking and pause rules.
@MainActor
final class ReadLater: ObservableObject {
    static let shared = ReadLater()
    static let storageKey = "readLater.v1"
    static let windowKey = "readLater.window.v1"

    @Published private(set) var items: [ReadLaterItem]
    @Published var window: ReadingWindow {
        didSet { StoredJSON.save(window, key: Self.windowKey, to: defaults) }
    }

    private let defaults: UserDefaults

    init(defaults: UserDefaults = BrowserPreferences.defaults) {
        self.defaults = defaults
        items = StoredJSON.load([ReadLaterItem].self, key: Self.storageKey, from: defaults) ?? []
        window = StoredJSON.load(ReadingWindow.self, key: Self.windowKey, from: defaults) ?? ReadingWindow()
    }

    var unread: [ReadLaterItem] { items.filter { $0.readAt == nil } }
    var read: [ReadLaterItem] { items.filter { $0.readAt != nil } }

    func contains(_ url: URL?) -> Bool {
        guard let url else { return false }
        return items.contains { $0.url == url && $0.readAt == nil }
    }

    func add(_ url: URL, title: String) {
        guard let scheme = url.scheme?.lowercased(), scheme == "http" || scheme == "https", !contains(url) else { return }
        items.insert(ReadLaterItem(title: Bookmarks.title(title, for: url), url: url), at: 0)
        save()
    }

    func setRead(_ id: UUID, _ read: Bool, at date: Date = Date()) {
        guard let index = items.firstIndex(where: { $0.id == id }) else { return }
        items[index].readAt = read ? date : nil
        save()
    }

    func remove(_ id: UUID) {
        items.removeAll { $0.id == id }
        save()
    }

    private func save() {
        StoredJSON.save(items, key: Self.storageKey, to: defaults)
    }
}
