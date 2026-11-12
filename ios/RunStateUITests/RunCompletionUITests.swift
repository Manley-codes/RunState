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
    func testWrittenReflectionStaysReadableAfterCorrectionAndRelaunchWithCloudOff() throws {
        #if targetEnvironment(simulator)
        continueAfterFailure = false
        let app = XCUIApplication()
        let passage = "You found a little more pace in the second mile. You finished feeling good after nearly twenty minutes on your feet."
        app.launchArguments = ["-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        app.launchEnvironment["RUNSTATE_TEST_JOURNAL"] = UUID().uuidString
        app.launchEnvironment["RUNSTATE_TEST_SEED"] = """
            {"history":[{"id":"\(runID)","startedAt":816202800,"finishedAt":816203994,
             "meters":3540.5568,"activeSeconds":1194,"pausedWithCompleteTime":true,
             "startTimezoneID":"America/Chicago","preRunEnergy":"Moderate","postRunEnergy":"feelingGood",
             "firstPostRunEnergy":"feelingGood","postRunEffort":5,"firstPostRunEffort":5,
             "splitProgress":{"mileEnds":[547,1080],"distance":3540.5568,"closedAt":1194,
                              "uncovered":3,"interrupted":false,"reliable":true},
             "writtenReflection":{"saved":{"text":"\(passage)","isTestData":true}}}]}
            """
        app.launch()
        defer { app.terminate() }
        let history = app.buttons["startHistory"]
        let row = app.buttons["history.run." + runID]
        let reflection = app.descendants(matching: .any).matching(identifier: "reflection.text").firstMatch
        XCTAssertTrue(history.waitForExistence(timeout: 30))
        history.tap()
        XCTAssertTrue(row.waitForExistence(timeout: 10))
        row.tap()
        XCTAssertTrue(reflection.waitForExistence(timeout: 10))
        XCTAssertEqual(reflection.label, passage)
        XCTAssertFalse(app.buttons["reflection.request"].exists)
        if !reflection.isHittable { app.swipeUp() }
        attachScreenshot("RunState-WrittenReflection", in: app)

        let edit = app.buttons["history.checkIn"]
        if !edit.isHittable { app.swipeDown() }
        edit.tap()
        let spent = app.buttons["completion.energy.spent"]
        XCTAssertTrue(spent.waitForExistence(timeout: 10))
        spent.tap()
        XCTAssertTrue(spent.isSelected)
        if !reflection.isHittable { app.swipeUp() }
        XCTAssertEqual(reflection.label, passage)

        app.terminate()
        app.launch()
        XCTAssertTrue(history.waitForExistence(timeout: 30))
        history.tap()
        XCTAssertTrue(row.waitForExistence(timeout: 10))
        row.tap()
        XCTAssertTrue(reflection.waitForExistence(timeout: 10))
        XCTAssertEqual(reflection.label, passage)
        XCTAssertFalse(app.buttons["reflection.request"].exists)
        #else
        throw XCTSkip("This test uses an isolated simulator journal containing a saved sample reflection.")
        #endif
    }

    @MainActor
    func testOlderRunCanConfirmItsZoneThenRequestOneReflectionAndReadItOffline() throws {
        #if targetEnvironment(simulator)
        continueAfterFailure = false
        let app = XCUIApplication()
        let latestFinish = Int(Date().timeIntervalSinceReferenceDate) - 60
        let passage = "You finished that mile feeling good. Ten minutes on your feet gave you room to find a steady rhythm."
        app.launchArguments = ["-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        app.launchEnvironment["RUNSTATE_TEST_JOURNAL"] = UUID().uuidString
        app.launchEnvironment["RUNSTATE_TEST_SEED"] = """
            {"history":[{"id":"\(runID)","startedAt":\(latestFinish - 600),"finishedAt":\(latestFinish),
              "meters":1609.344,"activeSeconds":600,"pausedWithCompleteTime":true,
              "preRunEnergy":"Moderate","postRunEnergy":"feelingGood","firstPostRunEnergy":"feelingGood",
              "splitProgress":{"mileEnds":[600],"distance":1609.344,"closedAt":600,
                               "uncovered":0,"interrupted":false,"reliable":true}}]}
            """
        app.launchEnvironment["RUNSTATE_TEST_WRITTEN_ANSWERS"] = """
            {"spent":"Lower energy sample.","feelingGood":"\(passage)","poweredUp":"Higher energy sample.",
             "noSelection":"Neutral sample.","isTestData":true}
            """
        app.launch()
        defer { app.terminate() }
        let history = app.buttons["startHistory"]
        let row = app.buttons["history.run." + runID]
        XCTAssertTrue(history.waitForExistence(timeout: 30))
        history.tap()
        XCTAssertTrue(row.waitForExistence(timeout: 10))
        row.tap()
        let chooseZone = app.buttons["reflection.chooseZone"]
        XCTAssertTrue(chooseZone.waitForExistence(timeout: 10))
        if !chooseZone.isHittable { app.swipeUp() }
        chooseZone.tap()
        let zonePage = app.navigationBars["Run start time zone"]
        XCTAssertTrue(zonePage.waitForExistence(timeout: 10))
        zonePage.buttons["Cancel"].tap()
        XCTAssertTrue(chooseZone.waitForExistence(timeout: 10))
        chooseZone.tap()
        app.buttons["reflection.browseZones"].tap()
        let search = app.searchFields.firstMatch
        XCTAssertTrue(search.waitForExistence(timeout: 10))
        search.tap()
        search.typeText("Chicago")
        let chicago = app.buttons["reflection.zone.America/Chicago"]
        XCTAssertTrue(chicago.waitForExistence(timeout: 10))
        chicago.tap()
        XCTAssertTrue(zonePage.waitForExistence(timeout: 10))
        app.buttons["reflection.confirmZone"].tap()

        let setup = app.buttons["reflection.settings"]
        XCTAssertTrue(setup.waitForExistence(timeout: 10))
        if !setup.isHittable { app.swipeUp() }
        setup.tap()
        let settings = app.navigationBars["Written reflections"]
        XCTAssertTrue(settings.waitForExistence(timeout: 10))
        let field = app.secureTextFields["reflections.accessCode"]
        field.tap()
        field.typeText(String(repeating: "s", count: 45))
        let saveCode = app.buttons["Save code and enable"]
        if !saveCode.isHittable { app.swipeUp() }
        saveCode.tap()
        XCTAssertTrue(app.switches.matching(identifier: "reflections.enabled").firstMatch.waitForExistence(timeout: 10))
        settings.buttons["Done"].tap()
        let create = app.buttons["reflection.request"]
        XCTAssertTrue(create.waitForExistence(timeout: 10))
        if !create.isHittable { app.swipeUp() }
        let reflection = app.descendants(matching: .any).matching(identifier: "reflection.text").firstMatch
        XCTAssertFalse(reflection.exists)
        create.tap()
        XCTAssertTrue(reflection.waitForExistence(timeout: 10))
        XCTAssertEqual(reflection.label, passage)
        XCTAssertTrue(app.staticTexts["Sample reflection"].exists)

        // Remove the fake service answer, then relaunch into the same journal.
        app.terminate()
        app.launchEnvironment.removeValue(forKey: "RUNSTATE_TEST_WRITTEN_ANSWERS")
        app.launch()
        XCTAssertTrue(history.waitForExistence(timeout: 30))
        history.tap()
        XCTAssertTrue(row.waitForExistence(timeout: 10))
        row.tap()
        XCTAssertTrue(reflection.waitForExistence(timeout: 10))
        XCTAssertEqual(reflection.label, passage)
        XCTAssertFalse(create.exists)
        #else
        throw XCTSkip("This test uses a simulated writing service and its own simulator Keychain entry.")
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
