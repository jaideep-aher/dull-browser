import Foundation
import WebKit
import os

/// Blocks images, scripts, media and fetches from listed hosts inside pages that are allowed.
/// Page and frame loads are left to the navigation delegate, which shows the closed page.
///
/// WebKit compiles the rules once and keeps them on disk, keyed by a hash of the list.
@MainActor
enum SubresourceRules {
    nonisolated private static let chunkSize = 30_000
    nonisolated private static let resourceTypes = ["image", "style-sheet", "script", "font", "raw", "svg-document", "media", "ping"]

    private static var loaded: [WKContentRuleList]?
    private static var loading: Task<[WKContentRuleList], Never>?

    static func load() async -> [WKContentRuleList] {
        if let loaded { return loaded }
        if let loading { return await loading.value }
        let task = Task { await compile() }
        loading = task
        let lists = await task.value
        loaded = lists
        loading = nil
        return lists
    }

    private static func compile() async -> [WKContentRuleList] {
        let chunks = await Task.detached(priority: .utility) { () -> [(String, String)] in
            let domains = SiteBlocker.shared.matcher.domains.sorted()
            let version = fingerprint(domains)
            return stride(from: 0, to: domains.count, by: chunkSize).map { start in
                let slice = domains[start..<min(start + chunkSize, domains.count)]
                return ("blocklist-\(version)-\(start / chunkSize)", encode(slice))
            }
        }.value

        guard let store = WKContentRuleListStore.default() else { return [] }
        var lists: [WKContentRuleList] = []
        let started = Date()
        for (identifier, json) in chunks {
            if let cached = try? await store.contentRuleList(forIdentifier: identifier) {
                lists.append(cached)
                continue
            }
            do {
                if let list = try await store.compileContentRuleList(forIdentifier: identifier, encodedContentRuleList: json) {
                    lists.append(list)
                }
            } catch {
                Logger.blocking.error("Rule list \(identifier, privacy: .public) failed: \(error.localizedDescription, privacy: .public)")
            }
        }
        let elapsed = Date().timeIntervalSince(started)
        Logger.blocking.info("Sub-resource rules ready: \(lists.count) lists in \(elapsed, format: .fixed(precision: 2))s")
        return lists
    }

    private static var addedList: (domains: Set<String>, list: WKContentRuleList)?

    /// The sites the person added, compiled into one small list that is replaced when it grows.
    static func loadAdded() async -> WKContentRuleList? {
        let domains = SiteBlocker.shared.addedDomains
        guard !domains.isEmpty else { return nil }
        if let addedList, addedList.domains == domains { return addedList.list }
        let sorted = domains.sorted()
        let identifier = "added-\(fingerprint(sorted))"
        guard let store = WKContentRuleListStore.default() else { return nil }
        let list: WKContentRuleList?
        if let cached = try? await store.contentRuleList(forIdentifier: identifier) {
            list = cached
        } else {
            list = try? await store.compileContentRuleList(forIdentifier: identifier,
                                                           encodedContentRuleList: encode(sorted[...]))
        }
        if let list { addedList = (domains, list) }
        return list
    }

    nonisolated static func encode(_ domains: ArraySlice<String>) -> String {
        let types = resourceTypes.map { "\"\($0)\"" }.joined(separator: ",")
        var rules: [String] = []
        rules.reserveCapacity(domains.count)
        for domain in domains where domain.allSatisfy(isDomainCharacter) {
            let escaped = domain.replacingOccurrences(of: ".", with: "\\\\.")
            rules.append(#"{"trigger":{"url-filter":"^[^:]+://+([^:/]+\\.)?\#(escaped)[/:]","resource-type":[\#(types)]},"action":{"type":"block"}}"#)
        }
        return "[" + rules.joined(separator: ",") + "]"
    }

    nonisolated private static func isDomainCharacter(_ c: Character) -> Bool {
        c.isASCII && (c.isLetter || c.isNumber || c == "." || c == "-" || c == "_")
    }

    /// FNV-1a over the sorted list, so a new list compiles under a new identifier.
    nonisolated private static func fingerprint(_ domains: [String]) -> String {
        var hash: UInt64 = 0xcbf2_9ce4_8422_2325
        for domain in domains {
            for byte in domain.utf8 {
                hash ^= UInt64(byte)
                hash = hash &* 0x100_0000_01b3
            }
            hash ^= 0x0a
            hash = hash &* 0x100_0000_01b3
        }
        return String(hash, radix: 16)
    }
}
