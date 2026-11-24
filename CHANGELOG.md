# Changelog

## [0.5.0] – 2026-11-24

### Review

- **Flashcards from your own sentences:** the card shows the sentence with
  a gap where the word was; "Show hint" reveals the translation. The answer
  shows the word, its translation and definition. Words saved without a
  sentence ask for the word by its meaning.
- **Spaced repetition with FSRS-6**, the scheduler Anki uses, ported from
  the reference implementation and tested against its numbers. New words
  go through two short steps (1 and 10 minutes); after that each rating
  plans the next review for when you're about to forget the word. Every
  rating button shows its interval.
- **Daily limits:** 20 new words and 200 reviews a day by default, changed
  under Settings → Review. A day starts at 4 a.m.
- **Undo** the last rating; edit a word right from its card.
- Words with several sentences show them **in turn**.
- The home screen shows what is due; the word list can be **filtered by
  learning status** (new, learning, known – known means remembered for at
  least three weeks); each word's page shows its progress and can reset it.
- **E-ink mode:** no animation when the answer appears, larger rating
  buttons.

### Good to know

- Reviews are logged separately from the words (date, rating, the sentence
  shown), ready for export and sync later; see `docs/data-format.md`.
- Updating keeps all your words: the database moves to version 2 on first
  start.

## [0.4.0] – 2026-11-24

### Your word list

- **Search** in base forms, translations, definitions, the words as they
  appeared, sentences and book titles – also in Chinese and Japanese.
- **Filters** by language (when you collect in more than one) and by book,
  novel or manga, with the number of words each.
- **A page per word:** change base form, translation and definition, see
  every sentence with its book, app and date, delete single sentences.
- **Delete** a word and get it back with "Undo" right afterwards.
- **Merge duplicates:** Verba points out words that are probably the same
  ("running" and "run", also via the dictionary) and merges them in one
  tap – sentences and translations are kept. Any two words of a language
  can be merged by hand, and changing a base form to one you already have
  offers the same.

### Good to know

- Filtering by learning status comes with reviewing (0.5.0).

## [0.3.0] – 2026-11-23

### Offline dictionaries

- **Settings → Dictionaries:** pick the dictionaries for the languages you
  read and download them once; after that they work without internet. So
  far: English → German (240,000 words), Japanese → German, Japanese →
  English (217,000 each) and Chinese → English (122,000), 9–22 MB each.
- The sheet now fills in the base form, translation and definition from the
  dictionary and lists the other meanings; tap one to use it. Fields you
  typed into stay as you typed them, and a word you saved before keeps
  your translation.
- Verba finds what you mean, not only the exact word: "running" → run,
  "gave up" → give up, 食べた → 食べる; in Chinese and Japanese it takes the
  longest dictionary match from the character you tapped.
- Where there's no dictionary into your language yet (e.g. Chinese →
  German), Verba uses the English one and says so.
- Chinese readings are shown with tone marks (xué xí).
- Each dictionary lists its sources and licenses. A dictionary file you
  downloaded yourself can be imported.

### Good to know

- Verba now has the internet permission – used only when you download a
  dictionary, and only from this project's releases on GitHub.

## [0.2.0] – 2026-11-23

### Collect words from any app

- Select text in another app and choose **Verba** in the selection menu –
  or share it to Verba. Reading apps that send words to an external
  dictionary (ColorDict or Aard 2) reach Verba the same way.
- A small sheet opens over what you're reading: the word, its base form,
  a translation, a definition and the book, novel or manga. Verba
  remembers the title you used last in that app.
- Select a whole sentence and tap the word you want: the sentence is saved
  with it. Languages without spaces – Chinese, Japanese, Thai – are split
  into words too.
- **Any language:** Verba guesses the language from the script and lets you
  change it; translations go into your own language (Settings → Translate
  into).
- English words get their base form ("running" → "run"); other languages
  keep the word for now. You can always edit it.
- Saving a word you already have adds the new sentence to it instead of a
  duplicate.
- The home screen lists your words with their latest sentence.
- There's no dictionary yet, so you type the translation yourself. Offline
  dictionaries come with 0.3.0.

### Good to know

- The selection menu shows Verba only in apps that look for text tools,
  such as Chrome or Firefox. Everywhere else: Share → Save to Verba.

## [0.1.0] – 2026-11-23

### The foundation

- First version: Verba installs, starts and has its settings – collecting
  words comes with 0.2.0.
- Light and dark theme, following the system or set by hand.
- **E-ink mode:** pure black and white, no animations, larger touch targets.
  On Boox e-readers Verba suggests it on first start.
- English and German; the app language can differ from the system language.
- No permissions, no internet access, no Google Play services. Your data is
  excluded from Google's cloud backup.
- Releases are signed and can be installed and kept up to date with
  Obtainium.
