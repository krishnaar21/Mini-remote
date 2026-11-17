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

    static func scan() async -> [Device] {
        guard let prefix = localPrefix() else { return [] }
        return await withTaskGroup(of: Device?.self) { g in
            var out: [Device] = []
            var next = 1
            func add() { if next <= 254 { let h = "\(prefix).\(next)"; next += 1; g.addTask { await probe(h) } } }
            for _ in 0..<40 { add() }
            while let r = await g.next() {
                if let d = r { out.append(d) }
                add()
            }
            return out
        }
    }

    private static func portOpen(_ host: String, _ port: UInt16) async -> Bool {
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
        let ok = (try? await r.wait(0.6)) ?? false
        c.cancel()
        return ok
    }

    private static func probe(_ host: String) async -> Device? {
        if let xml = try? await Http.get("http://\(host):8060/query/device-info", session: Http.probe) {
            let name = firstMatch("<user-device-name>(.*?)</user-device-name>", in: xml)?[1]
                ?? firstMatch("<friendly-device-name>(.*?)</friendly-device-name>", in: xml)?[1] ?? "Roku"
            return Device(id: "roku-\(host)", name: name.isEmpty ? "Roku" : xmlText(name), host: host, brand: .ROKU)
        }
        if let j = try? await Http.get("http://\(host):8001/api/v2/", session: Http.probe) {
            let n = ((json(j)?["device"] as? [String: Any])?["name"] as? String) ?? ""
            return Device(id: "samsung-\(host)", name: n.isEmpty ? "Samsung TV" : n, host: host, brand: .SAMSUNG)
        }
        if await portOpen(host, 3000), await portOpen(host, 3001) { return Device(id: "lg-\(host)", name: "LG TV", host: host, brand: .LG) }
        if await portOpen(host, 6466), await portOpen(host, 6467) { return Device(id: "android_tv-\(host)", name: "Google TV", host: host, brand: .ANDROID_TV) }
        return nil
    }
}
