#if RUNSTATE_FIELD_TOOLS
import AVFAudio
import XCTest
@testable import RunState

final class FieldSamplePlaybackTests: XCTestCase {
    private enum Event: Equatable {
        case clip(FieldSampleSound, Int, Int?)
        case voice(String)
    }

    @MainActor private final class Output: FieldSampleOutput {
        var events: [Event] = []
        var opened = false
        var interrupted: (() -> Void)?
        var block: Event?
        var arrived: XCTestExpectation?
        var closed: XCTestExpectation?
        var waiter: CheckedContinuation<Void, Error>?

        func open(interrupted: @escaping () -> Void) throws {
            opened = true
            self.interrupted = interrupted
        }
        func play(_ sound: FieldSampleSound, from milliseconds: Int, for duration: Int?) async throws {
            try await record(.clip(sound, milliseconds, duration))
        }
        func speak(_ text: String) async throws { try await record(.voice(text)) }
        private func record(_ event: Event) async throws {
            events.append(event)
            if event == block {
                try await withCheckedThrowingContinuation { (next: CheckedContinuation<Void, Error>) in
                    waiter = next
                    arrived?.fulfill()
                }
            }
        }
        func close() {
            guard opened else { return }
            opened = false
            interrupted = nil
            let waiting = waiter
            waiter = nil
            waiting?.resume(throwing: CancellationError())
            closed?.fulfill()
        }
    }

    @MainActor
    func testExcerptStartsAtChosenCueAndReturnsToExactOriginalPosition() async {
        let output = Output()
        let done = expectation(description: "Original playback returned")
        output.closed = done
        let excerptEnd = ContinuousClock().now
        var deadline: ContinuousClock.Instant?
        let player = FieldSamplePlayback(output: output, now: { excerptEnd }, waitUntil: { deadline = $0 })
        let text = "A quieter mile.\nKeep that rhythm."
        player.start(text: text, startMilliseconds: 4_000, durationMilliseconds: 4_000, originallyPlaying: true)
        await fulfillment(of: [done], timeout: 3)
        XCTAssertEqual(output.events, [.clip(.original, 1_000, 5_000), .voice(text),
                                      .clip(.excerpt, 4_000, 4_000), .clip(.chime, 0, nil),
                                      .clip(.original, 6_000, 3_000)])
        XCTAssertEqual(deadline, excerptEnd.advanced(by: .milliseconds(1_200)))
        XCTAssertEqual(player.phase, .finished)
    }

    @MainActor
    func testOriginalPausedStaysPausedAndRestoresAfterTheChime() async {
        let output = Output()
        let done = expectation(description: "Paused sample completed")
        output.closed = done
        var waits = 0
        let player = FieldSamplePlayback(output: output, waitUntil: { _ in waits += 1 })
        player.start(text: "Same reflection", startMilliseconds: 1_000, durationMilliseconds: 2_000,
                     originallyPlaying: false)
        await fulfillment(of: [done], timeout: 3)
        XCTAssertEqual(output.events, [.voice("Same reflection"), .clip(.excerpt, 1_000, 2_000), .clip(.chime, 0, nil)])
        XCTAssertEqual(waits, 1) // Silent five-second lead-in only.
        XCTAssertEqual(player.phase, .finished)
    }

    @MainActor
    func testStoppingOrLosingHeadphonesNeverPlaysTheChimeOrResumesMusic() async {
        for interrupt in [false, true] {
            let output = Output()
            let reached = expectation(description: "Playing the excerpt")
            output.arrived = reached
            output.block = .clip(.excerpt, 4_000, 4_000)
            let player = FieldSamplePlayback(output: output, waitUntil: { _ in })
            player.start(text: "Run sample", startMilliseconds: 4_000, durationMilliseconds: 4_000,
                         originallyPlaying: true)
            await fulfillment(of: [reached], timeout: 3)
            if interrupt { output.interrupted?() } else { player.stop() }
            await Task.yield()
            XCTAssertFalse(player.isPlaying)
            XCTAssertFalse(output.opened)
            XCTAssertEqual(output.events.last, output.block)
            if interrupt {
                guard case .failed = player.phase else { return XCTFail("Missing interruption status") }
            } else { XCTAssertEqual(player.phase, .idle) }
        }
    }

    @MainActor
    func testInvalidCueDoesNotTakeTheAudioSession() {
        let output = Output()
        let player = FieldSamplePlayback(output: output, waitUntil: { _ in })
        player.start(text: "Invalid sample", startMilliseconds: Int.max, durationMilliseconds: 4_000,
                     originallyPlaying: true)
        XCTAssertFalse(output.opened)
        XCTAssertFalse(player.isPlaying)
        guard case .failed = player.phase else { return XCTFail("Missing cue error") }
    }

    @MainActor
    func testNativeOutputPlaysBoundedAudioAndAndroidChimeFromTheFieldBundle() async throws {
        let audio = FieldSampleAudioOutput()
        defer { audio.close() }
        try audio.open { XCTFail("Unexpected system interruption") }
        try await audio.play(.original, from: 6_000, for: 120)
        try await audio.play(.excerpt, from: 4_000, for: 120)
        try await audio.play(.chime, from: 0, for: nil)
    }
}
#endif
