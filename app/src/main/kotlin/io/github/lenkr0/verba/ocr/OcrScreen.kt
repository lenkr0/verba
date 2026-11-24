package io.github.lenkr0.verba.ocr

import android.graphics.Bitmap
import android.text.format.Formatter
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lenkr0.verba.R
import io.github.lenkr0.verba.ui.components.VerbaCard
import io.github.lenkr0.verba.ui.theme.LocalEInkMode
import io.github.lenkr0.verba.ui.theme.reading
import kotlin.math.roundToInt

/** Test tags of the text recognition screen. */
object OcrTags {
    const val DOWNLOAD = "ocr_download"
    const val RECOGNIZING = "ocr_recognizing"
    const val TEXT_MODE = "ocr_text_mode"
    fun block(index: Int) = "ocr_block_$index"
    fun row(index: Int) = "ocr_row_$index"
}

@Composable
fun OcrScreen(viewModel: OcrViewModel, onClose: () -> Unit, onBlock: (TextBlock) -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val progress by viewModel.modelProgress.collectAsStateWithLifecycle()
    val error by viewModel.modelError.collectAsStateWithLifecycle()
    OcrContent(
        state = state,
        modelProgress = progress,
        modelError = error,
        onClose = onClose,
        onBlock = onBlock,
        onDownload = viewModel::downloadModels,
        onCancelDownload = viewModel::cancelDownload,
        onRetry = viewModel::retry,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrContent(
    state: OcrState,
    modelProgress: Float?,
    modelError: OcrModelError?,
    onClose: () -> Unit,
    onBlock: (TextBlock) -> Unit,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onRetry: () -> Unit,
) {
    var textMode by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ocr_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(painterResource(R.drawable.ic_close), stringResource(R.string.action_close))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            when (state) {
                OcrState.LoadingImage -> Unit
                OcrState.ImageFailed -> Message(stringResource(R.string.ocr_image_failed))
                is OcrState.NeedsModels -> ModelsCard(modelProgress, modelError, onDownload, onCancelDownload)
                is OcrState.Recognizing -> Recognizing()
                is OcrState.Failed -> {
                    Message(stringResource(R.string.ocr_failed))
                    TextButton(onClick = onRetry, modifier = Modifier.padding(horizontal = 16.dp)) {
                        Text(stringResource(R.string.action_retry))
                    }
                }
                is OcrState.Done -> if (state.blocks.isEmpty()) {
                    Message(stringResource(R.string.ocr_nothing_found))
                    PageImage(state.image, emptyList(), onBlock)
                } else {
                    ModeSwitch(textMode, onChange = { textMode = it })
                    if (textMode) BlockList(state.blocks, onBlock) else PageImage(state.image, state.blocks, onBlock)
                }
            }
        }
    }
}

@Composable
private fun Message(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
    )
}

@Composable
private fun Recognizing() {
    Column(Modifier.fillMaxWidth().padding(24.dp).testTag(OcrTags.RECOGNIZING), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.ocr_recognizing), style = MaterialTheme.typography.bodyLarge)
        // E-ink: no moving indicator, the text says enough
        if (!LocalEInkMode.current) LinearProgressIndicator(Modifier.fillMaxWidth())
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModeSwitch(textMode: Boolean, onChange: (Boolean) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.ocr_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = !textMode,
                onClick = { onChange(false) },
                shape = SegmentedButtonDefaults.itemShape(0, 2),
            ) { Text(stringResource(R.string.ocr_mode_image)) }
            SegmentedButton(
                selected = textMode,
                onClick = { onChange(true) },
                shape = SegmentedButtonDefaults.itemShape(1, 2),
                modifier = Modifier.testTag(OcrTags.TEXT_MODE),
            ) { Text(stringResource(R.string.ocr_mode_text)) }
        }
    }
}

/** The page, scaled to the screen's width, with a tappable frame around each text block. */
@Composable
private fun PageImage(image: Bitmap, blocks: List<TextBlock>, onBlock: (TextBlock) -> Unit) {
    val bitmap = remember(image) { image.asImageBitmap() }
    val density = LocalDensity.current
    val frame = BorderStroke(if (LocalEInkMode.current) 3.dp else 2.dp, MaterialTheme.colorScheme.primary)
    BoxWithConstraints(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        val scale = constraints.maxWidth.toFloat() / image.width
        val height = with(density) { (image.height * scale).toDp() }
        Box(Modifier.fillMaxWidth().height(height)) {
            Image(bitmap, contentDescription = null, contentScale = ContentScale.FillWidth, modifier = Modifier.fillMaxSize())
            blocks.forEachIndexed { index, block ->
                val b = block.bounds
                val pad = 4.dp
                Box(
                    Modifier
                        .offset { IntOffset((b.left * scale).roundToInt() - pad.roundToPx(), (b.top * scale).roundToInt() - pad.roundToPx()) }
                        .size(with(density) { (b.width * scale).toDp() } + pad * 2, with(density) { (b.height * scale).toDp() } + pad * 2)
                        .border(frame, MaterialTheme.shapes.small)
                        .clickable { onBlock(block) }
                        .semantics { contentDescription = block.text }
                        .testTag(OcrTags.block(index)),
                )
            }
        }
    }
}

/** The recognized texts as a list: easier to read on small screens and with a screen reader. */
@Composable
private fun BlockList(blocks: List<TextBlock>, onBlock: (TextBlock) -> Unit) {
    LazyColumn(Modifier.fillMaxSize()) {
        itemsIndexed(blocks) { index, block ->
            Text(
                block.text,
                style = MaterialTheme.typography.bodyLarge.reading(),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onBlock(block) }
                    .testTag(OcrTags.row(index))
                    .padding(horizontal = 20.dp, vertical = 14.dp),
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

/** Shown once: the models have to be downloaded before the first recognition. */
@Composable
private fun ModelsCard(progress: Float?, error: OcrModelError?, onDownload: () -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    val size = remember { Formatter.formatShortFileSize(context, OcrModels.PACK.size) }
    Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        VerbaCard(Modifier.widthIn(max = 560.dp).fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.ocr_models_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.ocr_models_body, size), style = MaterialTheme.typography.bodyMedium)
                if (error != null) {
                    Text(
                        stringResource(if (error == OcrModelError.Checksum) R.string.ocr_models_error_checksum else R.string.ocr_models_error_download),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (progress == null) {
                    Button(onClick = onDownload, modifier = Modifier.align(Alignment.End).testTag(OcrTags.DOWNLOAD)) {
                        Text(stringResource(R.string.ocr_models_download, size))
                    }
                } else {
                    val percent = (progress * 100).roundToInt()
                    Text(stringResource(R.string.ocr_models_downloading, percent), style = MaterialTheme.typography.bodyMedium)
                    if (!LocalEInkMode.current) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
                    }
                }
            }
        }
        Text(
            stringResource(R.string.ocr_models_privacy),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(16.dp).widthIn(max = 560.dp),
        )
    }
}
