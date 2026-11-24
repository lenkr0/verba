package io.github.lenkr0.verba.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RepositoryHelpersTest {
    @Test
    fun translationsAreCombinedOnce() {
        assertEquals("laufen; rennen; flitzen", combineTranslations("laufen; rennen", "Rennen; flitzen"))
        assertEquals("laufen", combineTranslations("", "laufen"))
        assertEquals("", combineTranslations(" ; ", ""))
    }

    @Test
    fun likeWildcardsInTheQueryAreLiteral() {
        assertNull(likePattern("   "))
        assertEquals("%run%", likePattern(" run "))
        assertEquals("%100\\%%", likePattern("100%"))
        assertEquals("%a\\_b%", likePattern("a_b"))
        assertEquals("%a\\\\b%", likePattern("a\\b"))
    }
}
