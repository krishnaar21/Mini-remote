import Foundation

enum Brand: String, Codable, CaseIterable, Identifiable {
    case ROKU, SAMSUNG, LG, ANDROID_TV
    var id: String { rawValue }
    var label: String {
        switch self {
        case .ROKU: return "Roku"
        case .SAMSUNG: return "Samsung"
        case .LG: return "LG webOS"
        case .ANDROID_TV: return "Android / Google TV"
        }
    }
}

enum RemoteKey: String, CaseIterable {
    case up, down, left, right, ok, back, home, playPause, power, volUp, volDown, mute, voice
    case rewind, fastForward, chUp, chDown, guide, chList, info, input, menu, audio
    case n0, n1, n2, n3, n4, n5, n6, n7, n8, n9

    var word: String {
        switch self {
        case .up: return "Up"; case .down: return "Down"; case .left: return "Left"; case .right: return "Right"
        case .ok: return "OK"; case .back: return "Back"; case .home: return "Home"; case .playPause: return "Play / Pause"
        case .power: return "Power"; case .volUp: return "Volume +"; case .volDown: return "Volume -"; case .mute: return "Mute"
        case .voice: return "Voice"; case .rewind: return "Rewind"; case .fastForward: return "Fast forward"
        case .chUp: return "Channel +"; case .chDown: return "Channel -"; case .guide: return "TV Guide"
        case .chList: return "Channel list"; case .info: return "Info"; case .input: return "Input"
        case .menu: return "Display settings"; case .audio: return "Audio"
        default: return digit.map(String.init) ?? rawValue
        }
    }

    var digit: Character? {
        switch self {
        case .n0: return "0"; case .n1: return "1"; case .n2: return "2"; case .n3: return "3"; case .n4: return "4"
        case .n5: return "5"; case .n6: return "6"; case .n7: return "7"; case .n8: return "8"; case .n9: return "9"
        default: return nil
        }
    }

    static func forDigit(_ c: Character) -> RemoteKey? { allCases.first { $0.digit == c } }
}

/** A saved TV. [token] and [pin] are secrets: the whole list lives in the Keychain. */
struct Device: Codable, Identifiable, Equatable {
    var id: String
    var name: String
    var host: String
    var brand: Brand
    var token: String? = nil
    /** SHA-256 (base64) of the TV's TLS certificate, recorded on first pairing. */
    var pin: String? = nil
}

struct Settings: Codable, Equatable {
    var haptics = true
    var hapticStrength: Double = 1
    var sound = true
    var soundLevel: Double = 1
    var accentHex = "FF453A"
    var scrubStep: Double = 36
    var keepAwake = true
    /** "dial" or "trackpad". */
    var controlStyle = "dial"
    /** Home layout: "strip" (Black) or "cards". */
    var layout = "strip"
}

extension Settings {
    /** Reads older saved settings too: any missing field keeps its default. */
    init(from decoder: Decoder) throws {
        self.init()
        let c = try decoder.container(keyedBy: CodingKeys.self)
        haptics = try c.decodeIfPresent(Bool.self, forKey: .haptics) ?? haptics
        hapticStrength = try c.decodeIfPresent(Double.self, forKey: .hapticStrength) ?? hapticStrength
        sound = try c.decodeIfPresent(Bool.self, forKey: .sound) ?? sound
        soundLevel = try c.decodeIfPresent(Double.self, forKey: .soundLevel) ?? soundLevel
        accentHex = try c.decodeIfPresent(String.self, forKey: .accentHex) ?? accentHex
        scrubStep = try c.decodeIfPresent(Double.self, forKey: .scrubStep) ?? scrubStep
        keepAwake = try c.decodeIfPresent(Bool.self, forKey: .keepAwake) ?? keepAwake
        controlStyle = try c.decodeIfPresent(String.self, forKey: .controlStyle) ?? controlStyle
        let l = try c.decodeIfPresent(String.self, forKey: .layout) ?? layout
        layout = ["strip", "cards"].contains(l) ? l : "strip"
    }
}

let HAPTIC_STRENGTHS: [(Double, String)] = [(0.6, "Light"), (1, "Medium"), (1.5, "Strong")]
let SOUND_LEVELS: [(Double, String)] = [(0.5, "Soft"), (1, "Medium"), (1.7, "Loud")]
let ACCENTS: [(String, String)] = [("Red", "FF453A"), ("Green", "30D158"), ("Blue", "0A84FF"), ("Purple", "BF5AF2"), ("Pink", "FF375F"), ("White", "F5F5F7")]

enum Link { case idle, connecting, ready, error }

enum ShortcutKind: String, Codable { case app = "APP", channel = "CHANNEL" }

/** Something saved on the Apps page: a channel (value = number) or an app (brand nil = catalog key). */
struct Shortcut: Codable, Identifiable, Equatable {
    var id: String
    var kind: ShortcutKind
    var name: String
    var value: String
    var brand: Brand? = nil
    var icon: String? = nil
    var pinned = false
    var added = false
}

struct PairPrompt: Equatable { var tvName: String; var error: String? }

struct TvApp: Equatable, Identifiable { var id: String; var name: String; var icon: String? = nil; var version: String? = nil }

struct NowOn: Equatable { var name: String; var live: Bool }

struct CatalogApp: Identifiable {
    var key: String; var name: String; var bg: UInt32; var ink: UInt32; var ids: [Brand: String]
    var vendor: String = ""; var link: String? = nil
    var id: String { key }
}

/** Popular apps and their ids per TV platform (Android TV: package names). */
enum Catalog {
    static let apps: [CatalogApp] = [
        CatalogApp(key: "netflix", name: "Netflix", bg: 0x0B0B0B, ink: 0xE50914, ids: [.ROKU: "12", .SAMSUNG: "3201907018807", .LG: "netflix", .ANDROID_TV: "com.netflix.ninja"], vendor: "Netflix, Inc.", link: "https://www.netflix.com/title"),
        CatalogApp(key: "youtube", name: "YouTube", bg: 0xFF0000, ink: 0xFFFFFF, ids: [.ROKU: "837", .SAMSUNG: "111299001912", .LG: "youtube.leanback.v4", .ANDROID_TV: "com.google.android.youtube.tv"], vendor: "Google LLC", link: "https://www.youtube.com"),
        CatalogApp(key: "prime", name: "Prime Video", bg: 0x0F79AF, ink: 0xFFFFFF, ids: [.ROKU: "13", .SAMSUNG: "3201910019365", .LG: "amazon", .ANDROID_TV: "com.amazon.amazonvideo.livingroom"], vendor: "Amazon", link: "https://app.primevideo.com"),
        CatalogApp(key: "hotstar", name: "JioHotstar", bg: 0x0C1B4D, ink: 0xFFFFFF, ids: [.ANDROID_TV: "in.startv.hotstar.dplus.tv"], vendor: "JioStar"),
        CatalogApp(key: "disney", name: "Disney+", bg: 0x0E2A5C, ink: 0xFFFFFF, ids: [.ROKU: "291097", .SAMSUNG: "3201901017640", .LG: "com.disney.disneyplus-prod", .ANDROID_TV: "com.disney.disneyplus"], vendor: "Disney", link: "https://www.disneyplus.com"),
        CatalogApp(key: "sonyliv", name: "SonyLIV", bg: 0x111111, ink: 0xF5F5F5, ids: [.ANDROID_TV: "com.sonyliv"], vendor: "Sony Pictures Networks India"),
        CatalogApp(key: "zee5", name: "ZEE5", bg: 0x6B21A8, ink: 0xFFFFFF, ids: [.ANDROID_TV: "com.graymatrix.did"], vendor: "Zee Entertainment"),
        CatalogApp(key: "spotify", name: "Spotify", bg: 0x1DB954, ink: 0x0B0B0B, ids: [.ROKU: "22297", .SAMSUNG: "3201606009684", .LG: "spotify-beehive", .ANDROID_TV: "com.spotify.tv.android"], vendor: "Spotify AB"),
        CatalogApp(key: "appletv", name: "Apple TV", bg: 0x3A3A3D, ink: 0xFFFFFF, ids: [.ROKU: "551012", .SAMSUNG: "3201807016597", .LG: "com.apple.appletv", .ANDROID_TV: "com.apple.atve.androidtv.appletv"], vendor: "Apple Inc."),
        CatalogApp(key: "ytmusic", name: "YouTube Music", bg: 0x3A3A3D, ink: 0xFF4E45, ids: [.ANDROID_TV: "com.google.android.youtube.tvmusic"], vendor: "Google LLC"),
        CatalogApp(key: "plex", name: "Plex", bg: 0x1F1F1F, ink: 0xE5A00D, ids: [.ROKU: "13535", .SAMSUNG: "3201512006963", .ANDROID_TV: "com.plexapp.android"], vendor: "Plex, Inc."),
        CatalogApp(key: "hulu", name: "Hulu", bg: 0x1CE783, ink: 0x0B0B0B, ids: [.ROKU: "2285", .ANDROID_TV: "com.hulu.livingroomplus"], vendor: "Hulu, LLC"),
        CatalogApp(key: "max", name: "Max", bg: 0x002BE7, ink: 0xFFFFFF, ids: [.ROKU: "61322", .ANDROID_TV: "com.wbd.stream"], vendor: "Warner Bros. Discovery"),
        CatalogApp(key: "peacock", name: "Peacock", bg: 0x0B0B0B, ink: 0xFFFFFF, ids: [.ROKU: "593099", .ANDROID_TV: "com.peacocktv.peacockandroid"], vendor: "NBCUniversal"),
        CatalogApp(key: "paramount", name: "Paramount+", bg: 0x0064FF, ink: 0xFFFFFF, ids: [.ROKU: "31440", .ANDROID_TV: "com.cbs.ott"], vendor: "Paramount"),
        CatalogApp(key: "tubi", name: "Tubi", bg: 0x7408FF, ink: 0xFFF200, ids: [.ROKU: "41468", .ANDROID_TV: "com.tubitv"], vendor: "Tubi, Inc."),
        CatalogApp(key: "pluto", name: "Pluto TV", bg: 0x2B2B2B, ink: 0xFFF200, ids: [.ROKU: "74519", .ANDROID_TV: "tv.pluto.android"], vendor: "Paramount"),
        CatalogApp(key: "rokuchannel", name: "The Roku Channel", bg: 0x6C3C97, ink: 0xFFFFFF, ids: [.ROKU: "151908"], vendor: "Roku, Inc."),
    ]
    static func byKey(_ k: String) -> CatalogApp? { apps.first { $0.key == k } }
}

/** A TV's name for the top bar: without its IP address, at most 18 characters. */
func shortName(_ name: String, _ fallback: String) -> String {
    var n = name.replacingOccurrences(of: #"\(?\b\d{1,3}(\.\d{1,3}){3}\b\)?"#, with: "", options: .regularExpression)
    n = n.replacingOccurrences(of: #"\s+"#, with: " ", options: .regularExpression).trimmingCharacters(in: .whitespaces)
    if n.isEmpty { n = fallback }
    return n.count > 18 ? String(n.prefix(17)).trimmingCharacters(in: .whitespaces) + "…" : n
}
