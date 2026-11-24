package io.github.lenkr0.verba.language

/**
 * Turns numbered pinyin as CC-CEDICT writes it ("xue2 xi2", "lu:4") into
 * pinyin with tone marks ("xué xí", "lǜ"). Anything else stays as it is.
 */
object Pinyin {
    private val syllable = Regex("([a-zA-Zü:]+)([1-5])")
    private val marks = mapOf(
        'a' to "āáǎàa", 'e' to "ēéěèe", 'i' to "īíǐìi", 'o' to "ōóǒòo", 'u' to "ūúǔùu", 'ü' to "ǖǘǚǜü",
    )

    fun withToneMarks(numbered: String): String =
        syllable.replace(numbered) { match ->
            val letters = match.groupValues[1].replace("u:", "ü").replace("U:", "Ü")
            val tone = match.groupValues[2].toInt()
            mark(letters, tone)
        }

    private fun mark(syllable: String, tone: Int): String {
        val lower = syllable.lowercase()
        // The tone goes on a or e; in "ou" on the o; otherwise on the last vowel
        val index = when {
            'a' in lower -> lower.indexOf('a')
            'e' in lower -> lower.indexOf('e')
            "ou" in lower -> lower.indexOf('o')
            else -> lower.indexOfLast { it in marks }
        }
        if (index < 0) return syllable
        val vowel = lower[index]
        var marked = marks.getValue(vowel)[tone - 1]
        if (syllable[index].isUpperCase()) marked = marked.uppercaseChar()
        return syllable.substring(0, index) + marked + syllable.substring(index + 1)
    }
}
