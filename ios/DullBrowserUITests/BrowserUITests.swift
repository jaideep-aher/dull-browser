import XCTest

/// Drives the app in the simulator. The network tests need an internet connection.
final class BrowserUITests: XCTestCase {
    private var app: XCUIApplication!

    override func setUp() {
        continueAfterFailure = false
        app = XCUIApplication()
        app.launchArguments = ["-UITesting", "YES", "-introSeen", "YES", "-UITestResetSession", "YES"]
    }

    private func screenshot(_ name: String) {
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }

    private func open(_ text: String) {
        let search = app.textFields["searchField"]
        let field = search.exists ? search : app.textFields["addressField"]
        XCTAssertTrue(field.waitForExistence(timeout: 10))
        field.tap()
        let current = (field.value as? String) ?? ""
        if !current.isEmpty, current != field.placeholderValue {
            field.typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: current.count + 8))
        }
        field.typeText(text + "\n")
    }

    private func assertClosed(host: String, file: StaticString = #filePath, line: UInt = #line) {
        let message = app.staticTexts["blockedMessage"]
        XCTAssertTrue(message.waitForExistence(timeout: 20), file: file, line: line)
        XCTAssertEqual(message.label, "This site stays closed in Dull Browser.", file: file, line: line)
        XCTAssertEqual(app.staticTexts["blockedHost"].label, host, file: file, line: line)
    }

    func testFirstRunShowsThreeLines() {
        app.launchArguments = ["-UITesting", "YES", "-introSeen", "NO", "-UITestResetSession", "YES"]
        app.launch()
        XCTAssertTrue(app.staticTexts["The list is in the app."].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["There is no switch."].exists)
        XCTAssertTrue(app.staticTexts["Other sites mean another browser."].exists)
        screenshot("first-run")
        app.buttons["introContinue"].tap()
        XCTAssertTrue(app.textFields["searchField"].waitForExistence(timeout: 5))
    }

    func testNewTabHasClockSearchTabsAndSettings() {
        app.launch()
        XCTAssertTrue(app.textFields["searchField"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["clock"].exists)
        XCTAssertEqual(app.textFields.count, 1)
        XCTAssertTrue(app.buttons["tabsButton"].exists)
        XCTAssertTrue(app.buttons["Settings"].exists)
        XCTAssertEqual(app.links.count, 0)
        screenshot("new-tab")
    }

    func testExampleDotComLoads() {
        app.launch()
        open("example.com")
        XCTAssertTrue(app.webViews.descendants(matching: .any)["Example Domain"].waitForExistence(timeout: 30))
        XCTAssertFalse(app.staticTexts["blockedMessage"].exists)
        screenshot("example-com")
    }

    func testTypedListedHostsStayClosed() {
        app.launch()
        open("bbc.com")
        assertClosed(host: "bbc.com")
        XCTAssertEqual(app.webViews.staticTexts.count, 0)
        screenshot("bbc-closed")

        for host in ["bbc.co.uk", "pornhub.com", "www.xvideos.com"] {
            open(host)
            assertClosed(host: host)
        }
    }

    func testServerRedirectToListedHostStaysClosed() {
        app.launch()
        open("https://httpbin.org/redirect-to?url=https%3A%2F%2Fwww.bbc.com%2F&status_code=302")
        assertClosed(host: "www.bbc.com")
        screenshot("redirect-closed")
    }

    /// Not in the bundled list; Cloudflare for Families answers 0.0.0.0 for it.
    func testFamilyDNSClosesUnlistedAdultHost() {
        app.launch()
        open("nudity.testcategory.com")
        assertClosed(host: "nudity.testcategory.com")
        screenshot("family-dns-closed")
    }

    func testGoogleWrapperIsRefusedBeforeLoading() {
        app.launch()
        open("https://www.google.com/url?q=https://www.bbc.com/news")
        assertClosed(host: "www.bbc.com")
    }

    func testGoogleSearchResultForBBCStaysClosed() throws {
        app.launch()
        open("https://www.google.com/search?q=bbc")
        XCTAssertFalse(app.staticTexts["blockedMessage"].waitForExistence(timeout: 5))
        let result = app.webViews.links.containing(NSPredicate(format: "label CONTAINS[c] 'BBC'")).firstMatch
        guard result.waitForExistence(timeout: 30) else {
            screenshot("google-no-result")
            throw XCTSkip("Google did not show a BBC result link in the simulator")
        }
        screenshot("google-results")
        result.tap()
        let message = app.staticTexts["blockedMessage"]
        XCTAssertTrue(message.waitForExistence(timeout: 20))
        XCTAssertTrue(app.staticTexts["blockedHost"].label.hasSuffix("bbc.com")
            || app.staticTexts["blockedHost"].label.hasSuffix("bbc.co.uk"))
        screenshot("google-result-closed")

        // The results page was never left, so Back shows it again.
        app.buttons["Back"].tap()
        XCTAssertTrue(result.waitForExistence(timeout: 10))
        XCTAssertFalse(app.staticTexts["blockedMessage"].exists)
    }
}
