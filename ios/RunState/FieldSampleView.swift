#if RUNSTATE_FIELD_TOOLS
import SwiftUI

/// Self-contained audition for the personal iPhone build.
/// The sample has no access to the runner's journal, hosted services or Spotify.
@MainActor
struct FieldSampleView: View {
    @StateObject private var playback = FieldSamplePlayback()
    @Environment(\.scenePhase) private var scenePhase
    @State private var originallyPlaying = true
    private static let sample = try? MusicFinishSample.response()

    var body: some View {
        Form {
            if let sample = Self.sample, let cue = sample.snippet {
                let text = sample.text(for: .feelingGood)
                Section("Reflection") {
                    Text(verbatim: text)
                        .lineSpacing(3)
                        .accessibilityIdentifier("fieldSample.reflection")
                }
                Section("Selected passage") {
                    Label {
                        VStack(alignment: .leading, spacing: 3) {
                            Text(verbatim: cue.title).font(.headline)
                            Text(verbatim: cue.artist).font(.subheadline).foregroundStyle(.secondary)
                        }
                    } icon: {
                        Image(systemName: "music.note")
                    }
                    .accessibilityElement(children: .combine)
                }
                Section {
                    Toggle("Original track playing at finish", isOn: $originallyPlaying)
                        .disabled(playback.isPlaying)
                        .accessibilityIdentifier("fieldSample.originalPlaying")
                    if playback.isPlaying {
                        Button {
                            playback.stop()
                        } label: {
                            Label("Stop sample", systemImage: "stop.fill")
                        }
                        .accessibilityIdentifier("fieldSample.stop")
                    } else {
                        Button {
                            playback.start(text: text, startMilliseconds: cue.startPositionMillis,
                                           durationMilliseconds: cue.durationMillis, originallyPlaying: originallyPlaying)
                        } label: {
                            Label("Play sample finish", systemImage: "play.fill")
                        }
                        .accessibilityIdentifier("fieldSample.play")
                    }
                    if let status {
                        Text(verbatim: status)
                            .font(.subheadline)
                            .accessibilityIdentifier("fieldSample.status")
                    }
                } footer: {
                    Text("Start five seconds before a fictional run ends. Pause other music to hear the gap clearly. The iPhone voice and both sample tracks play offline.")
                }
            } else {
                Text("The sample recording could not be loaded from this build.")
            }
        }
        .navigationTitle("Sample run finish")
        .navigationBarTitleDisplayMode(.inline)
        .onDisappear { playback.stop() }
        .onChange(of: scenePhase) { phase in
            if phase != .active { playback.stop() }
        }
    }

    private var status: String? {
        switch playback.phase {
        case .idle: return nil
        case .leadIn: return originallyPlaying ? "Original track playing" : "Original track paused"
        case .reflection: return "Reading the reflection"
        case .excerpt: return "Playing the selected passage"
        case .closing: return "Closing chime and pause"
        case .restored: return originallyPlaying ? "Original track resumed" : "Original track stayed paused"
        case .finished: return "Sample finished"
        case .failed(let message): return message
        }
    }
}
#endif
