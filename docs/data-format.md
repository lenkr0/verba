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

## Tables (schema version 1)

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

## Syncing duplicates

Two devices can create an entry for the same base form independently, with
different IDs. A sync has to merge them: keep the entry with the older
`created_at`, point the other one's contexts to it and mark the other one
deleted. The same goes for sources with the same `title_key`.

## Planned

- **Reviews (M4):** a review state per entry (FSRS: due date, stability,
  difficulty) and a log of every review, again with UUIDs and tombstones.
- **JSON backup (M7):** the tables above as arrays in one file, with a format
  marker and version:

  ```json
  {
    "format": "verba-backup",
    "version": 1,
    "exportedAt": 1795000000000,
    "entries": [],
    "contexts": [],
    "sources": []
  }
  ```

- **Word of the day (M8):** chosen deterministically from the date and the
  entry IDs and documented here, so other apps can show the same word.
