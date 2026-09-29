import Foundation

/// A host matches when it equals a listed domain or is a subdomain of one,
/// so one "bbc.com" entry also covers "www.bbc.com".
struct DomainMatcher: Sendable {
    let domains: Set<String>

    func matches(_ host: String) -> Bool {
        var name = host.lowercased()
        while name.hasSuffix(".") { name.removeLast() }
        guard !name.isEmpty else { return false }

        var candidate = Substring(name)
        while true {
            // Stop before the final label: a stray "com" in the list must not block the web.
            guard candidate.contains(".") else { return false }
            if domains.contains(String(candidate)) { return true }
            guard let dot = candidate.firstIndex(of: ".") else { return false }
            candidate = candidate[candidate.index(after: dot)...]
        }
    }
}
