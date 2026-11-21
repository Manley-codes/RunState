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

    func testBuildTenJournalKeepsEverySavedDetailThroughAnotherSave() throws {
        // The shape Builds 5–10 write: check-in originals, route sections, splits, a saved
        // reflection with its voice receipt, an interrupted request and an archived pair.
        let saved = """
            {"history":[
              {"id":"8D5B2C64-31C8-4C5E-9E0A-6F1D2B3C4A51","startedAt":816202800,"finishedAt":816204100,
               "meters":3300,"activeSeconds":1250,"pausedWithCompleteTime":true,"preRunEnergy":"High",
               "shoePairID":"1A2B3C4D-5E6F-4A7B-8C9D-0E1F2A3B4C5D","postRunEnergy":"poweredUp","postRunEffort":7,
               "firstPostRunEnergy":"feelingGood","firstPostRunEffort":6,"startTimezoneID":"America/Chicago",
               "route":{"points":[{"latitude":29.76043,"longitude":-95.36981,"beginsSegment":true},
                                  {"latitude":29.76143,"longitude":-95.36881,"beginsSegment":false},
                                  {"latitude":29.76243,"longitude":-95.36781,"beginsSegment":true}],
                        "isTruncated":false,"needsAnchor":false,"samplingStride":1,"stepsSinceSample":0},
               "splitProgress":{"mileEnds":[600,1200],"distance":3300,"closedAt":1250,"uncovered":4,
                                "interrupted":false,"reliable":true},
               "writtenReflection":{"saved":{"text":"Saved words.","isTestData":false,
                                             "credentialID":"6C7D8E9F-0A1B-4C2D-8E3F-4A5B6C7D8E9F"}},
               "spokenRequest":{"credentialID":"6C7D8E9F-0A1B-4C2D-8E3F-4A5B6C7D8E9F","outcome":"received"}},
              {"id":"2F3E4D5C-6B7A-4988-A7B6-C5D4E3F2A1B0","startedAt":816289200,"finishedAt":816289800,
               "meters":1609.344,"activeSeconds":590,"pausedWithCompleteTime":true,
               "shoePairID":"7E8F9A0B-1C2D-4E3F-9A4B-5C6D7E8F9A0B","startTimezoneID":"America/Chicago",
               "splitProgress":{"mileEnds":[590],"distance":1609.344,"closedAt":590,"uncovered":2,
                                "interrupted":false,"reliable":true},
               "writtenReflection":{"pending":{
                 "request":{"requestId":"0d9c8b7a-6f5e-4d3c-8b2a-1f0e9d8c7b6a","runId":"2f3e4d5c-6b7a-4988-a7b6-c5d4e3f2a1b0",
                   "evidence":{"officialStartEpochMillis":1794596400000,"finishEpochMillis":1794597000000,
                     "startTimezoneId":"America/Chicago","activeDurationMillis":590000,
                     "metrics":{"distanceMeters":1609.344,"source":"GPS","coverage":"COMPLETE","displayUnit":"MILES"},
                     "preRunEnergy":"UNKNOWN"}},
                 "credentialID":"6C7D8E9F-0A1B-4C2D-8E3F-4A5B6C7D8E9F","replayOnly":true,"firstAttemptAt":816289900}}}],
             "active":{"id":"9A8B7C6D-5E4F-4A3B-8C2D-1E0F9A8B7C6D","startedAt":816300000,"meters":40,"activeSeconds":12,
                       "pausedWithCompleteTime":true,"shoePairID":"7E8F9A0B-1C2D-4E3F-9A4B-5C6D7E8F9A0B",
                       "startTimezoneID":"America/Chicago",
                       "splitProgress":{"mileEnds":[],"distance":40,"closedAt":12,"uncovered":1,"interrupted":false,"reliable":true}},
             "shoePairs":[{"id":"7E8F9A0B-1C2D-4E3F-9A4B-5C6D7E8F9A0B","brand":"Hoka","model":"Clifton"},
                          {"id":"1A2B3C4D-5E6F-4A7B-8C9D-0E1F2A3B4C5D","brand":"Brooks","model":"Ghost","nickname":"Daily",
                           "archiveID":"3B4C5D6E-7F8A-4B9C-8D0E-1F2A3B4C5D6E"}],
             "selectedShoeID":"7E8F9A0B-1C2D-4E3F-9A4B-5C6D7E8F9A0B",
             "selectionRevision":"4C5D6E7F-8A9B-4C0D-9E1F-2A3B4C5D6E7F"}
            """
        let journal = try JSONDecoder().decode(RunJournal.self, from: Data(saved.utf8))
        XCTAssertEqual(journal.history.count, 2)
        let first = journal.history[0]
        let second = journal.history[1]
        // Corrections keep the first reported answers beside the current ones.
        XCTAssertEqual(first.postRunEnergy, .poweredUp)
        XCTAssertEqual(first.firstPostRunEnergy, .feelingGood)
        XCTAssertEqual(first.firstPostRunEffort, 6)
        XCTAssertEqual(first.route?.points.count, 3)
        XCTAssertEqual(first.route?.points.filter(\.beginsSegment).count, 2)
        XCTAssertEqual(first.mileSplits.map(\.activeSeconds), [600, 600, 50])
        XCTAssertEqual(first.mileSplits.map(\.isPartial), [false, false, true])
        XCTAssertNotNil(ActiveRunView.averagePaceText(for: first, at: first.activeSeconds))
        XCTAssertEqual(first.writtenReflection?.saved?.text, "Saved words.")
        XCTAssertEqual(first.spokenRequest?.outcome, .received)
        // The interrupted request keeps its frozen facts, credential binding and replay-only state.
        let pending = try XCTUnwrap(second.writtenReflection?.pending)
        XCTAssertTrue(pending.replayOnly)
        XCTAssertNil(pending.selectedEnergy)
        XCTAssertEqual(pending.request.runId, second.id.uuidString.lowercased())
        XCTAssertEqual(pending.credentialID, first.writtenReflection?.saved?.credentialID)
        // An archived pair still names its run and keeps its miles. Active runs never count.
        let archived = try XCTUnwrap(journal.shoe(for: first.shoePairID))
        let selected = try XCTUnwrap(journal.selectedShoe)
        XCTAssertTrue(archived.isArchived)
        XCTAssertEqual(selected.model, "Clifton")
        XCTAssertEqual(journal.miles(for: archived.id), 3300 / RunDistance.metersPerMile, accuracy: 0.000_001)
        XCTAssertEqual(journal.miles(for: selected.id), 1, accuracy: 0.000_001)
        XCTAssertEqual(journal.active?.canDiscardAsShortRun, true)
        // Saving with this build writes the same values, so an update cannot drop older details.
        XCTAssertEqual(try JSONDecoder().decode(RunJournal.self, from: JSONEncoder().encode(journal)), journal)
    }

    func testSavedStartIsShownInTheTimeZoneWhereTheRunBegan() throws {
        var run = RecordedRun(id: UUID(), startedAt: start)
        let tokyo = try XCTUnwrap(TimeZone(identifier: "Asia/Tokyo"))
        let chicago = try XCTUnwrap(TimeZone(identifier: "America/Chicago"))
        func text(in zone: TimeZone, date: Date.FormatStyle.DateStyle = .abbreviated) -> String {
            var format = Date.FormatStyle(date: date, time: .shortened)
            format.timeZone = zone
            return start.formatted(format)
        }
        XCTAssertNotEqual(text(in: tokyo), text(in: chicago))
        run.startTimezoneID = "Asia/Tokyo"
        XCTAssertEqual(run.startText(date: .abbreviated, time: .shortened), text(in: tokyo))
        XCTAssertEqual(run.startText(date: .complete, time: .shortened), text(in: tokyo, date: .complete))
        // Runs saved before the zone was recorded, or with an unreadable zone, use the iPhone's zone.
        for legacy in [nil, "Not/AZone"] as [String?] {
            run.startTimezoneID = legacy
            XCTAssertEqual(run.startText(date: .abbreviated, time: .shortened),
                           start.formatted(date: .abbreviated, time: .shortened))
        }
    }
}
