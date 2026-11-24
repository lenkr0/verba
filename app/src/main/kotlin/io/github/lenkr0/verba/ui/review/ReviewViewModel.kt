package io.github.lenkr0.verba.ui.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lenkr0.verba.review.Rating
import io.github.lenkr0.verba.review.ReviewCounts
import io.github.lenkr0.verba.review.ReviewItem
import io.github.lenkr0.verba.review.ReviewLimits
import io.github.lenkr0.verba.review.ReviewRepository
import io.github.lenkr0.verba.review.ReviewUndo
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReviewUiState(
    val loading: Boolean = true,
    /** Null when there is nothing (left) to review. */
    val item: ReviewItem? = null,
    val revealed: Boolean = false,
    val hintShown: Boolean = false,
    /** What each rating schedules, for the button labels. */
    val intervals: Map<Rating, Long> = emptyMap(),
    /** Cards rated in this session. */
    val reviewed: Int = 0,
    val canUndo: Boolean = false,
)

class ReviewViewModel(
    private val repository: ReviewRepository,
    private val limits: Flow<ReviewLimits>,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val state = MutableStateFlow(ReviewUiState())
    val uiState: StateFlow<ReviewUiState> = state.asStateFlow()

    val counts: StateFlow<ReviewCounts?> =
        repository.counts(limits).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private var undo: ReviewUndo? = null
    private var shownAt = 0L

    /** One change at a time, so a double tap can't rate two cards. */
    private var work: Job? = null

    init {
        loadNext()
    }

    fun loadNext() = launchWork { show(repository.next(limits.first())) }

    fun reveal() = state.update { current ->
        current.copy(revealed = true, intervals = current.item?.let(repository::intervals).orEmpty())
    }

    fun showHint() = state.update { it.copy(hintShown = true) }

    fun rate(rating: Rating) {
        val item = state.value.item ?: return
        if (!state.value.revealed) return
        launchWork {
            undo = repository.rate(item, rating, durationMs = clock() - shownAt)
            state.update { it.copy(reviewed = it.reviewed + 1, canUndo = true) }
            show(repository.next(limits.first()))
        }
    }

    /** Brings back the last rated card, unrated. */
    fun undo() {
        val last = undo ?: return
        launchWork {
            repository.undo(last)
            undo = null
            state.update { it.copy(reviewed = (it.reviewed - 1).coerceAtLeast(0), canUndo = false) }
            show(last.item)
        }
    }

    /** Picks up changes made meanwhile on the word's page (or its deletion). */
    fun refresh() {
        val item = state.value.item ?: return
        launchWork {
            val fresh = repository.reload(item)
            if (fresh == null) {
                show(repository.next(limits.first()))
            } else {
                state.update { it.copy(item = fresh) }
            }
        }
    }

    private fun show(item: ReviewItem?) {
        shownAt = clock()
        state.update {
            it.copy(loading = false, item = item, revealed = false, hintShown = false, intervals = emptyMap())
        }
    }

    private fun launchWork(block: suspend () -> Unit) {
        if (work?.isActive == true) return
        work = viewModelScope.launch { block() }
    }
}
