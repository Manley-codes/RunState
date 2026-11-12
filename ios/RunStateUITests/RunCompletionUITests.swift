import XCTest

final class RunCompletionUITests: XCTestCase {
    private let runID = "CA6CD111-0A66-44C7-8487-F620B69CBF7C"
    private let earlierID = "8B59A990-B02B-47FE-9AE1-BF59F863248F"

    @MainActor
    func testFinishSaveCheckInAndDeleteOnlyThisRunFromHistory() throws {
        #if targetEnvironment(simulator)
        continueAfterFailure = false
        // A checkpoint of a paused, measured 2.20-mile run. The simulator exercises
        // the real Finish, check-in and storage flows without claiming it ran outdoors.
        let checkpoint = """
            {"id":"\(runID)","startedAt":816202800,"meters":3540.5568,"activeSeconds":1194,
             "preRunEnergy":"Moderate","pausedWithCompleteTime":true,
             "splitProgress":{"mileEnds":[547,1080],"distance":3540.5568,"closedAt":1194,
                              "uncovered":3,"interrupted":false,"reliable":true}}
            """
        let app = launchPausedRun(checkpoint)
        defer { app.terminate() }
        showFinishPrompt(in: app)
        let confirm = app.buttons["Save run"]
        XCTAssertTrue(confirm.waitForExistence(timeout: 10))
        confirm.tap()

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
        let history = app.buttons["startHistory"]
        XCTAssertTrue(history.waitForExistence(timeout: 30))
        history.tap()
        let savedRuns = app.navigationBars["Saved runs"]
        XCTAssertTrue(savedRuns.waitForExistence(timeout: 10))
        let recorded = app.buttons["history.run." + runID]
        let earlier = app.buttons["history.run." + earlierID]
        XCTAssertTrue(recorded.waitForExistence(timeout: 10))
        XCTAssertTrue(earlier.exists)
        recorded.tap()
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
        XCTAssertFalse(recorded.exists)
        XCTAssertTrue(earlier.exists)
        #else
        throw XCTSkip("This test uses an isolated simulator journal.")
        #endif
    }

    @MainActor
    func testShortPausedRunCanBeKeptThenDiscardedWithoutTouchingHistory() throws {
        #if targetEnvironment(simulator)
        continueAfterFailure = false
        let checkpoint = """
            {"id":"\(runID)","startedAt":816202800,"activeSeconds":12,"meters":0,"pausedWithCompleteTime":true}
            """
        let app = launchPausedRun(checkpoint)
        defer { app.terminate() }
        let shortRun = app.alerts["Save this short run?"]
        showFinishPrompt(in: app)
        XCTAssertTrue(shortRun.waitForExistence(timeout: 10))
        XCTAssertTrue(shortRun.buttons["Save run"].exists)
        shortRun.buttons["Keep paused"].tap()
        showFinishPrompt(in: app)
        XCTAssertTrue(shortRun.waitForExistence(timeout: 10))
        shortRun.buttons["Discard run"].tap()
        let history = app.buttons["startHistory"]
        XCTAssertTrue(history.waitForExistence(timeout: 10))
        history.tap()
        let earlier = app.buttons["history.run." + earlierID]
        XCTAssertTrue(earlier.waitForExistence(timeout: 10))
        XCTAssertFalse(app.buttons["history.run." + runID].exists)
        #else
        throw XCTSkip("This test uses an isolated simulator journal.")
        #endif
    }

    @MainActor
    private func launchPausedRun(_ checkpoint: String) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = ["-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        app.launchEnvironment["RUNSTATE_TEST_JOURNAL"] = UUID().uuidString
        app.launchEnvironment["RUNSTATE_TEST_SEED"] = """
            {"active":\(checkpoint),"history":[{"id":"\(earlierID)","startedAt":816030000,
            "meters":1609.344,"activeSeconds":600,"finishedAt":816030600}]}
            """
        app.launch()
        return app
    }

    @MainActor
    private func showFinishPrompt(in app: XCUIApplication) {
        let finish = app.buttons["Finish and save"]
        XCTAssertTrue(finish.waitForExistence(timeout: 15))
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
