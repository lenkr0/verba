package io.github.lenkr0.verba.backup

import android.database.sqlite.SQLiteDatabase
import androidx.core.database.sqlite.transaction
import io.github.lenkr0.verba.data.EntryListItem
import io.github.lenkr0.verba.data.VocabRepository
import io.github.lenkr0.verba.review.Cloze
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.io.File
import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * An Anki deck package (.apkg) for AnkiDroid or Anki: one cloze note per word,
 * its most recent sentence with the word as the gap, the translation as a hint
 * to tap. The classic package format (collection.anki2, schema 11), which
 * every Anki version imports. Notes keep their id across exports, so
 * importing again updates them instead of adding duplicates. Cards start as
 * new: Anki schedules them itself.
 */
class AnkiExport(
    private val vocabulary: VocabRepository,
    private val workDirectory: File,
    private val now: () -> Long = System::currentTimeMillis,
) {
    suspend fun export(out: OutputStream): Int = withContext(Dispatchers.IO) {
        val items = vocabulary.entryList().first().sortedBy { it.entry.createdAt }
        workDirectory.mkdirs()
        val file = File(workDirectory, "collection.anki2")
        file.delete()
        try {
            SQLiteDatabase.openOrCreateDatabase(file, null).use { db -> write(db, items) }
            ZipOutputStream(out).apply {
                putNextEntry(ZipEntry("collection.anki2"))
                file.inputStream().use { it.copyTo(this) }
                closeEntry()
                putNextEntry(ZipEntry("media"))
                write("{}".toByteArray())
                closeEntry()
                finish()
            }
            out.flush()
        } finally {
            file.delete()
            File(file.path + "-journal").delete()
        }
        items.size
    }

    private fun write(db: SQLiteDatabase, items: List<EntryListItem>) {
        val nowMs = now()
        val nowS = nowMs / 1000
        db.transaction {
            SCHEMA.forEach(db::execSQL)
            db.execSQL(
                "INSERT INTO col VALUES (1, ?, ?, ?, 11, 0, 0, 0, ?, ?, ?, ?, '{}')",
                arrayOf<Any>(nowS, nowMs, nowMs, conf().toString(), models(nowS).toString(), decks(nowS).toString(), deckConf().toString()),
            )
            items.forEachIndexed { i, item ->
                val e = item.entry
                val fields = listOf(clozeText(item), html(e.lemma), html(e.translation), html(e.definition), html(item.latestSource.orEmpty()))
                val noteId = nowMs + i
                val tags = listOfNotNull("verba", e.language, item.latestSource?.let(::tag)).joinToString(" ", " ", " ")
                db.execSQL(
                    "INSERT INTO notes VALUES (?, ?, ?, ?, -1, ?, ?, ?, ?, 0, '')",
                    arrayOf<Any>(noteId, "verba:${e.id}", MODEL_ID, nowS, tags, fields.joinToString("\u001f"), e.lemma, checksum(e.lemma)),
                )
                db.execSQL(
                    "INSERT INTO cards VALUES (?, ?, ?, 0, ?, -1, 0, 0, ?, 0, 0, 0, 0, 0, 0, 0, 0, '')",
                    arrayOf<Any>(nowMs + i, noteId, DECK_ID, nowS, i + 1),
                )
            }
        }
    }

    private fun models(nowS: Long) = buildJsonObject {
        put(MODEL_ID.toString(), buildJsonObject {
            put("id", MODEL_ID)
            put("name", "Verba Cloze")
            put("type", 1) // cloze
            put("mod", nowS)
            put("usn", -1)
            put("sortf", 1) // sort by the word
            put("did", DECK_ID)
            putJsonArray("tmpls") {
                add(buildJsonObject {
                    put("name", "Cloze")
                    put("ord", 0)
                    put("qfmt", FRONT)
                    put("afmt", BACK)
                    put("did", JsonPrimitive(null as String?))
                    put("bqfmt", "")
                    put("bafmt", "")
                })
            }
            put("flds", JsonArray(FIELDS.mapIndexed { ord, name ->
                buildJsonObject {
                    put("name", name)
                    put("ord", ord)
                    put("sticky", false)
                    put("rtl", false)
                    put("font", "Liberation Sans")
                    put("size", 20)
                    putJsonArray("media") {}
                }
            }))
            put("css", CSS)
            put("latexPre", "\\documentclass[12pt]{article}\n\\special{papersize=3in,5in}\n\\usepackage{amssymb,amsmath}\n\\pagestyle{empty}\n\\setlength{\\parindent}{0in}\n\\begin{document}\n")
            put("latexPost", "\\end{document}")
            put("latexsvg", false)
            put("req", buildJsonArray { add(buildJsonArray { add(0); add("any"); add(buildJsonArray { add(0) }) }) })
            putJsonArray("tags") {}
            putJsonArray("vers") {}
        })
    }

    private fun deck(id: Long, name: String, nowS: Long) = buildJsonObject {
        put("id", id)
        put("name", name)
        put("desc", if (id == DECK_ID) "Words saved with Verba" else "")
        put("conf", 1)
        put("dyn", 0)
        put("collapsed", false)
        put("extendNew", 10)
        put("extendRev", 50)
        put("mod", nowS)
        put("usn", -1)
        for (key in listOf("newToday", "revToday", "lrnToday", "timeToday")) putJsonArray(key) { add(0); add(0) }
    }

    private fun decks(nowS: Long) = buildJsonObject {
        put("1", deck(1, "Default", nowS))
        put(DECK_ID.toString(), deck(DECK_ID, "Verba", nowS))
    }

    private fun conf() = buildJsonObject {
        putJsonArray("activeDecks") { add(1) }
        put("curDeck", 1)
        put("curModel", MODEL_ID.toString())
        put("newSpread", 0)
        put("collapseTime", 1200)
        put("timeLim", 0)
        put("estTimes", true)
        put("dueCounts", true)
        put("addToCur", true)
        put("sortType", "noteFld")
        put("sortBackwards", false)
        put("nextPos", 1)
    }

    private fun deckConf() = buildJsonObject {
        put("1", buildJsonObject {
            put("id", 1)
            put("name", "Default")
            put("mod", 0)
            put("usn", 0)
            put("maxTaken", 60)
            put("autoplay", true)
            put("timer", 0)
            put("replayq", true)
            put("new", buildJsonObject {
                putJsonArray("delays") { add(1); add(10) }
                putJsonArray("ints") { add(1); add(4); add(7) }
                put("initialFactor", 2500)
                put("order", 1)
                put("perDay", 20)
                put("bury", true)
                put("separate", true)
            })
            put("lapse", buildJsonObject {
                putJsonArray("delays") { add(10) }
                put("mult", 0)
                put("minInt", 1)
                put("leechFails", 8)
                put("leechAction", 0)
            })
            put("rev", buildJsonObject {
                put("perDay", 200)
                put("ease4", 1.3)
                put("fuzz", 0.05)
                put("ivlFct", 1)
                put("maxIvl", 36500)
                put("minSpace", 1)
                put("bury", true)
            })
        })
    }

    companion object {
        /** Fixed ids: importing a newer export updates the same note type and deck. */
        const val MODEL_ID = 1_732_500_000_001L
        const val DECK_ID = 1_732_500_000_002L
        val FIELDS = listOf("Text", "Word", "Translation", "Definition", "Source")

        private const val FRONT =
            "{{cloze:Text}}{{#Translation}}<div class=\"hint\">{{hint:Translation}}</div>{{/Translation}}"
        private const val BACK =
            "{{cloze:Text}}<hr id=answer><div class=\"word\">{{Word}}</div><div>{{Translation}}</div>" +
                "{{#Definition}}<div class=\"definition\">{{Definition}}</div>{{/Definition}}" +
                "{{#Source}}<div class=\"source\">{{Source}}</div>{{/Source}}"
        // No colors for the card itself: Anki's light and night themes apply
        private const val CSS =
            ".card { font-family: serif; font-size: 22px; text-align: center; }\n" +
                ".cloze { font-weight: bold; color: #1f4e79; }\n" +
                ".word { font-size: 26px; margin-bottom: 8px; }\n" +
                ".hint, .definition, .source { font-family: sans-serif; font-size: 16px; color: #555; margin-top: 8px; }\n" +
                ".nightMode .cloze { color: #9ecbff; }\n.nightMode .hint, .nightMode .definition, .nightMode .source { color: #aaa; }"

        private val SCHEMA = listOf(
            "CREATE TABLE col (id integer primary key, crt integer not null, mod integer not null, scm integer not null, ver integer not null, dty integer not null, usn integer not null, ls integer not null, conf text not null, models text not null, decks text not null, dconf text not null, tags text not null)",
            "CREATE TABLE notes (id integer primary key, guid text not null, mid integer not null, mod integer not null, usn integer not null, tags text not null, flds text not null, sfld integer not null, csum integer not null, flags integer not null, data text not null)",
            "CREATE TABLE cards (id integer primary key, nid integer not null, did integer not null, ord integer not null, mod integer not null, usn integer not null, type integer not null, queue integer not null, due integer not null, ivl integer not null, factor integer not null, reps integer not null, lapses integer not null, left integer not null, odue integer not null, odid integer not null, flags integer not null, data text not null)",
            "CREATE TABLE revlog (id integer primary key, cid integer not null, usn integer not null, ease integer not null, ivl integer not null, lastIvl integer not null, factor integer not null, time integer not null, type integer not null)",
            "CREATE TABLE graves (usn integer not null, oid integer not null, type integer not null)",
            "CREATE INDEX ix_notes_usn on notes (usn)",
            "CREATE INDEX ix_cards_usn on cards (usn)",
            "CREATE INDEX ix_revlog_usn on revlog (usn)",
            "CREATE INDEX ix_cards_nid on cards (nid)",
            "CREATE INDEX ix_cards_sched on cards (did, queue, due)",
            "CREATE INDEX ix_revlog_cid on revlog (cid)",
            "CREATE INDEX ix_notes_csum on notes (csum)",
        )

        /** The sentence with the word as cloze 1; just the word when there is no sentence. */
        internal fun clozeText(item: EntryListItem): String {
            val cloze = Cloze.of(item.latestSentence.orEmpty(), "", item.latestWordStart ?: -1, item.latestWordEnd ?: -1)
            return if (cloze == null) {
                "{{c1::${html(item.entry.lemma)}}}"
            } else {
                html(cloze.before) + "{{c1::" + html(cloze.answer) + "}}" + html(cloze.after)
            }
        }

        internal fun html(text: String) = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>")

        /** Anki tags can't contain spaces. */
        private fun tag(title: String) = title.trim().replace(Regex("\\s+"), "_")

        /** Anki's duplicate check: the first 8 hex digits of the SHA-1 of the sort field. */
        internal fun checksum(text: String): Long {
            val sha1 = MessageDigest.getInstance("SHA-1").digest(text.toByteArray())
            return sha1.take(4).fold(0L) { acc, b -> (acc shl 8) or (b.toLong() and 0xff) }
        }
    }
}
