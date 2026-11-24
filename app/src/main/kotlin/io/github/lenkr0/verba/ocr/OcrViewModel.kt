package io.github.lenkr0.verba.ocr

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed interface OcrState {
    data object LoadingImage : OcrState

    data object ImageFailed : OcrState

    /** The models have to be downloaded first. */
    data class NeedsModels(val image: Bitmap) : OcrState

    data class Recognizing(val image: Bitmap) : OcrState

    data class Done(val image: Bitmap, val blocks: List<TextBlock>) : OcrState

    data class Failed(val image: Bitmap) : OcrState
}

/** Loads the image, makes sure the models are there, then recognizes its text once. */
class OcrViewModel(
    private val models: OcrModels,
    private val loadImage: suspend () -> Bitmap?,
    /** Where the engine is closed after the screen is gone. */
    private val appScope: CoroutineScope,
) : ViewModel() {
    private val engine = OcrEngine(models)
    private val state = MutableStateFlow<OcrState>(OcrState.LoadingImage)
    val uiState: StateFlow<OcrState> = state.asStateFlow()

    val modelProgress = models.progress
    val modelError = models.error

    init {
        viewModelScope.launch {
            val image = runCatching { loadImage() }.getOrNull()
            if (image == null) {
                state.value = OcrState.ImageFailed
                return@launch
            }
            if (!models.installed.value) {
                state.value = OcrState.NeedsModels(image)
                models.installed.filter { it }.first()
            }
            recognize(image)
        }
    }

    fun downloadModels() {
        models.clearError()
        models.download()
    }

    fun cancelDownload() = models.cancel()

    fun retry() {
        val image = (state.value as? OcrState.Failed)?.image ?: return
        viewModelScope.launch { recognize(image) }
    }

    private suspend fun recognize(image: Bitmap) {
        state.value = OcrState.Recognizing(image)
        state.value = try {
            OcrState.Done(image, engine.recognize(image))
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            OcrState.Failed(image)
        }
    }

    override fun onCleared() {
        appScope.launch { engine.close() }
    }
}
