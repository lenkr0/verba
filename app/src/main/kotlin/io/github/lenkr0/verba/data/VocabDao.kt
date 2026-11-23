package io.github.lenkr0.verba.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface VocabDao {
    @Query(
        """
        SELECT e.*,
            c.sentence AS latest_sentence,
            c.word_start AS latest_word_start,
            c.word_end AS latest_word_end,
            s.title AS latest_source,
            (SELECT COUNT(*) FROM contexts cc WHERE cc.entry_id = e.id AND cc.deleted_at IS NULL) AS context_count
        FROM entries e
        LEFT JOIN contexts c ON c.id = (
            SELECT c2.id FROM contexts c2
            WHERE c2.entry_id = e.id AND c2.deleted_at IS NULL
            ORDER BY (c2.sentence = '') ASC, c2.created_at DESC
            LIMIT 1
        )
        LEFT JOIN sources s ON s.id = c.source_id AND s.deleted_at IS NULL
        WHERE e.deleted_at IS NULL
        ORDER BY e.updated_at DESC
        """,
    )
    fun entryList(): Flow<List<EntryListItem>>

    @Query("SELECT * FROM entries WHERE language = :language AND lemma_key = :lemmaKey AND deleted_at IS NULL LIMIT 1")
    suspend fun findEntry(language: String, lemmaKey: String): EntryEntity?

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
