package io.github.lenkr0.verba.language

import java.util.Locale

/**
 * Languages are stored as BCP 47 language tags ("en", "de", "zh", "ja" …).
 * Verba works with any pair; this list only feeds the pickers.
 */
object Languages {
    val all: List<String> = listOf(
        "ar", "bn", "cs", "da", "de", "el", "en", "es", "fa", "fi", "fr", "he", "hi", "hu", "id",
        "it", "ja", "ko", "ms", "nb", "nl", "pl", "pt", "ro", "ru", "sk", "sv", "th", "tr", "uk",
        "vi", "zh",
    )

    /** "Deutsch" for "de" when shown in German, "German" in English. */
    fun displayName(tag: String, inLocale: Locale = Locale.getDefault()): String {
        val name = Locale.forLanguageTag(tag).getDisplayLanguage(inLocale)
        return name.ifBlank { tag }.replaceFirstChar { it.titlecase(inLocale) }
    }

    /** Short label for chips, e.g. "EN", "ZH". */
    fun shortLabel(tag: String): String = tag.substringBefore('-').uppercase(Locale.ROOT)

    /** The device language if Verba knows it, else English. */
    fun defaultTarget(locale: Locale = Locale.getDefault()): String =
        locale.language.takeIf { it in all } ?: "en"
}
