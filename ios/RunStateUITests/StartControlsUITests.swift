import XCTest

final class StartControlsUITests: XCTestCase {
    @MainActor
    func testRunnerCanConnectSpotifyThenStartPauseAndResumeAfterTheCountdown() throws {
        #if targetEnvironment(simulator)
        continueAfterFailure = false
        let app = XCUIApplication()
        app.launchEnvironment["RUNSTATE_TEST_JOURNAL"] = UUID().uuidString
        app.launchEnvironment["RUNSTATE_TEST_SPOTIFY_TRACK"] = "1"
        app.launchArguments = ["-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        app.resetAuthorizationStatus(for: .location)
        defer {
            app.terminate()
            app.resetAuthorizationStatus(for: .location)
        }
        app.launch()
        let start = app.buttons["startPrimary"]
        XCTAssertTrue(start.waitForExistence(timeout: 30))
        let track = app.descendants(matching: .any).matching(identifier: "spotify.nowPlaying").firstMatch
        XCTAssertFalse(track.exists)
        XCTAssertFalse(app.buttons["spotify.connect"].exists)
        start.tap()
        let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
        let allow = springboard.buttons.matching(NSPredicate(format: "label BEGINSWITH[c] %@", "Allow While Using")).firstMatch
        XCTAssertTrue(allow.waitForExistence(timeout: 15))
        allow.tap()
        let ready = XCTNSPredicateExpectation(predicate: NSPredicate(format: "label == %@", "Start run"), object: start)
        XCTAssertEqual(XCTWaiter.wait(for: [ready], timeout: 10), .completed)
        app.buttons["startSettings"].tap()
        let spotifySettings = app.buttons["spotify.settings"]
        XCTAssertTrue(spotifySettings.waitForExistence(timeout: 10))
        spotifySettings.tap()
        let connect = app.buttons["spotify.connect"]
        let clientID = app.textFields["spotify.clientID"]
        XCTAssertTrue(clientID.waitForExistence(timeout: 10))
        XCTAssertFalse(connect.isEnabled)
        clientID.tap()
        clientID.typeText(String(repeating: "a", count: 32) + "\n")
        XCTAssertTrue(connect.isEnabled)
        connect.tap()
        let expectedTrack = "Playing on Spotify, Morning Circuit, RunState Test Artist"
        XCTAssertTrue(track.waitForExistence(timeout: 10))
        XCTAssertEqual(track.label, expectedTrack)
        app.navigationBars["Spotify"].buttons.element(boundBy: 0).tap()
        app.navigationBars["Run Settings"].buttons["Done"].tap()
        XCTAssertFalse(track.exists)
        start.tap()
        // Wait for the stable run control after the normal countdown finishes.
        let pause = app.buttons["Pause run"]
        let resume = app.buttons["Resume run"]
        XCTAssertTrue(pause.waitForExistence(timeout: 60))
        func element(_ identifier: String) -> XCUIElement {
            app.descendants(matching: .any).matching(identifier: identifier).firstMatch
        }
        func reveal(_ element: XCUIElement) {
            let window = app.windows.firstMatch.frame
            for _ in 0..<4 where !element.isHittable {
                if element.frame.midY < window.midY { app.scrollViews.firstMatch.swipeDown() }
                else { app.scrollViews.firstMatch.swipeUp() }
            }
            XCTAssertTrue(element.isHittable, element.identifier)
        }
        func capture(_ name: String) {
            let screenshot = XCTAttachment(data: app.screenshot().pngRepresentation, uniformTypeIdentifier: "public.png")
            screenshot.name = name
            screenshot.lifetime = .keepAlways
            add(screenshot)
        }
        // Android's Spotify Music tile replaces the older song card; its value names the fresh song.
        let music = app.buttons["activeRunMusic"]
        XCTAssertTrue(music.waitForExistence(timeout: 10))
        let fresh = XCTNSPredicateExpectation(predicate: NSPredicate(format: "value == %@", expectedTrack), object: music)
        XCTAssertEqual(XCTWaiter.wait(for: [fresh], timeout: 10), .completed)
        XCTAssertEqual(music.label, "Open Spotify Music")
        XCTAssertFalse(track.exists)
        XCTAssertTrue(element("activeRunDistanceRing").exists)
        XCTAssertTrue(element("activeRunAveragePace").exists)
        reveal(music)
        capture("RunState-NowPlaying")
        music.tap()
        // Opening the player swaps it for distance, keeps distance and pace visible and sends nothing.
        let song = element("musicTrack")
        XCTAssertTrue(song.waitForExistence(timeout: 10))
        XCTAssertEqual(song.label, "Morning Circuit, RunState Test Artist. Open in Spotify")
        XCTAssertEqual(music.label, "Close music player")
        XCTAssertFalse(element("activeRunDistanceRing").exists)
        XCTAssertTrue(element("activeRunDistance").exists)
        XCTAssertTrue(element("activeRunAveragePace").exists)
        let playPause = app.buttons["musicPlayPause"]
        XCTAssertTrue(playPause.waitForExistence(timeout: 10))
        XCTAssertEqual(playPause.label, "Pause music")
        XCTAssertFalse(app.buttons["musicPrevious"].isEnabled)
        reveal(playPause)
        playPause.tap()
        let musicPaused = XCTNSPredicateExpectation(predicate: NSPredicate(format: "label == %@", "Play music"),
                                                    object: playPause)
        XCTAssertEqual(XCTWaiter.wait(for: [musicPaused], timeout: 10), .completed)
        XCTAssertTrue(pause.exists, "Song controls never pause the run")
        let next = app.buttons["musicNext"]
        XCTAssertTrue(next.isEnabled)
        reveal(next)
        next.tap()
        let skipped = XCTNSPredicateExpectation(predicate: NSPredicate(format: "label BEGINSWITH %@", "Second Wind,"),
                                                object: song)
        XCTAssertEqual(XCTWaiter.wait(for: [skipped], timeout: 10), .completed)
        XCTAssertEqual(playPause.label, "Play music")
        XCTAssertTrue(app.buttons["musicPrevious"].isEnabled)
        XCTAssertFalse(next.isEnabled)
        XCTAssertTrue(pause.exists)
        capture("RunState-MusicPlayer")
        let close = app.buttons["musicClose"]
        reveal(close)
        close.tap()
        XCTAssertTrue(element("activeRunDistanceRing").waitForExistence(timeout: 10))
        XCTAssertFalse(song.exists)
        XCTAssertEqual(music.label, "Open Spotify Music")
        reveal(pause)
        pause.tap()
        XCTAssertTrue(resume.waitForExistence(timeout: 15))
        resume.tap()
        XCTAssertTrue(pause.waitForExistence(timeout: 15))
        pause.tap()
        XCTAssertTrue(resume.waitForExistence(timeout: 15))
        #else
        throw XCTSkip("This test records an isolated run on the simulator.")
        #endif
    }

    @MainActor
    func testChoosingShoesIsRememberedAndCanBeClearedBeforeARun() throws {
        #if targetEnvironment(simulator)
        continueAfterFailure = false
        let app = XCUIApplication()
        app.launchEnvironment["RUNSTATE_TEST_JOURNAL"] = UUID().uuidString
        app.launchArguments = ["-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        app.launch()

        let shoes = app.buttons["startShoes"]
        XCTAssertTrue(shoes.waitForExistence(timeout: 30))
        shoes.tap()
        let catalogPair = app.buttons["shoeCatalog_ASICS_Novablast6"]
        if catalogPair.waitForExistence(timeout: 3) {
            catalogPair.tap()
        } else {
            // A reused simulator may already have added this pair on an earlier test run.
            let savedPair = app.buttons.matching(NSPredicate(format: "identifier BEGINSWITH %@ AND label CONTAINS[c] %@",
                                                              "shoeSelect_", "Novablast 6")).firstMatch
            XCTAssertTrue(savedPair.waitForExistence(timeout: 5))
            savedPair.tap()
        }
        let chosen = NSPredicate(format: "value CONTAINS[c] %@", "Novablast 6")
        let picked = XCTNSPredicateExpectation(predicate: chosen, object: shoes)
        XCTAssertEqual(XCTWaiter.wait(for: [picked], timeout: 10), .completed)
        app.terminate()
        app.launch()
        XCTAssertTrue(shoes.waitForExistence(timeout: 30))
        XCTAssertTrue(chosen.evaluate(with: shoes))
        shoes.tap()
        let clear = app.buttons["shoeNoTracking"]
        XCTAssertTrue(clear.waitForExistence(timeout: 5))
        clear.tap()
        let cleared = XCTNSPredicateExpectation(predicate: NSPredicate(format: "value == %@", "No shoes selected"), object: shoes)
        XCTAssertEqual(XCTWaiter.wait(for: [cleared], timeout: 10), .completed)
        #else
        throw XCTSkip("This test adds a shoe pair to the simulator’s local test library.")
        #endif
    }

    @MainActor
    func testMapSettingsAndSpotifyReturnToStartWithoutRecordingARun() throws {
        #if targetEnvironment(simulator)
        continueAfterFailure = false
        let app = XCUIApplication()
        app.launchEnvironment["RUNSTATE_TEST_JOURNAL"] = UUID().uuidString
        app.launchArguments = ["-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        app.resetAuthorizationStatus(for: .location)
        app.launch()

        // The photographic opening dismisses itself. Use the normal launch path.
        let map = app.buttons["startMap"]
        XCTAssertTrue(map.waitForExistence(timeout: 30))
        let settings = app.buttons["startSettings"]
        let spotify = app.buttons["startSpotify"]
        let history = app.buttons["startHistory"]
        XCTAssertTrue(settings.exists)
        XCTAssertTrue(spotify.exists)
        XCTAssertTrue(history.exists)

        map.tap()
        let closeMap = app.navigationBars["Live map"].buttons["Done"]
        XCTAssertTrue(closeMap.waitForExistence(timeout: 10))
        // A fresh simulator can browse the map without granting location access.
        XCTAssertTrue(app.buttons["Enable location"].exists)
        closeMap.tap()

        settings.tap()
        let announcements = app.switches.matching(identifier: "mileAnnouncements").firstMatch
        XCTAssertTrue(announcements.waitForExistence(timeout: 10))
        let route = app.switches.matching(identifier: "saveRunRoute").firstMatch
        XCTAssertTrue(route.exists)
        let privacy = app.buttons["Privacy & data"]
        if !privacy.isHittable { app.swipeUp() }
        privacy.tap()
        let privacyPage = app.navigationBars["Privacy & data"]
        XCTAssertTrue(privacyPage.waitForExistence(timeout: 10))
        privacyPage.buttons.element(boundBy: 0).tap()
        if !announcements.isHittable { app.swipeDown() }
        let original = try XCTUnwrap(announcements.value as? String)
        XCTAssertTrue(["0", "1"].contains(original))
        let changed = original == "1" ? "0" : "1"
        announcements.coordinate(withNormalizedOffset: .init(dx: 0.9, dy: 0.5)).tap()
        XCTAssertEqual(announcements.value as? String, changed)
        let closeSettings = app.navigationBars["Run Settings"].buttons["Done"]
        closeSettings.tap()
        settings.tap()
        XCTAssertTrue(announcements.waitForExistence(timeout: 10))
        XCTAssertEqual(announcements.value as? String, changed)
        announcements.coordinate(withNormalizedOffset: .init(dx: 0.9, dy: 0.5)).tap()
        XCTAssertEqual(announcements.value as? String, original)
        closeSettings.tap()

        // Spotify is absent on the clean CI simulator. Cancel keeps the runner in RunState.
        spotify.tap()
        let cancel = app.alerts.buttons["Cancel"]
        XCTAssertTrue(cancel.waitForExistence(timeout: 10))
        XCTAssertTrue(app.alerts.buttons["View in App Store"].exists)
        cancel.tap()

        history.tap()
        XCTAssertTrue(app.staticTexts["Your saved runs will appear here."].waitForExistence(timeout: 10))
        app.navigationBars["Saved runs"].buttons["Done"].tap()
        XCTAssertTrue(app.buttons["startPrimary"].isHittable)
        #else
        throw XCTSkip("This flow exercises an iOS simulator with no saved runs, location permission or Spotify installation.")
        #endif
    }
}
