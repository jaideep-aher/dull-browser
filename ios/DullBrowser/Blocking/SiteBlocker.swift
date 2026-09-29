import Foundation
import os

/// The fixed list compiled into the app, plus any sites the person added themselves.
/// There is no preference, no allow list and no refresh. Added sites can never be taken off.
final class SiteBlocker: Sendable {
    static let shared = SiteBlocker(domains: Blocklist.load(), added: CustomBlocklist.load(from: BrowserPreferences.defaults))

    let matcher: DomainMatcher
    private let added: OSAllocatedUnfairLock<DomainMatcher>

    init(domains: Set<String>, added: [String] = []) {
        matcher = DomainMatcher(domains: domains)
        self.added = OSAllocatedUnfairLock(initialState: DomainMatcher(domains: Set(added)))
    }

    var addedDomains: Set<String> { added.withLock { $0.domains } }

    /// Only ever grows the list.
    func add(_ domains: some Sequence<String>) {
        added.withLock { $0 = DomainMatcher(domains: $0.domains.union(domains)) }
    }

    func isListed(host: String?) -> Bool {
        listedDomain(host: host) != nil
    }

    /// The list entry that covers `host`, used to count attempts per site.
    func listedDomain(host: String?) -> String? {
        guard let host, !host.isEmpty else { return nil }
        return matcher.match(host) ?? added.withLock { $0.match(host) }
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
