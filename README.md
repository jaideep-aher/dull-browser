# Dull Browser

An Android browser for school and university work. The sites that eat an evening stay closed, and the app has no switch for that.

## Why

The assignment and the distraction live on the same phone. You look something up for a class, a result is right there, and the next hour is news, a feed, or a video. Adult sites work the same way. The page loads, and the work is gone. A habit only grows if the next page opens. If it never opens, that session does not start, and the habit does not get another rep.

Leaving it as a setting does not work. A setting gets turned off at 1am. Dull Browser compiles the list into the app. There is no toggle, no "open anyway", and no exception for one site. The only way around it is a different browser. That extra step is the whole point.

## What it does

A new tab is a clock and a search box. No suggested links, no news, no row of bookmarks waiting to be tapped.

Links from other apps can open here, and they go through the same list. Typing an address does too. So does a Google result: the click is often a redirect, and the redirect stops when the destination is on the list. The page you get is one sentence, the name of the site, and nothing else.

The list is the usual time sinks (social, video, the big news sites) and the adult sites people end up on when they are avoiding work. The full build also blocks ads. Search goes to Kagi.

## The time

There is no honest number of hours to print here. The time you get back is the session that never starts. A result you meant to glance at, a video site, a porn site: each one is easy to call a two-minute look, and then the evening is over. For a student that evening was the reading, the problem set, or sleep. Closed means the tab never becomes the night.

## Build

JDK 21 or newer, and the Android SDK.

```bash
./gradlew assembleSlateFullDebug
```

| Flavor | Application id | Ads blocked |
|---|---|---|
| `slateFull` | `app.slate.browser` | yes |
| `slateLite` | `app.slate.browser.lite` | no |

The site list is in both. It is not the ad-block switch.

Built on Mozilla Public License 2.0. See `LICENSE`.
