import Foundation
import XCTest
@testable import RunState

final class RunSpokenReflectionTests: XCTestCase {
    private static let text = "Your steady finish carried you home."
    private static let code = String(repeating: "a", count: 43)

    private final class Vault: ReflectionCredentialVault {
        var value: ReflectionCredentials?
        func read() -> ReflectionCredentials? { value }
        func write(_ value: ReflectionCredentials) { self.value = value }
        func remove() { value = nil }
    }

    @MainActor private final class Player: ReflectionVoicePlaying {
        var recordings: [Data] = []
        var readings: [String] = []
        var ended: ((ReflectionVoiceResult) -> Void)?
        var stops = 0
        func play(audio: Data, onEnd: @escaping (ReflectionVoiceResult) -> Void) throws {
            recordings.append(audio)
            ended = onEnd
        }
        func speak(text: String, onEnd: @escaping (ReflectionVoiceResult) -> Void) throws {
            readings.append(text)
            ended = onEnd
        }
        func stop() {
            stops += 1
            ended = nil
        }
    }

    private actor Service: ReflectionSpeechServing {
        enum WaitPoint: Equatable { case beforeSend, afterSend }
        let pause: WaitPoint?
        let reached: XCTestExpectation?
        let failure: ReflectionSpeechServiceError?
        var sent: [String] = []
        var ids: [UUID] = []
        private var continuation: CheckedContinuation<Void, Never>?
        init(pause: WaitPoint? = nil, reached: XCTestExpectation? = nil, failure: ReflectionSpeechServiceError? = nil) {
            self.pause = pause
            self.reached = reached
            self.failure = failure
        }
        func synthesize(text: String, runID: UUID, accessCode: String,
                        willSend: @escaping @MainActor () throws -> Void) async throws -> Data {
            if pause == .beforeSend { await waitHere() }
            try await willSend()
            sent.append(text)
            ids.append(runID)
            if pause == .afterSend { await waitHere() }
            if let failure { throw failure }
            return Data([7, 8, 9])
        }
        private func waitHere() async {
            await withCheckedContinuation { continuation in
                self.continuation = continuation
                reached?.fulfill()
            }
        }
        func release() {
            continuation?.resume()
            continuation = nil
        }
    }

    @MainActor private final class Fixture {
        let folder = FileManager.default.temporaryDirectory.appending(path: UUID().uuidString)
        let preferences = UserDefaults(suiteName: UUID().uuidString)!
        let player = Player()
        let connection: ReflectionConnection
        let service: Service
        let id: UUID
        let tracker: RunTracker
        init(service: Service = Service(), sample: Bool = false, legacy: Bool = false) throws {
            self.service = service
            connection = ReflectionConnection(preferences: preferences, vault: Vault())
            try connection.save(code: RunSpokenReflectionTests.code)
            var journal = RunJournal()
            journal.beginRun(at: Date())
            id = try XCTUnwrap(journal.active?.id)
            journal.finish(at: Date())
            let credentialID = try connection.credentials().id
            let answer = SavedWrittenReflection(text: RunSpokenReflectionTests.text, isTestData: sample,
                                                 credentialID: legacy ? nil : credentialID)
            journal.history[0].writtenReflection = .init(saved: answer)
            let file = folder.appending(path: "journal.json")
            try journal.save(to: file)
            tracker = RunTracker(preferences: preferences, journalURL: file, reflectionConnection: connection,
                                 speechService: service, reflectionPlayer: player)
        }
        var state: SpokenReflectionState? { tracker.spokenReflectionState(for: tracker.journal.history[0]) }
        func relaunch() -> RunTracker {
            RunTracker(preferences: preferences, journalURL: folder.appending(path: "journal.json"),
                       reflectionConnection: connection, speechService: service, reflectionPlayer: player)
        }
        func cleanup() { try? FileManager.default.removeItem(at: folder) }
    }

    @MainActor
    func testExactSavedTextAndRunIDAreSentOnceWithOfflineReplayAndInterruption() async throws {
        let f = try Fixture(legacy: true)
        defer { f.cleanup() }
        try f.tracker.saveCheckIn(for: f.id, energy: .poweredUp, effort: 8)
        XCTAssertEqual(f.state?.hosted, .generate)
        await f.tracker.playSpokenReflection(for: f.id)
        XCTAssertEqual(f.state?.playing, .recorded)
        f.player.ended?(.interrupted)
        XCTAssertNil(f.state?.playing)
        XCTAssertNotNil(f.state?.message)
        try f.connection.setEnabled(false)
        await f.tracker.playSpokenReflection(for: f.id)
        XCTAssertEqual(f.player.recordings, [Data([7, 8, 9]), Data([7, 8, 9])])
        let sent = await f.service.sent
        let ids = await f.service.ids
        XCTAssertEqual(sent, [Self.text])
        XCTAssertEqual(ids, [f.id])
        let disk = try RunJournal.load(from: f.folder.appending(path: "journal.json"))
        XCTAssertEqual(disk.history[0].spokenRequest?.outcome, .received)
        let reopened = f.relaunch()
        XCTAssertNil(reopened.spokenWork.audio)
        XCTAssertEqual(reopened.spokenReflectionState(for: reopened.journal.history[0])?.hosted, .unavailable)
        reopened.speakOnDevice(for: f.id)
        XCTAssertEqual(f.player.readings, [Self.text])
    }

    @MainActor
    func testAmbiguousRequestSurvivesRelaunchAndOnlyDeviceVoiceCanPlay() async throws {
        let f = try Fixture(service: Service(failure: .unavailable))
        defer { f.cleanup() }
        await f.tracker.playSpokenReflection(for: f.id)
        let reopened = f.relaunch()
        XCTAssertEqual(reopened.journal.history[0].spokenRequest?.outcome, .sent)
        XCTAssertEqual(reopened.spokenReflectionState(for: reopened.journal.history[0])?.hosted, .unavailable)
        await reopened.playSpokenReflection(for: f.id)
        reopened.speakOnDevice(for: f.id)
        let sent = await f.service.sent
        XCTAssertEqual(sent, [Self.text])
        XCTAssertEqual(f.player.readings, [Self.text])
    }

    @MainActor
    func testConfirmedRejectionPermitsExplicitRetryWithoutChangingText() async throws {
        let f = try Fixture(service: Service(failure: .accessDenied))
        defer { f.cleanup() }
        await f.tracker.playSpokenReflection(for: f.id)
        XCTAssertEqual(f.state?.hosted, .generate)
        XCTAssertNil(f.tracker.journal.history[0].spokenRequest)
        XCTAssertTrue(f.player.recordings.isEmpty)
    }

    @MainActor
    func testUnwritableJournalPreventsTheVoiceCall() async throws {
        let f = try Fixture()
        defer { f.cleanup() }
        let file = f.folder.appending(path: "journal.json")
        try FileManager.default.moveItem(at: file, to: f.folder.appending(path: "original"))
        try FileManager.default.createDirectory(at: file, withIntermediateDirectories: false)
        await f.tracker.playSpokenReflection(for: f.id)
        let sent = await f.service.sent
        XCTAssertTrue(sent.isEmpty)
        XCTAssertEqual(f.state?.hosted, .generate)
        XCTAssertNil(f.tracker.journal.history[0].spokenRequest)
        f.tracker.speakOnDevice(for: f.id)
        XCTAssertEqual(f.player.readings, [Self.text])
    }

    @MainActor
    func testSampleNeverLeavesPhoneAndCredentialReplacementCannotClaimWrittenAnswer() async throws {
        let sample = try Fixture(sample: true)
        defer { sample.cleanup() }
        XCTAssertEqual(sample.state?.hosted, .unavailable)
        await sample.tracker.playSpokenReflection(for: sample.id)
        sample.tracker.speakOnDevice(for: sample.id)
        let sent = await sample.service.sent
        XCTAssertTrue(sent.isEmpty)
        XCTAssertEqual(sample.player.readings, [Self.text])

        let f = try Fixture()
        defer { f.cleanup() }
        await f.tracker.playSpokenReflection(for: f.id)
        try f.connection.save(code: String(repeating: "b", count: 43))
        f.tracker.spokenConnectionChanged(to: f.connection.revision)
        XCTAssertNil(f.tracker.spokenWork.audio)
        XCTAssertNil(f.state?.playing)
        XCTAssertEqual(f.state?.hosted, .unavailable)
        XCTAssertEqual(f.player.stops, 1)
    }

    @MainActor
    func testCancelBeforeSendLeavesNoMarkerAndAllowsAnImmediateDeviceRead() async throws {
        let gate = expectation(description: "health check pending")
        let service = Service(pause: .beforeSend, reached: gate)
        let f = try Fixture(service: service)
        defer { f.cleanup() }
        let job = Task { await f.tracker.playSpokenReflection(for: f.id) }
        await fulfillment(of: [gate], timeout: 3)
        XCTAssertEqual(f.state?.hosted, .loading)
        f.tracker.stopSpokenReflection(for: f.id)
        XCTAssertEqual(f.state?.hosted, .unavailable)
        f.tracker.speakOnDevice(for: f.id)
        XCTAssertEqual(f.state?.playing, .device)
        await service.release()
        await job.value
        let sent = await service.sent
        XCTAssertTrue(sent.isEmpty)
        XCTAssertNil(f.tracker.journal.history[0].spokenRequest)
        XCTAssertEqual(f.state?.hosted, .generate)
        XCTAssertTrue(f.player.recordings.isEmpty)
    }

    @MainActor
    func testCancelAfterDispatchFinishesSilentlyAndDoesNotReplaceDeviceVoice() async throws {
        let gate = expectation(description: "request delivered")
        let service = Service(pause: .afterSend, reached: gate)
        let f = try Fixture(service: service)
        defer { f.cleanup() }
        let job = Task { await f.tracker.playSpokenReflection(for: f.id) }
        await fulfillment(of: [gate], timeout: 3)
        await f.tracker.playSpokenReflection(for: f.id)
        f.tracker.stopSpokenReflection(for: f.id)
        XCTAssertEqual(f.state?.hosted, .unavailable)
        f.tracker.speakOnDevice(for: f.id)
        await service.release()
        await job.value
        XCTAssertEqual(f.state?.playing, .device)
        XCTAssertEqual(f.state?.hosted, .replay)
        XCTAssertTrue(f.player.recordings.isEmpty)
        let sent = await service.sent
        XCTAssertEqual(sent, [Self.text])
    }

    @MainActor
    func testDeletionPreventsLateAudioOrJournalResurrection() async throws {
        let gate = expectation(description: "request delivered")
        let service = Service(pause: .afterSend, reached: gate)
        let f = try Fixture(service: service)
        defer { f.cleanup() }
        let job = Task { await f.tracker.playSpokenReflection(for: f.id) }
        await fulfillment(of: [gate], timeout: 3)
        try f.tracker.deleteSavedRun(f.id)
        await service.release()
        await job.value
        XCTAssertNil(f.tracker.spokenWork.audio)
        XCTAssertTrue(f.relaunch().journal.history.isEmpty)
        XCTAssertTrue(f.player.recordings.isEmpty)
    }
}
