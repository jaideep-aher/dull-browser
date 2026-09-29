import Foundation

extension Notification.Name {
    static let blocklistGrew = Notification.Name("DullBrowser.blocklistGrew")
}

/// Sites the person chose to block. They are stored as a plain list of domains in the order
/// added. There is deliberately no way to remove one: not in the UI, not in this API.
enum CustomBlocklist {
    static let storageKey = "customBlocklist.v1"
    static let limit = 1_000

    enum AddResult: Equatable {
        case added(String)
        case alreadyBlocked(String)
        case invalid
        case full
    }

    static func load(from defaults: UserDefaults) -> [String] {
        (defaults.stringArray(forKey: storageKey) ?? []).filter(SiteAddress.isDomain)
    }

    /// Adding is immediate: open tabs on the site close, and it is blocked like a listed site.
    @discardableResult
    static func add(_ input: String, defaults: UserDefaults = BrowserPreferences.defaults,
                    blocker: SiteBlocker = .shared) -> AddResult {
        guard let domain = SiteAddress.normalize(input) else { return .invalid }
        if blocker.isListed(host: domain) { return .alreadyBlocked(domain) }
        var stored = load(from: defaults)
        guard stored.count < limit else { return .full }
        stored.append(domain)
        defaults.set(stored, forKey: storageKey)
        blocker.add([domain])
        NotificationCenter.default.post(name: .blocklistGrew, object: blocker)
        return .added(domain)
    }
}
