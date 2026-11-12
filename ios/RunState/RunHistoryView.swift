import SwiftUI

struct RunHistoryView: View {
    @ObservedObject var tracker: RunTracker
    @Environment(\.dismiss) private var dismiss
    @State private var path: [UUID] = []

    var body: some View {
        NavigationStack(path: $path) {
            List {
                if tracker.journal.history.isEmpty {
                    Text("Your saved runs will appear here.")
                        .foregroundStyle(.secondary)
                }
                ForEach(tracker.journal.history.reversed()) { run in
                    NavigationLink(value: run.id) {
                        VStack(alignment: .leading, spacing: 5) {
                            Text(run.startedAt.formatted(date: .abbreviated, time: .shortened))
                                .font(.subheadline)
                                .foregroundStyle(.secondary)
                            Text("\(distanceText(run)) · \(ActiveRunView.clockText(run.activeSeconds))")
                                .font(.headline)
                                .monospacedDigit()
                            if run.hasPartialDistance(at: run.activeSeconds) {
                                Text("Partial GPS distance").font(.footnote).foregroundStyle(.secondary)
                            }
                            if let energy = run.preRunEnergy {
                                Text("Starting energy · " + energy).font(.footnote)
                            }
                            if let shoe = tracker.journal.shoe(for: run.shoePairID) {
                                Text("Shoes · " + shoe.fullName).font(.footnote)
                            }
                        }
                        .padding(.vertical, 3)
                    }
                    .accessibilityIdentifier("history.run." + run.id.uuidString)
                }
            }
            .navigationTitle("Saved runs")
            .navigationDestination(for: UUID.self) { id in
                SavedRunDetail(tracker: tracker, runID: id) { path.removeAll() }
            }
            .toolbar {
                ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() } }
            }
        }
        .preferredColorScheme(.dark)
    }
}

private struct SavedRunDetail: View {
    @ObservedObject var tracker: RunTracker
    let runID: UUID
    let onDelete: () -> Void
    @State private var confirmingDeletion = false
    @State private var deletionError: String?

    private var run: RecordedRun? {
        tracker.journal.history.first { $0.id == runID }
    }

    var body: some View {
        List {
            if let run {
                Section {
                    LabeledContent("Distance", value: distanceText(run))
                    LabeledContent("Active time", value: ActiveRunView.clockText(run.activeSeconds))
                    let pace = ActiveRunView.averagePaceText(for: run, at: run.activeSeconds)
                    LabeledContent("Average pace", value: pace ?? "Unavailable")
                } header: {
                    Text(run.startedAt.formatted(date: .complete, time: .shortened))
                        .textCase(nil)
                } footer: {
                    if let note = run.partialDistanceNote { Text(note) }
                }
                Section("Check-in") {
                    if let energy = run.preRunEnergy {
                        LabeledContent("Starting energy", value: energy)
                    }
                    if let energy = run.postRunEnergy {
                        LabeledContent("Finishing energy", value: energy.label)
                    }
                    if let effort = run.postRunEffort {
                        LabeledContent("Effort", value: RunCompletionView.effortLabel(effort))
                    }
                    NavigationLink {
                        RunCompletionView(tracker: tracker, runID: run.id)
                    } label: {
                        Text(run.postRunEnergy == nil && run.postRunEffort == nil ? "Add post-run check-in" : "Edit post-run check-in")
                    }
                    .accessibilityIdentifier("history.checkIn")
                }
                if let shoe = tracker.journal.shoe(for: run.shoePairID) {
                    Section("Shoes") { Text(shoe.fullName) }
                }
                SavedRunSplitsView(run: run)
                if let route = run.route, route.hasPoints {
                    Section("Route") {
                        SavedRouteView(route: route)
                            .frame(height: 210)
                            .listRowInsets(EdgeInsets())
                    }
                }
            }
        }
        .navigationTitle("Run details")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                Button {
                    confirmingDeletion = true
                } label: {
                    Label("Delete run", systemImage: "trash")
                }
                .accessibilityIdentifier("history.delete")
                .disabled(run == nil || !tracker.canWrite)
                .alert("Delete this run?", isPresented: $confirmingDeletion, presenting: run) { item in
                    Button("Cancel", role: .cancel) {}
                    Button("Delete run", role: .destructive) { delete(item) }
                } message: { item in
                    let date = item.startedAt.formatted(date: .abbreviated, time: .shortened)
                    Text("This permanently removes the \(distanceText(item)) run from \(date) and its saved details from this iPhone.")
                }
            }
        }
        .alert("Couldn’t delete run", isPresented: Binding(
            get: { deletionError != nil },
            set: { if !$0 { deletionError = nil } }
        )) {
            Button("OK") {}
        } message: {
            Text(deletionError ?? "")
        }
    }

    private func delete(_ item: RecordedRun) {
        do {
            try tracker.deleteSavedRun(item.id)
            onDelete()
        } catch {
            deletionError = error.localizedDescription
        }
    }
}

private func distanceText(_ run: RecordedRun) -> String {
    guard run.meters.isFinite, run.meters >= 0 else { return "Unavailable" }
    let miles = run.meters / RunDistance.metersPerMile
    return miles.formatted(.number.precision(.fractionLength(2))) + " mi"
}
