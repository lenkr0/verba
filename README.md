# Verba

*A calm vocabulary collector for reading books, novels and manga in any language.*

**English** · [Deutsch](README.de.md)

Select a word in any app, see its translation into your own language and
save it together with the sentence you found it in – then review it later
with that sentence. Whether you read English fantasy, Chinese web novels or
Japanese manga: Verba works the same way for every language pair. Its
dictionaries work offline, it has no ads and no tracking, and it doesn't
need Google Play services. An E-ink mode makes it comfortable on
e-readers such as the Boox Go 7.

> **Status: early development.** Verba collects words from any app, looks
> them up in offline dictionaries you download in the app and lets you review
> them with spaced repetition. The features below arrive milestone by
> milestone, see the [roadmap](../../issues?q=label%3Amilestone).
> Screenshots follow soon.

## Features

Already there:

- **Collect words from any app:** select text and choose Verba in the
  selection menu, share it to Verba, or send it from a reader's dictionary
  lookup. Tap the word you want in the sentence – Chinese and Japanese are
  split into words too – and save it with the sentence, the book and the
  app it came from.
- **Offline dictionaries per language pair**, downloaded in the app and
  then used without internet: English → German, Japanese → German,
  Japanese → English and Chinese → English so far, built from Wiktionary,
  Ding, JMdict and CC-CEDICT. Verba finds base forms ("running" → "run",
  "gave up" → "give up"), Japanese conjugations (食べた → 食べる) and the
  longest match in languages without spaces; where there's no dictionary
  into your language yet, it shows the English one and says so.
- **Word list** with search (words, translations, sentences, books),
  filters by language, book and learning status (new, learning, known),
  and a page per word: edit it, see all its sentences and its progress,
  delete a word or a single sentence (with undo), and merge duplicates –
  Verba points out likely ones ("running" and "run").
- **Review with spaced repetition** (FSRS-6, the scheduler Anki uses): the
  card shows one of your sentences with a gap where the word was – tap
  "Show hint" for the translation. Rate Again, Hard, Good or Easy; each
  button says when the word comes back. Words with several sentences show
  them in turn. Daily limits for new words and reviews, and the last
  rating can be undone.
- **Light and dark theme**, following the system or set by hand.
- **E-ink mode:** pure black and white, no animations, larger touch targets.
  On Boox e-readers Verba suggests it on first start.
- **English and German**, with a per-app language setting.

Planned, in this order:

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

Pick your own language once under Settings → Translate into, and download
the dictionaries you need under Settings → Dictionaries.

To review, tap **Start** on the home screen when words are due. New words
come in the order you saved them, 20 a day unless you change it under
Settings → Review. A learning day starts at 4 a.m., so reading late at night
still counts for the evening.

## Privacy

- Verba requests **one permission: internet access**, used only when you
  download a dictionary – from this project's releases on GitHub, nothing
  else. CI fails if a library tries to add any other permission
  (`tools/check-permissions.sh`).
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

The dictionaries are built by `tools/dictionary` (Python 3) in the
[Dictionaries workflow](.github/workflows/dictionaries.yml), monthly and
whenever the builder changes; sources, catalog and pack format are described
in [docs/dictionary-format.md](docs/dictionary-format.md).

## Translations

The UI is available in English and German. Strings live in
`app/src/main/res/values-<language>/strings.xml`; a new language also needs
an entry in `AppLanguage.supported`. Corrections and new languages are
welcome.

## License

GPL-3.0-or-later, see [LICENSE](LICENSE). Third-party notices are in
[NOTICE.md](NOTICE.md). The dictionaries are separate downloads under their
sources' licenses (CC BY-SA 4.0, GPL-2.0-or-later); the app shows them for
every dictionary. Changes are listed in [CHANGELOG.md](CHANGELOG.md).
