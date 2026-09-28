# Dull Browser

**Look something up. Get back to work.**

Dull is an Android browser for school, university, and focused work. It keeps social feeds, video platforms, news sites, and adult sites closed, with no in-app switch to let them through.

A new tab gives you a clock and a search box. No feed. No suggested links. Nothing asking you to stay.

## Why Dull?

The assignment and the distraction live on the same phone. A quick search can turn into an evening of scrolling. Dull puts a fixed boundary between the two: when a listed site is blocked, there is no “open anyway,” temporary pause, or per-site exception.

You can still use another browser. The point is to make leaving your work a deliberate decision.

## What you get

- **A quiet start page:** a clock and search, without recommended content.
- **Built-in site blocking:** a bundled domain list covering social, video, news, and adult sites, including their subdomains.
- **Checks along the way:** typed addresses, links opened from other apps, and supported redirect links are checked for blocked destinations.
- **A simple blocked page:** the site name and a short message, with no override button.
- **Kagi search by default:** other search engines are available in settings.
- **Optional ad blocking:** available in the Full build, separately from the always-on site list.

## Build and install

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

## How blocking works

The browser checks hosts against [`blocklist.txt`](app/src/main/assets/blocklist.txt), which is bundled in the APK. An entry such as `youtube.com` also covers `m.youtube.com` and `music.youtube.com`. Supported search redirects, AMP links, and Android intent links are inspected for embedded destinations, and navigation checks also cover the destination page.

For top-level navigation to hosts not blocked by the bundled list, the browser also consults Cloudflare's family DNS-over-HTTPS resolver. These checks send the hostname to Cloudflare. If the resolver is unavailable, the additional check allows navigation; the bundled list still applies.

Blocking applies **inside Dull Browser**. It does not restrict other browsers or apps, and a domain list cannot catch every distracting or adult site. Ad-block settings and ad-block exceptions do not disable the site list.

## Maintain the blocklist

The generated list combines the upstream sources defined in [`tools/build-blocklist.py`](tools/build-blocklist.py) with two local files:

- [`tools/extra-domains.txt`](tools/extra-domains.txt) for additional domains.
- [`tools/news-domains.txt`](tools/news-domains.txt) for news domains.

Edit the relevant local file, then regenerate the asset and rebuild:

```bash
python3 tools/build-blocklist.py
./gradlew assembleSlateFullDebug
```

Regeneration requires Python 3 and an internet connection. Review the generated diff before committing it. Normal app builds use the checked-in asset and do not need this step. Changing the bundled list requires a new build; users cannot edit it in the app.

## Tests and contributions

Run the Full variant's unit tests:

```bash
./gradlew testSlateFullDebugUnitTest
```

Tests include domain matching and redirect extraction. See [`CONTRIBUTING.md`](CONTRIBUTING.md) before proposing changes. The core constraint is intentional: no switch, allowlist, or “open anyway” for the site blocker.

Report bugs or incorrect blocks in [Issues](https://github.com/jaideep-aher/dull-browser/issues), including the affected URL, app variant, Android version, and steps to reproduce.

## License

Licensed under the [Mozilla Public License 2.0](LICENSE).
