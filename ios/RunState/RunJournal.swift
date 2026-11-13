import CoreLocation
import Foundation

enum PostRunEnergy: String, Codable, CaseIterable {
    case spent, feelingGood, poweredUp, notSure

    var label: String {
        switch self {
        case .spent: return "Spent"
        case .feelingGood: return "Feeling Good"
        case .poweredUp: return "Powered Up"
        case .notSure: return "Not sure"
        }
    }
}

enum SavedRunError: LocalizedError {
    case notSaved, invalidEffort, storage, ineligibleForDiscard
    var errorDescription: String? {
        switch self {
        case .notSaved: return "This run is no longer in History."
        case .invalidEffort: return "Choose an effort from 0 to 10."
        case .storage: return "RunState couldn’t save that change. Check your iPhone’s storage and try again."
        case .ineligibleForDiscard: return "This run can no longer be discarded as a short, paused run."
        }
    }
}

struct RecordedRun: Codable, Identifiable, Equatable {
    let id: UUID
    let startedAt: Date
    var meters = 0.0
    var activeSeconds: TimeInterval = 0
    // Absent on older records. A recording interrupted between checkpoints may have uncounted time.
    var pausedWithCompleteTime: Bool?
    var finishedAt: Date?
    var preRunEnergy: String?
    var shoePairID: UUID?
    var postRunEnergy: PostRunEnergy?
    var postRunEffort: Int?
    // Preserve the first reported answers when a runner revisits a saved activity.
    var firstPostRunEnergy: PostRunEnergy?
    var firstPostRunEffort: Int?
    var route: RunRoute?
    var splitProgress: RunSplitProgress?
    var startTimezoneID: String?
    var writtenReflection: RunWrittenReflection?
    var spokenRequest: SpokenReflectionRequestRecord?

    var canDiscardAsShortRun: Bool {
        finishedAt == nil && pausedWithCompleteTime == true && (0..<30).contains(activeSeconds)
    }
}

struct ShoePair: Codable, Identifiable, Equatable {
    let id: UUID
    let brand: String
    let model: String
    let nickname: String?
    fileprivate var archiveID: UUID?

    var isArchived: Bool { archiveID != nil }
    var displayName: String { nickname ?? model }
    var fullName: String { [brand, model, nickname].compactMap { $0 }.joined(separator: " · ") }
}

struct ShoeArchiveUndo: Equatable {
    fileprivate let pairID: UUID
    fileprivate let archiveID: UUID
    fileprivate let clearedSelectionRevision: UUID?
}

struct ShoeChangeError: LocalizedError {
    let errorDescription: String?
    init(_ message: String) { errorDescription = message }
}

struct RunJournal: Codable, Equatable {
    var active: RecordedRun?
    var history: [RecordedRun] = []
    private(set) var shoePairs: [ShoePair] = []
    private var selectedShoeID: UUID?
    private var selectionRevision: UUID?

    init() {}

    init(from decoder: Decoder) throws {
        let values = try decoder.container(keyedBy: CodingKeys.self)
        active = try values.decodeIfPresent(RecordedRun.self, forKey: .active)
        history = try values.decode([RecordedRun].self, forKey: .history)
        // Earlier builds stored only active and history. Never infer a shoe for those runs.
        if values.contains(.shoePairs) {
            shoePairs = try values.decode([ShoePair].self, forKey: .shoePairs)
        }
        selectedShoeID = try values.decodeIfPresent(UUID.self, forKey: .selectedShoeID)
        selectionRevision = try values.decodeIfPresent(UUID.self, forKey: .selectionRevision)
    }

    func shoe(for id: UUID?) -> ShoePair? {
        guard let id else { return nil }
        return shoePairs.first { $0.id == id }
    }

    var selectedShoe: ShoePair? {
        guard let pair = shoe(for: selectedShoeID), !pair.isArchived else { return nil }
        return pair
    }

    func miles(for id: UUID) -> Double {
        // The iPhone journal records measured GPS distance. Active runs never enter this sum.
        let meters = history.reduce(0.0) { total, run in
            guard run.shoePairID == id, run.finishedAt != nil, run.meters.isFinite, run.meters > 0 else { return total }
            return total + run.meters
        }
        return meters / RunDistance.metersPerMile
    }

    mutating func addShoe(brand: String, model: String, nickname: String? = nil, id: UUID = UUID()) throws {
        let brand = brand.trimmingCharacters(in: .whitespacesAndNewlines)
        let model = model.trimmingCharacters(in: .whitespacesAndNewlines)
        let name = (nickname ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        guard !brand.isEmpty, brand.count <= 60 else { throw ShoeChangeError("Enter a brand of 1–60 characters.") }
        guard !model.isEmpty, model.count <= 80 else { throw ShoeChangeError("Enter a model of 1–80 characters.") }
        guard name.count <= 40 else { throw ShoeChangeError("Keep the pair nickname to 40 characters or fewer.") }
        let pair = ShoePair(id: id, brand: brand, model: model, nickname: name.isEmpty ? nil : name)
        if let existing = shoe(for: id) {
            guard existing == pair else { throw ShoeChangeError("This pair’s ID is already in use. Reopen Add Shoes to add a pair.") }
        } else {
            shoePairs.insert(pair, at: 0)
        }
        rememberSelection(id)
    }

    mutating func selectShoe(_ id: UUID?) throws {
        if let id, shoe(for: id)?.isArchived != false { throw ShoeChangeError("Select an active saved pair.") }
        rememberSelection(id)
    }

    mutating func archiveShoe(_ id: UUID) throws -> ShoeArchiveUndo {
        guard let index = shoePairs.firstIndex(where: { $0.id == id }), !shoePairs[index].isArchived
        else { throw ShoeChangeError("This pair is no longer in Your Shoes.") }
        let archiveID = UUID()
        let wasSelected = selectedShoeID == id
        shoePairs[index].archiveID = archiveID
        if wasSelected { rememberSelection(nil) }
        return ShoeArchiveUndo(pairID: id, archiveID: archiveID, clearedSelectionRevision: wasSelected ? selectionRevision : nil)
    }

    mutating func undoArchive(_ receipt: ShoeArchiveUndo) throws {
        guard let index = shoePairs.firstIndex(where: { $0.id == receipt.pairID }),
              shoePairs[index].archiveID == receipt.archiveID
        else { throw ShoeChangeError("This removal has already changed. Check the saved pairs.") }
        shoePairs[index].archiveID = nil
        if let revision = receipt.clearedSelectionRevision, selectionRevision == revision {
            rememberSelection(receipt.pairID)
        }
    }

    mutating func restoreShoe(_ id: UUID) throws {
        guard let index = shoePairs.firstIndex(where: { $0.id == id }), shoePairs[index].isArchived
        else { throw ShoeChangeError("This pair is no longer archived.") }
        shoePairs[index].archiveID = nil
        rememberSelection(id)
    }

    private mutating func rememberSelection(_ id: UUID?) {
        selectedShoeID = id
        // Even an explicit choice to run without shoes supersedes an earlier archive's Undo.
        selectionRevision = UUID()
    }

    mutating func beginRun(at date: Date, preRunEnergy: String? = nil, saveRoute: Bool = false) {
        guard active == nil else { return }
        active = RecordedRun(id: UUID(), startedAt: date, preRunEnergy: preRunEnergy, shoePairID: selectedShoe?.id,
                             route: saveRoute ? RunRoute() : nil, splitProgress: RunSplitProgress(),
                             startTimezoneID: TimeZone.current.identifier)
    }

    mutating func discardShortPausedRun(_ id: UUID) throws {
        guard let run = active, run.id == id, run.canDiscardAsShortRun,
              !history.contains(where: { $0.id == id }) else { throw SavedRunError.ineligibleForDiscard }
        active = nil
    }

    mutating func finish(at date: Date) {
        guard var run = active else { return }
        run.splitProgress?.endSegment(at: run.activeSeconds)
        run.finishedAt = date
        if !history.contains(where: { $0.id == run.id }) { history.append(run) }
        active = nil
    }

    mutating func saveCheckIn(for id: UUID, energy: PostRunEnergy?, effort: Int?) throws {
        guard active?.id != id, let index = history.firstIndex(where: { $0.id == id }) else { throw SavedRunError.notSaved }
        if let effort, !(0...10).contains(effort) { throw SavedRunError.invalidEffort }
        if history[index].firstPostRunEnergy == nil { history[index].firstPostRunEnergy = energy }
        if history[index].firstPostRunEffort == nil { history[index].firstPostRunEffort = effort }
        history[index].postRunEnergy = energy
        history[index].postRunEffort = effort
    }

    mutating func deleteSavedRun(_ id: UUID) throws {
        guard active?.id != id else { throw SavedRunError.notSaved }
        history.removeAll { $0.id == id }
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
    enum FixResult {
        case ignored, interrupted, anchor, stationary
        case movement(Double)
    }

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
        if case let .movement(meters) = classify(point, now: now) { return meters }
        return 0
    }

    mutating func classify(_ point: CLLocation, now: Date) -> FixResult {
        let time = point.timestamp
        guard time >= segmentStart, lastSeen.map({ time > $0 }) ?? true else { return .ignored }
        let age = now.timeIntervalSince(time)
        guard age >= 0 else { return .ignored }
        lastSeen = time
        guard age <= 10, point.horizontalAccuracy >= 0, point.horizontalAccuracy <= 25,
              CLLocationCoordinate2DIsValid(point.coordinate) else {
            anchor = nil
            latest = nil
            return .interrupted
        }
        guard let previous = latest, let origin = anchor,
              time.timeIntervalSince(previous.timestamp) <= 15 else {
            anchor = point
            latest = point
            return .anchor
        }
        let interval = time.timeIntervalSince(previous.timestamp)
        let recentTravel = point.distance(from: previous) - previous.horizontalAccuracy - point.horizontalAccuracy
        let meters = point.distance(from: origin)
        guard recentTravel <= 9 * interval,
              meters <= origin.horizontalAccuracy + point.horizontalAccuracy
                  || meters <= 9 * time.timeIntervalSince(origin.timestamp) else {
            anchor = nil
            latest = nil
            return .interrupted
        }
        latest = point
        guard meters > origin.horizontalAccuracy + point.horizontalAccuracy else { return .stationary }
        anchor = point
        return .movement(meters)
    }
}
