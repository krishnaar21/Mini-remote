import SwiftUI

extension Color {
    init(hex: UInt32, alpha: Double = 1) {
        self.init(.sRGB, red: Double((hex >> 16) & 0xFF) / 255, green: Double((hex >> 8) & 0xFF) / 255, blue: Double(hex & 0xFF) / 255, opacity: alpha)
    }
    init(hexString: String) { self.init(hex: UInt32(hexString, radix: 16) ?? 0xFF453A) }
}

/** Dark, neutral palette (matches the Android app's Material colours). */
enum T {
    static let bg = Color(hex: 0x1C1C1E)
    static let sheet = Color(hex: 0x262629)
    static let container = Color(hex: 0x2E2E31)
    static let high = Color(hex: 0x3D3D41)
    static let highest = Color(hex: 0x4C4C51)
    static let pressed = Color(hex: 0x5A5A60)
    static let ink = Color(hex: 0xF7F7F8)
    static let dim = Color(hex: 0xBDC1C6)
    static let muted = Color(hex: 0xB4B4B9)
    static let line = Color(hex: 0x3A3A3E)
    static let ok = Color(hex: 0x6DD58C)
    static let warn = Color(hex: 0xFDD663)
    static let danger = Color(hex: 0xF2B8B5)
}

/** SF Symbol for the key that was pressed (shown in the dial's well). */
func keySymbol(_ k: RemoteKey, muted: Bool, playing: Bool) -> String? {
    switch k {
    case .up: return "chevron.up"; case .down: return "chevron.down"; case .left: return "chevron.left"; case .right: return "chevron.right"
    case .back: return "chevron.left"; case .home: return "tv"; case .playPause: return playing ? "play.fill" : "pause.fill"
    case .power: return "power"; case .volUp, .chUp: return "plus"; case .volDown, .chDown: return "minus"
    case .mute: return muted ? "speaker.slash.fill" : "speaker.wave.2.fill"; case .voice: return "mic.fill"
    default: return nil
    }
}

func linkWord(_ l: Link) -> String {
    switch l { case .ready: return "Connected"; case .connecting: return "Connecting…"; case .error: return "Can't reach"; case .idle: return "" }
}
