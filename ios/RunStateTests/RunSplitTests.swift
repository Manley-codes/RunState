import CoreLocation
import XCTest
@testable import RunState

final class RunSplitTests: XCTestCase {
    private let mile = RunDistance.metersPerMile

    func testGpsStatusReflectsAcceptedAndRejectedFixesAndIgnoresOldCallbacks() {
        let epoch = Date(timeIntervalSince1970: 1_800_000_000)
        var run = RecordedRun(id: UUID(), startedAt: epoch)
        var filter = RunDistance()
        filter.startSegment(at: epoch)
        func point(_ east: Double, at seconds: Double) -> CLLocation {
            CLLocation(coordinate: CLLocationCoordinate2D(latitude: 0, longitude: east / 111_319.49),
                       altitude: 0, horizontalAccuracy: 1, verticalAccuracy: 2,
                       timestamp: epoch.addingTimeInterval(seconds))
        }
        func deliver(_ points: [CLLocation], at seconds: Double) -> RunGPSObservation? {
            run.record(points, using: &filter, deliveredAt: epoch.addingTimeInterval(seconds),
                       activeAtDelivery: seconds, segmentStart: 0)
        }
        XCTAssertEqual(deliver([point(0, at: 0)], at: 0), .tracking)
        XCTAssertEqual(deliver([point(20, at: 5), point(5, at: 2)], at: 5), .tracking)
        XCTAssertEqual(deliver([point(20, at: 6)], at: 6), .tracking)
        // Good reported accuracy cannot make a stale fix usable.
        XCTAssertEqual(deliver([point(30, at: 10), point(20, at: 6)], at: 22), .waiting)
        XCTAssertNil(deliver([point(10, at: 4)], at: 22))
        XCTAssertEqual(deliver([point(40, at: 23)], at: 23), .tracking)
        XCTAssertEqual(deliver([point(41, at: 24), point(1_000, at: 25)], at: 25), .waiting)
        XCTAssertEqual(run.meters, 20, accuracy: 0.2)
    }

    func testMileBoundaryUsesIndividualBatchedFixTimesAndPausedTimeIsExcluded() throws {
        var journal = RunJournal()
        let epoch = Date(timeIntervalSince1970: 1_800_000_000)
        journal.beginRun(at: epoch)
        var run = try XCTUnwrap(journal.active)
        var filter = RunDistance()
        filter.startSegment(at: epoch)
        run.splitProgress?.startSegment(at: 0)

        func point(_ east: Double, at seconds: Double) -> CLLocation {
            CLLocation(coordinate: CLLocationCoordinate2D(latitude: 0, longitude: east / 111_319.49),
                       altitude: 0, horizontalAccuracy: 1, verticalAccuracy: 2,
                       timestamp: epoch.addingTimeInterval(seconds))
        }
        for delivery in stride(from: 10, through: 410, by: 10) {
            let fixes = stride(from: delivery == 10 ? 0 : delivery - 5, through: delivery, by: 5)
                .map { point(Double($0 * 4), at: Double($0)) }
            run.record(fixes, using: &filter, deliveredAt: epoch.addingTimeInterval(Double(delivery)),
                       activeAtDelivery: Double(delivery), segmentStart: 0)
        }
        run.splitProgress?.endSegment(at: 410)
        let beforePause = try XCTUnwrap(run.splitProgress)
        let checkpoint = try JSONEncoder().encode(beforePause)
        run.splitProgress = try JSONDecoder().decode(RunSplitProgress.self, from: checkpoint)
        filter.startSegment(at: epoch.addingTimeInterval(710))
        run.splitProgress?.startSegment(at: 410)
        // This first GPS position is well away from where the original recording paused.
        for offset in stride(from: 0, through: 50, by: 5) {
            let fix = point(5_000 + Double(offset * 4), at: Double(710 + offset))
            run.record([fix], using: &filter, deliveredAt: fix.timestamp,
                       activeAtDelivery: Double(410 + offset), segmentStart: 410)
        }
        run.activeSeconds = 460
        journal.active = run
        journal.finish(at: epoch.addingTimeInterval(760))
        let saved = try XCTUnwrap(journal.history.first)
        let measuredSpeed = point(20, at: 5).distance(from: point(0, at: 0)) / 5
        XCTAssertEqual(saved.meters, 460 * measuredSpeed, accuracy: 0.1)
        let rows = saved.mileSplits
        XCTAssertEqual(rows.count, 2)
        let first = try XCTUnwrap(rows.first)
        XCTAssertFalse(first.isPartial)
        XCTAssertEqual(first.activeSeconds, mile / measuredSpeed, accuracy: 0.05)
        let partial = try XCTUnwrap(rows.last)
        XCTAssertTrue(partial.isPartial)
        XCTAssertEqual(partial.meters, saved.meters - mile, accuracy: 0.001)
        XCTAssertEqual(rows.map(\.activeSeconds).reduce(0, +), 460, accuracy: 0.001)
    }

    func testCurrentMileHasMinimumEvidenceAndRestartsAtEachBoundary() throws {
        var progress = RunSplitProgress()
        progress.startSegment(at: 0)
        progress.observe(meters: 0, at: 0)
        progress.observe(meters: 27, at: 9)
        XCTAssertNil(progress.currentMile(meters: 27, activeSeconds: 9))
        progress.observe(meters: 30, at: 10)
        XCTAssertNotNil(progress.currentMile(meters: 30, activeSeconds: 10))
        for seconds in stride(from: 20, through: 540, by: 10) {
            progress.observe(meters: Double(seconds * 3), at: Double(seconds))
        }
        XCTAssertNil(progress.currentMile(meters: 1_620, activeSeconds: 540))
        progress.observe(meters: 1_650, at: 550)
        let current = try XCTUnwrap(progress.currentMile(meters: 1_650, activeSeconds: 550))
        XCTAssertEqual(current.number, 2)
        XCTAssertEqual(current.meters, 1_650 - mile, accuracy: 0.001)
        XCTAssertEqual(current.activeSeconds, 550 - mile / 3, accuracy: 0.001)
    }

    func testLongGpsGapsAndOldRecordsDoNotProduceSplits() {
        var progress = RunSplitProgress()
        progress.startSegment(at: 0)
        progress.observe(meters: 0, at: 0)
        progress.observe(meters: 30, at: 10)
        progress.interrupt()
        progress.observe(meters: 30, at: 45)
        progress.observe(meters: 60, at: 55)
        progress.endSegment(at: 55)
        XCTAssertNil(progress.currentMile(meters: 60, activeSeconds: 55))
        XCTAssertTrue(progress.finishedSplits(meters: 60, activeSeconds: 55).isEmpty)

        let prior = RecordedRun(id: UUID(), startedAt: Date(), meters: 3_000, activeSeconds: 900,
                                finishedAt: Date())
        XCTAssertTrue(prior.mileSplits.isEmpty)
    }

    func testExactMileIncludesStationaryTimeWithoutExtraPartial() throws {
        var progress = RunSplitProgress()
        progress.startSegment(at: 0)
        progress.observe(meters: 0, at: 0)
        for index in 1...60 { progress.observe(meters: mile * Double(index) / 60, at: Double(index * 10)) }
        progress.observe(meters: mile, at: 610)
        progress.endSegment(at: 612)
        let rows = progress.finishedSplits(meters: mile, activeSeconds: 612)
        XCTAssertEqual(rows.count, 1)
        let row = try XCTUnwrap(rows.first)
        XCTAssertFalse(row.isPartial)
        XCTAssertEqual(row.activeSeconds, 612)
    }

    func testAveragePaceUsesRecordedCoverageLiveAndAfterSaving() throws {
        var journal = RunJournal()
        journal.beginRun(at: Date())
        var run = try XCTUnwrap(journal.active)
        XCTAssertNil(ActiveRunView.averagePaceText(for: run, at: 20))
        XCTAssertNil(run.partialDistanceNote)
        run.splitProgress?.startSegment(at: 0)
        run.splitProgress?.observe(meters: 0, at: 0)
        for i in 1...54 { run.splitProgress?.observe(meters: mile * Double(i) / 54, at: Double(i * 10)) }
        run.meters = mile
        run.activeSeconds = 540
        XCTAssertEqual(ActiveRunView.averagePaceText(for: run, at: 540), "9:00 /mi")
        var complete = run
        complete.splitProgress?.endSegment(at: 540)
        complete.finishedAt = Date()
        XCTAssertEqual(ActiveRunView.averagePaceText(for: complete, at: 540), "9:00 /mi")
        XCTAssertNil(complete.partialDistanceNote)

        // No usable fix during the next minute. Preserve the mile's distance and stop
        // displaying a slower pace as if all of that minute had been measured.
        XCTAssertNil(ActiveRunView.averagePaceText(for: run, at: 600))
        run.activeSeconds = 600
        journal.active = run
        journal.finish(at: Date())
        let partial = try XCTUnwrap(journal.history.first)
        XCTAssertEqual(partial.meters, mile)
        XCTAssertNil(ActiveRunView.averagePaceText(for: partial, at: 600))
        XCTAssertNotNil(partial.partialDistanceNote)
        XCTAssertTrue(partial.mileSplits.isEmpty)
    }

    func testLegacyAveragePaceStillDisplaysWithoutClaimingModernGpsCoverage() {
        let older = RecordedRun(id: UUID(), startedAt: Date(), meters: mile, activeSeconds: 600, finishedAt: Date())
        XCTAssertNil(older.splitProgress)
        XCTAssertNil(older.pausedWithCompleteTime)
        XCTAssertEqual(ActiveRunView.averagePaceText(for: older, at: 600), "10:00 /mi")
        XCTAssertNil(older.partialDistanceNote)
    }

    @MainActor
    func testRelaunchDuringRunKeepsTotalsAndWithholdsIncompleteSplits() throws {
        let directory = FileManager.default.temporaryDirectory.appending(path: UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: directory) }
        let url = directory.appending(path: "runs.json")
        var journal = RunJournal()
        journal.beginRun(at: Date())
        journal.active?.activeSeconds = 100
        journal.active?.meters = 400
        journal.active?.pausedWithCompleteTime = false
        try journal.save(to: url)
        let tracker = RunTracker(preferences: .standard, journalURL: url)
        XCTAssertEqual(tracker.journal.active?.meters, 400)
        XCTAssertEqual(tracker.journal.active?.activeSeconds, 100)
        XCTAssertNil(tracker.journal.active?.splitProgress)
        tracker.finish()
        let saved = try XCTUnwrap(tracker.journal.history.first)
        XCTAssertNotNil(saved.partialDistanceNote)
        XCTAssertNil(ActiveRunView.averagePaceText(for: saved, at: 100))
    }

    func testBriefGpsRecoveryReanchorsWithoutInventingMovement() throws {
        var progress = RunSplitProgress()
        progress.startSegment(at: 0)
        for index in 0...10 {
            progress.observe(meters: Double(index * 30), at: Double(index * 10))
        }
        progress.interrupt()
        XCTAssertNil(progress.currentMile(meters: 300, activeSeconds: 101))
        progress.observe(meters: 300, at: 102)
        progress.observe(meters: 330, at: 112)
        progress.endSegment(at: 112)
        let row = try XCTUnwrap(progress.finishedSplits(meters: 330, activeSeconds: 112).first)
        XCTAssertEqual(row.meters, 330)
        XCTAssertEqual(row.activeSeconds, 112)
    }
}
