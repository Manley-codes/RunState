import SwiftUI

/// Every answer updates the already-saved run. Leaving without answering is valid.
struct RunCompletionView: View {
    @ObservedObject var tracker: RunTracker
    let runID: UUID

    @Environment(\.dynamicTypeSize) private var typeSize
    @State private var choosingEffort = false
    @State private var saveError: String?

    private static let ink = Color(red: 45/255, green: 56/255, blue: 50/255)
    private static let muted = Color(red: 80/255, green: 88/255, blue: 80/255)
    private static let green = Color(red: 66/255, green: 91/255, blue: 60/255)
    private static let surface = Color(red: 240/255, green: 244/255, blue: 244/255)

    var body: some View {
        ScrollView {
            if let run = tracker.journal.history.first(where: { $0.id == runID }) {
                VStack(alignment: .leading, spacing: 24) {
                    recap(run)
                    Divider()
                    energySection(run)
                    Divider()
                    effortSection(run)
                }
                .padding(20)
                .frame(maxWidth: 550, alignment: .leading)
                .frame(maxWidth: .infinity)
            } else {
                Text("This run is no longer in History.").padding()
            }
        }
        .background(LinearGradient(colors: [.white, Self.surface],
                                   startPoint: .top, endPoint: .bottom).ignoresSafeArea())
        .foregroundStyle(Self.ink)
        .tint(Self.green)
        .environment(\.colorScheme, .light)
        .toolbarColorScheme(.light, for: .navigationBar)
        .toolbarBackground(Color.white, for: .navigationBar)
        .toolbarBackground(.visible, for: .navigationBar)
        .navigationTitle("Run complete")
        .navigationBarTitleDisplayMode(.inline)
        .alert("Couldn’t save your check-in", isPresented: Binding(
            get: { saveError != nil }, set: { if !$0 { saveError = nil } }
        )) {
            Button("OK") { saveError = nil }
        } message: {
            Text(saveError ?? "")
        }
    }

    private func recap(_ run: RecordedRun) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(run.startedAt.formatted(date: .abbreviated, time: .shortened))
                .font(.subheadline).foregroundStyle(Self.muted)
            HStack(alignment: .lastTextBaseline, spacing: 6) {
                Text(run.meters / RunDistance.metersPerMile, format: .number.precision(.fractionLength(2)))
                    .font(.custom("BarlowCondensed-Medium", size: 58, relativeTo: .largeTitle))
                    .monospacedDigit()
                Text("mi").font(.title3)
            }
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("Distance")
            .accessibilityValue("\((run.meters / RunDistance.metersPerMile).formatted(.number.precision(.fractionLength(2)))) miles")
            let layout = typeSize.isAccessibilitySize ? AnyLayout(VStackLayout(alignment: .leading, spacing: 12))
                : AnyLayout(HStackLayout(alignment: .top, spacing: 32))
            layout {
                metric("Active time", ActiveRunView.clockText(run.activeSeconds))
                metric("Average pace", ActiveRunView.averagePaceText(for: run, at: run.activeSeconds) ?? "—")
            }
            if let note = run.partialDistanceNote { Text(note).font(.footnote).foregroundStyle(Self.muted) }
        }
    }

    private func metric(_ name: String, _ value: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(name).font(.caption).foregroundStyle(Self.muted)
            Text(verbatim: value).font(.headline).monospacedDigit()
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(name)
        .accessibilityValue(value == "—" ? "Unavailable" : value.replacingOccurrences(of: " /mi", with: " per mile"))
    }

    private func energySection(_ run: RecordedRun) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("How did you feel at the finish?").font(.title3.bold()).accessibilityAddTraits(.isHeader)
            let layout = typeSize >= .xxLarge ? AnyLayout(VStackLayout(spacing: 8)) : AnyLayout(HStackLayout(spacing: 8))
            layout {
                energyCard(.spent, detail: "Low energy", surface: Color(red: 91/255, green: 107/255, blue: 106/255), run: run)
                energyCard(.feelingGood, detail: "Steady energy", surface: Color(red: 76/255, green: 98/255, blue: 88/255), run: run)
                energyCard(.poweredUp, detail: "High energy", surface: Color(red: 224/255, green: 186/255, blue: 120/255), run: run)
            }
            HStack {
                Button {
                    save(energy: .notSure, effort: run.postRunEffort)
                } label: {
                    HStack {
                        if run.postRunEnergy == .notSure { Image(systemName: "checkmark") }
                        Text(PostRunEnergy.notSure.label)
                    }
                    .frame(minHeight: 44)
                }
                .accessibilityIdentifier("completion.energy.notSure")
                .accessibilityAddTraits(run.postRunEnergy == .notSure ? .isSelected : [])
                if run.postRunEnergy != nil {
                    Spacer()
                    Button("Clear energy") { save(energy: nil, effort: run.postRunEffort) }
                        .frame(minHeight: 44)
                }
            }
            .font(.subheadline)
        }
    }

    private func energyCard(_ energy: PostRunEnergy, detail: String, surface: Color, run: RecordedRun) -> some View {
        let selected = run.postRunEnergy == energy
        return Button {
            save(energy: energy, effort: run.postRunEffort)
        } label: {
            VStack(spacing: 5) {
                Text(energy.label).font(.subheadline.bold())
                Text(detail).font(.caption)
            }
            .multilineTextAlignment(.center)
            .frame(maxWidth: .infinity, minHeight: typeSize >= .xxLarge ? 68 : 88)
            .padding(.horizontal, 3)
            .foregroundStyle(energy == .poweredUp ? Self.ink : .white)
            .background(surface, in: RoundedRectangle(cornerRadius: 4))
            .overlay(alignment: .topTrailing) {
                if selected {
                    Image(systemName: "checkmark.circle.fill")
                        .font(.footnote).symbolRenderingMode(.palette)
                        .foregroundStyle(.white, Self.ink)
                        .padding(4)
                }
            }
        }
        .buttonStyle(.plain)
        .accessibilityLabel(energy.label)
        .accessibilityValue(detail)
        .accessibilityAddTraits(selected ? .isSelected : [])
        .accessibilityIdentifier("completion.energy." + energy.rawValue)
    }

    private func effortSection(_ run: RecordedRun) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("How hard was the whole run?").font(.title3.bold()).accessibilityAddTraits(.isHeader)
            Button {
                choosingEffort.toggle()
            } label: {
                HStack {
                    Text(run.postRunEffort.map(Self.effortLabel) ?? "Rate effort (optional)")
                    Spacer()
                    Image(systemName: choosingEffort ? "chevron.up" : "chevron.down").font(.caption.weight(.bold))
                }
                .frame(minHeight: 44).contentShape(Rectangle())
            }
            .accessibilityHint(choosingEffort ? "Hide the effort scale" : "Choose a rating from zero to ten")
            .accessibilityIdentifier("completion.effort")
            if choosingEffort {
                LazyVGrid(columns: [GridItem(.adaptive(minimum: typeSize.isAccessibilitySize ? 140 : 87), spacing: 8)],
                          spacing: 8) {
                    ForEach(0...10, id: \.self) { value in
                        let selected = value == run.postRunEffort
                        Button {
                            if save(energy: run.postRunEnergy, effort: value) { choosingEffort = false }
                        } label: {
                            VStack(spacing: 2) {
                                Text(verbatim: String(value)).font(.headline)
                                if let anchor = Self.effortAnchor(value) { Text(anchor).font(.caption2) }
                            }
                            .frame(maxWidth: .infinity, minHeight: 48)
                            .padding(3)
                            .foregroundStyle(selected ? .white : Self.ink)
                            .background(selected ? Self.green : .white, in: RoundedRectangle(cornerRadius: 6))
                            .overlay(RoundedRectangle(cornerRadius: 6).strokeBorder(Self.green.opacity(0.3)))
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel(Self.effortLabel(value))
                        .accessibilityAddTraits(selected ? .isSelected : [])
                        .accessibilityIdentifier("completion.effort.\(value)")
                    }
                }
                if run.postRunEffort != nil {
                    Button("Clear effort") {
                        if save(energy: run.postRunEnergy, effort: nil) { choosingEffort = false }
                    }
                    .frame(minHeight: 44)
                }
            }
        }
    }

    nonisolated private static func effortAnchor(_ rating: Int) -> String? {
        switch rating {
        case 0: return "Rest"
        case 1: return "Very, very easy"
        case 2: return "Easy"
        case 3: return "Moderate"
        case 4: return "Somewhat hard"
        case 5: return "Hard"
        case 7: return "Very hard"
        case 10: return "Maximal"
        default: return nil
        }
    }

    nonisolated static func effortLabel(_ rating: Int) -> String {
        effortAnchor(rating).map { "\(rating) · \($0)" } ?? String(rating)
    }

    @discardableResult
    private func save(energy: PostRunEnergy?, effort: Int?) -> Bool {
        do {
            try tracker.saveCheckIn(for: runID, energy: energy, effort: effort)
            return true
        } catch {
            saveError = error.localizedDescription
            return false
        }
    }
}
