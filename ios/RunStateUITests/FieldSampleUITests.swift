import XCTest

final class FieldSampleUITests: XCTestCase {
    @MainActor
    func testSampleFinishCanPlayAndStopWithoutSavingARun() throws {
        #if RUNSTATE_FIELD_TOOLS && targetEnvironment(simulator)
        continueAfterFailure = false
        let app = XCUIApplication()
        app.launchArguments = ["-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        app.launchEnvironment["RUNSTATE_TEST_JOURNAL"] = UUID().uuidString
        app.launch()
        defer { app.terminate() }

        let settings = app.buttons["startSettings"]
        XCTAssertTrue(settings.waitForExistence(timeout: 30))
        settings.tap()
        let entry = app.buttons["settings.sampleRunFinish"]
        for _ in 0..<3 where !entry.isHittable { app.swipeUp() }
        XCTAssertTrue(entry.isHittable)
        entry.tap()

        let view = app.navigationBars["Sample run finish"]
        XCTAssertTrue(view.waitForExistence(timeout: 10))
        let text = app.staticTexts["fieldSample.reflection"]
        XCTAssertTrue(text.exists)
        XCTAssertFalse(text.label.isEmpty)
        let play = app.buttons["fieldSample.play"]
        let stop = app.buttons["fieldSample.stop"]
        let toggle = app.switches.matching(identifier: "fieldSample.originalPlaying").firstMatch
        XCTAssertTrue(toggle.exists)
        XCTAssertEqual(toggle.value as? String, "1")
        let screenshot = XCTAttachment(data: app.screenshot().pngRepresentation, uniformTypeIdentifier: "public.png")
        screenshot.name = "RunState-SampleFinish"
        screenshot.lifetime = .keepAlways
        add(screenshot)

        if !play.isHittable { app.swipeUp() }
        play.tap()
        XCTAssertTrue(stop.waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["fieldSample.status"].exists)
        XCTAssertFalse(toggle.isEnabled)
        stop.tap()
        XCTAssertTrue(play.waitForExistence(timeout: 5))
        XCTAssertTrue(toggle.isEnabled)
        toggle.coordinate(withNormalizedOffset: .init(dx: 0.9, dy: 0.5)).tap()
        XCTAssertEqual(toggle.value as? String, "0")
        play.tap()
        XCTAssertTrue(stop.waitForExistence(timeout: 5))
        view.buttons.element(boundBy: 0).tap()
        XCTAssertTrue(app.navigationBars["Run Settings"].waitForExistence(timeout: 10))
        for _ in 0..<3 where !entry.isHittable { app.swipeUp() }
        XCTAssertTrue(entry.isHittable)
        entry.tap()
        XCTAssertTrue(play.waitForExistence(timeout: 5))
        XCTAssertFalse(stop.exists)

        view.buttons.element(boundBy: 0).tap()
        app.navigationBars["Run Settings"].buttons["Done"].tap()
        app.buttons["startHistory"].tap()
        XCTAssertTrue(app.staticTexts["Your saved runs will appear here."].waitForExistence(timeout: 10))
        #else
        throw XCTSkip("The sample requires the personal iPhone field tools and an isolated simulator journal.")
        #endif
    }
}
