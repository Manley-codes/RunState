import CoreLocation
import XCTest
@testable import RunState

final class RunSplitTests: XCTestCase {
    private let mile = RunDistance.metersPerMile

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
        XCTAssertEqual(saved.meters, 1_840, accuracy: 1)
        let rows = saved.mileSplits
        XCTAssertEqual(rows.count, 2)
        let first = try XCTUnwrap(rows.first)
        XCTAssertFalse(first.isPartial)
        XCTAssertEqual(first.activeSeconds, mile / 4, accuracy: 0.2)
        let partial = try XCTUnwrap(rows.last)
        XCTAssertTrue(partial.isPartial)
        XCTAssertEqual(partial.meters, 1_840 - mile, accuracy: 1)
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
