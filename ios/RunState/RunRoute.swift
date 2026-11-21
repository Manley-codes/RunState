import CoreLocation
import Foundation

/// A local drawing of accepted GPS fixes. Recorded distance is calculated independently.
struct RunRoute: Codable, Equatable {
    struct Point: Codable, Equatable {
        let latitude: Double
        let longitude: Double
        let beginsSegment: Bool
    }

    static let pointLimit = 4_096
    private(set) var points: [Point] = []
    private(set) var isTruncated = false
    private var needsAnchor = true
    private var samplingStride = 1
    private var stepsSinceSample = 0

    var hasPoints: Bool { !points.isEmpty }

    init() {}

    init(from decoder: Decoder) throws {
        let values = try decoder.container(keyedBy: CodingKeys.self)
        var stored = try values.nestedUnkeyedContainer(forKey: .points)
        while !stored.isAtEnd && stored.currentIndex < Self.pointLimit {
            let point = try stored.decode(Point.self)
            let coordinate = CLLocationCoordinate2D(latitude: point.latitude, longitude: point.longitude)
            guard Self.isValid(coordinate) else { needsAnchor = true; continue }
            points.append(Point(latitude: point.latitude, longitude: point.longitude,
                                beginsSegment: point.beginsSegment || needsAnchor))
            needsAnchor = false
        }
        let hadGap = try values.decodeIfPresent(Bool.self, forKey: .needsAnchor) ?? false
        needsAnchor = needsAnchor || hadGap
        let truncated = try values.decodeIfPresent(Bool.self, forKey: .isTruncated) ?? false
        isTruncated = !stored.isAtEnd || truncated
        let stride = try values.decodeIfPresent(Int.self, forKey: .samplingStride) ?? 1
        let steps = try values.decodeIfPresent(Int.self, forKey: .stepsSinceSample) ?? 0
        samplingStride = min(max(1, stride), Self.pointLimit)
        if !needsAnchor, points.last?.beginsSegment == false, (0..<samplingStride).contains(steps) {
            stepsSinceSample = steps
        }
    }

    mutating func append(_ coordinate: CLLocationCoordinate2D, beginsSegment: Bool) {
        guard !isTruncated else { return }
        guard Self.isValid(coordinate) else {
            needsAnchor = true
            return
        }

        let starts = beginsSegment || needsAnchor || points.isEmpty
        // About one metre of precision is sufficient for a saved outline.
        let point = Point(latitude: (coordinate.latitude * 100_000).rounded() / 100_000,
                          longitude: (coordinate.longitude * 100_000).rounded() / 100_000,
                          beginsSegment: starts)
        if !starts, let last = points.last,
           last.latitude == point.latitude, last.longitude == point.longitude { return }

        if !starts && stepsSinceSample > 0 {
            // Keep the current endpoint accurate between regularly retained samples.
            points[points.count - 1] = point
        } else {
            if points.count >= Self.pointLimit && !makeRoom() {
                isTruncated = true
                return
            }
            points.append(point)
        }
        needsAnchor = false
        stepsSinceSample = starts ? 0 : (stepsSinceSample + 1) % samplingStride
    }

    private static func isValid(_ point: CLLocationCoordinate2D) -> Bool {
        point.latitude.isFinite && point.longitude.isFinite && CLLocationCoordinate2DIsValid(point)
    }

    private mutating func makeRoom() -> Bool {
        var retained: [Point] = []
        retained.reserveCapacity(points.count / 2)
        var step = 0
        for index in points.indices {
            let point = points[index]
            if point.beginsSegment { step = 0 }
            let endsSegment = index == points.count - 1 || points[index + 1].beginsSegment
            if step.isMultiple(of: 2) || endsSegment { retained.append(point) }
            step += 1
        }
        // Thousands of disconnected anchors cannot be simplified into a truthful line.
        // Leave those sections intact and mark the outline as partial.
        guard retained.count <= Self.pointLimit * 3 / 4 else { return false }
        points = retained
        samplingStride = min(samplingStride * 2, Self.pointLimit)
        return true
    }
}
