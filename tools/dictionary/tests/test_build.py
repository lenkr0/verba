"""Builds packs from tiny samples in each source's format and checks lookups.
Run: python -m unittest discover tools/dictionary/tests"""

import gzip
import json
import os
import sqlite3
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from verba_dict import builders  # noqa: E402
from verba_dict.keys import key  # noqa: E402

EN_WIKTIONARY = [
    {"word": "run", "lang_code": "en", "pos": "verb",
     "forms": [{"form": "runs", "tags": ["present"]}, {"form": "running"}, {"form": "ran", "tags": ["past"]},
               {"form": "en-verb", "tags": ["inflection-template"]}],
     "senses": [{"glosses": ["To move swiftly.", "To move quickly on two feet."]}],
     "translations": [
         {"lang_code": "de", "word": "rennen", "sense": "to move quickly on two feet"},
         {"lang_code": "de", "word": "laufen", "sense": "to move quickly on two feet"},
         {"lang_code": "fr", "word": "courir", "sense": "to move quickly on two feet"},
         {"lang_code": "de", "word": "leiten", "sense": "to manage"}]},
    {"word": "ran", "lang_code": "en", "pos": "verb",
     "senses": [{"glosses": ["simple past of run"], "form_of": [{"word": "run"}]}]},
    {"word": "obscure", "lang_code": "en", "pos": "adj",
     "senses": [{"glosses": ["Dark, faint or indistinct."]}]},
    {"word": "Lauf", "lang_code": "de", "pos": "noun", "senses": [{"glosses": ["run"]}]},
]

DE_WIKTIONARY = [
    {"word": "obscure", "lang_code": "en", "pos": "adj", "senses": [{"glosses": ["dunkel, unklar"]}]},
]

DING = """# Version :: 1.9
laufen {vi}; rennen {vi} | laufend | gelaufen :: to run | running | run
dunkel; finster :: obscure
Hund {m} | Hunde {pl} :: dog | dogs
"""

JMDICT = """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE JMdict [
<!ENTITY v5r "Godan verb with 'ru' ending">
<!ENTITY n "noun (common) (futsuumeishi)">
]>
<JMdict>
<entry><ent_seq>1</ent_seq>
<k_ele><keb>走る</keb><ke_pri>ichi1</ke_pri></k_ele>
<r_ele><reb>はしる</reb></r_ele>
<sense><pos>&v5r;</pos><gloss>to run</gloss><gloss xml:lang="ger">laufen</gloss><gloss xml:lang="ger">rennen</gloss></sense>
</entry>
<entry><ent_seq>2</ent_seq>
<k_ele><keb>猫</keb></k_ele>
<r_ele><reb>ねこ</reb></r_ele>
<sense><pos>&n;</pos><gloss>cat</gloss></sense>
</entry>
<entry><ent_seq>3</ent_seq>
<k_ele><keb>猫</keb></k_ele>
<r_ele><reb>ねこま</reb></r_ele>
<sense><pos>&n;</pos><gloss>cat (archaic)</gloss></sense>
</entry>
</JMdict>
"""

CEDICT = """# CC-CEDICT
學習 学习 [xue2 xi2] /to learn/to study/
小說 小说 [xiao3 shuo1] /novel/fiction/
"""


def write(directory, name, content, gz=False):
    path = os.path.join(directory, name)
    opener = gzip.open if gz else open
    with opener(path, "wt", encoding="utf-8") as f:
        f.write(content)
    return path


def lookup(pack, word):
    """What the app does: base form or inflected form → senses."""
    db = sqlite3.connect(pack)
    rows = db.execute(
        """SELECT h.lemma, h.reading, s.translations, s.definition, s.definition_language, s.source
           FROM headwords h JOIN senses s ON s.headword_id = h.id
           WHERE h.lemma_key = ?1 OR h.id IN (SELECT headword_id FROM forms WHERE form_key = ?1)
           ORDER BY h.rank, h.id, s.position""",
        (key(word),),
    ).fetchall()
    meta = dict(db.execute("SELECT key, value FROM meta"))
    db.close()
    return rows, meta


class BuildTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.dir = self.tmp.name

    def tearDown(self):
        self.tmp.cleanup()

    def test_english_german(self):
        en = write(self.dir, "en.jsonl.gz", "\n".join(json.dumps(e) for e in EN_WIKTIONARY), gz=True)
        de = write(self.dir, "de.jsonl", "\n".join(json.dumps(e) for e in DE_WIKTIONARY))
        ding = write(self.dir, "de-en.txt", DING)
        pack = os.path.join(self.dir, "en-de.sqlite")
        meta = builders.build_en_de(pack, "test", en, de, ding)
        self.assertEqual(("en", "de"), (meta["source_language"], meta["target_language"]))

        rows, _ = lookup(pack, "running")  # inflected form → run
        self.assertEqual("run", rows[0][0])
        self.assertEqual("rennen; laufen", rows[0][2])
        self.assertEqual("to move quickly on two feet", rows[0][3])
        self.assertNotIn("courir", " ".join(r[2] for r in rows))
        self.assertEqual("run", lookup(pack, "RAN")[0][0][0])  # form-of entry

        rows, _ = lookup(pack, "obscure")  # German from de.wiktionary and Ding
        self.assertIn("dunkel, unklar", [r[2] for r in rows])
        self.assertIn("finster", " ".join(r[2] for r in rows))

        rows, _ = lookup(pack, "dogs")  # only in Ding, with its plural
        self.assertEqual(("dog", "Hund"), (rows[0][0], rows[0][2]))
        self.assertEqual([], lookup(pack, "Lauf")[0])  # German words aren't English headwords

    def test_japanese_german_with_english_fallback(self):
        path = write(self.dir, "JMdict.gz", JMDICT, gz=True)
        pack = os.path.join(self.dir, "ja-de.sqlite")
        builders.build_ja(pack, "test", path, "de")
        rows, _ = lookup(pack, "はしる")  # reading → kanji headword
        self.assertEqual(("走る", "はしる", "laufen; rennen", "de"), (rows[0][0], rows[0][1], rows[0][2], rows[0][4]))
        rows, _ = lookup(pack, "猫")  # no German gloss → English definition
        self.assertEqual(("ねこ", "", "cat", "en"), (rows[0][1], rows[0][2], rows[0][3], rows[0][4]))

    def test_chinese_english(self):
        path = write(self.dir, "cedict.txt", CEDICT)
        pack = os.path.join(self.dir, "zh-en.sqlite")
        builders.build_zh_en(pack, "test", path)
        rows, _ = lookup(pack, "學習")  # traditional → simplified headword
        self.assertEqual(("学习", "xue2 xi2", "to learn; to study"), rows[0][:3])
        _, meta = lookup(pack, "小说")
        self.assertEqual("CC-CEDICT", json.loads(meta["sources"])[0]["name"])


if __name__ == "__main__":
    unittest.main()
