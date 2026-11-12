import MapKit
import SwiftUI
import UIKit

struct RunLiveMapView: View {
    @ObservedObject var tracker: RunTracker
    @Environment(\.dismiss) private var dismiss
    @Environment(\.openURL) private var openURL
    @Environment(\.scenePhase) private var scenePhase
    @State private var locationServicesOff = false

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                ForegroundMap(showsPosition: scenePhase == .active && tracker.hasLocationPermission)
                locationNotice
            }
            .navigationTitle("Live map")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() } }
            }
        }
        .onAppear { tracker.refreshLocationAuthorization() }
        .onChange(of: scenePhase) { phase in
            if phase == .active { tracker.refreshLocationAuthorization() }
        }
        .task(id: scenePhase == .active && tracker.authorization == .denied) {
            guard scenePhase == .active, tracker.authorization == .denied else {
                locationServicesOff = false
                return
            }
            // A disabled system switch and a denied app permission both report .denied.
            let enabled = await Task.detached(priority: .utility) {
                CLLocationManager.locationServicesEnabled()
            }.value
            if !Task.isCancelled { locationServicesOff = !enabled }
        }
    }

    @ViewBuilder private var locationNotice: some View {
        switch tracker.authorization {
        case .notDetermined:
            notice("Allow RunState to show your position on the map.", action: "Enable location",
                   perform: tracker.requestPermission)
        case .denied where locationServicesOff:
            notice("Turn on Location Services in iPhone Settings under Privacy & Security.")
        case .denied:
            notice("Location access for RunState is off.", action: "Open Settings", perform: openSettings)
        case .restricted:
            notice("Location access is restricted on this iPhone.")
        case .authorizedAlways, .authorizedWhenInUse:
            if tracker.accuracyAuthorization == .reducedAccuracy {
                notice("Your location is approximate. Enable Precise Location for a closer view.",
                       action: "Open Settings", perform: openSettings)
            }
        @unknown default:
            notice("Location access is unavailable.")
        }
    }

    private func notice(_ message: String, action: String? = nil,
                        perform: @escaping () -> Void = {}) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(message).font(.subheadline)
            if let action {
                Button(action, action: perform).frame(minHeight: 44)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal).padding(.vertical, 8)
        .background(.regularMaterial)
    }

    private func openSettings() {
        if let url = URL(string: UIApplication.openSettingsURLString) { openURL(url) }
    }
}

/// MapKit owns the preview's location dot. Opening this view never starts the run tracker.
private struct ForegroundMap: UIViewRepresentable {
    let showsPosition: Bool

    final class Coordinator {
        weak var recenter: MKUserTrackingButton?
    }

    func makeCoordinator() -> Coordinator { Coordinator() }

    func makeUIView(context: Context) -> MKMapView {
        let map = MKMapView()
        let button = MKUserTrackingButton(mapView: map)
        button.translatesAutoresizingMaskIntoConstraints = false
        button.accessibilityIdentifier = "liveMapRecenter"
        button.isHidden = true
        map.addSubview(button)
        NSLayoutConstraint.activate([
            button.leadingAnchor.constraint(equalTo: map.safeAreaLayoutGuide.leadingAnchor, constant: 12),
            button.topAnchor.constraint(equalTo: map.safeAreaLayoutGuide.topAnchor, constant: 12),
            button.widthAnchor.constraint(equalToConstant: 44),
            button.heightAnchor.constraint(equalToConstant: 44)
        ])
        context.coordinator.recenter = button
        return map
    }

    func updateUIView(_ map: MKMapView, context: Context) {
        context.coordinator.recenter?.isHidden = !showsPosition
        // Re-enable following only when location access starts. Other SwiftUI updates must
        // leave the map where the runner panned it until they use the native recenter control.
        guard map.showsUserLocation != showsPosition else { return }
        if showsPosition {
            map.showsUserLocation = true
            map.setUserTrackingMode(.follow, animated: false)
        } else {
            Self.stopShowingPosition(on: map)
        }
    }

    static func dismantleUIView(_ map: MKMapView, coordinator: Coordinator) {
        coordinator.recenter?.mapView = nil
        stopShowingPosition(on: map)
    }

    private static func stopShowingPosition(on map: MKMapView) {
        map.setUserTrackingMode(.none, animated: false)
        map.showsUserLocation = false
    }
}
