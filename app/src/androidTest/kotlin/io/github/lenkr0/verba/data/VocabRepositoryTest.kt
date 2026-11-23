package io.github.lenkr0.verba.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VocabRepositoryTest {
    private lateinit var db: VerbaDatabase
    private lateinit var repository: VocabRepository
    private var clock = 1_000L

    @Before
    fun setUp() {
        db = VerbaDatabase.inMemory(ApplicationProvider.getApplicationContext())
        repository = VocabRepository(db, now = { clock++ })
    }

    @After
    fun tearDown() = db.close()

    private fun capture(
        lemma: String,
        sentence: String = "",
        word: String = lemma,
        language: String = "en",
        title: String? = null,
        app: String? = null,
        translation: String = "",
    ) = Capture(
        language = language, targetLanguage = "de", wordForm = word, lemma = lemma, translation = translation,
        definition = "", sentence = sentence, wordStart = sentence.indexOf(word),
        wordEnd = sentence.indexOf(word).let { if (it < 0) -1 else it + word.length },
        sourceTitle = title, sourceApp = app, sourceAppLabel = null,
    )

    @Test
    fun sameBaseFormCollectsSentences() = runTest {
        val first = repository.save(capture("run", "She was running late.", "running", translation = "laufen"))
        val second = repository.save(capture("Run", "He ran home.", "ran", translation = "laufen, rennen"))

        assertFalse(first.addedToExisting)
        assertTrue(second.addedToExisting)
        assertEquals(first.entryId, second.entryId)

        val list = repository.entryList().first()
        assertEquals(1, list.size)
        assertEquals(2, list[0].contextCount)
        assertEquals("laufen, rennen", list[0].entry.translation)
        assertEquals("He ran home.", list[0].latestSentence)
        assertEquals("ran", list[0].latestSentence!!.substring(list[0].latestWordStart!!, list[0].latestWordEnd!!))
    }

    @Test
    fun languagesKeepTheirOwnEntries() = runTest {
        repository.save(capture("gift", language = "en"))
        repository.save(capture("gift", language = "de"))
        assertEquals(2, repository.entryList().first().size)
    }

    @Test
    fun sourcesAreMatchedIgnoringCase() = runTest {
        repository.save(capture("run", title = "The Hobbit", app = "org.koreader.launcher"))
        repository.save(capture("walk", title = "the hobbit "))
        repository.save(capture("学习", language = "zh", title = "三体", app = "com.android.chrome"))

        assertEquals(listOf("三体", "The Hobbit"), repository.recentTitles())
        assertEquals("The Hobbit", repository.lastTitleForApp("org.koreader.launcher"))
        assertEquals("三体", repository.lastTitleForApp("com.android.chrome"))
        assertNull(repository.lastTitleForApp("app.mihon"))
    }

    @Test
    fun existingShowsWhatIsSaved() = runTest {
        assertNull(repository.existing("en", "run"))
        repository.save(capture("run", "She was running late.", "running", translation = "laufen"))
        val existing = repository.existing("en", "RUN ")!!
        assertEquals("laufen", existing.translation)
        assertEquals(1, existing.contextCount)
    }

    @Test
    fun aSentenceWithoutTheWordStoresNoOffsets() = runTest {
        repository.save(capture("run", sentence = "Something else entirely.", word = "running"))
        val item = repository.entryList().first().single()
        assertEquals(-1, item.latestWordStart)
    }
}
