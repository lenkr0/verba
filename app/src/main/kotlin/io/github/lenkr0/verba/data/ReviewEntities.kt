package io.github.lenkr0.verba.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Learning progress of an entry (FSRS). One card per entry, keyed by the
 * entry's id; an entry without a live card is new (never reviewed, or reset).
 */
@Entity(tableName = "review_cards", indices = [Index(value = ["due"])])
data class ReviewCardEntity(
    @PrimaryKey @ColumnInfo(name = "entry_id") val entryId: String,
    /** 1 learning, 2 review, 3 relearning. */
    val state: Int,
    /** Current (re)learning step, from 0; null in review. */
    val step: Int?,
    /** Days until the chance of remembering falls to 90 %. */
    val stability: Double,
    /** 1 (easy) to 10 (hard). */
    val difficulty: Double,
    val due: Long,
    @ColumnInfo(name = "last_review") val lastReview: Long,
    /** Number of reviews, and how often a learned word was forgotten. */
    val reps: Int,
    val lapses: Int,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
)

/** One review, kept forever: the history a scheduler can be re-run or tuned on. */
@Entity(
    tableName = "review_logs",
    indices = [Index(value = ["entry_id"]), Index(value = ["reviewed_at"])],
)
data class ReviewLogEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "entry_id") val entryId: String,
    /** The sentence the card showed; null if it showed none. */
    @ColumnInfo(name = "context_id") val contextId: String?,
    /** 1 again, 2 hard, 3 good, 4 easy. */
    val rating: Int,
    /** Card state before this review: 0 new, 1 learning, 2 review, 3 relearning. */
    val state: Int,
    @ColumnInfo(name = "reviewed_at") val reviewedAt: Long,
    /** Time from showing the card to rating it. */
    @ColumnInfo(name = "duration_ms") val durationMs: Long?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
)
