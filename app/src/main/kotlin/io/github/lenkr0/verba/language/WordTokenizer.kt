package io.github.lenkr0.verba.language

import android.icu.text.BreakIterator
import android.icu.util.ULocale

/** A piece of the selected text; [start]/[end] are UTF-16 offsets into it. */
data class Token(val text: String, val start: Int, val end: Int, val isWord: Boolean)

/** A sentence of the selection with its words. */
data class Sentence(val text: String, val start: Int, val tokens: List<Token>)

/**
 * Splits text into sentences and words with ICU, which ships with Android.
 * Its dictionaries also segment languages written without spaces – Chinese,
 * Japanese, Thai, Khmer, Lao, Burmese.
 */
object WordTokenizer {
    // ICU rule status ranges: letters 200–299, kana 300–399, ideographs 400–499.
    // Numbers (100–199) and punctuation/spaces (0–99) can't be looked up.
    private const val WORD_LETTER = 200
    private const val WORD_IDEO_LIMIT = 500

    fun sentences(text: String, language: String): List<Sentence> {
        val locale = ULocale.forLanguageTag(language)
        val breaker = BreakIterator.getSentenceInstance(locale).apply { setText(text) }
        val result = mutableListOf<Sentence>()
        var start = breaker.first()
        var end = breaker.next()
        while (end != BreakIterator.DONE) {
            val raw = text.substring(start, end)
            val trimmedStart = start + (raw.length - raw.trimStart().length)
            val sentenceText = raw.trim()
            if (sentenceText.isNotEmpty()) {
                result += Sentence(sentenceText, trimmedStart, words(sentenceText, language))
            }
            start = end
            end = breaker.next()
        }
        return result
    }

    fun words(text: String, language: String): List<Token> {
        val breaker = BreakIterator.getWordInstance(ULocale.forLanguageTag(language)).apply { setText(text) }
        val tokens = mutableListOf<Token>()
        var start = breaker.first()
        var end = breaker.next()
        while (end != BreakIterator.DONE) {
            val status = breaker.ruleStatus
            tokens += Token(text.substring(start, end), start, end, isWord = status in WORD_LETTER until WORD_IDEO_LIMIT)
            start = end
            end = breaker.next()
        }
        return tokens
    }
}
