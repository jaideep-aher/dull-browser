import XCTest

final class BrowserWorkflowTests: XCTestCase {
    private var app: XCUIApplication!
    private var server: FixtureServer!

    override func setUpWithError() throws {
        continueAfterFailure = false
        app = XCUIApplication()
        app.launchArguments = ["-UITesting", "YES", "-introSeen", "YES", "-UITestResetSession", "YES"]
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
        app.buttons["searchEngine_google"].tap()
        XCTAssertEqual(app.buttons["searchEngine_google"].value as? String, "Selected")
        XCTAssertTrue(app.staticTexts["Always on"].exists)
        XCTAssertEqual(app.switches.count, 0)
        capture("settings-search-engines")
        app.buttons["settingsDone"].tap()
        open("dull browser test query")
        let address = app.textFields["addressField"]
        XCTAssertTrue(address.waitForExistence(timeout: 5))
        XCTAssertTrue(String(describing: address.value).contains("google.com"))
        app.terminate()
        app.launch()
        app.buttons["Settings"].tap()
        XCTAssertEqual(app.buttons["searchEngine_google"].value as? String, "Selected")
        app.buttons["searchEngine_duckDuckGo"].tap()
        app.buttons["settingsDone"].tap()
        open("youtube.com")
        XCTAssertTrue(app.staticTexts["blockedMessage"].waitForExistence(timeout: 5))
        open("dull browser second query")
        XCTAssertTrue(String(describing: app.textFields["addressField"].value).contains("duckduckgo.com"))
        app.buttons["Settings"].tap()
        app.buttons["searchEngine_kagi"].tap()
        app.buttons["settingsDone"].tap()
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
        XCTAssertTrue(app.buttons["Settings"].isHittable)
        app.buttons["Settings"].tap()
        XCTAssertTrue(app.buttons["searchEngine_google"].waitForExistence(timeout: 5))
        capture("settings-landscape")
        app.buttons["settingsDone"].tap()
        XCTAssertTrue(app.buttons["tabsButton"].isHittable)
    }
}
