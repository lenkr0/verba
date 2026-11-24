package io.github.lenkr0.verba.review

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.lenkr0.verba.data.Capture
import io.github.lenkr0.verba.data.LearningStatus
import io.github.lenkr0.verba.data.ListFilter
import io.github.lenkr0.verba.data.VerbaDatabase
import io.github.lenkr0.verba.data.VocabRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class ReviewRepositoryTest {
    private lateinit var db: VerbaDatabase
    private lateinit var vocabulary: VocabRepository
    private lateinit var review: ReviewRepository

    /** 2026-01-01 08:00 UTC; the review day runs from 04:00 to 04:00 UTC. */
    private var clock = 1_767_254_400_000L
    private val limits = ReviewLimits(newPerDay = 20, reviewsPerDay = 200)

    @Before
    fun setUp() {
        db = VerbaDatabase.inMemory(ApplicationProvider.getApplicationContext())
        vocabulary = VocabRepository(db, now = { clock })
        review = ReviewRepository(db, Fsrs(fuzz = null), now = { clock }, zone = { ZoneOffset.UTC })
    }

    @After
    fun tearDown() = db.close()

    private suspend fun save(lemma: String, sentence: String = "", word: String = lemma, translation: String = "x"): String {
        clock += 1_000
        return vocabulary.save(
            Capture(
                language = "en", targetLanguage = "de", wordForm = word, lemma = lemma, translation = translation,
                definition = "", sentence = sentence, wordStart = sentence.indexOf(word),
                wordEnd = sentence.indexOf(word).let { if (it < 0) -1 else it + word.length },
                sourceTitle = null, sourceApp = null, sourceAppLabel = null,
            ),
        ).entryId
    }

    private suspend fun statusOf(id: String, status: LearningStatus) =
        vocabulary.entryList(ListFilter(status = status)).first().any { it.entry.id == id }

    @Test
    fun newWordGoesThroughTheLearningSteps() = runTest {
        val id = save("linger", "The smell lingered in the hall.", "lingered")
        val first = review.next(limits)!!
        assertEquals(id, first.entry.id)
        assertNull(first.card)
        assertEquals(Cloze("The smell ", "lingered", " in the hall."), first.cloze)
        assertEquals(10 * Fsrs.MINUTE, review.intervals(first)[Rating.Good])

        review.rate(first, Rating.Good, durationMs = 4_000)
        val card = db.reviewDao().card(id)!!
        assertEquals(CardState.Learning.value, card.state)
        assertEquals(1, card.step)
        assertEquals(clock + 10 * Fsrs.MINUTE, card.due)
        assertEquals(LearningStatus.Learning, LearningStatus.of(card))
        assertEquals(true, statusOf(id, LearningStatus.Learning))

        val log = db.reviewDao().logs(id).single()
        assertEquals(0, log.state)
        assertEquals(Rating.Good.value, log.rating)
        assertEquals(first.context!!.context.id, log.contextId)
        assertEquals(4_000L, log.durationMs)

        // Not due for 10 minutes, but nothing else is left: it comes early
        assertEquals(id, review.next(limits)?.entry?.id)
        clock += 30 * Fsrs.MINUTE
        review.rate(review.next(limits)!!, Rating.Good, null)
        assertEquals(CardState.Review.value, db.reviewDao().card(id)!!.state)
        assertNull(review.next(limits))
    }

    @Test
    fun dailyLimitForNewWords() = runTest {
        val first = save("one")
        val second = save("two")
        val limitOne = limits.copy(newPerDay = 1)
        assertEquals(ReviewCounts(learning = 0, review = 0, new = 1, newWaiting = 1, nextDue = null), review.countsNow(limitOne))

        review.rate(review.next(limitOne)!!, Rating.Easy, null)
        // The first word is learned (due in days); the second waits for tomorrow
        assertNull(review.next(limitOne))
        assertEquals(0, review.countsNow(limitOne).new)
        assertEquals(1, review.countsNow(limitOne).newWaiting)

        clock += Fsrs.DAY
        val next = review.next(limitOne)!!
        assertEquals(second, next.entry.id)
        assertNotEquals(first, second)
    }

    @Test
    fun dailyLimitForReviews() = runTest {
        val id = save("glare")
        review.rate(review.next(limits)!!, Rating.Easy, null)
        val due = db.reviewDao().card(id)!!.due
        clock = due + Fsrs.MINUTE

        assertEquals(1, review.countsNow(limits).review)
        assertNull(review.next(limits.copy(reviewsPerDay = 0)))
        val item = review.next(limits)!!
        assertEquals(id, item.entry.id)
        review.rate(item, Rating.Again, null)
        val card = db.reviewDao().card(id)!!
        assertEquals(CardState.Relearning.value, card.state)
        assertEquals(1, card.lapses)
        assertEquals(2, card.reps)
    }

    @Test
    fun undoPutsTheCardBack() = runTest {
        val id = save("linger")
        val item = review.next(limits)!!
        val undo = review.rate(item, Rating.Good, null)
        assertEquals(0, review.countsNow(limits).new)

        review.undo(undo)
        assertNull(db.reviewDao().card(id))
        assertEquals(emptyList<Any>(), db.reviewDao().logs(id))
        assertEquals(1, review.countsNow(limits).new)
        assertEquals(true, statusOf(id, LearningStatus.New))

        // A review card goes back to its earlier state
        review.rate(review.next(limits)!!, Rating.Easy, null)
        val before = db.reviewDao().card(id)!!
        clock = before.due
        val again = review.rate(review.next(limits)!!, Rating.Again, null)
        review.undo(again)
        assertEquals(before.copy(updatedAt = clock), db.reviewDao().card(id))
        assertEquals(1, db.reviewDao().logs(id).size)
    }

    @Test
    fun sentencesTakeTurns() = runTest {
        val id = save("run", "She was running late.", "running")
        save("run", "He ran home.", "ran")
        val first = review.next(limits)!!
        assertEquals("She was running late.", first.context?.context?.sentence)
        review.rate(first, Rating.Again, null)
        clock += Fsrs.MINUTE
        val second = review.next(limits)!!
        assertEquals(id, second.entry.id)
        assertEquals("He ran home.", second.context?.context?.sentence)
    }

    @Test
    fun wordWithoutSentenceIsAskedByMeaning() = runTest {
        save("linger", translation = "verweilen")
        val item = review.next(limits)!!
        assertNull(item.cloze)
        assertNull(item.context)
    }

    @Test
    fun deletedWordsLeaveTheQueueAndComeBackWithTheirProgress() = runTest {
        val id = save("linger")
        review.rate(review.next(limits)!!, Rating.Good, null)
        val token = vocabulary.delete(id)
        assertNull(review.next(limits))
        assertNull(review.countsNow(limits).nextDue)

        vocabulary.restore(id, token)
        assertNotNull(review.next(limits)?.card)
    }

    @Test
    fun mergingKeepsProgressAndHistory() = runTest {
        val learned = save("ran")
        val target = save("run")
        review.rate(review.next(limits)!!, Rating.Easy, null) // "ran", the older word
        assertNotNull(db.reviewDao().card(learned))

        vocabulary.merge(learned, target)
        assertNull(db.reviewDao().card(learned))
        assertEquals(CardState.Review.value, db.reviewDao().card(target)?.state)
        assertEquals(1, db.reviewDao().logs(target).size)
    }

    @Test
    fun resetMakesTheWordNewAgain() = runTest {
        val id = save("linger")
        review.rate(review.next(limits)!!, Rating.Easy, null)
        assertEquals(true, statusOf(id, LearningStatus.Learning))

        review.reset(id)
        assertNull(db.reviewDao().card(id))
        assertEquals(true, statusOf(id, LearningStatus.New))
        assertEquals(1, db.reviewDao().logs(id).size)
        assertNull(review.next(limits)?.card)
    }

    @Test
    fun knownAfterThreeWeeksOfStability() = runTest {
        val id = save("linger")
        var item = review.next(limits)!!
        review.rate(item, Rating.Easy, null)
        while (LearningStatus.of(db.reviewDao().card(id)) != LearningStatus.Known) {
            clock = db.reviewDao().card(id)!!.due
            item = review.next(limits)!!
            review.rate(item, Rating.Easy, null)
        }
        assertEquals(true, statusOf(id, LearningStatus.Known))
        assertEquals(false, statusOf(id, LearningStatus.Learning))
    }
}
