import SwiftUI
import WebKit

struct ContentView: View {
    @StateObject private var model = BrowserModel()
    @AppStorage("introSeen") private var introSeen = false

    var body: some View {
        ZStack {
            Theme.paper.ignoresSafeArea()
            if model.showingNewTab {
                NewTabView { model.open($0) }
            } else {
                VStack(spacing: 0) {
                    ZStack {
                        WebViewHost(webView: model.webView)
                            .id(ObjectIdentifier(model.webView))
                        if let host = model.blockedHost {
                            BlockedView(host: host)
                        } else if let error = model.loadError {
                            LoadErrorView(message: error)
                        }
                    }
                    BrowserBar(model: model)
                }
            }
            if !introSeen {
                FirstRunView { introSeen = true }
                    .transition(.opacity)
            }
        }
        .onOpenURL { model.openIncoming($0) }
        .task {
            #if DEBUG
            // Launch argument for simulator checks: -DebugOpen <address>
            if let address = UserDefaults.standard.string(forKey: "DebugOpen") { model.open(address) }
            #endif
        }
        .animation(.easeOut(duration: 0.2), value: introSeen)
        .preferredColorScheme(.light)
        .tint(Theme.ink)
    }
}

struct WebViewHost: UIViewRepresentable {
    let webView: WKWebView

    func makeUIView(context: Context) -> WKWebView { webView }

    func updateUIView(_ uiView: WKWebView, context: Context) {}
}
