#if RUNSTATE_FIELD_TOOLS
import XCTest
@testable import RunState

final class FieldRecognitionTests: XCTestCase {
    @MainActor private final class Capture: FieldSongRecognizing {
        let started: XCTestExpectation
        var stopped: XCTestExpectation?
        var stops = 0
        var onReady: (@MainActor () -> Void)?
        var onMatch: (@MainActor (String) -> Void)?
        var waiting: CheckedContinuation<Void, Error>?

        init(started: XCTestExpectation) { self.started = started }

        func listen(onReady: @escaping @MainActor () -> Void,
                    onMatch: @escaping @MainActor (String) -> Void) async throws {
            self.onReady = onReady
            self.onMatch = onMatch
            try await withCheckedThrowingContinuation { next in
                waiting = next
                started.fulfill()
            }
        }

        func stop() {
            stops += 1
            waiting?.resume(throwing: CancellationError())
            waiting = nil
            stopped?.fulfill()
        }
    }

    @MainActor func testOnlyOneMatchPerTrackAndDeliveryStateIsKept() async {
        let start = expectation(description: "Microphone setup started")
        let source = Capture(started: start)
        let test = FieldRecognitionTest(makeRecognizer: { source })
        test.start()
        await fulfillment(of: [start], timeout: 3)
        XCTAssertEqual(test.phase, .preparing)
        source.onMatch?("RunState Original")
        XCTAssertTrue(test.matches.isEmpty)
        source.onReady?()

        source.onMatch?("RunState Original")
        source.onMatch?("RunState Original")
        test.sceneChanged(isBackground: true)
        source.onMatch?("RunState Original")
        source.onMatch?("RunState Excerpt")
        test.sceneChanged(isBackground: false)
        source.onMatch?("RunState Excerpt")
        XCTAssertEqual(test.matches.map(\.title),
                       ["RunState Original", "RunState Original", "RunState Excerpt", "RunState Excerpt"])
        XCTAssertEqual(test.matches.map(\.receivedInBackground), [false, true, true, false])
        XCTAssertEqual(test.phase, .listening)
        test.stop()
        XCTAssertEqual(test.phase, .stopped)
        XCTAssertEqual(source.stops, 1)
    }

    @MainActor func testStopThenStartIgnoresLateCallbacksFromFirstAttempt() async {
        let first = Capture(started: expectation(description: "First start"))
        let second = Capture(started: expectation(description: "Second start"))
        var attempts = [first, second]
        let test = FieldRecognitionTest(makeRecognizer: { attempts.removeFirst() })
        test.start()
        test.start()
        await fulfillment(of: [first.started], timeout: 3)
        first.onReady?()
        first.onMatch?("RunState Original")
        test.stop()
        test.stop()
        test.start()
        await fulfillment(of: [second.started], timeout: 3)
        first.onReady?()
        first.onMatch?("RunState Original")
        XCTAssertEqual(test.phase, .preparing)
        XCTAssertTrue(test.matches.isEmpty)
        second.onReady?()
        second.onMatch?("RunState Excerpt")
        XCTAssertEqual(test.matches.map(\.title), ["RunState Excerpt"])
        XCTAssertEqual(first.stops, 1)
        test.stop()
    }

    @MainActor func testOverdueCallbackAfterSuspensionCannotAddEvidence() async {
        let source = Capture(started: expectation(description: "Listening"))
        var instant = ContinuousClock().now
        let test = FieldRecognitionTest(makeRecognizer: { source }, now: { instant })
        test.start()
        await fulfillment(of: [source.started], timeout: 3)
        source.onReady?()
        test.sceneChanged(isBackground: true)
        instant = instant.advanced(by: .seconds(1_201))
        source.onMatch?("RunState Original")
        XCTAssertFalse(test.isActive)
        XCTAssertTrue(test.matches.isEmpty)
        XCTAssertEqual(test.phase, .timedOut)
        XCTAssertEqual(source.stops, 1)
    }

    @MainActor func testRecognitionStopsAtDeadlineEvenWithoutASceneChangeOrMatch() async {
        let source = Capture(started: expectation(description: "Started"))
        let stopped = expectation(description: "Stopped on timer")
        source.stopped = stopped
        let test = FieldRecognitionTest(makeRecognizer: { source }, limit: .milliseconds(200))
        test.start()
        await fulfillment(of: [source.started, stopped], timeout: 3)
        XCTAssertEqual(test.phase, .timedOut)
        XCTAssertFalse(test.isActive)
    }

    @MainActor func testFreeCatalogFailureAllowsRetryAndShowsAReason() async {
        let source = Capture(started: expectation(description: "Started"))
        let stopped = expectation(description: "Failure cleaned up")
        source.stopped = stopped
        let test = FieldRecognitionTest(makeRecognizer: { source })
        test.start()
        await fulfillment(of: [source.started], timeout: 3)
        let waiting = source.waiting
        source.waiting = nil
        waiting?.resume(throwing: NSError(domain: "Test", code: 1,
            userInfo: [NSLocalizedDescriptionKey: "Microphone permission was denied."]))
        await fulfillment(of: [stopped], timeout: 3)
        XCTAssertEqual(test.phase, .failed("Microphone permission was denied."))
        XCTAssertFalse(test.isActive)
    }

    @MainActor func testUnavailableRecognitionNeverRequestsAudio() {
        let test = FieldRecognitionTest(makeRecognizer: nil)
        test.start()
        XCTAssertFalse(test.available)
        XCTAssertFalse(test.isActive)
        XCTAssertEqual(test.phase, .idle)
    }
}
#endif
