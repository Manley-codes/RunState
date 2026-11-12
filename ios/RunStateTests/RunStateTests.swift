import CoreLocation
import UIKit
import XCTest
@testable import RunState

final class RunStateTests: XCTestCase {
    let epoch = Date(timeIntervalSince1970: 1_800_000_000)

    // At the equator a degree of longitude spans roughly 111,319 metres.
    func fix(_ metersEast: Double, _ seconds: Double, accuracy: Double = 2) -> CLLocation {
        CLLocation(coordinate: CLLocationCoordinate2D(latitude: 0, longitude: metersEast / 111_319),
                   altitude: 0, horizontalAccuracy: accuracy, verticalAccuracy: 5,
                   timestamp: epoch.addingTimeInterval(seconds))
    }

    func testPausedMovementAndDelayedPreResumeFixDoNotAddDistance() {
        var distance = RunDistance()
        distance.startSegment(at: epoch)
        let first = fix(0, 0), second = fix(15, 3)
        XCTAssertEqual(distance.receive(first, now: first.timestamp), 0)
        XCTAssertEqual(distance.receive(second, now: second.timestamp), 15, accuracy: 0.5)
        distance.startSegment(at: epoch.addingTimeInterval(10))
        let resume = fix(500, 10), later = fix(515, 13)
        XCTAssertEqual(distance.receive(fix(35, 9), now: resume.timestamp), 0)
        XCTAssertEqual(distance.receive(resume, now: resume.timestamp), 0)
        XCTAssertEqual(distance.receive(later, now: later.timestamp), 15, accuracy: 0.5)
    }

    func testNoiseBadFixesJumpsAndUnobservedTimeAreExcluded() {
        var distance = RunDistance()
        for fix in [fix(0, 0), fix(2, 1), fix(1_000, 2),
                    fix(1_001, 3), fix(1_010, 4, accuracy: 50),
                    fix(1_012, 5), fix(1_200, 30)] {
            XCTAssertEqual(distance.receive(fix, now: fix.timestamp), 0)
        }
        let stale = fix(1_215, 33)
        XCTAssertEqual(distance.receive(stale, now: epoch.addingTimeInterval(50)), 0)
    }

    func testMileCrossingEmitsOnceAndKeepsWorkingAfterPause() {
        let mile = RunDistance.metersPerMile
        XCTAssertNil(RunDistance.mileCrossed(from: 0, to: mile - 1))
        XCTAssertEqual(RunDistance.mileCrossed(from: mile - 1, to: mile), 1)
        XCTAssertNil(RunDistance.mileCrossed(from: mile, to: mile + 2))
        XCTAssertEqual(RunDistance.mileCrossed(from: 2 * mile - 5, to: 2 * mile + 1), 2)
    }

    func testSavingIsIdempotentAndKeepsHistoryThroughRelaunch() throws {
        let folder = FileManager.default.temporaryDirectory.appending(path: UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: folder) }
        let url = folder.appending(path: "runs.json")
        var journal = try RunJournal.load(from: url)
        let id = UUID()
        journal.active = RecordedRun(id: id, startedAt: epoch, meters: 1_610, activeSeconds: 540)
        try journal.save(to: url)
        journal = try RunJournal.load(from: url)
        XCTAssertEqual(journal.active?.id, id)
        journal.finish(at: epoch.addingTimeInterval(600))
        journal.finish(at: epoch.addingTimeInterval(601))
        try journal.save(to: url)
        let reopened = try RunJournal.load(from: url)
        XCTAssertNil(reopened.active)
        XCTAssertEqual(reopened.history.count, 1)
        XCTAssertEqual(reopened.history.first?.id, id)
        XCTAssertEqual(reopened.history.first?.meters, 1_610)
        XCTAssertEqual(reopened.history.first?.activeSeconds, 540)
    }

    func testOriginalSavedRunsOpenAndOptionalStartingEnergySurvivesSaving() throws {
        let old = """
            {"history":[{"id":"8C588C75-AACA-49C2-B849-355D732E80BB","startedAt":0,"meters":1610,"activeSeconds":540}]}
            """
        var journal = try JSONDecoder().decode(RunJournal.self, from: Data(old.utf8))
        XCTAssertNil(journal.history.first?.preRunEnergy)
        journal.active = RecordedRun(id: UUID(), startedAt: epoch, preRunEnergy: "High")
        journal.finish(at: epoch.addingTimeInterval(60))
        let saved = try JSONDecoder().decode(RunJournal.self, from: JSONEncoder().encode(journal))
        XCTAssertEqual(saved.history.map(\.preRunEnergy), [nil, "High"])
    }

    func testBuild3HistoryAndPausedRunStayUnassignedAfterAddingShoes() throws {
        let saved = """
            {"active":{"id":"77777777-7777-4777-8777-777777777777","startedAt":10,"meters":180,"activeSeconds":60,"preRunEnergy":"Low"},
             "history":[{"id":"88888888-8888-4888-8888-888888888888","startedAt":0,"meters":1609.344,"activeSeconds":600,"finishedAt":700}]}
            """
        var journal = try JSONDecoder().decode(RunJournal.self, from: Data(saved.utf8))
        XCTAssertTrue(journal.shoePairs.isEmpty)
        try journal.addShoe(brand: "Nike", model: "Pegasus 42", nickname: nil)
        let pair = try XCTUnwrap(journal.selectedShoe)
        journal.beginRun(at: epoch, preRunEnergy: "High")
        XCTAssertEqual(journal.active?.preRunEnergy, "Low")
        XCTAssertNil(journal.active?.shoePairID)
        journal.finish(at: epoch)
        XCTAssertEqual(journal.history.count, 2)
        XCTAssertTrue(journal.history.allSatisfy { $0.shoePairID == nil })
        XCTAssertEqual(journal.miles(for: pair.id), 0)
    }

    func testPairCapturedAtStartCountsFinishedDistanceOnceAcrossReloads() throws {
        var journal = RunJournal()
        try journal.addShoe(brand: "Brooks", model: "Ghost 18", nickname: "First pair")
        let first = try XCTUnwrap(journal.selectedShoe)
        journal.beginRun(at: epoch, preRunEnergy: nil)
        journal.active?.meters = 2 * RunDistance.metersPerMile
        XCTAssertEqual(journal.miles(for: first.id), 0)

        try journal.addShoe(brand: "Brooks", model: "Ghost 18", nickname: "Second pair")
        let second = try XCTUnwrap(journal.selectedShoe)
        XCTAssertNotEqual(first.id, second.id)
        journal = try JSONDecoder().decode(RunJournal.self, from: JSONEncoder().encode(journal))
        journal.beginRun(at: epoch, preRunEnergy: nil)
        XCTAssertEqual(journal.active?.shoePairID, first.id)
        journal.finish(at: epoch.addingTimeInterval(900))
        journal.finish(at: epoch.addingTimeInterval(901))

        try journal.selectShoe(nil)
        journal.beginRun(at: epoch.addingTimeInterval(1_000), preRunEnergy: nil)
        journal.active?.meters = RunDistance.metersPerMile
        journal.finish(at: epoch.addingTimeInterval(1_500))
        let reopened = try JSONDecoder().decode(RunJournal.self, from: JSONEncoder().encode(journal))
        XCTAssertEqual(reopened.history.map(\.shoePairID), [first.id, nil])
        XCTAssertEqual(reopened.miles(for: first.id), 2, accuracy: 0.0001)
        XCTAssertEqual(reopened.miles(for: second.id), 0)
    }

    func testAddingTheSamePhysicalPairTwiceIsSafeToRetry() throws {
        let id = UUID()
        var journal = RunJournal()
        try journal.addShoe(brand: "  Nike ", model: "Pegasus 42", nickname: " Race ", id: id)
        try journal.addShoe(brand: "Nike", model: "Pegasus 42", nickname: "Race", id: id)
        XCTAssertEqual(journal.shoePairs.count, 1)
        XCTAssertEqual(journal.selectedShoe?.displayName, "Race")
        XCTAssertThrowsError(try journal.addShoe(brand: "Nike", model: "Another model", id: id))
        XCTAssertThrowsError(try journal.addShoe(brand: " ", model: "Unbranded"))
        XCTAssertEqual(journal.shoePairs.count, 1)
        XCTAssertEqual(journal.selectedShoe?.id, id)
    }

    func testArchivingPreservesMilesAndUndoHonorsLaterSelection() throws {
        var journal = RunJournal()
        try journal.addShoe(brand: "HOKA", model: "Clifton 11", nickname: nil)
        let pair = try XCTUnwrap(journal.selectedShoe)
        journal.beginRun(at: epoch, preRunEnergy: nil)
        journal.active?.meters = RunDistance.metersPerMile
        journal.finish(at: epoch.addingTimeInterval(600))

        let archive = try journal.archiveShoe(pair.id)
        XCTAssertNil(journal.selectedShoe)
        XCTAssertEqual(journal.shoe(for: journal.history.first?.shoePairID)?.id, pair.id)
        XCTAssertThrowsError(try journal.selectShoe(pair.id))
        try journal.undoArchive(archive)
        XCTAssertEqual(journal.selectedShoe?.id, pair.id)
        let again = try journal.archiveShoe(pair.id)
        try journal.selectShoe(nil) // An explicit choice after archiving must win over Undo.
        try journal.undoArchive(again)
        XCTAssertNil(journal.selectedShoe)
        _ = try journal.archiveShoe(pair.id)
        try journal.restoreShoe(pair.id)
        XCTAssertEqual(journal.selectedShoe?.id, pair.id)
        XCTAssertEqual(journal.miles(for: pair.id), 1, accuracy: 0.0001)
    }

    @MainActor
    func testShoeSelectionWriteFailureLeavesPublishedJournalUntouched() throws {
        let suite = "RunStateTests." + UUID().uuidString
        let preferences = try XCTUnwrap(UserDefaults(suiteName: suite))
        defer { preferences.removePersistentDomain(forName: suite) }
        let folder = FileManager.default.temporaryDirectory.appending(path: UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: folder) }
        let url = folder.appending(path: "runs.json")
        let tracker = RunTracker(preferences: preferences, journalURL: url)
        try tracker.addShoe(brand: "Nike", model: "Pegasus 42", nickname: nil)
        let saved = tracker.journal
        XCTAssertEqual(try RunJournal.load(from: url), saved)
        // Make the expected file path a directory to force a real atomic-write failure.
        try FileManager.default.removeItem(at: url)
        try FileManager.default.createDirectory(at: url, withIntermediateDirectories: false)
        XCTAssertThrowsError(try tracker.selectShoe(nil))
        XCTAssertEqual(tracker.journal, saved)
    }

    @MainActor
    func testCanceledCountdownCannotStartTheNextRunAndStartNowStartsOnce() throws {
        var introCallbacks: [() -> Void] = []
        var starts = 0
        let countdown = RunCountIn(speak: { _, completion in introCallbacks.append(completion) }, silence: {})
        countdown.begin { starts += 1 }
        countdown.begin { XCTFail("Duplicate countdown") }
        let stale = try XCTUnwrap(introCallbacks.first)
        XCTAssertEqual(countdown.seconds, 10)
        countdown.cancel()
        countdown.begin { starts += 1 }
        stale()
        XCTAssertFalse(countdown.cadenceActive)
        XCTAssertEqual(introCallbacks.count, 2)
        introCallbacks[1]()
        XCTAssertTrue(countdown.cadenceActive)
        countdown.startNow()
        countdown.startNow()
        introCallbacks[1]()
        XCTAssertNil(countdown.seconds)
        XCTAssertEqual(starts, 1)
    }

    @MainActor
    func testTimedCountdownStartsOnceAndSpeaksOnlyTheLastThreeDigits() async {
        let started = expectation(description: "Countdown finished")
        var cues: [String] = []
        let countdown = RunCountIn(speak: { text, finished in cues.append(text); finished() }, silence: {})
        countdown.begin { started.fulfill() }
        await fulfillment(of: [started], timeout: 20)
        XCTAssertNil(countdown.seconds)
        XCTAssertEqual(cues, ["Starting in ten.", "3", "2", "1"])
    }

    @MainActor
    func testElapsedCountdownAndLiveRunMetrics() {
        let beginning = ContinuousClock().now
        XCTAssertEqual(RunCountIn.secondsLeft(since: beginning, now: beginning + .milliseconds(999)), 10)
        XCTAssertEqual(RunCountIn.secondsLeft(since: beginning, now: beginning + .seconds(7)), 3)
        XCTAssertEqual(RunCountIn.secondsLeft(since: beginning, now: beginning + .seconds(12)), 0)
        XCTAssertNil(ActiveRunView.paceText(meters: 0, activeSeconds: 20))
        XCTAssertEqual(ActiveRunView.paceText(meters: RunDistance.metersPerMile, activeSeconds: 540), "9:00 /mi")
        XCTAssertEqual(ActiveRunView.clockText(3_901), "01:05:01")
    }

    @MainActor
    func testMileAnnouncementPreferenceSurvivesRelaunchWithoutCreatingARun() throws {
        let suite = "RunStateTests." + UUID().uuidString
        let preferences = try XCTUnwrap(UserDefaults(suiteName: suite))
        defer { preferences.removePersistentDomain(forName: suite) }
        let journalURL = FileManager.default.temporaryDirectory.appending(path: UUID().uuidString + ".json")
        let first = RunTracker(preferences: preferences, journalURL: journalURL)
        XCTAssertTrue(first.mileAnnouncementsEnabled)
        first.mileAnnouncementsEnabled = false

        let reopened = RunTracker(preferences: preferences, journalURL: journalURL)
        XCTAssertFalse(reopened.mileAnnouncementsEnabled)
        reopened.mileAnnouncementsEnabled = true
        XCTAssertTrue(RunTracker(preferences: preferences, journalURL: journalURL).mileAnnouncementsEnabled)
        XCTAssertFalse(FileManager.default.fileExists(atPath: journalURL.path))
    }

    @MainActor
    func testAppBundleContainsThePhotographicOpeningAndInstrumentFonts() throws {
        let bundle = Bundle(for: RunTracker.self)
        let opening = try XCTUnwrap(bundle.urls(forResourcesWithExtension: "webp", subdirectory: "opening"))
        XCTAssertEqual(opening.count, 33)
        for url in opening { XCTAssertNotNil(UIImage(contentsOfFile: url.path), url.lastPathComponent) }
        for (name, ext) in [("opening_first_frame", "webp"), ("start_map_reference", "jpg"), ("start_music_orb", "jpg")] {
            let picture = try XCTUnwrap(bundle.url(forResource: name, withExtension: ext))
            XCTAssertNotNil(UIImage(contentsOfFile: picture.path))
        }
        for name in ["BarlowCondensed-Medium", "Anton-Regular"] {
            XCTAssertNotNil(UIFont(name: name, size: 18), name)
        }
    }
}
