# ZnaiKo (Talkto) - notes for Claude

ZnaiKo (Знайко) is an Android virtual pet for children that is also an AI assistant (Claude through the Anthropic Java SDK). Owner: Tanatos1987. Talk to the owner in Bulgarian.

- Repo: https://github.com/Tanatos1987/Talkto. Working branch: `claude/talkto-android-app-k5s2jz` (PR #2). Push only there.
- Full history, Google Play Console status and the exact declaration texts: `docs/PROJECT_NOTES.md` (Bulgarian).
- Sister project with the same toolchain: Panelka (https://github.com/Tanatos1987/Panelka).

## Stack

- Kotlin 2.3 + Jetpack Compose, AGP 9.4 (built-in Kotlin: no `org.jetbrains.kotlin.android` plugin), Gradle 9.6, minSdk 30, targetSdk 36.
- `:core`: pure Kotlin business logic with fast JVM tests. `:app`: Android UI, services, Room, DataStore, ML Kit, Shizuku.
- Product flavors:
  - `full`: package `com.talkto.app`, every feature;
  - `play`: package `znaiKo.app`, the Google Play build. Its manifest removes all-files access, QUERY_ALL_PACKAGES, the accessibility service and the gallery permissions (photos only through the system picker); hiddenapibypass is a `full`-only dependency loaded by reflection.
- CI (`.github/workflows/android.yml`): core tests, Robolectric tests, lint, a debug APK, a signed `bundlePlayRelease` AAB built from repository secrets (TALKTO_KEYSTORE_B64, TALKTO_KEYSTORE_PASSWORD, TALKTO_KEY_ALIAS, TALKTO_KEY_PASSWORD), a `launch` job that opens the debug APK, the release APK and the Play bundle on an emulator and fails on a start-up crash, and a `release` job that publishes every push to the working branch that passed `launch` as GitHub Release `v1.1.<run>` (the owner installs from Releases). versionCode is the CI run number, versionName `1.1.<run>`.

## Rules

- Never commit keystores, passwords or API keys.
- Never write the owner's email into the repo.
- Any change to the `play` flavor must keep restricted permissions out of its merged manifest.
- R8 problems show only in release builds: a red `launch` job means the build does not open on a phone.
- Releases come only from CI's `release` job; never create one by hand. A release found broken on a phone goes into that job's "Remove releases that do not open" list (v1.1.94 is there).
