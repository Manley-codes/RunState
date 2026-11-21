import Combine
import Foundation
import XCTest
@testable import RunState

@MainActor
final class SpotifyObservationTests: XCTestCase {
    private final class Source: SpotifyNowPlayingSource {
        var onEvent: ((SpotifyObservationEvent) -> Void)?
        var authorizationRequests: [String] = []
        var resumes = 0
        var suspends = 0
        var callbacks = 0
        func authorize(clientID: String) { authorizationRequests.append(clientID) }
        func resume() { resumes += 1 }
        func suspend() { suspends += 1 }
        func forget() {}
        func handleCallback(_ url: URL) -> Bool {
            guard url.scheme == "runstatetest" else { return false }
            callbacks += 1
            onEvent?(.authorized)
            return true
        }
    }

    private let id = String(repeating: "a1", count: 16)
    private let other = String(repeating: "b2", count: 16)
    private let track = URL(string: "https://open.spotify.com/track/0123456789abcdefghijkl")!
    private let callback = URL(string: "runstatetest://callback")!

    private func preferences(_ suite: String = "test.spotify." + UUID().uuidString) -> UserDefaults {
        let defaults = UserDefaults(suiteName: suite)!
        addTeardownBlock { UserDefaults().removePersistentDomain(forName: suite) }
        return defaults
    }

    func testOnlyExplicitConnectionSendsAValidatedPublicClientID() {
        let source = Source()
        let observation = SpotifyObservation(preferences: preferences(), source: source, bundledClientID: id)
        XCTAssertEqual(observation.state, .off)
        observation.setActive(false)
        observation.connect()
        observation.setActive(true)
        observation.retry()
        XCTAssertFalse(observation.handleCallback(callback))
        for invalid in [id + "a", "a", id.replacingOccurrences(of: "1", with: "x"), String(repeating: "á", count: 32)] {
            XCTAssertFalse(SpotifyObservation.isValidClientID(invalid))
            observation.connect(clientID: invalid)
        }
        XCTAssertTrue(source.authorizationRequests.isEmpty)
        observation.connect(clientID: " " + other.uppercased() + "\n")
        XCTAssertEqual(source.authorizationRequests, [other])
        XCTAssertEqual(observation.state, .authorizing)
        XCTAssertFalse(observation.handleCallback(URL(string: "https://example.com")!))
        XCTAssertEqual(source.resumes, 0)
    }

    func testAuthorizationMayReturnBeforeOrAfterActivationAndCancellationIsIgnored() {
        for callbackFirst in [true, false] {
            let source = Source()
            let observation = SpotifyObservation(preferences: preferences(), source: source, bundledClientID: id)
            observation.connect()
            observation.setActive(false)
            if callbackFirst {
                XCTAssertTrue(observation.handleCallback(callback))
                XCTAssertEqual(observation.state, .suspended)
                XCTAssertEqual(source.resumes, 0)
                observation.setActive(true)
            } else {
                observation.setActive(true)
                XCTAssertEqual(source.resumes, 0)
                XCTAssertTrue(observation.handleCallback(callback))
            }
            XCTAssertEqual(observation.state, .connecting)
            XCTAssertTrue(observation.isAuthorized)
            observation.setActive(true)
            XCTAssertEqual(source.resumes, 1)
            XCTAssertEqual(source.authorizationRequests.count, 1)
            observation.connect()
            let canceled = source.onEvent
            observation.disconnect()
            canceled?(.authorized)
            XCTAssertFalse(observation.handleCallback(callback))
            XCTAssertEqual(observation.state, .off)
            XCTAssertFalse(observation.isAuthorized)
        }
    }

    func testSuspensionImmediatelyClearsSongAndOldCallbacksCannotRepopulateAfterReturning() {
        let source = Source()
        let observation = SpotifyObservation(preferences: preferences(), source: source, bundledClientID: id)
        observation.connect()
        source.onEvent?(.authorized)
        let priorConnection = source.onEvent
        priorConnection?(.playing(title: "First", artist: "Artist", trackURL: track))
        XCTAssertEqual(observation.state, .playing(title: "First", artist: "Artist", trackURL: track))
        observation.setActive(false)
        XCTAssertEqual(source.suspends, 1)
        XCTAssertEqual(observation.state, .suspended)
        priorConnection?(.paused(title: "Old", artist: "Artist", trackURL: track))
        XCTAssertEqual(observation.state, .suspended)
        observation.setActive(true)
        priorConnection?(.playing(title: "Old", artist: "Artist", trackURL: track))
        XCTAssertEqual(observation.state, .connecting)
        source.onEvent?(.paused(title: "New", artist: "Artist", trackURL: track))
        XCTAssertEqual(observation.state, .paused(title: "New", artist: "Artist", trackURL: track))
        source.onEvent?(.unavailable("Spotify disconnected."))
        XCTAssertEqual(observation.state, .unavailable("Spotify disconnected."))
        observation.retry()
        XCTAssertEqual(source.resumes, 3)
        XCTAssertEqual(source.authorizationRequests, [id])
    }

    func testCanceledHandoffReturnsToAnExplicitConnectionAction() async {
        let source = Source()
        let observation = SpotifyObservation(preferences: preferences(), source: source, bundledClientID: id,
                                             authorizationReturnDelay: .zero)
        observation.connect()
        let canceled = source.onEvent
        let returned = expectation(description: "The interrupted authorization can be started again")
        let subscription = observation.$state.sink { state in
            if case .unavailable = state { returned.fulfill() }
        }
        defer { subscription.cancel() }
        observation.setActive(false)
        observation.setActive(true)
        await fulfillment(of: [returned], timeout: 3)
        guard case .unavailable = observation.state else { return XCTFail("Expected a canceled authorization") }
        canceled?(.authorized)
        XCTAssertFalse(observation.isAuthorized)
        XCTAssertFalse(observation.handleCallback(callback))
        XCTAssertEqual(source.resumes, 0)
        observation.connect()
        XCTAssertEqual(source.authorizationRequests, [id, id])
    }

    func testExpiredAuthorizationInForegroundOrOnReturnNeedsAnExplicitConnectAndKeepsThePublicID() {
        for returnFromLock in [false, true] {
            let source = Source()
            let observation = SpotifyObservation(preferences: preferences(), source: source, bundledClientID: nil)
            observation.connect(clientID: other)
            source.onEvent?(.authorized)
            source.onEvent?(.playing(title: "Before expiry", artist: "Artist", trackURL: track))
            if returnFromLock {
                observation.setActive(false)
                observation.setActive(true)
            }
            let expiredConnection = source.onEvent
            expiredConnection?(.authorizationExpired)
            expiredConnection?(.playing(title: "Old", artist: "Artist", trackURL: track))
            guard case .unavailable = observation.state else { return XCTFail("Show the expired connection") }
            XCTAssertFalse(observation.isAuthorized)
            XCTAssertEqual(observation.clientID, other)
            let resumes = source.resumes
            observation.retry()
            XCTAssertEqual(source.resumes, resumes)
            XCTAssertEqual(source.authorizationRequests, [other])
            observation.connect()
            XCTAssertEqual(source.authorizationRequests, [other, other])
        }
    }

    func testAnUnansweredPlayerRequestCanBeRetriedWithoutReauthorizing() async {
        let source = Source()
        let observation = SpotifyObservation(preferences: preferences(), source: source, bundledClientID: id,
                                             playerConnectionTimeout: .zero)
        observation.connect()
        let timedOut = expectation(description: "Foreground player did not deliver")
        let subscription = observation.$state.sink { state in
            if case .unavailable = state { timedOut.fulfill() }
        }
        defer { subscription.cancel() }
        source.onEvent?(.authorized)
        let original = source.onEvent
        await fulfillment(of: [timedOut], timeout: 3)
        XCTAssertTrue(observation.isAuthorized)
        XCTAssertEqual(source.suspends, 1)
        observation.retry()
        original?(.paused(title: "Old", artist: "Artist", trackURL: track))
        XCTAssertEqual(observation.state, .connecting)
        source.onEvent?(.playing(title: "Current", artist: "Artist", trackURL: track))
        XCTAssertEqual(observation.state, .playing(title: "Current", artist: "Artist", trackURL: track))
        XCTAssertEqual(source.authorizationRequests, [id])
    }

    func testRelaunchRetainsOnlyThePublicIDAndForgettingRestoresTheBundleDefault() {
        let suite = "test.spotify." + UUID().uuidString
        let defaults = preferences(suite)
        let source = Source()
        let observation = SpotifyObservation(preferences: defaults, source: source, bundledClientID: id)
        observation.connect(clientID: other)
        source.onEvent?(.authorized)
        source.onEvent?(.playing(title: "Private song", artist: "Private artist", trackURL: track))
        XCTAssertEqual(defaults.persistentDomain(forName: suite) as? [String: String], ["spotify.publicClientID": other])
        let reboot = Source()
        let reopened = SpotifyObservation(preferences: defaults, source: reboot, bundledClientID: id)
        XCTAssertEqual(reopened.state, .off)
        XCTAssertEqual(reopened.clientID, other)
        reopened.retry()
        reopened.setActive(false)
        reopened.setActive(true)
        XCTAssertTrue(reboot.authorizationRequests.isEmpty)
        XCTAssertEqual(reboot.resumes, 0)
        reopened.disconnect()
        XCTAssertEqual(reopened.clientID, id)
        let empty = SpotifyObservation(preferences: defaults, source: Source(), bundledClientID: "$(SPOTIFY_CLIENT_ID)")
        XCTAssertNil(empty.clientID)
    }
}
