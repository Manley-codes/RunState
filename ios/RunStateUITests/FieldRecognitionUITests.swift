#if RUNSTATE_FIELD_TOOLS
import XCTest

final class FieldRecognitionUITests: XCTestCase {
    @MainActor
    func testPrivateControlsAreReachableBeforeRequestingTheMicrophone() throws {
        let app = XCUIApplication()
        app.launchEnvironment["RUNSTATE_TEST_JOURNAL"] = UUID().uuidString
        app.launchArguments = ["-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        app.launch()
        defer { app.terminate() }
        let settings = app.buttons["startSettings"]
        XCTAssertTrue(settings.waitForExistence(timeout: 30))
        settings.tap()
        let entry = app.buttons["settings.lockedSongTest"]
        for _ in 0..<4 where !entry.isHittable { app.swipeUp() }
        XCTAssertTrue(entry.isHittable)
        entry.tap()
        XCTAssertTrue(app.navigationBars["Two-song recognition"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.buttons["Share RunState Original"].exists)
        XCTAssertTrue(app.buttons["Share RunState Excerpt"].exists)
        let start = app.buttons["fieldRecognition.start"]
        for _ in 0..<3 where !start.isHittable { app.swipeUp() }
        XCTAssertTrue(start.isHittable)
        XCTAssertEqual(app.staticTexts["fieldRecognition.status"].label, "Ready for the two test tracks.")
        XCTAssertFalse(app.buttons["fieldRecognition.stop"].exists)
    }
}
#endif
