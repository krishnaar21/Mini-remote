import SwiftUI
import UIKit

/** What the dial / trackpad is showing right now (finger, lit arrow, two-finger mode). */
struct PadState: Equatable {
    var touch: CGPoint?
    var lit: RemoteKey?
    var wellDown = false
    var two = false
    var twoKey: RemoteKey?
}

/**
 * The touch logic for both the dial and the trackpad, in UIKit so it can follow several
 * fingers at once. One finger navigates (tap, hold, slide in steps); two fingers slide for
 * volume (up / down) and channel (sideways); a quick two-finger tap is reported for the
 * dial / trackpad switch. While a finger is down, the page scroller behind is held still.
 */
final class TouchPadView: UIView {
    enum Mode { case dial, trackpad }
    var mode: Mode = .dial
    var stepPx: CGFloat = 36
    var offline = false
    var onKey: (RemoteKey) -> Void = { _ in }
    var onTick: (RemoteKey) -> Void = { _ in }
    var onVoice: () -> Void = {}
    var onTwo: (RemoteKey) -> Void = { _ in }
    var onTwoTap: () -> Void = {}
    var onOffline: () -> Void = {}
    var onState: (PadState) -> Void = { _ in }

    private var state = PadState() { didSet { if state != oldValue { onState(state) } } }
    private var primary: UITouch?
    private var last: CGPoint = .zero
    private var startPt: CGPoint = .zero
    private var acc: CGPoint = .zero
    private var moved = false, held = false, inWell = false
    private var zoneKey: RemoteKey = .up
    private var horizontal: Bool?
    private var holdTimer: Timer?, repeatTimer: Timer?
    private var twoActive = false, twoAcc = CGPoint.zero, twoLast: CGPoint?, twoCount = 0, twoMax = 0
    private var twoTravel: CGFloat = 0, twoStart: TimeInterval = 0
    private var locked: [UIScrollView] = []

    override init(frame: CGRect) { super.init(frame: frame); isMultipleTouchEnabled = true; backgroundColor = .clear }
    required init?(coder: NSCoder) { fatalError() }

    private func live(_ e: UIEvent?) -> [UITouch] {
        (e?.allTouches ?? []).filter { $0.phase != .ended && $0.phase != .cancelled && $0.view === self }
    }

    private func lockScroll(_ on: Bool) {
        if on {
            var v = superview
            while let s = v { if let sc = s as? UIScrollView, sc.isScrollEnabled { sc.isScrollEnabled = false; locked.append(sc) }; v = s.superview }
        } else { locked.forEach { $0.isScrollEnabled = true }; locked = [] }
    }

    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
        if primary == nil && !twoActive { lockScroll(true) }
        let all = live(event)
        if offline { if primary == nil { primary = touches.first; startPt = primary!.location(in: self); moved = false }; return }
        if all.count >= 2 { beginTwo(all); return }
        if primary == nil, let t = touches.first { primary = t; beginOne(t.location(in: self)) }
    }

    override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent?) {
        if offline { if let p = primary, hypot(p.location(in: self).x - startPt.x, p.location(in: self).y - startPt.y) > 10 { moved = true }; return }
        let all = live(event)
        if twoActive || all.count >= 2 { if !twoActive { beginTwo(all) }; moveTwo(all); return }
        if let p = primary, touches.contains(p) { moveOne(p.location(in: self)) }
    }

    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) { lifted(event, cancelled: false) }
    override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent?) { lifted(event, cancelled: true) }

    private func lifted(_ event: UIEvent?, cancelled: Bool) {
        let rest = live(event)
        if !rest.isEmpty { if twoActive { twoLast = nil; twoCount = rest.count }; return }
        finish(cancelled: cancelled)
    }

    /* ---- one finger */

    private func beginOne(_ p: CGPoint) {
        let c = CGPoint(x: bounds.midX, y: bounds.midY)
        let rel = CGPoint(x: p.x - c.x, y: p.y - c.y)
        acc = .zero; last = p; moved = false; held = false; horizontal = nil
        if mode == .dial {
            inWell = hypot(rel.x, rel.y) < bounds.width * 0.385 / 2
            let h = abs(rel.x) > abs(rel.y)
            zoneKey = h ? (rel.x > 0 ? .right : .left) : (rel.y > 0 ? .down : .up)
            if !inWell { horizontal = h }
            state = PadState(touch: p, lit: inWell ? nil : zoneKey, wellDown: inWell)
        } else {
            inWell = true
            state = PadState(touch: p)
        }
        holdTimer = Timer.scheduledTimer(withTimeInterval: mode == .dial ? 0.35 : 0.45, repeats: false) { [weak self] _ in
            guard let self, !self.moved else { return }
            self.held = true
            if self.inWell { self.onVoice() } else {
                self.onTick(self.zoneKey)
                self.repeatTimer = Timer.scheduledTimer(withTimeInterval: 0.12, repeats: true) { [weak self] _ in
                    guard let self else { return }
                    self.onTick(self.zoneKey)
                }
            }
        }
    }

    private func moveOne(_ p: CGPoint) {
        acc.x += p.x - last.x; acc.y += p.y - last.y
        last = p
        state.touch = p
        if mode == .dial {
            if !moved && hypot(acc.x, acc.y) > 8 {
                if horizontal == nil || inWell { horizontal = abs(acc.x) > abs(acc.y) }
                moved = true; holdTimer?.invalidate()
                state.wellDown = false
            }
            guard moved, !held, let h = horizontal else { return }
            let v = h ? acc.x : acc.y
            if abs(v) >= stepPx {
                let k: RemoteKey = h ? (v > 0 ? .right : .left) : (v > 0 ? .down : .up)
                onTick(k); state.lit = k
                if h { acc = CGPoint(x: acc.x - (v > 0 ? stepPx : -stepPx), y: 0) } else { acc = CGPoint(x: 0, y: acc.y - (v > 0 ? stepPx : -stepPx)) }
            }
        } else {
            if !moved && hypot(acc.x, acc.y) > 8 { moved = true; holdTimer?.invalidate() }
            guard moved, !held else { return }
            if abs(acc.x) >= stepPx && abs(acc.x) >= abs(acc.y) {
                let k: RemoteKey = acc.x > 0 ? .right : .left
                onTick(k); state.lit = k
                acc = CGPoint(x: acc.x - (acc.x > 0 ? stepPx : -stepPx), y: acc.y * 0.5)
            } else if abs(acc.y) >= stepPx {
                let k: RemoteKey = acc.y > 0 ? .down : .up
                onTick(k); state.lit = k
                acc = CGPoint(x: acc.x * 0.5, y: acc.y - (acc.y > 0 ? stepPx : -stepPx))
            }
        }
    }

    /* ---- two fingers */

    private func centroid(_ t: [UITouch]) -> CGPoint {
        let pts = t.map { $0.location(in: self) }
        return CGPoint(x: pts.map(\.x).reduce(0, +) / CGFloat(pts.count), y: pts.map(\.y).reduce(0, +) / CGFloat(pts.count))
    }

    private func beginTwo(_ t: [UITouch]) {
        if twoActive { return }
        holdTimer?.invalidate(); repeatTimer?.invalidate()
        twoActive = true; moved = true
        twoAcc = .zero; twoTravel = 0; twoStart = CACurrentMediaTime(); twoMax = t.count; twoCount = t.count
        twoLast = centroid(t)
        state = PadState(two: true)
    }

    private func moveTwo(_ t: [UITouch]) {
        guard !t.isEmpty else { return }
        twoMax = max(twoMax, t.count)
        let c = centroid(t)
        if t.count != twoCount || twoLast == nil { twoCount = t.count; twoLast = c; return }
        if t.count >= 2, let l = twoLast {
            twoAcc.x += c.x - l.x; twoAcc.y += c.y - l.y
            twoTravel += hypot(c.x - l.x, c.y - l.y)
        }
        twoLast = c
        let step: CGFloat = 30
        if abs(twoAcc.y) >= step && abs(twoAcc.y) >= abs(twoAcc.x) {
            let k: RemoteKey = twoAcc.y < 0 ? .volUp : .volDown
            onTwo(k); state.twoKey = k
            twoAcc = CGPoint(x: twoAcc.x * 0.5, y: twoAcc.y - (twoAcc.y > 0 ? step : -step))
        } else if abs(twoAcc.x) >= step * 1.6 {
            let k: RemoteKey = twoAcc.x > 0 ? .chUp : .chDown
            onTwo(k); state.twoKey = k
            twoAcc = CGPoint(x: twoAcc.x - (twoAcc.x > 0 ? step * 1.6 : -step * 1.6), y: twoAcc.y * 0.5)
        }
    }

    /* ---- end */

    private func finish(cancelled: Bool) {
        holdTimer?.invalidate(); repeatTimer?.invalidate()
        if offline {
            if !moved && !cancelled { onOffline() }
        } else if twoActive {
            if !cancelled && twoMax >= 2 && twoTravel < 12 && CACurrentMediaTime() - twoStart < 0.35 { onTwoTap() }
        } else if !moved && !held && !cancelled {
            onKey(mode == .dial ? (inWell ? .ok : zoneKey) : .ok)
        }
        primary = nil; twoActive = false; twoLast = nil; moved = false; held = false
        state = PadState()
        lockScroll(false)
    }
}

struct TouchPad: UIViewRepresentable {
    var mode: TouchPadView.Mode
    var stepPx: CGFloat
    var offline: Bool
    var onKey: (RemoteKey) -> Void
    var onTick: (RemoteKey) -> Void
    var onVoice: () -> Void
    var onTwo: (RemoteKey) -> Void
    var onTwoTap: () -> Void
    var onOffline: () -> Void
    @Binding var state: PadState

    func makeUIView(context: Context) -> TouchPadView { TouchPadView() }
    func updateUIView(_ v: TouchPadView, context: Context) {
        v.mode = mode; v.stepPx = stepPx; v.offline = offline
        v.onKey = onKey; v.onTick = onTick; v.onVoice = onVoice; v.onTwo = onTwo; v.onTwoTap = onTwoTap; v.onOffline = onOffline
        let b = $state
        v.onState = { s in DispatchQueue.main.async { b.wrappedValue = s } }
    }
}
