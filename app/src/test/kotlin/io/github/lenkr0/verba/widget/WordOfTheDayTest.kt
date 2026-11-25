package io.github.lenkr0.verba.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class WordOfTheDayTest {
    private val ids = listOf(
        "3f2b8c1e-0d4a-4c6e-9b7a-1e2f3a4b5c6d",
        "a1b2c3d4-e5f6-4789-8abc-def012345678",
        "7c9e6679-7425-40de-944b-e07fc1f90ae7",
        "e4eaaaf2-d142-11e1-b3e4-080027620cdd",
    )

    @Test
    fun matchesTheDocumentedAlgorithm() {
        // Expected picks computed with Python's hashlib, as another app would
        assertEquals(ids[0], WordOfTheDay.pick(ids, LocalDate.of(2026, 11, 25)))
        assertEquals(ids[3], WordOfTheDay.pick(ids, LocalDate.of(2026, 11, 26)))
        assertEquals(ids[3], WordOfTheDay.pick(ids, LocalDate.of(2027, 1, 1)))
        assertEquals(
            "1cfcb091fd4ce847",
            WordOfTheDay.key(LocalDate.of(2026, 11, 25), ids[0]).take(8).joinToString("") { "%02x".format(it) },
        )
    }

    @Test
    fun orderOfTheWordsDoesNotMatter() {
        val date = LocalDate.of(2026, 11, 25)
        assertEquals(WordOfTheDay.pick(ids, date), WordOfTheDay.pick(ids.reversed(), date))
    }

    @Test
    fun noWordsNoPick() {
        assertNull(WordOfTheDay.pick(emptyList(), LocalDate.of(2026, 11, 25)))
    }
}
