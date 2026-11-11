import CoreLocation
import SwiftUI
import UIKit

@main
struct RunStateApp: App {
    @StateObject private var tracker = RunTracker()
    var body: some Scene { WindowGroup { RunScreen(tracker: tracker) } }
}

private struct RunScreen: View {
    @ObservedObject var tracker: RunTracker
    @Environment(\.openURL) private var openURL
    private let accent = Color(red: 0.8, green: 0.94, blue: 0.34)

    var body: some View {
        NavigationStack {
            List {
                if let active = tracker.journal.active {
                    Section(tracker.running ? "Active run" : "Paused run") {
                        LabeledContent("Distance", value: miles(active.meters))
                        TimelineView(.periodic(from: .now, by: 1)) { _ in
                            LabeledContent("Active time", value: duration(tracker.activeSeconds))
                        }
                        if tracker.running {
                            Text(tracker.gpsStatus).font(.footnote).foregroundStyle(.secondary)
                            Button("Pause run", action: tracker.pause)
                        } else {
                            locationControl("Resume run")
                            Button("Finish and save", action: tracker.finish)
                        }
                    }
                } else {
                    Section { locationControl("Start run") }
                }
                if let message = tracker.errorMessage {
                    Section { Text(message).accessibilityLabel("Status. " + message) }
                }
                if !tracker.journal.history.isEmpty {
                    Section("Saved runs") {
                        ForEach(tracker.journal.history.reversed()) { run in
                            VStack(alignment: .leading, spacing: 4) {
                                Text(run.startedAt.formatted(date: .abbreviated, time: .shortened))
                                Text(miles(run.meters) + " · " + duration(run.activeSeconds))
                                    .font(.subheadline.monospacedDigit()).foregroundStyle(.secondary)
                            }
                        }
                    }
                }
            }
            .navigationTitle("RunState")
            .tint(accent)
            .preferredColorScheme(.dark)
        }
    }

    @ViewBuilder private func locationControl(_ title: String) -> some View {
        if tracker.hasLocationPermission {
            Button(title, action: tracker.startOrResume).disabled(!tracker.canWrite)
        } else if tracker.authorization == .notDetermined {
            Button("Enable location", action: tracker.requestPermission)
        } else {
            Button("Open location settings") {
                if let url = URL(string: UIApplication.openSettingsURLString) { openURL(url) }
            }
        }
    }

    private func miles(_ meters: Double) -> String {
        (meters / RunDistance.metersPerMile).formatted(.number.precision(.fractionLength(2))) + " mi"
    }

    private func duration(_ seconds: TimeInterval) -> String {
        Duration.seconds(Int(seconds)).formatted(.time(pattern: .minuteSecond))
    }
}
