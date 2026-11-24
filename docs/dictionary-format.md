# Dictionary packs

Verba's offline dictionaries come as **packs, one per language pair**
(e.g. English → German, Japanese → German). The app contains none of them;
you choose and download the ones you need under Settings → Dictionaries,
after which they work offline.

Packs are built by [`tools/dictionary`](../tools/dictionary) in the
[Dictionaries workflow](../.github/workflows/dictionaries.yml) and published
as assets of the release [`dictionaries`](../../../releases/tag/dictionaries),
together with `catalog.json`. The workflow runs monthly, so packs follow the
updates of their sources.

## Sources

| Pack | Sources | License of the pack |
|---|---|---|
| English → German | English Wiktionary (translation tables), German Wiktionary, Ding | GPL-2.0-or-later and CC BY-SA 4.0 parts |
| Japanese → German | JMdict (German glosses; English where an entry has none) | CC BY-SA 4.0 |
| Japanese → English | JMdict | CC BY-SA 4.0 |
| Chinese → English | CC-CEDICT | CC BY-SA 4.0 |

Wiktionary data is extracted by Tatu Ylonen's
[wiktextract](https://github.com/tatuylonen/wiktextract) and downloaded from
[kaikki.org](https://kaikki.org). Every pack lists its sources, licenses and
attributions in its metadata; the app shows them.

## Catalog

`catalog.json`:

```json
{
  "format": "verba-dictionary-catalog",
  "version": 1,
  "updatedAt": "2026-11-23T21:00:00Z",
  "packs": [
    {
      "id": "en-de",
      "sourceLanguage": "en",
      "targetLanguage": "de",
      "title": "English → German",
      "version": "2026.11.23",
      "formatVersion": 1,
      "headwords": 240064,
      "url": "https://github.com/lenkr0/verba/releases/download/dictionaries/en-de.sqlite.gz",
      "size": 20237000,
      "installedSize": 47083520,
      "sha256": "e232302b…",
      "sources": [{ "id": "enwiktionary", "name": "…", "url": "…", "license": "CC BY-SA 4.0", "license_url": "…", "attribution": "…" }]
    }
  ]
}
```

`size` and `sha256` refer to the gzip file; `installedSize` to the
uncompressed SQLite file on the device.

## Pack format (version 1)

A pack is an SQLite database. The app opens it read-only.

```sql
CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT NOT NULL);
CREATE TABLE headwords (
    id INTEGER PRIMARY KEY,
    lemma TEXT NOT NULL,       -- base form as shown: "run", "走る", "学习"
    lemma_key TEXT NOT NULL,   -- NFKC + lower case, like the app's LemmaKey
    reading TEXT,              -- kana or pinyin, if any
    pos TEXT,                  -- part of speech as given by the source
    rank INTEGER NOT NULL      -- lower first; >= 50000 means "after all others"
);
CREATE TABLE senses (
    headword_id INTEGER NOT NULL,
    position INTEGER NOT NULL,
    translations TEXT NOT NULL,          -- "; "-separated, in the target language
    definition TEXT NOT NULL,            -- short, may be empty
    definition_language TEXT NOT NULL,   -- target language, or "en" as a fallback
    source TEXT NOT NULL                 -- id of a source in meta "sources"
);
CREATE TABLE forms (form_key TEXT NOT NULL, headword_id INTEGER NOT NULL);
```

`meta` keys: `format` (`verba-dictionary`), `format_version` (`1`),
`source_language`, `target_language`, `title`, `version`, `built_at`,
`headwords` and `sources` (JSON list with `id`, `name`, `url`, `license`,
`license_url`, `attribution`).

`forms` maps inflected and alternative forms to their headword: "ran" → run,
"學習" (traditional) → 学习, "はしる" (reading) → 走る.

### Lookup

```sql
SELECT h.*, s.* FROM headwords h JOIN senses s ON s.headword_id = h.id
WHERE h.lemma_key = :key OR h.id IN (SELECT headword_id FROM forms WHERE form_key = :key)
ORDER BY h.rank >= 50000, h.lemma_key != :key, h.rank, h.id, s.position
```

Demoted entries go last, then exact matches before matches through a form,
then by rank (common and well-translated words first).

The app adds what a static table can't: English base form rules, Japanese
deinflection (食べた → 食べる) and, for languages without spaces, the
longest dictionary match from the tapped character on.

A new format version is only used for changes old apps can't read; the app
skips packs with a newer `format_version` than it knows.
