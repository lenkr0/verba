package io.github.lenkr0.verba.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** What is left to review today, for the home screen and the review screen. */
data class ReviewStats(
    /** Learning cards due by the learn-ahead time. */
    @ColumnInfo(name = "learning_due") val learningDue: Int,
    /** Review cards due today. */
    @ColumnInfo(name = "review_due") val reviewDue: Int,
    /** Words never reviewed. */
    @ColumnInfo(name = "new_available") val newAvailable: Int,
    @ColumnInfo(name = "new_done") val newDone: Int,
    @ColumnInfo(name = "reviews_done") val reviewsDone: Int,
    /** When the next card is due, if any. */
    @ColumnInfo(name = "next_due") val nextDue: Long?,
)

// Cards and logs of deleted entries stay, so undoing a deletion keeps the
// progress; the queries here skip them by joining live entries.
@Dao
interface ReviewDao {
    @Query("SELECT * FROM review_cards WHERE entry_id = :entryId AND deleted_at IS NULL")
    suspend fun card(entryId: String): ReviewCardEntity?

    /** Including a deleted card, which an upsert replaces. */
    @Query("SELECT * FROM review_cards WHERE entry_id = :entryId")
    suspend fun anyCard(entryId: String): ReviewCardEntity?

    @Query("SELECT * FROM review_cards WHERE entry_id = :entryId AND deleted_at IS NULL")
    fun cardFlow(entryId: String): Flow<ReviewCardEntity?>

    @Query(
        """
        SELECT r.* FROM review_cards r JOIN entries e ON e.id = r.entry_id AND e.deleted_at IS NULL
        WHERE r.deleted_at IS NULL AND r.state IN (1, 3) AND r.due <= :until
        ORDER BY r.due LIMIT 1
        """,
    )
    suspend fun nextLearning(until: Long): ReviewCardEntity?

    @Query(
        """
        SELECT r.* FROM review_cards r JOIN entries e ON e.id = r.entry_id AND e.deleted_at IS NULL
        WHERE r.deleted_at IS NULL AND r.state = 2 AND r.due < :until
        ORDER BY r.due LIMIT 1
        """,
    )
    suspend fun nextReview(until: Long): ReviewCardEntity?

    /** The oldest word not reviewed yet. */
    @Query(
        """
        SELECT e.* FROM entries e
        WHERE e.deleted_at IS NULL
            AND NOT EXISTS (SELECT 1 FROM review_cards r WHERE r.entry_id = e.id AND r.deleted_at IS NULL)
        ORDER BY e.created_at LIMIT 1
        """,
    )
    suspend fun nextNew(): EntryEntity?

    @Query(
        """
        SELECT
            (SELECT COUNT(*) FROM review_cards r JOIN entries e ON e.id = r.entry_id AND e.deleted_at IS NULL
                WHERE r.deleted_at IS NULL AND r.state IN (1, 3) AND r.due <= :learnUntil) AS learning_due,
            (SELECT COUNT(*) FROM review_cards r JOIN entries e ON e.id = r.entry_id AND e.deleted_at IS NULL
                WHERE r.deleted_at IS NULL AND r.state = 2 AND r.due < :dayEnd) AS review_due,
            (SELECT COUNT(*) FROM entries e WHERE e.deleted_at IS NULL
                AND NOT EXISTS (SELECT 1 FROM review_cards r WHERE r.entry_id = e.id AND r.deleted_at IS NULL)
            ) AS new_available,
            (SELECT COUNT(*) FROM review_logs l
                WHERE l.deleted_at IS NULL AND l.reviewed_at >= :dayStart AND l.state = 0) AS new_done,
            (SELECT COUNT(*) FROM review_logs l
                WHERE l.deleted_at IS NULL AND l.reviewed_at >= :dayStart AND l.state = 2) AS reviews_done,
            (SELECT MIN(r.due) FROM review_cards r JOIN entries e ON e.id = r.entry_id AND e.deleted_at IS NULL
                WHERE r.deleted_at IS NULL) AS next_due
        """,
    )
    fun stats(learnUntil: Long, dayStart: Long, dayEnd: Long): Flow<ReviewStats>

    /** The sentence shown at the last review, to show a different one next time. */
    @Query(
        """
        SELECT context_id FROM review_logs WHERE entry_id = :entryId AND deleted_at IS NULL
        ORDER BY reviewed_at DESC LIMIT 1
        """,
    )
    suspend fun lastContextId(entryId: String): String?

    /** Sentences of an entry, oldest first. */
    @Query(
        """
        SELECT c.*, s.title AS source_title FROM contexts c
        LEFT JOIN sources s ON s.id = c.source_id AND s.deleted_at IS NULL
        WHERE c.entry_id = :entryId AND c.deleted_at IS NULL
        ORDER BY c.created_at
        """,
    )
    suspend fun contexts(entryId: String): List<ContextWithSource>

    @Query("SELECT * FROM review_logs WHERE entry_id = :entryId AND deleted_at IS NULL ORDER BY reviewed_at")
    suspend fun logs(entryId: String): List<ReviewLogEntity>

    @Upsert
    suspend fun upsert(card: ReviewCardEntity)

    @Insert
    suspend fun insert(log: ReviewLogEntity)

    @Query("UPDATE review_cards SET deleted_at = :time, updated_at = :time WHERE entry_id = :entryId AND deleted_at IS NULL")
    suspend fun markCardDeleted(entryId: String, time: Long)

    @Query("UPDATE review_logs SET deleted_at = :time, updated_at = :time WHERE id = :id AND deleted_at IS NULL")
    suspend fun markLogDeleted(id: String, time: Long)

    @Query("UPDATE review_logs SET entry_id = :intoId, updated_at = :time WHERE entry_id = :fromId")
    suspend fun moveLogs(fromId: String, intoId: String, time: Long)
}
