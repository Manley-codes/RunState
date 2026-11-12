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
                Section {
                    Toggle("Save route in History", isOn: $tracker.saveRoutes)
                        .accessibilityIdentifier("saveRunRoute")
                } header: {
                    Text("Run history")
                } footer: {
                    Text("For new runs, save GPS points on this iPhone to draw your route. You can delete a run and its route together in History. Distance and pace work with this off.")
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
                Section {
                    NavigationLink("Privacy & data") {
                        List {
                            Section("Saved on this iPhone") {
                                Text("RunState saves run totals, available mile splits, your check-in answers and your shoe choices on this device. Run History is excluded from device backups.")
                                Text("GPS measures distance even when Save route in History is off. Turning it on keeps the path for runs you start afterward.")
                            }
                            Section("Maps and music") {
                                Text("History draws saved routes on this iPhone. The live street map uses Apple Maps to load the area you view.")
                                Text("The Spotify shortcut opens Spotify. This iPhone build does not collect your listening history or send your runs to the reflection service.")
                            }
                            Section("Remove a saved run") {
                                Text("In History, open the run and tap the trash button. After you confirm, its check-in, route and splits are removed and its distance comes off the shoe’s total.")
                            }
                        }
                        .navigationTitle("Privacy & data")
                        .navigationBarTitleDisplayMode(.inline)
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
