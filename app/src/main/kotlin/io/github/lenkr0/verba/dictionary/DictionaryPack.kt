package io.github.lenkr0.verba.dictionary

import android.database.sqlite.SQLiteDatabase
import io.github.lenkr0.verba.language.LemmaKey
import kotlinx.serialization.json.Json
import java.io.Closeable
import java.io.File

/** A downloaded pack (docs/dictionary-format.md), opened read-only. */
class DictionaryPack private constructor(private val db: SQLiteDatabase, val info: PackInfo) : Closeable {

    /** Entries whose base form or one of whose forms is [text]; best matches first. */
    fun lookup(text: String, maxEntries: Int = MAX_ENTRIES): List<DictionaryEntry> {
        val key = LemmaKey.of(text)
        if (key.isEmpty()) return emptyList()
        val entries = LinkedHashMap<Long, DictionaryEntry>()
        db.rawQuery(LOOKUP, arrayOf(key)).use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                if (id !in entries && entries.size >= maxEntries) continue
                val entry = entries.getOrPut(id) {
                    DictionaryEntry(c.getString(1), c.getStringOrNull(2), c.getStringOrNull(3), emptyList())
                }
                val sense = DictionarySense(
                    translations = c.getString(4).split("; ").filter { it.isNotBlank() },
                    definition = c.getString(5),
                    definitionLanguage = c.getString(6),
                    source = c.getString(7),
                )
                entries[id] = entry.copy(senses = entry.senses + sense)
            }
        }
        return entries.values.toList()
    }

    override fun close() = db.close()

    companion object {
        /** The newest pack format this app understands. */
        const val FORMAT_VERSION = 1
        private const val MAX_ENTRIES = 3

        private val LOOKUP = """
            SELECT h.id, h.lemma, h.reading, h.pos, s.translations, s.definition, s.definition_language, s.source
            FROM headwords h JOIN senses s ON s.headword_id = h.id
            WHERE h.lemma_key = ?1 OR h.id IN (SELECT headword_id FROM forms WHERE form_key = ?1)
            ORDER BY h.rank >= 50000, h.lemma_key != ?1, h.rank, h.id, s.position
            LIMIT 40
        """.trimIndent()

        private val json = Json { ignoreUnknownKeys = true }

        /** Opens [file] and checks it is a pack this app can read; throws [InvalidPackException] if not. */
        fun open(file: File): DictionaryPack {
            val db = try {
                SQLiteDatabase.openDatabase(
                    file.path,
                    null,
                    SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS,
                )
            } catch (e: Exception) {
                throw InvalidPackException("not an SQLite file", e)
            }
            try {
                return DictionaryPack(db, readInfo(db, file.length()))
            } catch (e: Exception) {
                db.close()
                throw if (e is InvalidPackException) e else InvalidPackException("unreadable pack", e)
            }
        }

        private fun readInfo(db: SQLiteDatabase, size: Long): PackInfo {
            val meta = mutableMapOf<String, String>()
            db.rawQuery("SELECT key, value FROM meta", null).use { c ->
                while (c.moveToNext()) meta[c.getString(0)] = c.getString(1)
            }
            if (meta["format"] != "verba-dictionary") throw InvalidPackException("not a Verba dictionary")
            val formatVersion = meta["format_version"]?.toIntOrNull() ?: 0
            if (formatVersion !in 1..FORMAT_VERSION) {
                throw InvalidPackException("format $formatVersion needs a newer Verba")
            }
            val source = meta["source_language"].orEmpty()
            val target = meta["target_language"].orEmpty()
            if (source.isEmpty() || target.isEmpty()) throw InvalidPackException("languages missing")
            return PackInfo(
                id = "$source-$target",
                sourceLanguage = source,
                targetLanguage = target,
                title = meta["title"].orEmpty(),
                version = meta["version"].orEmpty(),
                formatVersion = formatVersion,
                headwords = meta["headwords"]?.toIntOrNull() ?: 0,
                sources = meta["sources"]?.let { json.decodeFromString<List<PackSource>>(it) }.orEmpty(),
                sizeBytes = size,
            )
        }
    }
}

class InvalidPackException(message: String, cause: Throwable? = null) : Exception(message, cause)

private fun android.database.Cursor.getStringOrNull(index: Int): String? = if (isNull(index)) null else getString(index)
