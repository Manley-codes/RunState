import AVFAudio
import Foundation

enum ReflectionVoiceResult: Equatable { case finished, interrupted, failed }

@MainActor
protocol ReflectionVoicePlaying: AnyObject {
    func play(audio: Data, onEnd: @escaping (ReflectionVoiceResult) -> Void) throws
    func speak(text: String, onEnd: @escaping (ReflectionVoiceResult) -> Void) throws
    func stop()
}

/// Each playback owns the audio session until it finishes, fails or is stopped.
/// Releasing it lets iOS restore the other app's volume and playback policy.
@MainActor
final class ReflectionVoicePlayer: NSObject, ReflectionVoicePlaying, AVAudioPlayerDelegate, AVSpeechSynthesizerDelegate {
    private enum PlaybackError: Error { case unreadableAudio, emptyText }

    private let session = AVAudioSession.sharedInstance()
    private let notifications: NotificationCenter
    private var observers: [NSObjectProtocol] = []
    private var watchdog: Task<Void, Never>?
    private var generation = 0
    private var completion: ((ReflectionVoiceResult) -> Void)?
    private var audioPlayer: AVAudioPlayer?
    private var speech: AVSpeechSynthesizer?
    private var utterance: AVSpeechUtterance?

    init(notifications: NotificationCenter = .default) {
        self.notifications = notifications
        super.init()
    }

    func play(audio: Data, onEnd: @escaping (ReflectionVoiceResult) -> Void) throws {
        stop()
        guard !audio.isEmpty else { throw PlaybackError.unreadableAudio }
        let player = try AVAudioPlayer(data: audio)
        guard player.duration.isFinite, player.duration > 0 else { throw PlaybackError.unreadableAudio }
        try acquire(onEnd)
        audioPlayer = player
        player.delegate = self
        guard player.prepareToPlay(), player.play() else {
            stop()
            throw PlaybackError.unreadableAudio
        }
        armWatchdog(seconds: player.duration + 5)
    }

    func speak(text: String, onEnd: @escaping (ReflectionVoiceResult) -> Void) throws {
        stop()
        guard !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { throw PlaybackError.emptyText }
        let line = AVSpeechUtterance(string: text)
        line.voice = AVSpeechSynthesisVoice(language: "en-US")
        let synthesizer = AVSpeechSynthesizer()
        synthesizer.usesApplicationAudioSession = true
        try acquire(onEnd)
        speech = synthesizer
        utterance = line
        synthesizer.delegate = self
        armWatchdog(seconds: 15)
        synthesizer.speak(line)
    }

    func stop() { release(nil) }

    private func acquire(_ onEnd: @escaping (ReflectionVoiceResult) -> Void) throws {
        try session.setCategory(.playback, mode: .default, options: [.duckOthers])
        try session.setActive(true)
        generation += 1
        completion = onEnd
        let current = generation
        for name in [AVAudioSession.interruptionNotification, AVAudioSession.routeChangeNotification,
                     AVAudioSession.mediaServicesWereResetNotification] {
            let observer = notifications.addObserver(forName: name, object: nil, queue: .main) { [weak self] note in
                let interrupted = note.name == AVAudioSession.interruptionNotification &&
                    (note.userInfo?[AVAudioSessionInterruptionTypeKey] as? UInt) == AVAudioSession.InterruptionType.began.rawValue
                let outputRemoved = note.name == AVAudioSession.routeChangeNotification &&
                    (note.userInfo?[AVAudioSessionRouteChangeReasonKey] as? UInt) == AVAudioSession.RouteChangeReason.oldDeviceUnavailable.rawValue
                guard interrupted || outputRemoved || note.name == AVAudioSession.mediaServicesWereResetNotification else { return }
                Task { @MainActor in
                    guard let self, self.generation == current else { return }
                    self.release(.interrupted)
                }
            }
            observers.append(observer)
        }
    }

    /// A missing OS delegate callback must not leave music ducked indefinitely.
    private func armWatchdog(seconds: TimeInterval) {
        watchdog?.cancel()
        let current = generation
        watchdog = Task { [weak self] in
            do { try await Task.sleep(for: .seconds(seconds)) } catch { return }
            guard let self, self.generation == current else { return }
            self.release(.failed)
        }
    }

    private func release(_ result: ReflectionVoiceResult?) {
        guard let onEnd = completion else { return }
        completion = nil
        generation += 1
        watchdog?.cancel()
        watchdog = nil
        for observer in observers { notifications.removeObserver(observer) }
        observers.removeAll()
        audioPlayer?.delegate = nil
        audioPlayer?.stop()
        audioPlayer = nil
        speech?.delegate = nil
        speech?.stopSpeaking(at: .immediate)
        speech = nil
        utterance = nil
        try? session.setActive(false, options: .notifyOthersOnDeactivation)
        if let result { onEnd(result) }
    }

    nonisolated func audioPlayerDidFinishPlaying(_ player: AVAudioPlayer, successfully flag: Bool) {
        Task { @MainActor [weak self] in
            guard let self, self.audioPlayer === player else { return }
            self.release(flag ? .finished : .failed)
        }
    }

    nonisolated func audioPlayerDecodeErrorDidOccur(_ player: AVAudioPlayer, error: Error?) {
        Task { @MainActor [weak self] in
            guard let self, self.audioPlayer === player else { return }
            self.release(.failed)
        }
    }

    nonisolated func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didStart utterance: AVSpeechUtterance) {
        Task { @MainActor [weak self] in
            guard let self, self.utterance === utterance else { return }
            self.armWatchdog(seconds: max(30, Double(utterance.speechString.count) * 0.24 + 15))
        }
    }

    nonisolated func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didFinish utterance: AVSpeechUtterance) {
        Task { @MainActor [weak self] in
            guard let self, self.utterance === utterance else { return }
            self.release(.finished)
        }
    }

    nonisolated func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didCancel utterance: AVSpeechUtterance) {
        Task { @MainActor [weak self] in
            guard let self, self.utterance === utterance else { return }
            self.release(.interrupted)
        }
    }
}
