import XCTest

final class BrowserWorkflowTests: XCTestCase {
    private var app: XCUIApplication!
    private var server: FixtureServer!

    override func setUpWithError() throws {
        continueAfterFailure = false
        app = XCUIApplication()
        app.launchArguments = ["-UITesting", "YES", "-introSeen", "YES", "-UITestResetSession", "YES",
                               "-UITestResetPreferences", "YES"]
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

    private var tabRows: XCUIElementQuery {
        app.buttons.matching(NSPredicate(format: "identifier BEGINSWITH 'selectTab_'"))
    }

    /// Relaunches with the settings chosen so far.
    private func relaunch(restoringSession: Bool) {
        XCUIDevice.shared.press(.home)
        app.terminate()
        app.launchArguments = ["-UITesting", "YES", "-introSeen", "YES",
                               "-UITestResetSession", restoringSession ? "NO" : "YES"]
        app.launch()
    }

    /// Flips a settings switch, scrolling the form until it can be tapped.
    private func flip(_ identifier: String) {
        let toggle = app.switches[identifier]
        scrollSettings(to: toggle)
        let before = toggle.value as? String
        toggle.coordinate(withNormalizedOffset: CGVector(dx: 0.93, dy: 0.5)).tap()
        XCTAssertNotEqual(toggle.value as? String, before)
    }

    /// Settings rows load lazily, so rows further down only exist once scrolled into view.
    private func scrollSettings(to element: XCUIElement, file: StaticString = #filePath, line: UInt = #line) {
        XCTAssertTrue(app.buttons["settingsDone"].waitForExistence(timeout: 5), file: file, line: line)
        let form = app.collectionViews.firstMatch
        var swipes = 0
        while !(element.exists && element.isHittable), swipes < 8 {
            form.swipeUp(velocity: .slow)
            swipes += 1
        }
        XCTAssertTrue(element.isHittable, "Could not reach \(element)", file: file, line: line)
    }

    private func assertNoSwitchTouchesBlocking(file: StaticString = #filePath, line: UInt = #line) {
        let allowed: Set = ["showClock", "desktopSites", "javaScript", "newWindowTabs", "startFresh"]
        let switches = app.switches.allElementsBoundByIndex
        let rows = switches.filter { !$0.identifier.isEmpty }
        for toggle in switches {
            // Each Toggle row also exposes its bare control, without an identifier, inside the row.
            let known = toggle.identifier.isEmpty
                ? rows.contains { allowed.contains($0.identifier) && $0.frame.contains(toggle.frame) }
                : allowed.contains(toggle.identifier)
            XCTAssertTrue(known, "Unexpected switch \(toggle.debugDescription)", file: file, line: line)
        }
    }

    private func capture(_ name: String) {
        let shot = XCTAttachment(screenshot: app.screenshot())
        shot.name = name
        shot.lifetime = .keepAlways
        add(shot)
    }

    func testCreateSwitchCloseTabsKeepsPageAndFormState() {
        app.launch()
        open(server.url())
        XCTAssertTrue(page("Page One").waitForExistence(timeout: 10))
        let note = app.webViews.textFields["Note"]
        note.tap()
        note.typeText("keep this")
        app.buttons["tabsButton"].tap()
        app.buttons["addTab"].tap()
        XCTAssertTrue(app.textFields["searchField"].waitForExistence(timeout: 5))
        XCTAssertEqual(app.buttons["tabsButton"].value as? String, "2")
        open("youtube.com")
        XCTAssertTrue(app.staticTexts["blockedMessage"].waitForExistence(timeout: 5))
        app.buttons["tabsButton"].tap()
        XCTAssertEqual(tabRows.count, 2)
        capture("tabs-two-pages")
        tabRows.element(boundBy: 0).tap()
        XCTAssertTrue(page("Page One").waitForExistence(timeout: 5))
        XCTAssertEqual(note.value as? String, "keep this")
        app.buttons["tabsButton"].tap()
        app.buttons.matching(NSPredicate(format: "identifier BEGINSWITH 'closeTab_'" )).element(boundBy: 1).tap()
        XCTAssertEqual(tabRows.count, 1)
        app.buttons["tabsDone"].tap()
        XCTAssertEqual(app.buttons["tabsButton"].value as? String, "1")
        XCTAssertEqual(note.value as? String, "keep this")
    }

    func testClosingLastTabCreatesUsableStartPage() {
        app.launch()
        app.buttons["tabsButton"].tap()
        app.buttons.matching(NSPredicate(format: "identifier BEGINSWITH 'closeTab_'" )).firstMatch.tap()
        XCTAssertEqual(tabRows.count, 1)
        app.buttons["tabsDone"].tap()
        XCTAssertTrue(app.textFields["searchField"].waitForExistence(timeout: 5))
        open("instagram.com")
        XCTAssertTrue(app.staticTexts["blockedMessage"].waitForExistence(timeout: 5))
    }

    func testSearchPreferencePersistsAndBlockingStaysOn() {
        app.launch()
        app.buttons["Settings"].tap()
        XCTAssertTrue(app.buttons["searchEngine_google"].waitForExistence(timeout: 5))
        XCTAssertEqual(app.buttons["searchEngine_google"].value as? String, "Selected")
        XCTAssertFalse(app.buttons["searchEngine_kagi"].exists)
        capture("settings-search-engines")
        app.buttons["searchEngine_bing"].tap()
        XCTAssertEqual(app.buttons["searchEngine_bing"].value as? String, "Selected")
        assertNoSwitchTouchesBlocking()
        scrollSettings(to: app.staticTexts["Always on"])
        assertNoSwitchTouchesBlocking()
        capture("settings-lower")
        app.buttons["settingsDone"].tap()
        open("dull browser test query")
        let address = app.textFields["addressField"]
        XCTAssertTrue(address.waitForExistence(timeout: 5))
        XCTAssertTrue(String(describing: address.value).contains("bing.com"))
        relaunch(restoringSession: false)
        app.buttons["Settings"].tap()
        XCTAssertEqual(app.buttons["searchEngine_bing"].value as? String, "Selected")
        app.buttons["searchEngine_duckDuckGo"].tap()
        app.buttons["settingsDone"].tap()
        open("youtube.com")
        XCTAssertTrue(app.staticTexts["blockedMessage"].waitForExistence(timeout: 5))
        open("dull browser second query")
        XCTAssertTrue(String(describing: app.textFields["addressField"].value).contains("duckduckgo.com"))
        app.buttons["Settings"].tap()
        app.buttons["searchEngine_google"].tap()
        app.buttons["settingsDone"].tap()
    }

    func testAppearanceSettingPersistsAndDarkensPages() {
        app.launch()
        XCTAssertTrue(app.textFields["searchField"].waitForExistence(timeout: 5))
        XCTAssertGreaterThan(brightness(), 0.95)
        app.buttons["Settings"].tap()
        XCTAssertTrue(app.buttons["Dark"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.buttons["Light"].isSelected)
        app.buttons["Dark"].tap()
        XCTAssertTrue(app.buttons["Dark"].isSelected)
        capture("settings-dark")
        app.buttons["settingsDone"].tap()
        XCTAssertLessThan(brightness(), 0.2)
        capture("new-tab-dark")
        open("youtube.com")
        XCTAssertTrue(app.staticTexts["blockedMessage"].waitForExistence(timeout: 5))
        XCTAssertLessThan(brightness(), 0.2)
        relaunch(restoringSession: false)
        XCTAssertTrue(app.buttons["Settings"].waitForExistence(timeout: 5))
        XCTAssertLessThan(brightness(), 0.2)
        app.buttons["Settings"].tap()
        XCTAssertTrue(app.buttons["Dark"].isSelected)
        app.buttons["Light"].tap()
        app.buttons["settingsDone"].tap()
        XCTAssertGreaterThan(brightness(), 0.95)
    }

    func testClockCanBeHiddenWithoutChangingSearch() {
        app.launch()
        XCTAssertTrue(app.staticTexts["clock"].waitForExistence(timeout: 5))
        app.buttons["Settings"].tap()
        let toggle = app.switches["showClock"]
        XCTAssertTrue(toggle.waitForExistence(timeout: 5))
        XCTAssertEqual(toggle.value as? String, "1")
        toggle.coordinate(withNormalizedOffset: CGVector(dx: 0.93, dy: 0.5)).tap()
        XCTAssertEqual(toggle.value as? String, "0")
        app.buttons["settingsDone"].tap()
        XCTAssertTrue(app.textFields["searchField"].waitForExistence(timeout: 5))
        XCTAssertFalse(app.staticTexts["clock"].exists)
        open("youtube.com")
        XCTAssertTrue(app.staticTexts["blockedMessage"].waitForExistence(timeout: 5))
        app.buttons["Settings"].tap()
        XCTAssertTrue(toggle.waitForExistence(timeout: 5))
        toggle.coordinate(withNormalizedOffset: CGVector(dx: 0.93, dy: 0.5)).tap()
        XCTAssertEqual(toggle.value as? String, "1")
        app.buttons["settingsDone"].tap()
    }

    func testJavaScriptCanBeTurnedOffWithoutReopeningListedSites() {
        app.launch()
        open(server.url("/script"))
        XCTAssertTrue(page("Script ran").waitForExistence(timeout: 10))
        app.buttons["Settings"].tap()
        flip("javaScript")
        assertNoSwitchTouchesBlocking()
        app.buttons["settingsDone"].tap()
        app.buttons["Reload"].tap()
        XCTAssertTrue(page("Script off").waitForExistence(timeout: 10))
        open("youtube.com")
        XCTAssertTrue(app.staticTexts["blockedMessage"].waitForExistence(timeout: 5))
    }

    func testNewWindowLinksCanStayInCurrentTab() {
        app.launch()
        app.buttons["Settings"].tap()
        flip("newWindowTabs")
        app.buttons["settingsDone"].tap()
        open(server.url())
        XCTAssertTrue(page("Page One").waitForExistence(timeout: 10))
        app.webViews.links["Open new window"].tap()
        XCTAssertTrue(page("Page Two").waitForExistence(timeout: 5))
        XCTAssertEqual(app.buttons["tabsButton"].value as? String, "1")
    }

    func testStartFreshDropsPreviousTabsOnRelaunch() {
        app.launch()
        app.buttons["Settings"].tap()
        flip("startFresh")
        app.buttons["settingsDone"].tap()
        open(server.url())
        XCTAssertTrue(page("Page One").waitForExistence(timeout: 10))
        app.buttons["tabsButton"].tap()
        app.buttons["addTab"].tap()
        XCTAssertEqual(app.buttons["tabsButton"].value as? String, "2")
        relaunch(restoringSession: true)
        XCTAssertTrue(app.textFields["searchField"].waitForExistence(timeout: 5))
        XCTAssertEqual(app.buttons["tabsButton"].value as? String, "1")
        open("youtube.com")
        XCTAssertTrue(app.staticTexts["blockedMessage"].waitForExistence(timeout: 5))
    }

    func testReloadAndPullToRefreshFetchPageAgain() {
        app.launch()
        open(server.url("/count"))
        XCTAssertTrue(page("Visit 1").waitForExistence(timeout: 10))
        XCTAssertTrue(app.buttons["Reload"].waitForExistence(timeout: 5))
        app.buttons["Reload"].tap()
        XCTAssertTrue(page("Visit 2").waitForExistence(timeout: 10))
        let web = app.webViews.firstMatch
        web.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.15))
            .press(forDuration: 0.05, thenDragTo: web.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.85)))
        XCTAssertTrue(page("Visit 3").waitForExistence(timeout: 10))

        XCUIDevice.shared.press(.home)
        app.terminate()
        app.launchArguments = ["-UITesting", "YES", "-introSeen", "YES", "-UITestResetSession", "NO"]
        app.launch()
        XCTAssertTrue(page("Visit 4").waitForExistence(timeout: 10))
        XCTAssertTrue(app.buttons["Reload"].waitForExistence(timeout: 5))
        app.buttons["Reload"].tap()
        XCTAssertTrue(page("Visit 5").waitForExistence(timeout: 10))
    }

    func testReloadCannotReopenBlockedPage() {
        app.launch()
        open("youtube.com")
        XCTAssertTrue(app.staticTexts["blockedMessage"].waitForExistence(timeout: 5))
        XCTAssertFalse(app.buttons["Reload"].isEnabled)
        open(server.url())
        XCTAssertTrue(page("Page One").waitForExistence(timeout: 10))
        app.webViews.links["Blocked video"].tap()
        XCTAssertTrue(app.staticTexts["blockedMessage"].waitForExistence(timeout: 5))
        XCTAssertFalse(app.buttons["Reload"].isEnabled)
        XCTAssertEqual(app.webViews.count, 0)
    }

    /// Average brightness of a strip of empty page near the top of the screen, from 0 to 1.
    private func brightness() -> CGFloat {
        Thread.sleep(forTimeInterval: 0.5)
        guard let image = app.screenshot().image.cgImage else { return -1 }
        var pixel = [UInt8](repeating: 0, count: 4)
        let context = CGContext(data: &pixel, width: 1, height: 1, bitsPerComponent: 8, bytesPerRow: 4,
                                space: CGColorSpaceCreateDeviceRGB(),
                                bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
        let x = image.width / 2, y = image.height * 15 / 100
        context.draw(image, in: CGRect(x: -x, y: -(image.height - 1 - y), width: image.width, height: image.height))
        return (CGFloat(pixel[0]) + CGFloat(pixel[1]) + CGFloat(pixel[2])) / (3 * 255)
    }


    func testRelaunchRestoresLatestPageAfterFollowingLink() {
        app.launch()
        open(server.url())
        XCTAssertTrue(page("Page One").waitForExistence(timeout: 10))
        app.webViews.links["Next page"].tap()
        XCTAssertTrue(page("Page Two").waitForExistence(timeout: 5))
        XCUIDevice.shared.press(.home)
        app.terminate()
        app.launchArguments = ["-UITesting", "YES", "-introSeen", "YES", "-UITestResetSession", "NO"]
        app.launch()
        XCTAssertTrue(page("Page Two").waitForExistence(timeout: 10))
    }

    func testTabsRestoreAfterRelaunch() {
        app.launch()
        open("youtube.com")
        XCTAssertTrue(app.staticTexts["blockedMessage"].waitForExistence(timeout: 5))
        app.buttons["tabsButton"].tap()
        app.buttons["addTab"].tap()
        open("instagram.com")
        XCTAssertTrue(app.staticTexts["blockedMessage"].waitForExistence(timeout: 5))
        XCUIDevice.shared.press(.home)
        app.terminate()
        app.launchArguments = ["-UITesting", "YES", "-introSeen", "YES", "-UITestResetSession", "NO"]
        app.launch()
        XCTAssertTrue(app.staticTexts["blockedHost"].waitForExistence(timeout: 5))
        XCTAssertEqual(app.staticTexts["blockedHost"].label, "instagram.com")
        XCTAssertEqual(app.buttons["tabsButton"].value as? String, "2")
        app.buttons["tabsButton"].tap()
        tabRows.element(boundBy: 0).tap()
        XCTAssertTrue(app.staticTexts["blockedHost"].waitForExistence(timeout: 5))
        XCTAssertEqual(app.staticTexts["blockedHost"].label, "youtube.com")
    }

    func testBackForwardAndBlockedLinkRecovery() {
        app.launch()
        open(server.url())
        XCTAssertTrue(page("Page One").waitForExistence(timeout: 10))
        app.webViews.links["Next page"].tap()
        XCTAssertTrue(page("Page Two").waitForExistence(timeout: 5))
        app.buttons["Back"].tap()
        XCTAssertTrue(page("Page One").waitForExistence(timeout: 5))
        app.buttons["Forward"].tap()
        XCTAssertTrue(page("Page Two").waitForExistence(timeout: 5))
        app.webViews.links["Blocked video"].tap()
        XCTAssertTrue(app.staticTexts["blockedMessage"].waitForExistence(timeout: 5))
        XCTAssertFalse(page("Page Two").exists)
        app.buttons["Back"].tap()
        XCTAssertTrue(page("Page Two").waitForExistence(timeout: 5))
        XCTAssertFalse(app.staticTexts["blockedMessage"].exists)
    }

    func testBlockedFirstPageBackReturnsHome() {
        app.launch()
        open("youtube.com")
        XCTAssertTrue(app.staticTexts["blockedMessage"].waitForExistence(timeout: 5))
        app.buttons["Back"].tap()
        XCTAssertTrue(app.textFields["searchField"].waitForExistence(timeout: 5))
        XCTAssertFalse(app.staticTexts["blockedMessage"].exists)
    }

    func testLocalRedirectCannotBypassBlocking() {
        app.launch()
        open(server.url("/redirect"))
        XCTAssertTrue(app.staticTexts["blockedMessage"].waitForExistence(timeout: 10))
        XCTAssertEqual(app.staticTexts["blockedHost"].label, "youtube.com")
    }

    func testNewWindowOpensSeparateTab() {
        app.launch()
        open(server.url())
        XCTAssertTrue(page("Page One").waitForExistence(timeout: 10))
        app.webViews.links["Open new window"].tap()
        XCTAssertTrue(page("Page Two").waitForExistence(timeout: 5))
        XCTAssertEqual(app.buttons["tabsButton"].value as? String, "2")
        app.buttons["tabsButton"].tap()
        tabRows.element(boundBy: 0).tap()
        XCTAssertTrue(page("Page One").waitForExistence(timeout: 5))
    }

    func testFailedPageCanBeReplacedByWorkingPage() {
        app.launch()
        open("http://127.0.0.1:1/unreachable")
        XCTAssertTrue(app.staticTexts["This page did not load."].waitForExistence(timeout: 10))
        app.buttons["Reload"].tap()
        XCTAssertTrue(app.staticTexts["This page did not load."].waitForExistence(timeout: 5))
        open(server.url())
        XCTAssertTrue(page("Page One").waitForExistence(timeout: 10))
        XCTAssertFalse(app.staticTexts["This page did not load."].exists)
    }

    func testControlsStayReachableInLandscape() {
        app.launch()
        XCUIDevice.shared.orientation = .landscapeLeft
        defer { XCUIDevice.shared.orientation = .portrait }
        // A tap sent mid-rotation is dropped, so wait for the layout to settle first.
        let window = app.windows.firstMatch
        let rotated = expectation(for: NSPredicate { _, _ in window.frame.width > window.frame.height },
                                  evaluatedWith: nil)
        wait(for: [rotated], timeout: 5)
        Thread.sleep(forTimeInterval: 1)
        XCTAssertTrue(app.buttons["Settings"].isHittable)
        app.buttons["Settings"].tap()
        XCTAssertTrue(app.buttons["searchEngine_google"].waitForExistence(timeout: 5))
        capture("settings-landscape")
        app.buttons["settingsDone"].tap()
        XCTAssertTrue(app.buttons["tabsButton"].isHittable)
    }
}
