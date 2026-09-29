import Foundation

/// One local calendar day. Site keys are list domains such as "youtube.com".
struct DayStats: Codable, Equatable {
    var day: String
    var blocked: [String: Int] = [:]
    var paused: [String: Int] = [:]
    var wentBack = 0
    var continued = 0
    var loosened = false

    var blockedTotal: Int { blocked.values.reduce(0, +) }
}

struct Milestone: Codable, Equatable {
    var days: Int
    var reached: String
}

struct StatsData: Codable, Equatable {
    var days: [DayStats] = []
    var milestones: [Milestone] = []
    /// Shown once on the start page, then cleared.
    var noticeMilestone: Int?
}

struct WeekSummary: Equatable {
    var attempts: Int
    var wentBack: Int
    var continued: Int
    var minutesSaved: Int
    var topSites: [(site: String, count: Int)]

    static func == (lhs: WeekSummary, rhs: WeekSummary) -> Bool {
        lhs.attempts == rhs.attempts && lhs.wentBack == rhs.wentBack && lhs.continued == rhs.continued
            && lhs.minutesSaved == rhs.minutesSaved && lhs.topSites.map(\.site) == rhs.topSites.map(\.site)
            && lhs.topSites.map(\.count) == rhs.topSites.map(\.count)
    }
}

/// Counts kept on this device only. A day joins the streak when Dull was opened that day and no
/// pause was turned off that day. A day without Dull, or a day a pause removal took effect, ends it.
@MainActor
final class Stats: ObservableObject {
    static let shared = Stats()
    static let storageKey = "stats.v1"
    static let minutesKey = "stats.minutesPerAttempt"
    static let historyLimit = 400
    static let milestoneDays = [7, 30, 100]
    static let minuteOptions = [3, 5, 8, 10, 15]
    static let defaultMinutes = 8

    @Published private(set) var data: StatsData

    private let defaults: UserDefaults
    private let now: () -> Date
    private let calendar: Calendar

    init(defaults: UserDefaults = BrowserPreferences.defaults, now: @escaping () -> Date = Date.init,
         calendar: Calendar = .current) {
        self.defaults = defaults
        self.now = now
        self.calendar = calendar
        data = StoredJSON.load(StatsData.self, key: Self.storageKey, from: defaults) ?? StatsData()
    }

    var today: String { DayKey.string(for: now(), calendar: calendar) }

    var minutesPerAttempt: Int {
        let stored = defaults.integer(forKey: Self.minutesKey)
        return Self.minuteOptions.contains(stored) ? stored : Self.defaultMinutes
    }

    func day(_ key: String) -> DayStats? { data.days.last { $0.day == key } }

    func blockedToday(site: String) -> Int { day(today)?.blocked[site] ?? 0 }

    func pausesToday(site: String) -> Int { day(today)?.paused[site] ?? 0 }

    func recordUse() { update { _ in } }

    func recordBlocked(site: String) { update { $0.blocked[site, default: 0] += 1 } }

    func recordPauseShown(site: String) { update { $0.paused[site, default: 0] += 1 } }

    func recordPause(wentBack: Bool) {
        update { wentBack ? ($0.wentBack += 1) : ($0.continued += 1) }
    }

    func recordLoosening() { update { $0.loosened = true } }

    func dismissMilestoneNotice() {
        guard data.noticeMilestone != nil else { return }
        data.noticeMilestone = nil
        save()
    }

    var currentStreak: Int { Self.streak(in: data.days, today: today, calendar: calendar) }

    func week() -> WeekSummary {
        Self.summary(of: data.days, endingOn: today, minutesPerAttempt: minutesPerAttempt, calendar: calendar)
    }

    /// Consecutive clean days ending today, or yesterday if today has no record yet.
    static func streak(in days: [DayStats], today: String, calendar: Calendar = .current) -> Int {
        let byDay = Dictionary(days.map { ($0.day, $0) }, uniquingKeysWith: { _, last in last })
        var cursor = today
        if byDay[today] == nil, let yesterday = DayKey.adding(-1, to: today, calendar: calendar) { cursor = yesterday }
        var count = 0
        while let record = byDay[cursor], !record.loosened {
            count += 1
            guard let previous = DayKey.adding(-1, to: cursor, calendar: calendar) else { break }
            cursor = previous
        }
        return count
    }

    /// The seven days ending on `endingOn`, today included.
    static func summary(of days: [DayStats], endingOn end: String, minutesPerAttempt: Int,
                        calendar: Calendar = .current) -> WeekSummary {
        let window = Set((0..<7).compactMap { DayKey.adding(-$0, to: end, calendar: calendar) })
        let recent = days.filter { window.contains($0.day) }
        var perSite: [String: Int] = [:]
        for day in recent { perSite.merge(day.blocked, uniquingKeysWith: +) }
        let attempts = recent.reduce(0) { $0 + $1.blockedTotal }
        let wentBack = recent.reduce(0) { $0 + $1.wentBack }
        let top = perSite.sorted { $0.value == $1.value ? $0.key < $1.key : $0.value > $1.value }
            .prefix(5).map { (site: $0.key, count: $0.value) }
        return WeekSummary(attempts: attempts, wentBack: wentBack,
                           continued: recent.reduce(0) { $0 + $1.continued },
                           minutesSaved: (attempts + wentBack) * minutesPerAttempt, topSites: top)
    }

    /// "~40m" under an hour, then whole hours: "~6h".
    static func savedLabel(minutes: Int) -> String {
        minutes < 60 ? "~\(minutes)m" : "~\(Int((Double(minutes) / 60).rounded()))h"
    }

    private func update(_ change: (inout DayStats) -> Void) {
        let key = today
        if let index = data.days.lastIndex(where: { $0.day == key }) {
            change(&data.days[index])
        } else {
            var record = DayStats(day: key)
            change(&record)
            data.days.append(record)
            data.days.sort { $0.day < $1.day }
            if data.days.count > Self.historyLimit { data.days.removeFirst(data.days.count - Self.historyLimit) }
        }
        checkMilestones(today: key)
        save()
    }

    private func checkMilestones(today key: String) {
        let streak = Self.streak(in: data.days, today: key, calendar: calendar)
        for days in Self.milestoneDays where streak >= days && !data.milestones.contains(where: { $0.days == days }) {
            data.milestones.append(Milestone(days: days, reached: key))
            data.noticeMilestone = days
        }
    }

    private func save() {
        StoredJSON.save(data, key: Self.storageKey, to: defaults)
    }
}
