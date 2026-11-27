import Foundation
import CryptoKit
import Security

/** An error whose message is written for the user. [unsupported] = the TV doesn't have that feature. */
struct UserFacing: LocalizedError {
    let message: String
    var unsupported = false
    init(_ m: String, unsupported: Bool = false) { message = m; self.unsupported = unsupported }
    var errorDescription: String? { message }
}

func missing(_ k: RemoteKey, _ brand: String) -> UserFacing { UserFacing("\(k.word) isn't available on \(brand)", unsupported: true) }

/** A paired TV presented a different certificate than the one pinned. */
struct CertChanged: LocalizedError {
    var errorDescription: String? { "This TV's security certificate changed. If you reset or replaced it, remove it and add it again." }
}

/** Only talk to devices on the local network. */
enum HostValidator {
    static func isPrivateIPv4(_ host: String) -> Bool {
        let p = host.trimmingCharacters(in: .whitespaces).split(separator: ".", omittingEmptySubsequences: false)
        guard p.count == 4 else { return false }
        let o = p.compactMap { s -> Int? in (s.count >= 1 && s.count <= 3 && s.allSatisfy(\.isNumber)) ? Int(s) : nil }
        guard o.count == 4, o.allSatisfy({ $0 <= 255 }) else { return false }
        return o[0] == 10 || (o[0] == 172 && (16...31).contains(o[1])) || (o[0] == 192 && o[1] == 168)
    }
}

/** SHA-256 (base64) of a certificate's DER bytes. */
func fingerprint(_ c: SecCertificate) -> String {
    Data(SHA256.hash(data: SecCertificateCopyData(c) as Data)).base64EncodedString()
}

func leafCertificate(_ trust: SecTrust) -> SecCertificate? {
    (SecTrustCopyCertificateChain(trust) as? [SecCertificate])?.first
}

/** No redirects: a device on the network can't bounce us to some other (public) address. */
final class NoRedirect: NSObject, URLSessionTaskDelegate {
    func urlSession(_ session: URLSession, task: URLSessionTask, willPerformHTTPRedirection response: HTTPURLResponse,
                    newRequest request: URLRequest, completionHandler: @escaping (URLRequest?) -> Void) { completionHandler(nil) }
}

enum Http {
    static let plain: URLSession = {
        let c = URLSessionConfiguration.ephemeral
        c.timeoutIntervalForRequest = 8
        return URLSession(configuration: c, delegate: NoRedirect(), delegateQueue: nil)
    }()
    static let probe: URLSession = {
        let c = URLSessionConfiguration.ephemeral
        c.timeoutIntervalForRequest = 1.2
        return URLSession(configuration: c, delegate: NoRedirect(), delegateQueue: nil)
    }()

    static func get(_ url: String, session: URLSession = plain) async throws -> String {
        guard let u = URL(string: url) else { throw UserFacing("Bad address") }
        let (data, resp) = try await session.data(from: u)
        guard let h = resp as? HTTPURLResponse, (200..<300).contains(h.statusCode) else { throw UserFacing("The TV said no (\((resp as? HTTPURLResponse)?.statusCode ?? 0))") }
        return String(decoding: data, as: UTF8.self)
    }

    @discardableResult
    static func post(_ url: String) async throws -> Int {
        guard let u = URL(string: url) else { throw UserFacing("Bad address") }
        var r = URLRequest(url: u)
        r.httpMethod = "POST"
        r.httpBody = Data()
        let (_, resp) = try await plain.data(for: r)
        let code = (resp as? HTTPURLResponse)?.statusCode ?? 0
        guard (200..<300).contains(code) else { throw UserFacing("The TV said no (\(code))") }
        return code
    }
}

/** A value that arrives once (or fails), awaited with a timeout. Cancelling the wait resolves it. */
final class Once<T> {
    private var cont: CheckedContinuation<T, Error>?
    private var result: Result<T, Error>?
    private let lock = NSLock()

    func resolve(_ r: Result<T, Error>) {
        lock.lock()
        if result != nil { lock.unlock(); return }
        result = r
        let c = cont; cont = nil
        lock.unlock()
        c?.resume(with: r)
    }
    func succeed(_ v: T) { resolve(.success(v)) }
    func fail(_ e: Error) { resolve(.failure(e)) }

    private func value() async throws -> T {
        try await withTaskCancellationHandler {
            try await withCheckedThrowingContinuation { (c: CheckedContinuation<T, Error>) in
                lock.lock()
                if let r = result { lock.unlock(); c.resume(with: r) } else { cont = c; lock.unlock() }
            }
        } onCancel: { [weak self] in self?.fail(CancellationError()) }
    }

    func wait(_ seconds: Double, _ message: String = "The TV didn't answer in time") async throws -> T {
        try await withThrowingTaskGroup(of: T.self) { g in
            g.addTask { try await self.value() }
            g.addTask {
                try await Task.sleep(nanoseconds: UInt64(seconds * 1_000_000_000))
                throw UserFacing(message)
            }
            defer { g.cancelAll() }
            guard let v = try await g.next() else { throw UserFacing(message) }
            return v
        }
    }
}

/** URLSession delegate that pins the TV's certificate (first use records it) and reports socket open / close. */
final class PinDelegate: NSObject, URLSessionWebSocketDelegate {
    let expected: String?
    private(set) var seen: String?
    private(set) var changed = false
    var onOpen: (() -> Void)?
    var onClose: (() -> Void)?

    init(expected: String?) { self.expected = expected }

    func urlSession(_ session: URLSession, didReceive challenge: URLAuthenticationChallenge,
                    completionHandler: @escaping (URLSession.AuthChallengeDisposition, URLCredential?) -> Void) {
        guard challenge.protectionSpace.authenticationMethod == NSURLAuthenticationMethodServerTrust,
              let trust = challenge.protectionSpace.serverTrust, let leaf = leafCertificate(trust) else {
            completionHandler(.performDefaultHandling, nil); return
        }
        let fp = fingerprint(leaf)
        if let e = expected, e != fp { changed = true; completionHandler(.cancelAuthenticationChallenge, nil); return }
        seen = fp
        completionHandler(.useCredential, URLCredential(trust: trust))
    }

    func urlSession(_ session: URLSession, webSocketTask: URLSessionWebSocketTask, didOpenWithProtocol protocol: String?) { onOpen?() }
    func urlSession(_ session: URLSession, webSocketTask: URLSessionWebSocketTask, didCloseWith closeCode: URLSessionWebSocketTask.CloseCode, reason: Data?) { onClose?() }
}

/** A text WebSocket (ws or wss). Messages arrive on [onText]; failures on [onFail]. */
final class WS {
    let delegate: PinDelegate
    private let session: URLSession
    private let task: URLSessionWebSocketTask
    var onText: ((String) -> Void)?
    var onFail: ((Error) -> Void)?

    init(_ url: URL, pin: String?) {
        delegate = PinDelegate(expected: pin)
        let c = URLSessionConfiguration.ephemeral
        c.timeoutIntervalForRequest = 30
        session = URLSession(configuration: c, delegate: delegate, delegateQueue: nil)
        task = session.webSocketTask(with: url)
    }

    func start() { task.resume(); receive() }

    private func receive() {
        task.receive { [weak self] r in
            guard let self else { return }
            switch r {
            case .success(let m):
                switch m {
                case .string(let s): self.onText?(s)
                case .data(let d): self.onText?(String(decoding: d, as: UTF8.self))
                @unknown default: break
                }
                self.receive()
            case .failure(let e):
                self.onFail?(self.delegate.changed ? CertChanged() : e)
            }
        }
    }

    func send(_ s: String) async throws { try await task.send(.string(s)) }

    func close() {
        task.cancel(with: .normalClosure, reason: nil)
        session.invalidateAndCancel()
    }
}

func json(_ s: String) -> [String: Any]? {
    (try? JSONSerialization.jsonObject(with: Data(s.utf8))) as? [String: Any]
}

func jsonString(_ o: [String: Any]) -> String {
    (try? JSONSerialization.data(withJSONObject: o)).map { String(decoding: $0, as: UTF8.self) } ?? "{}"
}

func enc(_ s: String) -> String { s.addingPercentEncoding(withAllowedCharacters: .alphanumerics) ?? s }

func xmlText(_ s: String) -> String {
    s.replacingOccurrences(of: "&lt;", with: "<").replacingOccurrences(of: "&gt;", with: ">")
        .replacingOccurrences(of: "&quot;", with: "\"").replacingOccurrences(of: "&apos;", with: "'")
        .replacingOccurrences(of: "&#39;", with: "'").replacingOccurrences(of: "&amp;", with: "&")
        .trimmingCharacters(in: .whitespaces)
}

func firstMatch(_ pattern: String, in s: String) -> [String]? {
    guard let re = try? NSRegularExpression(pattern: pattern),
          let m = re.firstMatch(in: s, range: NSRange(s.startIndex..., in: s)) else { return nil }
    return (0..<m.numberOfRanges).map { i in
        let r = m.range(at: i)
        return r.location == NSNotFound ? "" : (Range(r, in: s).map { String(s[$0]) } ?? "")
    }
}

func allMatches(_ pattern: String, in s: String) -> [[String]] {
    guard let re = try? NSRegularExpression(pattern: pattern) else { return [] }
    return re.matches(in: s, range: NSRange(s.startIndex..., in: s)).map { m in
        (0..<m.numberOfRanges).map { i in
            let r = m.range(at: i)
            return r.location == NSNotFound ? "" : (Range(r, in: s).map { String(s[$0]) } ?? "")
        }
    }
}
