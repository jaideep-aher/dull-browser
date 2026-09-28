import Combine
import Foundation

/// Each tab owns its web view and history. Only the selected restored tab loads immediately.
@MainActor
final class BrowserSession: ObservableObject {
    @Published private(set) var tabs: [BrowserModel]
    @Published private(set) var selectedID: UUID
    private let defaults: UserDefaults
    private var observers: Set<AnyCancellable> = []
    private static let storageKey = "browserSession.v1"

    private struct SavedTab: Codable {
        let id: UUID
        let address: String?
    }

    private struct Snapshot: Codable {
        let tabs: [SavedTab]
        let selectedID: UUID
    }

    init(defaults: UserDefaults = BrowserPreferences.defaults, restore: Bool = true) {
        self.defaults = defaults
        if restore, let data = defaults.data(forKey: Self.storageKey),
           let snapshot = try? JSONDecoder().decode(Snapshot.self, from: data),
           !snapshot.tabs.isEmpty,
           Set(snapshot.tabs.map(\.id)).count == snapshot.tabs.count {
            let restored = snapshot.tabs.map { BrowserModel(id: $0.id, restoredURL: $0.address.flatMap(URL.init(string:))) }
            tabs = restored
            selectedID = restored.contains(where: { $0.id == snapshot.selectedID }) ? snapshot.selectedID : restored[0].id
        } else {
            let first = BrowserModel()
            tabs = [first]
            selectedID = first.id
        }
        observeTabs()
        activeTab.restoreIfNeeded()
    }

    var activeTab: BrowserModel { tabs.first(where: { $0.id == selectedID }) ?? tabs[0] }

    func addTab(url: URL? = nil) {
        activeTab.deactivate()
        let tab = BrowserModel()
        tabs.append(tab)
        selectedID = tab.id
        observeTabs()
        if let url { tab.load(url) }
        save()
    }

    func select(_ id: UUID) {
        guard tabs.contains(where: { $0.id == id }) else { return }
        activeTab.deactivate()
        selectedID = id
        activeTab.restoreIfNeeded()
        save()
    }

    func close(_ id: UUID) {
        guard let index = tabs.firstIndex(where: { $0.id == id }) else { return }
        tabs[index].close()
        tabs.remove(at: index)
        if tabs.isEmpty {
            let replacement = BrowserModel()
            tabs = [replacement]
            selectedID = replacement.id
        } else if selectedID == id {
            selectedID = tabs[min(index, tabs.count - 1)].id
            activeTab.restoreIfNeeded()
        }
        observeTabs()
        save()
    }

    func save() {
        let snapshot = Snapshot(tabs: tabs.map { SavedTab(id: $0.id, address: $0.savedAddress) }, selectedID: selectedID)
        if let data = try? JSONEncoder().encode(snapshot) { defaults.set(data, forKey: Self.storageKey) }
    }

    private func observeTabs() {
        observers.removeAll()
        for tab in tabs {
            tab.openInNewTab = { [weak self] url in self?.addTab(url: url) }
            // Published properties notify before mutation; persist after the update has landed.
            tab.$url.combineLatest(tab.$blockedHost, tab.$showingNewTab)
                .dropFirst().receive(on: RunLoop.main).sink { [weak self] _ in
                self?.save()
            }.store(in: &observers)
        }
    }
}
