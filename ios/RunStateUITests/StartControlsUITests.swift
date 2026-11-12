import XCTest

final class StartControlsUITests: XCTestCase {
    @MainActor
    func testMapSettingsAndSpotifyReturnToStartWithoutRecordingARun() throws {
        #if targetEnvironment(simulator)
        continueAfterFailure = false
        let app = XCUIApplication()
        app.launchArguments = ["-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
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
