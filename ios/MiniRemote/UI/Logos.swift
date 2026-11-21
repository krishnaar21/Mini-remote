import SwiftUI
import UIKit
import CoreImage

/**
 * App logos for Quick access and the app tiles: the app's own website icon (apple-touch-icon,
 * 180px, HTTPS), then Google's favicon service. Only the website is contacted. Cached on disk.
 */
enum Logos {
    /** Official website of each catalog app. */
    static let sites: [String: String] = [
        "netflix": "netflix.com", "youtube": "youtube.com", "prime": "primevideo.com", "hotstar": "jiohotstar.com",
        "disney": "disneyplus.com", "sonyliv": "sonyliv.com", "zee5": "zee5.com", "spotify": "spotify.com",
        "appletv": "tv.apple.com", "ytmusic": "music.youtube.com", "plex": "plex.tv", "hulu": "hulu.com",
        "max": "max.com", "peacock": "peacocktv.com", "paramount": "paramountplus.com", "tubi": "tubitv.com",
        "pluto": "pluto.tv", "rokuchannel": "therokuchannel.roku.com",
    ]

    private static let mem = NSCache<NSString, UIImage>()
    private static var dir: URL {
        let d = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0].appendingPathComponent("logos")
        try? FileManager.default.createDirectory(at: d, withIntermediateDirectories: true)
        return d
    }

    static func icon(site: String) async -> UIImage? {
        guard !site.isEmpty, site.allSatisfy({ $0.isLetter || $0.isNumber || $0 == "." || $0 == "-" }) else { return nil }
        if let m = mem.object(forKey: site as NSString) { return m }
        let file = dir.appendingPathComponent(site + ".png")
        if let d = try? Data(contentsOf: file), let img = UIImage(data: d) { mem.setObject(img, forKey: site as NSString); return img }
        let www = site.filter { $0 == "." }.count == 1 ? "www." + site : site
        for url in ["https://\(www)/apple-touch-icon.png", "https://\(site)/apple-touch-icon.png",
                    "https://www.google.com/s2/favicons?domain=\(site)&sz=256"] {
            guard let u = URL(string: url) else { continue }
            var r = URLRequest(url: u); r.timeoutInterval = 8
            guard let (data, resp) = try? await URLSession.shared.data(for: r), (resp as? HTTPURLResponse)?.statusCode == 200,
                  resp.url?.scheme == "https", data.count < 400_000, let img = UIImage(data: data), img.size.width * img.scale >= 96 else { continue }
            try? data.write(to: file)
            mem.setObject(img, forKey: site as NSString)
            return img
        }
        return nil
    }
}

/** A logo if there is one, else the fallback. */
struct SiteLogo<Fallback: View>: View {
    let site: String?
    @ViewBuilder let fallback: () -> Fallback
    @State private var image: UIImage?
    var body: some View {
        Group {
            if let image { Image(uiImage: image).resizable().scaledToFit().clipShape(RoundedRectangle(cornerRadius: 14)) } else { fallback() }
        }
        .task(id: site) { if let site { image = await Logos.icon(site: site) } }
    }
}

/* ------------------------------------------------ App Store icons */

extension Logos {
    /** Apple App Store ids for catalog apps, with a word the listing's name must contain (so a wrong id can't show another app). */
    static let store: [String: (Int, String)] = [
        "netflix": (363590051, "netflix"), "youtube": (544007664, "youtube"), "prime": (545519333, "prime"),
        "hotstar": (934459219, "hotstar"), "disney": (1446075923, "disney"), "sonyliv": (587794258, "sony"),
        "zee5": (743691886, "zee"), "spotify": (324684580, "spotify"), "appletv": (1174078549, "tv"),
        "ytmusic": (1017492454, "music"), "plex": (383457673, "plex"), "hulu": (376510438, "hulu"),
        "max": (971265422, "max"), "peacock": (1508186374, "peacock"), "paramount": (530168168, "paramount"),
        "tubi": (886445756, "tubi"), "pluto": (751712884, "pluto"),
    ]

    /** The app's official icon from its App Store listing (iTunes lookup), cached on disk. */
    static func storeIcon(_ key: String) async -> UIImage? {
        guard let (id, word) = store[key] else { return nil }
        let k = "ios-\(id)" as NSString
        if let m = mem.object(forKey: k) { return m }
        let file = dir.appendingPathComponent("ios-\(id).png")
        if let d = try? Data(contentsOf: file), let img = UIImage(data: d) { mem.setObject(img, forKey: k); return img }
        for country in ["in", "us"] {
            guard let u = URL(string: "https://itunes.apple.com/lookup?id=\(id)&country=\(country)"),
                  let (data, _) = try? await URLSession.shared.data(from: u),
                  let o = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                  let item = (o["results"] as? [[String: Any]])?.first,
                  (item["trackName"] as? String)?.lowercased().contains(word) == true,
                  let art = item["artworkUrl512"] as? String, art.hasPrefix("https://"), art.contains("mzstatic.com") else { continue }
            let small = (art as NSString).deletingLastPathComponent + "/256x256bb.png"
            for url in [small, art] {
                guard let iu = URL(string: url), let (img, resp) = try? await URLSession.shared.data(from: iu),
                      (resp as? HTTPURLResponse)?.statusCode == 200, img.count < 600_000, let ui = UIImage(data: img) else { continue }
                try? img.write(to: file)
                mem.setObject(ui, forKey: k)
                return ui
            }
        }
        return nil
    }

    /** The best icon for a catalog app: App Store first, then its website. */
    static func best(_ key: String) async -> UIImage? {
        if let s = await storeIcon(key) { return s }
        if let site = sites[key] { return await icon(site: site) }
        return nil
    }

    /** How an icon sits on a tile: its edges stretched out (plain edges), or as a smaller plate (busy or see-through edges). */
    enum Fit { case extend(UIImage), plate(Color?) }

    private static let ctx = CIContext()

    /**
     * Prepares an icon for a [side]-point tile: the icon at three quarters of the tile, with its
     * own edge pixels stretched to the tile's edge (Core Image's clamp), so its background
     * carries on. Icons with see-through corners or artwork reaching the edge are left as plates.
     */
    static func fit(_ img: UIImage, side: CGFloat, scale: CGFloat) -> Fit {
        guard var cg = img.cgImage, var stats = edgeStats(cg) else { return .plate(nil) }
        var trimmed = false
        // App Store artwork often comes already rounded (see-through corners, solid sides). Trim
        // to just inside the rounding so the icon's own background can carry on to the circle's
        // edge, as the Android tiles do, instead of a square sitting on a plate.
        if !stats.opaque && stats.sides {
            let inset = CGFloat(min(cg.width, cg.height)) * 0.1
            if let inner = cg.cropping(to: CGRect(x: inset, y: inset, width: CGFloat(cg.width) - 2 * inset, height: CGFloat(cg.height) - 2 * inset)),
               let s2 = edgeStats(inner), s2.opaque { cg = inner; stats = s2; trimmed = true }
        }
        // See-through corners: the icon on the tile's colour. Artwork to the edge: a smaller plate on its edge colour.
        guard stats.opaque else { return .plate(nil) }
        guard !stats.busy else { return .plate(stats.edge) }
        let inset = CGFloat(min(cg.width, cg.height)) * 0.035
        let src = CIImage(cgImage: cg).cropped(to: CGRect(x: inset, y: inset, width: CGFloat(cg.width) - 2 * inset, height: CGFloat(cg.height) - 2 * inset))
        let px = side * scale
        // A trimmed icon has lost its outer margin: draw it a little smaller so the mark keeps its size.
        let k = px * (trimmed ? 0.62 : 0.74) / src.extent.width
        let moved = src.transformed(by: CGAffineTransform(translationX: -src.extent.minX, y: -src.extent.minY))
            .clampedToExtent()
            .transformed(by: CGAffineTransform(scaleX: k, y: k))
            .transformed(by: CGAffineTransform(translationX: (px - src.extent.width * k) / 2, y: (px - src.extent.height * k) / 2))
            .cropped(to: CGRect(x: 0, y: 0, width: px, height: px))
        guard let out = ctx.createCGImage(moved, from: CGRect(x: 0, y: 0, width: px, height: px)) else { return .plate(nil) }
        return .extend(UIImage(cgImage: out, scale: scale, orientation: .up))
    }

    /** Corners opaque? Border one plain colour (low spread of brightness)? */
    private static func edgeStats(_ cg: CGImage) -> (opaque: Bool, busy: Bool, edge: Color, sides: Bool)? {
        let w = 64, h = 64
        var px = [UInt8](repeating: 0, count: w * h * 4)
        // Draw while the buffer's pointer is valid (an &px argument only lives for the call).
        let drawn: Bool = px.withUnsafeMutableBytes { buf in
            guard let c = CGContext(data: buf.baseAddress, width: w, height: h, bitsPerComponent: 8, bytesPerRow: w * 4,
                                    space: CGColorSpaceCreateDeviceRGB(), bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue) else { return false }
            c.draw(cg, in: CGRect(x: 0, y: 0, width: w, height: h))
            return true
        }
        guard drawn else { return nil }
        func at(_ x: Int, _ y: Int) -> (Double, Double) {
            let i = (y * w + x) * 4
            let l = (0.299 * Double(px[i]) + 0.587 * Double(px[i + 1]) + 0.114 * Double(px[i + 2])) / 255
            return (l, Double(px[i + 3]) / 255)
        }
        func rgb(_ x: Int, _ y: Int) -> (Double, Double, Double) { let i = (y * w + x) * 4; return (Double(px[i]) / 255, Double(px[i + 1]) / 255, Double(px[i + 2]) / 255) }
        let i = 3
        let corners = [at(i, i), at(w - 1 - i, i), at(i, h - 1 - i), at(w - 1 - i, h - 1 - i)]
        let opaque = corners.allSatisfy { $0.1 > 0.8 }
        var lum: [Double] = []
        for t in 0...15 {
            let x = i + (w - 2 * i - 1) * t / 15, y = i + (h - 2 * i - 1) * t / 15
            lum += [at(x, i).0, at(x, h - 1 - i).0, at(i, y).0, at(w - 1 - i, y).0]
        }
        let m = lum.reduce(0, +) / Double(lum.count)
        let sd = (lum.map { ($0 - m) * ($0 - m) }.reduce(0, +) / Double(lum.count)).squareRoot()
        let cs = [rgb(i, i), rgb(w - 1 - i, i), rgb(i, h - 1 - i), rgb(w - 1 - i, h - 1 - i)]
        var edge = Color(red: cs.map(\.0).reduce(0, +) / 4, green: cs.map(\.1).reduce(0, +) / 4, blue: cs.map(\.2).reduce(0, +) / 4)
        if !Look.p.light && cs.allSatisfy({ 0.299 * $0.0 + 0.587 * $0.1 + 0.114 * $0.2 < 0.08 }) { edge = T.high }
        // The middle of each side: solid even when the corners are rounded off.
        let sides = [at(w / 2, i), at(w / 2, h - 1 - i), at(i, h / 2), at(w - 1 - i, h / 2)].allSatisfy { $0.1 > 0.8 }
        return (opaque, sd > 0.16, edge, sides)
    }
}

/**
 * A quick access app as a tile (circle on Home, rounded square in More): its official icon,
 * edges extended to fill the tile; or the icon as a plate on the app's colour; or initials.
 */
struct AppTile: View {
    let s: Shortcut
    let size: CGFloat
    var square = false
    @Environment(\.displayScale) private var scale
    @State private var fit: Logos.Fit?
    @State private var image: UIImage?

    var body: some View {
        let cat = s.kind == .app && s.brand == nil ? Catalog.byKey(s.value) : (s.kind == .app ? Catalog.apps.first { $0.name.caseInsensitiveCompare(s.name) == .orderedSame } : nil)
        let base = s.kind == .channel ? Color(hex: 0x263042) : (cat.map { Color(hex: $0.bg) } ?? Color(hex: 0x3A3A3F))
        // Near-black tiles would vanish on a dark remote: lift them to a dark grey.
        let bg = !Look.p.light && (cat?.bg ?? 0x3A3A3F) < 0x181818 ? T.high : base
        let shape = square ? AnyShape(RoundedRectangle(cornerRadius: 18, style: .continuous)) : AnyShape(Circle())
        ZStack {
            shape.fill(bg)
            switch fit {
            case .extend(let img): Image(uiImage: img).resizable().scaledToFill()
            case .plate(let edge):
                ZStack {
                    if let edge { edge }
                    if let image { Image(uiImage: image).resizable().scaledToFit().frame(width: size * (edge == nil ? 0.74 : 0.64), height: size * (edge == nil ? 0.74 : 0.64))
                        .clipShape(RoundedRectangle(cornerRadius: size * 0.16, style: .continuous)) }
                }
            case nil:
                Text(s.kind == .channel ? s.value : String(s.name.prefix(2)).uppercased())
                    .font(.system(size: size * 0.26, weight: .bold, design: s.kind == .channel ? .monospaced : .default))
                    .foregroundStyle(cat.map { Color(hex: $0.ink) } ?? .white)
            }
        }
        .frame(width: square ? nil : size, height: size)
        .frame(maxWidth: square ? .infinity : nil)
        .clipShape(shape)
        .contentShape(shape)
        .task(id: s.id) {
            guard let key = cat?.key, let img = await Logos.best(key) else { return }
            image = img
            fit = Logos.fit(img, side: size, scale: scale)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(s.name)
        .accessibilityAddTraits(.isButton)
    }
}
