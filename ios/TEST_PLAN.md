# iOS test plan

The reported problems are missing tab management and missing search-engine settings. Test both new features and the always-on blocking rules they must preserve.

## Automated checks

| Area | Checks | Test suite |
| --- | --- | --- |
| Tabs | Add without replacing old page; switch with form state intact; close background, active, and last tab; restore tab URLs and selected tab after relaunch | BrowserSessionTests, BrowserWorkflowTests |
| Search | Each engine builds correctly encoded queries; typed URLs remain URLs; selection persists; new searches use selected engine | SearchEngineTests, BrowserInputTests, BrowserWorkflowTests |
| Blocking | Direct domains, subdomains, lookalikes, Google wrappers, AMP, redirects, family DNS, and blocking after engine changes | BlockingTests, BrowserUITests, BrowserWorkflowTests |
| Navigation | Back/Forward, blocked-page recovery, failed-load recovery, new-window links become tabs | BrowserWorkflowTests |
| Layout | Tabs and Settings available on start and content pages; landscape controls; blocked content hidden from accessibility | BrowserWorkflowTests |
| Startup | First-run instructions, quiet start page | BrowserUITests |

`BrowserWorkflowTests` serves local fixture pages from the test runner, on both simulator and physical iPhone. These tests do not depend on external search results. The older live-site tests still require internet access; Google may show a consent screen or omit the expected result, which is reported as a skip.

## Run on simulator

```bash
xcodebuild -project ios/DullBrowser.xcodeproj -scheme DullBrowser \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro Max' \
  -resultBundlePath /tmp/DullBrowser-tests.xcresult test
```

Use a new result-bundle path for each run. To run only deterministic workflow tests, add `-only-testing:DullBrowserUITests/BrowserWorkflowTests`. For unit tests, add `-only-testing:DullBrowserTests`.

## Run on a connected iPhone

The device must be unlocked, trusted, reachable in Xcode, and have Developer Mode enabled. Select a signing team locally, then run the same tests with `-destination 'platform=iOS,id=DEVICE_UDID'`. Do not commit personal signing credentials.

## Manual device checks

- Open several useful sites, type into a form, switch tabs, and return. The page and form should remain intact while the app stays open.
- Close a background tab, the active tab, and the last tab. The browser should always leave one usable tab.
- Change the search engine, search from both input fields, restart the app, and check the selected engine again.
- Try YouTube, Instagram, and a listed adult domain. Each should show the blocked page, with no unblock setting.
- Follow allowed and blocked links, redirects, and a link that requests a new window.
- Try a failed connection, Reload, and Back. Return to a useful site.
- Rotate the phone, use a large text size, dismiss the keyboard, and verify that controls stay reachable.
- Switch tabs while media plays; background media should pause.
- Background and relaunch the app. Tab URLs and selection restore; full navigation history and unsaved form contents are not restored across process termination.

UI tests run with a separate preferences store to preserve the user's tabs and search choice.

Record simulator and physical-device results separately. A simulator pass is not a device pass.
