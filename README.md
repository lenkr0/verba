# Verba

*A calm vocabulary collector for reading English books and manga on Android.*

**English** · [Deutsch](README.de.md)

Select a word in any app, see its German translation and save it together
with the sentence you found it in – then review it later with that sentence.
Verba works completely offline, has no ads, no tracking and not even
internet access, and doesn't need Google Play services. An E-ink mode makes
it comfortable on e-readers such as the Boox Go 7.

> **Status: early development.** Version 0.1.0 is the foundation – themes,
> E-ink mode, languages and settings. The features below arrive milestone by
> milestone, see the [roadmap](../../issues?q=label%3Amilestone). Screenshots
> follow with the first features.

## Features

Already there:

- **Light and dark theme**, following the system or set by hand.
- **E-ink mode:** pure black and white, no animations, larger touch targets.
  On Boox e-readers Verba suggests it on first start.
- **English and German**, with a per-app language setting.

Planned, in this order:

- [ ] **Select → translate → save** from the text selection menu of any app:
  word, base form ("running" → "run"), German translations, a short English
  definition and the sentence as context. Verba remembers the app and the
  book or manga you're reading.
- [ ] **Offline English → German dictionary** built from Wiktionary and Ding.
- [ ] **Word list** with search, filters, editing and merging duplicates by
  base form.
- [ ] **Review** with flashcards and FSRS spaced repetition; the card shows
  your sentence with a gap.
- [ ] **Manga:** text recognition on screenshots (share or quick-settings
  tile); tap a recognized word to look it up.
- [ ] **Sentence translation** (optional, offline if feasible).
- [ ] **Export and backup:** CSV, JSON, AnkiDroid.
- [ ] **Home screen widget** "Word of the day".

## Privacy

- Verba requests **no permissions** – not even internet access. CI fails if
  a library tries to add one (`tools/check-permissions.sh`).
- No telemetry, no ads, no Google Play services.
- Your words are excluded from Google's cloud backup; moving to a new phone
  by cable or Wi-Fi (device-to-device transfer) still works.

## Install

Verba is published on [GitHub Releases](../../releases). The easiest way to
install it and get updates automatically is
[Obtainium](https://github.com/ImranR98/Obtainium):

1. Install Obtainium (from its GitHub releases or from F-Droid).
2. In Obtainium tap **Add app**, paste `https://github.com/lenkr0/verba` and
   tap **Add**.
3. Tap **Install**. Android asks once to allow installs from Obtainium.

Obtainium then looks for new versions in the background and notifies you.
You can also download `verba-<version>.apk` from the latest release and open
it. Every release lists the SHA-256 of the APK and of the signing
certificate.

Requires Android 13 or newer. Made for Android 13 (Boox Go 7) and Android 16
(Poco F6 Pro, HyperOS).

## Build

Requires JDK 21 and the Android SDK (Android Studio or the command-line
tools).

```
./gradlew assembleDebug               # debug APK, installs next to the release as "Verba Debug"
./gradlew testDebugUnitTest           # unit tests
./gradlew connectedDebugAndroidTest   # instrumented tests on a device or emulator
./gradlew lintDebug                   # Android lint
```

CI builds, runs the unit tests, lint and the permission check on every push;
pull requests and `main` also run the instrumented tests on Android 13 and
Android 16 emulators without Google Play. A tag `v1.2.3` – matching
`verba.version` in `gradle.properties` – builds the signed APK and publishes
the release. How the signing key is created and stored is described in
[docs/release-signing.md](docs/release-signing.md).

## Translations

The UI is available in English and German. Strings live in
`app/src/main/res/values-<language>/strings.xml`; a new language also needs
an entry in `AppLanguage.supported`. Corrections and new languages are
welcome.

## License

GPL-3.0-or-later, see [LICENSE](LICENSE). Third-party notices are in
[NOTICE.md](NOTICE.md). Changes are listed in [CHANGELOG.md](CHANGELOG.md).
