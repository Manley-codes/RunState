import CoreLocation
import Foundation

struct RunMileSplit: Equatable, Identifiable {
    let number: Int
    let meters: Double
    let activeSeconds: TimeInterval
    let isPartial: Bool
    var id: Int { number }
}

/// Keeps only measured mile boundaries and the timing evidence needed to check GPS coverage.
/// All times are seconds on the run's active clock, regardless of how long a pause lasts.
struct RunSplitProgress: Codable, Equatable {
    private static let distanceTolerance = 0.000_001
    private static let maximumGap: TimeInterval = 15
    private var mileEnds: [TimeInterval] = []
    private var distance = 0.0
    private var closedAt: TimeInterval = 0
    private var segmentStart: TimeInterval?
    private var lastFix: TimeInterval?
    private var uncovered: TimeInterval = 0
    private var interrupted = false
    private var reliable = true

    mutating func startSegment(at seconds: TimeInterval) {
        // An app restart restores the last persisted checkpoint as paused. Never join fixes
        // across that restart, even if the original process missed the explicit pause.
        if segmentStart != nil { endSegment(at: seconds) }
        guard seconds.isFinite, abs(seconds - closedAt) < 0.001 else {
            reliable = false
            return
        }
        segmentStart = seconds
        lastFix = nil
        interrupted = false
    }

    mutating func interrupt() {
        if lastFix != nil { interrupted = true }
    }

    mutating func observe(meters: Double, at seconds: TimeInterval) {
        guard reliable, let segmentStart else { return }
        let previous = lastFix ?? segmentStart
        let elapsed = seconds - previous
        guard meters.isFinite, meters >= distance, elapsed.isFinite,
              elapsed >= 0, elapsed <= Self.maximumGap else {
            reliable = false
            return
        }
        if lastFix == nil || interrupted {
            // A new anchor can establish position, never a distance across the missed interval.
            guard meters == distance else {
                reliable = false
                return
            }
            uncovered += elapsed
        } else if meters > distance {
            guard elapsed > 0 else {
                reliable = false
                return
            }
            // Accurate stationary fixes also advance the clock. Interpolate a crossing only
            // between the two most recent valid fixes, never across a stop in GPS recording.
            let mile = RunDistance.metersPerMile
            var boundary = Double(mileEnds.count + 1) * mile
            while boundary <= meters + Self.distanceTolerance {
                let fraction = min(1, max(0, (boundary - distance) / (meters - distance)))
                mileEnds.append(previous + elapsed * fraction)
                boundary = Double(mileEnds.count + 1) * mile
            }
        }
        distance = meters
        lastFix = seconds
        interrupted = false
    }

    mutating func endSegment(at seconds: TimeInterval) {
        guard let segmentStart else { return }
        if let lastFix {
            let tail = seconds - lastFix
            if !tail.isFinite || tail < 0 || tail > Self.maximumGap || interrupted {
                reliable = false
            } else {
                uncovered += tail
            }
        } else if seconds != segmentStart {
            reliable = false
        }
        closedAt = seconds
        self.segmentStart = nil
        lastFix = nil
        interrupted = false
    }

    private func hasCoverage(meters: Double, at seconds: TimeInterval) -> Bool {
        guard reliable, !interrupted, distance > 0,
              abs(distance - meters) <= Self.distanceTolerance,
              seconds.isFinite, seconds > 0 else { return false }
        let tail: TimeInterval
        if segmentStart != nil {
            guard let lastFix else { return false }
            tail = seconds - lastFix
        } else {
            guard abs(seconds - closedAt) < 0.001 else { return false }
            tail = 0
        }
        // Match Android's allowance for short acquisition, recovery and finish edges.
        return tail >= 0 && tail <= Self.maximumGap && uncovered + tail <= seconds * 0.05
    }

    func currentMile(meters: Double, activeSeconds: TimeInterval) -> RunMileSplit? {
        guard hasCoverage(meters: meters, at: activeSeconds) else { return nil }
        let remaining = meters - Double(mileEnds.count) * RunDistance.metersPerMile
        let duration = activeSeconds - (mileEnds.last ?? 0)
        guard remaining >= 25, duration >= 10 else { return nil }
        return RunMileSplit(number: mileEnds.count + 1, meters: remaining, activeSeconds: duration, isPartial: true)
    }

    func finishedSplits(meters: Double, activeSeconds: TimeInterval) -> [RunMileSplit] {
        guard segmentStart == nil, hasCoverage(meters: meters, at: activeSeconds) else { return [] }
        let mile = RunDistance.metersPerMile
        let remainder = meters - Double(mileEnds.count) * mile
        let exactFinish = !mileEnds.isEmpty && abs(remainder) <= Self.distanceTolerance
        var rows: [RunMileSplit] = []
        var previous: TimeInterval = 0
        for (index, crossing) in mileEnds.enumerated() {
            // Stationary time after the last crossing still belongs to that exact mile.
            let end = exactFinish && index == mileEnds.count - 1 ? activeSeconds : crossing
            guard end.isFinite, end > previous, end <= activeSeconds else { return [] }
            rows.append(RunMileSplit(number: index + 1, meters: mile, activeSeconds: end - previous, isPartial: false))
            previous = end
        }
        if !exactFinish {
            guard remainder > 0, remainder < mile, activeSeconds > previous else { return [] }
            rows.append(RunMileSplit(number: rows.count + 1, meters: remainder, activeSeconds: activeSeconds - previous, isPartial: true))
        }
        return rows
    }
}

extension RecordedRun {
    /// Apply a complete Core Location delivery in timestamp order supplied by the OS. A
    /// batched mile crossing uses the fix time, independent of when the callback arrived.
    mutating func record(_ locations: [CLLocation], using distance: inout RunDistance,
                         deliveredAt: Date, activeAtDelivery: TimeInterval, segmentStart: TimeInterval) {
        for point in locations {
            switch distance.classify(point, now: deliveredAt) {
            case .ignored:
                continue
            case .interrupted:
                splitProgress?.interrupt()
                continue
            case .anchor:
                splitProgress?.interrupt()
                route?.append(point.coordinate, beginsSegment: true)
            case .movement(let added):
                meters += added
                route?.append(point.coordinate, beginsSegment: false)
            case .stationary:
                break
            }
            let age = deliveredAt.timeIntervalSince(point.timestamp)
            splitProgress?.observe(meters: meters, at: max(segmentStart, activeAtDelivery - age))
        }
    }

    var mileSplits: [RunMileSplit] {
        guard finishedAt != nil else { return [] }
        return splitProgress?.finishedSplits(meters: meters, activeSeconds: activeSeconds) ?? []
    }
}
