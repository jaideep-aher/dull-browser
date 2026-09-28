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

Use **Settings** (the gear) to choose Kagi, Google, DuckDuckGo, or Bing. The selection persists and applies to searches from both the start page and address bar. Kagi is the initial default and requires an account.

Site blocking stays on in every tab and with every search engine. There is no setting to unblock a site.

See [TEST_PLAN.md](TEST_PLAN.md) for automated and physical-device checks.
