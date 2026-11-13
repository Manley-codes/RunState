import CoreLocation
import SwiftUI
import UIKit

@main
@MainActor
struct RunStateApp: App {
    @StateObject private var tracker = RunTracker()
    @StateObject private var spotify = RunStateApp.makeSpotifyObservation()

    var body: some Scene { WindowGroup { RunScreen(tracker: tracker, spotify: spotify) } }

    private static func makeSpotifyObservation() -> SpotifyObservation {
        #if DEBUG && targetEnvironment(simulator)
        let environment = ProcessInfo.processInfo.environment
        if let journal = environment["RUNSTATE_TEST_JOURNAL"], let id = UUID(uuidString: journal),
           let preferences = UserDefaults(suiteName: "uitest." + id.uuidString) {
            return SpotifyObservation(preferences: preferences,
                                      source: SimulatorSpotifySource(offersTrack: environment["RUNSTATE_TEST_SPOTIFY_TRACK"] == "1"),
                                      bundledClientID: nil)
        }
        #endif
        return SpotifyObservation()
    }
}

#if DEBUG && targetEnvironment(simulator)
@MainActor
private final class SimulatorSpotifySource: SpotifyNowPlayingSource {
    var onEvent: ((SpotifyObservationEvent) -> Void)?
    let offersTrack: Bool
    private var authorized = false

    init(offersTrack: Bool) { self.offersTrack = offersTrack }

    func authorize(clientID: String) {
        guard offersTrack else {
            onEvent?(.unavailable("Spotify is unavailable in this simulator."))
            return
        }
        authorized = true
        onEvent?(.authorized)
    }

    func resume() {
        guard authorized else { return }
        onEvent?(.playing(title: "Morning Circuit", artist: "RunState Test Artist",
                          trackURL: URL(string: "https://open.spotify.com/track/0123456789ABCDEFGHIJKL")!))
    }
    func suspend() {}
    func forget() { authorized = false }
    func handleCallback(_ url: URL) -> Bool { false }
}
#endif

extension RunJournal {
    func shoeMileageText(for id: UUID) -> String {
        miles(for: id).formatted(.number.precision(.fractionLength(1))) + " MI"
    }
}

@MainActor
private struct RunScreen: View {
    @ObservedObject var tracker: RunTracker
    @ObservedObject var spotify: SpotifyObservation
    @StateObject private var countdown: RunCountIn
    #if RUNSTATE_FIELD_TOOLS
    @StateObject private var fieldRecognition = FieldRecognitionTest()
    #endif
    @Environment(\.openURL) private var openURL
    @Environment(\.scenePhase) private var scenePhase
    @State private var opening: Bool
    private enum RunSheet: Hashable, Identifiable {
        case history, map, settings, shoes
        case completion(UUID)
        var id: Self { self }
    }

    @State private var sheet: RunSheet?
    @State private var spotifyUnavailable = false
    @State private var energy: String?
    @State private var startMessage: String?

    init(tracker: RunTracker, spotify: SpotifyObservation) {
        self.tracker = tracker
        self.spotify = spotify
        _opening = State(initialValue: tracker.journal.active == nil && tracker.canWrite)
        _countdown = StateObject(wrappedValue: RunCountIn(speak: tracker.voice.countdownCue, silence: tracker.voice.stop))
    }

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            if tracker.journal.active != nil {
                ActiveRunView(tracker: tracker, spotify: spotify, onResume: resume, onFinish: finish)
            } else if opening {
                RunOpeningView { opening = false }
            } else if let seconds = countdown.seconds {
                RunCountdownView(seconds: seconds, cadenceActive: countdown.cadenceActive,
                                 onStartNow: countdown.startNow, onCancel: countdown.cancel)
            } else {
                let shoe = tracker.journal.selectedShoe
                RunStartView(primaryTitle: primaryTitle, locationStatus: startStatus, canStart: tracker.canWrite,
                             selectedEnergy: energy, onSelectEnergy: { energy = $0 },
                             onPrimary: prepareStart, onHistory: { sheet = .history },
                             onOpenMap: { sheet = .map }, onOpenSettings: { sheet = .settings },
                             onOpenSpotify: openSpotify,
                             selectedShoeName: shoe?.displayName,
                             shoeMileageText: shoe.map { tracker.journal.shoeMileageText(for: $0.id) },
                             onOpenShoes: { sheet = .shoes })
            }
        }
        .preferredColorScheme(.dark)
        .onAppear { spotify.setActive(scenePhase == .active) }
        .onOpenURL { url in _ = spotify.handleCallback(url) }
        .onChange(of: scenePhase) { phase in
            #if RUNSTATE_FIELD_TOOLS
            fieldRecognition.sceneChanged(isBackground: phase == .background)
            #endif
            spotify.setActive(phase == .active)
            if phase == .background { tracker.checkpointForBackground() }
            if phase != .active && countdown.seconds != nil {
                countdown.cancel()
                startMessage = "Countdown canceled. Start again when ready."
            }
        }
        #if RUNSTATE_FIELD_TOOLS
        .onChange(of: tracker.journal.active?.id) { runID in
            if runID == nil { fieldRecognition.stop() }
        }
        #endif
        .onChange(of: tracker.authorization) { _ in
            startMessage = nil
            if !tracker.hasLocationPermission { countdown.cancel() }
        }
        .sheet(item: $sheet) { destination in
            switch destination {
            case .history: RunHistoryView(tracker: tracker)
            case .completion(let runID):
                NavigationStack {
                    RunCompletionView(tracker: tracker, runID: runID)
                        .toolbar {
                            ToolbarItem(placement: .confirmationAction) {
                                Button("Done") { sheet = nil }
                            }
                        }
                }
                .preferredColorScheme(.light)
            case .map: RunLiveMapView(tracker: tracker)
            case .settings:
                RunSettingsView(tracker: tracker, spotify: spotify)
                    #if RUNSTATE_FIELD_TOOLS
                    .environmentObject(fieldRecognition)
                    #endif
            case .shoes: RunShoesView(tracker: tracker)
            }
        }
        .alert("Spotify couldn’t open", isPresented: $spotifyUnavailable) {
            Button("View in App Store") {
                openURL(URL(string: "https://apps.apple.com/app/id324684580")!)
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("Check that Spotify is installed on this iPhone.")
        }
    }

    private func openSpotify() {
        // Open the app without sending a track or playback command.
        openURL(URL(string: "spotify:")!) { opened in
            spotifyUnavailable = !opened
        }
    }

    private var primaryTitle: String {
        if tracker.hasLocationPermission { return "Start run" }
        return tracker.authorization == .notDetermined ? "Enable location" : "Open location settings"
    }

    private var startStatus: String {
        if let message = tracker.errorMessage ?? startMessage { return message }
        return tracker.hasLocationPermission ? "Location enabled" : "Location access needed"
    }

    private func requestLocation() {
        if tracker.authorization == .notDetermined { tracker.requestPermission() }
        else if let settings = URL(string: UIApplication.openSettingsURLString) { openURL(settings) }
    }

    private func prepareStart() {
        guard tracker.canWrite, tracker.journal.active == nil else { return }
        guard tracker.hasLocationPermission else { requestLocation(); return }
        startMessage = nil
        let selection = energy
        countdown.begin {
            guard UIApplication.shared.applicationState == .active, tracker.journal.active == nil else { return }
            tracker.startOrResume(preRunEnergy: selection)
            if tracker.running {
                energy = nil
                tracker.voice.announceStart()
            }
        }
    }

    private func resume() {
        if tracker.hasLocationPermission { tracker.startOrResume() }
        else { requestLocation() }
    }

    private func finish() {
        guard let runID = tracker.journal.active?.id else { return }
        tracker.finish()
        if tracker.journal.active == nil, tracker.journal.history.contains(where: { $0.id == runID }) {
            sheet = .completion(runID)
        }
    }
}
