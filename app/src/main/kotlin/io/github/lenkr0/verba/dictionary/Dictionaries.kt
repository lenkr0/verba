package io.github.lenkr0.verba.dictionary

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.InputStream

sealed interface CatalogState {
    data object NotLoaded : CatalogState
    data object Loading : CatalogState
    data class Loaded(val catalog: Catalog) : CatalogState
    data object Failed : CatalogState
}

/** A problem to show once, e.g. in a snackbar. */
enum class DictionaryError { Download, Checksum, InvalidFile }

/**
 * Everything the Dictionaries screen needs: installed packs, the catalog and
 * running downloads. Downloads run in the app's scope, so they continue when
 * you leave the screen (but not when Android ends the app – there is no
 * background service).
 */
class Dictionaries(
    val store: DictionaryStore,
    private val downloader: DictionaryDownloader,
    private val scope: CoroutineScope,
) {
    private val catalogState = MutableStateFlow<CatalogState>(CatalogState.NotLoaded)
    val catalog: StateFlow<CatalogState> = catalogState.asStateFlow()

    /** Pack id → fraction downloaded (0..1). */
    private val progressState = MutableStateFlow<Map<String, Float>>(emptyMap())
    val progress: StateFlow<Map<String, Float>> = progressState.asStateFlow()

    private val errorState = MutableStateFlow<DictionaryError?>(null)
    val error: StateFlow<DictionaryError?> = errorState.asStateFlow()

    private val jobs = mutableMapOf<String, Job>()

    fun loadCatalog(force: Boolean = false) {
        if (!force && catalogState.value is CatalogState.Loaded) return
        catalogState.value = CatalogState.Loading
        scope.launch {
            catalogState.value = runCatching { downloader.fetchCatalog() }
                .fold({ CatalogState.Loaded(it) }, { CatalogState.Failed })
        }
    }

    fun download(pack: CatalogPack) {
        if (jobs[pack.id]?.isActive == true) return
        progressState.update { it + (pack.id to 0f) }
        jobs[pack.id] = scope.launch {
            runCatching {
                downloader.download(pack) { read, total ->
                    progressState.update { it + (pack.id to (read.toFloat() / total.coerceAtLeast(1)).coerceIn(0f, 1f)) }
                }
            }.onFailure { e ->
                if (e !is kotlinx.coroutines.CancellationException) {
                    errorState.value = if (e is ChecksumException) DictionaryError.Checksum else DictionaryError.Download
                }
            }
            progressState.update { it - pack.id }
            jobs.remove(pack.id)
        }
    }

    fun cancel(id: String) {
        jobs.remove(id)?.cancel()
        progressState.update { it - id }
    }

    fun delete(id: String) {
        scope.launch { store.delete(id) }
    }

    fun import(open: () -> InputStream?) {
        scope.launch {
            runCatching { open()?.let { downloader.import(it) } }
                .onFailure { errorState.value = DictionaryError.InvalidFile }
        }
    }

    fun clearError() {
        errorState.value = null
    }
}
