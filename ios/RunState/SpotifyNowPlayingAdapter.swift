import Foundation
import SpotifyiOS
import UIKit

/// The Spotify SDK calls its delegates on the main thread. A connection belongs to
/// one foreground visit; an authorization may span the switch to Spotify and back.
@MainActor
final class SpotifyAppRemoteSource: NSObject, SpotifyNowPlayingSource,
    @preconcurrency SPTSessionManagerDelegate, @preconcurrency SPTAppRemoteDelegate {
    private static let redirect = URL(string: "runstate://spotify-callback")!
    private static let noPlayback = "Play a song in Spotify, then return to RunState to reconnect."
    var onEvent: ((SpotifyObservationEvent) -> Void)?

    private var authorizing: SPTSessionManager?
    private var configuration: SPTConfiguration?
    private var session: SPTSession?
    private var expiration: Timer?
    private var remote: SPTAppRemote?
    private var listener: PlayerUpdates?
    /// The song in the latest state from the current connection, for its cover.
    private var currentTrack: (any SPTAppRemoteTrack)?

    func authorize(clientID: String) {
        forget()
        let configuration = SPTConfiguration(clientID: clientID, redirectURL: Self.redirect)
        // An empty string resumes Spotify playback. nil requests authorization only.
        configuration.playURI = nil
        let manager = SPTSessionManager(configuration: configuration, delegate: self)
        guard manager.isSpotifyAppInstalled else {
            onEvent?(.unavailable("Install Spotify on this iPhone to connect."))
            return
        }
        self.configuration = configuration
        authorizing = manager
        manager.initiateSession(with: .appRemoteControl, options: .clientOnly, campaign: nil)
    }

    func handleCallback(_ url: URL) -> Bool {
        guard let manager = authorizing, let parts = URLComponents(url: url, resolvingAgainstBaseURL: false),
              parts.scheme == Self.redirect.scheme, parts.host == Self.redirect.host,
              parts.user == nil, parts.password == nil, parts.port == nil, parts.percentEncodedPath.isEmpty else { return false }
        // Spotify's session manager owns PKCE and validates the authorization response.
        return manager.application(UIApplication.shared, open: url)
    }

    func sessionManager(manager: SPTSessionManager, didInitiate session: SPTSession) {
        guard authorizing === manager else { return }
        authorizing = nil
        manager.delegate = nil
        guard !session.isExpired, !session.accessToken.isEmpty, session.scope.contains(.appRemoteControl) else {
            forget()
            onEvent?(.unavailable("Spotify access was unavailable. Connect again to show the current song."))
            return
        }
        self.session = session
        let timer = Timer(fire: session.expirationDate, interval: 0, repeats: false) { [weak self, weak session] _ in
            Task { @MainActor in
                guard let self, let session, self.session === session else { return }
                self.endExpiredSession()
            }
        }
        expiration = timer
        RunLoop.main.add(timer, forMode: .common)
        // The coordinator reconnects if active, or waits for the scene's next activation.
        onEvent?(.authorized)
    }

    func sessionManager(manager: SPTSessionManager, didFailWith error: Error) {
        guard manager === authorizing else { return }
        forget()
        onEvent?(.unavailable("Spotify couldn’t connect. Check the app setup and try again."))
    }

    func resume() {
        guard authorizing == nil else { return }
        guard let configuration, let session, !session.isExpired else { endExpiredSession(); return }
        if let remote {
            if let listener { fetchCurrentState(from: remote, listener: listener) }
            return
        }
        let remote = SPTAppRemote(configuration: configuration, logLevel: .none)
        remote.connectionParameters.accessToken = session.accessToken
        remote.delegate = self
        self.remote = remote
        onEvent?(.connecting)
        remote.connect()
    }

    func suspend() {
        let previous = remote
        remote = nil
        previous?.delegate = nil
        previous?.playerAPI?.delegate = nil
        listener = nil
        currentTrack = nil
        previous?.disconnect()
    }

    func forget() {
        suspend()
        expiration?.invalidate()
        expiration = nil
        authorizing?.delegate = nil
        authorizing = nil
        session = nil
        configuration = nil
    }

    private func endExpiredSession() {
        forget()
        onEvent?(.authorizationExpired)
    }

    func appRemoteDidEstablishConnection(_ appRemote: SPTAppRemote) {
        guard remote === appRemote else { return }
        guard let api = appRemote.playerAPI else { loseConnection(appRemote); return }
        // A player-state delegate callback doesn't identify its sender. Give each
        // connection a delegate so late updates cannot appear on the next visit.
        let updates = PlayerUpdates { [weak self, weak appRemote] state in
            guard let self, let appRemote, self.remote === appRemote else { return }
            self.deliver(state)
        }
        listener = updates
        api.delegate = updates
        api.subscribe(toPlayerState: { [weak self, weak appRemote] _, error in
            guard let self, let appRemote, self.remote === appRemote else { return }
            if error != nil { self.loseConnection(appRemote) }
        })
        fetchCurrentState(from: appRemote, listener: updates)
    }

    private func fetchCurrentState(from appRemote: SPTAppRemote, listener updates: PlayerUpdates) {
        guard remote === appRemote else { return }
        let revision = updates.nextRevision()
        appRemote.playerAPI?.getPlayerState { [weak self, weak appRemote, weak updates] result, error in
            guard let self, let appRemote, self.remote === appRemote, let updates,
                  updates.revision == revision else { return }
            // A newer subscription event always wins over an older snapshot.
            if error == nil, let state = result as? SPTAppRemotePlayerState {
                self.deliver(state)
            } else {
                self.onEvent?(.unavailable("Waiting for a track from Spotify."))
            }
        }
    }

    private func deliver(_ state: SPTAppRemotePlayerState) {
        currentTrack = state.track
        onEvent?(Self.event(for: state))
    }

    /// Explicit in-run controls. Each refreshes the player before reporting success, so the next
    /// tap never decides pause versus resume from an older state.
    func perform(_ command: SpotifyPlayerCommand, completion: @escaping (Bool) -> Void) {
        guard let remote, remote.isConnected, let api = remote.playerAPI, let listener else {
            completion(false)
            return
        }
        let acknowledged: SPTAppRemoteCallback = { [weak self, weak remote, weak listener] _, error in
            guard let self, let remote, let listener, self.remote === remote, error == nil else {
                completion(false)
                return
            }
            let revision = listener.nextRevision()
            remote.playerAPI?.getPlayerState { [weak self, weak remote, weak listener] result, error in
                guard let self, let remote, self.remote === remote, let listener else {
                    completion(false)
                    return
                }
                guard error == nil, let state = result as? SPTAppRemotePlayerState else {
                    // Spotify accepted the command, but its result is unknown. Reconnect before
                    // another control instead of guessing from the old state.
                    self.loseConnection(remote)
                    completion(false)
                    return
                }
                if listener.revision == revision { self.deliver(state) }
                completion(true)
            }
        }
        switch command {
        case .previous: api.skip(toPrevious: acknowledged)
        case .next: api.skip(toNext: acknowledged)
        case .pause: api.pause(acknowledged)
        case .resume: api.resume(acknowledged)
        }
    }

    func artwork(forTrack uri: String, completion: @escaping (UIImage?) -> Void) {
        guard let remote, remote.isConnected, let images = remote.imageAPI,
              let track = currentTrack, track.uri == uri else {
            completion(nil)
            return
        }
        images.fetchImage(forItem: track, with: CGSize(width: 320, height: 320)) { [weak self, weak remote] result, error in
            guard let self, let remote, self.remote === remote, error == nil else {
                completion(nil)
                return
            }
            completion(result as? UIImage)
        }
    }

    func appRemote(_ appRemote: SPTAppRemote, didDisconnectWithError error: Error?) {
        loseConnection(appRemote)
    }

    func appRemote(_ appRemote: SPTAppRemote, didFailConnectionAttemptWithError error: Error?) {
        loseConnection(appRemote)
    }

    private func loseConnection(_ connection: SPTAppRemote) {
        guard remote === connection else { return }
        suspend()
        onEvent?(.unavailable(Self.noPlayback))
    }

    private static func event(for state: SPTAppRemotePlayerState) -> SpotifyObservationEvent {
        let track = state.track
        let prefix = "spotify:track:"
        let id = track.uri.dropFirst(prefix.count)
        guard !track.isEpisode, !track.isAdvertisement, track.uri.hasPrefix(prefix),
              id.utf8.count == 22, id.utf8.allSatisfy({ (48...57).contains($0) || (65...90).contains($0) || (97...122).contains($0) }),
              let link = URL(string: "https://open.spotify.com/track/" + id),
              !track.name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
              !track.artist.name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            return .unavailable("No Spotify track to show.")
        }
        let album = track.album.name.trimmingCharacters(in: .whitespacesAndNewlines)
        let restrictions = state.playbackRestrictions
        let playback = SpotifyPlayback(trackURI: track.uri, album: album.isEmpty ? nil : album,
                                       durationMillis: Int(clamping: track.duration),
                                       positionMillis: max(0, state.playbackPosition),
                                       receivedAt: .now, playbackSpeed: Double(state.playbackSpeed),
                                       canSkipPrevious: restrictions.canSkipPrevious,
                                       canSkipNext: restrictions.canSkipNext)
        return state.isPaused ? .paused(title: track.name, artist: track.artist.name, trackURL: link, playback: playback)
                              : .playing(title: track.name, artist: track.artist.name, trackURL: link, playback: playback)
    }

    @MainActor
    private final class PlayerUpdates: NSObject, @preconcurrency SPTAppRemotePlayerStateDelegate {
        let receive: (SPTAppRemotePlayerState) -> Void
        private(set) var revision = 0
        init(receive: @escaping (SPTAppRemotePlayerState) -> Void) { self.receive = receive }
        @discardableResult
        func nextRevision() -> Int {
            revision += 1
            return revision
        }
        func playerStateDidChange(_ playerState: SPTAppRemotePlayerState) {
            nextRevision()
            receive(playerState)
        }
    }
}
