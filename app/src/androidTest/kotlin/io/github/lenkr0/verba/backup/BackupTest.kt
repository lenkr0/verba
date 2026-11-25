package io.github.lenkr0.verba.backup

import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.lenkr0.verba.data.Capture
import io.github.lenkr0.verba.data.VerbaDatabase
import io.github.lenkr0.verba.data.VocabRepository
import io.github.lenkr0.verba.review.Fsrs
import io.github.lenkr0.verba.review.Rating
import io.github.lenkr0.verba.review.ReviewLimits
import io.github.lenkr0.verba.review.ReviewRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.ZoneOffset
import java.util.zip.ZipInputStream

@RunWith(AndroidJUnit4::class)
class BackupTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    /** Two devices with their own database and clock. */
    private inner class Device {
        var clock = 1_767_254_400_000L
        val db = VerbaDatabase.inMemory(context)
        val vocabulary = VocabRepository(db, now = { clock })
        val review = ReviewRepository(db, Fsrs(fuzz = null), now = { clock }, zone = { ZoneOffset.UTC })
        val backups = BackupRepository(db, vocabulary, "test", now = { clock })

        suspend fun save(lemma: String, sentence: String, word: String, translation: String, title: String? = null): String {
            clock += 1_000
            return vocabulary.save(
                Capture(
                    language = "en", targetLanguage = "de", wordForm = word, lemma = lemma, translation = translation,
                    definition = "", sentence = sentence, wordStart = sentence.indexOf(word),
                    wordEnd = sentence.indexOf(word) + word.length, sourceTitle = title, sourceApp = null, sourceAppLabel = null,
                ),
            ).entryId
        }

        suspend fun backup(): ByteArray = ByteArrayOutputStream().also { backups.export(it) }.toByteArray()

        suspend fun restore(bytes: ByteArray) = backups.restore(ByteArrayInputStream(bytes))

        suspend fun words() = vocabulary.entryList().first()
    }

    private lateinit var a: Device
    private lateinit var b: Device

    @Before
    fun setUp() {
        a = Device()
        b = Device()
    }

    @After
    fun tearDown() {
        a.db.close()
        b.db.close()
    }

    @Test
    fun backupRestoresEverythingOnAnEmptyDevice() = runTest {
        val id = a.save("run", "She was running late.", "running", "laufen", "The Hobbit")
        a.save("glare", "The old wizard glared.", "glared", "starren")
        a.review.rate(a.review.next(ReviewLimits(20, 200))!!, Rating.Good, 1_000)

        val json = JSONObject(a.backup().decodeToString())
        assertEquals("verba-backup", json.getString("format"))
        assertEquals(1, json.getInt("version"))
        assertEquals(2, json.getJSONArray("entries").length())
        assertEquals("running", json.getJSONArray("contexts").getJSONObject(0).getString("word_form"))

        val result = b.restore(a.backup())
        assertEquals(RestoreResult(added = 2, updated = 0), result)
        val words = b.words()
        assertEquals(setOf("run", "glare"), words.map { it.entry.lemma }.toSet())
        assertEquals("The Hobbit", words.single { it.entry.id == id }.latestSource)
        assertEquals(1, b.db.reviewDao().logs(id).size)
        assertEquals(a.db.reviewDao().card(id), b.db.reviewDao().card(id))
    }

    @Test
    fun restoringTwiceChangesNothing() = runTest {
        a.save("run", "She was running late.", "running", "laufen")
        val bytes = a.backup()
        assertEquals(RestoreResult(0, 0), a.restore(bytes))
        assertEquals(1, a.words().size)
    }

    @Test
    fun theNewerChangeWins() = runTest {
        val id = a.save("run", "She was running late.", "running", "laufen")
        val old = a.backup()
        a.clock += 60_000
        a.vocabulary.update(id, "run", "laufen; rennen", "")

        // An older backup doesn't undo the newer translation …
        assertEquals(RestoreResult(0, 0), a.restore(old))
        assertEquals("laufen; rennen", a.words().single().entry.translation)

        // … but a newer one from another device replaces an older local version
        b.restore(old)
        assertEquals(RestoreResult(added = 0, updated = 1), b.restore(a.backup()))
        assertEquals("laufen; rennen", b.words().single().entry.translation)
    }

    @Test
    fun theSameWordFromTwoDevicesIsMerged() = runTest {
        a.save("run", "She was running late.", "running", "laufen", "The Hobbit")
        b.clock += 5_000
        b.save("run", "He ran home.", "ran", "rennen", "the hobbit")

        val result = a.restore(b.backup())
        assertEquals(0, result.added)
        val run = a.words().single()
        assertEquals(2, run.contextCount)
        assertEquals("laufen; rennen", run.entry.translation)
        // Books with the same title (any case) become one
        assertEquals(1, a.vocabulary.sourceCounts().first().size)
    }

    @Test
    fun otherFilesAreRejected() = runTest {
        for ((bytes, reason) in listOf(
            "hello".toByteArray() to BackupException.Reason.NotABackup,
            """{"format":"something-else","version":1,"exported_at":0,"app_version":"x"}""".toByteArray() to BackupException.Reason.NotABackup,
            """{"format":"verba-backup","version":99,"exported_at":0,"app_version":"x"}""".toByteArray() to BackupException.Reason.TooNew,
        )) {
            try {
                a.restore(bytes)
                fail("restored ${bytes.decodeToString()}")
            } catch (e: BackupException) {
                assertEquals(reason, e.reason)
            }
        }
    }

    @Test
    fun csvHasOneRowPerWord() = runTest {
        a.save("run", "She said: \"run, now\".", "run", "laufen")
        a.save("学习", "我每天学习中文。", "学习", "lernen")
        val out = ByteArrayOutputStream()
        assertEquals(2, CsvExport(a.vocabulary, zone = { ZoneOffset.UTC }).export(out))
        val text = out.toByteArray().decodeToString()
        assertTrue(text.startsWith("﻿word,translation,definition,language,target_language,sentence,source,status,added\r\n"))
        assertTrue(text, "run,laufen,,en,de,\"She said: \"\"run, now\"\".\",,new,2026-01-01\r\n" in text)
        assertTrue(text, "学习,lernen,,en,de,我每天学习中文。,,new,2026-01-01\r\n" in text)
    }

    @Test
    fun ankiPackageHasOneClozeNotePerWord() = runTest {
        a.save("run", "She was running late.", "running", "laufen", "The Hobbit")
        a.save("glare", "The old wizard glared.", "glared", "starren")
        val out = ByteArrayOutputStream()
        val work = File(context.cacheDir, "anki-test").apply { deleteRecursively() }
        assertEquals(2, AnkiExport(a.vocabulary, work, now = { a.clock }).export(out))

        val files = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(out.toByteArray())).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                files[entry.name] = zip.readBytes()
            }
        }
        assertEquals(setOf("collection.anki2", "media"), files.keys)
        assertEquals("{}", files.getValue("media").decodeToString())

        val collection = File(work, "check.anki2").apply { writeBytes(files.getValue("collection.anki2")) }
        SQLiteDatabase.openDatabase(collection.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("SELECT models, decks FROM col", null).use { c ->
                c.moveToFirst()
                val model = JSONObject(c.getString(0)).getJSONObject(AnkiExport.MODEL_ID.toString())
                assertEquals(1, model.getInt("type")) // cloze
                assertEquals(AnkiExport.FIELDS, (0..<5).map { model.getJSONArray("flds").getJSONObject(it).getString("name") })
                assertEquals("Verba", JSONObject(c.getString(1)).getJSONObject(AnkiExport.DECK_ID.toString()).getString("name"))
            }
            val notes = db.rawQuery("SELECT guid, flds, tags FROM notes ORDER BY id", null).use { c ->
                generateSequence { if (c.moveToNext()) Triple(c.getString(0), c.getString(1), c.getString(2)) else null }.toList()
            }
            assertEquals(2, notes.size)
            val (guid, fields, tags) = notes[0]
            assertTrue(guid.startsWith("verba:"))
            assertEquals(listOf("She was {{c1::running}} late.", "run", "laufen", "", "The Hobbit"), fields.split('\u001f'))
            assertEquals(" verba en The_Hobbit ", tags)
            db.rawQuery("SELECT count(*), min(type), max(queue) FROM cards WHERE did = ?", arrayOf(AnkiExport.DECK_ID.toString())).use { c ->
                c.moveToFirst()
                assertEquals(2, c.getInt(0))
                assertEquals(0, c.getInt(1)) // new
                assertEquals(0, c.getInt(2))
            }
        }
    }
}
