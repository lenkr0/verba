# Verba

*A calm vocabulary collector for reading books, novels and manga in any language.*

**English** · [Deutsch](README.de.md)

Select a word in any app, see its translation into your own language and
save it together with the sentence you found it in – then review it later
with that sentence. Whether you read English fantasy, Chinese web novels or
Japanese manga: Verba works the same way for every language pair. It works
completely offline, has no ads, no tracking and not even internet access,
and doesn't need Google Play services. An E-ink mode makes it comfortable on
e-readers such as the Boox Go 7.

> **Status: early development.** Verba collects words from any app; offline
> dictionaries follow in the next version. The features below arrive
> milestone by milestone, see the [roadmap](../../issues?q=label%3Amilestone).
> Screenshots follow soon.

## Features

Already there:

- **Collect words from any app:** select text and choose Verba in the
  selection menu, share it to Verba, or send it from a reader's dictionary
  lookup. Tap the word you want in the sentence – Chinese and Japanese are
  split into words too – and save it with the sentence, the book and the
  app it came from. English words get their base form ("running" → "run").
  Until the dictionaries arrive, you type the translation yourself.
- **Word list** with each word's latest sentence and source.
- **Light and dark theme**, following the system or set by hand.
- **E-ink mode:** pure black and white, no animations, larger touch targets.
  On Boox e-readers Verba suggests it on first start.
- **English and German**, with a per-app language setting.

Planned, in this order:

- [ ] **Offline dictionaries per language pair**, built from Wiktionary and
  other open dictionaries – starting with English → German, more pairs to
  follow.
- [ ] **Word list** with search, filters, editing and merging duplicates by
  base form.
- [ ] **Review** with flashcards and FSRS spaced repetition; the card shows
  your sentence with a gap.
- [ ] **Manga and images:** text recognition on screenshots (share or
  quick-settings tile); tap a recognized word to look it up.
- [ ] **Sentence translation** (optional, offline if feasible).
- [ ] **Export and backup:** CSV, JSON, AnkiDroid.
- [ ] **Home screen widget** "Word of the day".

## Using Verba

Verba shows up in three places:

- **Selection menu:** select text, then look for "Verba" in the menu (often
  under ⋮). This works in apps that look for text tools, such as Chrome or
  Firefox; some apps don't offer it.
- **Share:** select text → Share → "Save to Verba". Works in almost every
  app.
- **Reading apps with external dictionaries:** if your reader can send words
  to ColorDict or Aard 2, choose one of them as its dictionary; the word
  then opens in Verba.

Pick your own language once under Settings → Translate into.

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
