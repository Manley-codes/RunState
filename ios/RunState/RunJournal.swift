import CoreLocation
import Foundation

struct RecordedRun: Codable, Identifiable, Equatable {
    let id: UUID
    let startedAt: Date
    var meters = 0.0
    var activeSeconds: TimeInterval = 0
    var finishedAt: Date?
    var preRunEnergy: String?
}

struct RunJournal: Codable, Equatable {
    var active: RecordedRun?
    var history: [RecordedRun] = []

    mutating func finish(at date: Date) {
        guard var run = active else { return }
        run.finishedAt = date
        history.append(run)
        active = nil
    }

    static func load(from url: URL) throws -> Self {
        do {
            return try JSONDecoder().decode(Self.self, from: Data(contentsOf: url))
        } catch let error as CocoaError where error.code == .fileReadNoSuchFile {
            return Self()
        }
    }

    func save(to url: URL) throws {
        var folder = url.deletingLastPathComponent()
        try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        var values = URLResourceValues()
        values.isExcludedFromBackup = true
        try folder.setResourceValues(values)
        try JSONEncoder().encode(self).write(to: url, options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
    }
}

/// Hold the distance anchor while fixes overlap, so a stationary runner's GPS noise does not add up.
struct RunDistance {
    static let metersPerMile = 1_609.344
    static func mileCrossed(from previous: Double, to total: Double) -> Int? {
        let mile = Int(total / metersPerMile)
        return mile > Int(previous / metersPerMile) ? mile : nil
    }
    private var anchor: CLLocation?
    private var latest: CLLocation?
    private var lastSeen: Date?
    private var segmentStart: Date = .distantPast

    mutating func startSegment(at date: Date) {
        anchor = nil
        latest = nil
        lastSeen = nil
        segmentStart = date
    }

    mutating func receive(_ point: CLLocation, now: Date) -> Double {
        let time = point.timestamp
        guard time >= segmentStart, lastSeen.map({ time > $0 }) ?? true else { return 0 }
        let age = now.timeIntervalSince(time)
        guard age >= 0 else { return 0 }
        lastSeen = time
        guard age <= 10, point.horizontalAccuracy >= 0, point.horizontalAccuracy <= 25,
              CLLocationCoordinate2DIsValid(point.coordinate) else {
            anchor = nil
            latest = nil
            return 0
        }
        guard let previous = latest, let origin = anchor,
              time.timeIntervalSince(previous.timestamp) <= 15 else {
            anchor = point
            latest = point
            return 0
        }
        let interval = time.timeIntervalSince(previous.timestamp)
        let recentTravel = point.distance(from: previous) - previous.horizontalAccuracy - point.horizontalAccuracy
        let meters = point.distance(from: origin)
        guard recentTravel <= 9 * interval,
              meters <= origin.horizontalAccuracy + point.horizontalAccuracy
                  || meters <= 9 * time.timeIntervalSince(origin.timestamp) else {
            anchor = nil
            latest = nil
            return 0
        }
        latest = point
        guard meters > origin.horizontalAccuracy + point.horizontalAccuracy else { return 0 }
        anchor = point
        return meters
    }
}
