package io.github.lenkr0.verba.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A deleted word whose deletion can still be undone. */
data class PendingDeletion(val entryId: String, val lemma: String, val token: Long)

/**
 * Hands a deletion from the detail screen to the word list, which offers
 * "Undo" after the detail screen has closed.
 */
class Deletions {
    private val state = MutableStateFlow<PendingDeletion?>(null)
    val pending: StateFlow<PendingDeletion?> = state.asStateFlow()

    fun offer(deletion: PendingDeletion) {
        state.value = deletion
    }

    fun clear() {
        state.value = null
    }
}
