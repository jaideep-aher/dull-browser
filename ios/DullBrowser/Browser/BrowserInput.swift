import Foundation

/// Turns what was typed into a page address or a Kagi search.
enum BrowserInput {
    static let searchPrefix = "https://kagi.com/search?q="

    static func url(for input: String) -> URL? {
        let text = input.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { return nil }
        let lower = text.lowercased()

        if lower.hasPrefix("http://") || lower.hasPrefix("https://") {
            if let url = URL(string: text), url.host?.isEmpty == false { return url }
        } else if !text.contains(" "), looksLikeHost(text), let url = URL(string: "https://" + text), url.host != nil {
            return url
        }
        return search(text)
    }

    static func search(_ query: String) -> URL? {
        var allowed = CharacterSet.urlQueryAllowed
        allowed.remove(charactersIn: "&+=?#")
        guard let encoded = query.addingPercentEncoding(withAllowedCharacters: allowed) else { return nil }
        return URL(string: searchPrefix + encoded)
    }

    private static func looksLikeHost(_ text: String) -> Bool {
        let host = text.prefix { $0 != "/" && $0 != "?" && $0 != "#" }.split(separator: ":").first ?? ""
        let labels = host.split(separator: ".", omittingEmptySubsequences: false)
        guard labels.count >= 2, labels.allSatisfy({ !$0.isEmpty }) else { return false }
        if labels.allSatisfy({ $0.allSatisfy(\.isNumber) }) { return labels.count == 4 }
        guard let tld = labels.last, tld.count >= 2, tld.allSatisfy(\.isLetter) else { return false }
        return true
    }
}
