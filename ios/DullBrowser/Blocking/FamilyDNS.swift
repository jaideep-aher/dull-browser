import Foundation
import os

/// Asks Cloudflare for Families whether a host is filtered. The bundled list is exact but
/// finite; this catches adult hosts it does not know. Only used for top-level navigations.
///
/// Network problems count as not filtered and are not cached, so a flaky connection does not
/// make the browser unusable. The bundled list still applies either way.
actor FamilyDNS {
    static let shared = FamilyDNS()

    private static let resolver = URL(string: "https://family.cloudflare-dns.com/dns-query")!
    private static let blockedAddresses: Set<String> = ["0.0.0.0", "::"]
    private static let cacheLimit = 2_048

    private let session: URLSession
    private var cache: [String: Bool] = [:]
    private var cacheOrder: [String] = []
    private var inFlight: [String: Task<Bool?, Never>] = [:]

    init() {
        let config = URLSessionConfiguration.ephemeral
        // Short, so a slow resolver cannot noticeably stall a page load.
        config.timeoutIntervalForRequest = 1.5
        config.timeoutIntervalForResource = 1.5
        config.waitsForConnectivity = false
        config.requestCachePolicy = .reloadIgnoringLocalCacheData
        session = URLSession(configuration: config)
    }

    func isFiltered(_ rawHost: String) async -> Bool {
        let host = rawHost.lowercased().trimmingCharacters(in: CharacterSet(charactersIn: "."))
        guard Self.shouldQuery(host) else { return false }
        if let cached = cache[host] { return cached }

        let task: Task<Bool?, Never>
        if let running = inFlight[host] {
            task = running
        } else {
            let session = session
            task = Task.detached { await Self.query(host, session: session) }
            inFlight[host] = task
        }

        let result = await task.value
        inFlight[host] = nil
        guard let result else { return false }
        remember(host, result)
        return result
    }

    private func remember(_ host: String, _ filtered: Bool) {
        guard cache.updateValue(filtered, forKey: host) == nil else { return }
        cacheOrder.append(host)
        if cacheOrder.count > Self.cacheLimit {
            cache[cacheOrder.removeFirst()] = nil
        }
        if filtered { Logger.blocking.info("Family DNS filtered \(host, privacy: .public)") }
    }

    private static func shouldQuery(_ host: String) -> Bool {
        guard host.contains("."), host != "localhost" else { return false }
        // IP literals have nothing to resolve.
        if host.contains(":") || host.allSatisfy({ $0.isNumber || $0 == "." }) { return false }
        return true
    }

    private static func query(_ host: String, session: URLSession) async -> Bool? {
        var components = URLComponents(url: resolver, resolvingAgainstBaseURL: false)!
        components.queryItems = [URLQueryItem(name: "name", value: host), URLQueryItem(name: "type", value: "A")]
        var request = URLRequest(url: components.url!)
        request.setValue("application/dns-json", forHTTPHeaderField: "Accept")

        do {
            let (data, response) = try await session.data(for: request)
            guard (response as? HTTPURLResponse)?.statusCode == 200 else { return nil }
            return isFilteredAnswer(data)
        } catch {
            Logger.blocking.debug("Family DNS lookup failed for \(host, privacy: .public): \(error.localizedDescription)")
            return nil
        }
    }

    /// A filtered name resolves to the null address instead of its real records.
    static func isFilteredAnswer(_ data: Data) -> Bool? {
        guard let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { return nil }
        let answers = json["Answer"] as? [[String: Any]] ?? []
        return answers.contains { ($0["data"] as? String).map(blockedAddresses.contains) ?? false }
    }
}
