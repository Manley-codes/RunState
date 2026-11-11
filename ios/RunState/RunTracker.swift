import Combine
import CoreLocation
import Foundation

final class RunTracker: NSObject, ObservableObject, CLLocationManagerDelegate {
    @Published private(set) var journal = RunJournal()
    @Published private(set) var running = false
    @Published private(set) var authorization: CLAuthorizationStatus = .notDetermined
    @Published private(set) var gpsStatus = ""
    @Published private(set) var errorMessage: String?
    private(set) var canWrite = true

    private let gps = CLLocationManager()
    private let voice = MileVoice()
    private let journalURL = URL.applicationSupportDirectory.appending(path: "FieldTest/runs.json")
    private var distance = RunDistance()
    private let clock = ContinuousClock()
    private var timerAnchor: ContinuousClock.Instant?

    override init() {
        super.init()
        authorization = gps.authorizationStatus
        do {
            journal = try RunJournal.load(from: journalURL)
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
    func requestPermission() { gps.requestWhenInUseAuthorization() }

    func startOrResume() {
        guard !running, hasLocationPermission, canWrite else { return }
        let now = Date()
        errorMessage = nil
        if journal.active == nil {
            var next = journal
            next.active = RecordedRun(id: UUID(), startedAt: now)
            guard store(next) else { return }
            journal = next
        }
        timerAnchor = clock.now
        distance.startSegment(at: now)
        gpsStatus = "Finding GPS…"
        running = true
        gps.allowsBackgroundLocationUpdates = true
        gps.startUpdatingLocation()
    }

    func pause() {
        guard running else { return }
        checkpoint(at: clock.now)
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

    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        authorization = manager.authorizationStatus
        if running && !hasLocationPermission {
            pause()
            errorMessage = "Run paused because location access changed."
        }
    }

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard running, let previous = journal.active else { return }
        let now = Date()
        var added = 0.0
        for point in locations { added += distance.receive(point, now: now) }
        if let point = locations.last {
            if manager.accuracyAuthorization == .reducedAccuracy { gpsStatus = "Enable Precise Location in Settings for distance." }
            else if point.horizontalAccuracy < 0 || point.horizontalAccuracy > 25 { gpsStatus = "Waiting for a clearer GPS signal…" }
            else { gpsStatus = "Tracking GPS" }
        }
        journal.active?.meters += added
        let mile = RunDistance.mileCrossed(from: previous.meters, to: previous.meters + added)
        let tick = clock.now
        if mile != nil || elapsedSinceCheckpoint(at: tick) >= 15 {
            checkpoint(at: tick)
            _ = store(journal)
        }
        if let mile { voice.announce(mile) }
    }

    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        guard running else { return }
        distance.startSegment(at: Date())
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
