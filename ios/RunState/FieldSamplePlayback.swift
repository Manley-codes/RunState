#if RUNSTATE_FIELD_TOOLS
import AVFAudio
import Combine
import Foundation

enum FieldSampleSound: String, CaseIterable {
    case original = "field-finish-original"
    case excerpt = "field-finish-excerpt"
    case chime = "snippet_close"
}

@MainActor
protocol FieldSampleOutput: AnyObject {
    func open(interrupted: @escaping () -> Void) throws
    func play(_ sound: FieldSampleSound, from milliseconds: Int, for duration: Int?) async throws
    func speak(_ text: String) async throws
    func close()
}

@MainActor
final class FieldSamplePlayback: ObservableObject {
    enum Phase: Equatable {
        case idle, leadIn, reflection, excerpt, closing, restored, finished
        case failed(String)
    }
    @Published private(set) var phase: Phase = .idle
    var isPlaying: Bool { task != nil }

    private let output: any FieldSampleOutput
    private let now: () -> ContinuousClock.Instant
    private let waitUntil: (ContinuousClock.Instant) async throws -> Void
    private var task: Task<Void, Never>?
    private var activeID: UUID?

    convenience init() {
        self.init(output: FieldSampleAudioOutput())
    }

    init(output: any FieldSampleOutput, now: @escaping () -> ContinuousClock.Instant = { ContinuousClock().now },
         waitUntil: @escaping (ContinuousClock.Instant) async throws -> Void = {
             try await ContinuousClock().sleep(until: $0)
         }) {
        self.output = output
        self.now = now
        self.waitUntil = waitUntil
    }

    func start(text: String, startMilliseconds: Int, durationMilliseconds: Int, originallyPlaying: Bool) {
        stop()
        guard !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
              startMilliseconds >= 0, (1...30_000).contains(durationMilliseconds),
              startMilliseconds <= 30_000 - durationMilliseconds else {
            phase = .failed("The sample’s reflection or excerpt is unavailable.")
            return
        }
        let id = UUID()
        activeID = id
        phase = .leadIn
        task = Task { [weak self] in
            guard let self, self.activeID == id else { return }
            do {
                try self.output.open { [weak self] in
                    guard let self, self.activeID == id else { return }
                    self.finish(.failed("Playback was interrupted. You can play the sample again."))
                }
                // In the fictional run the original is already 1 second into its track.
                var originalPosition = 1_000
                if originallyPlaying {
                    try await self.output.play(.original, from: originalPosition, for: 5_000)
                    originalPosition += 5_000
                } else {
                    try await self.waitUntil(self.now().advanced(by: .seconds(5)))
                }
                try Task.checkCancellation()
                self.phase = .reflection
                try await self.output.speak(text)
                try Task.checkCancellation()
                self.phase = .excerpt
                try await self.output.play(.excerpt, from: startMilliseconds, for: durationMilliseconds)
                try Task.checkCancellation()
                self.phase = .closing
                // Android's 1.2-second return gap starts when the excerpt ends; the
                // 420ms Run chime plays inside that gap. Paused music stays silent.
                let returnAt = self.now().advanced(by: .milliseconds(1_200))
                try await self.output.play(.chime, from: 0, for: nil)
                try Task.checkCancellation()
                if originallyPlaying {
                    try await self.waitUntil(returnAt)
                    try Task.checkCancellation()
                    self.phase = .restored
                    try await self.output.play(.original, from: originalPosition, for: 3_000)
                }
                if self.activeID == id { self.finish(.finished) }
            } catch {
                if self.activeID == id {
                    self.finish(.failed("The sample couldn’t finish playing. Check the audio output and try again."))
                }
            }
        }
    }

    func stop() { finish(.idle) }

    private func finish(_ result: Phase) {
        activeID = nil
        task?.cancel()
        task = nil
        output.close()
        phase = result
    }
}

/// One audio session covers the full sample, including speech and the quiet gap.
/// All clips are local. Scheduling a fixed range of PCM frames prevents a blip from
/// the beginning of the selected track or an overlong excerpt on a busy UI thread.
@MainActor
final class FieldSampleAudioOutput: NSObject, FieldSampleOutput, AVSpeechSynthesizerDelegate {
    private enum AudioError: Error { case unavailable }
    private let session = AVAudioSession.sharedInstance()
    private var engine: AVAudioEngine?
    private var node: AVAudioPlayerNode?
    private var files: [FieldSampleSound: AVAudioFile] = [:]
    private var voice: AVSpeechSynthesizer?
    private var utterance: AVSpeechUtterance?
    private var ownsSession = false
    private var observers: [NSObjectProtocol] = []
    private var operation: UUID?
    private var completion: CheckedContinuation<Void, Error>?
    private var watchdog: Task<Void, Never>?

    func open(interrupted: @escaping () -> Void) throws {
        for sound in FieldSampleSound.allCases {
            let ext = sound == .chime ? "wav" : "mp3"
            guard let url = Bundle.main.url(forResource: sound.rawValue, withExtension: ext) else {
                throw AudioError.unavailable
            }
            let file = try AVAudioFile(forReading: url)
            guard file.processingFormat.channelCount == 1, file.processingFormat.sampleRate == 48_000 else {
                throw AudioError.unavailable
            }
            files[sound] = file
        }
        try session.setCategory(.playback, mode: .default, options: [.duckOthers])
        try session.setActive(true)
        ownsSession = true
        let engine = AVAudioEngine()
        let node = AVAudioPlayerNode()
        engine.attach(node)
        engine.connect(node, to: engine.mainMixerNode, format: files[.original]?.processingFormat)
        self.engine = engine
        self.node = node
        engine.prepare()
        try engine.start()
        let voice = AVSpeechSynthesizer()
        voice.usesApplicationAudioSession = true
        voice.delegate = self
        self.voice = voice

        for name in [AVAudioSession.interruptionNotification, AVAudioSession.routeChangeNotification,
                     AVAudioSession.mediaServicesWereResetNotification] {
            let observer = NotificationCenter.default.addObserver(forName: name, object: nil, queue: .main) { event in
                let began = event.name == AVAudioSession.interruptionNotification &&
                    (event.userInfo?[AVAudioSessionInterruptionTypeKey] as? UInt) == AVAudioSession.InterruptionType.began.rawValue
                let unplugged = event.name == AVAudioSession.routeChangeNotification &&
                    (event.userInfo?[AVAudioSessionRouteChangeReasonKey] as? UInt) == AVAudioSession.RouteChangeReason.oldDeviceUnavailable.rawValue
                if began || unplugged || event.name == AVAudioSession.mediaServicesWereResetNotification {
                    Task { @MainActor in interrupted() }
                }
            }
            observers.append(observer)
        }
    }

    func play(_ sound: FieldSampleSound, from milliseconds: Int, for duration: Int?) async throws {
        try Task.checkCancellation()
        guard let file = files[sound], let node, engine?.isRunning == true else { throw AudioError.unavailable }
        let framesPerMs = file.processingFormat.sampleRate / 1_000
        let first = AVAudioFramePosition((Double(milliseconds) * framesPerMs).rounded())
        let length = duration.map { AVAudioFramePosition((Double($0) * framesPerMs).rounded()) } ?? (file.length - first)
        guard first >= 0, length > 0, length <= AVAudioFramePosition(UInt32.max),
              first <= file.length, length <= file.length - first else { throw AudioError.unavailable }
        let id = UUID()
        node.stop()
        node.volume = sound == .chime ? 0.76 : 1
        try await withCheckedThrowingContinuation { (next: CheckedContinuation<Void, Error>) in
            watch(id, next, timeout: 5 + Double(length) / file.processingFormat.sampleRate)
            node.scheduleSegment(file, startingFrame: first, frameCount: AVAudioFrameCount(length),
                                 at: nil, completionCallbackType: .dataPlayedBack) { [weak self] _ in
                Task { @MainActor in self?.complete(id) }
            }
            node.play()
        }
    }

    func speak(_ text: String) async throws {
        try Task.checkCancellation()
        guard let voice else { throw AudioError.unavailable }
        let line = AVSpeechUtterance(string: text)
        line.voice = AVSpeechSynthesisVoice(language: "en-US")
        utterance = line
        try await withCheckedThrowingContinuation { (next: CheckedContinuation<Void, Error>) in
            watch(UUID(), next, timeout: 15 + Double(text.count) * 0.24)
            voice.speak(line)
        }
    }

    private func watch(_ id: UUID, _ next: CheckedContinuation<Void, Error>, timeout: Double) {
        operation = id
        completion = next
        watchdog = Task { [weak self] in
            do { try await Task.sleep(for: .seconds(timeout)) } catch { return }
            self?.complete(id, error: AudioError.unavailable)
        }
    }

    private func complete(_ id: UUID, error: Error? = nil) {
        guard operation == id else { return }
        operation = nil
        watchdog?.cancel()
        watchdog = nil
        let next = completion
        completion = nil
        if let error { next?.resume(throwing: error) }
        else { next?.resume() }
    }

    func close() {
        if let operation { complete(operation, error: CancellationError()) }
        for observer in observers { NotificationCenter.default.removeObserver(observer) }
        observers.removeAll()
        voice?.delegate = nil
        voice?.stopSpeaking(at: .immediate)
        voice = nil
        utterance = nil
        node?.stop()
        engine?.stop()
        node = nil
        engine = nil
        files.removeAll()
        if ownsSession {
            ownsSession = false
            try? session.setActive(false, options: .notifyOthersOnDeactivation)
        }
    }

    nonisolated func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didFinish line: AVSpeechUtterance) {
        Task { @MainActor [weak self] in
            guard let self, self.utterance === line, let id = self.operation else { return }
            self.utterance = nil
            self.complete(id)
        }
    }

    nonisolated func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didCancel line: AVSpeechUtterance) {
        Task { @MainActor [weak self] in
            guard let self, self.utterance === line, let id = self.operation else { return }
            self.utterance = nil
            self.complete(id, error: AudioError.unavailable)
        }
    }
}
#endif
