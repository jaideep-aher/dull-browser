import Combine
import SwiftUI
import UIKit
import WebKit
import os

/// One web view and the gate in front of it. Every page load, redirect and response passes
/// through the navigation delegate below before anything is shown.
@MainActor
final class BrowserModel: NSObject, ObservableObject, Identifiable {
    let id: UUID
    var openInNewTab: ((URL) -> Void)?
    @Published private(set) var title = ""
    private var restoredURL: URL?
    private var requestedURL: URL?
    private var committedURL: URL?
    @Published private(set) var webView: WKWebView
    @Published private(set) var showingNewTab = true
    @Published private(set) var blockedHost: String?
    @Published private(set) var loadError: String?
    @Published private(set) var url: URL?
    @Published private(set) var isLoading = false
    @Published private(set) var progress = 0.0
    @Published private(set) var canGoBack = false
    @Published private(set) var canGoForward = false

    private var blocker: SiteBlocker { .shared }
    private var ruleLists: [WKContentRuleList] = []
    private var observers: Set<AnyCancellable> = []
    /// Bumps on each navigation so a late DNS answer for an old one is dropped.
    private var generation = 0

    init(id: UUID = UUID(), restoredURL: URL? = nil) {
        self.id = id
        self.restoredURL = restoredURL
        webView = Self.makeWebView(ruleLists: [])
        super.init()
        attach(webView)
        Task {
            let lists = await SubresourceRules.load()
            ruleLists = lists
            lists.forEach(webView.configuration.userContentController.add)
        }
    }

    var savedAddress: String? {
        if let restoredURL { return restoredURL.absoluteString }
        if showingNewTab { return nil }
        if let blockedHost { return "https://\(blockedHost)" }
        return (loadError != nil ? requestedURL : (url ?? requestedURL))?.absoluteString
    }

    var tabTitle: String {
        if let blockedHost { return blockedHost }
        if let restoredURL { return restoredURL.host ?? "Page" }
        if showingNewTab { return "New tab" }
        return title.isEmpty ? (url?.host ?? "Loading…") : title
    }

    func restoreIfNeeded() {
        guard let target = restoredURL else { return }
        restoredURL = nil
        load(target)
    }

    func deactivate() {
        webView.pauseAllMediaPlayback(completionHandler: nil)
    }

    func close() {
        generation += 1
        webView.stopLoading()
        webView.pauseAllMediaPlayback(completionHandler: nil)
        webView.navigationDelegate = nil
        webView.uiDelegate = nil
        observers.removeAll()
    }

    var addressForDisplay: String {
        if let blockedHost { return blockedHost }
        return url?.host ?? url?.absoluteString ?? ""
    }

    var addressForEditing: String {
        if let blockedHost { return "https://\(blockedHost)" }
        return url?.absoluteString ?? ""
    }

    var canStepBack: Bool { canGoBack || blockedHost != nil || loadError != nil || !showingNewTab }

    func open(_ input: String) {
        guard let url = BrowserInput.url(for: input) else { return }
        load(url)
    }

    /// Links handed over by other apps: dullbrowser://open-url?url=<encoded address>
    func openIncoming(_ url: URL) {
        guard url.scheme?.lowercased() == "dullbrowser",
              let target = URLComponents(url: url, resolvingAgainstBaseURL: false)?
                .queryItems?.first(where: { $0.name == "url" })?.value
        else { return }
        open(target)
    }

    func load(_ url: URL) {
        generation += 1
        restoredURL = nil
        requestedURL = url
        self.url = url
        blockedHost = nil
        loadError = nil
        showingNewTab = false
        webView.load(URLRequest(url: url))
    }

    func goBack() {
        if blockedHost != nil || loadError != nil {
            blockedHost = nil
            loadError = nil
            if committedURL == nil {
                newTab()
            } else {
                requestedURL = committedURL
                url = committedURL
            }
        } else if webView.canGoBack {
            webView.goBack()
        } else {
            newTab()
        }
    }

    func goForward() {
        blockedHost = nil
        loadError = nil
        webView.goForward()
    }

    func reloadOrStop() {
        if webView.isLoading {
            webView.stopLoading()
        } else if blockedHost == nil {
            if loadError != nil, let requestedURL {
                load(requestedURL)
            } else {
                webView.reload()
            }
        }
    }

    func newTab() {
        generation += 1
        restoredURL = nil
        requestedURL = nil
        committedURL = nil
        title = ""
        webView.pauseAllMediaPlayback(completionHandler: nil)
        webView.stopLoading()
        webView.navigationDelegate = nil
        webView.uiDelegate = nil
        observers.removeAll()
        webView = Self.makeWebView(ruleLists: ruleLists)
        attach(webView)
        blockedHost = nil
        loadError = nil
        showingNewTab = true
    }

    private static func makeWebView(ruleLists: [WKContentRuleList]) -> WKWebView {
        let config = WKWebViewConfiguration()
        config.allowsInlineMediaPlayback = true
        ruleLists.forEach(config.userContentController.add)
        let view = WKWebView(frame: .zero, configuration: config)
        view.allowsBackForwardNavigationGestures = true
        view.isOpaque = false
        view.backgroundColor = UIColor(Theme.paper)
        #if DEBUG
        view.isInspectable = true
        #endif
        return view
    }

    private func attach(_ view: WKWebView) {
        view.navigationDelegate = self
        view.uiDelegate = self
        url = view.url
        view.publisher(for: \.title).sink { [weak self] in self?.title = $0 ?? "" }.store(in: &observers)
        view.publisher(for: \.url).sink { [weak self] in self?.url = $0 }.store(in: &observers)
        view.publisher(for: \.isLoading).sink { [weak self] in self?.isLoading = $0 }.store(in: &observers)
        view.publisher(for: \.estimatedProgress).sink { [weak self] in self?.progress = $0 }.store(in: &observers)
        view.publisher(for: \.canGoBack).sink { [weak self] in self?.canGoBack = $0 }.store(in: &observers)
        view.publisher(for: \.canGoForward).sink { [weak self] in self?.canGoForward = $0 }.store(in: &observers)
    }

    /// Shows the closed page. Nothing from the blocked host is on screen behind it.
    private func showBlocked(_ host: String, in view: WKWebView, stage: String, committed: Bool = false) {
        guard view === webView else { return }
        Logger.blocking.info("Closed \(host, privacy: .public) at \(stage, privacy: .public)")
        view.pauseAllMediaPlayback(completionHandler: nil)
        blockedHost = host.lowercased()
        loadError = nil
        showingNewTab = false
        guard committed else { return }
        // Only reached if a page on a listed host got past every earlier check.
        view.stopLoading()
        if view.canGoBack {
            view.goBack()
        } else {
            view.loadHTMLString("", baseURL: nil)
        }
    }
}

extension BrowserModel: WKNavigationDelegate {
    func webView(_ webView: WKWebView, decidePolicyFor action: WKNavigationAction) async -> WKNavigationActionPolicy {
        guard webView === self.webView, let url = action.request.url else { return .cancel }
        let isMainFrame = action.targetFrame?.isMainFrame ?? true

        if isMainFrame {
            if let host = blocker.listedHost(for: url) {
                showBlocked(host, in: webView, stage: host == url.host ? "navigation" : "hidden link")
                return .cancel
            }
        } else if blocker.isListed(host: url.host) {
            return .cancel
        }

        switch url.scheme?.lowercased() ?? "" {
        case "http", "https":
            if isMainFrame { requestedURL = url }
            if isMainFrame, let host = url.host {
                // Start the lookup while the request goes out; the response waits on it.
                Task.detached(priority: .userInitiated) { _ = await FamilyDNS.shared.isFiltered(host) }
            }
            return .allow
        case "about", "data", "blob":
            if isMainFrame { requestedURL = url }
            return .allow
        case "mailto", "tel", "sms", "facetime":
            if action.navigationType == .linkActivated { await UIApplication.shared.open(url) }
            return .cancel
        case "intent":
            let fallback = NavigationHops.extract(url.absoluteString).dropFirst().first {
                let scheme = NavigationHops.schemeOf($0)
                return scheme == "http" || scheme == "https"
            }
            if let fallback, let fallbackURL = URL(string: fallback) { load(fallbackURL) }
            return .cancel
        default:
            // Handoffs to other apps are not opened from here.
            return .cancel
        }
    }

    func webView(_ webView: WKWebView, decidePolicyFor response: WKNavigationResponse) async -> WKNavigationResponsePolicy {
        guard webView === self.webView else { return .cancel }
        guard let url = response.response.url else { return .allow }
        guard response.isForMainFrame else {
            return blocker.isListed(host: url.host) ? .cancel : .allow
        }
        if let host = blocker.listedHost(for: url) {
            showBlocked(host, in: webView, stage: "response")
            return .cancel
        }
        guard let scheme = url.scheme?.lowercased(), scheme == "http" || scheme == "https",
              let host = url.host
        else { return .allow }

        let started = generation
        // Suspends here; the lookup itself runs off the main thread.
        let filtered = await FamilyDNS.shared.isFiltered(host)
        guard webView === self.webView, started == generation else { return .cancel }
        guard filtered else { return .allow }
        showBlocked(host, in: webView, stage: "family dns")
        return .cancel
    }

    func webView(_ webView: WKWebView, didReceiveServerRedirectForProvisionalNavigation navigation: WKNavigation!) {
        guard let url = webView.url, let host = blocker.listedHost(for: url) else { return }
        webView.stopLoading()
        showBlocked(host, in: webView, stage: "server redirect")
    }

    func webView(_ webView: WKWebView, didStartProvisionalNavigation navigation: WKNavigation!) {
        guard webView === self.webView else { return }
        generation += 1
    }

    func webView(_ webView: WKWebView, didCommit navigation: WKNavigation!) {
        guard webView === self.webView, let url = webView.url else { return }
        // WebKit can silently substitute about:blank for a URL using a restricted port,
        // without sending didFail. Preserve the requested address and show a useful error.
        if url.absoluteString == "about:blank", let target = requestedURL,
           target.scheme == "http" || target.scheme == "https" {
            self.url = target
            loadError = "This address could not be opened. Check the address and try again."
            return
        }
        if let host = blocker.listedHost(for: url) {
            showBlocked(host, in: webView, stage: "commit", committed: true)
        } else {
            committedURL = url
            requestedURL = url
            loadError = nil
        }
    }

    func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
        report(error, in: webView)
    }

    func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
        report(error, in: webView)
    }

    func webViewWebContentProcessDidTerminate(_ webView: WKWebView) {
        webView.reload()
    }

    private func report(_ error: Error, in view: WKWebView) {
        let error = error as NSError
        let cancelled = error.domain == NSURLErrorDomain && error.code == NSURLErrorCancelled
        // 102 is a frame load interrupted by policy. 101 means an unsupported URL
        // and must produce a visible error rather than leaving a blank page.
        let stoppedByPolicy = error.domain == "WebKitErrorDomain" && error.code == 102
        guard view === webView, !cancelled, !stoppedByPolicy, blockedHost == nil else { return }
        loadError = error.localizedDescription
    }
}

extension BrowserModel: WKUIDelegate {
    /// New-window links get their own tab, through the same blocking gate.
    func webView(
        _ webView: WKWebView,
        createWebViewWith configuration: WKWebViewConfiguration,
        for action: WKNavigationAction,
        windowFeatures: WKWindowFeatures
    ) -> WKWebView? {
        if action.targetFrame == nil, let target = action.request.url {
            if let openInNewTab { openInNewTab(target) }
            else { load(target) }
        }
        return nil
    }
}
