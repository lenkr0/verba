package io.github.lenkr0.verba.review

/** A sentence split around the word to recall: [before] ___ [after]. */
data class Cloze(val before: String, val answer: String, val after: String) {
    companion object {
        /**
         * The gap is at the saved offsets; if they are unknown, at the first
         * occurrence of [wordForm]. Null when there is no sentence around the
         * word, so a gap would show nothing.
         */
        fun of(sentence: String, wordForm: String, start: Int, end: Int): Cloze? {
            val range = when {
                start in 0..<end && end <= sentence.length -> start..<end
                wordForm.isNotBlank() -> sentence.indexOf(wordForm, ignoreCase = true)
                    .takeIf { it >= 0 }
                    ?.let { it..<(it + wordForm.length) }
                else -> null
            } ?: return null
            val cloze = Cloze(
                sentence.substring(0, range.first),
                sentence.substring(range),
                sentence.substring(range.last + 1),
            )
            return cloze.takeUnless { it.before.isBlank() && it.after.isBlank() }
        }
    }
}
