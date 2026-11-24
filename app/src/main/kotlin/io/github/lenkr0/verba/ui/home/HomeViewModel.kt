package io.github.lenkr0.verba.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lenkr0.verba.data.Deletions
import io.github.lenkr0.verba.data.EntryListItem
import io.github.lenkr0.verba.data.LanguageCount
import io.github.lenkr0.verba.data.LearningStatus
import io.github.lenkr0.verba.data.ListFilter
import io.github.lenkr0.verba.data.PendingDeletion
import io.github.lenkr0.verba.data.SourceCount
import io.github.lenkr0.verba.data.VocabRepository
import io.github.lenkr0.verba.review.ReviewCounts
import io.github.lenkr0.verba.review.ReviewLimits
import io.github.lenkr0.verba.review.ReviewRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HomeViewModel(
    private val vocabulary: VocabRepository,
    private val deletions: Deletions,
    review: ReviewRepository,
    reviewLimits: Flow<ReviewLimits>,
) : ViewModel() {
    private val filterState = MutableStateFlow(ListFilter())
    val filter: StateFlow<ListFilter> = filterState.asStateFlow()

    /** Null while loading. Typing waits a moment before searching; chips apply at once. */
    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    val entries: StateFlow<List<EntryListItem>?> = filterState
        .debounce { if (it.query.isBlank()) 0L else SEARCH_DELAY_MS }
        .flatMapLatest { vocabulary.entryList(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val sources: StateFlow<List<SourceCount>> =
        vocabulary.sourceCounts().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val languages: StateFlow<List<LanguageCount>> =
        vocabulary.languageCounts().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val pendingDeletion: StateFlow<PendingDeletion?> = deletions.pending

    val reviewCounts: StateFlow<ReviewCounts?> =
        review.counts(reviewLimits).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setQuery(query: String) = filterState.update { it.copy(query = query) }

    fun toggleLanguage(language: String) =
        filterState.update { it.copy(language = if (it.language == language) null else language) }

    fun toggleSource(sourceId: String) =
        filterState.update { it.copy(sourceId = if (it.sourceId == sourceId) null else sourceId) }

    fun toggleStatus(status: LearningStatus) =
        filterState.update { it.copy(status = if (it.status == status) null else status) }

    fun undo(deletion: PendingDeletion) {
        deletions.clear()
        viewModelScope.launch { vocabulary.restore(deletion.entryId, deletion.token) }
    }

    fun dismissUndo() = deletions.clear()

    private companion object {
        const val SEARCH_DELAY_MS = 200L
    }
}
