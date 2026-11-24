package io.github.lenkr0.verba.dictionary

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class DictionaryTest {
    private lateinit var root: File
    private lateinit var store: DictionaryStore
    private lateinit var lookup: DictionaryLookup

    @Before
    fun setUp() {
        root = File(ApplicationProvider.getApplicationContext<android.content.Context>().cacheDir, "dictionary-test")
        root.deleteRecursively()
        root.mkdirs()
        store = DictionaryStore(File(root, "packs"))
        lookup = DictionaryLookup(store)
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    private suspend fun install(source: String, target: String, words: List<TestPacks.Word>): PackInfo =
        store.install(TestPacks.create(File(root, "$source-$target.tmp"), source, target, words))

    @Test
    fun formsPhrasesAndBaseFormsAreFound() = runTest {
        install("en", "de", TestPacks.englishGerman)

        val running = lookup.lookup("en", "de", "running", "running late")!!
        assertEquals("run", running.entries.first().lemma)
        assertEquals(listOf("laufen", "rennen"), running.entries.first().senses.first().translations)
        assertFalse(running.fallback)

        // the phrase beats the single word
        val gave = lookup.lookup("en", "de", "gave", "gave up smoking.")!!
        assertEquals("gave up", gave.matched)
        assertEquals("give up", gave.entries.first().lemma)

        assertEquals("hobbit", lookup.lookup("en", "de", "Hobbits", "Hobbits.")!!.entries.first().lemma)
        assertNull(lookup.lookup("en", "de", "wizard", "wizard"))
        assertTrue(lookup.canLookUp("en", "de"))
        assertFalse(lookup.canLookUp("fr", "de"))
    }

    @Test
    fun japaneseUsesTheLongestMatchAndFallsBackToEnglish() = runTest {
        install("ja", "en", TestPacks.japaneseEnglish)
        // ICU splits 食べ|た; the match continues into the next piece and is deinflected
        val result = lookup.lookup("ja", "de", "食べ", "食べた。")!!
        assertEquals("食べる", result.entries.first().lemma)
        assertEquals("たべる", result.entries.first().reading)
        assertTrue("no ja→de pack, so English", result.fallback)
        assertTrue(lookup.canLookUp("ja", "de"))
    }

    @Test
    fun installReplacesAndDeleteRemoves() = runTest {
        install("en", "de", TestPacks.englishGerman)
        install("en", "de", listOf(TestPacks.Word("run", listOf("rennen"))))
        assertEquals(listOf("en-de"), store.installed.value.map { it.id })
        assertEquals(listOf("rennen"), lookup.lookup("en", "de", "run", "run")!!.entries.first().senses.first().translations)
        store.delete("en-de")
        assertTrue(store.installed.value.isEmpty())
        assertNull(lookup.lookup("en", "de", "run", "run"))
    }

    @Test
    fun filesThatAreNotPacksAreRejected() = runTest {
        val garbage = File(root, "garbage.tmp").apply { writeText("not a database") }
        val newer = TestPacks.create(File(root, "newer.tmp"), "en", "de", TestPacks.englishGerman, formatVersion = 99)
        val foreign = TestPacks.create(File(root, "foreign.tmp"), "en", "de", TestPacks.englishGerman, format = "something-else")
        for (file in listOf(garbage, newer, foreign)) {
            try {
                store.install(file)
                fail("${file.name} was installed")
            } catch (_: InvalidPackException) {
            }
        }
        assertTrue(store.installedNow().isEmpty())
    }

    @Test
    fun downloadChecksTheChecksum() = runTest {
        val pack = TestPacks.create(File(root, "src.sqlite"), "en", "de", TestPacks.englishGerman)
        val gz = TestPacks.gzip(pack, File(root, "en-de.sqlite.gz"))
        val downloader = DictionaryDownloader(store, "test")
        fun catalogPack(sha: String) = CatalogPack(
            id = "en-de", sourceLanguage = "en", targetLanguage = "de", title = "", version = "1", formatVersion = 1,
            headwords = 4, url = gz.toURI().toString(), size = gz.length(), installedSize = pack.length(), sha256 = sha,
        )

        try {
            downloader.download(catalogPack("0".repeat(64))) { _, _ -> }
            fail("a wrong checksum was accepted")
        } catch (_: ChecksumException) {
        }
        assertTrue(store.installedNow().isEmpty())

        var lastProgress = 0L
        val info = downloader.download(catalogPack(TestPacks.sha256(gz))) { read, _ -> lastProgress = read }
        assertEquals("en-de", info.id)
        assertEquals(listOf("en-de"), store.installed.value.map { it.id })
        assertTrue(lastProgress >= 0)
        assertTrue("no temporary files left", File(root, "packs").listFiles()!!.none { it.name.endsWith(".part") })
    }

    @Test
    fun importTakesPlainAndCompressedFiles() = runTest {
        val downloader = DictionaryDownloader(store, "test")
        val plain = TestPacks.create(File(root, "a.sqlite"), "ja", "en", TestPacks.japaneseEnglish)
        downloader.import(plain.inputStream())
        val compressed = TestPacks.gzip(
            TestPacks.create(File(root, "b.sqlite"), "en", "de", TestPacks.englishGerman),
            File(root, "b.sqlite.gz"),
        )
        downloader.import(compressed.inputStream())
        assertEquals(listOf("en-de", "ja-en"), store.installed.value.map { it.id })
    }

    @Test
    fun catalogIsReadAndFilteredByFormat() = runTest {
        val catalogFile = File(root, "catalog.json").apply {
            writeText(
                """{"format":"verba-dictionary-catalog","version":1,"packs":[
                  {"id":"en-de","sourceLanguage":"en","targetLanguage":"de","title":"English → German","version":"1",
                   "formatVersion":1,"headwords":10,"url":"u","size":1,"installedSize":2,"sha256":"x","sources":[],"future":true},
                  {"id":"en-fr","sourceLanguage":"en","targetLanguage":"fr","title":"","version":"1",
                   "formatVersion":2,"headwords":10,"url":"u","size":1,"installedSize":2,"sha256":"x"}]}""",
            )
        }
        val catalog = DictionaryDownloader(store, "test", catalogUrl = catalogFile.toURI().toString()).fetchCatalog()
        assertEquals("packs of a newer format are hidden", listOf("en-de"), catalog.packs.map { it.id })
    }
}
