# Contributing

Patches that stay compatible with the Mozilla Public License 2.0 are welcome.

Do not add a switch, an allow list, or an "open anyway" for the site list. That list is the product.

See the [feature roadmap](docs/FEATURE_ROADMAP.md) for planned work.

## Branches and releases

This repository is a monorepo: the Android app lives at the root (`app/` and the Gradle files) and the iOS app lives in `ios/`. Platforms are folders, not long-lived branches.

- **`main` is the trunk.** It is the default branch and the source for every release on both platforms. Never force-push it.
- **Work on short-lived branches** created from `main`, named with a platform prefix:
  - `android/...` for Android changes, for example `android/study-sessions`
  - `ios/...` for iOS changes, for example `ios/lockdown-guide`
  - `docs/...` for documentation, for example `docs/feature-roadmap`
  - `shared/...` for changes to the blocklist, tools or both apps at once
- **Open a pull request into `main`** for every change, and merge once checks pass. Delete the branch after merging unless it is kept on purpose.
- **Tag releases per platform** on the `main` commit that was shipped, for example `android-v1.1.0` and `ios-v1.1.0`.
- **The legacy `ios` branch** is kept for history only. The iOS app now lives in `ios/` on `main`; do not base new work on the `ios` branch.

Before opening a pull request, run the checks for the platform you changed:

```bash
# Android
./gradlew testSlateFullDebugUnitTest

# iOS: see ios/README.md for simulator build and test commands
```
