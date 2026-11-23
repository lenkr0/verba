package io.github.lenkr0.verba.dictionary

import io.github.lenkr0.verba.language.Lemmatizer

/** A text to look up, and the text in the sentence it stands for ("give up" for "gave up"). */
data class Candidate(val text: String, val surface: String)

/**
 * The texts to try in a dictionary for a tapped word, best first.
 *
 * [word] is the tapped word, [following] the text from that word to the end
 * of its sentence. Languages without spaces are matched by the longest
 * dictionary entry starting at the tapped character (ICU's segmentation and
 * the dictionary don't always agree: 我想 is two words, 食べ|た one); other
 * languages first try phrases of up to four words ("gave up" → "give up").
 */
object LookupCandidates {
    private val withoutSpaces = setOf("zh", "ja", "th", "lo", "km", "my")
    private const val MAX_CHARS = 12
    private const val MAX_WORDS = 4

    fun of(language: String, word: String, following: String): List<Candidate> {
        val start = following.ifBlank { word }
        val candidates = LinkedHashMap<String, Candidate>()
        fun add(text: String, surface: String) {
            if (text.isNotBlank() && text !in candidates) candidates[text] = Candidate(text, surface)
        }
        if (language in withoutSpaces) {
            val chars = start.codePoints().toArray()
            for (length in minOf(chars.size, MAX_CHARS) downTo 1) {
                val prefix = String(chars, 0, length)
                if (prefix.isBlank()) continue
                add(prefix, prefix)
                if (language == "ja") JapaneseDeinflector.candidates(prefix).forEach { add(it, prefix) }
            }
        } else {
            val lemmatizer = Lemmatizer.forLanguage(language)
            // A phrase never crosses punctuation: "gave up smoking, finally"
            val clause = start.split(CLAUSE_END, limit = 2).first()
            val words = clause.split(Regex("\\s+")).map { it.trim(*PUNCTUATION) }.filter { it.isNotEmpty() }
            for (count in minOf(words.size, MAX_WORDS) downTo 2) {
                val phrase = words.take(count)
                val surface = phrase.joinToString(" ")
                add(surface, surface)
                add((listOf(lemmatizer.lemma(phrase.first())) + phrase.drop(1)).joinToString(" "), surface)
            }
            add(word, word)
            add(lemmatizer.lemma(word), word)
        }
        add(word, word)
        return candidates.values.toList()
    }

    private val CLAUSE_END = Regex("[,.;:!?…—–()\"“”]")
    private val PUNCTUATION = charArrayOf('.', ',', ';', ':', '!', '?', '"', '“', '”', '‘', '’', '(', ')', '…', '—', '–')
}
