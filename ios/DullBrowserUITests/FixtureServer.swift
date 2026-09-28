import Foundation
import Network

/// Runs inside the test runner on either the simulator or a physical iPhone.
/// Local pages avoid third-party outages, cookie walls and changing search results.
final class FixtureServer {
    private var listener: NWListener?
    private let queue = DispatchQueue(label: "DullBrowserUITests.FixtureServer")
    private(set) var port: UInt16 = 0
    private var visits = 0

    func start(completion: @escaping () -> Void) throws {
        let listener = try NWListener(using: .tcp, on: .any)
        self.listener = listener
        listener.stateUpdateHandler = { [weak self] state in
            if case .ready = state {
                self?.port = listener.port!.rawValue
                completion()
            }
        }
        listener.newConnectionHandler = { [weak self] connection in
            connection.start(queue: self?.queue ?? .global())
            connection.receive(minimumIncompleteLength: 1, maximumLength: 16384) { data, _, _, _ in
                let request = data.flatMap { String(data: $0, encoding: .utf8) } ?? ""
                let path = request.split(separator: " ").dropFirst().first.map(String.init) ?? "/"
                let response: String
                if path == "/redirect" {
                    response = "HTTP/1.1 302 Found\r\nLocation: https://youtube.com/\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                } else if path == "/count" {
                    let visit = (self?.visits ?? 0) + 1
                    self?.visits = visit
                    let body = """
                    <!doctype html><html><head><meta name="viewport" content="width=device-width, initial-scale=1"><title>Visit \(visit)</title></head>
                    <body style="min-height: 150vh"><h1>Visit \(visit)</h1></body></html>
                    """
                    response = "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nCache-Control: no-store\r\nContent-Length: \(body.utf8.count)\r\nConnection: close\r\n\r\n\(body)"
                } else if path == "/ua" {
                    let agent = Self.header("User-Agent", in: request) ?? ""
                    response = Self.page(agent.contains("Mobile") ? "Mobile layout" : "Desktop layout")
                } else if path == "/missing" {
                    response = Self.page("Missing page", status: "404 Not Found")
                } else if path == "/cookie" {
                    let kept = Self.header("Cookie", in: request)?.contains("dull=1") == true
                    response = Self.page(kept ? "Cookie kept" : "Cookie none",
                                         extraHeaders: "Set-Cookie: dull=1; Max-Age=3600; Path=/\r\n")
                } else if path == "/script" {
                    let body = """
                    <!doctype html><html><head><meta name="viewport" content="width=device-width, initial-scale=1"><title>Script off</title></head>
                    <body><h1>Script off</h1>
                    <script>document.title = "Script ran"; document.querySelector("h1").textContent = "Script ran";</script>
                    </body></html>
                    """
                    response = "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nCache-Control: no-store\r\nContent-Length: \(body.utf8.count)\r\nConnection: close\r\n\r\n\(body)"
                } else {
                    let title = path == "/two" ? "Page Two" : "Page One"
                    let body = """
                    <!doctype html><html><head><meta name="viewport" content="width=device-width, initial-scale=1"><title>\(title)</title></head>
                    <body><h1>\(title)</h1><label>Note <input aria-label="Note"></label>
                    <p><a href="/two">Next page</a></p>
                    <p><a href="/two" target="_blank">Open new window</a></p>
                    <p><a href="https://youtube.com">Blocked video</a></p>
                    <p><a href="/redirect">Redirect to video</a></p>
                    </body></html>
                    """
                    response = "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: \(body.utf8.count)\r\nConnection: close\r\n\r\n\(body)"
                }
                connection.send(content: Data(response.utf8), completion: .contentProcessed { _ in connection.cancel() })
            }
        }
        listener.start(queue: queue)
    }

    private static func header(_ name: String, in request: String) -> String? {
        request.components(separatedBy: "\r\n")
            .first { $0.lowercased().hasPrefix(name.lowercased() + ":") }
            .map { String($0.dropFirst(name.count + 1)).trimmingCharacters(in: .whitespaces) }
    }

    /// A page whose title and only heading are the same text, so tests can find it by name.
    private static func page(_ title: String, status: String = "200 OK", extraHeaders: String = "") -> String {
        let body = """
        <!doctype html><html><head><meta name="viewport" content="width=device-width, initial-scale=1"><title>\(title)</title></head>
        <body><h1>\(title)</h1></body></html>
        """
        return "HTTP/1.1 \(status)\r\nContent-Type: text/html; charset=utf-8\r\nCache-Control: no-store\r\n\(extraHeaders)"
            + "Content-Length: \(body.utf8.count)\r\nConnection: close\r\n\r\n\(body)"
    }

    func url(_ path: String = "/one") -> String { "http://127.0.0.1:\(port)\(path)" }
    func stop() { listener?.cancel(); listener = nil }
}
