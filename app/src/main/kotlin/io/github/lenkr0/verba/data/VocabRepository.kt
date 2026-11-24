package io.github.lenkr0.verba.data

import androidx.room.withTransaction
import io.github.lenkr0.verba.language.LemmaKey
import io.github.lenkr0.verba.language.Lemmatizer
import kotlinx.coroutines.flow.Flow
import java.util.Locale
import java.util.UUID

/** Everything the bottom sheet collected about one word. */
data class Capture(
    val language: String,
    val targetLanguage: String,
    val wordForm: String,
    val lemma: String,
    val translation: String,
    val definition: String,
    /** The sentence, or "" when only the word was selected. */
    val sentence: String,
    val wordStart: Int,
    val wordEnd: Int,
    val sourceTitle: String?,
    val sourceApp: String?,
    val sourceAppLabel: String?,
)

data class SaveResult(val entryId: String, val addedToExisting: Boolean)

/** What the word list shows; every part is optional. */
data class ListFilter(val query: String = "", val language: String? = null, val sourceId: String? = null)

sealed interface UpdateResult {
    data object Saved : UpdateResult

    /** The new base form is already another entry; nothing was changed. */
    data class Duplicate(val other: EntryEntity) : UpdateResult
}

/** What is already saved under a base form, shown in the sheet before saving. */
data class ExistingEntry(val translation: String, val definition: String, val contextCount: Int)

class VocabRepository(
    private val db: VerbaDatabase,
    private val now: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val dao = db.vocabDao()

    fun entryList(filter: ListFilter = ListFilter()): Flow<List<EntryListItem>> =
        dao.entryList(likePattern(filter.query), filter.language, filter.sourceId)

    fun sourceCounts(): Flow<List<SourceCount>> = dao.sourceCounts()

    fun languageCounts(): Flow<List<LanguageCount>> = dao.languageCounts()

    fun entry(id: String): Flow<EntryEntity?> = dao.entry(id)

    fun contexts(entryId: String): Flow<List<ContextWithSource>> = dao.contexts(entryId)

    /** Changes base form, translation and definition, unless the base form belongs to another entry. */
    suspend fun update(id: String, lemma: String, translation: String, definition: String): UpdateResult =
        db.withTransaction {
            val entry = dao.entryNow(id) ?: return@withTransaction UpdateResult.Saved
            val newLemma = lemma.trim().ifEmpty { entry.lemma }
            val key = LemmaKey.of(newLemma)
            if (key != entry.lemmaKey) {
                dao.findEntry(entry.language, key)?.takeIf { it.id != id }?.let {
                    return@withTransaction UpdateResult.Duplicate(it)
                }
            }
            dao.update(
                entry.copy(
                    lemma = newLemma,
                    lemmaKey = key,
                    translation = translation.trim(),
                    definition = definition.trim(),
                    updatedAt = now(),
                ),
            )
            UpdateResult.Saved
        }

    suspend fun deleteContext(contextId: String) = dao.markContextDeleted(contextId, now())

    /** Marks an entry and its sentences deleted; returns the token for [restore]. */
    suspend fun delete(id: String): Long = db.withTransaction {
        val time = now()
        dao.markContextsDeleted(id, time)
        dao.markEntryDeleted(id, time)
        time
    }

    /**
     * Undoes [delete]. If the same base form was saved again in the meantime,
     * the restored entry is merged into that one, so there are no duplicates.
     */
    suspend fun restore(id: String, deletedAt: Long) = db.withTransaction {
        val time = now()
        dao.restoreEntry(id, deletedAt, time)
        dao.restoreContexts(id, deletedAt, time)
        val entry = dao.entryNow(id) ?: return@withTransaction
        dao.findByKeys(entry.language, listOf(entry.lemmaKey), id).firstOrNull()?.let { mergeNow(id, it.id, time) }
    }

    /**
     * Moves all sentences of [fromId] to [intoId], adds its translations that
     * [intoId] doesn't have, and marks [fromId] deleted.
     */
    suspend fun merge(fromId: String, intoId: String) = db.withTransaction { mergeNow(fromId, intoId, now()) }

    private suspend fun mergeNow(fromId: String, intoId: String, time: Long) {
        if (fromId == intoId) return
        val from = dao.entryNow(fromId) ?: return
        val into = dao.entryNow(intoId) ?: return
        dao.moveContexts(fromId, intoId, time)
        dao.update(
            into.copy(
                translation = combineTranslations(into.translation, from.translation),
                definition = into.definition.ifBlank { from.definition },
                updatedAt = time,
            ),
        )
        dao.markEntryDeleted(fromId, time)
    }

    /**
     * Other entries that are probably the same word: their base form is this
     * entry's base form by the language's rules ("running" → "run") or one of
     * [baseForms] (e.g. from a dictionary).
     */
    suspend fun likelyDuplicates(entry: EntryEntity, baseForms: List<String> = emptyList()): List<EntryEntity> {
        val keys = (listOf(Lemmatizer.forLanguage(entry.language).lemma(entry.lemma)) + baseForms)
            .map(LemmaKey::of)
            .filter { it.isNotEmpty() && it != entry.lemmaKey }
            .distinct()
        return if (keys.isEmpty()) emptyList() else dao.findByKeys(entry.language, keys, entry.id)
    }

    /** Entries of the same language for the "merge with" picker. */
    suspend fun mergeCandidates(entry: EntryEntity, query: String, limit: Int = 30): List<EntryEntity> =
        dao.mergeCandidates(entry.language, entry.id, likePattern(query) ?: "%", limit)

    suspend fun existing(language: String, lemma: String): ExistingEntry? {
        if (lemma.isBlank()) return null
        val entry = dao.findEntry(language, LemmaKey.of(lemma)) ?: return null
        return ExistingEntry(entry.translation, entry.definition, dao.contextCount(entry.id))
    }

    suspend fun recentTitles(limit: Int = 20): List<String> = dao.recentTitles(limit)

    suspend fun lastTitleForApp(app: String?): String? =
        if (app == null) null else dao.lastTitleForApp(app)

    /**
     * Saves a capture. A base form that is already saved in this language gets
     * the new sentence added and its translation and definition replaced by
     * what the sheet showed (which started from the saved values).
     */
    suspend fun save(capture: Capture): SaveResult = db.withTransaction {
        val time = now()
        val lemma = capture.lemma.trim().ifEmpty { capture.wordForm.trim() }
        val lemmaKey = LemmaKey.of(lemma)
        val existing = dao.findEntry(capture.language, lemmaKey)

        val entryId = if (existing != null) {
            dao.update(
                existing.copy(
                    lemma = lemma,
                    translation = capture.translation.trim(),
                    definition = capture.definition.trim(),
                    targetLanguage = capture.targetLanguage,
                    updatedAt = time,
                ),
            )
            existing.id
        } else {
            newId().also { id ->
                dao.insert(
                    EntryEntity(
                        id = id,
                        language = capture.language,
                        targetLanguage = capture.targetLanguage,
                        lemma = lemma,
                        lemmaKey = lemmaKey,
                        translation = capture.translation.trim(),
                        definition = capture.definition.trim(),
                        createdAt = time,
                        updatedAt = time,
                    ),
                )
            }
        }

        val sourceId = capture.sourceTitle?.trim()?.takeIf { it.isNotEmpty() }?.let { title -> sourceId(title, time) }
        val hasOffsets = capture.sentence.isNotEmpty() && capture.wordStart >= 0 &&
            capture.wordEnd <= capture.sentence.length && capture.wordStart < capture.wordEnd
        dao.insert(
            ContextEntity(
                id = newId(),
                entryId = entryId,
                sentence = capture.sentence.trim(),
                wordForm = capture.wordForm.trim(),
                wordStart = if (hasOffsets) capture.wordStart else -1,
                wordEnd = if (hasOffsets) capture.wordEnd else -1,
                sourceId = sourceId,
                sourceApp = capture.sourceApp,
                sourceAppLabel = capture.sourceAppLabel,
                createdAt = time,
                updatedAt = time,
            ),
        )
        SaveResult(entryId, addedToExisting = existing != null)
    }

    private suspend fun sourceId(title: String, time: Long): String {
        val key = title.lowercase(Locale.ROOT)
        dao.findSource(key)?.let { return it.id }
        return newId().also { id ->
            dao.insert(SourceEntity(id = id, title = title, titleKey = key, createdAt = time, updatedAt = time))
        }
    }
}

/** "a; b" + "b; c" → "a; b; c": translations of both, each once, in order. */
internal fun combineTranslations(first: String, second: String): String =
    (first.split(";") + second.split(";")).map { it.trim() }.filter { it.isNotEmpty() }
        .distinctBy { it.lowercase() }.joinToString("; ")

/** A LIKE pattern that finds [query] anywhere, with LIKE's wildcards escaped; null for no query. */
internal fun likePattern(query: String): String? =
    query.trim().takeIf { it.isNotEmpty() }?.let {
        "%" + it.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
    }
