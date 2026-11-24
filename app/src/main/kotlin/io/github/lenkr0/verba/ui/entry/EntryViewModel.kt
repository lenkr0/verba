package io.github.lenkr0.verba.ui.entry

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lenkr0.verba.data.ContextWithSource
import io.github.lenkr0.verba.data.Deletions
import io.github.lenkr0.verba.data.EntryEntity
import io.github.lenkr0.verba.data.PendingDeletion
import io.github.lenkr0.verba.data.ReviewCardEntity
import io.github.lenkr0.verba.data.UpdateResult
import io.github.lenkr0.verba.data.VocabRepository
import io.github.lenkr0.verba.dictionary.DictionaryLookup
import io.github.lenkr0.verba.review.ReviewRepository
import io.github.lenkr0.verba.settings.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One word: its fields (editable), its sentences, and what can be merged with it. */
class EntryViewModel(
    private val id: String,
    private val vocabulary: VocabRepository,
    private val dictionary: DictionaryLookup,
    private val settings: SettingsRepository,
    private val deletions: Deletions,
    private val review: ReviewRepository,
) : ViewModel() {
    val entry: StateFlow<EntryEntity?> =
        vocabulary.entry(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val contexts: StateFlow<List<ContextWithSource>> =
        vocabulary.contexts(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Learning progress; null while the word is new. */
    val card: StateFlow<ReviewCardEntity?> =
        review.card(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    var lemma by mutableStateOf("")
        private set
    var translation by mutableStateOf("")
        private set
    var definition by mutableStateOf("")
        private set

    /** Entries that are probably the same word, offered for merging. */
    var duplicates by mutableStateOf<List<EntryEntity>>(emptyList())
        private set

    /** Set when an edited base form already belongs to another entry. */
    var conflict by mutableStateOf<EntryEntity?>(null)
        private set

    init {
        viewModelScope.launch {
            val loaded = vocabulary.entry(id).filterNotNull().first()
            lemma = loaded.lemma
            translation = loaded.translation
            definition = loaded.definition
            findDuplicates(loaded)
        }
    }

    fun hasChanges(saved: EntryEntity): Boolean =
        lemma.trim() != saved.lemma || translation.trim() != saved.translation || definition.trim() != saved.definition

    fun onLemmaChange(value: String) { lemma = value }

    fun onTranslationChange(value: String) { translation = value }

    fun onDefinitionChange(value: String) { definition = value }

    fun save() {
        viewModelScope.launch {
            when (val result = vocabulary.update(id, lemma, translation, definition)) {
                UpdateResult.Saved -> vocabulary.entry(id).first()?.let { findDuplicates(it) }
                is UpdateResult.Duplicate -> conflict = result.other
            }
        }
    }

    fun dismissConflict() {
        conflict = null
    }

    /** Deletes the word and hands the undo to the word list. */
    fun delete(onDeleted: () -> Unit) {
        val current = entry.value ?: return
        viewModelScope.launch {
            val token = vocabulary.delete(id)
            deletions.offer(PendingDeletion(id, current.lemma, token))
            onDeleted()
        }
    }

    fun resetProgress() {
        viewModelScope.launch { review.reset(id) }
    }

    fun deleteContext(contextId: String) {
        viewModelScope.launch { vocabulary.deleteContext(contextId) }
    }

    /** Moves this word into [intoId]; [onMerged] gets the id to show next. */
    fun mergeInto(intoId: String, onMerged: (String) -> Unit) {
        // Keep what's typed here: it's part of the word being merged
        viewModelScope.launch {
            vocabulary.update(id, entry.value?.lemma ?: lemma, translation, definition)
            vocabulary.merge(id, intoId)
            conflict = null
            onMerged(intoId)
        }
    }

    suspend fun mergeCandidates(query: String): List<EntryEntity> {
        val current = entry.value ?: return emptyList()
        return vocabulary.mergeCandidates(current, query)
    }

    private suspend fun findDuplicates(current: EntryEntity) {
        val target = settings.settings.value.targetLanguage
        val baseForms = dictionary.lookup(current.language, target, current.lemma, current.lemma)
            ?.entries?.map { it.lemma }.orEmpty()
        duplicates = vocabulary.likelyDuplicates(current, baseForms)
    }
}
