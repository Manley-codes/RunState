import Combine
import Foundation
import UIKit
import XCTest
@testable import RunState

/// The in-run player: every control is explicit, answers come from Spotify and nothing is kept
/// from an older song or connection.
@MainActor
final class SpotifyPlayerTests: XCTestCase {
    private struct ArtworkRequest {
        let uri: String
        let completion: (UIImage?) -> Void
    }

    private final class Player: SpotifyNowPlayingSource {
        var onEvent: ((SpotifyObservationEvent) -> Void)?
        var commands: [SpotifyPlayerCommand] = []
        var answers: [(Bool) -> Void] = []
        var artworkRequests: [ArtworkRequest] = []
        var resumes = 0
        var suspends = 0
        func authorize(clientID: String) {}
        func resume() { resumes += 1 }
        func suspend() { suspends += 1 }
        func forget() {}
        func handleCallback(_ url: URL) -> Bool { false }
        func perform(_ command: SpotifyPlayerCommand, completion: @escaping (Bool) -> Void) {
            commands.append(command)
            answers.append(completion)
        }
        func artwork(forTrack uri: String, completion: @escaping (UIImage?) -> Void) {
            artworkRequests.append(ArtworkRequest(uri: uri, completion: completion))
        }
    }

    private let id = String(repeating: "a1", count: 16)
    private let link = URL(string: "https://open.spotify.com/track/0123456789abcdefghijkl")!

    private func connected(_ player: Player, commandTimeout: Duration = .seconds(60)) -> SpotifyObservation {
        let suite = "test.spotify.player." + UUID().uuidString
        addTeardownBlock { UserDefaults().removePersistentDomain(forName: suite) }
        let observation = SpotifyObservation(preferences: UserDefaults(suiteName: suite)!, source: player,
                                             bundledClientID: id, playerConnectionTimeout: .seconds(60),
                                             commandTimeout: commandTimeout)
        observation.connect()
        player.onEvent?(.authorized)
        return observation
    }

    private func details(_ song: String, position: Int = 30_000, previous: Bool = false, next: Bool = true,
                         at instant: ContinuousClock.Instant = .now) -> SpotifyPlayback {
        SpotifyPlayback(trackURI: "spotify:track:" + song, album: "Album", durationMillis: 200_000,
                        positionMillis: position, receivedAt: instant, playbackSpeed: 1,
                        canSkipPrevious: previous, canSkipNext: next)
    }

    private func report(_ player: Player, _ title: String, _ playback: SpotifyPlayback?, paused: Bool = false) {
        player.onEvent?(paused ? .paused(title: title, artist: "Artist", trackURL: link, playback: playback)
                               : .playing(title: title, artist: "Artist", trackURL: link, playback: playback))
    }

    private func picture() -> UIImage {
        UIGraphicsImageRenderer(size: CGSize(width: 2, height: 2)).image { _ in }
    }

    func testPlayerDetailsFollowTheCurrentSongAndClearWhenTheConnectionEnds() {
        let player = Player()
        let observation = connected(player)
        XCTAssertEqual(observation.state, .connecting)
        XCTAssertNil(observation.playback)
        XCTAssertFalse(observation.canTogglePlayback)
        report(player, "First", details("A"))
        XCTAssertEqual(observation.playback?.trackURI, "spotify:track:A")
        XCTAssertEqual(observation.playback?.album, "Album")
        XCTAssertTrue(observation.canTogglePlayback)
        XCTAssertTrue(observation.canSkipNext)
        XCTAssertFalse(observation.canSkipPrevious)
        // A song without player details stays visible but offers no controls.
        report(player, "Plain", nil)
        XCTAssertEqual(observation.state, .playing(title: "Plain", artist: "Artist", trackURL: link))
        XCTAssertNil(observation.playback)
        XCTAssertFalse(observation.canTogglePlayback)
        report(player, "Second", details("B", previous: true, next: false), paused: true)
        XCTAssertTrue(observation.canSkipPrevious)
        XCTAssertFalse(observation.canSkipNext)
        XCTAssertTrue(observation.canTogglePlayback)
        observation.setActive(false)
        XCTAssertNil(observation.playback)
        XCTAssertFalse(observation.canTogglePlayback)
        observation.setActive(true)
        XCTAssertEqual(observation.state, .connecting)
        XCTAssertNil(observation.playback)
        report(player, "Third", details("C"))
        XCTAssertEqual(observation.playback?.trackURI, "spotify:track:C")
        player.onEvent?(.unavailable("Spotify disconnected."))
        XCTAssertNil(observation.playback)
        XCTAssertFalse(observation.canTogglePlayback)
        report(player, "Fourth", details("D"))
        observation.disconnect()
        XCTAssertNil(observation.playback)
        XCTAssertFalse(observation.canSkipNext)
    }

    func testPositionIsADisplayEstimateThatStopsWhilePausedAndAtTheEnd() {
        let start = ContinuousClock.now
        let song = details("A", position: 30_000, at: start)
        XCTAssertEqual(song.position(at: start, paused: false), 30_000)
        XCTAssertEqual(song.position(at: start + .seconds(2), paused: false), 32_000)
        XCTAssertEqual(song.position(at: start + .seconds(2), paused: true), 30_000)
        XCTAssertEqual(song.position(at: start + .seconds(600), paused: false), 200_000)
        XCTAssertEqual(song.position(at: start - .seconds(5), paused: false), 30_000)
        var faster = song
        faster.playbackSpeed = 2
        XCTAssertEqual(faster.position(at: start + .seconds(2), paused: false), 34_000)
        var stalled = song
        stalled.playbackSpeed = 0
        XCTAssertEqual(stalled.position(at: start + .seconds(2), paused: false), 30_000)
        var invalid = song
        invalid.playbackSpeed = .nan
        XCTAssertEqual(invalid.position(at: start + .seconds(2), paused: false), 30_000)
        var unknownLength = song
        unknownLength.durationMillis = 0
        XCTAssertEqual(unknownLength.position(at: start + .seconds(2), paused: false), 0)
        var pastTheEnd = song
        pastTheEnd.positionMillis = 250_000
        XCTAssertEqual(pastTheEnd.position(at: start + .seconds(2), paused: false), 200_000)
    }

    func testControlsSendOneCommandAtATimeOnlyWhereSpotifyAllowsIt() {
        let player = Player()
        let observation = connected(player)
        observation.togglePlayback()
        observation.next()
        XCTAssertTrue(player.commands.isEmpty, "Nothing is controlled before Spotify reports a song")
        report(player, "First", details("A", previous: false, next: true))
        observation.previous()
        XCTAssertTrue(player.commands.isEmpty, "Spotify reported no previous song")
        observation.next()
        XCTAssertEqual(player.commands, [.next])
        XCTAssertTrue(observation.commandPending)
        XCTAssertFalse(observation.canSkipNext)
        XCTAssertFalse(observation.canTogglePlayback)
        observation.togglePlayback()
        observation.next()
        XCTAssertEqual(player.commands, [.next], "Another tap waits for Spotify's answer")
        report(player, "Second", details("B", previous: true, next: true))
        player.answers[0](true)
        XCTAssertFalse(observation.commandPending)
        XCTAssertNil(observation.commandMessage)
        player.answers[0](false)
        XCTAssertNil(observation.commandMessage, "A repeated answer changes nothing")
        observation.togglePlayback()
        XCTAssertEqual(player.commands, [.next, .pause])
        player.answers[1](false)
        XCTAssertEqual(observation.commandMessage, "Spotify couldn’t change playback. Try again.")
        XCTAssertFalse(observation.commandPending)
        report(player, "Second", details("B", previous: true, next: true), paused: true)
        observation.togglePlayback()
        XCTAssertEqual(player.commands, [.next, .pause, .resume])
        XCTAssertNil(observation.commandMessage)
        // Spotify reconnecting forgets an unanswered control instead of finishing it later.
        player.onEvent?(.connecting)
        XCTAssertFalse(observation.commandPending)
        XCTAssertNil(observation.playback)
        player.answers[2](false)
        XCTAssertNil(observation.commandMessage)
        XCTAssertEqual(observation.state, .connecting)
        observation.previous()
        observation.togglePlayback()
        XCTAssertEqual(player.commands.count, 3)
    }

    func testAnUnansweredCommandDropsTheConnectionWithoutReplayingIt() async {
        let player = Player()
        let observation = connected(player, commandTimeout: .zero)
        report(player, "First", details("A"))
        let connection = player.onEvent
        let dropped = expectation(description: "An unanswered control ends the connection")
        let subscription = observation.$state.sink { state in
            if case .unavailable = state { dropped.fulfill() }
        }
        defer { subscription.cancel() }
        observation.togglePlayback()
        XCTAssertEqual(player.commands, [.pause])
        await fulfillment(of: [dropped], timeout: 3)
        XCTAssertEqual(observation.state, .unavailable("Spotify didn’t respond. Try the connection again."))
        XCTAssertEqual(player.suspends, 1)
        XCTAssertNil(observation.playback)
        XCTAssertFalse(observation.commandPending)
        XCTAssertTrue(observation.isAuthorized)
        player.answers[0](true)
        connection?(.playing(title: "Late", artist: "Artist", trackURL: link, playback: details("L")))
        XCTAssertEqual(observation.state, .unavailable("Spotify didn’t respond. Try the connection again."))
        observation.retry()
        XCTAssertEqual(observation.state, .connecting)
        XCTAssertEqual(player.resumes, 2)
        report(player, "Fresh", details("B"))
        XCTAssertEqual(observation.state, .playing(title: "Fresh", artist: "Artist", trackURL: link))
        XCTAssertEqual(player.commands, [.pause], "The unanswered control is never sent again")
    }

    func testAlbumCoverIsOnlyShownForTheSongItBelongsTo() {
        let player = Player()
        let observation = connected(player)
        observation.loadArtwork()
        XCTAssertTrue(player.artworkRequests.isEmpty, "No song, no request")
        report(player, "First", details("A"))
        observation.loadArtwork()
        observation.loadArtwork()
        XCTAssertEqual(player.artworkRequests.map { $0.uri }, ["spotify:track:A"])
        XCTAssertTrue(observation.loadingArtwork)
        player.artworkRequests[0].completion(picture())
        XCTAssertFalse(observation.loadingArtwork)
        XCTAssertNotNil(observation.currentArtwork)
        observation.loadArtwork()
        XCTAssertEqual(player.artworkRequests.count, 1, "The same song keeps its cover")
        report(player, "First", details("A"), paused: true)
        XCTAssertNotNil(observation.currentArtwork, "Pausing keeps the same song's cover")
        report(player, "Second", details("B"))
        XCTAssertNil(observation.currentArtwork)
        observation.loadArtwork()
        report(player, "Third", details("C"))
        player.artworkRequests[1].completion(picture())
        XCTAssertNil(observation.currentArtwork, "A late cover never appears with another song")
        XCTAssertFalse(observation.loadingArtwork)
        observation.loadArtwork()
        XCTAssertEqual(player.artworkRequests.last?.uri, "spotify:track:C")
        player.artworkRequests[2].completion(nil)
        XCTAssertNil(observation.currentArtwork)
        XCTAssertFalse(observation.loadingArtwork)
        observation.loadArtwork()
        XCTAssertEqual(player.artworkRequests.count, 4)
        player.artworkRequests[3].completion(picture())
        XCTAssertNotNil(observation.currentArtwork)
        observation.setActive(false)
        XCTAssertNil(observation.currentArtwork)
        XCTAssertNil(observation.artwork)
    }

    func testPlayerArtworkChoiceKeepsItsSavedNamesAndDefaultsToHeadphones() {
        XCTAssertEqual(MusicPlayerLayout.preferenceKey, "run.musicPlayerLayout")
        XCTAssertEqual(MusicPlayerLayout.allCases.map(\.rawValue), ["headphones", "album"])
        XCTAssertEqual(MusicPlayerLayout.allCases.map(\.label), ["Headphones", "Album cover"])
        XCTAssertNil(MusicPlayerLayout(rawValue: "cover"))
        XCTAssertNotNil(musicHeadphonesArt, "The shared Android headphones art is bundled")
    }
}
