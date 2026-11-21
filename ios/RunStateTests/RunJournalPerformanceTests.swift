import CoreLocation
import XCTest
@testable import RunState

/// Saving and opening a route-heavy history: about a year of daily runs with saved routes.
/// Timings print as "PERF" lines in the test log; assertions check every saved value.
final class RunJournalPerformanceTests: XCTestCase {
    private static let runs = 200
    private static let pointsPerRun = 1_800
    private let start = Date(timeIntervalSince1970: 1_800_000_000)

    private func heavyJournal() -> RunJournal {
        var journal = RunJournal()
        for index in 0..<Self.runs {
            let day = start.addingTimeInterval(Double(index) * 86_400)
            journal.beginRun(at: day, preRunEnergy: "Moderate", saveRoute: true)
            for step in 0..<Self.pointsPerRun {
                // About three metres apart, so every point is kept.
                journal.active?.route?.append(CLLocationCoordinate2D(latitude: 29.7 + Double(step) * 0.00003,
                                                                     longitude: -95.4 + Double(index) * 0.0001),
                                              beginsSegment: false)
            }
            journal.active?.meters = 5_000
            journal.active?.activeSeconds = 1_800
            journal.active?.pausedWithCompleteTime = true
            journal.finish(at: day.addingTimeInterval(1_900))
        }
        return journal
    }

    /// Milliseconds for the middle of several attempts, so one slow attempt cannot decide it.
    private func median(_ attempts: Int = 5, _ work: () throws -> Void) rethrows -> Double {
        var times: [Double] = []
        for _ in 0..<attempts {
            let elapsed = try ContinuousClock().measure { try work() }
            times.append(Double(elapsed.components.seconds) * 1_000 + Double(elapsed.components.attoseconds) / 1e15)
        }
        return times.sorted()[times.count / 2]
    }

    func testRouteHeavyCheckpointEncodesOnlyTheRunInProgress() throws {
        var journal = heavyJournal()
        XCTAssertEqual(journal.history.count, Self.runs)
        XCTAssertEqual(journal.history.last?.route?.points.count, Self.pointsPerRun)
        journal.beginRun(at: start.addingTimeInterval(Double(Self.runs) * 86_400), saveRoute: true)
        journal.active?.meters = 1_000
        journal.active?.activeSeconds = 300
        let folder = FileManager.default.temporaryDirectory.appending(path: UUID().uuidString, directoryHint: .isDirectory)
        let file = folder.appending(path: "runs.json")
        defer { try? FileManager.default.removeItem(at: folder) }

        var bytes = 0
        let full = try median { bytes = try JSONEncoder().encode(journal).count }
        var cache: RunJournal.EncodedHistory?
        _ = try journal.encoded(reusing: &cache)
        let checkpoint = try median {
            journal.active?.meters += 10
            journal.active?.activeSeconds += 15
            _ = try journal.encoded(reusing: &cache)
        }
        let saved = try journal.encoded(reusing: &cache)
        XCTAssertEqual(try JSONDecoder().decode(RunJournal.self, from: saved), journal)
        let write = try median(3) {
            journal.active?.activeSeconds += 15
            try journal.save(to: file, reusing: &cache)
        }
        XCTAssertEqual(try RunJournal.load(from: file), journal)
        let open = try median(3) { _ = try RunJournal.load(from: file) }

        // Finishing adds one run to the reused JSON instead of encoding every saved run again.
        journal.finish(at: start.addingTimeInterval(Double(Self.runs) * 86_400 + 400))
        let finished = try median(1) { try journal.save(to: file, reusing: &cache) }
        XCTAssertEqual(try RunJournal.load(from: file), journal)
        XCTAssertEqual(journal.history.count, Self.runs + 1)

        print(String(format: "PERF route-heavy journal: %ld runs, %.1f MB; full encode %.1f ms; checkpoint encode %.1f ms; checkpoint save with file write %.1f ms; finish save %.1f ms; open %.1f ms",
                     Self.runs + 1, Double(bytes) / 1_048_576, full, checkpoint, write, finished, open))
        // Generous on purpose: the measured gap is far larger, and CI machines vary.
        XCTAssertLessThan(checkpoint * 3, full, "A checkpoint should not encode the saved history again.")
    }

    func testReusedHistoryFollowsEveryKindOfChange() throws {
        var journal = RunJournal()
        var cache: RunJournal.EncodedHistory?
        func check(_ step: String) throws {
            let saved = try journal.encoded(reusing: &cache)
            XCTAssertEqual(try JSONDecoder().decode(RunJournal.self, from: saved), journal, step)
        }
        try check("empty journal")
        // Saved text that looks like the history slot cannot be mistaken for it.
        try journal.addShoe(brand: "Brooks", model: "Ghost", nickname: #""history":[]"#)
        try journal.selectShoe(journal.shoePairs[0].id)
        try check("pair with a look-alike nickname")
        journal.beginRun(at: start, preRunEnergy: "High", saveRoute: true)
        journal.active?.route?.append(CLLocationCoordinate2D(latitude: 29.7, longitude: -95.4), beginsSegment: true)
        try check("run in progress")
        for offset in 1...3 {
            journal.active?.meters = Double(offset) * 1_000
            journal.finish(at: start.addingTimeInterval(Double(offset) * 600))
            try check("finished run \(offset)")
            journal.beginRun(at: start.addingTimeInterval(Double(offset) * 900))
            try check("next run \(offset)")
        }
        try journal.saveCheckIn(for: journal.history[0].id, energy: .spent, effort: 4)
        try check("edited check-in")
        try journal.deleteSavedRun(journal.history[1].id)
        try check("deleted run")
        _ = try journal.archiveShoe(journal.shoePairs[0].id)
        try check("archived pair")
        journal.active?.pausedWithCompleteTime = true
        try journal.discardShortPausedRun(XCTUnwrap(journal.active?.id))
        try check("discarded short run")
        journal.history.removeAll()
        try check("history cleared")
    }
}
