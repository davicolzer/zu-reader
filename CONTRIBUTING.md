# Contributing to Zu Reader

Thanks for helping! Contributions come as **issues**: bug reports, wrong readings and ideas.
Issues can be in **English or Portuguese**. *Issues podem ser em inglês ou em português.*

> Pull requests are open only to the maintainer, who makes the changes. If you have a fix in mind,
> describe it in an issue (code snippets are welcome there).
>
> *Só o mantenedor abre pull requests e faz as mudanças. Se você tem uma correção em mente, descreva
> numa issue (trechos de código são bem-vindos).*

## Reporting a wrong reading

The most useful contribution is a **photo the app reads wrong**, together with the correct text.
Open a [bug report](../../issues/new?template=bug_report.yml) and attach it. With your permission,
the photo may be added to `testdata/real/` so the fix stays covered by the tests.

## Reporting a bug

Open a [bug report](../../issues/new?template=bug_report.yml) with what you did, what you expected,
what happened, a screenshot if possible, the app version (Settings → About) and your phone model.

## Suggesting a feature

Open a [feature request](../../issues/new?template=feature_request.yml). A photo, a sketch or a
scene from the game helps.

## Security

Do not report security problems in a public issue. See [SECURITY.md](SECURITY.md).

## Building it yourself

The code is MIT-licensed: you can build and change it for yourself. You need **JDK 25** and the
**Android SDK** with platform 37 (Android Studio does this), then:

```bash
./gradlew build                # compile, run every test and lint
./gradlew :app:installDebug    # installs "Zu Reader Teste" next to the official app
```

## For the maintainer

Each change goes in a pull request **against `staging`** (CI runs tests and lint), is tested on the
phone with the staging APK after the merge, and then goes to `main` in a pull request from `staging`,
which publishes the release. See [docs/RELEASE.md](docs/RELEASE.md).

- Reader changes come with a test in `core/vision` or `core/zu`; accuracy must not drop on the real
  photos (`RealPhotosTest`, `CommandReaderTest`).
- App texts go in **both** `values/strings.xml` (English) and `values-pt/strings.xml` (Portuguese).
- Screens must work in the **light and dark themes**.
- Heavy work (decoding photos, reading, drawing on bitmaps) never runs on the main thread.

## Code of conduct

Be kind and respectful. See [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md).
