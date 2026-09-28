import Foundation

/// Hosts tucked into a link: AMP pages, share wrappers, intent handoffs.
/// Short links are left alone. The page that actually loads is checked on its own host.
enum NavigationHops {
    static let queryKeys: Set<String> = [
        "url", "u", "q", "redirect", "redirect_url", "dest", "destination", "target",
        "to", "link", "href", "continue", "next", "adurl", "imgurl", "imgrefurl",
    ]

    private static let googleWrapperPaths = ["/url", "/amp", "/aclk", "/goto", "/link", "/imgres"]

    private static let ampGoogle = try! NSRegularExpression(
        pattern: #"https?://(?:www\.)?google\.[^/]+/amp/(?:s/)?([^/?#]+)"#,
        options: .caseInsensitive
    )

    private static let ampCdn = try! NSRegularExpression(
        pattern: #"https?://[^/]*cdn\.ampproject\.org/(?:[a-z]/)*s/([^/?#]+)"#,
        options: .caseInsensitive
    )

    /// URLs this navigation can land on, including `rawURL` itself.
    static func extract(_ rawURL: String) -> [String] {
        var found: [String] = []
        var seen: Set<String> = []
        walk(rawURL, depth: 0, found: &found, seen: &seen)
        return found
    }

    private static func walk(_ rawURL: String, depth: Int, found: inout [String], seen: inout Set<String>) {
        guard depth <= 4 else { return }
        let url = rawURL.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !url.isEmpty, seen.insert(url).inserted else { return }
        found.append(url)

        for next in unwrapIntent(url) { walk(next, depth: depth + 1, found: &found, seen: &seen) }
        if let next = unwrapAmp(url) { walk(next, depth: depth + 1, found: &found, seen: &seen) }
        if isWrapper(url) {
            for next in queryDestinations(url) { walk(next, depth: depth + 1, found: &found, seen: &seen) }
        }
    }

    private static func isWrapper(_ url: String) -> Bool {
        guard let host = hostOf(url) else { return false }
        let path = pathOf(url)
        let bare = host.hasPrefix("www.") ? String(host.dropFirst(4)) : host

        if bare == "google.com" || bare.hasSuffix(".google.com") || bare.hasPrefix("google.") {
            return googleWrapperPaths.contains { path.hasPrefix($0) }
        }
        switch bare {
        case "l.facebook.com", "lm.facebook.com", "l.instagram.com", "l.messenger.com", "out.reddit.com":
            return true
        case "youtube.com", "m.youtube.com", "music.youtube.com":
            return path.hasPrefix("/redirect")
        default:
            return path.hasPrefix("/l.php")
        }
    }

    private static func queryDestinations(_ url: String) -> [String] {
        guard let questionMark = url.firstIndex(of: "?") else { return [] }
        let query = url[url.index(after: questionMark)...].prefix { $0 != "#" }
        guard !query.isEmpty else { return [] }

        return query.split(separator: "&").compactMap { pair in
            let parts = pair.split(separator: "=", maxSplits: 1, omittingEmptySubsequences: false)
            let key = decode(String(parts[0])).lowercased()
            guard queryKeys.contains(key) else { return nil }
            let value = parts.count > 1 ? decode(String(parts[1])) : ""
            let lower = value.lowercased()
            return lower.hasPrefix("http://") || lower.hasPrefix("https://") || lower.hasPrefix("intent:") ? value : nil
        }
    }

    private static func unwrapAmp(_ url: String) -> String? {
        let range = NSRange(url.startIndex..., in: url)
        guard let match = ampGoogle.firstMatch(in: url, range: range) ?? ampCdn.firstMatch(in: url, range: range),
              let hostRange = Range(match.range(at: 1), in: url),
              let matchRange = Range(match.range, in: url)
        else { return nil }
        let host = url[hostRange]
        guard host.contains(".") else { return nil }
        return "https://\(host)\(url[matchRange.upperBound...])"
    }

    private static func unwrapIntent(_ url: String) -> [String] {
        guard url.lowercased().hasPrefix("intent:") else { return [] }
        var results: [String] = []

        let parts = url.split(separator: "#", maxSplits: 1, omittingEmptySubsequences: false)
        let beforeFragment = String(parts[0])
        let fragment = parts.count > 1 ? String(parts[1]) : ""
        let hierarchical = beforeFragment.split(separator: ":", maxSplits: 1, omittingEmptySubsequences: false)
            .dropFirst().first.map(String.init) ?? ""

        var scheme = "https"
        if let range = fragment.range(of: "scheme=") {
            let value = fragment[range.upperBound...].prefix { $0 != ";" }
            if !value.trimmingCharacters(in: .whitespaces).isEmpty { scheme = decode(String(value)) }
        }

        if hierarchical.hasPrefix("//") {
            let hostAndPath = hierarchical.dropFirst(2)
            if !hostAndPath.isEmpty { results.append("\(scheme)://\(hostAndPath)") }
        }

        if let range = fragment.range(of: "S.browser_fallback_url=") {
            let fallback = decode(String(fragment[range.upperBound...].prefix { $0 != ";" }))
            let lower = fallback.lowercased()
            if lower.hasPrefix("http://") || lower.hasPrefix("https://") { results.append(fallback) }
        }
        return results
    }

    static func schemeOf(_ url: String) -> String? {
        guard let colon = url.firstIndex(of: ":") else { return nil }
        return url[..<colon].lowercased()
    }

    static func hostOf(_ url: String) -> String? {
        guard let schemeEnd = url.range(of: "://") else { return nil }
        var authority = url[schemeEnd.upperBound...].prefix { $0 != "/" && $0 != "?" && $0 != "#" }
        if let at = authority.lastIndex(of: "@") { authority = authority[authority.index(after: at)...] }
        let host = authority.prefix { $0 != ":" }.lowercased()
        return host.isEmpty ? nil : host
    }

    private static func pathOf(_ url: String) -> String {
        let afterScheme = url.range(of: "://").map { url[$0.upperBound...] } ?? Substring(url)
        guard let slash = afterScheme.firstIndex(of: "/") else { return "/" }
        let path = afterScheme[afterScheme.index(after: slash)...].prefix { $0 != "?" && $0 != "#" }
        return "/" + path
    }

    /// Form decoding, applied at most twice for double-encoded values.
    private static func decode(_ value: String) -> String {
        var current = value
        for _ in 0..<2 {
            guard let decoded = current.replacingOccurrences(of: "+", with: " ").removingPercentEncoding,
                  decoded != current
            else { return current }
            current = decoded
        }
        return current
    }
}
