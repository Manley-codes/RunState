import SwiftUI
import UIKit

struct SpotifyObservationSettings: View {
    @ObservedObject var observation: SpotifyObservation
    @State private var enteredID = ""
    @FocusState private var enteringID: Bool

    var body: some View {
        Form {
            Section {
                switch observation.state {
                case .off:
                    connectButton
                case .authorizing:
                    ProgressView("Waiting for Spotify…")
                    Button("Cancel connection", action: disconnect)
                case .connecting:
                    ProgressView("Connecting to Spotify…")
                    Button("Cancel connection", action: disconnect)
                case .suspended:
                    Text("Updates resume when RunState is open.")
                    Button("Disconnect Spotify", action: disconnect)
                case .playing, .paused:
                    SpotifyTrackLink(state: observation.state)
                    Button("Disconnect Spotify", action: disconnect)
                case .unavailable(let message):
                    Text(verbatim: message).font(.subheadline)
                    if observation.isAuthorized {
                        Button("Try connection again", action: observation.retry)
                    }
                    connectButton
                    Button("Clear connection", action: disconnect)
                }
            } footer: {
                Text("Show the current song on your run screen while RunState is open. Song details stay out of History and written reflections.")
            }
            Section {
                if observation.clientID == nil {
                    TextField("RunState Spotify client ID", text: $enteredID)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .focused($enteringID)
                        .submitLabel(.done)
                        .onSubmit { enteringID = false }
                        .accessibilityIdentifier("spotify.clientID")
                    Text("Use the 32-character public Client ID from the RunState app in the Spotify Developer Dashboard.")
                        .font(.footnote).foregroundStyle(.secondary)
                }
                DisclosureGroup("Spotify setup details") {
                    Text("Register this iPhone app and its redirect URI in the RunState app’s Spotify Developer Dashboard settings.")
                        .font(.footnote).foregroundStyle(.secondary)
                    if let bundleID = Bundle.main.bundleIdentifier {
                        setupValue("iOS bundle ID", value: bundleID)
                    }
                    setupValue("Redirect URI", value: "runstate://spotify-callback")
                }
            } header: {
                Text("Field-test setup")
            }
        }
        .navigationTitle("Spotify")
        .navigationBarTitleDisplayMode(.inline)
    }

    private var connectButton: some View {
        Button("Connect to Spotify") {
            observation.connect(clientID: observation.clientID ?? enteredID.trimmingCharacters(in: .whitespacesAndNewlines))
        }
        .disabled(observation.clientID == nil && !SpotifyObservation.isValidClientID(enteredID.trimmingCharacters(in: .whitespacesAndNewlines)))
        .accessibilityIdentifier("spotify.connect")
    }

    private func disconnect() {
        enteredID = ""
        observation.disconnect()
    }

    private func setupValue(_ title: String, value: String) -> some View {
        HStack {
            VStack(alignment: .leading, spacing: 4) {
                Text(verbatim: title).font(.caption).foregroundStyle(.secondary)
                Text(verbatim: value).font(.footnote.monospaced()).textSelection(.enabled)
            }
            Spacer()
            Button {
                UIPasteboard.general.string = value
            } label: {
                Image(systemName: "doc.on.doc").frame(minWidth: 44, minHeight: 44)
            }
            .buttonStyle(.borderless)
            .accessibilityLabel("Copy " + title)
        }
    }
}

/// A fresh Spotify player update is the only thing that adds music to Active Run.
/// No waiting, disconnected, or previous-track card occupies the metric area.
struct SpotifyTrackLink: View {
    let state: SpotifyObservation.State
    @Environment(\.colorScheme) private var colorScheme

    @ViewBuilder var body: some View {
        switch state {
        case .playing(let title, let artist, let url):
            track(title: title, artist: artist, url: url, status: "Playing on Spotify")
        case .paused(let title, let artist, let url):
            track(title: title, artist: artist, url: url, status: "Paused on Spotify")
        default:
            EmptyView()
        }
    }

    private func track(title: String, artist: String, url: URL, status: String) -> some View {
        Link(destination: url) {
            HStack(spacing: 12) {
                Image("spotify_mark_white")
                    .renderingMode(.template)
                    .resizable()
                    .scaledToFit()
                    .frame(width: 24, height: 24)
                    .foregroundStyle(colorScheme == .dark ? Color.white : Color.black)
                    .accessibilityHidden(true)
                VStack(alignment: .leading, spacing: 2) {
                    Text(verbatim: status).font(.caption).foregroundStyle(.secondary)
                    Text(verbatim: title).font(.headline).lineLimit(2)
                    if !artist.isEmpty {
                        Text(verbatim: artist).font(.subheadline).foregroundStyle(.secondary).lineLimit(2)
                    }
                }
                Spacer(minLength: 4)
                Image(systemName: "arrow.up.right")
                    .font(.footnote.weight(.semibold)).foregroundStyle(.secondary)
                    .accessibilityHidden(true)
            }
            .frame(maxWidth: .infinity, minHeight: 44, alignment: .leading)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .ignore)
        .accessibilityAddTraits(.isLink)
        .accessibilityLabel([status, title, artist].filter { !$0.isEmpty }.joined(separator: ", "))
        .accessibilityHint("Opens this track in Spotify.")
        .accessibilityIdentifier("spotify.nowPlaying")
    }
}
