"""Writes a Verba dictionary pack: one SQLite file per language pair.

The format is described in docs/dictionary-format.md. The app opens packs
read-only; everything it needs for a lookup is indexed by `lemma_key` /
`form_key`, which are normalized exactly like the app's LemmaKey.
"""

from __future__ import annotations

import json
import os
import sqlite3
from dataclasses import dataclass, field
from datetime import datetime, timezone

from .keys import key

FORMAT = "verba-dictionary"
FORMAT_VERSION = 1

SCHEMA = """
CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT NOT NULL);
CREATE TABLE headwords (
    id INTEGER PRIMARY KEY,
    lemma TEXT NOT NULL,
    lemma_key TEXT NOT NULL,
    reading TEXT,
    pos TEXT,
    rank INTEGER NOT NULL DEFAULT 0
);
CREATE TABLE senses (
    headword_id INTEGER NOT NULL,
    position INTEGER NOT NULL,
    translations TEXT NOT NULL,
    definition TEXT NOT NULL,
    definition_language TEXT NOT NULL,
    source TEXT NOT NULL
);
CREATE TABLE forms (form_key TEXT NOT NULL, headword_id INTEGER NOT NULL);
"""

INDEXES = """
CREATE INDEX idx_headwords_key ON headwords(lemma_key);
CREATE INDEX idx_senses_headword ON senses(headword_id, position);
CREATE INDEX idx_forms_key ON forms(form_key);
"""


@dataclass
class Source:
    """A dictionary the pack is built from; shown on the attribution screen."""

    id: str
    name: str
    url: str
    license: str
    license_url: str
    attribution: str = ""


@dataclass
class Sense:
    translations: list[str]
    definition: str
    definition_language: str
    source: str


@dataclass
class Headword:
    lemma: str
    pos: str | None = None
    reading: str | None = None
    rank: int = 0
    senses: list[Sense] = field(default_factory=list)
    forms: set[str] = field(default_factory=set)


def clean_list(items) -> list[str]:
    """Trimmed, de-duplicated (case-sensitive, keeps order), empty ones dropped."""
    seen, result = set(), []
    for item in items:
        item = " ".join(str(item).split())
        if item and item not in seen:
            seen.add(item)
            result.append(item)
    return result


class PackWriter:
    MAX_SENSES = 6
    MAX_DEFINITION = 160

    def __init__(self, path: str, source_language: str, target_language: str, title: str, version: str,
                 sources: list[Source]):
        if os.path.exists(path):
            os.remove(path)
        self.path = path
        self.db = sqlite3.connect(path)
        self.db.executescript(SCHEMA)
        self.meta = {
            "format": FORMAT,
            "format_version": str(FORMAT_VERSION),
            "source_language": source_language,
            "target_language": target_language,
            "title": title,
            "version": version,
            "built_at": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
            "sources": json.dumps([s.__dict__ for s in sources], ensure_ascii=False),
        }
        self.count = 0

    def add(self, headword: Headword) -> None:
        senses = [s for s in headword.senses if s.translations or s.definition][: self.MAX_SENSES]
        if not senses:
            return
        cursor = self.db.execute(
            "INSERT INTO headwords (lemma, lemma_key, reading, pos, rank) VALUES (?, ?, ?, ?, ?)",
            (headword.lemma, key(headword.lemma), headword.reading, headword.pos, headword.rank),
        )
        headword_id = cursor.lastrowid
        for position, sense in enumerate(senses):
            definition = " ".join(sense.definition.split())
            if len(definition) > self.MAX_DEFINITION:
                definition = definition[: self.MAX_DEFINITION - 1].rstrip() + "…"
            self.db.execute(
                "INSERT INTO senses VALUES (?, ?, ?, ?, ?, ?)",
                (headword_id, position, "; ".join(clean_list(sense.translations)), definition,
                 sense.definition_language, sense.source),
            )
        own_key = key(headword.lemma)
        for form_key in {key(f) for f in headword.forms} - {own_key, ""}:
            self.db.execute("INSERT INTO forms VALUES (?, ?)", (form_key, headword_id))
        self.count += 1

    def close(self) -> dict:
        """Indexes, compacts and returns the pack's metadata."""
        self.meta["headwords"] = str(self.count)
        self.db.executemany("INSERT INTO meta VALUES (?, ?)", self.meta.items())
        self.db.executescript(INDEXES)
        self.db.commit()
        self.db.execute("VACUUM")
        self.db.close()
        return dict(self.meta)
