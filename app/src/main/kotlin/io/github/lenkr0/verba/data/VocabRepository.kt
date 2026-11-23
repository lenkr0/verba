package io.github.lenkr0.verba.data

import androidx.room.withTransaction
import io.github.lenkr0.verba.language.LemmaKey
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

/** What is already saved under a base form, shown in the sheet before saving. */
data class ExistingEntry(val translation: String, val definition: String, val contextCount: Int)

class VocabRepository(
    private val db: VerbaDatabase,
    private val now: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val dao = db.vocabDao()

    fun entryList(): Flow<List<EntryListItem>> = dao.entryList()

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
