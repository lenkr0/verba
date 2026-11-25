package io.github.lenkr0.verba.backup

import io.github.lenkr0.verba.data.EntryEntity
import io.github.lenkr0.verba.data.EntryListItem
import org.junit.Assert.assertEquals
import org.junit.Test

class ExportFormatTest {
    @Test
    fun csvQuotesOnlyWhenNeeded() {
        assertEquals("run", CsvExport.quote("run"))
        assertEquals("\"laufen, rennen\"", CsvExport.quote("laufen, rennen"))
        assertEquals("\"She said \"\"no\"\".\"", CsvExport.quote("She said \"no\"."))
        assertEquals("\"two\nlines\"", CsvExport.quote("two\nlines"))
        assertEquals("a,\"b,c\",\r\n", CsvExport.line(listOf("a", "b,c", "")))
    }

    private fun item(lemma: String, sentence: String?, start: Int, end: Int) = EntryListItem(
        EntryEntity("id", "en", "de", lemma, lemma, "", "", 0, 0),
        sentence, start, end, null, 1,
    )

    @Test
    fun ankiClozeFromTheSentence() {
        assertEquals(
            "She was {{c1::running}} late &amp; tired.",
            AnkiExport.clozeText(item("run", "She was running late & tired.", 8, 15)),
        )
        // No sentence: the word alone is the gap, the translation is the hint
        assertEquals("{{c1::run}}", AnkiExport.clozeText(item("run", null, -1, -1)))
        assertEquals("&lt;b&gt;<br>x", AnkiExport.html("<b>\nx"))
    }

    @Test
    fun ankiChecksumMatchesAnki() {
        // First 8 hex digits of the SHA-1, as Anki computes it (values from Python's hashlib)
        assertEquals(3748319632L, AnkiExport.checksum("run"))
        assertEquals(2861161827L, AnkiExport.checksum("学习"))
    }
}
