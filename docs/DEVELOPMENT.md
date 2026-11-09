# Developing Zu Reader

Technical notes for whoever builds or maintains the app. For using the app, see the [README](../README.md).

## How it works

The reader is plain Kotlin with no ML framework, so it runs fully on the device:

1. **Ink mask**: global and local thresholds at several scales, for dark-on-light and light-on-dark text. If the result is weak, it also tries color variants (contrast, R/G/B channels, saturation).
2. **Glyph boxes**: every glyph fills all rows and columns of its 5×5 grid, so the mask is split by its projections. Skew up to ±12° and stretching up to 3× are corrected.
3. **Classification**: each glyph becomes a 5×5 bit matrix and is matched against the 24 Zu glyphs (6 shapes × 4 rotations) by Hamming distance.
4. **Text**: a Viterbi search over English letter pairs picks the most likely letters. This resolves the pairs that share a glyph (K/Q, U/V) and noisy blocks.

Columns are read top to bottom, right to left, as in the game. Horizontal reading is an option in the settings.

## Build from source

You need **JDK 25** and the **Android SDK** with platform 37 (Android Studio installs it).

```bash
git clone https://github.com/davicolzer/zu-reader.git
cd zu-reader
./gradlew build                # compile, run all tests and lint
./gradlew :app:installDebug    # install on a connected device or emulator
./gradlew :core:vision:test    # only the image-reading tests (no emulator needed)
```

| Tool | Version |
|---|---|
| JDK | 25 (LTS); bytecode targets Java 21 |
| Gradle | 9.8.2 |
| Kotlin | 2.4.21 |
| Android Gradle Plugin | 9.5.1 |
| Compose BOM | 2026.09.00 |
| compileSdk / targetSdk | 37 |
| minSdk | 26 (Android 8.0) |

## Project structure

```
app/            Android app (Jetpack Compose, CameraX, Room, DataStore)
  data/         collection (Room), settings (DataStore), language
  media/        loading and straightening photos, shrinking videos
  ui/           one folder per screen; ZuReaderApp (navigation) and ReaderViewModel (state)
core/zu/        pure Kotlin: Zu alphabet, classifier, reading order, English model
core/vision/    pure Kotlin: image → glyphs, numbers, commands, QR codes, writing in Zu
testdata/real/  real photos used by the regression tests
tools/          helper scripts (English letter-pair table, glyph network training)
docs/           roadmap (PLANO.md), release guide, this file, design files
```

The `core` modules do not depend on Android, so the readers are tested quickly on the JVM with real and synthetic images.

## Workflow and releases

Changes go in a pull request to `staging` (tests and lint), are tested on a phone with the staging
APK after the merge, then go to `main`, which publishes the release. Details (in Portuguese):
[RELEASE.md](RELEASE.md). Roadmap (in Portuguese): [PLANO.md](PLANO.md).

