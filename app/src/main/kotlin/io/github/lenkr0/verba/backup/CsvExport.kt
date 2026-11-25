package io.github.lenkr0.verba.backup

import io.github.lenkr0.verba.data.EntryListItem
import io.github.lenkr0.verba.data.LearningStatus
import io.github.lenkr0.verba.data.VocabRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.time.Instant
import java.time.ZoneId

/**
 * One row per word, for spreadsheets and other vocabulary apps: RFC 4180
 * quoting, UTF-8 with a byte order mark so Excel shows Chinese and Japanese
 * correctly.
 */
class CsvExport(private val vocabulary: VocabRepository, private val zone: () -> ZoneId = ZoneId::systemDefault) {
    suspend fun export(out: OutputStream): Int = withContext(Dispatchers.IO) {
        val items = vocabulary.entryList().first().sortedBy { it.entry.createdAt }
        val writer = out.bufferedWriter(Charsets.UTF_8)
        writer.write(BYTE_ORDER_MARK)
        writer.write(line(HEADER))
        for (item in items) writer.write(line(row(item)))
        writer.flush()
        items.size
    }

    private fun row(item: EntryListItem): List<String> {
        val e = item.entry
        return listOf(
            e.lemma,
            e.translation,
            e.definition,
            e.language,
            e.targetLanguage,
            item.latestSentence.orEmpty(),
            item.latestSource.orEmpty(),
            LearningStatus.of(item.learningStatus).name.lowercase(),
            Instant.ofEpochMilli(e.createdAt).atZone(zone()).toLocalDate().toString(),
        )
    }

    companion object {
        /** U+FEFF: tells Excel the file is UTF-8. */
        private const val BYTE_ORDER_MARK = 0xFEFF

        val HEADER = listOf("word", "translation", "definition", "language", "target_language", "sentence", "source", "status", "added")

        fun line(fields: List<String>): String = fields.joinToString(",", postfix = "\r\n", transform = ::quote)

        /** Quoted when needed; quotes inside are doubled. */
        fun quote(value: String): String =
            if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
                "\"" + value.replace("\"", "\"\"") + "\""
            } else {
                value
            }
    }
}
