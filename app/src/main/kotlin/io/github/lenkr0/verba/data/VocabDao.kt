package io.github.lenkr0.verba.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** A sentence of an entry with the title of its book, for the detail screen. */
data class ContextWithSource(
    @Embedded val context: ContextEntity,
    @ColumnInfo(name = "source_title") val sourceTitle: String?,
)

/** A book/manga and how many of your words come from it, for the filters. */
data class SourceCount(
    val id: String,
    val title: String,
    @ColumnInfo(name = "entry_count") val entryCount: Int,
)

data class LanguageCount(
    val language: String,
    @ColumnInfo(name = "entry_count") val entryCount: Int,
)

@Dao
interface VocabDao {
    /**
     * The word list, newest first. All filters are optional: [pattern] is a
     * LIKE pattern searched in base form, translation, definition, the words
     * as they appeared, sentences and book titles; [status] is a
     * [LearningStatus] code.
     */
    @Query(
        """
        SELECT e.*,
            c.sentence AS latest_sentence,
            c.word_start AS latest_word_start,
            c.word_end AS latest_word_end,
            s.title AS latest_source,
            (SELECT COUNT(*) FROM contexts cc WHERE cc.entry_id = e.id AND cc.deleted_at IS NULL) AS context_count,
            CASE WHEN r.entry_id IS NULL THEN 0 WHEN r.state = 2 AND r.stability >= :knownDays THEN 2 ELSE 1 END
                AS learning_status
        FROM entries e
        LEFT JOIN review_cards r ON r.entry_id = e.id AND r.deleted_at IS NULL
        LEFT JOIN contexts c ON c.id = (
            SELECT c2.id FROM contexts c2
            WHERE c2.entry_id = e.id AND c2.deleted_at IS NULL
            ORDER BY (c2.sentence = '') ASC, c2.created_at DESC
            LIMIT 1
        )
        LEFT JOIN sources s ON s.id = c.source_id AND s.deleted_at IS NULL
        WHERE e.deleted_at IS NULL
            AND (:language IS NULL OR e.language = :language)
            AND (:status IS NULL OR :status =
                CASE WHEN r.entry_id IS NULL THEN 0 WHEN r.state = 2 AND r.stability >= :knownDays THEN 2 ELSE 1 END)
            AND (:sourceId IS NULL OR EXISTS (
                SELECT 1 FROM contexts x WHERE x.entry_id = e.id AND x.deleted_at IS NULL AND x.source_id = :sourceId))
            AND (:pattern IS NULL
                OR e.lemma LIKE :pattern ESCAPE '\' OR e.translation LIKE :pattern ESCAPE '\'
                OR e.definition LIKE :pattern ESCAPE '\'
                OR EXISTS (
                    SELECT 1 FROM contexts y LEFT JOIN sources ys ON ys.id = y.source_id
                    WHERE y.entry_id = e.id AND y.deleted_at IS NULL
                        AND (y.sentence LIKE :pattern ESCAPE '\' OR y.word_form LIKE :pattern ESCAPE '\'
                            OR ys.title LIKE :pattern ESCAPE '\')))
        ORDER BY e.updated_at DESC
        """,
    )
    fun entryList(
        pattern: String?,
        language: String?,
        sourceId: String?,
        status: Int?,
        knownDays: Double = LearningStatus.KNOWN_STABILITY_DAYS,
    ): Flow<List<EntryListItem>>

    @Query(
        """
        SELECT s.id, s.title, COUNT(DISTINCT e.id) AS entry_count
        FROM sources s
        JOIN contexts c ON c.source_id = s.id AND c.deleted_at IS NULL
        JOIN entries e ON e.id = c.entry_id AND e.deleted_at IS NULL
        WHERE s.deleted_at IS NULL
        GROUP BY s.id
        ORDER BY MAX(c.created_at) DESC
        """,
    )
    fun sourceCounts(): Flow<List<SourceCount>>

    @Query(
        """
        SELECT language, COUNT(*) AS entry_count FROM entries
        WHERE deleted_at IS NULL GROUP BY language ORDER BY entry_count DESC
        """,
    )
    fun languageCounts(): Flow<List<LanguageCount>>

    @Query("SELECT * FROM entries WHERE id = :id")
    fun entry(id: String): Flow<EntryEntity?>

    @Query("SELECT * FROM entries WHERE id = :id")
    suspend fun entryNow(id: String): EntryEntity?

    @Query(
        """
        SELECT c.*, s.title AS source_title FROM contexts c
        LEFT JOIN sources s ON s.id = c.source_id AND s.deleted_at IS NULL
        WHERE c.entry_id = :entryId AND c.deleted_at IS NULL
        ORDER BY c.created_at DESC
        """,
    )
    fun contexts(entryId: String): Flow<List<ContextWithSource>>

    @Query("SELECT * FROM entries WHERE language = :language AND lemma_key = :lemmaKey AND deleted_at IS NULL LIMIT 1")
    suspend fun findEntry(language: String, lemmaKey: String): EntryEntity?

    @Query(
        """
        SELECT * FROM entries
        WHERE language = :language AND lemma_key IN (:keys) AND id != :excludeId AND deleted_at IS NULL
        """,
    )
    suspend fun findByKeys(language: String, keys: List<String>, excludeId: String): List<EntryEntity>

    /** Entries of one language to merge into, matching [pattern] (LIKE) in base form or translation. */
    @Query(
        """
        SELECT * FROM entries
        WHERE language = :language AND id != :excludeId AND deleted_at IS NULL
            AND (lemma LIKE :pattern ESCAPE '\' OR translation LIKE :pattern ESCAPE '\')
        ORDER BY lemma COLLATE NOCASE LIMIT :limit
        """,
    )
    suspend fun mergeCandidates(language: String, excludeId: String, pattern: String, limit: Int): List<EntryEntity>

    @Query("SELECT COUNT(*) FROM contexts WHERE entry_id = :entryId AND deleted_at IS NULL")
    suspend fun contextCount(entryId: String): Int

    @Query("SELECT * FROM sources WHERE title_key = :titleKey AND deleted_at IS NULL LIMIT 1")
    suspend fun findSource(titleKey: String): SourceEntity?

    /** Titles, most recently used first. */
    @Query(
        """
        SELECT s.title FROM sources s
        WHERE s.deleted_at IS NULL
        ORDER BY (SELECT MAX(c.created_at) FROM contexts c WHERE c.source_id = s.id AND c.deleted_at IS NULL) DESC,
            s.updated_at DESC
        LIMIT :limit
        """,
    )
    suspend fun recentTitles(limit: Int): List<String>

    /** The title last used with text from [app], so reading in the same app keeps its book. */
    @Query(
        """
        SELECT s.title FROM contexts c JOIN sources s ON s.id = c.source_id
        WHERE c.source_app = :app AND c.deleted_at IS NULL AND s.deleted_at IS NULL
        ORDER BY c.created_at DESC LIMIT 1
        """,
    )
    suspend fun lastTitleForApp(app: String): String?

    // --- Deleting is marking (tombstones), so it can be undone and synced ---

    @Query("UPDATE entries SET deleted_at = :time, updated_at = :time WHERE id = :id AND deleted_at IS NULL")
    suspend fun markEntryDeleted(id: String, time: Long)

    @Query("UPDATE contexts SET deleted_at = :time, updated_at = :time WHERE entry_id = :entryId AND deleted_at IS NULL")
    suspend fun markContextsDeleted(entryId: String, time: Long)

    @Query("UPDATE contexts SET deleted_at = :time, updated_at = :time WHERE id = :id AND deleted_at IS NULL")
    suspend fun markContextDeleted(id: String, time: Long)

    /** Undo: only what was deleted at [deletedAt] comes back, not older deletions. */
    @Query("UPDATE entries SET deleted_at = NULL, updated_at = :time WHERE id = :id AND deleted_at = :deletedAt")
    suspend fun restoreEntry(id: String, deletedAt: Long, time: Long)

    @Query("UPDATE contexts SET deleted_at = NULL, updated_at = :time WHERE entry_id = :entryId AND deleted_at = :deletedAt")
    suspend fun restoreContexts(entryId: String, deletedAt: Long, time: Long)

    @Query("UPDATE contexts SET entry_id = :intoId, updated_at = :time WHERE entry_id = :fromId AND deleted_at IS NULL")
    suspend fun moveContexts(fromId: String, intoId: String, time: Long)

    @Insert
    suspend fun insert(entry: EntryEntity)

    @Update
    suspend fun update(entry: EntryEntity)

    @Insert
    suspend fun insert(context: ContextEntity)

    @Insert
    suspend fun insert(source: SourceEntity)

    @Update
    suspend fun update(source: SourceEntity)
}
