import Foundation

/// Sites that are not blocked but are easy to lose an hour to. Opening one shows a short,
/// calm pause first. Major news sites are not here because the built-in list already blocks them,
/// and the block list always wins over a pause.
enum PauseCategory: String, CaseIterable, Codable, Identifiable {
    case shopping, news, sports, celebrity

    var id: String { rawValue }

    var title: String {
        switch self {
        case .shopping: "Shopping"
        case .news: "News"
        case .sports: "Sports"
        case .celebrity: "Celebrity and gossip"
        }
    }

    var domains: [String] {
        switch self {
        case .shopping:
            ["amazon.com", "amazon.co.uk", "amazon.ca", "amazon.de", "amazon.in", "ebay.com", "ebay.co.uk",
             "etsy.com", "temu.com", "shein.com", "aliexpress.com", "walmart.com", "target.com", "bestbuy.com",
             "wish.com", "wayfair.com", "costco.com", "macys.com", "nordstrom.com", "kohls.com", "asos.com",
             "zara.com", "hm.com", "newegg.com", "poshmark.com", "mercari.com", "depop.com", "vinted.com",
             "zalando.com", "flipkart.com", "myntra.com", "ajio.com", "meesho.com"]
        case .news:
            ["npr.org", "nypost.com", "thehill.com", "breitbart.com", "dailymail.com", "express.co.uk"]
        case .sports:
            ["espn.com", "espncricinfo.com", "cricbuzz.com", "bleacherreport.com", "si.com", "cbssports.com",
             "foxsports.com", "sports.yahoo.com", "theathletic.com", "nba.com", "nfl.com", "mlb.com", "nhl.com",
             "goal.com", "marca.com", "skysports.com"]
        case .celebrity:
            ["tmz.com", "people.com", "eonline.com", "pagesix.com", "usmagazine.com", "etonline.com",
             "justjared.com", "hollywoodlife.com", "radaronline.com", "x17online.com", "hellomagazine.com",
             "ok.co.uk", "buzzfeed.com"]
        }
    }
}

/// A pause the person asked to turn off. It keeps applying until `effectiveAt`.
struct ScheduledRemoval: Codable, Equatable, Identifiable {
    enum Kind: String, Codable { case category, site }

    var kind: Kind
    var value: String
    var effectiveAt: Date

    var id: String { "\(kind.rawValue):\(value)" }
}

struct PauseSettings: Codable, Equatable {
    var categories: Set<PauseCategory> = []
    var sites: [String] = []
    var removals: [ScheduledRemoval] = []
}

enum PauseRules {
    static let baseDelay: TimeInterval = 10
    /// After Continue, the same site opens without a pause in that tab for this long.
    static let grace: TimeInterval = 5 * 60
    static let removalDelay: TimeInterval = 24 * 60 * 60

    /// 10, 20, then 30 seconds for the first, second and later pauses on one site in a day.
    static func delay(pausesEarlierToday: Int, base: TimeInterval = baseDelay) -> TimeInterval {
        base * Double(min(max(pausesEarlierToday, 0), 2) + 1)
    }

    static var configuredBaseDelay: TimeInterval {
        #if DEBUG
        let seconds = UserDefaults.standard.double(forKey: "UITestPauseSeconds")
        if ProcessInfo.processInfo.arguments.contains("-UITesting"), seconds > 0 { return seconds }
        #endif
        return baseDelay
    }
}

/// Turning a pause on is immediate. Turning one off takes 24 hours and can be cancelled.
@MainActor
final class PauseList: ObservableObject {
    static let shared = PauseList()
    static let storageKey = "pauseList.v1"

    /// Work tools that share a domain with a shop.
    static let exceptions = DomainMatcher(domains: ["aws.amazon.com", "developer.amazon.com", "sellercentral.amazon.com"])

    @Published private(set) var settings: PauseSettings
    private(set) var matcher = DomainMatcher(domains: [])
    /// Called when a scheduled removal takes effect, which ends the day's streak.
    var onLoosened: () -> Void = {}

    private let defaults: UserDefaults
    private let now: () -> Date
    private let extraDomains: Set<String>

    init(defaults: UserDefaults = BrowserPreferences.defaults, now: @escaping () -> Date = Date.init,
         extraDomains: Set<String> = PauseList.debugDomains) {
        self.defaults = defaults
        self.now = now
        self.extraDomains = extraDomains
        settings = StoredJSON.load(PauseSettings.self, key: Self.storageKey, from: defaults) ?? PauseSettings()
        rebuild()
    }

    /// The pause-list domain covering `host`, or nil if the host opens without a pause.
    func site(for host: String?) -> String? {
        guard let host, !host.isEmpty, !Self.exceptions.matches(host) else { return nil }
        return matcher.match(host)
    }

    func isOn(_ category: PauseCategory) -> Bool { settings.categories.contains(category) }

    func pendingRemoval(_ kind: ScheduledRemoval.Kind, _ value: String) -> ScheduledRemoval? {
        settings.removals.first { $0.kind == kind && $0.value == value }
    }

    func turnOn(_ category: PauseCategory) {
        settings.categories.insert(category)
        settings.removals.removeAll { $0.kind == .category && $0.value == category.rawValue }
        commit()
    }

    enum AddResult: Equatable { case added(String), alreadyPaused(String), blocked(String), invalid }

    @discardableResult
    func addSite(_ input: String, blocker: SiteBlocker = .shared) -> AddResult {
        guard let domain = SiteAddress.normalize(input) else { return .invalid }
        if blocker.isListed(host: domain) { return .blocked(domain) }
        settings.removals.removeAll { $0.kind == .site && $0.value == domain }
        if settings.sites.contains(domain) {
            commit()
            return .alreadyPaused(domain)
        }
        settings.sites.append(domain)
        commit()
        return .added(domain)
    }

    func scheduleRemoval(of category: PauseCategory) {
        guard isOn(category) else { return }
        schedule(.category, category.rawValue)
    }

    func scheduleRemoval(ofSite domain: String) {
        guard settings.sites.contains(domain) else { return }
        schedule(.site, domain)
    }

    func cancelRemoval(_ removal: ScheduledRemoval) {
        settings.removals.removeAll { $0.id == removal.id }
        commit()
    }

    /// Runs at launch, when the app becomes active and when pause settings open.
    func applyDueRemovals() {
        let date = now()
        let due = settings.removals.filter { $0.effectiveAt <= date }
        guard !due.isEmpty else { return }
        for removal in due {
            switch removal.kind {
            case .category:
                if let category = PauseCategory(rawValue: removal.value) { settings.categories.remove(category) }
            case .site:
                settings.sites.removeAll { $0 == removal.value }
            }
        }
        settings.removals.removeAll { $0.effectiveAt <= date }
        commit()
        onLoosened()
    }

    private func schedule(_ kind: ScheduledRemoval.Kind, _ value: String) {
        guard pendingRemoval(kind, value) == nil else { return }
        settings.removals.append(ScheduledRemoval(kind: kind, value: value, effectiveAt: now() + PauseRules.removalDelay))
        commit()
    }

    private func commit() {
        StoredJSON.save(settings, key: Self.storageKey, to: defaults)
        rebuild()
    }

    private func rebuild() {
        var domains = Set(settings.sites).union(extraDomains)
        for category in settings.categories { domains.formUnion(category.domains) }
        matcher = DomainMatcher(domains: domains)
    }

    nonisolated static var debugDomains: Set<String> {
        #if DEBUG
        if ProcessInfo.processInfo.arguments.contains("-UITesting"),
           let list = UserDefaults.standard.string(forKey: "UITestPauseSites") {
            return Set(list.split(separator: ",").map { $0.trimmingCharacters(in: .whitespaces).lowercased() })
        }
        #endif
        return []
    }
}
