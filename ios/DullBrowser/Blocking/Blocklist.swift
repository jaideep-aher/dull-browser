import Foundation
import os

/// The site list bundled with the app. It is copied from the Android asset at build time
/// (app/src/main/assets/blocklist.txt) and never downloaded.
enum Blocklist {
    static let resourceName = "blocklist"

    /// Used only if the bundled file cannot be read at all.
    static let fallback: Set<String> = [
        "facebook.com",
        "instagram.com",
        "reddit.com",
        "snapchat.com",
        "tiktok.com",
        "twitter.com",
        "x.com",
        "youtube.com",
        "youtu.be",
    ]

    static func text(in bundle: Bundle = .main) -> String? {
        guard let url = bundle.url(forResource: resourceName, withExtension: "txt") else { return nil }
        return try? String(contentsOf: url, encoding: .utf8)
    }

    static func load(from bundle: Bundle = .main) -> Set<String> {
        guard let text = text(in: bundle) else {
            Logger.blocking.error("blocklist.txt is missing from the bundle, using the fallback list")
            return fallback
        }
        let domains = parse(text)
        Logger.blocking.info("Loaded \(domains.count) blocked domains")
        return domains.isEmpty ? fallback : domains
    }

    static func parse(_ text: String) -> Set<String> {
        var domains = Set<String>(minimumCapacity: 80_000)
        for line in text.split(whereSeparator: \.isNewline) {
            let domain = line.trimmingCharacters(in: .whitespaces).lowercased()
            if domain.isEmpty || domain.hasPrefix("#") { continue }
            domains.insert(domain)
        }
        return domains
    }
}

extension Logger {
    static let blocking = Logger(subsystem: "app.slate.browser.ios", category: "blocking")
}
