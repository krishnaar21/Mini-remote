import Foundation
import Darwin

/**
 * Wake-on-LAN: the "magic packet" (6 × 0xFF, then the MAC 16 times) broadcast on the home
 * network. Samsung, LG and Roku TVs wake with it when their network standby is on.
 * iOS lets an app broadcast only with Apple's multicast networking entitlement, which a signed
 * build needs to request; without it the send fails and the app says so.
 */
enum Wol {
    static func parse(_ mac: String) -> [UInt8]? {
        let hex = mac.filter(\.isHexDigit)
        guard hex.count == 12 else { return nil }
        var out: [UInt8] = []
        var i = hex.startIndex
        while i < hex.endIndex { let j = hex.index(i, offsetBy: 2); guard let b = UInt8(hex[i..<j], radix: 16) else { return nil }; out.append(b); i = j }
        return out
    }

    static func wake(mac: String, host: String) -> Bool {
        guard let m = parse(mac) else { return false }
        var packet = [UInt8](repeating: 0xFF, count: 6)
        for _ in 0..<16 { packet += m }
        let parts = host.split(separator: ".")
        let targets = (parts.count == 4 ? ["\(parts[0]).\(parts[1]).\(parts[2]).255"] : []) + ["255.255.255.255"]
        let fd = socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP)
        guard fd >= 0 else { return false }
        defer { close(fd) }
        var on: Int32 = 1
        setsockopt(fd, SOL_SOCKET, SO_BROADCAST, &on, socklen_t(MemoryLayout<Int32>.size))
        var sent = false
        for _ in 0..<3 {
            for t in targets {
                for port in [UInt16(9), UInt16(7)] {
                    var a = sockaddr_in()
                    a.sin_family = sa_family_t(AF_INET)
                    a.sin_port = port.bigEndian
                    inet_pton(AF_INET, t, &a.sin_addr)
                    let n = withUnsafePointer(to: &a) { p in
                        p.withMemoryRebound(to: sockaddr.self, capacity: 1) { sendto(fd, packet, packet.count, 0, $0, socklen_t(MemoryLayout<sockaddr_in>.size)) }
                    }
                    if n > 0 { sent = true }
                }
            }
            usleep(80_000)
        }
        return sent
    }
}
