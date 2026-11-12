import Combine
import Foundation

/// A countdown belongs to one Start tap. Only its current owner can start a recording.
@MainActor
final class RunCountIn: ObservableObject {
    @Published private(set) var seconds: Int?
    @Published private(set) var cadenceActive = false
    private let speak: (String, @escaping () -> Void) -> Void
    private let silence: () -> Void
    private var timer: Task<Void, Never>?
    private var token: UUID?
    private var start: (() -> Void)?

    init(speak: @escaping (String, @escaping () -> Void) -> Void, silence: @escaping () -> Void) {
        self.speak = speak
        self.silence = silence
    }

    func begin(start: @escaping () -> Void) {
        guard token == nil else { return }
        let id = UUID()
        token = id
        self.start = start
        seconds = 10
        timer = Task { [weak self] in
            do { try await Task.sleep(for: .seconds(5)) } catch { return }
            self?.beginCadence(id)
        }
        speak("Starting in ten.") { [weak self] in self?.beginCadence(id) }
    }

    private func beginCadence(_ id: UUID) {
        guard token == id, !cadenceActive else { return }
        timer?.cancel()
        cadenceActive = true
        silence()
        let clock = ContinuousClock()
        let beginning = clock.now
        timer = Task { [weak self] in
            var elapsed = 0
            while elapsed < 10 {
                do { try await clock.sleep(until: beginning + .seconds(elapsed + 1)) }
                catch { return }
                guard let self, self.token == id else { return }
                // After a scheduling delay, skip stale cues and keep the original finish time.
                let remaining = Self.secondsLeft(since: beginning, now: clock.now)
                if remaining == 0 {
                    self.commitStart(interruptCue: false)
                    return
                }
                elapsed = 10 - remaining
                self.seconds = remaining
                if remaining <= 3 { self.speak(String(remaining)) {} }
            }
        }
    }

    static func secondsLeft(since beginning: ContinuousClock.Instant, now: ContinuousClock.Instant) -> Int {
        let elapsed = beginning.duration(to: now).components.seconds
        return 10 - Int(min(10, max(0, elapsed)))
    }

    func startNow() {
        commitStart(interruptCue: true)
    }

    private func commitStart(interruptCue: Bool) {
        guard let start else { return }
        retire()
        if interruptCue { silence() }
        start()
    }

    func cancel() {
        guard token != nil else { return }
        retire()
        silence()
    }

    private func retire() {
        token = nil
        start = nil
        timer?.cancel()
        timer = nil
        seconds = nil
        cadenceActive = false
    }
}
