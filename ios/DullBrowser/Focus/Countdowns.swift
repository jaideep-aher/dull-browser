import Foundation

struct Countdown: Codable, Equatable, Identifiable {
    var id = UUID()
    var name: String
    /// Local calendar day, `yyyy-MM-dd`.
    var day: String
}

/// Named dates such as exams. The start page shows the nearest one that has not passed.
@MainActor
final class Countdowns: ObservableObject {
    static let shared = Countdowns()
    static let storageKey = "countdowns.v1"

    @Published private(set) var items: [Countdown]

    private let defaults: UserDefaults
    private let now: () -> Date
    private let calendar: Calendar

    init(defaults: UserDefaults = BrowserPreferences.defaults, now: @escaping () -> Date = Date.init,
         calendar: Calendar = .current) {
        self.defaults = defaults
        self.now = now
        self.calendar = calendar
        items = StoredJSON.load([Countdown].self, key: Self.storageKey, from: defaults) ?? []
    }

    func add(name: String, on date: Date) {
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        items.append(Countdown(name: String(trimmed.prefix(40)), day: DayKey.string(for: date, calendar: calendar)))
        items.sort { $0.day < $1.day }
        save()
    }

    func remove(_ id: UUID) {
        items.removeAll { $0.id == id }
        save()
    }

    /// Upcoming dates, nearest first. Past dates are hidden but kept until removed.
    var upcoming: [(countdown: Countdown, days: Int)] {
        let today = DayKey.string(for: now(), calendar: calendar)
        return items.compactMap { item in
            guard let days = DayKey.days(from: today, to: item.day, calendar: calendar), days >= 0 else { return nil }
            return (item, days)
        }
        .sorted { $0.days < $1.days }
    }

    var nextLabel: String? {
        upcoming.first.map { Self.label($0.countdown.name, days: $0.days) }
    }

    /// "Finals in 12 days", "Finals tomorrow", "Finals today".
    static func label(_ name: String, days: Int) -> String { "\(name) \(when(days: days))" }

    static func when(days: Int) -> String {
        switch days {
        case 0: "today"
        case 1: "tomorrow"
        default: "in \(days) days"
        }
    }

    private func save() {
        StoredJSON.save(items, key: Self.storageKey, to: defaults)
    }
}
