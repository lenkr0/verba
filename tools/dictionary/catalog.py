"""Compresses built packs and writes catalog.json, the list the app shows
under Settings → Dictionaries.

    python tools/dictionary/catalog.py out/ https://github.com/lenkr0/verba/releases/download/dictionaries
"""

import gzip
import hashlib
import json
import os
import shutil
import sqlite3
import sys
from datetime import datetime, timezone

CATALOG_FORMAT = "verba-dictionary-catalog"
CATALOG_VERSION = 1


def compress(path: str) -> str:
    target = path + ".gz"
    with open(path, "rb") as src, gzip.open(target, "wb", compresslevel=9) as dst:
        shutil.copyfileobj(src, dst, 1 << 20)
    return target


def sha256(path: str) -> str:
    digest = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def main() -> None:
    directory, base_url = sys.argv[1], sys.argv[2].rstrip("/")
    packs = []
    for name in sorted(os.listdir(directory)):
        if not name.endswith(".sqlite"):
            continue
        path = os.path.join(directory, name)
        db = sqlite3.connect(path)
        meta = dict(db.execute("SELECT key, value FROM meta"))
        db.close()
        gz = compress(path)
        pack_id = f"{meta['source_language']}-{meta['target_language']}"
        packs.append({
            "id": pack_id,
            "sourceLanguage": meta["source_language"],
            "targetLanguage": meta["target_language"],
            "title": meta["title"],
            "version": meta["version"],
            "formatVersion": int(meta["format_version"]),
            "headwords": int(meta["headwords"]),
            "url": f"{base_url}/{os.path.basename(gz)}",
            "size": os.path.getsize(gz),
            "installedSize": os.path.getsize(path),
            "sha256": sha256(gz),
            "sources": json.loads(meta["sources"]),
        })
    catalog = {
        "format": CATALOG_FORMAT,
        "version": CATALOG_VERSION,
        "updatedAt": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "packs": packs,
    }
    with open(os.path.join(directory, "catalog.json"), "w", encoding="utf-8") as f:
        json.dump(catalog, f, ensure_ascii=False, indent=2)
    for p in packs:
        print(f"{p['id']}: {p['headwords']} headwords, {p['size'] / 1048576:.1f} MB download")


if __name__ == "__main__":
    main()
