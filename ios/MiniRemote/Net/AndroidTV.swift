import Foundation
import Network
import Security
import CryptoKit

/*
 * Android TV / Google TV remote, protocol v2 (what the Google TV phone app uses).
 *  - Pairing on port 6467: mutual TLS + small protobuf messages; the TV shows a 6-character
 *    hex code and we prove we saw it by hashing both TLS public keys with it.
 *  - Control on port 6466: mutual TLS with the same client certificate; key presses as protobuf.
 * The client key lives in the Keychain (this device only). Protobuf is hand-encoded.
 */

/* ------------------------------------------------------------ tiny protobuf */

struct Pb {
    var out = Data()
    mutating func raw(_ v: UInt64) {
        var x = v
        while x >= 0x80 { out.append(UInt8(x & 0x7F) | 0x80); x >>= 7 }
        out.append(UInt8(x))
    }
    mutating func int(_ f: Int, _ v: UInt64) { raw(UInt64(f << 3)); raw(v) }
    mutating func bytes(_ f: Int, _ b: Data) { raw(UInt64((f << 3) | 2)); raw(UInt64(b.count)); out.append(b) }
    mutating func str(_ f: Int, _ s: String) { bytes(f, Data(s.utf8)) }
    mutating func msg(_ f: Int, _ build: (inout Pb) -> Void) { var p = Pb(); build(&p); bytes(f, p.out) }

    static func make(_ build: (inout Pb) -> Void) -> Data { var p = Pb(); build(&p); return p.out }

    /** Field number -> values (UInt64 for varints, Data for length-delimited). */
    static func parse(_ data: Data) throws -> [Int: [Any]] {
        let b = [UInt8](data)
        var i = 0
        var m: [Int: [Any]] = [:]
        func varint() throws -> UInt64 {
            var r: UInt64 = 0, s: UInt64 = 0
            while true {
                guard i < b.count, s < 64 else { throw UserFacing("Bad message from the TV") }
                let x = b[i]; i += 1
                r |= UInt64(x & 0x7F) << s
                if x & 0x80 == 0 { return r }
                s += 7
            }
        }
        while i < b.count {
            let tag = try varint()
            let field = Int(tag >> 3)
            let v: Any
            switch tag & 7 {
            case 0: v = try varint()
            case 2:
                let n = Int(try varint())
                guard n >= 0, i + n <= b.count else { throw UserFacing("Bad message from the TV") }
                v = Data(b[i..<(i + n)]); i += n
            case 1: guard i + 8 <= b.count else { throw UserFacing("Bad message from the TV") }; i += 8; v = UInt64(0)
            case 5: guard i + 4 <= b.count else { throw UserFacing("Bad message from the TV") }; i += 4; v = UInt64(0)
            default: throw UserFacing("Bad message from the TV")
            }
            m[field, default: []].append(v)
        }
        return m
    }
}

private func long(_ m: [Int: [Any]], _ f: Int) -> UInt64? { m[f]?.first as? UInt64 }
private func sub(_ m: [Int: [Any]], _ f: Int) -> [Int: [Any]]? { (m[f]?.first as? Data).flatMap { try? Pb.parse($0) } }

/* ------------------------------------------------------------ DER / X.509 */

/** Just enough DER to build a self-signed X.509 certificate (iOS has no API for that). */
enum DER {
    static func len(_ n: Int) -> Data {
        if n < 128 { return Data([UInt8(n)]) }
        var b = [UInt8](); var x = n
        while x > 0 { b.insert(UInt8(x & 0xFF), at: 0); x >>= 8 }
        return Data([0x80 | UInt8(b.count)] + b)
    }
    static func tlv(_ tag: UInt8, _ c: Data) -> Data { Data([tag]) + len(c.count) + c }
    static func seq(_ parts: Data...) -> Data { tlv(0x30, parts.reduce(Data(), +)) }
    static func set(_ c: Data) -> Data { tlv(0x31, c) }
    static func int(_ bytes: [UInt8]) -> Data {
        var b = bytes
        while b.count > 1 && b[0] == 0 { b.removeFirst() }
        if b.isEmpty { b = [0] }
        if b[0] & 0x80 != 0 { b.insert(0, at: 0) }
        return tlv(0x02, Data(b))
    }
    static func oid(_ s: String) -> Data {
        let p = s.split(separator: ".").compactMap { Int($0) }
        var out = [UInt8(p[0] * 40 + p[1])]
        for v in p.dropFirst(2) {
            var chunk = [UInt8(v & 0x7F)]
            var x = v >> 7
            while x > 0 { chunk.insert(UInt8(x & 0x7F) | 0x80, at: 0); x >>= 7 }
            out += chunk
        }
        return tlv(0x06, Data(out))
    }
    static let null = Data([0x05, 0x00])
    static func utf8(_ s: String) -> Data { tlv(0x0C, Data(s.utf8)) }
    static func utc(_ d: Date) -> Data {
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_US_POSIX"); f.timeZone = TimeZone(identifier: "UTC"); f.dateFormat = "yyMMddHHmmss'Z'"
        return tlv(0x17, Data(f.string(from: d).utf8))
    }
    static func bitString(_ d: Data) -> Data { tlv(0x03, Data([0]) + d) }

    /** (modulus, exponent) from a PKCS#1 RSAPublicKey, leading zeros removed. */
    static func rsaParts(_ der: Data) -> (Data, Data)? {
        let b = [UInt8](der); var i = 0
        func readLen() -> Int? {
            guard i < b.count else { return nil }
            var l = Int(b[i]); i += 1
            if l & 0x80 != 0 {
                let n = l & 0x7F; l = 0
                for _ in 0..<n { guard i < b.count else { return nil }; l = (l << 8) | Int(b[i]); i += 1 }
            }
            return l
        }
        func readInt() -> Data? {
            guard i < b.count, b[i] == 0x02 else { return nil }
            i += 1
            guard let l = readLen(), i + l <= b.count else { return nil }
            var v = Array(b[i..<(i + l)]); i += l
            while v.count > 1 && v[0] == 0 { v.removeFirst() }
            return Data(v)
        }
        guard b.first == 0x30 else { return nil }
        i = 1
        _ = readLen()
        guard let m = readInt(), let e = readInt() else { return nil }
        return (m, e)
    }
}

/** This phone's identity for Android TVs: an RSA key in the Keychain plus a self-signed certificate. */
enum ClientIdentity {
    static let tag = Data("com.krishna.miniremote.atv".utf8)
    static let label = "Mini Remote ATV"

    static func find() -> SecIdentity? {
        for q in [[kSecClass as String: kSecClassIdentity, kSecAttrLabel as String: label, kSecReturnRef as String: true],
                  [kSecClass as String: kSecClassIdentity, kSecReturnRef as String: true]] as [[String: Any]] {
            var out: CFTypeRef?
            if SecItemCopyMatching(q as CFDictionary, &out) == errSecSuccess, let o = out, CFGetTypeID(o) == SecIdentityGetTypeID() {
                return (o as! SecIdentity)
            }
        }
        return nil
    }

    static func get() throws -> SecIdentity {
        if let id = find() { return id }
        var err: Unmanaged<CFError>?
        let attrs: [String: Any] = [
            kSecAttrKeyType as String: kSecAttrKeyTypeRSA,
            kSecAttrKeySizeInBits as String: 2048,
            kSecPrivateKeyAttrs as String: [
                kSecAttrIsPermanent as String: true,
                kSecAttrApplicationTag as String: tag,
                kSecAttrLabel as String: label,
                kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
            ] as [String: Any],
        ]
        guard let priv = SecKeyCreateRandomKey(attrs as CFDictionary, &err), let pub = SecKeyCopyPublicKey(priv),
              let pkcs1 = SecKeyCopyExternalRepresentation(pub, &err) as Data? else {
            throw UserFacing("Couldn't create this phone's TV key")
        }
        var serial = [UInt8](repeating: 0, count: 8)
        _ = SecRandomCopyBytes(kSecRandomDefault, serial.count, &serial)
        serial[0] &= 0x7F; serial[0] |= 0x01
        let name = DER.seq(DER.set(DER.seq(DER.oid("2.5.4.3"), DER.utf8("Mini Remote"))))
        let sigAlg = DER.seq(DER.oid("1.2.840.113549.1.1.11"), DER.null)
        let spki = DER.seq(DER.seq(DER.oid("1.2.840.113549.1.1.1"), DER.null), DER.bitString(pkcs1))
        let now = Date()
        let tbs = DER.seq(
            DER.tlv(0xA0, DER.int([2])), DER.int(serial), sigAlg, name,
            DER.seq(DER.utc(now.addingTimeInterval(-86_400)), DER.utc(now.addingTimeInterval(20 * 365 * 86_400))),
            name, spki)
        guard let sig = SecKeyCreateSignature(priv, .rsaSignatureMessagePKCS1v15SHA256, tbs as CFData, &err) as Data?,
              let cert = SecCertificateCreateWithData(nil, DER.seq(tbs, sigAlg, DER.bitString(sig)) as CFData) else {
            throw UserFacing("Couldn't create this phone's TV certificate")
        }
        let add: [String: Any] = [kSecClass as String: kSecClassCertificate, kSecValueRef as String: cert,
                                  kSecAttrLabel as String: label,
                                  kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly]
        SecItemAdd(add as CFDictionary, nil)
        guard let id = find() else { throw UserFacing("Couldn't set up this phone's TV identity") }
        return id
    }

    static func publicParts(_ id: SecIdentity) -> (Data, Data)? {
        var key: SecKey?
        guard SecIdentityCopyPrivateKey(id, &key) == errSecSuccess, let k = key, let pub = SecKeyCopyPublicKey(k),
              let der = SecKeyCopyExternalRepresentation(pub, nil) as Data? else { return nil }
        return DER.rsaParts(der)
    }

    /** Forgets this phone's Android TV identity (TVs will ask to pair again). */
    static func reset() {
        SecItemDelete([kSecClass as String: kSecClassCertificate, kSecAttrLabel as String: label] as CFDictionary)
        SecItemDelete([kSecClass as String: kSecClassKey, kSecAttrApplicationTag as String: tag] as CFDictionary)
    }
}

/* ------------------------------------------------------------ TLS link */

/** A mutual-TLS connection that speaks length-prefixed (varint) messages. */
final class TlsLink {
    final class Peer { var leaf: SecCertificate?; var changed = false }
    let peer = Peer()
    private let conn: NWConnection
    private let queue = DispatchQueue(label: "atv.tls")

    init(host: String, port: UInt16, identity: SecIdentity, pin: String?) {
        let tls = NWProtocolTLS.Options()
        let sp = tls.securityProtocolOptions
        if let sid = sec_identity_create(identity) { sec_protocol_options_set_local_identity(sp, sid) }
        let peer = self.peer
        // TVs use self-signed certificates: pin on first use instead of CA checks.
        sec_protocol_options_set_verify_block(sp, { _, trust, complete in
            let t = sec_trust_copy_ref(trust).takeRetainedValue()
            guard let leaf = leafCertificate(t) else { complete(false); return }
            if let pin, fingerprint(leaf) != pin { peer.changed = true; complete(false); return }
            peer.leaf = leaf
            complete(true)
        }, queue)
        conn = NWConnection(host: NWEndpoint.Host(host), port: NWEndpoint.Port(rawValue: port)!, using: NWParameters(tls: tls))
    }

    func open(_ timeout: Double = 8) async throws {
        let ready = Once<Void>()
        let peer = self.peer
        conn.stateUpdateHandler = { st in
            switch st {
            case .ready: ready.succeed(())
            case .failed(let e): ready.fail(peer.changed ? CertChanged() : e)
            case .waiting(let e): ready.fail(peer.changed ? CertChanged() : e)
            case .cancelled: ready.fail(UserFacing("Connection closed"))
            default: break
            }
        }
        conn.start(queue: queue)
        do { try await ready.wait(timeout, "Couldn't reach the TV") } catch { conn.cancel(); throw error }
    }

    func send(_ body: Data) async throws {
        var frame = Pb(); frame.raw(UInt64(body.count))
        let data = frame.out + body
        try await withCheckedThrowingContinuation { (c: CheckedContinuation<Void, Error>) in
            conn.send(content: data, completion: .contentProcessed { e in if let e { c.resume(throwing: e) } else { c.resume() } })
        }
    }

    private func receive(_ n: Int) async throws -> Data {
        try await withCheckedThrowingContinuation { (c: CheckedContinuation<Data, Error>) in
            conn.receive(minimumIncompleteLength: n, maximumLength: n) { data, _, _, err in
                if let err { c.resume(throwing: err) }
                else if let data, data.count == n { c.resume(returning: data) }
                else { c.resume(throwing: UserFacing("The TV closed the connection")) }
            }
        }
    }

    func read() async throws -> Data {
        var n = 0, s = 0
        while true {
            let x = Int(try await receive(1)[0])
            n |= (x & 0x7F) << s
            if x & 0x80 == 0 { break }
            s += 7
            if s >= 28 { throw UserFacing("Bad message from the TV") }
        }
        guard n <= 65_536 else { throw UserFacing("Bad message from the TV") }
        return n == 0 ? Data() : try await receive(n)
    }

    func close() { conn.cancel() }
}

/* -------------------------------------------------------------- adapter */

final class AndroidTvAdapter: RemoteAdapter {
    private let host: String
    private let pin: String?
    private let onPin: (String) -> Void
    private let askCode: (String?) async -> String?
    private var link: TlsLink?
    private var reader: Task<Void, Never>?
    var onVolume: ((TvVolume) -> Void)?
    var onDropped: (() -> Void)?
    private var up = false

    init(host: String, pin: String?, onPin: @escaping (String) -> Void, askCode: @escaping (String?) async -> String?) {
        self.host = host; self.pin = pin; self.onPin = onPin; self.askCode = askCode
    }

    private static let keys: [RemoteKey: Int] = {
        var m: [RemoteKey: Int] = [
            .up: 19, .down: 20, .left: 21, .right: 22, .ok: 23, .back: 4, .home: 3, .playPause: 85, .power: 26,
            .volUp: 24, .volDown: 25, .mute: 164, .voice: 84, .rewind: 89, .fastForward: 90, .chUp: 166, .chDown: 167,
            .guide: 172, .chList: 172, .info: 165, .input: 178, .menu: 176, .audio: 222,
        ]
        for k in RemoteKey.allCases { if let d = k.digit, let v = d.wholeNumberValue { m[k] = 7 + v } }
        return m
    }()

    func connect() async throws {
        close()
        let id = try ClientIdentity.get()
        if pin == nil { try await pair(id) }
        try await openRemote(id)
    }

    /* ---- pairing (port 6467) */

    private func outer(_ build: (inout Pb) -> Void) -> Data { Pb.make { p in p.int(1, 2); p.int(2, 200); build(&p) } }

    private func expect(_ link: TlsLink, _ field: Int) async throws -> [Int: [Any]] {
        let m = try Pb.parse(try await link.read())
        switch long(m, 2) {
        case 200: break
        case 402: throw UserFacing("That code didn't match. Try connecting again.")
        default: throw UserFacing("The TV stopped pairing. Try again.")
        }
        if m[field] == nil { throw UserFacing("The TV answered unexpectedly. Try again.") }
        return m
    }

    private func pair(_ id: SecIdentity) async throws {
        // The code check below is what authenticates the TV during pairing.
        let l = TlsLink(host: host, port: 6467, identity: id, pin: nil)
        try await l.open()
        defer { l.close() }
        let hex: (inout Pb) -> Void = { p in p.int(1, 3); p.int(2, 6) } // HEXADECIMAL, 6 symbols
        try await l.send(outer { p in p.msg(10) { r in r.str(1, "com.krishna.miniremote"); r.str(2, "Mini Remote") } }); _ = try await expect(l, 11)
        try await l.send(outer { p in p.msg(20) { o in o.msg(1, hex); o.int(3, 1) } }); _ = try await expect(l, 20)
        try await l.send(outer { p in p.msg(30) { c in c.msg(1, hex); c.int(2, 1) } }); _ = try await expect(l, 31)
        guard let leaf = l.peer.leaf, let sk = SecCertificateCopyKey(leaf), let sder = SecKeyCopyExternalRepresentation(sk, nil) as Data?,
              let server = DER.rsaParts(sder), let client = ClientIdentity.publicParts(id) else {
            throw UserFacing("Couldn't read the TV's key")
        }
        var err: String? = nil
        for _ in 0..<3 {
            guard let raw = await askCode(err) else { throw UserFacing("Pairing cancelled") }
            let code = raw.trimmingCharacters(in: .whitespaces).uppercased()
            guard code.count == 6, code.allSatisfy({ $0.isHexDigit }) else { err = "Enter the 6 characters shown on the TV"; continue }
            let chars = Array(code)
            var nonce = Data()
            for i in stride(from: 2, to: 6, by: 2) { nonce.append(UInt8(String(chars[i...i + 1]), radix: 16)!) }
            var h = SHA256()
            h.update(data: client.0); h.update(data: client.1); h.update(data: server.0); h.update(data: server.1); h.update(data: nonce)
            let secret = Data(h.finalize())
            // The first two characters are a checksum of the rest, so typos are caught here.
            if secret[0] != UInt8(String(chars[0...1]), radix: 16)! { err = "That code doesn't match. Check it and try again."; continue }
            try await l.send(outer { p in p.msg(40) { s in s.bytes(1, secret) } }); _ = try await expect(l, 41)
            return
        }
        throw UserFacing("That code didn't match. Try connecting again.")
    }

    /* ---- control (port 6466) */

    private func openRemote(_ id: SecIdentity) async throws {
        let l = TlsLink(host: host, port: 6466, identity: id, pin: pin)
        do { try await l.open() } catch {
            if error is CertChanged || pin == nil { throw error }
            throw UserFacing("The TV no longer recognises this phone. Remove it and add it again to re-pair.")
        }
        link = l
        let ready = Once<Void>()
        reader = Task { [weak self] in
            do {
                while !Task.isCancelled {
                    let m = try Pb.parse(try await l.read())
                    if m[1] != nil {
                        try await l.send(Pb.make { p in
                            p.msg(1) { c in
                                c.int(1, 622)
                                c.msg(2) { d in
                                    d.str(1, "iPhone"); d.str(2, "Apple"); d.int(3, 1); d.str(4, "1")
                                    d.str(5, "com.krishna.miniremote"); d.str(6, "0.2.0")
                                }
                            }
                        })
                    } else if m[2] != nil {
                        try await l.send(Pb.make { p in p.msg(2) { a in a.int(1, 622) } })
                        ready.succeed(())
                    } else if let ping = sub(m, 8) {
                        let v = long(ping, 1) ?? 0
                        try await l.send(Pb.make { p in p.msg(9) { r in r.int(1, v) } })
                    } else if let v = sub(m, 50) {
                        // RemoteSetVolumeLevel: max (6), level (7), muted (8), sent whenever it changes.
                        let mx = Int(long(v, 6) ?? 0)
                        if mx > 0 { self?.onVolume?(TvVolume(level: Int(long(v, 7) ?? 0), max: mx, muted: (long(v, 8) ?? 0) != 0)) }
                    }
                    // RemoteError (3) is about one message (e.g. a key the TV lacks): not fatal.
                }
            } catch {
                ready.fail(error)
                if let self, self.link === l {
                    self.link = nil
                    if self.up { self.up = false; self.onDropped?() }
                }
            }
        }
        do { try await ready.wait(10) } catch { close(); throw error }
        up = true
        if pin == nil, let leaf = l.peer.leaf { onPin(fingerprint(leaf)) }
    }

    private func key(_ code: Int) async throws {
        guard let l = link else { throw UserFacing("Not connected") }
        try await l.send(Pb.make { p in p.msg(10) { k in k.int(1, UInt64(code)); k.int(2, 3) } }) // 3 = short press
    }

    func send(_ k: RemoteKey) async throws {
        guard let c = Self.keys[k] else { throw missing(k, "this TV") }
        try await key(c)
    }

    /** Opens an app: a full app link, or a package name (via its Play Store launch link). */
    func launchApp(_ id: String) async throws {
        guard let l = link else { throw UserFacing("Not connected") }
        let appLink = id.contains("://") ? id : "market://launch?id=\(id)"
        try await l.send(Pb.make { p in p.msg(90) { a in a.str(1, appLink) } })
    }

    func openStore(_ id: String) async throws {
        let pkg = id.components(separatedBy: "id=").last ?? id
        try await launchApp("market://details?id=\(pkg)")
    }

    /** Letters, digits and a few symbols as key presses. */
    func typeText(_ text: String) async throws {
        var codes: [Int] = []
        for c in text.lowercased() {
            if let a = c.asciiValue, c >= "a" && c <= "z" { codes.append(29 + Int(a - 97)) }
            else if let v = c.wholeNumberValue, c.isASCII { codes.append(7 + v) }
            else if c == " " { codes.append(62) } else if c == "." { codes.append(56) }
            else if c == "@" { codes.append(77) } else if c == "-" { codes.append(69) }
            else { throw UserFacing("Only letters, numbers and spaces can be typed on this TV", unsupported: true) }
        }
        for c in codes { try await key(c) }
    }

    func backspace() async throws { try await key(67) }

    func close() {
        up = false
        reader?.cancel(); reader = nil
        link?.close(); link = nil
    }
}
