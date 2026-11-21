import AVFAudio
import XCTest
@testable import RunState

final class ReflectionVoicePlayerTests: XCTestCase {
    private func tone() throws -> Data {
        let file = try XCTUnwrap(Bundle(for: ReflectionVoicePlayerTests.self)
            .url(forResource: "reflection-test-tone", withExtension: "mp3"))
        return try Data(contentsOf: file)
    }

    @MainActor
    func testHostedAudioDecodesAndPlaysToCompletion() async throws {
        let sample = try tone()
        let decoder = try AVAudioPlayer(data: sample)
        XCTAssertGreaterThan(decoder.duration, 0.1)
        XCTAssertLessThan(decoder.duration, 1.0)
        let player = ReflectionVoicePlayer()
        defer { player.stop() }
        let end = expectation(description: "The synthetic MP3 finishes playing")
        var results: [ReflectionVoiceResult] = []

        try player.play(audio: sample) {
            results.append($0)
            end.fulfill()
        }
        await fulfillment(of: [end], timeout: 10)
        player.stop()
        XCTAssertEqual(results, [.finished])
    }

    @MainActor
    func testHeadphoneRemovalAndInterruptionsStopEachPlaybackOnce() async throws {
        let sample = try tone()
        let signals: [(Notification.Name, [String: UInt])] = [
            (AVAudioSession.routeChangeNotification,
             [AVAudioSessionRouteChangeReasonKey: AVAudioSession.RouteChangeReason.oldDeviceUnavailable.rawValue]),
            (AVAudioSession.interruptionNotification,
             [AVAudioSessionInterruptionTypeKey: AVAudioSession.InterruptionType.began.rawValue])
        ]
        let center = NotificationCenter()
        let player = ReflectionVoicePlayer(notifications: center)
        defer { player.stop() }

        for (name, info) in signals {
            let end = expectation(description: "System event stops the reflection")
            var results: [ReflectionVoiceResult] = []
            try player.play(audio: sample) {
                results.append($0)
                end.fulfill()
            }
            for _ in 0..<2 { center.post(name: name, object: AVAudioSession.sharedInstance(), userInfo: info) }
            await fulfillment(of: [end], timeout: 5)
            player.stop()
            XCTAssertEqual(results, [.interrupted])
        }
    }

    @MainActor
    func testQueuedInterruptionFromStoppedPlaybackCannotEndItsReplacement() async throws {
        let center = NotificationCenter()
        let player = ReflectionVoicePlayer(notifications: center)
        defer { player.stop() }
        let sample = try tone()
        var oldResults: [ReflectionVoiceResult] = []
        try player.play(audio: sample) { oldResults.append($0) }
        center.post(name: AVAudioSession.interruptionNotification, object: AVAudioSession.sharedInstance(),
                    userInfo: [AVAudioSessionInterruptionTypeKey: AVAudioSession.InterruptionType.began.rawValue])
        player.stop()
        let end = expectation(description: "The replacement finishes")
        var newResults: [ReflectionVoiceResult] = []
        try player.play(audio: sample) {
            newResults.append($0)
            end.fulfill()
        }
        await fulfillment(of: [end], timeout: 10)
        XCTAssertTrue(oldResults.isEmpty)
        XCTAssertEqual(newResults, [.finished])
    }

    @MainActor
    func testEmptyOrUnreadableInputsFailBeforeStartingPlayback() throws {
        let player = ReflectionVoicePlayer()
        let unexpected: (ReflectionVoiceResult) -> Void = { _ in XCTFail("An invalid input started playback") }
        XCTAssertThrowsError(try player.play(audio: Data(), onEnd: unexpected))
        XCTAssertThrowsError(try player.play(audio: Data("not an audio file".utf8), onEnd: unexpected))
        XCTAssertThrowsError(try player.speak(text: " \n ", onEnd: unexpected))
        player.stop()
    }
}
