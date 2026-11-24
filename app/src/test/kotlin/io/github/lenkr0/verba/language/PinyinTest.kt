package io.github.lenkr0.verba.language

import org.junit.Assert.assertEquals
import org.junit.Test

class PinyinTest {
    @Test
    fun numbersBecomeToneMarks() {
        assertEquals("xué xí", Pinyin.withToneMarks("xue2 xi2"))
        assertEquals("wǒ", Pinyin.withToneMarks("wo3"))
        assertEquals("xiǎo shuō", Pinyin.withToneMarks("xiao3 shuo1"))
        assertEquals("lǜ", Pinyin.withToneMarks("lu:4"))
        assertEquals("gòu", Pinyin.withToneMarks("gou4"))
        assertEquals("duì", Pinyin.withToneMarks("dui4"))
        assertEquals("ma", Pinyin.withToneMarks("ma5"))
        assertEquals("Běi jīng", Pinyin.withToneMarks("Bei3 jing1"))
    }

    @Test
    fun otherReadingsStayAsTheyAre() {
        assertEquals("たべる", Pinyin.withToneMarks("たべる"))
        assertEquals("xue xi", Pinyin.withToneMarks("xue xi"))
    }
}
