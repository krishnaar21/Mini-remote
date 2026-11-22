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

    /** How an icon sits on a tile: drawn to fill it (its background carried to the edges), or as a plate on the tile's colour. */
    enum Fit { case extend(UIImage), plate(Color?) }

    private static let ci = CIContext()

    /**
     * Prepares an icon for a tile of [width] × [height] points (a circle, or a wider rounded
     * square), never stretching or cropping the logo itself:
     * - plain edges (Netflix, YouTube, Prime): the icon at about three quarters of the tile's
     *   short side, its own edge pixels carried out to the tile's edges;
     * - busy or very rounded artwork (SonyLIV, Disney+): the icon whole, on a soft blur of itself
     *   that fills the tile, its edge feathered into that blur so no border shows;
     * - a glyph on nothing (see-through): a plate on the tile's colour.
     */
    static func fit(_ img: UIImage, width: CGFloat, height: CGFloat, scale: CGFloat) -> Fit {
        guard var cg = img.cgImage, var stats = edgeStats(cg) else { return .plate(nil) }
        let orig = cg, origStats = stats
        var trimmed = false
        // App Store artwork often comes already rounded (see-through corners, solid sides): trim
        // to just inside the rounding so its own background can carry on.
        if !stats.opaque && stats.sides {
            let inset = CGFloat(min(cg.width, cg.height)) * 0.14
            if let inner = cg.cropping(to: CGRect(x: inset, y: inset, width: CGFloat(cg.width) - 2 * inset, height: CGFloat(cg.height) - 2 * inset)),
               let s2 = edgeStats(inner), s2.opaque { cg = inner; stats = s2; trimmed = true }
        }
        let pw = Int((width * scale).rounded()), ph = Int((height * scale).rounded())
        guard pw > 0, ph > 0 else { return .plate(nil) }
        // A black icon background would melt into the dark remote: lift it to the key grey.
        let lift = !Look.p.light && stats.lum < 0.14
        if !stats.opaque && !origStats.sides { return .plate(nil) }
        if !stats.opaque || stats.busy { return ambient(orig, pw: pw, ph: ph, scale: scale, lift: lift) }
        // Trim the soft, anti-aliased outer pixels.
        let t = Int((CGFloat(min(cg.width, cg.height)) * 0.06).rounded())
        guard let src = cg.cropping(to: CGRect(x: t, y: t, width: cg.width - 2 * t, height: cg.height - 2 * t)) else { return .plate(nil) }
        let sw = src.width, sh = src.height
        guard sw > 6, sh > 6, let c = canvas(pw, ph, lift: lift) else { return .plate(nil) }
        // A trimmed icon has lost its outer margin: draw it a little smaller so the mark keeps its size.
        let k = CGFloat(min(pw, ph)) * (trimmed ? 0.58 : 0.74) / CGFloat(max(sw, sh))
        // Whole pixels everywhere, so the carried-out edges meet the icon without a hairline seam.
        let iw = (CGFloat(sw) * k).rounded(), ih = (CGFloat(sh) * k).rounded()
        let W = CGFloat(pw), H = CGFloat(ph)
        let x0 = ((W - iw) / 2).rounded(), y0 = ((H - ih) / 2).rounded()
        let x1 = x0 + iw, y1 = y0 + ih
        func piece(_ x: Int, _ y: Int, _ w: Int, _ h: Int, into r: CGRect) {
            guard r.width > 0, r.height > 0, let part = src.cropping(to: CGRect(x: x, y: y, width: w, height: h)) else { return }
            c.draw(part, in: r)
        }
        // Edge colours come from a few pixels inside (the outermost can carry a tint), and each band
        // tucks 2 px under the icon, which is drawn last on top.
        let e = 3, o: CGFloat = 2
        c.interpolationQuality = .none
        // Image rows run top-down; Core Graphics' y runs bottom-up.
        piece(0, e, sw, 1, into: CGRect(x: x0, y: y1 - o, width: iw, height: H - y1 + o))
        piece(0, sh - 1 - e, sw, 1, into: CGRect(x: x0, y: 0, width: iw, height: y0 + o))
        piece(e, 0, 1, sh, into: CGRect(x: 0, y: y0, width: x0 + o, height: ih))
        piece(sw - 1 - e, 0, 1, sh, into: CGRect(x: x1 - o, y: y0, width: W - x1 + o, height: ih))
        piece(e, e, 1, 1, into: CGRect(x: 0, y: y1 - o, width: x0 + o, height: H - y1 + o))
        piece(sw - 1 - e, e, 1, 1, into: CGRect(x: x1 - o, y: y1 - o, width: W - x1 + o, height: H - y1 + o))
        piece(e, sh - 1 - e, 1, 1, into: CGRect(x: 0, y: 0, width: x0 + o, height: y0 + o))
        piece(sw - 1 - e, sh - 1 - e, 1, 1, into: CGRect(x: x1 - o, y: 0, width: W - x1 + o, height: y0 + o))
        c.interpolationQuality = .high
        // The icon itself, its outer pixel trimmed again so no soft edge line is left.
        if let core = src.cropping(to: CGRect(x: 1, y: 1, width: sw - 2, height: sh - 2)) {
            c.draw(core, in: CGRect(x: x0 + k, y: y0 + k, width: iw - 2 * k, height: ih - 2 * k))
        }
        guard let out = c.makeImage() else { return .plate(nil) }
        return .extend(UIImage(cgImage: out, scale: scale, orientation: .up))
    }

    /** A drawing surface; with `lift`, grey underneath and drawing in Lighten mode, so black becomes the key grey. */
    private static func canvas(_ pw: Int, _ ph: Int, lift: Bool) -> CGContext? {
        guard let c = CGContext(data: nil, width: pw, height: ph, bitsPerComponent: 8, bytesPerRow: 0,
                                space: CGColorSpaceCreateDeviceRGB(), bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue) else { return nil }
        c.interpolationQuality = .high
        if lift {
            let g = Look.p.high
            c.setFillColor(red: CGFloat((g >> 16) & 0xFF) / 255, green: CGFloat((g >> 8) & 0xFF) / 255, blue: CGFloat(g & 0xFF) / 255, alpha: 1)
            c.fill(CGRect(x: 0, y: 0, width: pw, height: ph))
            c.setBlendMode(.lighten)
        }
        return c
    }

    /**
     * Busy or very rounded artwork: the whole icon, its proportions kept, over a soft blur of
     * itself enlarged to fill the tile; the icon's edge is feathered into the blur.
     */
    private static func ambient(_ cg: CGImage, pw: Int, ph: Int, scale: CGFloat, lift: Bool) -> Fit {
        let W = CGFloat(pw), H = CGFloat(ph), short = min(W, H)
        let icon = CIImage(cgImage: cg)
        let side = min(icon.extent.width, icon.extent.height)
        // The background: the solid middle of the icon (away from rounded corners), enlarged to
        // cover the tile and blurred heavily, so only its colours remain.
        let mid = icon.cropped(to: icon.extent.insetBy(dx: side * 0.16, dy: side * 0.16))
        let kb = max(W, H) * 1.3 / mid.extent.width
        let bg = mid.transformed(by: CGAffineTransform(translationX: -mid.extent.midX, y: -mid.extent.midY))
            .clampedToExtent()
            .transformed(by: CGAffineTransform(scaleX: kb, y: kb))
            .transformed(by: CGAffineTransform(translationX: W / 2, y: H / 2))
            .applyingGaussianBlur(sigma: Double(short * 0.09))
        // The icon at 78% of the tile's short side, centred.
        let f = short * 0.78, kf = f / side
        let fg = icon.transformed(by: CGAffineTransform(scaleX: kf, y: kf))
            .transformed(by: CGAffineTransform(translationX: (W - icon.extent.width * kf) / 2, y: (H - icon.extent.height * kf) / 2))
        // A rounded mask a little inside the icon, softened, so its edge melts into the blur.
        let r = CGRect(x: (W - f) / 2, y: (H - f) / 2, width: f, height: f).insetBy(dx: f * 0.03, dy: f * 0.03)
        guard let gen = CIFilter(name: "CIRoundedRectangleGenerator", parameters: [
            "inputExtent": CIVector(cgRect: r), "inputRadius": f * 0.2, "inputColor": CIColor.white]),
              let shape = gen.outputImage else { return .plate(nil) }
        let mask = shape.applyingGaussianBlur(sigma: Double(f * 0.035))
        let out = fg.applyingFilter("CIBlendWithMask", parameters: [kCIInputBackgroundImageKey: bg, kCIInputMaskImageKey: mask])
        let frame = CGRect(x: 0, y: 0, width: W, height: H)
        guard let rendered = ci.createCGImage(out.cropped(to: frame), from: frame), let c = canvas(pw, ph, lift: lift) else { return .plate(nil) }
        c.draw(rendered, in: frame)
        guard let img = c.makeImage() else { return .plate(nil) }
        return .extend(UIImage(cgImage: img, scale: scale, orientation: .up))
    }

    /** Corners opaque? Border one plain colour (low spread of brightness)? */
    private static func edgeStats(_ cg: CGImage) -> (opaque: Bool, busy: Bool, edge: Color, sides: Bool, lum: Double)? {
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
        return (opaque, sd > 0.16, edge, sides, m)
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
    /** The tile's real width (square tiles stretch to their column). */
    @State private var width: CGFloat = 0

    var body: some View {
        let cat = s.kind == .app && s.brand == nil ? Catalog.byKey(s.value) : (s.kind == .app ? Catalog.apps.first { $0.name.caseInsensitiveCompare(s.name) == .orderedSame } : nil)
        let base = s.kind == .channel ? Color(hex: 0x263042) : (cat.map { Color(hex: $0.bg) } ?? Color(hex: 0x3A3A3F))
        // Near-black tiles would vanish on a dark remote: lift them to a dark grey.
        let bg = !Look.p.light && (cat?.bg ?? 0x3A3A3F) < 0x181818 ? T.high : base
        let shape = square ? AnyShape(RoundedRectangle(cornerRadius: 18, style: .continuous)) : AnyShape(Circle())
        ZStack {
            shape.fill(bg)
            switch fit {
            // Made for this exact tile size, so it's shown as it is (never stretched or cropped).
            case .extend(let img): Image(uiImage: img).resizable()
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
        .onGeometryChange(for: CGFloat.self) { $0.size.width } action: { width = $0 }
        .task(id: "\(s.id)-\(Int(width))") {
            guard width > 0, let key = cat?.key, let img = await Logos.best(key) else { return }
            image = img
            fit = Logos.fit(img, width: width, height: size, scale: scale)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(s.name)
        .accessibilityAddTraits(.isButton)
    }
}
