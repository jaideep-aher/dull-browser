import UIKit
import WebKit
import os

/// Small page previews for the tab grid. A preview is taken only when a tab is left or the app
/// goes to the background, never while a page is on screen. Previews are browsing data: they
/// are removed with their tab and when browsing data is cleared.
@MainActor
final class TabThumbnails: ObservableObject {
    static let shared = TabThumbnails()
    static let width: CGFloat = 160
    static let aspect: CGFloat = 1.25
    static let memoryLimit = 8

    @Published private(set) var images: [UUID: UIImage] = [:]
    private var order: [UUID] = []
    private var loading: Set<UUID> = []
    private let directory: URL?

    init(directory: URL? = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first?
        .appendingPathComponent("TabThumbnails", isDirectory: true)) {
        self.directory = directory
        if let directory { try? FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true) }
    }

    /// Only real, visible pages are captured. Blocked, paused and failed pages keep their placeholder.
    func capture(_ tab: BrowserModel) {
        guard Feature.tabThumbnails.isUnlocked, tab.hasCapturablePage else { return }
        capture(tab.webView, id: tab.id)
    }

    func capture(_ view: WKWebView, id: UUID) {
        let bounds = view.bounds
        guard view.window != nil, bounds.width > 1, bounds.height > 1 else { return }
        let config = WKSnapshotConfiguration()
        config.rect = CGRect(x: 0, y: 0, width: bounds.width, height: min(bounds.height, bounds.width * Self.aspect))
        config.snapshotWidth = NSNumber(value: Double(Self.width))
        config.afterScreenUpdates = false
        let started = CFAbsoluteTimeGetCurrent()
        view.takeSnapshot(with: config) { [weak self] image, _ in
            guard let image else { return }
            let file = self?.file(for: id)
            Task.detached(priority: .utility) {
                let (small, data) = Self.shrink(image)
                if let file, let data { try? data.write(to: file, options: .atomic) }
                await MainActor.run {
                    self?.store(small, for: id)
                    let elapsed = (CFAbsoluteTimeGetCurrent() - started) * 1000
                    Logger.thumbnails.debug("Tab preview ready in \(elapsed, format: .fixed(precision: 1)) ms, \(data?.count ?? 0) bytes")
                }
            }
        }
    }

    /// Loads a saved preview from disk off the main thread, if there is one.
    func loadIfNeeded(_ id: UUID) {
        guard images[id] == nil, !loading.contains(id), let file = file(for: id) else { return }
        loading.insert(id)
        Task.detached(priority: .utility) { [weak self] in
            let image = (try? Data(contentsOf: file)).flatMap { UIImage(data: $0)?.preparingForDisplay() }
            await MainActor.run {
                self?.loading.remove(id)
                if let image { self?.store(image, for: id) }
            }
        }
    }

    func remove(_ id: UUID) {
        images[id] = nil
        order.removeAll { $0 == id }
        if let file = file(for: id) { try? FileManager.default.removeItem(at: file) }
    }

    func removeAll(keeping ids: Set<UUID> = []) {
        for id in images.keys where !ids.contains(id) { images[id] = nil }
        order.removeAll { !ids.contains($0) }
        guard let directory else { return }
        Task.detached(priority: .utility) {
            let files = (try? FileManager.default.contentsOfDirectory(at: directory, includingPropertiesForKeys: nil)) ?? []
            for file in files where !ids.contains(where: { file.lastPathComponent == "\($0.uuidString).jpg" }) {
                try? FileManager.default.removeItem(at: file)
            }
        }
    }

    private func store(_ image: UIImage, for id: UUID) {
        images[id] = image
        order.removeAll { $0 == id }
        order.append(id)
        while order.count > Self.memoryLimit { images[order.removeFirst()] = nil }
    }

    private func file(for id: UUID) -> URL? {
        directory?.appendingPathComponent("\(id.uuidString).jpg")
    }

    /// Redraws at 2x so a preview holds about 320 by 400 pixels, then encodes a compact JPEG.
    nonisolated private static func shrink(_ image: UIImage) -> (UIImage, Data?) {
        let size = CGSize(width: width, height: image.size.height * width / max(image.size.width, 1))
        let format = UIGraphicsImageRendererFormat()
        format.scale = 2
        format.opaque = true
        let small = UIGraphicsImageRenderer(size: size, format: format).image { _ in
            image.draw(in: CGRect(origin: .zero, size: size))
        }
        return (small, small.jpegData(compressionQuality: 0.6))
    }
}

extension Logger {
    static let thumbnails = Logger(subsystem: "app.slate.browser.ios", category: "thumbnails")
}
