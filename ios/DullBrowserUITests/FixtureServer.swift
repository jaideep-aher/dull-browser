import Foundation
import Network

/// Runs inside the test runner on either the simulator or a physical iPhone.
/// Local pages avoid third-party outages, cookie walls and changing search results.
final class FixtureServer {
    private var listener: NWListener?
    private let queue = DispatchQueue(label: "DullBrowserUITests.FixtureServer")
    private(set) var port: UInt16 = 0

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

    func url(_ path: String = "/one") -> String { "http://127.0.0.1:\(port)\(path)" }
    func stop() { listener?.cancel(); listener = nil }
}
