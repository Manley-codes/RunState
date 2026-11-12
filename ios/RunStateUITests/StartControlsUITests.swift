import XCTest

final class StartControlsUITests: XCTestCase {
    @MainActor
    func testChoosingShoesIsRememberedAndCanBeClearedBeforeARun() throws {
        #if targetEnvironment(simulator)
        continueAfterFailure = false
        let app = XCUIApplication()
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
