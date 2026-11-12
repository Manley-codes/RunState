import Foundation
import XCTest
@testable import RunState

final class RunWrittenReflectionTests: XCTestCase {
    private static let answers = WrittenReflectionAnswers(spent: "Low ending.", feelingGood: "Steady ending.",
                                                          poweredUp: "High ending.", noSelection: "Open ending.",
                                                          isTestData: true)

    private actor Service: WrittenReflectionServing {
        struct Call {
            let request: WrittenReflectionRequest
            let replayOnly: Bool
        }
        var calls: [Call] = []
        private var failures: [ReflectionServiceError]
        private let answers: WrittenReflectionAnswers
        private let started: XCTestExpectation?
        private var continuation: CheckedContinuation<Void, Never>?

        init(failures: [ReflectionServiceError] = [], started: XCTestExpectation? = nil,
             answers: WrittenReflectionAnswers = RunWrittenReflectionTests.answers) {
            self.failures = failures
            self.started = started
            self.answers = answers
        }
        func generate(_ request: WrittenReflectionRequest, accessCode: String, replayOnly: Bool) async throws -> WrittenReflectionAnswers {
            calls.append(.init(request: request, replayOnly: replayOnly))
            if let started, calls.count == 1 {
                await withCheckedContinuation { continuation in
                    self.continuation = continuation
                    started.fulfill()
                }
            }
            if !failures.isEmpty { throw failures.removeFirst() }
            return answers
        }
        func release() {
            continuation?.resume()
            continuation = nil
        }
    }

    private final class Vault: ReflectionCredentialVault {
        var stored: ReflectionCredentials?
        func read() -> ReflectionCredentials? { stored }
        func write(_ value: ReflectionCredentials) { stored = value }
        func remove() { stored = nil }
    }

    @MainActor
    private final class Fixture {
        let directory: URL
        let file: URL
        let preferences: UserDefaults
        let suite: String
        let connection: ReflectionConnection
        let runID: UUID
        let service: Service
        let tracker: RunTracker
        let originalCode = String(repeating: "a", count: 43)
        let otherCode = String(repeating: "b", count: 43)

        init(service: Service, legacy: Bool = false) throws {
            directory = FileManager.default.temporaryDirectory.appending(path: UUID().uuidString)
            file = directory.appending(path: "runs.json")
            let suiteName = UUID().uuidString
            suite = suiteName
            preferences = try XCTUnwrap(UserDefaults(suiteName: suiteName))
            connection = ReflectionConnection(preferences: preferences, vault: Vault())
            self.service = service
            var journal = RunJournal()
            let start = Date(timeIntervalSince1970: 1_750_000_000)
            journal.beginRun(at: start, preRunEnergy: "Low")
            if legacy { journal.active?.startTimezoneID = nil }
            runID = try XCTUnwrap(journal.active?.id)
            journal.active?.meters = 600
            journal.active?.activeSeconds = 300
            journal.finish(at: start.addingTimeInterval(300))
            try journal.save(to: file)
            tracker = RunTracker(preferences: preferences, journalURL: file,
                                 reflectionConnection: connection, reflectionService: service)
        }
        func relaunch() -> RunTracker {
            RunTracker(preferences: preferences, journalURL: file, reflectionConnection: connection, reflectionService: service)
        }
        func cleanup() {
            preferences.removePersistentDomain(forName: suite)
            try? FileManager.default.removeItem(at: directory)
        }
    }

    @MainActor
    func testAccessIsExplicitAndSavedOriginalEnergyTextSurvivesEditsDisableAndRelaunch() async throws {
        let service = Service()
        let f = try Fixture(service: service)
        defer { f.cleanup() }
        let tracker = f.tracker
        XCTAssertEqual(tracker.writtenReflectionState(for: tracker.journal.history[0]), .notConnected)
        await tracker.requestWrittenReflection(for: f.runID)
        let beforeConsent = await service.calls
        XCTAssertTrue(beforeConsent.isEmpty)
        try f.connection.save(code: f.originalCode)
        try tracker.saveCheckIn(for: f.runID, energy: .spent, effort: 3)
        try tracker.saveCheckIn(for: f.runID, energy: .poweredUp, effort: 6)
        await tracker.requestWrittenReflection(for: f.runID)
        try tracker.saveCheckIn(for: f.runID, energy: nil, effort: nil)
        try f.connection.setEnabled(false)
        let reopened = f.relaunch()
        XCTAssertEqual(reopened.writtenReflectionState(for: reopened.journal.history[0]), .saved(text: "Low ending.", isTestData: true))
        XCTAssertNil(reopened.journal.history[0].writtenReflection?.saved?.credentialID)
        XCTAssertNil(reopened.journal.history[0].writtenReflection?.pending)
        await reopened.requestWrittenReflection(for: f.runID)
        let calls = await service.calls
        XCTAssertEqual(calls.map(\.replayOnly), [false])
        let disk = try String(contentsOf: f.file, encoding: .utf8)
        XCTAssertFalse(disk.contains(f.originalCode))
        XCTAssertFalse(disk.contains("High ending."))
    }

    @MainActor
    func testRealSavedAnswerKeepsOriginalCredentialIDWithoutStoringCodeOrInventingLegacyBinding() async throws {
        let oldJSON = Data(#"{"text":"Older reflection","isTestData":false}"#.utf8)
        let old = try JSONDecoder().decode(SavedWrittenReflection.self, from: oldJSON)
        XCTAssertNil(old.credentialID)
        let answers = WrittenReflectionAnswers(spent: "A", feelingGood: "B", poweredUp: "C",
                                              noSelection: "  Original words.  ", isTestData: false)
        let f = try Fixture(service: Service(answers: answers))
        defer { f.cleanup() }
        try f.connection.save(code: f.originalCode)
        let originalID = try f.connection.credentials().id
        await f.tracker.requestWrittenReflection(for: f.runID)
        try f.connection.save(code: f.otherCode)
        let saved = try XCTUnwrap(f.relaunch().journal.history[0].writtenReflection?.saved)
        XCTAssertEqual(saved.text, "  Original words.  ")
        XCTAssertEqual(saved.credentialID, originalID)
        XCTAssertNotEqual(saved.credentialID, try f.connection.credentials().id)
        let journal = try String(contentsOf: f.file, encoding: .utf8)
        XCTAssertFalse(journal.contains(f.originalCode))
        XCTAssertFalse(journal.contains(f.otherCode))
    }

    @MainActor
    func testLegacyZoneNeedsExplicitConfirmationAndSurvivesRelaunchWithoutNetwork() async throws {
        let service = Service()
        let f = try Fixture(service: service, legacy: true)
        defer { f.cleanup() }
        try f.connection.save(code: f.originalCode)
        XCTAssertEqual(f.tracker.writtenReflectionState(for: f.tracker.journal.history[0]), .needsStartTimeZone)
        await f.tracker.requestWrittenReflection(for: f.runID)
        XCTAssertThrowsError(try f.tracker.confirmStartTimeZone(for: f.runID, identifier: "not a time zone"))
        XCTAssertNil(f.tracker.journal.history[0].startTimezoneID)
        try f.tracker.confirmStartTimeZone(for: f.runID, identifier: "America/Chicago")
        try f.tracker.confirmStartTimeZone(for: f.runID, identifier: "America/Chicago")
        let reopened = f.relaunch()
        XCTAssertEqual(reopened.journal.history[0].startTimezoneID, "America/Chicago")
        XCTAssertEqual(reopened.writtenReflectionState(for: reopened.journal.history[0]), .available)
        XCTAssertThrowsError(try reopened.confirmStartTimeZone(for: f.runID, identifier: "UTC"))
        XCTAssertThrowsError(try reopened.confirmStartTimeZone(for: UUID(), identifier: "UTC"))
        let calls = await service.calls
        XCTAssertTrue(calls.isEmpty)
        XCTAssertNil(f.tracker.journal.history[0].writtenReflection)
    }

    @MainActor
    func testLostReplyAndRelaunchUseOnlyFrozenReplayWithOriginalCredentialAndNoSelection() async throws {
        let service = Service(failures: [.deliveryFailed(nil)])
        let f = try Fixture(service: service)
        defer { f.cleanup() }
        try f.connection.save(code: f.originalCode)
        try f.tracker.saveCheckIn(for: f.runID, energy: .notSure, effort: nil)
        await f.tracker.requestWrittenReflection(for: f.runID)
        let tracker = f.relaunch()
        let pending = try XCTUnwrap(tracker.journal.history[0].writtenReflection?.pending)
        XCTAssertTrue(pending.replayOnly)
        try tracker.saveCheckIn(for: f.runID, energy: .poweredUp, effort: 4)
        try f.connection.save(code: f.otherCode)
        await tracker.requestWrittenReflection(for: f.runID)
        let rejected = await service.calls
        XCTAssertEqual(rejected.count, 1)
        try f.connection.save(code: f.originalCode)
        await tracker.requestWrittenReflection(for: f.runID)
        let calls = await service.calls
        XCTAssertEqual(calls.map(\.replayOnly), [false, true])
        XCTAssertEqual(calls[0].request, calls[1].request)
        XCTAssertEqual(tracker.writtenReflectionState(for: tracker.journal.history[0]), .saved(text: "Open ending.", isTestData: true))
    }

    @MainActor
    func testRejectedPreflightAllowsManualGenerationAfterCorrectingTheCode() async throws {
        let service = Service(failures: [.accessDenied])
        let f = try Fixture(service: service)
        defer { f.cleanup() }
        try f.connection.save(code: f.originalCode)
        await f.tracker.requestWrittenReflection(for: f.runID)
        let previous = try XCTUnwrap(f.tracker.journal.history[0].writtenReflection?.pending)
        XCTAssertFalse(previous.replayOnly)
        try f.connection.save(code: f.otherCode)
        await f.tracker.requestWrittenReflection(for: f.runID)
        let calls = await service.calls
        XCTAssertEqual(calls.map(\.replayOnly), [false, false])
        XCTAssertEqual(calls.first?.request, calls.last?.request)
        XCTAssertEqual(f.tracker.journal.history[0].writtenReflection?.saved?.text, "Open ending.")
    }

    @MainActor
    func testUnsupportedRecoveryOrConfirmedOldMissPreservesRunAndStopsOfferingRetryAfterRelaunch() async throws {
        for (error, ended) in [(ReflectionServiceError.replayUnsupported, WrittenRecoveryEnd.unsupportedService),
                               (.replayUnavailable, .noSavedAnswer)] {
            let service = Service(failures: [.deliveryFailed(nil), error])
            let f = try Fixture(service: service)
            defer { f.cleanup() }
            try f.connection.save(code: f.originalCode)
            await f.tracker.requestWrittenReflection(for: f.runID)
            let original = try XCTUnwrap(f.tracker.journal.history[0].writtenReflection?.pending)
            if ended == .noSavedAnswer {
                var old = original
                old.firstAttemptAt = Date().addingTimeInterval(-25 * 3600)
                try f.tracker.storeWrittenReflection(.init(pending: old), for: f.runID, replacing: original)
            }
            await f.tracker.requestWrittenReflection(for: f.runID)
            try f.connection.setEnabled(false)
            let reopened = f.relaunch()
            let run = try XCTUnwrap(reopened.journal.history.first)
            XCTAssertEqual(run.id, f.runID)
            XCTAssertEqual(reopened.writtenReflectionState(for: run), .unavailable(ended.message))
            try f.connection.setEnabled(true)
            await reopened.requestWrittenReflection(for: f.runID)
            let calls = await service.calls
            XCTAssertEqual(calls.map(\.replayOnly), [false, true])
        }
    }

    @MainActor
    func testRecentMissAndOldTemporaryFailureCanStillRecoverTheOriginalAnswer() async throws {
        let service = Service(failures: [.deliveryFailed(nil), .replayUnavailable, .preflightUnavailable])
        let f = try Fixture(service: service)
        defer { f.cleanup() }
        try f.connection.save(code: f.originalCode)
        await f.tracker.requestWrittenReflection(for: f.runID)
        await f.tracker.requestWrittenReflection(for: f.runID)
        let recent = try XCTUnwrap(f.tracker.journal.history[0].writtenReflection?.pending)
        XCTAssertNotNil(recent.firstAttemptAt)
        XCTAssertNil(recent.recoveryEnded)
        var old = recent
        old.firstAttemptAt = Date().addingTimeInterval(-25 * 3600)
        try f.tracker.storeWrittenReflection(.init(pending: old), for: f.runID, replacing: recent)
        await f.tracker.requestWrittenReflection(for: f.runID)
        let reopened = f.relaunch()
        await reopened.requestWrittenReflection(for: f.runID)
        XCTAssertEqual(reopened.journal.history[0].writtenReflection?.saved?.text, "Open ending.")
        let calls = await service.calls
        XCTAssertEqual(calls.map(\.replayOnly), [false, true, true, true])
    }

    @MainActor
    func testConcurrentTapAndConnectionDisableCannotAcceptLateReply() async throws {
        let entered = expectation(description: "Initial request started")
        let service = Service(started: entered)
        let f = try Fixture(service: service)
        defer { f.cleanup() }
        try f.connection.save(code: f.originalCode)
        let first = Task { await f.tracker.requestWrittenReflection(for: f.runID) }
        await fulfillment(of: [entered], timeout: 5)
        let second = Task { await f.tracker.requestWrittenReflection(for: f.runID) }
        await Task.yield()
        try f.connection.setEnabled(false)
        await service.release()
        await first.value
        await second.value
        let calls = await service.calls
        XCTAssertEqual(calls.count, 1)
        XCTAssertNil(f.tracker.journal.history[0].writtenReflection?.saved)
        XCTAssertTrue(f.tracker.journal.history[0].writtenReflection?.pending?.replayOnly == true)
        try f.connection.setEnabled(true)
        await f.tracker.requestWrittenReflection(for: f.runID)
        let modes = await service.calls.map(\.replayOnly)
        XCTAssertEqual(modes, [false, true])
    }

    @MainActor
    func testDeletionsPreventLateResponsesFromRevivingARun() async throws {
        let entered = expectation(description: "Request cached its evidence")
        let service = Service(started: entered)
        let f = try Fixture(service: service)
        defer { f.cleanup() }
        try f.connection.save(code: f.originalCode)
        let task = Task { await f.tracker.requestWrittenReflection(for: f.runID) }
        await fulfillment(of: [entered], timeout: 5)
        try f.tracker.deleteSavedRun(f.runID)
        await service.release()
        await task.value
        XCTAssertTrue(f.relaunch().journal.history.isEmpty)
    }

    @MainActor
    func testStorageFailureBeforeDispatchPreventsTheCall() async throws {
        let service = Service()
        let f = try Fixture(service: service)
        defer { f.cleanup() }
        try f.connection.save(code: f.originalCode)
        try FileManager.default.removeItem(at: f.file)
        try FileManager.default.createDirectory(at: f.file, withIntermediateDirectories: false)
        await f.tracker.requestWrittenReflection(for: f.runID)
        let calls = await service.calls
        XCTAssertTrue(calls.isEmpty)
        XCTAssertNil(f.tracker.journal.history[0].writtenReflection)
    }

    @MainActor
    func testArrivedAnswerWithDiskFailureCanBeSavedLocallyWithoutResending() async throws {
        let entered = expectation(description: "Request saved before dispatch")
        let service = Service(started: entered)
        let f = try Fixture(service: service)
        defer { f.cleanup() }
        try f.connection.save(code: f.originalCode)
        let task = Task { await f.tracker.requestWrittenReflection(for: f.runID) }
        await fulfillment(of: [entered], timeout: 5)
        let original = f.directory.appending(path: "original")
        try FileManager.default.moveItem(at: f.file, to: original)
        try FileManager.default.createDirectory(at: f.file, withIntermediateDirectories: false)
        await service.release()
        await task.value
        XCTAssertNil(f.tracker.journal.history[0].writtenReflection?.saved)
        try FileManager.default.removeItem(at: f.file)
        try FileManager.default.moveItem(at: original, to: f.file)
        await f.tracker.requestWrittenReflection(for: f.runID)
        let calls = await service.calls
        XCTAssertEqual(calls.count, 1)
        XCTAssertEqual(f.relaunch().journal.history[0].writtenReflection?.saved?.text, "Open ending.")
    }
}
