package io.github.lenkr0.verba.dictionary

import org.junit.Assert.assertTrue
import org.junit.Test

class JapaneseDeinflectorTest {
    private fun assertFinds(conjugated: String, dictionaryForm: String) {
        val candidates = JapaneseDeinflector.candidates(conjugated)
        assertTrue("$conjugated → $dictionaryForm, got $candidates", dictionaryForm in candidates)
    }

    @Test
    fun ichidanVerbs() {
        assertFinds("食べた", "食べる")
        assertFinds("食べなかった", "食べる")
        assertFinds("食べました", "食べる")
        assertFinds("食べている", "食べる")
        assertFinds("食べたい", "食べる")
        assertFinds("見られる", "見る")
    }

    @Test
    fun godanVerbs() {
        assertFinds("行きました", "行く")
        assertFinds("書いた", "書く")
        assertFinds("泳いで", "泳ぐ")
        assertFinds("話さない", "話す")
        assertFinds("待って", "待つ")
        assertFinds("死んだ", "死ぬ")
        assertFinds("遊ばなかった", "遊ぶ")
        assertFinds("読みます", "読む")
        assertFinds("帰ろう", "帰る")
        assertFinds("買えば", "買う")
        assertFinds("行ける", "行く")
    }

    @Test
    fun irregularVerbsAndAdjectives() {
        assertFinds("勉強した", "勉強する")
        assertFinds("来なかった", "来る")
        assertFinds("行った", "行く")
        assertFinds("高かった", "高い")
        assertFinds("高くなかった", "高い")
        assertFinds("寒くて", "寒い")
    }

    @Test
    fun plainWordsGiveFewCandidates() {
        assertTrue(JapaneseDeinflector.candidates("猫").isEmpty())
        assertTrue(JapaneseDeinflector.candidates("漫画").size < 3)
    }
}
