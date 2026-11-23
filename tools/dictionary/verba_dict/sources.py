"""Readers for the open dictionaries Verba's packs are built from.

All readers stream their input, so multi-gigabyte dumps don't need to fit
into memory.
"""

from __future__ import annotations

import gzip
import json
import re
import xml.etree.ElementTree as ET
from dataclasses import dataclass, field
from typing import Iterator


def open_text(path: str):
    return gzip.open(path, "rt", encoding="utf-8") if path.endswith(".gz") else open(path, encoding="utf-8")


# --- Wiktionary (wiktextract JSONL from kaikki.org) ---------------------------

def wiktextract(path: str, lang_code: str | None = None) -> Iterator[dict]:
    """Entries of a wiktextract dump, optionally only those of one language."""
    marker = f'"lang_code": "{lang_code}"' if lang_code else None
    with open_text(path) as f:
        for line in f:
            if marker and marker not in line:
                continue
            try:
                entry = json.loads(line)
            except json.JSONDecodeError:
                continue
            if lang_code is None or entry.get("lang_code") == lang_code:
                yield entry


SKIP_FORM_TAGS = {"table-tags", "inflection-template", "class", "romanization"}


def entry_forms(entry: dict) -> set[str]:
    """Inflected forms listed in an entry ("runs", "running", "ran")."""
    forms = set()
    for form in entry.get("forms", []):
        text = form.get("form", "")
        if text and not (set(form.get("tags", [])) & SKIP_FORM_TAGS) and text != entry.get("word"):
            forms.add(text)
    return forms


def form_of_targets(entry: dict) -> list[str]:
    """For entries like "ran" (past tense of run): the base forms."""
    return [f["word"] for s in entry.get("senses", []) for f in s.get("form_of", []) if f.get("word")]


def real_senses(entry: dict) -> list[dict]:
    """Senses with a gloss that are not just "past tense of …"."""
    return [s for s in entry.get("senses", []) if s.get("glosses") and not s.get("form_of")]


# --- Ding (TU Chemnitz), German ↔ English -----------------------------------

_BRACKETS = re.compile(r"\{[^}]*\}|\[[^\]]*\]|<[^>]*>")


def _clean_ding(text: str) -> str:
    return " ".join(_BRACKETS.sub("", text).split()).strip(" ;,")


def _english_key(text: str) -> str:
    """'to run {ran; run}' → 'run'; drops brackets and the infinitive 'to'."""
    text = _clean_ding(re.sub(r"\([^)]*\)", "", text))
    return text[3:] if text.startswith("to ") else text


@dataclass
class DingEntry:
    english: str
    german: list[str]
    english_forms: set[str] = field(default_factory=set)


def ding(path: str) -> Iterator[DingEntry]:
    """English headwords with German translations.

    A Ding line is "German | inflections :: English | inflections"; both
    sides are aligned by "|", synonyms are separated by ";". The first
    column is the entry, the others are its inflected forms.
    """
    with open_text(path) as f:
        for line in f:
            if line.startswith("#") or " :: " not in line:
                continue
            german_side, english_side = line.rstrip("\n").split(" :: ", 1)
            german_cols = german_side.split(" | ")
            english_cols = english_side.split(" | ")
            german = [_clean_ding(g) for g in german_cols[0].split("; ")]
            german = [g for g in german if g]
            forms = {_english_key(e) for col in english_cols[1:] for e in col.split("; ")}
            for english in english_cols[0].split("; "):
                key_text = _english_key(english)
                if key_text and german and len(key_text) <= 60:
                    yield DingEntry(key_text, german, {f for f in forms if f and f != key_text})


# --- JMdict (EDRDG), Japanese ------------------------------------------------

@dataclass
class JmdictEntry:
    kanji: list[str]
    readings: list[str]
    pos: str | None
    senses_by_lang: dict[str, list[list[str]]]
    common: bool


_XML_LANG = "{http://www.w3.org/XML/1998/namespace}lang"


def jmdict(path: str) -> Iterator[JmdictEntry]:
    """Entries of JMdict (the full, multilingual file). Glosses per sense and language
    ("eng", "ger", …); part of speech as the entity's text."""
    with open_text(path) as f:
        # JMdict defines its part-of-speech codes as DTD entities; expat can
        # resolve those declared in the internal subset.
        parser = ET.XMLParser()
        for _, element in ET.iterparse(f, events=("end",), parser=parser):
            if element.tag != "entry":
                continue
            kanji = [k.findtext("keb") for k in element.findall("k_ele")]
            readings = [r.findtext("reb") for r in element.findall("r_ele")]
            common = any(p.text in ("news1", "ichi1", "spec1", "gai1")
                         for p in element.iter() if p.tag in ("ke_pri", "re_pri"))
            senses_by_lang: dict[str, list[list[str]]] = {}
            pos = None
            for sense in element.findall("sense"):
                pos = pos or sense.findtext("pos")
                by_lang: dict[str, list[str]] = {}
                for gloss in sense.findall("gloss"):
                    lang = gloss.get(_XML_LANG, "eng")
                    if gloss.text:
                        by_lang.setdefault(lang, []).append(gloss.text)
                for lang, glosses in by_lang.items():
                    senses_by_lang.setdefault(lang, []).append(glosses)
            yield JmdictEntry([k for k in kanji if k], [r for r in readings if r], pos, senses_by_lang, common)
            element.clear()


# --- CC-CEDICT, Chinese → English --------------------------------------------

_CEDICT_LINE = re.compile(r"^(\S+) (\S+) \[([^\]]*)\] /(.*)/\s*$")


@dataclass
class CedictEntry:
    traditional: str
    simplified: str
    pinyin: str
    glosses: list[str]


def cedict(path: str) -> Iterator[CedictEntry]:
    with open_text(path) as f:
        for line in f:
            if line.startswith("#"):
                continue
            match = _CEDICT_LINE.match(line.strip())
            if match:
                trad, simp, pinyin, glosses = match.groups()
                yield CedictEntry(trad, simp, pinyin, [g for g in glosses.split("/") if g])
