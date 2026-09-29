import Foundation

/// The fixed list compiled into the app. There is no preference, no allow list and no refresh.
/// The only way to change it is to edit the list and ship a new build.
final class SiteBlocker: Sendable {
    static let shared = SiteBlocker(domains: Blocklist.load())

    let matcher: DomainMatcher

    init(domains: Set<String>) {
        matcher = DomainMatcher(domains: domains)
    }

    func isListed(host: String?) -> Bool {
        guard let host, !host.isEmpty else { return false }
        return matcher.matches(host)
    }

    /// The listed host this URL reaches, either directly or through a link hidden inside it
    /// such as an AMP page, a share wrapper or a search-result redirect.
    func listedHost(for url: URL) -> String? {
        if isListed(host: url.host) { return url.host }
        let host = url.host?.lowercased()
        let scheme = url.scheme?.lowercased()
        for hop in NavigationHops.extract(url.absoluteString) {
            let hopHost = NavigationHops.hostOf(hop)
            if hopHost == host, NavigationHops.schemeOf(hop) == scheme { continue }
            if isListed(host: hopHost) { return hopHost }
        }
        return nil
    }
}
