import AVFAudio

/// Spotify owns playback. This session only lowers other audio while the local utterance is speaking.
final class MileVoice: NSObject, AVSpeechSynthesizerDelegate {
    private let speech = AVSpeechSynthesizer()
    private let session = AVAudioSession.sharedInstance()
    private var activeUtterance: AVSpeechUtterance?
    var onFailure: (() -> Void)?

    override init() {
        super.init()
        speech.usesApplicationAudioSession = true
        speech.delegate = self
        NotificationCenter.default.addObserver(self, selector: #selector(interrupted(_:)),
            name: AVAudioSession.interruptionNotification, object: session)
    }

    func announce(_ mile: Int) {
        guard activeUtterance == nil else { return }
        let line = AVSpeechUtterance(string: mile == 1 ? "One mile." : "\(mile) miles.")
        line.voice = AVSpeechSynthesisVoice(language: "en-US")
        do {
            try session.setCategory(.playback, mode: .default, options: [.duckOthers])
            try session.setActive(true)
            activeUtterance = line
            speech.speak(line)
            // A missing completion callback must not leave the runner's music quiet indefinitely.
            DispatchQueue.main.asyncAfter(deadline: .now() + 8) { [weak self] in
                guard let self, self.activeUtterance === line else { return }
                self.speech.stopSpeaking(at: .immediate)
                self.release(line)
                self.onFailure?()
            }
        } catch {
            onFailure?()
        }
    }

    func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didFinish utterance: AVSpeechUtterance) {
        DispatchQueue.main.async { self.release(utterance) }
    }

    func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didCancel utterance: AVSpeechUtterance) {
        DispatchQueue.main.async { self.release(utterance) }
    }

    @objc private func interrupted(_ notification: Notification) {
        guard let type = notification.userInfo?[AVAudioSessionInterruptionTypeKey] as? UInt,
              type == AVAudioSession.InterruptionType.began.rawValue else { return }
        DispatchQueue.main.async {
            guard let line = self.activeUtterance else { return }
            self.speech.stopSpeaking(at: .immediate)
            self.release(line)
        }
    }

    private func release(_ utterance: AVSpeechUtterance) {
        guard activeUtterance === utterance else { return }
        activeUtterance = nil
        do {
            try session.setActive(false, options: .notifyOthersOnDeactivation)
        } catch {
            onFailure?()
        }
    }
}
