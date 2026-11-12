import Combine
import CoreLocation
import Foundation

final class RunTracker: NSObject, ObservableObject, CLLocationManagerDelegate {
    @Published private(set) var journal = RunJournal()
    @Published private(set) var running = false
    @Published private(set) var authorization: CLAuthorizationStatus = .notDetermined
    @Published private(set) var accuracyAuthorization: CLAccuracyAuthorization = .reducedAccuracy
    @Published var mileAnnouncementsEnabled: Bool {
        didSet { preferences.set(mileAnnouncementsEnabled, forKey: Self.mileAnnouncementsKey) }
    }
    @Published var saveRoutes: Bool {
        didSet { preferences.set(saveRoutes, forKey: Self.saveRoutesKey) }
    }
    @Published private(set) var gpsStatus = ""
    @Published private(set) var errorMessage: String?
    private(set) var canWrite = true
    let reflectionConnection: ReflectionConnection
    let reflectionService: any WrittenReflectionServing
    @Published var reflectionWork = WrittenReflectionWork()
    private var connectionChanges: AnyCancellable?

    private static let mileAnnouncementsKey = "run.mileAnnouncementsEnabled"
    private static let saveRoutesKey = "run.saveRoutes"
    private let preferences: UserDefaults
    private let gps = CLLocationManager()
    let voice = RunVoice()
    private let journalURL: URL
    private var distance = RunDistance()
    private let clock = ContinuousClock()
    private var timerAnchor: ContinuousClock.Instant?
    private var segmentHasCompleteTime = false
    private var segmentStartActiveSeconds: TimeInterval = 0

    override convenience init() {
        #if DEBUG && targetEnvironment(simulator)
        let environment = ProcessInfo.processInfo.environment
        if let value = environment["RUNSTATE_TEST_JOURNAL"], let id = UUID(uuidString: value) {
            let folder = URL.applicationSupportDirectory.appending(path: "UITests").appending(path: id.uuidString)
            let url = folder.appending(path: "runs.json")
            // A relaunch in one test reuses its journal. Other tests always start independently.
            if !FileManager.default.fileExists(atPath: url.path), let seed = environment["RUNSTATE_TEST_SEED"] {
                do {
                    let journal = try JSONDecoder().decode(RunJournal.self, from: Data(seed.utf8))
                    try journal.save(to: url)
                } catch {
                    preconditionFailure("Invalid simulator test journal: \(error)")
                }
            }
            let preferences = UserDefaults(suiteName: "uitest." + id.uuidString)!
            let vault = KeychainReflectionVault(service: "runstate.uitest." + id.uuidString)
            let connection = ReflectionConnection(preferences: preferences, vault: vault)
            let fixture = environment["RUNSTATE_TEST_WRITTEN_ANSWERS"].flatMap {
                try? JSONDecoder().decode(WrittenReflectionAnswers.self, from: Data($0.utf8))
            }
            self.init(preferences: preferences, journalURL: url, reflectionConnection: connection,
                      reflectionService: SimulatorWrittenReflectionService(answers: fixture))
            return
        }
        #endif
        self.init(preferences: .standard, journalURL: URL.applicationSupportDirectory.appending(path: "FieldTest/runs.json"))
    }

    init(preferences: UserDefaults, journalURL: URL, reflectionConnection: ReflectionConnection? = nil,
         reflectionService: any WrittenReflectionServing = ReflectionService()) {
        self.preferences = preferences
        self.journalURL = journalURL
        self.reflectionConnection = reflectionConnection ?? ReflectionConnection(preferences: preferences)
        self.reflectionService = reflectionService
        mileAnnouncementsEnabled = preferences.object(forKey: Self.mileAnnouncementsKey) as? Bool ?? true
        saveRoutes = preferences.bool(forKey: Self.saveRoutesKey)
        super.init()
        connectionChanges = self.reflectionConnection.$revision.dropFirst().sink { [weak self] _ in
            self?.cancelWrittenReflectionWork()
        }
        refreshLocationAuthorization()
        do {
            journal = try RunJournal.load(from: journalURL)
            if journal.active?.pausedWithCompleteTime == false {
                // A running checkpoint may predate the last movement before termination.
                // Keep its recorded totals and route, but there is no complete split timeline.
                journal.active?.splitProgress = nil
            }
        } catch {
            canWrite = false
            errorMessage = "RunState could not open the saved runs."
        }
        gps.delegate = self
        gps.activityType = .fitness
        gps.desiredAccuracy = kCLLocationAccuracyBest
        gps.distanceFilter = kCLDistanceFilterNone
        gps.pausesLocationUpdatesAutomatically = false
        gps.showsBackgroundLocationIndicator = true
        voice.onFailure = { [weak self] in self?.errorMessage = "The mile announcement audio was interrupted." }
    }

    var hasLocationPermission: Bool { authorization == .authorizedAlways || authorization == .authorizedWhenInUse }
    func requestPermission() {
        if gps.authorizationStatus == .notDetermined { gps.requestWhenInUseAuthorization() }
    }

    func addShoe(brand: String, model: String, nickname: String? = nil, id: UUID = UUID()) throws {
        try saveJournalChange { try $0.addShoe(brand: brand, model: model, nickname: nickname, id: id) }
    }

    func selectShoe(_ id: UUID?) throws {
        try saveJournalChange { try $0.selectShoe(id) }
    }

    func archiveShoe(_ id: UUID) throws -> ShoeArchiveUndo {
        try saveJournalChange { try $0.archiveShoe(id) }
    }

    func undoArchive(_ receipt: ShoeArchiveUndo) throws {
        try saveJournalChange { try $0.undoArchive(receipt) }
    }

    func restoreShoe(_ id: UUID) throws {
        try saveJournalChange { try $0.restoreShoe(id) }
    }

    func saveCheckIn(for id: UUID, energy: PostRunEnergy?, effort: Int?) throws {
        try saveJournalChange { try $0.saveCheckIn(for: id, energy: energy, effort: effort) }
    }

    /// Earlier builds recorded the absolute instant. Only the runner can identify the missing local zone.
    func confirmStartTimeZone(for id: UUID, identifier: String) throws {
        guard identifier == "UTC" || TimeZone.knownTimeZoneIdentifiers.contains(identifier) else { throw RunStartZoneError.invalid }
        guard journal.active?.id != id, let run = journal.history.first(where: { $0.id == id && $0.finishedAt != nil })
        else { throw SavedRunError.notSaved }
        if run.startTimezoneID == identifier { return }
        guard run.startTimezoneID == nil, run.writtenReflection == nil else { throw RunStartZoneError.alreadyRecorded }
        try saveJournalChange { next in
            guard let index = next.history.firstIndex(where: { $0.id == id }) else { throw SavedRunError.notSaved }
            next.history[index].startTimezoneID = identifier
        }
    }

    func deleteSavedRun(_ id: UUID) throws {
        try saveJournalChange { try $0.deleteSavedRun(id) }
        cancelWrittenReflectionWork(for: id)
    }

    func storeWrittenReflection(_ value: RunWrittenReflection, for id: UUID, replacing expected: PendingWrittenReflection?) throws {
        try saveJournalChange { journal in
            guard let index = journal.history.firstIndex(where: { $0.id == id && $0.finishedAt != nil }),
                  journal.history[index].writtenReflection?.saved == nil,
                  journal.history[index].writtenReflection?.pending == expected else { throw SavedRunError.notSaved }
            journal.history[index].writtenReflection = value
        }
    }

    func discardShortPausedRun(_ id: UUID) {
        guard !running else { return }
        do {
            try saveJournalChange { try $0.discardShortPausedRun(id) }
            errorMessage = nil
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    private func saveJournalChange<Result>(_ change: (inout RunJournal) throws -> Result) throws -> Result {
        guard canWrite else { throw SavedRunError.storage }
        var next = journal
        let result = try change(&next)
        do {
            try next.save(to: journalURL)
        } catch {
            throw SavedRunError.storage
        }
        journal = next
        return result
    }

    func startOrResume(preRunEnergy: String? = nil) {
        guard !running, hasLocationPermission, canWrite else { return }
        let now = Date()
        errorMessage = nil
        let completeTime = journal.active == nil || journal.active?.pausedWithCompleteTime == true
        var next = journal
        if next.active == nil {
            next.beginRun(at: now, preRunEnergy: preRunEnergy, saveRoute: saveRoutes)
        }
        // A crash after resuming must never leave an older pause eligible for discard.
        next.active?.pausedWithCompleteTime = false
        guard store(next) else { return }
        journal = next
        segmentHasCompleteTime = completeTime
        segmentStartActiveSeconds = journal.active?.activeSeconds ?? 0
        journal.active?.splitProgress?.startSegment(at: segmentStartActiveSeconds)
        timerAnchor = clock.now
        distance.startSegment(at: Date())
        gpsStatus = "Finding GPS…"
        running = true
        gps.allowsBackgroundLocationUpdates = true
        gps.startUpdatingLocation()
    }

    func pause() {
        guard running else { return }
        voice.stopCountdownCue()
        checkpoint(at: clock.now)
        journal.active?.pausedWithCompleteTime = segmentHasCompleteTime
        let pausedAt = journal.active?.activeSeconds ?? 0
        journal.active?.splitProgress?.endSegment(at: pausedAt)
        timerAnchor = nil
        running = false
        gps.stopUpdatingLocation()
        gps.allowsBackgroundLocationUpdates = false
        _ = store(journal)
    }

    func finish() {
        guard canWrite, journal.active != nil else { return }
        if running { pause() }
        var next = journal
        next.finish(at: Date())
        if store(next) {
            journal = next
            errorMessage = nil
        }
    }

    var activeSeconds: TimeInterval {
        (journal.active?.activeSeconds ?? 0) + elapsedSinceCheckpoint(at: clock.now)
    }

    private func elapsedSinceCheckpoint(at instant: ContinuousClock.Instant) -> TimeInterval {
        guard let timerAnchor else { return 0 }
        let delta = timerAnchor.duration(to: instant).components
        return Double(delta.seconds) + Double(delta.attoseconds) / 1e18
    }

    private func checkpoint(at instant: ContinuousClock.Instant) {
        let elapsed = elapsedSinceCheckpoint(at: instant)
        journal.active?.activeSeconds += elapsed
        timerAnchor = instant
    }

    func checkpointForBackground() {
        guard running else { return }
        checkpoint(at: clock.now)
        _ = store(journal)
    }

    func refreshLocationAuthorization() {
        authorization = gps.authorizationStatus
        accuracyAuthorization = gps.accuracyAuthorization
        if running && !hasLocationPermission {
            pause()
            errorMessage = "Run paused because location access changed."
        }
    }

    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        refreshLocationAuthorization()
    }

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard running, let previousMeters = journal.active?.meters else { return }
        let tick = clock.now
        let now = Date()
        let deliverySeconds = (journal.active?.activeSeconds ?? 0) + elapsedSinceCheckpoint(at: tick)
        let observation = journal.active?.record(locations, using: &distance, deliveredAt: now,
                                                 activeAtDelivery: deliverySeconds, segmentStart: segmentStartActiveSeconds)
        if manager.accuracyAuthorization == .reducedAccuracy {
            gpsStatus = "Enable Precise Location in Settings for distance."
        } else if let observation {
            gpsStatus = observation == .tracking ? "Tracking GPS" : "Waiting for a clearer GPS signal…"
        }
        let mile = RunDistance.mileCrossed(from: previousMeters, to: journal.active?.meters ?? previousMeters)
        if mile != nil || elapsedSinceCheckpoint(at: tick) >= 15 {
            checkpoint(at: tick)
            _ = store(journal)
        }
        if let mile, mileAnnouncementsEnabled { voice.announce(mile) }
    }

    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        guard running else { return }
        distance.startSegment(at: Date())
        journal.active?.splitProgress?.interrupt()
        if (error as? CLError)?.code == .denied {
            pause()
            errorMessage = "Run paused because location is unavailable."
        } else {
            gpsStatus = "Waiting for GPS…"
        }
    }

    private func store(_ next: RunJournal) -> Bool {
        do {
            try next.save(to: journalURL)
            return true
        } catch {
            errorMessage = "RunState could not save this run. Check device storage and try again."
            return false
        }
    }
}
