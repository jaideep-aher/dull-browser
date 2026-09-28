import Foundation

/// Turns what was typed into a page address or a search with the selected engine.
enum BrowserInput {
    static func url(for input: String, searchEngine: SearchEngine = .current) -> URL? {
        let text = input.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { return nil }
        let lower = text.lowercased()

        if lower.hasPrefix("http://") || lower.hasPrefix("https://") {
            if let url = URL(string: text), url.host?.isEmpty == false { return url }
        } else if !text.contains(" "), looksLikeHost(text), let url = URL(string: "https://" + text), url.host != nil {
            return url
        }
        return search(text, engine: searchEngine)
    }

    static func search(_ query: String, engine: SearchEngine = .current) -> URL? {
        var allowed = CharacterSet.urlQueryAllowed
        allowed.remove(charactersIn: "&+=?#")
        guard let encoded = query.addingPercentEncoding(withAllowedCharacters: allowed) else { return nil }
        return URL(string: engine.searchPrefix + encoded)
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
