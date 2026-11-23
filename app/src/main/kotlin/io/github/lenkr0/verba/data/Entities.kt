package io.github.lenkr0.verba.data

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// The data model is described in docs/data-format.md. Rules that keep it ready
// for syncing between devices: random UUIDs as ids, UTC epoch milliseconds,
// updated_at on every change, deletions as tombstones (deleted_at), and no
// database-level foreign keys, so records can arrive in any order.

/** A word to learn: one per base form and language, collecting all its sentences. */
@Entity(
    tableName = "entries",
    indices = [Index(value = ["language", "lemma_key"]), Index(value = ["updated_at"])],
)
data class EntryEntity(
    @PrimaryKey val id: String,
    /** BCP 47 tag of the word's language, e.g. "en", "zh". */
    val language: String,
    /** BCP 47 tag of the language translation and definition are written in. */
    @ColumnInfo(name = "target_language") val targetLanguage: String,
    val lemma: String,
    /** Normalized [lemma] (NFKC, lower case) that identifies duplicates. */
    @ColumnInfo(name = "lemma_key") val lemmaKey: String,
    val translation: String,
    val definition: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
)

/** One encounter of an entry: the sentence it appeared in, where and when. */
@Entity(
    tableName = "contexts",
    indices = [Index(value = ["entry_id"]), Index(value = ["source_id"]), Index(value = ["source_app"])],
)
data class ContextEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "entry_id") val entryId: String,
    /** The sentence, or "" if only the word was selected. */
    val sentence: String,
    /** The word exactly as it appeared, e.g. "running". */
    @ColumnInfo(name = "word_form") val wordForm: String,
    /** UTF-16 offsets of the word in [sentence]; -1 when unknown. */
    @ColumnInfo(name = "word_start") val wordStart: Int,
    @ColumnInfo(name = "word_end") val wordEnd: Int,
    @ColumnInfo(name = "source_id") val sourceId: String?,
    /** Package of the app the text came from, if Android told us. */
    @ColumnInfo(name = "source_app") val sourceApp: String?,
    @ColumnInfo(name = "source_app_label") val sourceAppLabel: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
)

/** A book, novel, manga or website the user reads. */
@Entity(tableName = "sources", indices = [Index(value = ["title_key"])])
data class SourceEntity(
    @PrimaryKey val id: String,
    val title: String,
    @ColumnInfo(name = "title_key") val titleKey: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
)

/** An entry for the word list, with its most recent sentence and source. */
data class EntryListItem(
    @Embedded val entry: EntryEntity,
    @ColumnInfo(name = "latest_sentence") val latestSentence: String?,
    @ColumnInfo(name = "latest_word_start") val latestWordStart: Int?,
    @ColumnInfo(name = "latest_word_end") val latestWordEnd: Int?,
    @ColumnInfo(name = "latest_source") val latestSource: String?,
    @ColumnInfo(name = "context_count") val contextCount: Int,
)
