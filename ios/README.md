# Dull Browser for iOS

SwiftUI and WKWebView. The site list is the Android asset, `app/src/main/assets/blocklist.txt`, bundled at build time. Bundle id `app.slate.browser.ios`, iOS 17 or newer.

Open `DullBrowser.xcodeproj` and run the `DullBrowser` scheme on an iPhone simulator, or:

```bash
cd ios
xcodebuild -scheme DullBrowser -destination 'platform=iOS Simulator,name=iPhone 17' -derivedDataPath build build
xcrun simctl install booted build/Build/Products/Debug-iphonesimulator/DullBrowser.app
xcrun simctl launch booted app.slate.browser.ios
```

If xcodebuild reports that CoreSimulator is out of date, run `sudo xcodebuild -runFirstLaunch` once.

`xcodebuild test` with the same destination runs the unit tests and the UI tests. The UI tests need a network connection.

To change what is blocked, edit the list on the Android side and build again.

## Tabs and settings

The numbered **Tabs** button is available on both the start page and web pages. Open it to create, switch, or close tabs. Each tab keeps its own page and Back/Forward history while the app is open. Tab addresses and the selected tab are restored after relaunch; unsaved form contents and full navigation history are not restored after the app process ends.

**Settings** (the gear) holds a short list of choices:

- **Search engine:** Google (default), DuckDuckGo, or Bing. Used by both the start page and address bar. A saved choice that is no longer offered falls back to Google.
- **Appearance:** Light (default, plain white), Dark, or System.
- **Pages:** show or hide the clock, text size, request desktop sites, JavaScript on or off, and whether links that ask for a new window open a new tab.
- **Privacy:** clear browsing data now, or start fresh each launch (previous tabs, cookies, and site data are dropped when Dull opens again).
- **About:** app version and the size of the site list.

Site blocking stays on in every tab and with every setting. There is no setting to unblock a site.

See [TEST_PLAN.md](TEST_PLAN.md) for automated and physical-device checks.
