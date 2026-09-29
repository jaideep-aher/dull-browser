import XCTest

/// Pause, added sites, the blocked page, the library and the tab grid, against local fixture pages.
final class FeatureWorkflowTests: XCTestCase {
    private var app: XCUIApplication!
    private var server: FixtureServer!
    private let baseArguments = ["-UITesting", "YES", "-introSeen", "YES", "-UITestResetSession", "YES",
                                 "-UITestResetPreferences", "YES"]

    override func setUpWithError() throws {
        continueAfterFailure = false
        app = XCUIApplication()
        app.launchArguments = baseArguments
        server = FixtureServer()
        let ready = expectation(description: "Local fixture server ready")
        try server.start { ready.fulfill() }
        wait(for: [ready], timeout: 5)
    }

    override func tearDown() {
        server.stop()
        super.tearDown()
    }

    private func open(_ text: String) {
        let search = app.textFields["searchField"]
        let field = search.exists ? search : app.textFields["addressField"]
        XCTAssertTrue(field.waitForExistence(timeout: 5))
        field.tap()
        let current = (field.value as? String) ?? ""
        if !current.isEmpty, current != field.placeholderValue {
            field.typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: current.count + 8))
        }
        field.typeText(text + "\n")
    }

    private func page(_ name: String) -> XCUIElement {
        app.webViews.descendants(matching: .any).matching(identifier: name).firstMatch
    }

    private func scrollSettings(to element: XCUIElement, file: StaticString = #filePath, line: UInt = #line) {
        XCTAssertTrue(app.buttons["settingsDone"].waitForExistence(timeout: 5), file: file, line: line)
        let form = app.collectionViews.firstMatch
        var swipes = 0
        while !(element.exists && element.isHittable), swipes < 12 {
            form.swipeUp(velocity: .slow)
            swipes += 1
        }
        XCTAssertTrue(element.isHittable, "Could not reach \(element)", file: file, line: line)
    }

    private func openPageMenuItem(_ label: String) {
        let menu = app.buttons["pageMenu"]
        XCTAssertTrue(menu.waitForExistence(timeout: 5))
        menu.tap()
        let item = app.buttons[label].firstMatch
        XCTAssertTrue(item.waitForExistence(timeout: 5), "No menu item \(label)")
        item.tap()
    }

    private func waitUntilEnabled(_ element: XCUIElement, timeout: TimeInterval) -> Bool {
        let enabled = expectation(for: NSPredicate(format: "isEnabled == true"), evaluatedWith: element)
        return XCTWaiter().wait(for: [enabled], timeout: timeout) == .completed
    }

    private func capture(_ name: String) {
        let shot = XCTAttachment(screenshot: app.screenshot())
        shot.name = name
        shot.lifetime = .keepAlways
        add(shot)
    }

    func testPauseCountsDownEscalatesAndGoBackReturnsHome() {
        app.launchArguments = baseArguments + ["-UITestPauseSites", "127.0.0.1", "-UITestPauseSeconds", "2"]
        app.launch()
        open(server.url())
        let question = app.staticTexts["pauseQuestion"]
        XCTAssertTrue(question.waitForExistence(timeout: 5))
        XCTAssertEqual(question.label, "Do you really want this?")
        XCTAssertEqual(app.staticTexts["pauseSite"].label, "127.0.0.1")
        let proceed = app.buttons["pauseContinue"]
        XCTAssertFalse(proceed.isEnabled)
        XCTAssertFalse(page("Page One").exists)
        capture("pause-countdown")
        app.buttons["pauseGoBack"].tap()
        XCTAssertTrue(app.textFields["searchField"].waitForExistence(timeout: 5))
        XCTAssertFalse(question.exists)

        open(server.url())
        XCTAssertTrue(question.waitForExistence(timeout: 5))
        XCTAssertTrue(proceed.label.contains("4s") || proceed.label.contains("3s"), proceed.label)
        XCTAssertFalse(proceed.isEnabled)
        XCTAssertTrue(waitUntilEnabled(proceed, timeout: 8))
        proceed.tap()
        XCTAssertTrue(page("Page One").waitForExistence(timeout: 10))
        app.webViews.links["Next page"].tap()
        XCTAssertTrue(page("Page Two").waitForExistence(timeout: 5))
        XCTAssertFalse(question.exists, "No second pause right after continuing")
    }

    func testBlockListWinsOverPauseForTheSameSite() {
        app.launchArguments = baseArguments + ["-UITestPauseSites", "youtube.com"]
        app.launch()
        open("youtube.com")
        XCTAssertTrue(app.staticTexts["blockedMessage"].waitForExistence(timeout: 5))
        XCTAssertFalse(app.staticTexts["pauseQuestion"].exists)
    }

    func testPauseCategoryTurnsOnNowAndOffOnlyAfterADay() {
        app.launch()
        app.buttons["Settings"].tap()
        let link = app.buttons["pauseSitesLink"]
        scrollSettings(to: link)
        link.tap()
        let turnOn = app.buttons["pauseOn_shopping"]
        XCTAssertTrue(turnOn.waitForExistence(timeout: 5))
        XCTAssertEqual(app.switches.count, 0, "Pauses have no switch that could turn them off at once")
        turnOn.tap()
        let turnOff = app.buttons["pauseOff_shopping"]
        XCTAssertTrue(turnOff.waitForExistence(timeout: 5))
        capture("pause-settings")
        app.navigationBars.buttons.element(boundBy: 0).tap()
        app.buttons["settingsDone"].tap()

        open("www.amazon.com")
        XCTAssertTrue(app.staticTexts["pauseQuestion"].waitForExistence(timeout: 5))
        XCTAssertEqual(app.staticTexts["pauseSite"].label, "Amazon")
        app.buttons["pauseGoBack"].tap()

        app.buttons["Settings"].tap()
        scrollSettings(to: link)
        link.tap()
        XCTAssertTrue(turnOff.waitForExistence(timeout: 5))
        turnOff.tap()
        let confirm = app.buttons["confirmPauseRemoval"].firstMatch
        XCTAssertTrue(confirm.waitForExistence(timeout: 5))
        confirm.tap()
        XCTAssertTrue(app.staticTexts["pauseRemovalPending_shopping"].waitForExistence(timeout: 5))
        let keep = app.buttons["pauseKeep_shopping"]
        XCTAssertTrue(keep.exists)
        app.navigationBars.buttons.element(boundBy: 0).tap()
        app.buttons["settingsDone"].tap()

        open("amazon.com")
        XCTAssertTrue(app.staticTexts["pauseQuestion"].waitForExistence(timeout: 5), "Still paused during the 24 hours")
        app.buttons["pauseGoBack"].tap()

        app.buttons["Settings"].tap()
        scrollSettings(to: link)
        link.tap()
        XCTAssertTrue(keep.waitForExistence(timeout: 5))
        keep.tap()
        XCTAssertTrue(turnOff.waitForExistence(timeout: 5))
        XCTAssertFalse(app.staticTexts["pauseRemovalPending_shopping"].exists)
    }

    func testBlockedPageCountsAttemptsShowsNoteAndOffersOnlyWaysOut() {
        app.launch()
        app.buttons["Settings"].tap()
        let note = app.textFields["blockedNoteField"]
        scrollSettings(to: note)
        // A field that just scrolled in at the bottom edge can miss the first tap.
        app.collectionViews.firstMatch.swipeUp(velocity: .slow)
        note.tap()
        if !app.keyboards.firstMatch.waitForExistence(timeout: 2) { note.tap() }
        XCTAssertTrue(app.keyboards.firstMatch.waitForExistence(timeout: 3))
        note.typeText("Finish the essay first.")
        app.buttons["settingsDone"].tap()

        open("youtube.com")
        let attempts = app.staticTexts["blockedAttempts"]
        XCTAssertTrue(attempts.waitForExistence(timeout: 5))
        XCTAssertEqual(attempts.label, "You tried YouTube once today.")
        XCTAssertEqual(app.staticTexts["blockedNote"].label, "Finish the essay first.")
        XCTAssertEqual(app.staticTexts["blockedMessage"].label, "This site stays closed in Dull Browser.")
        XCTAssertEqual(app.webViews.count, 0)
        XCTAssertEqual(app.links.count, 0)
        XCTAssertFalse(app.buttons["Reload"].isEnabled)
        capture("blocked-page")

        app.buttons["blockedGoBack"].tap()
        XCTAssertTrue(app.textFields["searchField"].waitForExistence(timeout: 5))
        open("m.youtube.com")
        XCTAssertTrue(attempts.waitForExistence(timeout: 5))
        XCTAssertEqual(attempts.label, "You tried YouTube 2 times today.")

        app.buttons["blockedReadLater"].tap()
        XCTAssertTrue(app.buttons["panelDone"].waitForExistence(timeout: 5))
        app.buttons["panelDone"].tap()
        XCTAssertTrue(app.staticTexts["blockedMessage"].exists)
        app.buttons["blockedStartPage"].tap()
        XCTAssertTrue(app.textFields["searchField"].waitForExistence(timeout: 5))
    }

    func testAddedSiteIsBlockedWithSubdomainsAndHasNoRemoveControl() {
        app.launch()
        app.buttons["Settings"].tap()
        let link = app.buttons["addedSitesLink"]
        scrollSettings(to: link)
        link.tap()
        let field = app.textFields["addedSiteField"]
        XCTAssertTrue(field.waitForExistence(timeout: 5))
        field.tap()
        field.typeText("https://www.Dull-Added-Test.com/page")
        app.buttons["addedSiteBlock"].tap()
        let confirm = app.buttons["confirmAddedSite"].firstMatch
        XCTAssertTrue(confirm.waitForExistence(timeout: 5))
        confirm.tap()
        let row = app.staticTexts["dull-added-test.com"]
        XCTAssertTrue(row.waitForExistence(timeout: 5))
        row.swipeLeft()
        XCTAssertFalse(app.buttons["Delete"].exists)
        XCTAssertFalse(app.buttons["Edit"].exists)
        capture("added-sites")

        field.tap()
        field.typeText("youtube.com")
        app.buttons["addedSiteBlock"].tap()
        XCTAssertTrue(app.staticTexts["youtube.com is already blocked."].waitForExistence(timeout: 5))
        app.navigationBars.buttons.element(boundBy: 0).tap()
        app.buttons["settingsDone"].tap()

        open("dull-added-test.com")
        XCTAssertTrue(app.staticTexts["blockedMessage"].waitForExistence(timeout: 5))
        XCTAssertEqual(app.staticTexts["blockedHost"].label, "dull-added-test.com")
        open("shop.dull-added-test.com/x")
        XCTAssertTrue(app.staticTexts["blockedHost"].waitForExistence(timeout: 5))
        XCTAssertEqual(app.staticTexts["blockedHost"].label, "shop.dull-added-test.com")
    }

    func testBookmarkFromPageMenuAppearsOnStartPageAndOpens() {
        app.launch()
        open(server.url())
        XCTAssertTrue(page("Page One").waitForExistence(timeout: 10))
        openPageMenuItem("Add bookmark")
        app.buttons["tabsButton"].tap()
        app.buttons["addTab"].tap()
        let quick = app.buttons["quickLink_0"]
        XCTAssertTrue(quick.waitForExistence(timeout: 5))
        XCTAssertEqual(quick.label, "Page One")
        XCTAssertEqual(app.textFields.count, 1)
        capture("start-page-quick-links")
        quick.tap()
        XCTAssertTrue(page("Page One").waitForExistence(timeout: 10))

        openPageMenuItem("Bookmarks")
        XCTAssertTrue(app.buttons["bookmark_Page One"].waitForExistence(timeout: 5))
        app.buttons["panelDone"].tap()
        openPageMenuItem("Remove bookmark")
        app.buttons["tabsButton"].tap()
        app.buttons["addTab"].tap()
        XCTAssertTrue(app.textFields["searchField"].waitForExistence(timeout: 5))
        XCTAssertFalse(quick.exists)
    }

    func testSavedForLaterPageOpensFromTheList() {
        app.launch()
        open(server.url("/two"))
        XCTAssertTrue(page("Page Two").waitForExistence(timeout: 10))
        openPageMenuItem("Save for later")
        app.buttons["Back"].tap()
        XCTAssertTrue(app.textFields["searchField"].waitForExistence(timeout: 5))
        app.buttons["Settings"].tap()
        let link = app.buttons["readLaterLink"]
        scrollSettings(to: link)
        link.tap()
        let item = app.buttons["readLater_\(server.url("/two"))"]
        XCTAssertTrue(item.waitForExistence(timeout: 5))
        XCTAssertFalse(app.staticTexts["readLaterLocked"].exists)
        capture("read-later")
        item.tap()
        XCTAssertTrue(page("Page Two").waitForExistence(timeout: 10))
    }

    func testTabGridShowsPreviewOfTheTabThatWasLeft() {
        app.launch()
        open(server.url())
        XCTAssertTrue(page("Page One").waitForExistence(timeout: 10))
        let note = app.webViews.textFields["Note"]
        note.tap()
        note.typeText("grid")
        app.buttons["tabsButton"].tap()
        app.buttons["addTab"].tap()
        open("youtube.com")
        XCTAssertTrue(app.staticTexts["blockedMessage"].waitForExistence(timeout: 5))
        app.buttons["tabsButton"].tap()
        let previews = app.images.matching(NSPredicate(format: "identifier BEGINSWITH 'tabThumbnail_'"))
        XCTAssertTrue(previews.firstMatch.waitForExistence(timeout: 5))
        XCTAssertEqual(previews.count, 1, "Only the real page has a preview; the blocked tab keeps its placeholder")
        let cards = app.buttons.matching(NSPredicate(format: "identifier BEGINSWITH 'selectTab_'"))
        XCTAssertEqual(cards.count, 2)
        capture("tab-grid")
        cards.element(boundBy: 0).tap()
        XCTAssertTrue(page("Page One").waitForExistence(timeout: 5))
        XCTAssertEqual(note.value as? String, "grid")
    }

    func testCountdownShowsNextToTheClock() {
        app.launch()
        app.buttons["Settings"].tap()
        let link = app.buttons["countdownsLink"]
        scrollSettings(to: link)
        link.tap()
        let name = app.textFields["countdownName"]
        XCTAssertTrue(name.waitForExistence(timeout: 5))
        name.tap()
        name.typeText("Finals")
        app.buttons["countdownAdd"].tap()
        app.navigationBars.buttons.element(boundBy: 0).tap()
        app.buttons["settingsDone"].tap()
        let label = app.staticTexts["countdownLabel"]
        XCTAssertTrue(label.waitForExistence(timeout: 5))
        XCTAssertEqual(label.label, "Finals in 7 days")
        XCTAssertTrue(app.staticTexts["clock"].exists)
        capture("start-page-countdown")
    }

    func testStatsCountTodayAndOfferAShareCard() {
        app.launch()
        open("youtube.com")
        XCTAssertTrue(app.staticTexts["blockedMessage"].waitForExistence(timeout: 5))
        app.buttons["Settings"].tap()
        let link = app.buttons["statsLink"]
        scrollSettings(to: link)
        link.tap()
        let today = app.staticTexts["statsTodayBlocked"].exists ? app.staticTexts["statsTodayBlocked"]
            : app.descendants(matching: .any)["statsTodayBlocked"]
        XCTAssertTrue(today.waitForExistence(timeout: 5))
        XCTAssertTrue("\(today.label) \(today.value ?? "")".contains("1"), today.debugDescription)
        XCTAssertTrue(app.descendants(matching: .any)["shareWeek"].waitForExistence(timeout: 5))
        let card = app.descendants(matching: .any)["shareCardPreview"]
        XCTAssertTrue(card.exists)
        XCTAssertFalse(card.label.lowercased().contains("youtube"), card.label)
        capture("stats")
    }
}
