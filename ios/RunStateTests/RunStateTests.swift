import CoreLocation
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
}
