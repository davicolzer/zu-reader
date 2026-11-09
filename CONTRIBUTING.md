# Contributing to Zu Reader

Thanks for helping! Issues and pull requests can be in **English or Portuguese**.
*Issues e PRs podem ser em inglês ou em português.*

## Reporting a wrong reading

The most useful contribution is a **photo the app reads wrong**, together with the correct text.
Open a [bug report](../../issues/new?template=bug_report.yml) and attach it. With your permission,
the photo may be added to `testdata/real/` so the fix stays covered by the tests.

## Setting up

1. Install **JDK 25** and the **Android SDK** with platform 37 (Android Studio does this).
2. Fork the repository and clone your fork.
3. Run `./gradlew build` once: it compiles, runs every test and runs lint.

Most of the reading logic lives in the `core` modules, which are plain Kotlin: you can work on them
and run `./gradlew :core:vision:test` without an emulator.

## Making a change

1. Create a branch from `staging` and open the pull request **against `staging`** (merged changes are tested
   on a phone there before going to `main`).
2. Keep the change focused: one fix or feature per pull request.
3. Add or update tests:
   - reader changes: a test in `core/vision` or `core/zu` (real photos in `testdata/real/`,
     or synthetic images like `CommandSyntheticTest`);
   - reader accuracy must not drop on the existing real photos (`RealPhotosTest`, `CommandReaderTest`).
4. App texts go in **both** `app/src/main/res/values/strings.xml` (English) and `values-pt/strings.xml` (Portuguese).
5. Screens must work in the **light and dark themes**. Attach screenshots of both for UI changes.
6. Run `./gradlew build` before opening the pull request. CI runs the same tests and lint. After the
   merge into `staging`, a comment on your pull request brings the link to download the test APK.

## Code style

- Kotlin official style (the IDE's default formatter).
- Jetpack Compose for UI; one folder per screen in `app/.../ui/`; shared pieces in `ui/components/`.
- Heavy work (decoding photos, reading, drawing on bitmaps) never runs on the main thread.
- Code comments are mostly in Portuguese. New comments can be in either language.

## Releases

Every merge into `main` publishes a release with the APK, marked as the latest one.
See [docs/RELEASE.md](docs/RELEASE.md).

## Code of conduct

Be kind and respectful. See [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md).
