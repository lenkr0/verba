package io.github.lenkr0.verba.language

import java.lang.Character.UnicodeScript

/**
 * Guesses the language of a selection from its writing system. Scripts used by
 * one language decide on their own (Kana → Japanese, Hangul → Korean); for
 * scripts shared by many languages (Latin, Cyrillic, Arabic, Han) the most
 * recently used language with that script wins.
 */
object ScriptDetector {
    private val defaultsByScript = mapOf(
        UnicodeScript.LATIN to "en",
        UnicodeScript.HAN to "zh",
        UnicodeScript.CYRILLIC to "ru",
        UnicodeScript.ARABIC to "ar",
        UnicodeScript.GREEK to "el",
        UnicodeScript.HEBREW to "he",
        UnicodeScript.THAI to "th",
        UnicodeScript.DEVANAGARI to "hi",
        UnicodeScript.BENGALI to "bn",
        UnicodeScript.HANGUL to "ko",
    )

    private val languagesByScript = mapOf(
        UnicodeScript.HAN to setOf("zh", "ja"),
        UnicodeScript.CYRILLIC to setOf("ru", "uk", "bg", "sr", "mk", "be", "kk"),
        UnicodeScript.ARABIC to setOf("ar", "fa", "ur"),
        UnicodeScript.DEVANAGARI to setOf("hi", "mr", "ne"),
    )

    // Languages written in Latin script: everything not listed for another script
    private val nonLatin: Set<String> = languagesByScript.values.flatten().toSet() +
        setOf("el", "he", "th", "bn", "ko")

    fun guess(text: String, recentLanguages: List<String>): String {
        val counts = mutableMapOf<UnicodeScript, Int>()
        text.codePoints().forEach { cp ->
            if (Character.isLetter(cp)) {
                val script = UnicodeScript.of(cp)
                counts[script] = (counts[script] ?: 0) + 1
            }
        }
        if (counts.isEmpty()) return recentLanguages.firstOrNull() ?: "en"

        // Kana only appears in Japanese, even when most characters are Han
        if ((counts[UnicodeScript.HIRAGANA] ?: 0) + (counts[UnicodeScript.KATAKANA] ?: 0) > 0) return "ja"

        val script = counts.maxBy { it.value }.key
        val candidates = languagesByScript[script]
        val recent = when {
            candidates != null -> recentLanguages.firstOrNull { it in candidates }
            script == UnicodeScript.LATIN -> recentLanguages.firstOrNull { it !in nonLatin }
            else -> null
        }
        return recent ?: defaultsByScript[script] ?: recentLanguages.firstOrNull() ?: "en"
    }
}
