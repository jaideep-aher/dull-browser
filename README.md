# Dull Browser

**Less scrolling. More living.**

Dull Browser is an **Android browser built to help you reduce screen time** by blocking addictive distractions: **YouTube, social media, porn, and other time-wasting sites**. Search for what you need, finish what you came to do, and put your phone down.

No “just five more minutes” button. Blocked sites stay blocked.

## What is it for?

- **Spend less time online.** Stop a quick lookup from becoming an hour of scrolling.
- **Stay focused.** Browse for study, work, and everyday tasks without drifting into feeds or videos.
- **Make distraction harder.** The blocklist is built in, with no in-app switch to turn it off.

## What does it block?

| Distraction | Examples |
| --- | --- |
| Video rabbit holes | YouTube, including mobile and short links |
| Social feeds | Instagram, TikTok, Facebook, X/Twitter, Reddit |
| Porn | Adult sites on the blocklist, plus additional family DNS filtering |
| News scrolling | News sites on the blocklist |

See the [full site list](app/src/main/assets/blocklist.txt). Listed domains and their subdomains are blocked, including when you reach them through supported search redirects.

## How do I use it?

1. **Open Dull** when you need to look something up.
2. **Search or enter a website.** The start page is just a clock and a search box.
3. **Browse with fewer distractions.** If a site is blocked, you get a short message instead of the page. There is no “open anyway.”

Search defaults to Kagi and can be changed in settings. The Full build also offers optional ad blocking.

**Scope:** Dull blocks sites inside this browser. It does not block other apps or browsers. It is designed to help you spend less time browsing; it does not promise a specific number of hours saved or catch every distracting site.

## Install from source

<details>
<summary>Android build and install instructions</summary>


### Requirements

- An Android device or emulator running **Android 9 (API 28) or newer**.
- **JDK 21** for the project's Java toolchain.
- An Android SDK installation with **compile SDK 37, minor version 2**, and **Build Tools 37.0.0**, as configured in [`app/build.gradle.kts`](app/build.gradle.kts).
- `ANDROID_HOME` pointing to your SDK, or an `sdk.dir` entry in a local `local.properties` file.

Clone the repository and build a debug APK:

```bash
git clone https://github.com/jaideep-aher/dull-browser.git
cd dull-browser
./gradlew assembleSlateFullDebug
```

The APK is written to:

```text
app/build/outputs/apk/slateFull/debug/app-slateFull-debug.apk
```

To build and install on a connected device with USB debugging enabled, or a running emulator:

```bash
./gradlew installSlateFullDebug
```

On Windows, use `gradlew.bat` in place of `./gradlew`.

### Build variants

| Variant | Application ID | Site blocking | Ad blocking |
| --- | --- | --- | --- |
| `slateFull` | `app.slate.browser` | Always on | Available; off by default |
| `slateLite` | `app.slate.browser.lite` | Always on | Unavailable |

For the Lite build, use `assembleSlateLiteDebug` or `installSlateLiteDebug`. The `slate` names are internal build and package identifiers; the app is Dull Browser.

</details>

## How blocking works

The site list ships with the app and stays on in both builds. Changing it requires rebuilding the app. Ad-block settings do not affect site blocking.

For page visits not blocked by the local list, Dull also checks the hostname with Cloudflare's family DNS-over-HTTPS resolver. This sends the hostname to Cloudflare. If that check fails, navigation is allowed unless the local list blocks it.

## Contribute

Found a site that should be blocked, or a useful site blocked by mistake? [Open an issue](https://github.com/jaideep-aher/dull-browser/issues) with the URL and what happened.

<details>
<summary>Update the blocklist and run tests</summary>

Edit [`tools/extra-domains.txt`](tools/extra-domains.txt) or [`tools/news-domains.txt`](tools/news-domains.txt), then regenerate and rebuild:

```bash
python3 tools/build-blocklist.py
./gradlew assembleSlateFullDebug
```

The script merges upstream sources with the local lists. It requires Python 3 and internet access. Review the generated diff before committing. Normal builds use the checked-in list and skip this step.

Run unit tests, including domain matching and redirect extraction:

```bash
./gradlew testSlateFullDebugUnitTest
```

</details>

Read [CONTRIBUTING.md](CONTRIBUTING.md) before proposing changes. Keep the core idea intact: no switch, allowlist, or “open anyway” for blocked sites.

## License

[Mozilla Public License 2.0](LICENSE).
