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
