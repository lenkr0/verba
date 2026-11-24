package io.github.lenkr0.verba.review

import androidx.room.withTransaction
import io.github.lenkr0.verba.data.ContextWithSource
import io.github.lenkr0.verba.data.EntryEntity
import io.github.lenkr0.verba.data.ReviewCardEntity
import io.github.lenkr0.verba.data.ReviewLogEntity
import io.github.lenkr0.verba.data.ReviewStats
import io.github.lenkr0.verba.data.VerbaDatabase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

data class ReviewLimits(val newPerDay: Int, val reviewsPerDay: Int)

/** Cards left today, within the daily limits. */
data class ReviewCounts(
    val learning: Int,
    val review: Int,
    val new: Int,
    /** New words beyond today's limit. */
    val newWaiting: Int,
    /** When the next card is due, if any; may be today or later. */
    val nextDue: Long?,
) {
    val total get() = learning + review + new
}

/** A card to show: the word and the sentence with the gap. */
data class ReviewItem(
    val entry: EntryEntity,
    /** Null for a word that was never reviewed. */
    val card: ReviewCardEntity?,
    val context: ContextWithSource?,
    /** Null if the word has no usable sentence; the card then asks for the word by its meaning. */
    val cloze: Cloze?,
)

/** What it takes to undo the last rating. */
data class ReviewUndo(val item: ReviewItem, val previous: ReviewCardEntity?, val logId: String)

/** A learning day starts at 4:00 local time, so late-night reading still counts for the evening. */
data class ReviewDay(val start: Long, val end: Long) {
    companion object {
        const val START_HOUR = 4

        fun of(time: Long, zone: ZoneId): ReviewDay {
            val local = Instant.ofEpochMilli(time).atZone(zone)
            val date = if (local.hour < START_HOUR) local.toLocalDate().minusDays(1) else local.toLocalDate()
            val startTime = LocalTime.of(START_HOUR, 0)
            return ReviewDay(
                start = date.atTime(startTime).atZone(zone).toInstant().toEpochMilli(),
                end = date.plusDays(1).atTime(startTime).atZone(zone).toInstant().toEpochMilli(),
            )
        }
    }
}

/**
 * Picks the next card and records ratings.
 *
 * Order: learning cards that are due, then review cards due today, then new
 * words (oldest first) – the last two within the daily limits. When nothing
 * else is left, learning cards due in the next [LEARN_AHEAD_MS] come early,
 * so a session doesn't end just to wait a few minutes.
 */
class ReviewRepository(
    private val db: VerbaDatabase,
    private val fsrs: Fsrs = Fsrs(),
    private val now: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val dao = db.reviewDao()
    private val vocab = db.vocabDao()

    /** Updated when the data changes and every minute, as cards become due. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun counts(limits: Flow<ReviewLimits>): Flow<ReviewCounts> =
        flow {
            while (true) {
                emit(now())
                delay(TICK_MS)
            }
        }.flatMapLatest { time -> stats(time) }.combine(limits, ::countsOf)

    suspend fun countsNow(limits: ReviewLimits): ReviewCounts = countsOf(stats(now()).first(), limits)

    fun card(entryId: String): Flow<ReviewCardEntity?> = dao.cardFlow(entryId)

    suspend fun next(limits: ReviewLimits): ReviewItem? {
        val time = now()
        val day = ReviewDay.of(time, zone())
        val stats = stats(time).first()
        dao.nextLearning(time)?.let { return itemFor(it) }
        if (stats.reviewsDone < limits.reviewsPerDay) dao.nextReview(day.end)?.let { return itemFor(it) }
        if (stats.newDone < limits.newPerDay) dao.nextNew()?.let { return itemFor(it, null) }
        dao.nextLearning(time + LEARN_AHEAD_MS)?.let { return itemFor(it) }
        return null
    }

    /** [item] with the word as it is now; null if it was deleted meanwhile. */
    suspend fun reload(item: ReviewItem): ReviewItem? {
        val entry = vocab.entryNow(item.entry.id)?.takeIf { it.deletedAt == null } ?: return null
        return itemFor(entry, dao.card(entry.id))
    }

    /** How long each rating would wait until the card comes back. */
    fun intervals(item: ReviewItem): Map<Rating, Long> {
        val time = now()
        return fsrs.preview(item.card?.toFsrs() ?: fsrs.newCard(time), time)
    }

    suspend fun rate(item: ReviewItem, rating: Rating, durationMs: Long?): ReviewUndo = db.withTransaction {
        val time = now()
        val entryId = item.entry.id
        val previous = dao.card(entryId)
        val after = fsrs.review(previous?.toFsrs() ?: fsrs.newCard(time), rating, time)
        val forgotten = previous?.state == CardState.Review.value && rating == Rating.Again
        dao.upsert(
            ReviewCardEntity(
                entryId = entryId,
                state = after.state.value,
                step = after.step,
                stability = after.stability!!,
                difficulty = after.difficulty!!,
                due = after.due,
                lastReview = time,
                reps = (previous?.reps ?: 0) + 1,
                lapses = (previous?.lapses ?: 0) + if (forgotten) 1 else 0,
                createdAt = previous?.createdAt ?: time,
                updatedAt = time,
            ),
        )
        val log = ReviewLogEntity(
            id = newId(),
            entryId = entryId,
            contextId = item.context?.context?.id,
            rating = rating.value,
            state = previous?.state ?: STATE_NEW,
            reviewedAt = time,
            durationMs = durationMs,
            createdAt = time,
            updatedAt = time,
        )
        dao.insert(log)
        ReviewUndo(item, previous, log.id)
    }

    /** Puts the card back as it was before the rating; the review no longer counts. */
    suspend fun undo(undo: ReviewUndo) = db.withTransaction {
        val time = now()
        if (undo.previous == null) {
            dao.markCardDeleted(undo.item.entry.id, time)
        } else {
            dao.upsert(undo.previous.copy(updatedAt = time))
        }
        dao.markLogDeleted(undo.logId, time)
    }

    /** Starts over: the word counts as new again. Its review history stays. */
    suspend fun reset(entryId: String) = dao.markCardDeleted(entryId, now())

    private fun stats(time: Long): Flow<ReviewStats> {
        val day = ReviewDay.of(time, zone())
        return dao.stats(learnUntil = time + LEARN_AHEAD_MS, dayStart = day.start, dayEnd = day.end)
    }

    private fun countsOf(stats: ReviewStats, limits: ReviewLimits): ReviewCounts {
        val newLeft = (limits.newPerDay - stats.newDone).coerceAtLeast(0)
        val new = minOf(stats.newAvailable, newLeft)
        return ReviewCounts(
            learning = stats.learningDue,
            review = minOf(stats.reviewDue, (limits.reviewsPerDay - stats.reviewsDone).coerceAtLeast(0)),
            new = new,
            newWaiting = stats.newAvailable - new,
            nextDue = stats.nextDue,
        )
    }

    private suspend fun itemFor(card: ReviewCardEntity): ReviewItem? =
        vocab.entryNow(card.entryId)?.let { itemFor(it, card) }

    /** Shows the word's sentences in turn: the one after the sentence shown last time. */
    private suspend fun itemFor(entry: EntryEntity, card: ReviewCardEntity?): ReviewItem {
        val usable = dao.contexts(entry.id).mapNotNull { context ->
            Cloze.of(context.context.sentence, context.context.wordForm, context.context.wordStart, context.context.wordEnd)
                ?.let { context to it }
        }
        if (usable.isEmpty()) return ReviewItem(entry, card, context = null, cloze = null)
        val last = dao.lastContextId(entry.id)
        val (context, cloze) = usable[(usable.indexOfFirst { it.first.context.id == last } + 1) % usable.size]
        return ReviewItem(entry, card, context, cloze)
    }

    private fun ReviewCardEntity.toFsrs() =
        FsrsCard(CardState.of(state), step, stability, difficulty, due, lastReview)

    companion object {
        const val LEARN_AHEAD_MS = 20 * 60_000L
        private const val TICK_MS = 60_000L
        private const val STATE_NEW = 0
    }
}
