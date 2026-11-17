import SwiftUI
import Speech
import AVFoundation

/**
 * Search on the TV. The TV's search opens as the sheet does; what you type goes to the TV as
 * you type it. The mic (its own square key) turns on Voice mode: the box gives way to
 * "Voice mode on…", your words show below and are typed on the TV, phrase after phrase,
 * until Cancel. Speech is recognised in the app (on the iPhone where possible): no popup.
 */
struct KeyboardSheet: View {
    @ObservedObject var vm: RemoteModel
    let accent: Color
    @Environment(\.dismiss) private var dismiss
    @State private var text = ""
    @StateObject private var voice = VoiceInput()
    @FocusState private var focused: Bool

    private func change(_ new: String) { vm.liveType(text, new); text = new }

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("Search").font(.system(size: 18, weight: .semibold)).foregroundStyle(T.ink).frame(maxWidth: .infinity)
            HStack(spacing: 10) {
                if voice.on {
                    HStack(spacing: 10) {
                        Circle().fill(voice.listening ? accent : T.muted).frame(width: 8, height: 8)
                        Text("Voice mode on…").font(.system(size: 16)).foregroundStyle(T.ink)
                        Spacer()
                    }
                    .padding(.horizontal, 16).frame(height: 52)
                    .glassEffect(.regular, in: .rect(cornerRadius: 14))
                    .accessibilityElement(children: .combine)
                } else {
                    HStack(spacing: 10) {
                        Image(systemName: "magnifyingglass").foregroundStyle(T.muted)
                        TextField("Search or type", text: Binding(get: { text }, set: { change($0) }))
                            .focused($focused).submitLabel(.done).onSubmit { dismiss() }
                            .foregroundStyle(T.ink).autocorrectionDisabled()
                    }
                    .padding(.horizontal, 14).frame(height: 52)
                    .glassEffect(.regular.interactive(), in: .rect(cornerRadius: 14))
                    // The mic: its own square key, outside the box.
                    Button { start() } label: {
                        Image(systemName: "mic.fill").font(.system(size: 20)).foregroundStyle(.white)
                            .frame(width: 52, height: 52).contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .glassEffect(.regular.interactive(), in: .rect(cornerRadius: 14))
                    .accessibilityLabel("Voice mode")
                }
            }
            Text(caption).font(.system(size: voice.on ? 16 : 12.5)).foregroundStyle(voice.on && !(voice.partial.isEmpty && text.isEmpty) ? T.ink : T.muted)
                .padding(.leading, 4).accessibilityAddTraits(.updatesFrequently)
            HStack(spacing: 12) {
                if voice.on {
                    WideButton(title: "Clear", kind: .secondary) { change("") }
                    WideButton(title: "Cancel") { voice.cancel() }
                } else {
                    WideButton(title: "Delete", kind: .secondary) { if text.isEmpty { vm.backspace() } else { change(String(text.dropLast())) } }
                    WideButton(title: "Done") { dismiss() }
                }
            }
            Spacer(minLength: 0)
        }
        .padding(24)
        .presentationDetents([.height(300), .large])
        .presentationDragIndicator(.visible)
        .onAppear {
            vm.openTvSearch()
            voice.onFinal = { said in change(text.trimmingCharacters(in: .whitespaces).isEmpty ? said : text.trimmingCharacters(in: .whitespaces) + " " + said) }
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { focused = true }
        }
        .onDisappear { voice.cancel() }
    }

    private var caption: String {
        if voice.on { return !voice.partial.isEmpty ? voice.partial : (!text.isEmpty ? text : "Speak now") }
        return voice.error ?? "Typed on the TV as you go."
    }

    private func start() {
        focused = false
        voice.start()
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
