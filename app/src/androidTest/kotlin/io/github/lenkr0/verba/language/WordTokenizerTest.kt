package io.github.lenkr0.verba.language

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WordTokenizerTest {
    private fun words(text: String, language: String) =
        WordTokenizer.words(text, language).filter { it.isWord }.map { it.text }

    @Test
    fun englishWordsWithoutPunctuationAndNumbers() {
        assertEquals(listOf("She", "was", "running", "late", "again"), words("She was running late – again, 3 times.", "en").dropLast(1))
        assertTrue("numbers are not words", "3" !in words("3 times", "en"))
    }

    @Test
    fun chineseIsSegmentedWithoutSpaces() {
        val result = words("我每天学习中文。", "zh")
        assertTrue("got $result", "学习" in result && "中文" in result)
    }

    @Test
    fun japaneseIsSegmentedWithoutSpaces() {
        val result = words("私は毎日日本語を勉強します。", "ja")
        assertTrue("got $result", "日本語" in result && "勉強" in result)
    }

    @Test
    fun offsetsPointIntoTheSentence() {
        val sentences = WordTokenizer.sentences("It rained. She was running late!", "en")
        assertEquals(listOf("It rained.", "She was running late!"), sentences.map { it.text })
        val second = sentences[1]
        val running = second.tokens.first { it.text == "running" }
        assertEquals("running", second.text.substring(running.start, running.end))
    }
}
