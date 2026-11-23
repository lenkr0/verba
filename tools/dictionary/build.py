"""Builds one dictionary pack and prints its metadata as JSON.

    python tools/dictionary/build.py en-de --out out/en-de.sqlite \\
        --enwiktionary kaikki.org-dictionary-English.jsonl \\
        --dewiktionary de-extract.jsonl.gz --ding de-en.txt.gz
    python tools/dictionary/build.py ja-de --out out/ja-de.sqlite --jmdict JMdict.gz
    python tools/dictionary/build.py ja-en --out out/ja-en.sqlite --jmdict JMdict.gz
    python tools/dictionary/build.py zh-en --out out/zh-en.sqlite --cedict cedict.txt.gz
"""

import argparse
import json
import os
import sys
from datetime import date

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from verba_dict import builders  # noqa: E402


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("pair", choices=["en-de", "ja-de", "ja-en", "zh-en"])
    parser.add_argument("--out", required=True)
    parser.add_argument("--version", default=date.today().strftime("%Y.%m.%d"))
    parser.add_argument("--enwiktionary")
    parser.add_argument("--dewiktionary")
    parser.add_argument("--ding")
    parser.add_argument("--jmdict")
    parser.add_argument("--cedict")
    args = parser.parse_args()

    os.makedirs(os.path.dirname(os.path.abspath(args.out)), exist_ok=True)
    if args.pair == "en-de":
        meta = builders.build_en_de(args.out, args.version, args.enwiktionary, args.dewiktionary, args.ding)
    elif args.pair in ("ja-de", "ja-en"):
        meta = builders.build_ja(args.out, args.version, args.jmdict, args.pair.split("-")[1])
    else:
        meta = builders.build_zh_en(args.out, args.version, args.cedict)
    meta["size"] = os.path.getsize(args.out)
    sys.stdout.reconfigure(encoding="utf-8")
    print(json.dumps(meta, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
