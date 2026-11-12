import CoreLocation
import SwiftUI
import UIKit

@main
struct RunStateApp: App {
    @StateObject private var tracker = RunTracker()
    var body: some Scene { WindowGroup { RunScreen(tracker: tracker) } }
}

@MainActor
private struct RunScreen: View {
    @ObservedObject var tracker: RunTracker
    @StateObject private var countdown: RunCountIn
    @Environment(\.openURL) private var openURL
    @Environment(\.scenePhase) private var scenePhase
    @State private var opening: Bool
    private enum RunSheet: String, Identifiable {
        case history, map, settings
        var id: Self { self }
    }

    @State private var sheet: RunSheet?
    @State private var spotifyUnavailable = false
    @State private var energy: String?
    @State private var startMessage: String?

    init(tracker: RunTracker) {
        self.tracker = tracker
        _opening = State(initialValue: tracker.journal.active == nil && tracker.canWrite)
        _countdown = StateObject(wrappedValue: RunCountIn(speak: tracker.voice.countdownCue, silence: tracker.voice.stop))
    }

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            if tracker.journal.active != nil {
                ActiveRunView(tracker: tracker, onResume: resume, onFinish: finish)
            } else if opening {
                RunOpeningView { opening = false }
            } else if let seconds = countdown.seconds {
                RunCountdownView(seconds: seconds, cadenceActive: countdown.cadenceActive,
                                 onStartNow: countdown.startNow, onCancel: countdown.cancel)
            } else {
                RunStartView(primaryTitle: primaryTitle, locationStatus: startStatus, canStart: tracker.canWrite,
                             selectedEnergy: energy, onSelectEnergy: { energy = $0 },
                             onPrimary: prepareStart, onHistory: { sheet = .history },
                             onOpenMap: { sheet = .map }, onOpenSettings: { sheet = .settings },
                             onOpenSpotify: openSpotify)
            }
        }
        .preferredColorScheme(.dark)
        .onChange(of: scenePhase) { phase in
            if phase != .active && countdown.seconds != nil {
                countdown.cancel()
                startMessage = "Countdown canceled. Start again when ready."
            }
        }
        .onChange(of: tracker.authorization) { _ in
            startMessage = nil
            if !tracker.hasLocationPermission { countdown.cancel() }
        }
        .sheet(item: $sheet) { destination in
            switch destination {
            case .history: history
            case .map: RunLiveMapView(tracker: tracker)
            case .settings: RunSettingsView(tracker: tracker)
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

    private var history: some View {
        NavigationStack {
            List {
                if tracker.journal.history.isEmpty {
                    Text("Your saved runs will appear here.").foregroundStyle(.secondary)
                }
                ForEach(tracker.journal.history.reversed()) { run in
                    VStack(alignment: .leading, spacing: 5) {
                        Text(run.startedAt.formatted(date: .abbreviated, time: .shortened))
                        Text("\((run.meters / RunDistance.metersPerMile).formatted(.number.precision(.fractionLength(2)))) mi · \(ActiveRunView.clockText(run.activeSeconds))")
                            .monospacedDigit()
                        if let selection = run.preRunEnergy { Text("Starting energy · " + selection).font(.footnote) }
                    }
                }
            }
            .navigationTitle("Saved runs")
            .toolbar {
                ToolbarItem(placement: .confirmationAction) { Button("Done") { sheet = nil } }
            }
        }
        .preferredColorScheme(.dark)
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
        tracker.finish()
        if tracker.journal.active == nil { sheet = .history }
    }
}
