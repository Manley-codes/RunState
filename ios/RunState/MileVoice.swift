import AVFAudio

/// Spotify owns playback. This session only lowers other audio while the local utterance is speaking.
final class RunVoice: NSObject, AVSpeechSynthesizerDelegate {
    private let speech = AVSpeechSynthesizer()
    private let session = AVAudioSession.sharedInstance()
    private var activeUtterance: AVSpeechUtterance?
    private var finished: (() -> Void)?
    private var isCountdownCue = false
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
        speak(mile == 1 ? "One mile." : "\(mile) miles.", onTimeout: onFailure)
    }

    func countdownCue(_ text: String, onFinished: @escaping () -> Void) {
        stop()
        speak(text, onFinished: onFinished)
    }

    func announceStart() {
        let go: () -> Void = { [weak self] in self?.countdownCue("Let’s go, runner.") {} }
        guard let lastDigit = activeUtterance, isCountdownCue else { go(); return }
        // GPS is already recording. Give the spoken “one” time to finish on headphones.
        finished = go
        DispatchQueue.main.asyncAfter(deadline: .now() + 2) { [weak self] in
            guard let self, self.activeUtterance === lastDigit else { return }
            self.speech.stopSpeaking(at: .immediate)
            self.release(lastDigit)
        }
    }

    func stopCountdownCue() {
        if isCountdownCue { stop() }
    }

    func stop() {
        guard let utterance = activeUtterance else { return }
        finished = nil
        speech.stopSpeaking(at: .immediate)
        release(utterance)
    }

    private func speak(_ text: String, onFinished: (() -> Void)? = nil, onTimeout: (() -> Void)? = nil) {
        let line = AVSpeechUtterance(string: text)
        line.voice = AVSpeechSynthesisVoice(language: "en-US")
        do {
            try session.setCategory(.playback, mode: .default, options: [.duckOthers])
            try session.setActive(true)
            activeUtterance = line
            isCountdownCue = onFinished != nil
            finished = onFinished
            speech.speak(line)
            // A missing completion callback must not leave the runner's music quiet indefinitely.
            DispatchQueue.main.asyncAfter(deadline: .now() + 8) { [weak self] in
                guard let self, self.activeUtterance === line else { return }
                self.speech.stopSpeaking(at: .immediate)
                self.release(line)
                onTimeout?()
            }
        } catch {
            onTimeout?()
            onFinished?()
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
            self.finished = nil
            self.speech.stopSpeaking(at: .immediate)
            self.release(line)
        }
    }

    private func release(_ utterance: AVSpeechUtterance) {
        guard activeUtterance === utterance else { return }
        activeUtterance = nil
        let failure = isCountdownCue ? nil : onFailure
        isCountdownCue = false
        let completion = finished
        finished = nil
        do {
            try session.setActive(false, options: .notifyOthersOnDeactivation)
        } catch {
            failure?()
        }
        completion?()
    }
}
