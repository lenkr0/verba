package io.github.lenkr0.verba.dictionary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LookupCandidatesTest {
    private fun texts(language: String, word: String, following: String) =
        LookupCandidates.of(language, word, following).map { it.text }

    @Test
    fun englishTriesPhrasesThenTheWordThenItsBaseForm() {
        assertEquals(
            listOf("gave up smoking", "give up smoking", "gave up", "give up", "gave", "give"),
            texts("en", "gave", "gave up smoking, finally."),
        )
    }

    @Test
    fun aPhraseInItsBaseFormStillMarksTheTextAsItStands() {
        val giveUp = LookupCandidates.of("en", "gave", "gave up smoking").first { it.text == "give up" }
        assertEquals("gave up", giveUp.surface)
    }

    @Test
    fun singleWordAtTheEndOfASentence() {
        assertEquals(listOf("hobbits", "hobbit"), texts("en", "hobbits", "hobbits."))
        assertEquals(listOf("running", "run"), texts("en", "running", ""))
    }

    @Test
    fun chineseTriesTheLongestPieceFirst() {
        val candidates = texts("zh", "我想", "我想看中文小说。")
        assertEquals("我想看中文小说。", candidates.first())
        assertTrue(candidates.indexOf("我想") < candidates.indexOf("我"))
        assertTrue("我" in candidates)
    }

    @Test
    fun japaneseAddsDictionaryFormsOfEachPiece() {
        val candidates = LookupCandidates.of("ja", "食べ", "食べた。")
        val taberu = candidates.first { it.text == "食べる" }
        assertEquals("the surface is what stood in the text", "食べた", taberu.surface)
        assertTrue(candidates.indexOf(taberu) < candidates.indexOfFirst { it.text == "食" })
    }
}
