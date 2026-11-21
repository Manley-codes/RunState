import Combine
import Foundation
import UIKit

/// Live details for the in-run player, as Spotify last reported them. Memory only.
struct SpotifyPlayback: Equatable {
    var trackURI: String
    var album: String?
    var durationMillis: Int
    var positionMillis: Int
    var receivedAt: ContinuousClock.Instant
    var playbackSpeed: Double
    var canSkipPrevious: Bool
    var canSkipNext: Bool

    /// A display estimate only. Spotify remains the authority for position and playback.
    func position(at now: ContinuousClock.Instant, paused: Bool) -> Int {
        guard durationMillis > 0, positionMillis >= 0 else { return 0 }
        let base = min(positionMillis, durationMillis)
        guard !paused, playbackSpeed.isFinite, playbackSpeed > 0 else { return base }
        let age = max(0, (now - receivedAt).inMilliseconds)
        return base + Int(min(age * playbackSpeed, Double(durationMillis - base)))
    }
}

private extension Duration {
    var inMilliseconds: Double { Double(components.seconds) * 1_000 + Double(components.attoseconds) / 1e15 }
}

/// The current song's cover, kept with the song it belongs to.
struct SpotifyArtwork {
    let trackURI: String
    let image: UIImage
}

enum SpotifyPlayerCommand: Equatable { case previous, next, pause, resume }

enum SpotifyObservationEvent {
    case authorized, authorizationExpired, connecting
    case playing(title: String, artist: String, trackURL: URL, playback: SpotifyPlayback? = nil)
    case paused(title: String, artist: String, trackURL: URL, playback: SpotifyPlayback? = nil)
    case unavailable(String)
}

@MainActor
protocol SpotifyNowPlayingSource: AnyObject {
    var onEvent: ((SpotifyObservationEvent) -> Void)? { get set }
    func authorize(clientID: String)
    func resume()
    func suspend()
    func forget()
    func handleCallback(_ url: URL) -> Bool
    /// One explicit control. Completes `true` after Spotify acknowledged it and sent fresh state.
    func perform(_ command: SpotifyPlayerCommand, completion: @escaping (Bool) -> Void)
    func artwork(forTrack uri: String, completion: @escaping (UIImage?) -> Void)
}

extension SpotifyNowPlayingSource {
    func perform(_ command: SpotifyPlayerCommand, completion: @escaping (Bool) -> Void) { completion(false) }
    func artwork(forTrack uri: String, completion: @escaping (UIImage?) -> Void) { completion(nil) }
}

/// Foreground presentation only. No playback event is stored in preferences or the run journal.
@MainActor
final class SpotifyObservation: ObservableObject {
    enum State: Equatable {
        case off, authorizing, connecting, suspended
        case playing(title: String, artist: String, trackURL: URL)
        case paused(title: String, artist: String, trackURL: URL)
        case unavailable(String)
    }

    @Published private(set) var state: State = .off
    @Published private(set) var clientID: String?
    @Published private(set) var isAuthorized = false
    @Published private(set) var playback: SpotifyPlayback?
    @Published private(set) var artwork: SpotifyArtwork?
    @Published private(set) var loadingArtwork = false
    @Published private(set) var commandPending = false
    @Published private(set) var commandMessage: String?

    private static let preferenceKey = "spotify.publicClientID"
    private let preferences: UserDefaults
    private let source: any SpotifyNowPlayingSource
    private let bundledClientID: String?
    private let authorizationReturnDelay: Duration
    private let playerConnectionTimeout: Duration
    private var active = true
    private var awaitingAuthorization = false
    private var responseTimeout: Task<Void, Never>?
    private var eventGeneration = 0
    private let commandTimeoutDuration: Duration
    private var commandTimeout: Task<Void, Never>?
    private var commandToken = 0
    private var artworkRequest = 0

    init(preferences: UserDefaults = .standard,
         source: (any SpotifyNowPlayingSource)? = nil,
         bundledClientID: String? = Bundle.main.object(forInfoDictionaryKey: "RunStateSpotifyClientID") as? String,
         authorizationReturnDelay: Duration = .seconds(8),
         playerConnectionTimeout: Duration = .seconds(15),
         commandTimeout: Duration = .seconds(5)) {
        self.preferences = preferences
        self.source = source ?? SpotifyAppRemoteSource()
        self.bundledClientID = Self.normalizedClientID(bundledClientID)
        self.authorizationReturnDelay = authorizationReturnDelay
        self.playerConnectionTimeout = playerConnectionTimeout
        self.commandTimeoutDuration = commandTimeout
        clientID = Self.normalizedClientID(preferences.string(forKey: Self.preferenceKey)) ?? self.bundledClientID
    }

    static func isValidClientID(_ value: String) -> Bool { normalizedClientID(value) != nil }

    private static func normalizedClientID(_ value: String?) -> String? {
        guard let trimmed = value?.trimmingCharacters(in: .whitespacesAndNewlines) else { return nil }
        let bytes = trimmed.utf8
        guard bytes.count == 32, bytes.allSatisfy({
            (48...57).contains($0) || (65...70).contains($0) || (97...102).contains($0)
        }) else { return nil }
        return trimmed.lowercased()
    }

    /// The only action that may open Spotify for authorization.
    func connect(clientID suppliedID: String? = nil) {
        guard active else { return }
        guard let valid = Self.normalizedClientID(suppliedID ?? clientID) else {
            state = .unavailable("Enter the 32-character public Spotify client ID.")
            return
        }
        endSession()
        clientID = valid
        preferences.set(valid, forKey: Self.preferenceKey)
        awaitingAuthorization = true
        state = .authorizing
        listen()
        source.authorize(clientID: valid)
    }

    func disconnect() {
        endSession()
        preferences.removeObject(forKey: Self.preferenceKey)
        clientID = bundledClientID
        state = .off
    }

    private func endSession() {
        responseTimeout?.cancel()
        responseTimeout = nil
        eventGeneration += 1
        source.onEvent = nil
        awaitingAuthorization = false
        isAuthorized = false
        clearPlayer()
        source.forget()
    }

    @discardableResult
    func handleCallback(_ url: URL) -> Bool {
        guard awaitingAuthorization else { return false }
        return source.handleCallback(url)
    }

    func setActive(_ newValue: Bool) {
        guard active != newValue else { return }
        active = newValue
        responseTimeout?.cancel()
        responseTimeout = nil
        if !active {
            if isAuthorized {
                // Invalidate queued notifications before disconnecting so an old track cannot reappear.
                eventGeneration += 1
                source.onEvent = nil
                clearPlayer()
                state = .suspended
            }
            if awaitingAuthorization || isAuthorized { source.suspend() }
        } else if isAuthorized {
            retry()
        } else if awaitingAuthorization {
            // A return from Spotify can precede the callback URL or have no callback at all.
            // Only run this timer in RunState; the user may need time to sign into Spotify.
            waitForResponse(after: authorizationReturnDelay, whileIn: .authorizing)
        }
    }

    /// Reconnects an already authorized, in-memory session without switching apps.
    func retry() {
        guard active, isAuthorized else { return }
        listen()
        clearPlayer()
        state = .connecting
        waitForResponse(after: playerConnectionTimeout, whileIn: .connecting)
        source.resume()
    }

    private func waitForResponse(after delay: Duration, whileIn expected: State) {
        responseTimeout?.cancel()
        let generation = eventGeneration
        responseTimeout = Task { [weak self] in
            do { try await Task.sleep(for: delay) } catch { return }
            guard let self, self.eventGeneration == generation, self.active, self.state == expected else { return }
            if self.awaitingAuthorization {
                self.endSession()
                self.state = .unavailable("The Spotify connection wasn’t completed. You can connect again.")
            } else {
                self.eventGeneration += 1
                self.source.onEvent = nil
                self.source.suspend()
                self.clearPlayer()
                self.state = .unavailable("Spotify hasn’t sent a current track. Try the connection again.")
            }
        }
    }

    private func listen() {
        eventGeneration += 1
        let generation = eventGeneration
        source.onEvent = { [weak self] event in
            guard let self, self.eventGeneration == generation else { return }
            self.receive(event)
        }
    }

    private func receive(_ event: SpotifyObservationEvent) {
        switch event {
        case .authorized:
            guard awaitingAuthorization else { return }
            responseTimeout?.cancel()
            awaitingAuthorization = false
            isAuthorized = true
            if active { retry() } else { state = .suspended }
        case .authorizationExpired:
            endSession()
            state = .unavailable("Spotify access expired. Connect again to show the current song.")
        case .connecting:
            if active && isAuthorized && state != .connecting {
                clearPlayer()
                state = .connecting
                waitForResponse(after: playerConnectionTimeout, whileIn: .connecting)
            }
        case let .playing(title, artist, url, details):
            guard active && isAuthorized else { return }
            responseTimeout?.cancel()
            let next = State.playing(title: title, artist: artist, trackURL: url)
            if next != state { state = next }
            accept(details)
        case let .paused(title, artist, url, details):
            guard active && isAuthorized else { return }
            responseTimeout?.cancel()
            let next = State.paused(title: title, artist: artist, trackURL: url)
            if next != state { state = next }
            accept(details)
        case .unavailable(let reason):
            if awaitingAuthorization {
                endSession()
                state = .unavailable(reason)
            } else if active && isAuthorized {
                responseTimeout?.cancel()
                clearPlayer()
                state = .unavailable(reason)
            }
        }
    }

    // MARK: In-run player

    var canSkipPrevious: Bool { canControl && playback?.canSkipPrevious == true }
    var canSkipNext: Bool { canControl && playback?.canSkipNext == true }
    var canTogglePlayback: Bool { canControl }

    /// Only a fresh song from the current connection can be controlled, one command at a time.
    private var canControl: Bool {
        guard active, isAuthorized, !commandPending, playback != nil else { return false }
        switch state {
        case .playing, .paused: return true
        default: return false
        }
    }

    /// The cover is shown only for the song it belongs to, never another song's picture.
    var currentArtwork: UIImage? {
        guard let artwork, artwork.trackURI == playback?.trackURI else { return nil }
        return artwork.image
    }

    /// Explicit player controls only. Opening the player never sends a command.
    func previous() { if canSkipPrevious { send(.previous) } }
    func next() { if canSkipNext { send(.next) } }
    func togglePlayback() {
        guard canTogglePlayback else { return }
        switch state {
        case .playing: send(.pause)
        case .paused: send(.resume)
        default: return
        }
    }

    private func send(_ command: SpotifyPlayerCommand) {
        commandToken += 1
        let token = commandToken
        let generation = eventGeneration
        let delay = commandTimeoutDuration
        commandPending = true
        commandMessage = nil
        commandTimeout?.cancel()
        commandTimeout = Task { [weak self] in
            do { try await Task.sleep(for: delay) } catch { return }
            guard let self, self.commandToken == token, self.commandPending else { return }
            // Drop the connection instead of leaving the controls disabled. Nothing is replayed.
            self.eventGeneration += 1
            self.source.onEvent = nil
            self.source.suspend()
            self.clearPlayer()
            self.state = .unavailable("Spotify didn’t respond. Try the connection again.")
        }
        source.perform(command) { [weak self] acknowledged in
            guard let self, self.commandToken == token, self.commandPending else { return }
            self.commandTimeout?.cancel()
            self.commandTimeout = nil
            self.commandPending = false
            if !acknowledged && self.eventGeneration == generation {
                self.commandMessage = "Spotify couldn’t change playback. Try again."
            }
        }
    }

    /// Loads the current song's cover for the Album cover choice.
    func loadArtwork() {
        guard active, isAuthorized, let uri = playback?.trackURI, artwork?.trackURI != uri, !loadingArtwork else { return }
        artworkRequest += 1
        let request = artworkRequest
        loadingArtwork = true
        source.artwork(forTrack: uri) { [weak self] image in
            guard let self, self.artworkRequest == request else { return }
            self.loadingArtwork = false
            guard self.playback?.trackURI == uri, let image else { return }
            self.artwork = SpotifyArtwork(trackURI: uri, image: image)
        }
    }

    private func accept(_ details: SpotifyPlayback?) {
        if details?.trackURI != playback?.trackURI {
            artworkRequest += 1
            loadingArtwork = false
            if artwork != nil { artwork = nil }
        }
        if details != playback { playback = details }
    }

    private func clearPlayer() {
        commandToken += 1
        commandTimeout?.cancel()
        commandTimeout = nil
        if commandPending { commandPending = false }
        if commandMessage != nil { commandMessage = nil }
        if playback != nil { playback = nil }
        artworkRequest += 1
        if loadingArtwork { loadingArtwork = false }
        if artwork != nil { artwork = nil }
    }
}
