import SwiftUI

struct WrittenReflectionView: View {
    @ObservedObject var tracker: RunTracker
    @ObservedObject private var connection: ReflectionConnection
    let run: RecordedRun
    private enum Sheet: Int, Identifiable {
        case timeZone, connection
        var id: Int { rawValue }
    }
    @State private var sheet: Sheet?

    init(tracker: RunTracker, run: RecordedRun) {
        self.tracker = tracker
        self.run = run
        _connection = ObservedObject(wrappedValue: tracker.reflectionConnection)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Written reflection")
                .font(.headline)
                .accessibilityAddTraits(.isHeader)
            switch tracker.writtenReflectionState(for: run) {
            case .saved(let text, let isTestData):
                if isTestData {
                    Text("Sample reflection").font(.caption).foregroundStyle(.secondary)
                }
                Text(verbatim: text)
                    .font(.body)
                    .lineSpacing(3)
                    .textSelection(.enabled)
                    .accessibilityIdentifier("reflection.text")
                if let voice = tracker.spokenReflectionState(for: run) {
                    listenControls(voice)
                }
            case .available:
                Text("Requesting a reflection sends this run’s summary to RunState and may use one reflection from your allowance.")
                    .font(.subheadline).foregroundStyle(.secondary)
                requestButton("Create reflection")
            case .needsStartTimeZone:
                Button("Choose this run’s start time zone") { sheet = .timeZone }
                    .font(.subheadline)
                    .accessibilityHint("Needed to request a reflection for this older run.")
                    .accessibilityIdentifier("reflection.chooseZone")
            case .notConnected:
                Text(connection.hasAccessCode
                     ? "Turn on written reflections to request one for this run."
                     : "Add your RunState access code to request a reflection for this run.")
                    .font(.subheadline).foregroundStyle(.secondary)
                Button(connection.hasAccessCode ? "Manage written reflections" : "Set up written reflections") {
                    sheet = .connection
                }
                .buttonStyle(.bordered)
                .accessibilityIdentifier("reflection.settings")
            case .preparing:
                ProgressView("Preparing your reflection…")
                Text("Your run is saved. You can return to it in History.")
                    .font(.subheadline).foregroundStyle(.secondary)
            case .retry(let message):
                Text(verbatim: message).font(.subheadline)
                requestButton("Retry reflection")
            case .unavailable(let message):
                Text(verbatim: message).font(.subheadline).foregroundStyle(.secondary)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .fixedSize(horizontal: false, vertical: true)
        .sheet(item: $sheet) { destination in
            switch destination {
            case .timeZone:
                RunStartZoneSheet(startedAt: run.startedAt) { identifier in
                    try tracker.confirmStartTimeZone(for: run.id, identifier: identifier)
                }
            case .connection:
                NavigationStack {
                    ReflectionSettingsView(connection: connection)
                        .toolbar {
                            ToolbarItem(placement: .confirmationAction) {
                                Button("Done") { sheet = nil }
                            }
                        }
                }
            }
        }
    }

    @ViewBuilder
    private func listenControls(_ voice: SpokenReflectionState) -> some View {
        if voice.playing != nil || voice.hosted == .loading {
            Button {
                tracker.stopSpokenReflection(for: run.id)
            } label: {
                Label(voice.hosted == .loading ? "Cancel voice request" : "Stop reading", systemImage: "stop.fill")
            }
            .buttonStyle(.bordered)
            .accessibilityIdentifier("reflection.voice.stop")
            if voice.hosted == .loading {
                ProgressView("Preparing RunState voice…").font(.footnote)
            }
        } else {
            if voice.hosted == .generate || voice.hosted == .replay {
                Button {
                    Task { await tracker.playSpokenReflection(for: run.id) }
                } label: {
                    Label(voice.hosted == .replay ? "Replay RunState voice" : "Listen with RunState voice",
                          systemImage: "play.fill")
                }
                .buttonStyle(.bordered)
                .accessibilityIdentifier("reflection.voice.hosted")
                if voice.hosted == .generate {
                    Text("Sends this text to RunState’s voice service. A new recording may use voice credits.")
                        .font(.footnote).foregroundStyle(.secondary)
                }
            }
            Button {
                tracker.speakOnDevice(for: run.id)
            } label: {
                Label("Read with iPhone voice", systemImage: "speaker.wave.2")
                    .frame(minHeight: 44)
            }
            .buttonStyle(.plain)
            .disabled(tracker.running)
            .accessibilityHint("Reads this reflection on your iPhone without using RunState voice credits.")
            .accessibilityIdentifier("reflection.voice.device")
        }
        if let message = voice.message {
            Text(verbatim: message).font(.footnote).foregroundStyle(.secondary)
        }
    }

    private func requestButton(_ title: String) -> some View {
        Button(title) {
            Task { await tracker.requestWrittenReflection(for: run.id) }
        }
        .buttonStyle(.bordered)
        .disabled(!tracker.canWrite)
        .accessibilityIdentifier("reflection.request")
    }
}

private struct RunStartZoneSheet: View {
    let startedAt: Date
    let confirm: (String) throws -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var selected = ZoneChoice.all.first { $0.id == TimeZone.current.identifier }
    @State private var errorMessage: String?

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    if let selected {
                        Text(selected.title).font(.headline)
                        LabeledContent("Run started", value: localStart(in: selected.zone))
                    }
                    NavigationLink {
                        RunStartZonePicker(selected: $selected)
                    } label: {
                        Text(selected == nil ? "Choose a time zone" : "Choose a different time zone")
                    }
                    .accessibilityIdentifier("reflection.browseZones")
                } header: {
                    if let selected, selected.id == TimeZone.current.identifier {
                        Text("Current iPhone time zone")
                    } else if selected != nil {
                        Text("Selected time zone")
                    }
                } footer: {
                    Text("This older run has a start time, but no saved time zone. Choose the time zone where you started the run.")
                }
                Section {
                    Button("Use this time zone") {
                        guard let selected else { return }
                        do {
                            try confirm(selected.id)
                            dismiss()
                        } catch {
                            errorMessage = error.localizedDescription
                        }
                    }
                    .disabled(selected == nil)
                    .accessibilityIdentifier("reflection.confirmZone")
                }
                if let errorMessage {
                    Text(verbatim: errorMessage).font(.subheadline)
                }
            }
            .navigationTitle("Run start time zone")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
            }
        }
    }

    private func localStart(in zone: TimeZone) -> String {
        var format = Date.FormatStyle(date: .abbreviated, time: .shortened)
        format.timeZone = zone
        return startedAt.formatted(format)
    }
}

private struct ZoneChoice: Identifiable {
    let id: String
    let zone: TimeZone
    let title: String

    static let all: [ZoneChoice] = Set(TimeZone.knownTimeZoneIdentifiers + ["UTC"]).sorted().compactMap { id in
        guard let zone = TimeZone(identifier: id) else { return nil }
        let city = id.split(separator: "/").last.map(String.init)?.replacingOccurrences(of: "_", with: " ") ?? id
        let name = zone.localizedName(for: .generic, locale: .autoupdatingCurrent)
        let title = name.map { $0 == city ? city : "\(city) · \($0)" } ?? city
        return ZoneChoice(id: id, zone: zone, title: title)
    }
}

private struct RunStartZonePicker: View {
    @Binding var selected: ZoneChoice?
    @Environment(\.dismiss) private var dismiss
    @State private var query = ""

    private var choices: [ZoneChoice] {
        let term = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !term.isEmpty else { return ZoneChoice.all }
        return ZoneChoice.all.filter { $0.title.localizedCaseInsensitiveContains(term) || $0.id.localizedCaseInsensitiveContains(term) }
    }

    var body: some View {
        List {
            if choices.isEmpty { Text("No matching time zones.").foregroundStyle(.secondary) }
            ForEach(choices) { choice in
                Button {
                    selected = choice
                    dismiss()
                } label: {
                    VStack(alignment: .leading) {
                        Text(verbatim: choice.title)
                        Text(verbatim: choice.id).font(.caption).foregroundStyle(.secondary)
                    }
                }
                .accessibilityIdentifier("reflection.zone." + choice.id)
            }
        }
        .navigationTitle("Choose time zone")
        .navigationBarTitleDisplayMode(.inline)
        .searchable(text: $query, placement: .navigationBarDrawer(displayMode: .always), prompt: "City or time zone")
    }
}
