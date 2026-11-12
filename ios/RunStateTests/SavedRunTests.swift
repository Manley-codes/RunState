import Foundation
import XCTest
@testable import RunState

final class SavedRunTests: XCTestCase {
    private let start = Date(timeIntervalSince1970: 1_800_000_000)

    func testDiscardRequiresMatchingUnfinishedRunWithReliableActiveTimeUnderThirtySeconds() throws {
        var journal = RunJournal()
        try journal.addShoe(brand: "Brooks", model: "Ghost")
        let pair = try XCTUnwrap(journal.selectedShoe)
        journal.beginRun(at: start)
        journal.active?.meters = RunDistance.metersPerMile
        journal.finish(at: start.addingTimeInterval(600))
        let saved = journal.history
        journal.beginRun(at: start.addingTimeInterval(900))
        let id = try XCTUnwrap(journal.active?.id)
        for reliable in [nil, false] as [Bool?] {
            journal.active?.pausedWithCompleteTime = reliable
            XCTAssertThrowsError(try journal.discardShortPausedRun(id))
        }
        journal.active?.pausedWithCompleteTime = true
        for elapsed in [-1, 30, 90] as [TimeInterval] {
            journal.active?.activeSeconds = elapsed
            XCTAssertThrowsError(try journal.discardShortPausedRun(id))
        }
        journal.active?.activeSeconds = 29.999
        var restored = try JSONDecoder().decode(RunJournal.self, from: JSONEncoder().encode(journal))
        XCTAssertThrowsError(try restored.discardShortPausedRun(saved[0].id))
        XCTAssertEqual(restored.active?.id, id)
        try restored.discardShortPausedRun(id)
        XCTAssertNil(restored.active)
        XCTAssertEqual(restored.history, saved)
        XCTAssertEqual(restored.selectedShoe, pair)
        XCTAssertEqual(restored.miles(for: pair.id), 1, accuracy: 0.00001)
        restored.beginRun(at: start)
        XCTAssertThrowsError(try restored.discardShortPausedRun(id))
        XCTAssertNotNil(restored.active)
    }

    @MainActor
    func testFailedDiscardKeepsShortRunPausedAndAllowsRetry() throws {
        let directory = FileManager.default.temporaryDirectory.appending(path: UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: directory) }
        let file = directory.appending(path: "journal")
        var journal = RunJournal()
        journal.beginRun(at: start)
        journal.active?.pausedWithCompleteTime = true
        try journal.save(to: file)
        let id = try XCTUnwrap(journal.active?.id)
        let tracker = RunTracker(preferences: .standard, journalURL: file)
        let original = directory.appending(path: "original")
        try FileManager.default.moveItem(at: file, to: original)
        try FileManager.default.createDirectory(at: file, withIntermediateDirectories: false)
        tracker.discardShortPausedRun(id)
        XCTAssertFalse(tracker.running)
        XCTAssertEqual(tracker.journal, journal)
        XCTAssertNotNil(tracker.errorMessage)
        XCTAssertEqual(try RunJournal.load(from: original), journal)
        try FileManager.default.removeItem(at: file)
        try FileManager.default.moveItem(at: original, to: file)
        tracker.discardShortPausedRun(id)
        XCTAssertNil(tracker.errorMessage)
        XCTAssertNil(try RunJournal.load(from: file).active)
    }

    func testBuildFourJournalKeepsSavedAndPausedRunsWithoutInventingAnswersOrRoutes() throws {
        let old = """
            {"history":[{"id":"79290DC7-B587-40EA-BE49-D908F877D101","startedAt":0,"meters":3600,"activeSeconds":1200,"finishedAt":1210}],
             "active":{"id":"C4F6C5F0-7608-47AB-97E1-56135BE34E74","startedAt":10000,"meters":190,"activeSeconds":75,"preRunEnergy":"High"}}
            """
        var journal = try JSONDecoder().decode(RunJournal.self, from: Data(old.utf8))
        let paused = try XCTUnwrap(journal.active)
        XCTAssertNil(paused.route)
        XCTAssertNil(paused.splitProgress)
        journal.beginRun(at: start, saveRoute: true)
        XCTAssertEqual(journal.active, paused)
        journal.finish(at: start)
        XCTAssertEqual(journal.history.map(\.meters), [3600, 190])
        XCTAssertTrue(journal.history.allSatisfy { $0.postRunEnergy == nil && $0.postRunEffort == nil && $0.route == nil })
    }

    func testCheckInSavesZeroAndNotSureAcrossCorrectionAndRelaunch() throws {
        var journal = RunJournal()
        journal.beginRun(at: start)
        let id = try XCTUnwrap(journal.active?.id)
        XCTAssertThrowsError(try journal.saveCheckIn(for: id, energy: .spent, effort: 8))
        journal.finish(at: start.addingTimeInterval(450))
        try journal.saveCheckIn(for: id, energy: .notSure, effort: nil)
        try journal.saveCheckIn(for: id, energy: .poweredUp, effort: 0)
        try journal.saveCheckIn(for: id, energy: nil, effort: 10)
        let previous = journal
        XCTAssertThrowsError(try journal.saveCheckIn(for: id, energy: .spent, effort: 11))
        XCTAssertEqual(journal, previous)
        XCTAssertThrowsError(try journal.saveCheckIn(for: UUID(), energy: .spent, effort: 5))
        let reopened = try JSONDecoder().decode(RunJournal.self, from: JSONEncoder().encode(journal))
        let run = try XCTUnwrap(reopened.history.first)
        XCTAssertNil(run.postRunEnergy)
        XCTAssertEqual(run.postRunEffort, 10)
        XCTAssertEqual(run.firstPostRunEnergy, .notSure)
        XCTAssertEqual(run.firstPostRunEffort, 0)
    }

    func testDeletingOneSavedRunUpdatesShoeMileageAndKeepsTheActiveRun() throws {
        var journal = RunJournal()
        try journal.addShoe(brand: "Brooks", model: "Ghost")
        let shoe = try XCTUnwrap(journal.selectedShoe)
        for miles in [1.0, 2.0] {
            journal.beginRun(at: start)
            journal.active?.meters = miles * RunDistance.metersPerMile
            journal.finish(at: start)
        }
        let first = journal.history[0].id
        let second = journal.history[1].id
        journal.beginRun(at: start)
        let active = try XCTUnwrap(journal.active)
        XCTAssertThrowsError(try journal.deleteSavedRun(active.id))
        try journal.deleteSavedRun(first)
        try journal.deleteSavedRun(first)
        XCTAssertEqual(journal.active, active)
        XCTAssertEqual(journal.history.map(\.id), [second])
        XCTAssertEqual(journal.miles(for: shoe.id), 2, accuracy: 0.00001)
        XCTAssertEqual(journal.selectedShoe, shoe)
    }

    @MainActor
    func testFailedEditAndDeleteKeepPreviouslySavedActivityIntact() throws {
        let dir = FileManager.default.temporaryDirectory.appending(path: UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: dir) }
        let file = dir.appending(path: "journal")
        var journal = RunJournal()
        journal.beginRun(at: start)
        journal.finish(at: start)
        try journal.save(to: file)
        let tracker = RunTracker(preferences: .standard, journalURL: file)
        XCTAssertTrue(tracker.canWrite)
        try FileManager.default.moveItem(at: file, to: dir.appending(path: "original"))
        try FileManager.default.createDirectory(at: file, withIntermediateDirectories: false)
        let id = journal.history[0].id
        XCTAssertThrowsError(try tracker.saveCheckIn(for: id, energy: .feelingGood, effort: 5))
        XCTAssertThrowsError(try tracker.deleteSavedRun(id))
        XCTAssertEqual(tracker.journal, journal)
    }

    @MainActor
    func testRouteSettingStartsOffAndOnlyAppliesToNewRuns() throws {
        let suite = UUID().uuidString
        let prefs = try XCTUnwrap(UserDefaults(suiteName: suite))
        defer { prefs.removePersistentDomain(forName: suite) }
        let file = FileManager.default.temporaryDirectory.appending(path: suite).appending(path: "runs.json")
        let tracker = RunTracker(preferences: prefs, journalURL: file)
        XCTAssertFalse(tracker.saveRoutes)
        var journal = RunJournal()
        journal.beginRun(at: start, saveRoute: tracker.saveRoutes)
        tracker.saveRoutes = true
        journal.beginRun(at: start, saveRoute: tracker.saveRoutes)
        XCTAssertNil(journal.active?.route)
        journal.finish(at: start)
        let relaunched = RunTracker(preferences: prefs, journalURL: file)
        XCTAssertTrue(relaunched.saveRoutes)
        journal.beginRun(at: start, saveRoute: relaunched.saveRoutes)
        XCTAssertNotNil(journal.active?.route)
        XCTAssertNil(journal.history.first?.route)
    }
}
