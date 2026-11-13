#if RUNSTATE_FIELD_TOOLS
import SwiftUI

@MainActor
final class FieldRecognitionTest: ObservableObject {
    enum Phase: Equatable {
        case idle, preparing, listening, stopped, timedOut, failed(String)
    }

    struct Match: Identifiable {
        let id = UUID()
        let title: String
        let receivedAt: Date
        let receivedInBackground: Bool
    }

    @Published private(set) var phase: Phase = .idle
    @Published private(set) var matches: [Match] = []
    var available: Bool { makeRecognizer != nil }
    var isActive: Bool { activeID != nil }

    private let makeRecognizer: (() -> any FieldSongRecognizing)?
    private let now: () -> ContinuousClock.Instant
    private let limit: Duration
    private var deadline: ContinuousClock.Instant?
    private var inBackground = false
    private var activeID: UUID?
    private var recognizer: (any FieldSongRecognizing)?
    private var listening: Task<Void, Never>?
    private var timeout: Task<Void, Never>?

    convenience init() {
        if #available(iOS 17, *) {
            self.init(makeRecognizer: { FieldSongRecognition() })
        } else {
            self.init(makeRecognizer: nil)
        }
    }

    init(makeRecognizer: (() -> any FieldSongRecognizing)?,
         now: @escaping () -> ContinuousClock.Instant = { ContinuousClock().now },
         limit: Duration = .seconds(1_200)) {
        self.makeRecognizer = makeRecognizer
        self.now = now
        self.limit = limit
    }

    func start() {
        guard !isActive, let makeRecognizer else { return }
        let source = makeRecognizer()
        let id = UUID()
        let end = now().advanced(by: limit)
        matches.removeAll()
        activeID = id
        recognizer = source
        deadline = end
        phase = .preparing

        timeout = Task { [weak self] in
            do { try await ContinuousClock().sleep(until: end) } catch { return }
            guard let self, self.activeID == id else { return }
            self.finish(.timedOut)
        }
        listening = Task { [weak self] in
            guard self?.activeID == id else { return }
            do {
                try await source.listen(onReady: { [weak self] in
                    guard let self, self.acceptsEvents(for: id) else { return }
                    self.phase = .listening
                }, onMatch: { [weak self] title in
                    self?.receive(title, for: id)
                })
                guard let self, self.acceptsEvents(for: id) else { return }
                self.finish(.failed("Recognition ended before you stopped the test."))
            } catch {
                guard let self, self.acceptsEvents(for: id) else { return }
                self.finish(.failed(error.localizedDescription))
            }
        }
    }

    func stop() {
        guard isActive else { return }
        finish(.stopped)
    }

    func sceneChanged(isBackground: Bool) {
        inBackground = isBackground
        refreshDeadline()
    }

    func refreshDeadline() {
        if let deadline, now() >= deadline { finish(.timedOut) }
    }

    private func acceptsEvents(for id: UUID) -> Bool {
        guard activeID == id else { return false }
        // A suspended app can receive an overdue result ahead of its sleeping timer.
        refreshDeadline()
        return activeID == id
    }

    private func receive(_ title: String, for id: UUID) {
        guard acceptsEvents(for: id), phase == .listening,
              !matches.contains(where: { $0.title == title && $0.receivedInBackground == inBackground })
        else { return }
        matches.append(Match(title: title, receivedAt: Date(), receivedInBackground: inBackground))
    }

    private func finish(_ result: Phase) {
        activeID = nil
        deadline = nil
        timeout?.cancel()
        timeout = nil
        listening?.cancel()
        listening = nil
        recognizer?.stop()
        recognizer = nil
        phase = result
    }
}

@MainActor
struct FieldRecognitionView: View {
    @ObservedObject var test: FieldRecognitionTest

    var body: some View {
        Form {
            if test.available {
                Section {
                    ForEach(["field-finish-original", "field-finish-excerpt"], id: \.self) { file in
                        if let trackURL = Bundle.main.url(forResource: file, withExtension: "mp3") {
                            ShareLink(item: trackURL) {
                                Text(file == "field-finish-original" ? "Share RunState Original" : "Share RunState Excerpt")
                            }
                        }
                    }
                } header: {
                    Text("Set up the two tracks")
                } footer: {
                    Text("In Spotify, turn on Local audio files under Settings and privacy → Apps and devices. Here, share each track, choose Save to Files, then On My iPhone → Spotify. Both will appear under Your Library → Local Files in Spotify.")
                }
                Section {
                    Text("Queue both test tracks in Spotify and pause at the start of the first. Return to RunState and start recognition. When you see Listening, lock your iPhone and press Play on your headphones or Lock Screen.")
                    if test.isActive {
                        Button("Stop recognition") { test.stop() }
                            .accessibilityIdentifier("fieldRecognition.stop")
                    } else {
                        Button("Start recognition") { test.start() }
                            .accessibilityIdentifier("fieldRecognition.start")
                    }
                    status.accessibilityIdentifier("fieldRecognition.status")
                } footer: {
                    Text("Uses microphone access. Audio is processed on this iPhone. You can leave this screen to start a run. The test stops after 20 minutes or when you end the run.")
                }
                Section {
                    if test.matches.isEmpty {
                        Text("No matches received.").foregroundStyle(.secondary)
                    }
                    ForEach(test.matches) { match in
                        VStack(alignment: .leading, spacing: 3) {
                            Text(verbatim: match.title).font(.headline)
                            HStack {
                                Text(match.receivedAt, format: .dateTime.hour().minute().second())
                                Text(match.receivedInBackground ? "· In background" : "· Outside background")
                            }
                            .font(.subheadline).foregroundStyle(.secondary)
                        }
                        .accessibilityElement(children: .combine)
                    }
                } header: {
                    Text("Matches")
                } footer: {
                    Text("Shows when RunState received each track’s first match in each app state. Locking the phone or switching apps can put RunState in the background. Starting a new test clears this list.")
                }
            } else {
                Text("On-device song recognition requires iOS 17 or later.")
            }
        }
        .navigationTitle("Two-song recognition")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear { test.refreshDeadline() }
    }

    private var status: Text {
        switch test.phase {
        case .idle: return Text("Ready for the two test tracks.")
        case .preparing: return Text("Preparing recognition…")
        case .listening: return Text("Listening for the two test tracks.")
        case .stopped: return Text("Recognition stopped.")
        case .timedOut: return Text("The 20-minute test has ended.")
        case .failed(let reason): return Text(verbatim: reason)
        }
    }
}
#endif
