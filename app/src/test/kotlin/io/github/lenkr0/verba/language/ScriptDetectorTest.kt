package io.github.lenkr0.verba.language

import org.junit.Assert.assertEquals
import org.junit.Test

class ScriptDetectorTest {
    @Test
    fun scriptsOfSingleLanguages() {
        assertEquals("ja", ScriptDetector.guess("猫が好きです", emptyList()))
        assertEquals("ko", ScriptDetector.guess("나는 학생입니다", emptyList()))
        assertEquals("th", ScriptDetector.guess("สวัสดีครับ", emptyList()))
    }

    @Test
    fun hanIsChineseUnlessJapaneseWasUsedLast() {
        assertEquals("zh", ScriptDetector.guess("我每天学习中文。", emptyList()))
        assertEquals("zh", ScriptDetector.guess("学习", listOf("en", "zh")))
        // a kanji-only word from someone reading Japanese
        assertEquals("ja", ScriptDetector.guess("勉強", listOf("ja", "zh")))
    }

    @Test
    fun latinTakesTheLastLatinLanguage() {
        assertEquals("en", ScriptDetector.guess("running", emptyList()))
        assertEquals("fr", ScriptDetector.guess("courir", listOf("zh", "fr", "en")))
        assertEquals("en", ScriptDetector.guess("running", listOf("ja", "zh")))
    }

    @Test
    fun cyrillicTakesTheLastCyrillicLanguage() {
        assertEquals("ru", ScriptDetector.guess("читать", listOf("en")))
        assertEquals("uk", ScriptDetector.guess("читати", listOf("uk", "ru")))
    }

    @Test
    fun noLettersFallsBackToTheLastLanguage() {
        assertEquals("de", ScriptDetector.guess("1984", listOf("de")))
        assertEquals("en", ScriptDetector.guess("…", emptyList()))
    }
}
