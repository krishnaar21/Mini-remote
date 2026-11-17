import SwiftUI

/** The gestures, each with a small looping demo. */
private enum Demo { case swipe, tap, double, hold, twoUp, twoSide, twoDouble, ring }

private struct Move: Identifiable { let demo: Demo; let title: String; let text: String; var id: String { title } }

private let ONE = [
    Move(demo: .swipe, title: "Move", text: "Swipe one finger. Hold at an edge to keep going."),
    Move(demo: .tap, title: "Select", text: "Tap the middle of the dial, or anywhere on the trackpad."),
    Move(demo: .double, title: "Mute", text: "Double-tap OK."),
    Move(demo: .hold, title: "Voice", text: "Hold OK, then speak to the TV."),
]
private let TWO = [
    Move(demo: .twoUp, title: "Volume", text: "Slide two fingers up or down."),
    Move(demo: .twoSide, title: "Channel", text: "Slide two fingers left or right."),
    Move(demo: .twoDouble, title: "Switch pad", text: "Double-tap with two fingers: dial ⇄ trackpad."),
]
private let RING = [Move(demo: .ring, title: "Volume ring", text: "Black layout: turn a finger around the ring. Clockwise is louder.")]

/** Gestures sheet: one finger, two fingers, and the ring. */
struct GesturesPage: View {
    let accent: Color
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                SectionTitle(text: "One finger")
                Card { rows(ONE) }
                SectionTitle(text: "Two fingers")
                Card { rows(TWO) }
                SectionTitle(text: "Ring")
                Card { rows(RING) }
                Text("Gestures work the same on the dial and the trackpad.").font(.system(size: 12.5)).foregroundStyle(T.muted).padding(.top, 10)
            }
            .padding(.horizontal, 24).padding(.bottom, 32)
        }
        .navigationTitle("Gestures").navigationBarTitleDisplayMode(.inline)
    }

    @ViewBuilder private func rows(_ l: [Move]) -> some View {
        ForEach(Array(l.enumerated()), id: \.1.id) { i, m in
            HStack(spacing: 16) {
                GestureDemo(demo: m.demo, accent: accent).frame(width: 84, height: 84).accessibilityHidden(true)
                VStack(alignment: .leading, spacing: 3) {
                    Text(m.title).font(.system(size: 15, weight: .medium)).foregroundStyle(T.ink)
                    Text(m.text).font(.system(size: 13)).foregroundStyle(T.muted).fixedSize(horizontal: false, vertical: true)
                }
                Spacer(minLength: 0)
            }
            .padding(.vertical, 14)
            .accessibilityElement(children: .combine)
            if i < l.count - 1 { RowDivider() }
        }
    }
}

private struct GestureDemo: View {
    let demo: Demo
    let accent: Color

    var body: some View {
        TimelineView(.animation) { tl in
            let period = (demo == .hold || demo == .ring) ? 2.2 : 1.8
            let t = tl.date.timeIntervalSinceReferenceDate.truncatingRemainder(dividingBy: period) / period
            Canvas { ctx, size in draw(&ctx, size, CGFloat(t)) }
        }
    }

    private func span(_ t: CGFloat, _ a: CGFloat, _ b: CGFloat) -> CGFloat { max(0, min(1, (t - a) / (b - a))) }
    private func ease(_ x: CGFloat) -> CGFloat { x * x * (3 - 2 * x) }
    private func fade(_ t: CGFloat, _ inEnd: CGFloat, _ outStart: CGFloat) -> CGFloat {
        t < inEnd ? t / inEnd : (t > outStart ? max(0, (1 - t) / (1 - outStart)) : 1)
    }

    private func finger(_ ctx: inout GraphicsContext, _ p: CGPoint, _ r: CGFloat, _ a: CGFloat) {
        guard a > 0 else { return }
        ctx.fill(Path(ellipseIn: CGRect(x: p.x - r * 1.9, y: p.y - r * 1.9, width: r * 3.8, height: r * 3.8)), with: .color(.white.opacity(0.18 * a)))
        ctx.fill(Path(ellipseIn: CGRect(x: p.x - r, y: p.y - r, width: r * 2, height: r * 2)), with: .color(.white.opacity(0.9 * a)))
    }

    private func ripple(_ ctx: inout GraphicsContext, _ c: CGPoint, _ w: CGFloat, _ p: CGFloat) {
        guard p > 0 && p < 1 else { return }
        let r = w * (0.1 + 0.3 * p)
        ctx.stroke(Path(ellipseIn: CGRect(x: c.x - r, y: c.y - r, width: r * 2, height: r * 2)), with: .color(accent.opacity(0.6 * (1 - p))), lineWidth: 2)
    }

    private func line(_ ctx: inout GraphicsContext, _ a: CGPoint, _ b: CGPoint, _ w: CGFloat, _ col: Color) {
        var p = Path(); p.move(to: a); p.addLine(to: b)
        ctx.stroke(p, with: .color(col), style: StrokeStyle(lineWidth: w, lineCap: .round))
    }

    private func draw(_ ctx: inout GraphicsContext, _ size: CGSize, _ t: CGFloat) {
        let w = size.width
        let c = CGPoint(x: w / 2, y: size.height / 2)
        if demo == .ring {
            ctx.fill(Path(ellipseIn: CGRect(x: c.x - w * 0.4, y: c.y - w * 0.4, width: w * 0.8, height: w * 0.8)), with: .color(T.high))
            ctx.stroke(Path(ellipseIn: CGRect(x: c.x - w * 0.47, y: c.y - w * 0.47, width: w * 0.94, height: w * 0.94)), with: .color(T.highest), lineWidth: 2)
        } else {
            ctx.fill(Path(roundedRect: CGRect(origin: .zero, size: size), cornerRadius: w * 0.22), with: .color(T.high))
        }
        let r = w * 0.085
        switch demo {
        case .swipe:
            let p = ease(span(t, 0.15, 0.65)), a = fade(t, 0.08, 0.8)
            let x = w * (0.28 + 0.44 * p)
            if p > 0 { line(&ctx, CGPoint(x: w * 0.28, y: c.y), CGPoint(x: x, y: c.y), r * 1.2, accent.opacity(0.45 * a)) }
            finger(&ctx, CGPoint(x: x, y: c.y), r, a)
        case .tap:
            finger(&ctx, c, r, fade(t, 0.1, 0.55)); ripple(&ctx, c, w, span(t, 0.2, 0.6))
        case .double:
            let on = (0.1...0.25).contains(t) || (0.33...0.48).contains(t)
            finger(&ctx, c, r, on ? 1 : 0.25)
            ripple(&ctx, c, w, span(t, 0.12, 0.4)); ripple(&ctx, c, w, span(t, 0.35, 0.65))
        case .hold:
            let a = fade(t, 0.06, 0.85), p = span(t, 0.12, 0.7)
            finger(&ctx, c, r, a)
            if p > 0 {
                var arc = Path()
                arc.addArc(center: c, radius: w * 0.24, startAngle: .degrees(-90), endAngle: .degrees(-90 + 360 * Double(p)), clockwise: false)
                ctx.stroke(arc, with: .color(accent.opacity(a)), style: StrokeStyle(lineWidth: 3, lineCap: .round))
            }
        case .twoUp:
            let p = ease(span(t, 0.15, 0.65)), a = fade(t, 0.08, 0.8)
            let y = w * (0.7 - 0.4 * p)
            for fx in [0.38, 0.62] as [CGFloat] {
                if p > 0 { line(&ctx, CGPoint(x: w * fx, y: w * 0.7), CGPoint(x: w * fx, y: y), r * 1.2, accent.opacity(0.4 * a)) }
                finger(&ctx, CGPoint(x: w * fx, y: y), r, a)
            }
        case .twoSide:
            let p = ease(span(t, 0.15, 0.65)), a = fade(t, 0.08, 0.8)
            let x = w * (0.3 + 0.4 * p)
            for fy in [0.38, 0.62] as [CGFloat] {
                if p > 0 { line(&ctx, CGPoint(x: w * 0.3, y: w * fy), CGPoint(x: x, y: w * fy), r * 1.2, accent.opacity(0.4 * a)) }
                finger(&ctx, CGPoint(x: x, y: w * fy), r, a)
            }
        case .twoDouble:
            let on = (0.1...0.25).contains(t) || (0.33...0.48).contains(t)
            for fx in [0.38, 0.62] as [CGFloat] { finger(&ctx, CGPoint(x: w * fx, y: c.y), r, on ? 1 : 0.25) }
            ripple(&ctx, c, w * 1.2, span(t, 0.35, 0.7))
        case .ring:
            // From the bottom, clockwise: louder.
            let p = ease(span(t, 0.1, 0.75)), a = fade(t, 0.05, 0.85)
            let rr = w * 0.47
            let ang = 90 + 140 * Double(p)
            if p > 0 {
                var arc = Path()
                arc.addArc(center: c, radius: rr, startAngle: .degrees(90), endAngle: .degrees(ang), clockwise: false)
                ctx.stroke(arc, with: .color(accent.opacity(a)), style: StrokeStyle(lineWidth: 4, lineCap: .round))
            }
            let rad = ang * .pi / 180
            finger(&ctx, CGPoint(x: c.x + rr * CGFloat(cos(rad)), y: c.y + rr * CGFloat(sin(rad))), r, a)
        }
    }
}
