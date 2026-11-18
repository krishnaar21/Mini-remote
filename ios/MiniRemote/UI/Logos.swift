import SwiftUI
import UIKit

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
