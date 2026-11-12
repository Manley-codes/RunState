import Combine
import Foundation

enum SpotifyObservationEvent {
    case authorized, authorizationExpired, connecting
    case playing(title: String, artist: String, trackURL: URL)
    case paused(title: String, artist: String, trackURL: URL)
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

    init(preferences: UserDefaults = .standard,
         source: (any SpotifyNowPlayingSource)? = nil,
         bundledClientID: String? = Bundle.main.object(forInfoDictionaryKey: "RunStateSpotifyClientID") as? String,
         authorizationReturnDelay: Duration = .seconds(8),
         playerConnectionTimeout: Duration = .seconds(15)) {
        self.preferences = preferences
        self.source = source ?? SpotifyAppRemoteSource()
        self.bundledClientID = Self.normalizedClientID(bundledClientID)
        self.authorizationReturnDelay = authorizationReturnDelay
        self.playerConnectionTimeout = playerConnectionTimeout
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
                state = .connecting
                waitForResponse(after: playerConnectionTimeout, whileIn: .connecting)
            }
        case let .playing(title, artist, url):
            guard active && isAuthorized else { return }
            responseTimeout?.cancel()
            let next = State.playing(title: title, artist: artist, trackURL: url)
            if next != state { state = next }
        case let .paused(title, artist, url):
            guard active && isAuthorized else { return }
            responseTimeout?.cancel()
            let next = State.paused(title: title, artist: artist, trackURL: url)
            if next != state { state = next }
        case .unavailable(let reason):
            if awaitingAuthorization {
                endSession()
                state = .unavailable(reason)
            } else if active && isAuthorized {
                responseTimeout?.cancel()
                state = .unavailable(reason)
            }
        }
    }
}
