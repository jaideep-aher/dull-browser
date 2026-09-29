# iOS test plan

The reported problems are missing tab management and missing search-engine settings. Test both new features and the always-on blocking rules they must preserve.

## Automated checks

| Area | Checks | Test suite |
| --- | --- | --- |
| Tabs | Add without replacing old page; switch with form state intact; close background, active, and last tab; restore tab URLs and selected tab after relaunch | BrowserSessionTests, BrowserWorkflowTests |
| Search | Google is the default; a saved engine that is no longer offered falls back to Google; each engine builds correctly encoded queries; typed URLs remain URLs; selection persists; new searches use selected engine | SearchEngineTests, PreferenceMigrationTests, BrowserInputTests, BrowserWorkflowTests |
| Settings | Light is the default and white; Dark and System apply and persist; clock, JavaScript, desktop sites, new-window links, and start-fresh behave as labeled; no setting touches blocking | AppearanceTests, PageSettingsTests, BrowserWorkflowTests |
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

Setup steps are in the [iOS README](README.md#run-on-your-iphone). Then:

```bash
xcodebuild -project ios/DullBrowser.xcodeproj -scheme DullBrowser \
  -destination 'platform=iOS,id=DEVICE_UDID' -allowProvisioningUpdates \
  DEVELOPMENT_TEAM=YOUR_TEAM_ID -resultBundlePath /tmp/DullBrowser-device.xcresult test
```

A free Personal Team can have only three sideloaded apps at once. UI tests install a second app, the test runner, so remove other sideloaded apps first or run only `-only-testing:DullBrowserTests`. Do not commit personal signing settings.

## Extended cases N01–N50

Fifty cases added on top of the original 66 automated tests: 42 automated (34 unit, 8 UI) and 8 manual. Unit tests are in `DullBrowserTests/ExtendedCoverageTests.swift`. UI tests are in `DullBrowserUITests/BrowserWorkflowTests.swift` and use the local `FixtureServer`.

| ID | Area | Case | Type | Test |
| --- | --- | --- | --- | --- |
| N01 | Search | Engines are Google, DuckDuckGo, Bing, each with an HTTPS search host | Unit | `SearchEngineChoiceTests.testEveryEngineHasNameAndHTTPSSearchHost` |
| N02 | Search | Any unknown saved engine (`yahoo`, empty, wrong case, `kagi`) becomes Google | Unit | `SearchEngineChoiceTests.testMigrationRewritesAnyUnknownEngineToGoogle` |
| N03 | Search | Migrating twice keeps a valid choice | Unit | `SearchEngineChoiceTests.testMigrationIsIdempotentForValidChoice` |
| N04 | Search | Stored values match exactly; near misses fall back to Google | Unit | `SearchEngineChoiceTests.testResolveMatchesStoredValuesExactly` |
| N05 | Input | IPv4 address opens directly | Unit | `AddressParsingTests.testIPv4AddressOpensDirectly` |
| N06 | Input | IPv4 with port and path opens directly | Unit | `AddressParsingTests.testIPv4WithPortAndPathOpensDirectly` |
| N07 | Input | Incomplete IP is searched | Unit | `AddressParsingTests.testIncompleteIPAddressIsSearched` |
| N08 | Input | `localhost:3000` opens directly | Unit | `AddressParsingTests.testLocalhostWithPortOpensDirectly` |
| N09 | Input | Explicit `http://localhost` URL is kept | Unit | `AddressParsingTests.testExplicitHTTPLocalhostIsKept` |
| N10 | Input | Host with port, query, and fragment is kept | Unit | `AddressParsingTests.testHostWithPortQueryAndFragmentIsKept` |
| N11 | Input | Uppercase scheme is accepted | Unit | `AddressParsingTests.testUppercaseSchemeIsAccepted` |
| N12 | Input | Surrounding whitespace is trimmed | Unit | `AddressParsingTests.testSurroundingWhitespaceIsTrimmed` |
| N13 | Input | Address followed by words is a search | Unit | `AddressParsingTests.testAddressFollowedByWordsIsSearched` |
| N14 | Input | `ftp:`, `javascript:`, `file:`, `data:` input becomes a search, never a load | Unit | `AddressParsingTests.testUnsupportedSchemesBecomeSearches` |
| N15 | Input | International domain (`münchen.de`) opens directly | Unit | `AddressParsingTests.testInternationalDomainOpensDirectly` |
| N16 | Input | Unicode and emoji searches keep every character on every engine | Unit | `AddressParsingTests.testUnicodeSearchKeepsEveryCharacter` |
| N17 | Input | Very long input (about 5,500 characters) is searched without loss | Unit | `AddressParsingTests.testVeryLongInputIsSearchedWithoutLoss` |
| N18 | Input | Numeric top-level domain is searched | Unit | `AddressParsingTests.testNumericTopLevelDomainIsSearched` |
| N19 | Input | Empty labels (`example..com`, `.com`) are searched | Unit | `AddressParsingTests.testEmptyLabelsAreSearched` |
| N20 | Blocking | Case and trailing dot do not bypass the list | Unit | `BlockingEdgeCaseTests.testListedHostIgnoresCaseAndTrailingDot` |
| N21 | Blocking | Deep subdomains of listed sites are blocked | Unit | `BlockingEdgeCaseTests.testDeepSubdomainsOfListedSitesAreBlocked` |
| N22 | Blocking | Lookalikes and listed names in paths are not blocked | Unit | `BlockingEdgeCaseTests.testLookalikesAndPathsAreNotBlocked` |
| N23 | Blocking | Listed host is blocked over HTTP and on any port | Unit | `BlockingEdgeCaseTests.testListedHostIsBlockedOnAnySchemeOrPort` |
| N24 | Blocking | No stored preference or allow list can turn blocking off | Unit | `BlockingEdgeCaseTests.testNoStoredPreferenceCanTurnBlockingOff` |
| N25 | Tabs | 21 tabs open with unique IDs; closing all leaves one start page | Unit | `TabSessionEdgeCaseTests.testManyTabsCanBeOpenedAndClosed` |
| N26 | Tabs | Closing the selected middle tab selects the next tab | Unit | `TabSessionEdgeCaseTests.testClosingSelectedMiddleTabSelectsNextTab` |
| N27 | Tabs | The selected tab is restored after relaunch | Unit | `TabSessionEdgeCaseTests.testSelectedTabIsRestored` |
| N28 | Tabs | Corrupt saved session starts with one tab | Unit | `TabSessionEdgeCaseTests.testCorruptSavedSessionStartsWithOneTab` |
| N29 | Tabs | Saved session with duplicate tab IDs is discarded | Unit | `TabSessionEdgeCaseTests.testSavedSessionWithDuplicateTabIDsIsDiscarded` |
| N30 | Tabs | Start fresh ignores saved tabs | Unit | `TabSessionEdgeCaseTests.testStartingFreshIgnoresSavedTabs` |
| N31 | Navigation | Back from a failed first page returns to the start page | Unit | `NavigationEdgeCaseTests.testBackFromFailedFirstPageReturnsToStartPage` |
| N32 | Navigation | Incoming links open only from `dullbrowser://open-url?url=` | Unit | `NavigationEdgeCaseTests.testIncomingLinksOpenOnlyFromDullScheme` |
| N33 | Appearance | Light, Dark, and System are applied to the app windows | Unit | `AppearanceAndPrivacyTests.testAppearanceIsAppliedToAppWindows` |
| N34 | Privacy | Clearing browsing data removes cookies and leaves blocking on | Unit | `AppearanceAndPrivacyTests.testClearingBrowsingDataRemovesCookies` |
| N35 | Appearance | System appearance can be selected and persists after relaunch | UI | `BrowserWorkflowTests.testSystemAppearanceIsSelectableAndPersists` |
| N36 | Settings | Text size choice persists after relaunch | UI | `BrowserWorkflowTests.testTextSizeChoicePersists` |
| N37 | Settings | Request desktop sites changes the layout the server sees; blocking stays on | UI | `BrowserWorkflowTests.testDesktopSiteRequestChangesPageLayout` |
| N38 | Edge case | Server 404 page is shown, not treated as a load failure | UI | `BrowserWorkflowTests.testServer404PageIsShownNotTreatedAsLoadFailure` |
| N39 | Edge case | Server going offline shows an error; Back recovers; blocking still works | UI | `BrowserWorkflowTests.testServerGoingOfflineShowsErrorAndBackRecovers` |
| N40 | Privacy | Clear browsing data removes a site cookie and closes tabs | UI | `BrowserWorkflowTests.testClearBrowsingDataRemovesCookies` |
| N41 | Blocking | Blocked page has no links or web content, Reload is disabled, and it stays closed in landscape | UI | `BrowserWorkflowTests.testBlockedPageHasNoWayThroughEvenInLandscape` |
| N42 | Blocking | Mixed-case `WWW.YouTube.com` typed address is blocked | UI | `BrowserWorkflowTests.testMixedCaseWwwAddressIsBlocked` |
| N43 | Navigation | Manual 1 below | Manual | — |
| N44 | Navigation | Manual 2 below | Manual | — |
| N45 | Search | Manual 3 below | Manual | — |
| N46 | Tabs | Manual 4 below | Manual | — |
| N47 | Privacy | Manual 5 below | Manual | — |
| N48 | Handoff | Manual 6 below | Manual | — |
| N49 | Appearance | Manual 7 below | Manual | — |
| N50 | Accessibility | Manual 8 below | Manual | — |

### Manual checklist for a physical iPhone

1. **N43 — Swipe navigation.** Open two pages, then swipe from the left edge to go back and from the right edge to go forward. The gesture follows your finger, and a blocked page cannot be swiped past.
2. **N44 — Pull to refresh on a real network.** On Wi-Fi and on cellular, pull down on a live page such as wikipedia.org. It reloads once, and the spinner stops.
3. **N45 — Real searches.** With each of Google, DuckDuckGo, and Bing, search from the start page and from the address bar. Results load, and tapping a YouTube result shows the blocked page.
4. **N46 — Backgrounding.** Play a video on an allowed site, press the Home gesture, and return. The video is paused, and the tab and page are unchanged.
5. **N47 — Start fresh after force-quit.** Turn on **Start fresh each launch**, sign in to a site, then force-quit from the app switcher and reopen. There is one empty tab, and the site is signed out.
6. **N48 — Handoff to other apps.** Tap a `mailto:` or `tel:` link, which opens Mail or Phone. Open `dullbrowser://open-url?url=https%3A%2F%2Fexample.com` from Notes, which opens in Dull. A handoff link to YouTube still shows the blocked page.
7. **N49 — System appearance live.** Set Appearance to **System**, then toggle Dark Mode in Control Center while Dull is open. The start page, toolbar, and Settings switch immediately. **Light** and **Dark** ignore the system toggle.
8. **N50 — Large text and VoiceOver.** At the largest Dynamic Type size, Settings rows wrap without clipping. With VoiceOver, every Settings control has a spoken label, and the blocked page reads its message and host.

## Focus and library features

Unit tests are in `DullBrowserTests/FocusFeatureTests.swift`; UI tests are in `DullBrowserUITests/FeatureWorkflowTests.swift` and use the local `FixtureServer`, with `-UITestPauseSites 127.0.0.1 -UITestPauseSeconds 2` where a fixture page must be paused.

| Area | Checks | Test suite |
| --- | --- | --- |
| Added sites | Typed sites reduce to a domain; IPs, single words and invalid labels are refused; added sites block subdomains and hidden links, persist, only grow, and ignore tampered entries; duplicates of listed sites are refused; there is no remove control | SiteAddressTests, CustomBlocklistTests, FeatureWorkflowTests |
| Pause | Off until turned on; subdomains match; work tools on shop domains are exempt; blocked sites cannot be paused and the block list wins; delay 10/20/30 s by opens that day; Continue only after the countdown; 5-minute grace in that tab only; removal waits 24 h, survives relaunch, can be cancelled, and ends the streak when applied; default sites are valid and not already blocked | PauseListTests, PauseNavigationTests, FeatureWorkflowTests |
| Blocked page | Attempts counted per site per day, not on restore; note and actions shown; still no links, web content or reload | PauseNavigationTests, FeatureWorkflowTests |
| Stats | Per-day aggregation and persistence; streak across gaps, loosened days and month ends; 7 and 30 day milestones once, with a one-time notice; 400-day cap; weekly totals and time saved; share line has totals only; card renders light and dark | StatsTests, FeatureWorkflowTests |
| Countdowns | Nearest upcoming date, today and tomorrow wording, past dates hidden, persistence | CountdownTests, FeatureWorkflowTests |
| Read later | Reading time open and closed, across midnight, time until open; save, mark read, remove, persistence; opening from the list | ReadingWindowTests, ReadLaterTests, FeatureWorkflowTests |
| Bookmarks | Add, toggle, edit, remove, persistence, http and https only, blocked sites still closed, start-page count capped at 8; page menu and start-page link | BookmarkTests, FeatureWorkflowTests |
| Tab grid | Only real pages are captured; preview is at most 320 px wide and under 80 KB, starting a capture costs under one frame; previews are removed from disk; grid shows the preview and a plain card for the blocked tab; form state survives switching | FeatureGateAndPreviewTests, FeatureWorkflowTests |
| Feature gate | Everything unlocked by default; locking every feature leaves blocking unchanged | FeatureGateAndPreviewTests |

### Manual checks for passwords and passkeys

1. On a physical iPhone with a saved password for a site, open its sign-in page in Dull, tap the username field, and use the key in the QuickType bar. The Passwords app (or the chosen password manager) fills the form.
2. Settings › Passwords › Open AutoFill settings opens the system AutoFill settings.
3. On a site that offers passkeys, a passkey prompt is not expected to work without the `com.apple.developer.web-browser.public-key-credential` entitlement; the site should still offer a password or another sign-in method.

## Other manual device checks

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
