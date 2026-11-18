import Foundation
import Network

/** Finds TVs by probing every address on the phone's /24 Wi-Fi subnet (no Bonjour needed). */
enum Discovery {
    static func localPrefix() -> String? {
        var addrs: UnsafeMutablePointer<ifaddrs>?
        guard getifaddrs(&addrs) == 0, let first = addrs else { return nil }
        defer { freeifaddrs(addrs) }
        var found: String?
        var p: UnsafeMutablePointer<ifaddrs>? = first
        while let cur = p {
            let ifa = cur.pointee
            if let sa = ifa.ifa_addr, sa.pointee.sa_family == UInt8(AF_INET) {
                var host = [CChar](repeating: 0, count: Int(NI_MAXHOST))
                getnameinfo(sa, socklen_t(sa.pointee.sa_len), &host, socklen_t(host.count), nil, 0, NI_NUMERICHOST)
                let ip = String(cString: host)
                let name = String(cString: ifa.ifa_name)
                if HostValidator.isPrivateIPv4(ip) && (name == "en0" || found == nil) {
                    found = ip
                    if name == "en0" { break }
                }
            }
            p = ifa.ifa_next
        }
        guard let ip = found, let dot = ip.lastIndex(of: ".") else { return nil }
        return String(ip[..<dot])
    }

    /** The ports that give a TV away: Roku, Samsung, LG, Android / Google TV. */
    private static let tvPorts: [UInt16] = [8060, 8001, 3000, 6466]

    /**
     * Two passes, so empty addresses cost almost nothing: a quick knock on the TV ports of every
     * address (many at once, 300 ms each), then a proper look only at those that answered.
     * Each TV is reported through [onFound] as soon as it's identified.
     */
    static func scan(onFound: @escaping @Sendable (Device) -> Void = { _ in }) async -> [Device] {
        guard let prefix = localPrefix() else { return [] }
        return await withTaskGroup(of: Device?.self) { g in
            var out: [Device] = []
            var next = 1
            func add() {
                guard next <= 254 else { return }
                let h = "\(prefix).\(next)"; next += 1
                g.addTask {
                    var open = Set<UInt16>()
                    await withTaskGroup(of: UInt16?.self) { k in
                        for p in tvPorts { k.addTask { await portOpen(h, p, wait: 0.3) ? p : nil } }
                        for await r in k { if let r { open.insert(r) } }
                    }
                    guard !open.isEmpty, let d = await probe(h, open) else { return nil }
                    onFound(d)
                    return d
                }
            }
            for _ in 0..<64 { add() }
            while let r = await g.next() {
                if let d = r { out.append(d) }
                add()
            }
            return out
        }
    }

    /** Names a TV gets when we can't read its own. */
    static func generic(_ name: String) -> Bool { ["LG TV", "Google TV", "Roku", "Samsung TV"].contains(name) || Brand.allCases.contains { $0.label == name } }

    /**
     * The name set on the TV itself, read without pairing: Roku and Samsung from their info pages,
     * Google TV from its Cast description (port 8008), LG (and Google TV as a fallback) from its
     * UPnP description found by asking the TV directly over SSDP.
     */
    static func nameOf(_ host: String, _ brand: Brand) async -> String? {
        guard HostValidator.isPrivateIPv4(host) else { return nil }
        var n: String?
        switch brand {
        case .ROKU:
            if let x = try? await Http.get("http://\(host):8060/query/device-info", session: Http.probe) {
                n = firstMatch("<user-device-name>(.*?)</user-device-name>", in: x)?[1] ?? firstMatch("<friendly-device-name>(.*?)</friendly-device-name>", in: x)?[1]
            }
        case .SAMSUNG:
            if let j = try? await Http.get("http://\(host):8001/api/v2/", session: Http.probe) { n = (json(j)?["device"] as? [String: Any])?["name"] as? String }
        case .ANDROID_TV:
            if let x = try? await Http.get("http://\(host):8008/ssdp/device-desc.xml", session: Http.probe) { n = firstMatch("<friendlyName>(.*?)</friendlyName>", in: x)?[1] }
            if n == nil, let j = try? await Http.get("http://\(host):8008/setup/eureka_info?params=name", session: Http.probe) { n = json(j)?["name"] as? String }
            if n == nil { n = await ssdpName(host) }
        case .LG:
            n = await ssdpName(host)
        }
        guard let v = n.map(xmlText)?.trimmingCharacters(in: .whitespaces), !v.isEmpty else { return nil }
        return String(v.prefix(30))
    }

    /** Asks [host] where its UPnP descriptions are (unicast SSDP) and reads a friendlyName. */
    private static func ssdpName(_ host: String) async -> String? {
        let c = NWConnection(host: NWEndpoint.Host(host), port: 1900, using: .udp)
        let found = Once<[String]>()
        var locs: [String] = []
        let ask = "M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 1\r\nST: ssdp:all\r\n\r\n".data(using: .utf8)!
        func receive() {
            c.receiveMessage { data, _, _, err in
                if let data, let t = String(data: data, encoding: .utf8),
                   let l = firstMatch("(?im)^location:\\s*(\\S+)", in: t)?[1], URL(string: l)?.host == host, !locs.contains(l) { locs.append(l) }
                if err == nil && locs.count < 6 { receive() }
            }
        }
        c.stateUpdateHandler = { st in
            if case .ready = st { c.send(content: ask, completion: .idempotent); c.send(content: ask, completion: .idempotent); receive() }
        }
        c.start(queue: .global())
        try? await Task.sleep(nanoseconds: 1_500_000_000)
        c.cancel()
        _ = found
        for l in locs {
            if let x = try? await Http.get(l, session: Http.probe), let f = firstMatch("<friendlyName>(.*?)</friendlyName>", in: x)?[1], !f.isEmpty { return f }
        }
        return nil
    }

    private static func portOpen(_ host: String, _ port: UInt16, wait: Double = 0.6) async -> Bool {
        let c = NWConnection(host: NWEndpoint.Host(host), port: NWEndpoint.Port(rawValue: port)!, using: .tcp)
        let r = Once<Bool>()
        c.stateUpdateHandler = { st in
            switch st {
            case .ready: r.succeed(true)
            case .failed, .waiting, .cancelled: r.succeed(false)
            default: break
            }
        }
        c.start(queue: .global())
        let ok = (try? await r.wait(wait)) ?? false
        c.cancel()
        return ok
    }

    private static func probe(_ host: String, _ open: Set<UInt16>) async -> Device? {
        if open.contains(8060), let xml = try? await Http.get("http://\(host):8060/query/device-info", session: Http.probe) {
            let name = firstMatch("<user-device-name>(.*?)</user-device-name>", in: xml)?[1]
                ?? firstMatch("<friendly-device-name>(.*?)</friendly-device-name>", in: xml)?[1] ?? "Roku"
            return Device(id: "roku-\(host)", name: name.isEmpty ? "Roku" : xmlText(name), host: host, brand: .ROKU)
        }
        if open.contains(8001), let j = try? await Http.get("http://\(host):8001/api/v2/", session: Http.probe) {
            let n = ((json(j)?["device"] as? [String: Any])?["name"] as? String) ?? ""
            return Device(id: "samsung-\(host)", name: n.isEmpty ? "Samsung TV" : n, host: host, brand: .SAMSUNG)
        }
        if open.contains(3000), await portOpen(host, 3001) { return Device(id: "lg-\(host)", name: await nameOf(host, .LG) ?? "LG TV", host: host, brand: .LG) }
        if open.contains(6466), await portOpen(host, 6467) { return Device(id: "android_tv-\(host)", name: await nameOf(host, .ANDROID_TV) ?? "Google TV", host: host, brand: .ANDROID_TV) }
        return nil
    }
}
