"""One builder per language pair: reads the sources, merges them into
headwords and writes a pack."""

from __future__ import annotations

import re
from collections import OrderedDict, defaultdict

from . import sources as src
from .keys import key
from .pack import Headword, PackWriter, Sense, Source

ENWIKTIONARY = Source(
    id="enwiktionary",
    name="English Wiktionary (via kaikki.org / wiktextract)",
    url="https://en.wiktionary.org",
    license="CC BY-SA 4.0",
    license_url="https://creativecommons.org/licenses/by-sa/4.0/",
    attribution="Wiktionary contributors; extracted by Tatu Ylonen's wiktextract (kaikki.org)",
)
DEWIKTIONARY = Source(
    id="dewiktionary",
    name="German Wiktionary (via kaikki.org / wiktextract)",
    url="https://de.wiktionary.org",
    license="CC BY-SA 4.0",
    license_url="https://creativecommons.org/licenses/by-sa/4.0/",
    attribution="Wiktionary contributors; extracted by Tatu Ylonen's wiktextract (kaikki.org)",
)
DING = Source(
    id="ding",
    name="Ding German–English dictionary",
    url="https://www-user.tu-chemnitz.de/~fri/ding/",
    license="GPL-2.0-or-later",
    license_url="https://www.gnu.org/licenses/old-licenses/gpl-2.0.html",
    attribution="Frank Richter, TU Chemnitz",
)
JMDICT = Source(
    id="jmdict",
    name="JMdict",
    url="https://www.edrdg.org/jmdict/j_jmdict.html",
    license="CC BY-SA 4.0",
    license_url="https://www.edrdg.org/edrdg/licence.html",
    attribution="Electronic Dictionary Research and Development Group (EDRDG)",
)
CEDICT = Source(
    id="cedict",
    name="CC-CEDICT",
    url="https://cc-cedict.org",
    license="CC BY-SA 4.0",
    license_url="https://creativecommons.org/licenses/by-sa/4.0/",
    attribution="MDBG and CC-CEDICT contributors",
)


MAX_DING_SENSES = 4
DE_FORM_OF = re.compile(
    r"^(?:Nominativ |Genitiv |Dativ |Akkusativ )?(?:Plural|Partizip|Präteritum|Präsens|Komparativ|Superlativ|"
    r"\d\. Person|Simple Past|Past Participle|Present Participle|Gerundium)[^:]*?\bvon[: ]+(\S+?)[.;,]?$"
)
# Ranks at or above this go after all other results (see docs/dictionary-format.md)
DEMOTED = 100_000


class Merger:
    """Collects headwords by (base form key, part of speech) and inflected forms by base form."""

    def __init__(self):
        self.headwords: "OrderedDict[tuple[str, str | None], Headword]" = OrderedDict()
        self.by_key: dict[str, list[Headword]] = defaultdict(list)
        self.forms_of: dict[str, set[str]] = defaultdict(set)

    def headword(self, lemma: str, pos: str | None) -> Headword:
        k = (key(lemma), pos)
        if k not in self.headwords:
            h = Headword(lemma, pos=pos)
            self.headwords[k] = h
            self.by_key[k[0]].append(h)
        return self.headwords[k]

    def by_lemma(self, lemma_key: str) -> list[Headword]:
        return self.by_key.get(lemma_key, [])

    def write(self, writer: PackWriter) -> None:
        form_keys = set()
        for lemma_key, forms in self.forms_of.items():
            for h in self.by_key.get(lemma_key, []):
                h.forms |= forms
                form_keys |= {key(f) for f in forms}
        for h in self.headwords.values():
            # "trembling" is mainly a form of "tremble": an entry known only from
            # Ding that is also an inflected form of another word goes last
            lemma_key = key(h.lemma)
            if (lemma_key in form_keys and lemma_key.endswith(("ing", "ed"))
                    and all(s.source == DING.id for s in h.senses)):
                h.rank += DEMOTED
            # Lower rank first: builders may mark common words; among the rest,
            # more translations first, so the usual reading of a word wins
            h.rank -= sum(len(s.translations) for s in h.senses)
            writer.add(h)


def build_en_de(out: str, version: str, enwiktionary: str, dewiktionary: str | None, ding: str | None) -> dict:
    merger = Merger()
    used = [ENWIKTIONARY]

    for entry in src.wiktextract(enwiktionary, "en"):
        word, pos = entry.get("word", ""), entry.get("pos")
        if not word:
            continue
        senses = src.real_senses(entry)
        targets = src.form_of_targets(entry)
        if targets and not senses:
            for target in targets:
                merger.forms_of[key(target)].add(word)
            continue
        groups: "OrderedDict[str, list[str]]" = OrderedDict()
        for t in entry.get("translations", []):
            if t.get("lang_code") == "de" and t.get("word"):
                groups.setdefault(t.get("sense") or "", []).append(t["word"])
        if not groups:
            continue  # German comes from the other sources, or this word gets none
        h = merger.headword(word, pos)
        h.forms |= src.entry_forms(entry)
        for sense_text, words in groups.items():
            h.senses.append(Sense(words, sense_text, "en", ENWIKTIONARY.id))

    if dewiktionary:
        used.append(DEWIKTIONARY)
        for entry in src.wiktextract(dewiktionary, "en"):
            word = entry.get("word", "")
            meanings = []
            for gloss in (s["glosses"][-1] for s in src.real_senses(entry)):
                # The German Wiktionary describes inflected forms in words:
                # "Plural von child" is a form of "child", not a meaning
                form_of = DE_FORM_OF.match(gloss)
                if form_of:
                    merger.forms_of[key(form_of.group(1))].add(word)
                else:
                    meanings.append(gloss)
            if word and meanings:
                h = merger.headword(word, entry.get("pos"))
                h.senses.append(Sense(meanings[:4], "", "de", DEWIKTIONARY.id))

    if ding:
        used.append(DING)
        # Each Ding line is one sense; a word can have several lines
        ding_senses: dict[str, list[list[str]]] = defaultdict(list)
        ding_forms: dict[str, set[str]] = defaultdict(set)
        ding_lemma: dict[str, str] = {}
        for e in src.ding(ding):
            k = key(e.english)
            ding_senses[k].append(e.german)
            ding_forms[k] |= e.english_forms
            ding_lemma.setdefault(k, e.english)
        for k, senses in ding_senses.items():
            existing = merger.by_lemma(k)
            target = existing[0] if existing else merger.headword(ding_lemma[k], None)
            known = {t for h in existing for s in h.senses for t in s.translations}
            # Ding's line order is arbitrary; the central meaning usually has the most synonyms
            for german in sorted(senses, key=len, reverse=True)[:MAX_DING_SENSES]:
                extra = [g for g in german if g not in known]
                if extra:
                    target.senses.append(Sense(extra, "", "de", DING.id))
                    known |= set(extra)
            merger.forms_of[k] |= ding_forms[k]

    writer = PackWriter(out, "en", "de", "English → German", version, used)
    merger.write(writer)
    return writer.close()


def build_ja(out: str, version: str, jmdict: str, target: str) -> dict:
    """Japanese → German (glosses "ger") or → English ("eng"); German packs
    fall back to English glosses for entries without German."""
    lang = {"de": "ger", "en": "eng"}[target]
    merger = Merger()
    for e in src.jmdict(jmdict):
        senses = e.senses_by_lang.get(lang)
        definition_language = target
        if not senses and target != "en":
            senses = e.senses_by_lang.get("eng")
            definition_language = "en"
        if not senses:
            continue
        lemma = e.kanji[0] if e.kanji else e.readings[0]
        h = merger.headword(lemma, e.pos)
        # JMdict lists the usual reading first; later entries with the same
        # spelling (archaic or rare readings) must not replace it
        h.reading = h.reading or (e.readings[0] if e.readings else None)
        h.forms |= set(e.kanji[1:]) | set(e.readings)
        for glosses in senses:
            if definition_language == target:
                h.senses.append(Sense(glosses, "", target, JMDICT.id))
            else:
                h.senses.append(Sense([], "; ".join(glosses), "en", JMDICT.id))
        if e.common:
            h.rank -= 1000
    title = {"de": "Japanese → German", "en": "Japanese → English"}[target]
    writer = PackWriter(out, "ja", target, title, version, [JMDICT])
    merger.write(writer)
    return writer.close()


def build_zh_en(out: str, version: str, cedict: str) -> dict:
    merger = Merger()
    for e in src.cedict(cedict):
        h = merger.headword(e.simplified, None)
        h.reading = h.reading or e.pinyin
        if e.traditional != e.simplified:
            h.forms.add(e.traditional)
        h.senses.append(Sense(e.glosses, "", "en", CEDICT.id))
    writer = PackWriter(out, "zh", "en", "Chinese → English", version, [CEDICT])
    merger.write(writer)
    return writer.close()
