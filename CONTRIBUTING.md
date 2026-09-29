# Contributing

Patches that stay compatible with the Mozilla Public License 2.0 are welcome.

Do not add a switch, an allow list, or an "open anyway" for the site list. That list is the product.

See the [feature roadmap](docs/FEATURE_ROADMAP.md) for planned work.

## Branches and releases

This repository is a monorepo: the Android app lives at the root (`app/` and the Gradle files) and the iOS app lives in `ios/`. The only long-lived branches are **`main`**, **`ios`**, and **`android`**. Do not create extra feature branches.

- **`main` is the trunk.** It is the default branch and the source for every release. Never force-push it.
- **`ios` is for iOS work.** Commit iOS changes here, then merge `ios` into `main`.
- **`android` is for Android work.** Commit Android changes here, then merge `android` into `main`.
- Shared docs, the blocklist, and tooling go on `main`, or land on `main` when the platform branches are merged.
- After a merge, fast-forward `ios` and `android` to `main` so the three branches stay aligned. Do not leave old feature branches around.
- **Tag releases per platform** on the `main` commit that was shipped, for example `android-v1.1.0` and `ios-v1.1.0`.

Before opening a pull request, run the checks for the platform you changed:

```bash
# Android
./gradlew testSlateFullDebugUnitTest

# iOS: see ios/README.md for simulator build and test commands
```
