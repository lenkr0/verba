"""Lookup keys, normalized exactly like the app's LemmaKey (Kotlin):
NFKC, trimmed, lower case (Locale.ROOT)."""

import unicodedata


def key(text: str) -> str:
    return unicodedata.normalize("NFKC", text.strip()).lower()
