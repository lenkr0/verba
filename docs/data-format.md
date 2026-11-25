# Data format

Verba keeps your words in a local SQLite database (Room, file `verba.db`).
This document describes the data model, so that backups, exports and a later
sync – for example a JSON file on your own WebDAV share that other apps such
as [Hora](https://github.com/lenkr0/hora) can read – can rely on it.

The exact schema of every database version is generated into
[`app/schemas/`](../app/schemas) and committed with the code.

## Principles

- **IDs** are random UUID v4 strings, created on the device that creates the
  record. They never change and are never reused.
- **Times** are milliseconds since the Unix epoch, UTC.
- Every record has `created_at` and `updated_at`; every change sets
  `updated_at`.
- **Deleting** sets `deleted_at` (a tombstone) instead of removing the row, so
  that a deletion can travel to other devices.
- **No foreign keys** in the database: relations are plain IDs, so records can
  arrive in any order during a sync.
- **Languages** are BCP 47 tags: `en`, `de`, `zh`, `ja` …
- **Text offsets** are UTF-16 code units, i.e. string indices in Java,
  Kotlin and JavaScript. (Rust strings are UTF-8: count with `encode_utf16()`.)
- For a future sync the rule is **last write wins per record**, decided by
  `updated_at`.

## Tables (schema version 2)

Version 1 (Verba 0.2–0.4) had `entries`, `contexts` and `sources`; version 2
(0.5) adds `review_cards` and `review_logs`.

### `entries` – a word to learn

One entry per base form and language; it collects every sentence the word
was found in.

| Column | Type | Meaning |
|---|---|---|
| `id` | text | UUID |
| `language` | text | Language of the word, e.g. `zh` |
| `target_language` | text | Language of translation and definition, e.g. `de` |
| `lemma` | text | Base form as shown, e.g. `run`, `学习` |
| `lemma_key` | text | `lemma` normalized (Unicode NFKC, lower case); identifies duplicates |
| `translation` | text | Free text, may list several translations |
| `definition` | text | Free text, may be empty |
| `created_at`, `updated_at` | integer | Epoch ms, UTC |
| `deleted_at` | integer, nullable | Tombstone |

Among entries that aren't deleted, `(language, lemma_key)` is unique: saving
a base form that already exists adds a sentence to that entry instead of
creating a second one.

### `contexts` – one encounter of a word

| Column | Type | Meaning |
|---|---|---|
| `id` | text | UUID |
| `entry_id` | text | The entry |
| `sentence` | text | The sentence, or `""` if only the word was selected |
| `word_form` | text | The word as it appeared, e.g. `running` |
| `word_start`, `word_end` | integer | UTF-16 offsets of the word in `sentence`; `-1` if unknown |
| `source_id` | text, nullable | The book, novel or manga |
| `source_app` | text, nullable | Package of the app the text came from, e.g. `com.android.chrome` |
| `source_app_label` | text, nullable | That app's name at the time |
| `created_at`, `updated_at` | integer | Epoch ms, UTC |
| `deleted_at` | integer, nullable | Tombstone |

### `sources` – what you read

| Column | Type | Meaning |
|---|---|---|
| `id` | text | UUID |
| `title` | text | As typed, e.g. `The Hobbit` |
| `title_key` | text | Lower-case title; titles that differ only in case are the same source |
| `created_at`, `updated_at` | integer | Epoch ms, UTC |
| `deleted_at` | integer, nullable | Tombstone |

### `review_cards` – learning progress of a word

One row per entry that has been reviewed, keyed by the entry's ID. An entry
without a live row is **new**. Scheduling follows
[FSRS-6](https://github.com/open-spaced-repetition/py-fsrs) with its
default parameters, a desired retention of 90 %, learning steps of 1 and
10 minutes and a relearning step of 10 minutes.

| Column | Type | Meaning |
|---|---|---|
| `entry_id` | text | The entry (primary key) |
| `state` | integer | `1` learning, `2` review, `3` relearning |
| `step` | integer, nullable | Current (re)learning step, from `0`; null in review |
| `stability` | real | FSRS stability: days until the chance of remembering falls to 90 % |
| `difficulty` | real | FSRS difficulty, `1` (easy) to `10` (hard) |
| `due` | integer | Next review, epoch ms, UTC |
| `last_review` | integer | Epoch ms, UTC |
| `reps` | integer | Number of reviews |
| `lapses` | integer | How often the word was forgotten in state review |
| `created_at`, `updated_at` | integer | Epoch ms, UTC |
| `deleted_at` | integer, nullable | Tombstone: progress was reset, the word is new again |

**Learning status** (the word list filter): *new* without a card, *known*
in state review with a stability of at least 21 days, *learning* otherwise.

### `review_logs` – every review

Append-only: a row is never changed, except that undoing a rating marks it
deleted. The logs are enough to recompute every card, or to tune the FSRS
parameters later.

| Column | Type | Meaning |
|---|---|---|
| `id` | text | UUID |
| `entry_id` | text | The entry |
| `context_id` | text, nullable | The sentence the card showed; null if none |
| `rating` | integer | `1` again, `2` hard, `3` good, `4` easy |
| `state` | integer | Card state *before* the review: `0` new, `1` learning, `2` review, `3` relearning |
| `reviewed_at` | integer | Epoch ms, UTC |
| `duration_ms` | integer, nullable | Time from showing the card to rating it |
| `created_at`, `updated_at` | integer | Epoch ms, UTC |
| `deleted_at` | integer, nullable | Tombstone (rating undone) |

### Daily limits

A learning day starts at 4:00 local time. The limit for new words counts
logs of that day with `state = 0`, the limit for reviews those with
`state = 2`; ratings of learning steps don't count.

## Deleting, undoing and merging

- **Deleting** a word marks the entry and its sentences with the same
  `deleted_at`. **Undo** clears exactly the marks with that time, so
  sentences deleted earlier on their own stay deleted. If the same base form
  was saved again in the meantime, the restored entry is merged into it.
- **Merging** entry A into entry B moves A's sentences to B, adds A's
  translations that B doesn't have (`;`-separated, each once), keeps B's
  definition (or takes A's if B has none) and marks A deleted. A's review
  logs move to B; B keeps its card, or takes over A's if it has none.
- A deleted entry keeps its card and logs, so undoing the deletion keeps
  the progress; reviews skip deleted entries.

## Syncing duplicates

Two devices can create an entry for the same base form independently, with
different IDs. A sync has to merge them: keep the entry with the older
`created_at`, point the other one's contexts and review logs to it, keep a
review card as in merging, and mark the other one deleted. The same goes
for sources with the same `title_key`.

Restoring a backup applies exactly these rules (see below).

## JSON backup

Settings → Backup and export → Back up writes one UTF-8 JSON file with every
row of every table, deleted ones included (their tombstones matter for a
merge). Rows use the column names and types above:

```json
{
  "format": "verba-backup",
  "version": 1,
  "exported_at": 1795000000000,
  "app_version": "0.7.0",
  "entries": [{ "id": "…", "language": "en", "target_language": "de", "lemma": "run", "…": "…" }],
  "contexts": [],
  "sources": [],
  "review_cards": [],
  "review_logs": []
}
```

- `format` is always `verba-backup`; `version` changes only when the meaning
  of existing fields changes. A reader should ignore fields it doesn't know.
- **Restoring merges, it never replaces:** a record that doesn't exist yet is
  added; one that exists is replaced only if the backup's `updated_at` is
  newer (last write wins per record). Then duplicates are merged as in
  *Syncing duplicates*. Restoring the same backup twice changes nothing, and
  an old backup never undoes newer work.
- The same file is the planned format for a sync, e.g. a JSON file on your
  own WebDAV share that other apps such as Hora read.

## CSV export

One row per word that isn't deleted, oldest first, UTF-8 with a byte order
mark, comma-separated, quoted as in RFC 4180, CRLF line ends. Columns:
`word`, `translation`, `definition`, `language`, `target_language`,
`sentence` (the most recent one), `source` (its book), `status` (`new`,
`learning`, `known`), `added` (local date, `YYYY-MM-DD`).

## Anki export

An `.apkg` package in Anki's classic format (`collection.anki2`, schema 11,
plus an empty `media` file), which AnkiDroid and Anki import:

- Note type **Verba Cloze** (cloze, fixed id `1732500000001`) with the fields
  `Text` (the most recent sentence with the word as `{{c1::…}}`, or just the
  word), `Word`, `Translation`, `Definition`, `Source`. The front shows the
  sentence with the gap and the translation as a hint to tap.
- Deck **Verba** (fixed id `1732500000002`), cards new; Anki schedules them.
- Each note's `guid` is `verba:` + the entry's ID, so importing a newer
  export updates the notes instead of adding duplicates.
- Tags: `verba`, the language, and the book with spaces as `_`.

## Word of the day

The widget's word of the day is chosen so that any app with the same words
(e.g. Hora, reading a synced backup) shows the same one:

1. Candidates: all entries with `deleted_at` null.
2. For each, the SHA-256 of the UTF-8 text `YYYY-MM-DD:<id>`, with the local
   date of the day (ISO 8601) and the entry's `id`.
3. The entry with the smallest hash (compared byte by byte, unsigned – the
   same as comparing the lowercase hex strings) is the word of the day.

The day changes at local midnight. Adding a word changes today's choice only
if the new word happens to win. In Python:

```python
import hashlib
def word_of_the_day(ids, date):  # date: "2026-11-25"
    return min(ids, key=lambda i: hashlib.sha256(f"{date}:{i}".encode()).hexdigest(), default=None)
```

For the IDs `3f2b8c1e-0d4a-4c6e-9b7a-1e2f3a4b5c6d`,
`a1b2c3d4-e5f6-4789-8abc-def012345678`, `7c9e6679-7425-40de-944b-e07fc1f90ae7`
and `e4eaaaf2-d142-11e1-b3e4-080027620cdd`, 2026-11-25 picks the first and
2026-11-26 the last (tested in `WordOfTheDayTest`).
