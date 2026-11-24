package io.github.lenkr0.verba.dictionary

import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.security.MessageDigest
import java.util.zip.GZIPOutputStream

/** Builds small packs in the format of docs/dictionary-format.md for tests. */
object TestPacks {
    data class Word(
        val lemma: String,
        val translations: List<String>,
        val definition: String = "",
        val forms: List<String> = emptyList(),
        val reading: String? = null,
        val rank: Int = 0,
    )

    fun create(
        file: File,
        source: String,
        target: String,
        words: List<Word>,
        formatVersion: Int = 1,
        format: String = "verba-dictionary",
    ): File {
        file.delete()
        val db = SQLiteDatabase.openOrCreateDatabase(file, null)
        db.execSQL("CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)")
        db.execSQL("CREATE TABLE headwords (id INTEGER PRIMARY KEY, lemma TEXT NOT NULL, lemma_key TEXT NOT NULL, reading TEXT, pos TEXT, rank INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE TABLE senses (headword_id INTEGER NOT NULL, position INTEGER NOT NULL, translations TEXT NOT NULL, definition TEXT NOT NULL, definition_language TEXT NOT NULL, source TEXT NOT NULL)")
        db.execSQL("CREATE TABLE forms (form_key TEXT NOT NULL, headword_id INTEGER NOT NULL)")
        val meta = mapOf(
            "format" to format, "format_version" to formatVersion.toString(), "source_language" to source,
            "target_language" to target, "title" to "$source → $target", "version" to "2026.01.01",
            "headwords" to words.size.toString(),
            "sources" to """[{"id":"test","name":"Test dictionary","url":"https://example.org","license":"CC0","license_url":"https://example.org/cc0","attribution":"Tests"}]""",
        )
        meta.forEach { (k, v) -> db.execSQL("INSERT INTO meta VALUES (?, ?)", arrayOf(k, v)) }
        words.forEachIndexed { index, word ->
            val id = index + 1L
            db.execSQL(
                "INSERT INTO headwords VALUES (?, ?, ?, ?, NULL, ?)",
                arrayOf<Any?>(id, word.lemma, word.lemma.lowercase(), word.reading, word.rank),
            )
            db.execSQL(
                "INSERT INTO senses VALUES (?, 0, ?, ?, ?, 'test')",
                arrayOf<Any?>(id, word.translations.joinToString("; "), word.definition, if (word.translations.isEmpty()) "en" else target),
            )
            word.forms.forEach { db.execSQL("INSERT INTO forms VALUES (?, ?)", arrayOf<Any?>(it.lowercase(), id)) }
        }
        db.close()
        File(file.path + "-journal").delete()
        return file
    }

    fun gzip(file: File, target: File): File {
        GZIPOutputStream(target.outputStream()).use { out -> file.inputStream().use { it.copyTo(out) } }
        return target
    }

    fun sha256(file: File): String =
        MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }

    val englishGerman = listOf(
        Word("run", listOf("laufen", "rennen"), "to move quickly on foot", forms = listOf("running", "ran", "runs")),
        Word("give up", listOf("aufgeben")),
        Word("give", listOf("geben"), forms = listOf("gave", "given")),
        Word("hobbit", listOf("Hobbit"), forms = listOf("hobbits")),
    )

    val japaneseEnglish = listOf(
        Word("食べる", emptyList(), "to eat", reading = "たべる"),
        Word("猫", emptyList(), "cat", reading = "ねこ"),
    )
}
