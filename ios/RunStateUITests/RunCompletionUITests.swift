import XCTest

final class RunCompletionUITests: XCTestCase {
    @MainActor
    func testFinishSaveCheckInAndDeleteOnlyThisRunFromHistory() throws {
        #if targetEnvironment(simulator)
        continueAfterFailure = false
        let app = XCUIApplication()
        app.launchArguments = ["-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        app.resetAuthorizationStatus(for: .location)
        defer {
            app.terminate()
            app.resetAuthorizationStatus(for: .location)
        }
        app.launch()

        let history = app.buttons["startHistory"]
        XCTAssertTrue(history.waitForExistence(timeout: 30))
        history.tap()
        let savedRuns = app.navigationBars["Saved runs"]
        XCTAssertTrue(savedRuns.waitForExistence(timeout: 10))
        let rows = app.buttons.matching(NSPredicate(format: "identifier BEGINSWITH %@", "history.run."))
        let originalIDs = Set(rows.allElementsBoundByIndex.map(\.identifier))
        savedRuns.buttons["Done"].tap()

        let start = app.buttons["startPrimary"]
        start.tap()
        let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
        let allowLocation = springboard.buttons.matching(NSPredicate(format: "label BEGINSWITH[c] %@", "Allow While Using")).firstMatch
        XCTAssertTrue(allowLocation.waitForExistence(timeout: 15))
        allowLocation.tap()
        let ready = XCTNSPredicateExpectation(predicate: NSPredicate(format: "label == %@", "Start run"), object: start)
        XCTAssertEqual(XCTWaiter.wait(for: [ready], timeout: 10), .completed)
        let shortRunPrompt = app.alerts["Save this short run?"]
        startAndPauseShortRun(in: app)
        showFinishPrompt(in: app)
        XCTAssertTrue(shortRunPrompt.waitForExistence(timeout: 10))
        shortRunPrompt.buttons["Save run"].tap()

        let good = app.buttons["completion.energy.feelingGood"]
        XCTAssertTrue(good.waitForExistence(timeout: 10))
        XCTAssertFalse(good.isSelected)
        good.tap()
        XCTAssertTrue(good.isSelected)
        let effort = app.buttons["completion.effort"]
        effort.tap()
        let hard = app.buttons["completion.effort.5"]
        if !hard.isHittable { app.swipeUp() }
        hard.tap()
        XCTAssertTrue(effort.label.contains("Hard"))
        if !good.isHittable { app.scrollViews.firstMatch.swipeDown() }
        attachScreenshot("RunState-PostRun", in: app)

        // A relaunch from the check-in itself exercises saving before dismissal.
        app.terminate()
        app.launch()
        XCTAssertTrue(history.waitForExistence(timeout: 30))
        history.tap()
        XCTAssertTrue(savedRuns.waitForExistence(timeout: 10))
        let newIDs = Set(rows.allElementsBoundByIndex.map(\.identifier)).subtracting(originalIDs)
        XCTAssertEqual(newIDs.count, 1)
        let row = app.buttons[try XCTUnwrap(newIDs.first)]
        row.tap()
        XCTAssertTrue(app.navigationBars["Run details"].waitForExistence(timeout: 10))
        attachScreenshot("RunState-HistoryDetail", in: app)
        let edit = app.buttons["history.checkIn"]
        XCTAssertTrue(edit.waitForExistence(timeout: 10))
        if !edit.isHittable { app.swipeUp() }
        edit.tap()
        XCTAssertTrue(good.waitForExistence(timeout: 10))
        XCTAssertTrue(good.isSelected)
        XCTAssertTrue(effort.label.contains("Hard"))
        app.navigationBars["Run complete"].buttons.element(boundBy: 0).tap()

        let delete = app.buttons["history.delete"]
        XCTAssertTrue(delete.waitForExistence(timeout: 10))
        delete.tap()
        let deletion = app.alerts["Delete this run?"]
        XCTAssertTrue(deletion.waitForExistence(timeout: 10))
        deletion.buttons["Cancel"].tap()
        XCTAssertTrue(delete.exists)
        delete.tap()
        deletion.buttons["Delete run"].tap()
        XCTAssertTrue(savedRuns.waitForExistence(timeout: 10))
        XCTAssertEqual(Set(rows.allElementsBoundByIndex.map(\.identifier)), originalIDs)
        savedRuns.buttons["Done"].tap()

        startAndPauseShortRun(in: app)
        showFinishPrompt(in: app)
        shortRunPrompt.buttons["Keep paused"].tap()
        XCTAssertTrue(app.buttons["Resume run"].waitForExistence(timeout: 10))
        showFinishPrompt(in: app)
        shortRunPrompt.buttons["Discard run"].tap()
        XCTAssertTrue(history.waitForExistence(timeout: 10))
        history.tap()
        XCTAssertTrue(savedRuns.waitForExistence(timeout: 10))
        XCTAssertEqual(Set(rows.allElementsBoundByIndex.map(\.identifier)), originalIDs)
        #else
        throw XCTSkip("This test grants location, records a run and removes its own record on the simulator.")
        #endif
    }

    @MainActor
    private func startAndPauseShortRun(in app: XCUIApplication) {
        app.buttons["startPrimary"].tap()
        let skipCountdown = app.buttons["Start now"]
        if skipCountdown.waitForExistence(timeout: 3), skipCountdown.isHittable { skipCountdown.tap() }
        let pause = app.buttons["Pause run"]
        XCTAssertTrue(pause.waitForExistence(timeout: 30))
        pause.tap()
    }

    @MainActor
    private func showFinishPrompt(in app: XCUIApplication) {
        let finish = app.buttons["Finish and save"]
        XCTAssertTrue(finish.waitForExistence(timeout: 10))
        if !finish.isHittable { app.swipeUp() }
        finish.tap()
    }

    @MainActor
    private func attachScreenshot(_ name: String, in app: XCUIApplication) {
        let attachment = XCTAttachment(data: app.screenshot().pngRepresentation, uniformTypeIdentifier: "public.png")
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}
