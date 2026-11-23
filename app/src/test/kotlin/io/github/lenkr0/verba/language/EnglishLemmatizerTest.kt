package io.github.lenkr0.verba.language

import org.junit.Assert.assertEquals
import org.junit.Test

class EnglishLemmatizerTest {
    private fun check(vararg pairs: Pair<String, String>) = pairs.forEach { (word, lemma) ->
        assertEquals("lemma of $word", lemma, EnglishLemmatizer.lemma(word))
    }

    @Test
    fun regularInflections() = check(
        "running" to "run", "stopped" to "stop", "walked" to "walk", "walking" to "walk",
        "played" to "play", "showing" to "show", "looked" to "look", "rained" to "rain",
        "studies" to "study", "studied" to "study", "cats" to "cat", "books" to "book",
        "watches" to "watch", "boxes" to "box", "classes" to "class", "wanted" to "want",
        "trembling" to "tremble", "handled" to "handle", "giggled" to "giggle", "curled" to "curl",
        "howled" to "howl", "pulled" to "pull",
    )

    @Test
    fun irregularAndSilentE() = check(
        "went" to "go", "was" to "be", "children" to "child", "thought" to "think",
        "making" to "make", "used" to "use", "hoping" to "hope", "smiled" to "smile",
        "leaves" to "leaf", "movies" to "movie",
    )

    @Test
    fun ambiguousFormsStayUnchanged() = check(
        // could be "hop" + ed or "hope" + d without a dictionary
        "hoped" to "hope", // in the table
        "danced" to "dance", // in the table
        "glared" to "glared", // not in the table: left alone rather than guessed
        "falling" to "fall", "passed" to "pass", "spelled" to "spell",
    )

    @Test
    fun wordsThatOnlyLookInflected() = check(
        "morning" to "morning", "nothing" to "nothing", "string" to "string", "always" to "always",
        "news" to "news", "hundred" to "hundred", "bus" to "bus", "this" to "this",
    )

    @Test
    fun capsAndSpacingFromMangaAreNormalized() = check("RUNNING" to "run", "  Cats " to "cat")

    @Test
    fun otherLanguagesKeepTheWord() {
        assertEquals("学习", Lemmatizer.forLanguage("zh").lemma("学习"))
        assertEquals("Häuser", Lemmatizer.forLanguage("de").lemma(" Häuser "))
    }

    @Test
    fun lemmaKeyIgnoresCaseAndWidth() {
        assertEquals(LemmaKey.of("Run"), LemmaKey.of("run "))
        // full-width Latin letters (common in CJK texts) match normal ones
        assertEquals(LemmaKey.of("ＡＢＣ"), LemmaKey.of("abc"))
    }
}
