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

## Run on your iPhone

The project uses automatic signing with bundle id `app.slate.browser.ios`. No team is committed, so choose your own.

1. Connect the iPhone by USB, unlock it, and tap **Trust** on the phone.
2. Open `DullBrowser.xcodeproj`. In **Xcode › Settings › Accounts**, sign in with your Apple ID.
3. Select the **DullBrowser** target, then **Signing & Capabilities**. Keep **Automatically manage signing** on and pick your **Team**. Do the same for **DullBrowserTests** and **DullBrowserUITests**. If the bundle id is taken, change it to something unique, such as `app.slate.browser.ios.yourname`, and do not commit that change.
4. Choose the iPhone as the run destination and press Run once. If iOS asks, turn on **Settings › Privacy & Security › Developer Mode** and restart the phone.
5. With a free Personal Team, trust the certificate under **Settings › General › VPN & Device Management**.
6. Find the phone's UDID with `xcrun devicectl list devices`, then run the tests from the repo root:

```bash
xcodebuild -project ios/DullBrowser.xcodeproj -scheme DullBrowser \
  -destination 'platform=iOS,id=<UDID>' -allowProvisioningUpdates DEVELOPMENT_TEAM=<TEAM_ID> test
```

Your Team ID is shown in Xcode's team menu and in the developer account's Membership page. A free Personal Team allows three sideloaded apps, and UI tests install an extra runner app. Delete other sideloaded apps first, or run only `-only-testing:DullBrowserTests`. Keep the phone unlocked while UI tests run. You can also run tests in Xcode with **Product › Test** (⌘U).

## Tabs and settings

The numbered **Tabs** button is available on both the start page and web pages. Open it to create, switch, or close tabs. Each tab keeps its own page and Back/Forward history while the app is open. Tab addresses and the selected tab are restored after relaunch; unsaved form contents and full navigation history are not restored after the app process ends.

**Settings** (the gear) holds a short list of choices:

- **Search engine:** Google (default), DuckDuckGo, or Bing. Used by both the start page and address bar. A saved choice that is no longer offered falls back to Google.
- **Appearance:** Light (default, plain white), Dark, or System.
- **Pages:** show or hide the clock, text size, request desktop sites, JavaScript on or off, and whether links that ask for a new window open a new tab.
- **Privacy:** clear browsing data now, or start fresh each launch (previous tabs, cookies, and site data are dropped when Dull opens again).
- **About:** app version and the size of the site list.
- **Site blocking, Focus, Library and Passwords:** added sites, the blocked-page note, pauses, countdowns, bookmarks, read later and stats. See [Focus and library features](#focus-and-library-features).

Site blocking stays on in every tab and with every setting. There is no setting to unblock a site.

See [TEST_PLAN.md](TEST_PLAN.md) for automated and physical-device checks.

## Focus and library features

Every feature below only adds strictness or comfort. None of them can make blocking weaker, and the block list is always checked before anything else. All of it is unlocked; `Feature` in `Focus/Feature.swift` lists each one so a paid plan could later hide entry points. A locked feature never stops rules the person already chose: added sites stay blocked and pauses keep pausing.

- **Pause before sites.** Settings › Focus › Pause before sites. Categories (Shopping, News, Sports, Celebrity and gossip) and your own sites. Opening a paused site, or any of its subdomains, in the main frame shows a full-screen pause: the site name, "Do you really want this?", a prominent **Go back** (counted as a win), and **Continue**, which unlocks after 10 seconds, then 20, then 30 for later opens of that site on the same day. After Continue, that site opens without another pause in that tab for 5 minutes. Turning a pause on is immediate. Turning one off is scheduled for 24 hours later, can be cancelled, and is applied at launch or when the app becomes active. Major news sites are not in the News category because the built-in list already blocks them. `aws.amazon.com`, `developer.amazon.com` and `sellercentral.amazon.com` are never paused.
- **Sites you added.** Settings › Site blocking › Sites you added. A typed site is reduced to its domain (`https://www.Example.com/x` becomes `example.com`) and then blocked exactly like a listed site: navigation checks, hidden links in wrappers, redirects, and a small sub-resource rule list. Open tabs on the site close at once. There is no delete control.
- **Blocked page.** Says how many times today the site was tried ("You tried YouTube 6 times today."), shows an optional note you write in Settings, and offers Go back, Read later and Start page. None of them lead to the site.
- **Countdowns.** Settings › Focus › Countdowns. The nearest upcoming date shows under the clock ("Finals in 12 days", "Finals tomorrow", "Finals today"). Past dates are hidden.
- **Stats.** Settings › Library › Stats, or the page menu. Blocked attempts per site per day, pauses gone back from or continued, time saved (blocked attempts plus pauses gone back from, times an average you choose, 8 minutes by default), and a streak. A streak day is a local calendar day on which Dull was opened and no pause removal took effect. A day without Dull, or with a removal, ends the streak. History keeps the last 400 days. Nothing leaves the device.
- **Weekly card.** The Stats screen renders a square card with `ImageRenderer` in the current appearance and shares it with the system share sheet. It shows totals only, never site names.
- **Milestones.** 7, 30 and 100 day streaks appear in Stats with the date reached, and once as a quiet line on the start page.
- **Read later.** "Save for later" in the page menu (the `…` in the address field) and when pressing on a link. An optional reading time (default 8–10 pm when turned on) keeps saved pages locked outside those hours, with the time until they open. Opening one goes through blocking and pauses as usual.
- **Bookmarks.** Add or remove from the page menu, edit, delete and reorder in Bookmarks. The first few (4 by default, up to 8) show on the start page. A blocked site can be bookmarked, but it still opens the closed page.
- **Tab grid.** Tabs show as cards with a page preview. A preview is taken only when you leave a tab or the app goes to the background, never while a page is on screen. It is captured at 160 points wide, redrawn at 2x (320×400 pixels) and saved as a JPEG in Caches. Eight previews are kept in memory. Previews are browsing data: they are removed with their tab and by Clear browsing data. Blocked, paused and start-page tabs show a plain card. On the iPhone 17 Pro simulator a capture costs about 2 ms on the main thread and is ready in about 25 ms.

The long-press link menu keeps WebKit's own actions but has no live link preview, because that preview would render the target outside the tab's navigation checks.

### Passwords and passkeys

Dull does not store passwords, passkeys or form data. Sign-in forms are left untouched: no scripts are injected and autofill attributes are not changed, so iOS Password AutoFill works in Dull's web views. On a sign-in field, the key in the QuickType bar opens the Passwords app or the password manager chosen in Settings › General › AutoFill & Passwords. Settings › Passwords explains this and opens those settings.

Two Apple entitlements would add more, and neither is included because both need Apple's approval:

- `com.apple.developer.web-browser` lets an app be chosen as the default browser. Default browsers get fuller AutoFill integration, such as offering to save new passwords.
- `com.apple.developer.web-browser.public-key-credential` lets a WKWebView browser use passkeys and security keys (WebAuthn) for any site. Without it, passkey prompts on web pages do not work in Dull, and sites fall back to a password or another method. The Account Holder of the developer account requests it through the form linked from Apple's documentation for that entitlement.

### Stored data

Everything is kept in the app's preferences as small JSON documents or plain lists, so the Android app can use the same shapes. Dates are ISO 8601; days are local `yyyy-MM-dd`.

| Key | Shape |
| --- | --- |
| `customBlocklist.v1` | Array of domains in the order added: `["example.com"]` |
| `pauseList.v1` | `{"categories":["shopping"],"sites":["example.org"],"removals":[{"kind":"category","value":"shopping","effectiveAt":"2026-09-29T12:00:00Z"}]}` |
| `stats.v1` | `{"days":[{"day":"2026-09-28","blocked":{"youtube.com":3},"paused":{"amazon.com":1},"wentBack":1,"continued":0,"loosened":false}],"milestones":[{"days":7,"reached":"2026-09-28"}],"noticeMilestone":7}` |
| `stats.minutesPerAttempt` | Integer, one of 3, 5, 8, 10, 15 |
| `countdowns.v1` | `[{"id":"…","name":"Finals","day":"2026-12-10"}]` |
| `bookmarks.v1`, `bookmarks.quickLinks` | `[{"id":"…","title":"…","url":"https://…","added":"…"}]`, integer 0–8 |
| `readLater.v1`, `readLater.window.v1` | `[{"id":"…","title":"…","url":"https://…","added":"…","readAt":null}]`, `{"enabled":false,"start":1200,"end":1320}` (minutes after midnight) |
| `blockedPageNote` | String |

The default pause categories are in `Focus/PauseList.swift`. A unit test checks that none of them is already on the block list.

UI tests can map fixture hosts to the pause list with the debug-only launch arguments `-UITestPauseSites 127.0.0.1` and `-UITestPauseSeconds 2`. Release builds ignore them.
