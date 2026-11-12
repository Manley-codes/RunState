import CoreLocation
import SwiftUI
import UIKit

struct RunSettingsView: View {
    @ObservedObject var tracker: RunTracker
    @Environment(\.dismiss) private var dismiss
    @Environment(\.openURL) private var openURL
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        NavigationStack {
            Form {
                Section("During a run") {
                    Toggle("Announce each mile", isOn: $tracker.mileAnnouncementsEnabled)
                        .accessibilityIdentifier("mileAnnouncements")
                }
                Section("Location") {
                    LabeledContent("Access", value: authorizationLabel)
                    if tracker.hasLocationPermission {
                        LabeledContent("Precise Location", value: tracker.accuracyAuthorization == .fullAccuracy ? "On" : "Off")
                        if tracker.accuracyAuthorization == .reducedAccuracy {
                            Text("Turn on Precise Location for accurate distance and pace.")
                                .font(.footnote).foregroundStyle(.secondary)
                        }
                    }
                    if tracker.authorization == .notDetermined {
                        Button("Allow location access", action: tracker.requestPermission)
                    } else if let settings = URL(string: UIApplication.openSettingsURLString) {
                        Button("Open iPhone Settings") { openURL(settings) }
                    }
                }
            }
            .navigationTitle("Run Settings")
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
        }
        .onAppear { tracker.refreshLocationAuthorization() }
        .onChange(of: scenePhase) { phase in
            if phase == .active { tracker.refreshLocationAuthorization() }
        }
    }

    private var authorizationLabel: String {
        switch tracker.authorization {
        case .notDetermined: return "Not requested"
        case .restricted: return "Restricted"
        case .denied: return "Off"
        case .authorizedAlways: return "Always"
        case .authorizedWhenInUse: return "While using RunState"
        @unknown default: return "Unknown"
        }
    }
}
