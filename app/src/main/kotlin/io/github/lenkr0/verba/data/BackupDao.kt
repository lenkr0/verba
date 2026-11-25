package io.github.lenkr0.verba.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

/** Whole tables, deleted rows included: backups carry tombstones like a sync would. */
@Dao
interface BackupDao {
    @Query("SELECT * FROM entries ORDER BY created_at")
    suspend fun entries(): List<EntryEntity>

    @Query("SELECT * FROM contexts ORDER BY created_at")
    suspend fun contexts(): List<ContextEntity>

    @Query("SELECT * FROM sources ORDER BY created_at")
    suspend fun sources(): List<SourceEntity>

    @Query("SELECT * FROM review_cards ORDER BY created_at")
    suspend fun reviewCards(): List<ReviewCardEntity>

    @Query("SELECT * FROM review_logs ORDER BY reviewed_at")
    suspend fun reviewLogs(): List<ReviewLogEntity>

    @Upsert
    suspend fun upsertEntries(rows: List<EntryEntity>)

    @Upsert
    suspend fun upsertContexts(rows: List<ContextEntity>)

    @Upsert
    suspend fun upsertSources(rows: List<SourceEntity>)

    @Upsert
    suspend fun upsertReviewCards(rows: List<ReviewCardEntity>)

    @Upsert
    suspend fun upsertReviewLogs(rows: List<ReviewLogEntity>)

    @Query("UPDATE contexts SET source_id = :intoId, updated_at = :time WHERE source_id = :fromId")
    suspend fun moveSource(fromId: String, intoId: String, time: Long)

    @Query("UPDATE sources SET deleted_at = :time, updated_at = :time WHERE id = :id AND deleted_at IS NULL")
    suspend fun markSourceDeleted(id: String, time: Long)
}
