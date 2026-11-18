import SwiftUI
import Speech
import AVFoundation

/**
 * Search on the TV. What you type goes into the box selected on the TV as you type it (pick
 * one with the arrows below); Search presses Enter there (Roku searches for it). The mic turns
 * on Voice mode: what you say is put in the box and searched straight away. On Android TV you
 * can also search YouTube or the Play Store directly.
 */
struct KeyboardSheet: View {
    @ObservedObject var vm: RemoteModel
    let accent: Color
    var openSearch = true
    var startVoice = false
    @Environment(\.dismiss) private var dismiss
    @State private var text = ""
    @StateObject private var voice = VoiceInput()
    @FocusState private var focused: Bool

    private func change(_ new: String) { vm.liveType(text, new); text = new }
    private func submit() { guard !text.trimmingCharacters(in: .whitespaces).isEmpty else { return }; vm.submitSearch(text); dismiss() }
    /** Clears the whole box, on the phone and on the TV (not just what was typed from here). */
    private func clearAll() { if voice.on { voice.cancel() }; let n = text.count; text = ""; vm.clearTvBox(n) }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
                SheetTitle(title: "Search", sub: caption).padding(.top, 22)
                HStack(spacing: 10) {
                    Group {
                        if voice.on {
                            HStack(spacing: 10) {
                                Circle().fill(voice.listening ? accent : T.muted).frame(width: 8, height: 8)
                                Text(voice.partial.isEmpty ? "Voice mode on…" : voice.partial).font(.system(size: 16)).foregroundStyle(T.ink).lineLimit(1)
                                Spacer()
                            }
                            .accessibilityElement(children: .combine)
                        } else {
                            HStack(spacing: 10) {
                                Image(systemName: "magnifyingglass").foregroundStyle(T.muted)
                                TextField("Search or type", text: Binding(get: { text }, set: { change($0) }))
                                    .focused($focused).submitLabel(.search).onSubmit { submit() }
                                    .foregroundStyle(T.ink).autocorrectionDisabled()
                            }
                        }
                    }
                    .padding(.horizontal, 14).frame(height: 54)
                    .background(RoundedRectangle(cornerRadius: 14, style: .continuous).fill(T.high))
                    Button { if voice.on { voice.cancel() } else { focused = false; voice.start() } } label: {
                        Image(systemName: voice.on ? "xmark" : "mic.fill").font(.system(size: 20)).foregroundStyle(voice.on ? accent : T.ink)
                            .frame(width: 54, height: 54).contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .background(RoundedRectangle(cornerRadius: 14, style: .continuous).fill(T.high))
                    .accessibilityLabel(voice.on ? "Stop voice" : "Voice mode")
                }
                .padding(.top, 16)
                HStack(spacing: 12) {
                    WideButton(title: "Clear", kind: .secondary) { clearAll() }
                    WideButton(title: "Search", enabled: !text.trimmingCharacters(in: .whitespaces).isEmpty || voice.on) { if voice.on { voice.cancel() }; submit() }
                }
                .padding(.top, 4)
                if vm.searchesApps {
                    HStack(spacing: 8) {
                        Text("Search in").font(.system(size: 12.5)).foregroundStyle(T.muted).padding(.trailing, 4)
                        ForEach([("youtube", "YouTube"), ("play", "Play Store")], id: \.0) { w, label in
                            let ok = !text.trimmingCharacters(in: .whitespaces).isEmpty
                            Button { vm.searchIn(w, text); dismiss() } label: {
                                Text(label).font(.system(size: 13, weight: .medium)).foregroundStyle(ok ? T.ink : T.muted)
                                    .frame(maxWidth: .infinity).frame(height: 38)
                            }
                            .buttonStyle(Pressable()).background(Capsule().fill(T.high)).disabled(!ok)
                        }
                    }
                    .padding(.top, 22)
                    NavPad(vm: vm).padding(.top, 18)
                }
            }
            .padding(.horizontal, 24).padding(.bottom, 24)
        .fitSheet()
        .onAppear {
            if openSearch { vm.openTvSearch() }
            // Spoken words go into the box, are typed on the TV, and searched: no extra tap.
            voice.onFinal = { said in change(said.trimmingCharacters(in: .whitespaces)); voice.cancel(); submit() }
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { if startVoice { voice.start() } else { focused = true } }
        }
        .onDisappear { voice.cancel() }
    }

    private var caption: String {
        if voice.on { return "Speak: it's searched when you stop." }
        if let e = voice.error { return e }
        return vm.searchesApps ? "Typed into the box selected on the TV." : "Typed on the TV as you go."
    }
}

/** Speech to text in the app (SFSpeechRecognizer), on the iPhone where possible. Keeps listening in Voice mode. */
@MainActor
final class VoiceInput: ObservableObject {
    @Published private(set) var on = false
    @Published private(set) var listening = false
    @Published private(set) var partial = ""
    @Published private(set) var error: String?
    var onFinal: (String) -> Void = { _ in }

    private let engine = AVAudioEngine()
    private var request: SFSpeechAudioBufferRecognitionRequest?
    private var task: SFSpeechRecognitionTask?
    private let recognizer = SFSpeechRecognizer()
    /** Which listen() a callback belongs to; older ones are ignored. */
    private var gen = 0
    private var misses = 0

    func start() {
        error = nil
        SFSpeechRecognizer.requestAuthorization { status in
            Task { @MainActor in
                guard status == .authorized else { self.error = "Speech recognition is off in Settings."; return }
                AVAudioApplication.requestRecordPermission { ok in
                    Task { @MainActor in
                        guard ok else { self.error = "Microphone access is off in Settings."; return }
                        self.on = true
                        self.listen()
                    }
                }
            }
        }
    }

    private func listen() {
        guard on, let rec = recognizer, rec.isAvailable else { error = "Speech isn't available right now."; on = false; return }
        stopAudio()
        gen += 1
        let mine = gen
        partial = ""
        do {
            let session = AVAudioSession.sharedInstance()
            try session.setCategory(.record, mode: .measurement, options: .duckOthers)
            try session.setActive(true, options: .notifyOthersOnDeactivation)
            let req = SFSpeechAudioBufferRecognitionRequest()
            req.shouldReportPartialResults = true
            if rec.supportsOnDeviceRecognition { req.requiresOnDeviceRecognition = true }
            request = req
            let input = engine.inputNode
            input.installTap(onBus: 0, bufferSize: 1024, format: input.outputFormat(forBus: 0)) { buf, _ in req.append(buf) }
            engine.prepare()
            try engine.start()
            listening = true
            task = rec.recognitionTask(with: req) { [weak self] result, err in
                Task { @MainActor in
                    guard let self, self.on, self.gen == mine else { return }
                    if let result {
                        self.misses = 0
                        self.partial = result.bestTranscription.formattedString
                        if result.isFinal {
                            let said = self.partial
                            self.partial = ""
                            if !said.isEmpty { self.onFinal(said) }
                            self.listen() // Voice mode: next phrase
                            return
                        }
                    }
                    if err != nil {
                        // Silence or a pause: finish this phrase and keep listening (unless it keeps failing).
                        let said = self.partial
                        self.partial = ""
                        if !said.isEmpty { self.onFinal(said); self.misses = 0 } else { self.misses += 1 }
                        if self.misses > 6 { self.error = "Didn't catch that. Tap the mic and try again."; self.cancel(); return }
                        try? await Task.sleep(nanoseconds: 250_000_000)
                        if self.gen == mine { self.listen() }
                    }
                }
            }
        } catch {
            self.error = "Couldn't listen. Try again."
            cancel()
        }
    }

    private func stopAudio() {
        gen += 1
        task?.cancel(); task = nil
        request?.endAudio(); request = nil
        if engine.isRunning { engine.stop() }
        engine.inputNode.removeTap(onBus: 0)
        listening = false
    }

    /** Leaves Voice mode; whatever was half heard is dropped. */
    func cancel() {
        on = false; misses = 0
        stopAudio()
        partial = ""
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
    }
}
