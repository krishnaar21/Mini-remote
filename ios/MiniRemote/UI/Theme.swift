import SwiftUI

extension Color {
    init(hex: UInt32, alpha: Double = 1) {
        self.init(.sRGB, red: Double((hex >> 16) & 0xFF) / 255, green: Double((hex >> 8) & 0xFF) / 255, blue: Double(hex & 0xFF) / 255, opacity: alpha)
    }
    init(hexString: String) { self.init(hex: UInt32(hexString, radix: 16) ?? 0xFF453A) }
}

/** One look for the whole app (Settings › Appearance › Theme). Every colour below reads from it. */
struct Palette: Equatable {
    let key: String, label: String, light: Bool
    let screen: UInt32, bg: UInt32, sheet: UInt32, container: UInt32, high: UInt32, highest: UInt32, pressed: UInt32
    let keyFill: UInt32, ink: UInt32, dim: UInt32, muted: UInt32, sub: UInt32, line: UInt32, dotOff: UInt32
}

enum Palettes {
    static let dark = Palette(key: "dark", label: "Dark", light: false, screen: 0x000000, bg: 0x1C1C1E, sheet: 0x262629, container: 0x2E2E31,
        high: 0x3D3D41, highest: 0x4C4C51, pressed: 0x5A5A60, keyFill: 0x1C1C1E, ink: 0xF7F7F8, dim: 0xBDC1C6, muted: 0xB4B4B9, sub: 0x8E8E93, line: 0x3A3A3E, dotOff: 0x7C7C82)
    static let light = Palette(key: "light", label: "Light", light: true, screen: 0xF2F2F7, bg: 0xFFFFFF, sheet: 0xF2F2F7, container: 0xFFFFFF,
        high: 0xE6E6EB, highest: 0xD5D5DB, pressed: 0xC7C7CD, keyFill: 0xFFFFFF, ink: 0x1C1C1E, dim: 0x48484D, muted: 0x5E5E63, sub: 0x8A8A8F, line: 0xD9D9DE, dotOff: 0xB8B8BE)
    static let navy = Palette(key: "navy", label: "Navy", light: false, screen: 0x070D1E, bg: 0x111A33, sheet: 0x121C37, container: 0x16213F,
        high: 0x203058, highest: 0x2B3D6B, pressed: 0x364A7D, keyFill: 0x131D39, ink: 0xEEF2FF, dim: 0xBAC4E2, muted: 0xA6B1D3, sub: 0x7F8BB2, line: 0x26355F, dotOff: 0x55618A)
    /** Follows the iPhone: Light or Dark as the system is (see resolve). */
    static let auto = Palette(key: "auto", label: "Auto", light: false, screen: dark.screen, bg: dark.bg, sheet: dark.sheet, container: dark.container,
        high: dark.high, highest: dark.highest, pressed: dark.pressed, keyFill: dark.keyFill, ink: dark.ink, dim: dark.dim, muted: dark.muted, sub: dark.sub, line: dark.line, dotOff: dark.dotOff)
    static let all = [auto, dark, light, navy]
    static func of(_ k: String) -> Palette { all.first { $0.key == k } ?? dark }
    /** The palette to draw with: Auto becomes Light or Dark as the iPhone is. */
    static func resolve(_ k: String, dark isDark: Bool) -> Palette { k == "auto" || k == "retro" ? (isDark ? dark : light) : of(k) }
}

/** The palette in use. The app's root view is rebuilt when it changes (see ContentView). */
enum Look { static var p = Palettes.dark }

enum T {
    static var screen: Color { Color(hex: Look.p.screen) }
    static var bg: Color { Color(hex: Look.p.bg) }
    static var sheet: Color { Color(hex: Look.p.sheet) }
    static var container: Color { Color(hex: Look.p.container) }
    static var high: Color { Color(hex: Look.p.high) }
    static var highest: Color { Color(hex: Look.p.highest) }
    static var pressed: Color { Color(hex: Look.p.pressed) }
    static var key: Color { Color(hex: Look.p.keyFill) }
    static var ink: Color { Color(hex: Look.p.ink) }
    static var dim: Color { Color(hex: Look.p.dim) }
    static var muted: Color { Color(hex: Look.p.muted) }
    static var sub: Color { Color(hex: Look.p.sub) }
    static var line: Color { Color(hex: Look.p.line) }
    static var dotOff: Color { Color(hex: Look.p.dotOff) }
    static let ok = Color(hex: 0x6DD58C)
    static let warn = Color(hex: 0xFDD663)
    static let danger = Color(hex: 0xF2B8B5)
    static let bt = Color(hex: 0x64D2FF)
}

/** SF Symbol for the key that was pressed (shown in the dial's well). */
func keySymbol(_ k: RemoteKey, muted: Bool, playing: Bool) -> String? {
    switch k {
    case .up: return "chevron.up"; case .down: return "chevron.down"; case .left: return "chevron.left"; case .right: return "chevron.right"
    case .back: return "chevron.left"; case .home: return "house"; case .playPause: return playing ? "play.fill" : "pause.fill"
    case .power: return "power"; case .volUp, .chUp: return "plus"; case .volDown, .chDown: return "minus"
    case .mute: return muted ? "speaker.slash.fill" : "speaker.wave.2.fill"; case .voice: return "mic.fill"
    default: return nil
    }
}

func linkWord(_ l: Link) -> String {
    switch l { case .ready: return "Connected"; case .connecting: return "Connecting…"; case .error: return "Can't reach"; case .idle: return "" }
}
