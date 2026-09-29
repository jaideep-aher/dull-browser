# Dull Browser feature roadmap

This document describes what Dull Browser does today, what is planned, how each feature will be built on Android and iOS, which tier it belongs to, and how the product will be launched. It is a working plan: statuses and dates will change as features ship.

## Contents

- [Product principle](#product-principle)
- [Dull and the Android launcher](#dull-and-the-android-launcher)
- [Tiers and pricing](#tiers-and-pricing)
- [How to read the feature tables](#how-to-read-the-feature-tables)
- [1. Blocking core](#1-blocking-core)
- [2. Study and focus](#2-study-and-focus)
- [3. Motivation and growth](#3-motivation-and-growth)
- [4. Calm browsing](#4-calm-browsing)
- [5. Lockdown integration](#5-lockdown-integration)
- [6. Pro: system-level and ecosystem features](#6-pro-system-level-and-ecosystem-features)
- [Non-goals](#non-goals)
- [Build order](#build-order)
- [Launch strategy](#launch-strategy)
- [Competitor reference](#competitor-reference)

## Product principle

Dull is a web browser that permanently blocks distractions. Social media, video feeds, porn and news sites are blocked inside the browser, and there is no switch, allowlist, pause or "open anyway" button.

**Nothing in any tier ever loosens blocking.** Paid tiers buy more strictness and more comfort, never less blocking. Every paid feature either adds restrictions (custom blocks, keyword blocks, schedules, study sessions, app blocking) or makes a restricted life easier to live with (reader mode, widgets, stats, sync). If a proposed feature would let a user reach a site that the free app blocks, it does not ship.

The audience is:

- **Adults practicing digital minimalism.** People moving to a "dumbphone" setup, cutting back on social media, or trying to stop the habit of opening a feed without thinking. Pitch: "The browser you can't talk your way out of."
- **College and university students.** People who need Google, Canvas and Docs but lose hours to YouTube and TikTok, especially around exams. Pitch: "Delete the scroll. Keep Google."

Parents managing a child's phone are **not** a target audience for Dull. That use case needs remote management, child accounts and a different privacy posture, so it belongs in a separate app.

## Dull and the Android launcher

The owner also builds a separate minimalist Android launcher, which will gain Opal and one sec-style app blocking. The two products split the work:

- **The launcher governs apps.** It decides which apps are visible, which are paused behind a delay, and which are blocked during focus time.
- **Dull governs the web.** It decides which sites load, in any browser session the user starts through Dull.
- **They link.** On Android, the launcher can hide other browsers so Dull becomes the only way onto the web, and the two share focus modes, study sessions, schedules and stats through the launcher bridge (feature 6.2).

iOS has no third-party launchers. On iOS, the equivalent of the launcher's app control comes from Apple's Screen Time API (FamilyControls, ManagedSettings and DeviceActivity), offered in Dull Pro (features 6.1 and 6.3).

## Tiers and pricing

| Tier | Price | What it buys |
| --- | --- | --- |
| **Free** | Free | Permanent blocking, bypass hardening, forced SafeSearch, basic study sessions, stats, share card, lockdown guide. Drives downloads and ratings. |
| **Dull Plus** | One-time, about $7.99 (with a $4.99 introductory price for the first two weeks) | On-device strictness and comfort: custom add-only blocklist, keyword blocking, commitment lock, custom study sessions, study allowlist, schedules, grayscale, unlimited pauses and quick links. Shared through Apple Family Sharing. |
| **Dull Pro** | $3.99/month, $29.99/year, or $59.99 lifetime | Ecosystem and system-level features: iOS app blocking through Screen Time, the Android launcher bridge, iOS app pauses, long-term insights, sync and an accountability partner. |

Plus is a one-time purchase because buyers in this category strongly prefer paying once, and every Plus feature runs entirely on the device with no ongoing cost. Pro is a subscription (with a lifetime option) because its features depend on ongoing platform work, entitlements and, for sync, infrastructure.

**Store fees.** Apple charges 15% under the App Store Small Business Program. Google charges 15% on subscriptions, and 15% on the first $1M of annual revenue for one-time purchases under its reduced service fee tier. Pricing above assumes those rates.

**Paywall placement.** The paywall appears softly after the user finishes the lockdown setup, and at the first "I want to add a site" moment. It never blocks the core function: the free app always blocks the full built-in list.

## How to read the feature tables

Each feature lists:

- **What it does**, in plain terms.
- **Android** and **iOS**: the intended implementation on each platform.
- **Tier**: Free, Plus or Pro.
- **Status**: *Exists* (shipped on `main`) or *Planned*.

## 1. Blocking core

### 1.1 Permanent built-in blocklist

- **What it does:** Blocks a bundled list of social media, video, porn and news domains, including their subdomains. The list ships inside the app and cannot be changed from within it. Hostnames not on the local list are also checked against Cloudflare's family DNS-over-HTTPS resolver.
- **Android:** `WebViewClient.shouldOverrideUrlLoading` stops top-level navigations to blocked hosts, and `shouldInterceptRequest` stops subresource requests (frames, scripts, media) so blocked content cannot load inside other pages.
- **iOS:** `WKNavigationDelegate` cancels navigations to blocked hosts, and a compiled `WKContentRuleList` built from the same list blocks subresource loads inside WebKit.
- **Tier:** Free
- **Status:** Exists on both platforms.

### 1.2 Bypass hardening

- **What it does:** Closes the common routes people use to reach a blocked site without visiting its domain directly:
  - Translation proxies, such as `*.translate.goog` and `translate.google.com/translate?u=...`.
  - Caches and archives, such as `web.archive.org` and `archive.ph`.
  - AMP and Google cache URLs that serve a blocked page from another host.
  - Image and video search results that preview or link to blocked sites.
  - Embeds, such as `youtube-nocookie.com` players on otherwise allowed pages.
  - Known public web proxies.
- **Shared design:** A single URL-unwrapping resolver, used on both platforms, extracts the real destination from wrapper parameters such as `u=`, `url=` and `q=`, and from proxy hostname encodings (for example `www-reddit-com.translate.goog`). The unwrapped URL is checked against the blocklist. Proxy, cache and archive hosts ship as a bundled, versioned bypass list so that new routes can be added in regular updates.
- **Android:** The resolver runs in `shouldOverrideUrlLoading` and `shouldInterceptRequest` before the blocklist check. Unit tests cover each known wrapper format.
- **iOS:** The resolver runs in the `WKNavigationDelegate` decision handler. Proxy and archive hosts are also added to the compiled content rule list.
- **Tier:** Free
- **Status:** Planned. Basic redirect extraction for search result links already exists.

### 1.3 Forced SafeSearch and quieter results

- **What it does:** Forces SafeSearch on Google, Bing and DuckDuckGo, and hides the video and "Top stories" carousels on Google results, which are the most common way back into feeds and news.
- **Android:** Rewrite search URLs to include `safe=active` (and the Bing and DuckDuckGo equivalents) before loading. Hide carousels with CSS injected at document start through `WebViewCompat.addDocumentStartJavaScript`.
- **iOS:** Rewrite search URLs in the navigation delegate. Hide carousels with a `WKUserScript` at document start, or with `css-display-none` rules in the content rule list.
- **Tier:** Free
- **Status:** Planned.

### 1.4 Add-only custom blocklist

- **What it does:** Lets the user add their own domains to block. Entries can be added at any time and take effect immediately. They cannot be removed instantly. An optional removal path schedules the removal with a 7-day cooldown, which the user can cancel.
- **Android:** Store entries in DataStore (or Room, if the list grows or needs metadata). Merge with the built-in list at lookup time.
- **iOS:** Store entries in SwiftData or a small JSON file. Recompile and reinstall the `WKContentRuleList` whenever the list changes.
- **Tier:** Plus
- **Status:** Exists on both platforms, with no removal path at all.

### 1.5 Keyword blocking

- **What it does:** Blocks any URL, and optionally any search query, that contains a user-chosen keyword (for example "celebrity" or a game's name). Like the custom list, keywords are add-only.
- **Android:** Match keywords against the unwrapped URL and search query in `shouldOverrideUrlLoading`.
- **iOS:** Match in the navigation delegate. Simple keywords can also be compiled into `url-filter` rules.
- **Tier:** Plus
- **Status:** Planned.

### 1.6 Pause before a site

- **What it does:** A one sec-style interstitial for sites the user wants to use less but not block. Opening the site shows a short breathing pause and asks whether the user still wants to continue. The delay grows each time the site is opened in a day.
- **Android:** Intercept the navigation, show a local interstitial page, and continue to the site only after the countdown finishes.
- **iOS:** Same flow, rendered with `loadHTMLString` or a native SwiftUI overlay.
- **Tier:** Free for 1 site, Plus for unlimited sites.
- **Status:** Exists on both platforms (native overlay, category lists and your own sites). This only applies to sites that are not already blocked; it never offers a way into a blocked site.

### 1.7 Commitment lock

- **What it does:** Makes stricter changes instant and looser changes slow. Adding a block, lengthening a session or adding a schedule takes effect immediately. Removing a custom block, shortening a schedule or disabling a pause takes effect after a 24-hour delay, or requires typing a long confirmation phrase, depending on the user's choice.
- **Android:** Store pending changes with their due time, apply them with WorkManager, and re-check on app launch in case the job was delayed.
- **iOS:** Store pending changes with their due time, schedule `BGTaskScheduler` work, and re-check on launch, since background tasks are not guaranteed to run on time.
- **Tier:** Plus
- **Status:** Partly exists on both platforms: turning off a pause waits 24 hours and is checked at launch. Planned for other changes.

## 2. Study and focus

### 2.1 Study sessions

- **What it does:** A focus timer that cannot be cancelled once started. During a session, Dull blocks more than usual (for example, everything except the study allowlist in 2.2).
- **Android:** A foreground service with an ongoing notification shows the time remaining. A Quick Settings `TileService` starts a session in one tap.
- **iOS:** A Live Activity shows the countdown on the Lock Screen and Dynamic Island. The session's end time is persisted so the session survives the app being closed, and a local notification marks the end.
- **Tier:** Free for 25-minute sessions, Plus for custom lengths.
- **Status:** Planned.

### 2.2 Study allowlist preset

- **What it does:** A ready-made list of study sites that stay open during a study session, while everything else is blocked: Google, Google Scholar, Canvas, Blackboard, Moodle, Google Docs and Drive, Notion, Wikipedia, JSTOR, Khan Academy, Quizlet, Overleaf and Wolfram Alpha. Users can add their own sites, such as their university portal.
- **Android and iOS:** Stored with the custom list. During a session, the navigation check allows only hosts on the preset plus the user's additions. The permanent blocklist still applies on top.
- **Tier:** Plus
- **Status:** Planned.

### 2.3 Schedules

- **What it does:** Turns on stricter modes at set times, such as "study allowlist only, 9 a.m. to 5 p.m. on weekdays" or "no news after 10 p.m."
- **Android:** `AlarmManager` fires at schedule boundaries to update state and notifications. Schedules are re-evaluated on app launch and after boot (`BOOT_COMPLETED`) so a missed alarm never leaves a schedule off.
- **iOS:** Each navigation checks the active schedule against the current time, so enforcement does not depend on background execution. A local notification announces when a schedule starts.
- **Tier:** Plus
- **Status:** Planned.

### 2.4 Exam countdown

- **What it does:** Shows a countdown to the user's next exam or deadline on the start page and in a home screen widget.
- **Android:** A Glance widget.
- **iOS:** A WidgetKit widget.
- **Tier:** Free
- **Status:** Start page countdown exists on both platforms; widgets are planned.

### 2.5 Focus mode filters

- **What it does:** Ties Dull's stricter modes to the phone's own focus modes. For example, when the iPhone's "Study" Focus turns on, Dull switches to the study allowlist.
- **Android:** Limited. Dull can listen for Do Not Disturb changes (`NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED`), but Android does not expose named focus modes to other apps.
- **iOS:** App Intents with `SetFocusFilterIntent`, configured by the user in Settings > Focus.
- **Tier:** Plus (primarily iOS).
- **Status:** Planned.

### 2.6 Shortcuts and automations

- **What it does:** Lets users start a study session or switch modes from automations, voice assistants or home screen shortcuts.
- **Android:** App Actions, plus deep links such as `dullbrowser://session/start?min=50`.
- **iOS:** App Intents, available in Shortcuts, Siri and Spotlight.
- **Tier:** Plus
- **Status:** Planned.

## 3. Motivation and growth

### 3.1 On-device stats

- **What it does:** Counts blocked attempts, study minutes and sessions completed, by day and by category. Nothing leaves the device.
- **Android:** Room database.
- **iOS:** SwiftData.
- **Tier:** Free
- **Status:** Blocked attempts and pause outcomes exist on both platforms, stored as a small JSON document. Study counts wait for 2.1.

### 3.2 Weekly share card

- **What it does:** Creates a shareable image such as "Dull blocked 214 attempts this week, 6 hours of study." This is the main growth loop: users post it on TikTok, Instagram Stories and Reddit, and the card carries the app name.
- **Android:** Render a Compose layout to a `Bitmap`, save it to the cache directory, and share it through a `FileProvider` URI.
- **iOS:** Render a SwiftUI view with `ImageRenderer` and share it with `ShareLink`.
- **Tier:** Free
- **Status:** Exists on both platforms.

### 3.3 Streaks and milestones

- **What it does:** Tracks streaks of days with study sessions or without blocked attempts, and celebrates milestones at 7, 30 and 100 days.
- **Android and iOS:** Computed from the on-device stats in 3.1.
- **Tier:** Free
- **Status:** Exists on both platforms, counting days Dull was opened with no pause turned off.

### 3.4 Blocked page with an intent message

- **What it does:** Replaces the plain "blocked" message with a calm page that reminds the user why they installed Dull. The free version uses a default message; Plus lets the user write their own (for example, "You wanted to finish the thesis draft").
- **Android:** `WebView.loadDataWithBaseURL` with a local HTML template.
- **iOS:** `WKWebView.loadHTMLString` with a local HTML template.
- **Tier:** Free (default message), Plus (custom message).
- **Status:** Exists on both platforms with today's attempt count, your own note and ways out. iOS uses a native page; Android uses an HTML page. There is still no continue-anyway.

### 3.5 Insights history

- **What it does:** Keeps 12 months of history, shows trends over time, and exports the data as CSV.
- **Android:** Charts with Vico or a small custom Compose `Canvas` chart.
- **iOS:** Swift Charts.
- **Tier:** Pro
- **Status:** Planned.

## 4. Calm browsing

### 4.1 Reader mode

- **What it does:** Shows articles as clean text without ads, sidebars or recommendations.
- **Android:** Inject Readability.js with `evaluateJavascript` and render the extracted article in a local template.
- **iOS:** Inject Readability.js with a `WKUserScript` and render the result in a local template.
- **Tier:** Free
- **Status:** Planned.

### 4.2 Distraction removal

- **What it does:** Hides comment sections, "recommended for you" panels, cookie banners and autoplaying media on allowed sites. Free users get built-in presets; Plus users can add their own element-hiding rules.
- **Android:** CSS and small scripts injected at document start with `WebViewCompat.addDocumentStartJavaScript`.
- **iOS:** `WKUserScript` at document start, plus `css-display-none` actions in the content rule list.
- **Tier:** Free (presets), Plus (custom rules).
- **Status:** Planned.

### 4.3 Grayscale mode

- **What it does:** Shows web pages in grayscale, which makes colorful, attention-grabbing pages less appealing.
- **Android:** Set the WebView to a hardware layer with a `Paint` whose `ColorMatrixColorFilter` has saturation 0.
- **iOS:** An injected CSS `filter: grayscale(1)`, or SwiftUI's `.grayscale(1)` modifier on the web view.
- **Tier:** Plus
- **Status:** Planned.

### 4.4 Intentional quick links

- **What it does:** Lets the user pin a few chosen sites to the start page. There is deliberately no "most visited" list, which would encourage habit loops.
- **Android and iOS:** A small stored list shown on the start page.
- **Tier:** Free for 4 links, Plus for 12.
- **Status:** Exists on both platforms as bookmarks shown on the start page (4 by default, up to 8).

### 4.5 Read-later queue

- **What it does:** Saves links to read later instead of opening them now. An optional time lock keeps the queue closed until a chosen time (for example, after the study session). Links can be sent to Dull from other apps.
- **Android:** Accept shared links through an `ACTION_SEND` intent filter.
- **iOS:** A Share Extension that writes to a shared App Group container.
- **Tier:** Free for 20 items, Plus for unlimited.
- **Status:** Exists on both platforms inside the app, with an optional reading time. Sharing into Dull from other apps is planned.

### 4.6 Feedless start page

- **What it does:** The start page is a clock and a search box. There is no news feed, no suggested content and no trending list.
- **Tier:** Free
- **Status:** Exists on both platforms.

## 5. Lockdown integration

### 5.1 Guided lockdown setup

- **What it does:** A step-by-step checklist, with screenshots, that makes Dull the only way onto the web. This is what turns Dull from a blocklist into real enforcement.
- **iOS:**
  1. Set Dull as the default browser. This requires Apple's `com.apple.developer.web-browser` entitlement, which must be requested from Apple.
  2. In Screen Time > Content & Privacy Restrictions > Allowed Apps, turn Safari off.
  3. In iTunes & App Store Purchases, turn off installing apps so other browsers cannot be installed.
  4. Set a Screen Time passcode and have a friend enter it, so the user does not know it.
- **Android:** Request the browser role with `RoleManager.ROLE_BROWSER` so Dull becomes the default browser. The owner's launcher can then hide other browsers. Without the launcher, the guide explains how to disable or uninstall other browsers where the device allows it.
- **Tier:** Free
- **Status:** Planned.

### 5.2 Widgets

- **What it does:** Home screen widgets for search, study sessions, streaks and the exam countdown.
- **Android:** Glance widgets.
- **iOS:** WidgetKit widgets, including Lock Screen widgets.
- **Tier:** Free (search and countdown widgets), Plus (session and stats widgets).
- **Status:** Planned.

### 5.3 In-app browser guidance and known limitations

- **What it does:** Explains how to make links from other apps open in Dull where possible, for example by changing each app's "open links in" setting.
- **Known limitation:** Browsers built into other apps, such as those in Gmail, Discord and the Google app, render pages themselves and bypass Dull. Dull cannot block them. The setup guide says this plainly. On iOS, Pro app blocking (6.1) can limit those apps; on Android, the launcher can.
- **Tier:** Free
- **Status:** Planned.

## 6. Pro: system-level and ecosystem features

### 6.1 iOS app blocking with Screen Time

- **What it does:** Blocks or limits other apps on the iPhone, such as Instagram, TikTok and games, using Apple's Screen Time API. It can also shield Safari itself, which strengthens the lockdown in 5.1.
- **iOS:** Request `FamilyControls` authorization with `.individual` (the user authorizes their own device). Apply restrictions with `ManagedSettingsStore`, run schedules with `DeviceActivity` monitors, and brand the block screen with a `ShieldConfiguration` extension. The FamilyControls distribution entitlement requires Apple's approval, which can take weeks, so **apply early**.
- **Android:** Not built into Dull. App blocking on Android is the launcher's job (see 6.2).
- **Tier:** Pro
- **Status:** Planned.

### 6.2 Launcher bridge (Android)

- **What it does:** Connects Dull and the owner's Android launcher so they act as one system: shared focus modes, study sessions and schedules, and combined stats across apps and the web.
- **Android:** A `ContentProvider` or bound service protected by a signature-level permission, so only apps signed with the same key can talk to each other. Simpler actions can use `dullbrowser://` intents. Both apps read and write the same mode, session and schedule state.
- **iOS:** Not applicable; 6.1 and 6.3 cover the iOS equivalent.
- **Tier:** Pro
- **Status:** Planned. To be built alongside the launcher's app blocking.

### 6.3 iOS app pause

- **What it does:** A one sec-style pause for apps. When a shielded app is opened, the user sees a short pause and can choose to open it for a limited time.
- **iOS:** A `ShieldAction` extension handles the button on the shield screen, then temporarily removes that app from the `ManagedSettingsStore` restrictions and schedules the shield to return.
- **Tier:** Pro
- **Status:** Planned.

### 6.4 Sync

- **What it does:** Syncs custom lists, schedules, quick links and stats between the user's devices.
- **iOS:** CloudKit private database, which needs no server and keeps data in the user's own iCloud account.
- **Android:** Requires either a small backend or Google Drive's app data folder.
- **Later:** A desktop browser extension that shares the same lists.
- **Tier:** Pro
- **Status:** Planned. Last in the build order.

### 6.5 Accountability partner

- **What it does:** Lets a trusted friend hold a one-time code that is required to approve loosening changes (for example, removing a custom block), instead of the 24-hour delay in 1.7. Works without a server: the friend receives the code once and reads it back when asked.
- **Android and iOS:** Generate a random code on the device, show it once for the user to send to the friend, and store only a hash of it.
- **Tier:** Pro
- **Status:** Planned.

## Non-goals

These are deliberately out of scope:

- **Break passes, "5 more minutes" or any temporary unblock.** They undo the product's core promise.
- **Social features, leaderboards or accounts in the free tier.** They add data collection and turn focus into another feed.
- **Parental remote controls.** Managing a child's phone needs a different product, so it belongs in a separate app.
- **System-wide app blocking inside Dull on Android.** The owner's launcher handles app blocking on Android; Dull stays a browser.

## Build order

### Before launch

- 1.2 Bypass hardening
- 1.3 Forced SafeSearch and quieter results
- 1.4 Add-only custom blocklist
- 2.1 Study sessions
- 2.2 Study allowlist preset
- 3.1 On-device stats
- 3.2 Weekly share card
- 3.4 Blocked page with an intent message
- 5.1 Guided lockdown setup
- Dull Plus in-app purchase with StoreKit 2 on iOS and Google Play Billing on Android
- Onboarding that asks "Who is this for? Me / Student" and sets default lists and tone
- Store and compliance basics: privacy policy (disclosing that hostnames are sent to Cloudflare), App Store privacy labels, age-rating answers, Play target audience 13+, App Review notes

### First month after launch

- 2.3 Schedules
- 1.6 Pause before a site
- 4.1 Reader mode
- 4.2 Distraction removal
- 4.3 Grayscale mode
- 5.2 Widgets
- 2.6 Shortcuts and automations

### Pro

1. **Apply for the FamilyControls distribution entitlement now**, since approval can take weeks.
2. Build 6.2 Launcher bridge alongside the launcher's app blocking.
3. Build 6.1 iOS app blocking and 6.3 iOS app pause once the entitlement is approved.
4. Build 6.4 Sync last.

## Launch strategy

### Platforms

- **iOS is the primary paid platform.** Screen Time can turn Safari off and block new installs, which lets Dull become the only browser on the phone. That is real enforcement, and it is where users pay. Submit about two weeks before launch, with review notes that open with the permanent-blocking design.
- **Android launches the same day as a free companion.** Check whether the Play developer account needs the closed-testing requirement for newer personal accounts (12 testers for 14 days) and start it early if so. Cross-promote from the owner's existing Android apps.

### One app, one store page per audience

Ship **one app**, not separate copies per audience. Near-identical apps are rejected under App Store guideline 4.3 (spam), split ratings, and multiply maintenance.

- The first screen asks **"Who is this for?"** with two answers: **Me** or **Student**. The answer sets the default lists, the tone and which setup guide appears first.
- Each audience gets its own marketing through **Custom Product Pages** on the App Store and **custom store listings** on Google Play, each with its own screenshots, headline and ad group.

### Timing

- **Target launch: November 10–17, 2026**, ahead of December finals, when students are most motivated to cut distractions.
- **Second wave: January 2027**, around New Year's resolutions, aimed mainly at adults.

### Pricing and expected conversion

- Free core, Plus at $4.99 for the first two weeks and $7.99 after, Pro as described in [Tiers and pricing](#tiers-and-pricing).
- Expect roughly **2–4% of iOS users** and **0.5–1% of Android users** to buy Plus. At 30,000 iOS downloads, that is roughly $4,000–7,000 after Apple's 15% commission. Downloads, not revenue, are the realistic first-year win.

### Go-to-market channels (about $3,000–5,000 over 90 days)

- **App store search optimization** (free, highest return): a title such as "Dull: Distraction-Free Browser", with keywords like dumbphone, social media blocker, focus, study, block websites and screen time. Localize listings for the UK, Canada and Australia.
- **Communities** (free): honest posts in r/dumbphones, r/nosurf, r/digitalminimalism and r/college.
- **Launch sites** (free): Product Hunt and a "Show HN" post on Hacker News. The no-off-switch design and open-source code suit both.
- **Micro-creators** (about $1,500–2,500): 10–20 digital-minimalism and study-with-me creators on TikTok and Instagram, paid per post plus a per-install bonus.
- **Apple Search Ads** (about $1,000–1,500): one ad group per audience, each pointing to its own Custom Product Page. Pause keywords that cost more than about $1.50 per install.
- **Campus ambassadors** (about $500): 5–10 students timed to finals, with QR-code flyers in libraries.
- **Press** (January): digital-wellbeing and productivity writers, around resolutions.

### 90-day targets

- **Keep investing if:** 15,000+ downloads, a 4.5+ rating, 20%+ of users still active after 30 days, and 2%+ of iOS users buying Plus. Then move on to Pro.
- **Change course if:** fewer than 5,000 downloads or fewer than 10% of users active after 30 days. Then stop paid marketing, keep the app free, and treat it as an open-source and portfolio project.
- **Measure privately:** store analytics and on-device counts only. No third-party trackers.

### Key risks

- **App Review.** Browser apps draw scrutiny on age rating ("Unrestricted Web Access"), guideline 4.2 (minimum functionality) and 4.3 (spam). Mitigate with forced SafeSearch, the built-in blocklist, real browsing features and clear review notes.
- **In-app browser bypass.** Browsers built into Gmail, Discord, the Google app and others bypass Dull. Say so in the setup guide; Pro app blocking on iOS and the launcher on Android reduce the gap.
- **Free competitors.** ScreenZen, Nova and Apple's own Screen Time are free. Dull's answer is the combination of permanent blocking, the lockdown guide and keeping normal Google search.
- **Children's privacy and US state age laws** (COPPA, and laws in states such as Texas, Utah and Louisiana). v1.1 collects no user data, which avoids most obligations. Keep it that way.
- **Google Play closed testing.** Newer personal developer accounts must run a closed test before production release. Check early so it does not delay launch.

## Competitor reference

Prices are approximate and change often; check current store listings before quoting them.

| Product | Positioning | Pricing (approximate) |
| --- | --- | --- |
| Opal | Premium iOS/Android app blocker built on Screen Time, with focus sessions and reports. | Subscription around $100/year, lifetime option. |
| one sec | Adds a breathing pause before opening distracting apps. | Around $20–30/year subscription. |
| BlockSite | Cross-platform site and app blocker with schedules. | Subscription around $40–60/year. |
| Freedom | Blocks sites and apps across all devices with synced sessions. | About $40/year, lifetime option. |
| ScreenZen | Free app pause and limit tool on iOS and Android. | Free, donation-supported. |
| Cold Turkey | Strict desktop blocker known for locks that cannot be undone. | One-time purchase, around $40. |
| SPIN Safe Browser | Filtered browser aimed at blocking porn; social media stays open. | Around $20/year. |
| Anti Browser | Minimal browser that blocks search and feeds almost entirely. | Low-cost or one-time. |
| Nova | Minimal browser for dumbphone-style setups with very restricted browsing. | Free with paid options. |
| midpoint | Adds friction before distracting apps and sites. | Subscription with a free tier. |

Dull's open position: normal Google search, with social media, video, porn and news permanently gone, and nothing the user can switch off.
