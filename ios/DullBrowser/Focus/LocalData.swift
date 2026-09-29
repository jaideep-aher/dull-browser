import Foundation

/// Small JSON documents kept in the preferences store. Nothing here leaves the device.
enum StoredJSON {
    static func load<T: Decodable>(_ type: T.Type, key: String, from defaults: UserDefaults) -> T? {
        guard let data = defaults.data(forKey: key) else { return nil }
        return try? decoder.decode(T.self, from: data)
    }

    static func save<T: Encodable>(_ value: T, key: String, to defaults: UserDefaults) {
        if let data = try? encoder.encode(value) { defaults.set(data, forKey: key) }
    }

    static let encoder: JSONEncoder = {
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .iso8601
        encoder.outputFormatting = .sortedKeys
        return encoder
    }()

    static let decoder: JSONDecoder = {
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        return decoder
    }()
}

/// Local calendar days as `yyyy-MM-dd`, the key used for stats and countdowns.
enum DayKey {
    static func string(for date: Date, calendar: Calendar = .current) -> String {
        let parts = calendar.dateComponents([.year, .month, .day], from: date)
        return String(format: "%04d-%02d-%02d", parts.year ?? 0, parts.month ?? 0, parts.day ?? 0)
    }

    static func date(from key: String, calendar: Calendar = .current) -> Date? {
        let parts = key.split(separator: "-").compactMap { Int($0) }
        guard parts.count == 3 else { return nil }
        return calendar.date(from: DateComponents(year: parts[0], month: parts[1], day: parts[2]))
    }

    static func adding(_ days: Int, to key: String, calendar: Calendar = .current) -> String? {
        guard let date = date(from: key, calendar: calendar),
              let moved = calendar.date(byAdding: .day, value: days, to: date) else { return nil }
        return string(for: moved, calendar: calendar)
    }

    /// Whole calendar days from `from` to `to`; negative when `to` is earlier.
    static func days(from: String, to: String, calendar: Calendar = .current) -> Int? {
        guard let start = date(from: from, calendar: calendar), let end = date(from: to, calendar: calendar) else { return nil }
        return calendar.dateComponents([.day], from: start, to: end).day
    }
}

/// What a person types when naming a site, reduced to the domain the lists store.
enum SiteAddress {
    /// `https://WWW.Example.com/path` becomes `example.com`. Returns nil for anything that is not
    /// a plain domain name: IP addresses, single words, spaces, or invalid labels.
    static func normalize(_ input: String) -> String? {
        var text = input.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard !text.isEmpty, !text.contains(where: \.isWhitespace) else { return nil }
        if !text.contains("://") { text = "https://" + text }
        guard var host = URL(string: text)?.host?.lowercased() else { return nil }
        while host.hasSuffix(".") { host.removeLast() }
        if host.hasPrefix("www.") { host.removeFirst(4) }
        return isDomain(host) ? host : nil
    }

    static func isDomain(_ host: String) -> Bool {
        guard host.count <= 253 else { return false }
        let labels = host.split(separator: ".", omittingEmptySubsequences: false)
        guard labels.count >= 2 else { return false }
        for label in labels {
            guard (1...63).contains(label.count),
                  label.allSatisfy({ $0.isASCII && ($0.isLetter || $0.isNumber || $0 == "-") }),
                  label.first != "-", label.last != "-" else { return false }
        }
        guard let tld = labels.last else { return false }
        return tld.hasPrefix("xn--") || (tld.count >= 2 && tld.allSatisfy(\.isLetter))
    }
}

/// Friendly names for the sites people try most, so the blocked page can say "YouTube".
enum SiteName {
    private static let known: [String: String] = [
        "youtube.com": "YouTube", "youtu.be": "YouTube", "instagram.com": "Instagram",
        "facebook.com": "Facebook", "tiktok.com": "TikTok", "x.com": "X", "twitter.com": "Twitter",
        "reddit.com": "Reddit", "snapchat.com": "Snapchat", "netflix.com": "Netflix", "twitch.tv": "Twitch",
        "pinterest.com": "Pinterest", "linkedin.com": "LinkedIn", "threads.net": "Threads",
        "bbc.com": "BBC", "bbc.co.uk": "BBC", "cnn.com": "CNN", "nytimes.com": "The New York Times",
        "amazon.com": "Amazon", "ebay.com": "eBay", "etsy.com": "Etsy", "temu.com": "Temu",
        "shein.com": "Shein", "aliexpress.com": "AliExpress", "walmart.com": "Walmart",
        "target.com": "Target", "bestbuy.com": "Best Buy", "espn.com": "ESPN", "tmz.com": "TMZ",
    ]

    static func display(_ domain: String) -> String {
        var name = domain.lowercased()
        if name.hasPrefix("www.") { name.removeFirst(4) }
        return known[name] ?? name
    }
}
