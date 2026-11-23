package io.github.lenkr0.verba.capture

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.lenkr0.verba.R
import io.github.lenkr0.verba.data.SaveResult
import io.github.lenkr0.verba.language.Languages
import io.github.lenkr0.verba.language.Sentence
import io.github.lenkr0.verba.ui.components.LanguagePickerDialog
import io.github.lenkr0.verba.ui.theme.LocalEInkMode
import io.github.lenkr0.verba.ui.theme.reading

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureSheet(viewModel: CaptureViewModel, onDismiss: () -> Unit, onSaved: (SaveResult, String) -> Unit) {
    val content: @Composable () -> Unit = { CaptureContent(viewModel, onSave = { viewModel.save(onSaved) }) }
    if (LocalEInkMode.current) {
        StaticSheet(onDismiss, content)
    } else {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            // Opens straight to full height; no half-open stop
            sheetState = rememberBottomSheetState(
                initialValue = SheetValue.Hidden,
                enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
            ),
        ) {
            Box(Modifier.imePadding()) { content() }
        }
    }
}

/**
 * The E-ink version of the sheet: appears at once, no gray scrim, a solid
 * border, closes with back or a tap outside.
 */
@Composable
private fun StaticSheet(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    BackHandler(onBack = onDismiss)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(interactionSource = null, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter,
    ) {
        val shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
        Surface(
            modifier = Modifier
                .statusBarsPadding()
                .padding(top = 24.dp)
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .border(2.dp, MaterialTheme.colorScheme.outline, shape),
            shape = shape,
        ) {
            Column(
                Modifier
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(top = 16.dp),
            ) { content() }
        }
    }
}

/** Test tags of the sheet's fields. */
object CaptureTags {
    const val LEMMA = "capture_lemma"
    const val TRANSLATION = "capture_translation"
    const val DEFINITION = "capture_definition"
    const val SENTENCE = "capture_sentence"
    const val TITLE = "capture_title"
    const val SAVE = "capture_save"
}

private enum class Picker { Source, Target }

@Composable
private fun CaptureContent(viewModel: CaptureViewModel, onSave: () -> Unit) {
    val state = viewModel.state
    var picker by rememberSaveable { mutableStateOf<Picker?>(null) }

    // The fields scroll; the Save button stays at the bottom, above the keyboard
    Column(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LanguageBar(
                source = state.language,
                target = state.targetLanguage,
                appLabel = state.sourceAppLabel,
                onPickSource = { picker = Picker.Source },
                onPickTarget = { picker = Picker.Target },
            )
            if (state.isSentenceMode) {
                Text(
                    stringResource(R.string.capture_tap_word),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TappableText(state.sentences, state.choice, viewModel::chooseWord)
                if (state.phraseAllowed) {
                    TextButton(onClick = viewModel::choosePhrase) {
                        Text(stringResource(R.string.capture_whole_selection))
                    }
                }
            }
            if (state.hasWord) WordFields(state, viewModel)
        }
        if (state.hasWord) {
            Button(
                onClick = onSave,
                enabled = state.canSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .testTag(CaptureTags.SAVE),
            ) {
                Text(stringResource(R.string.capture_save))
            }
        }
    }

    when (picker) {
        Picker.Source -> LanguagePickerDialog(
            title = stringResource(R.string.language_source_title),
            selected = state.language,
            recent = viewModel.recentLanguages,
            onSelect = { picker = null; viewModel.setLanguage(it) },
            onDismiss = { picker = null },
        )
        Picker.Target -> LanguagePickerDialog(
            title = stringResource(R.string.language_target_title),
            selected = state.targetLanguage,
            recent = emptyList(),
            onSelect = { picker = null; viewModel.setTargetLanguage(it) },
            onDismiss = { picker = null },
        )
        null -> Unit
    }
}

@Composable
private fun LanguageBar(
    source: String,
    target: String,
    appLabel: String?,
    onPickSource: () -> Unit,
    onPickTarget: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        LanguageChip(source, onPickSource)
        Icon(
            painterResource(R.drawable.ic_arrow_forward),
            contentDescription = null,
            modifier = Modifier.padding(horizontal = 6.dp).size(18.dp),
        )
        LanguageChip(target, onPickTarget)
        Spacer(Modifier.weight(1f))
        if (appLabel != null) {
            Text(
                stringResource(R.string.capture_from_app, appLabel),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@Composable
private fun LanguageChip(tag: String, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(Languages.displayName(tag)) },
        trailingIcon = { Icon(painterResource(R.drawable.ic_expand_more), contentDescription = null, Modifier.size(18.dp)) },
    )
}

/** The selection with every word tappable; works for languages without spaces too. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TappableText(sentences: List<Sentence>, choice: WordChoice?, onChoose: (Int, Int) -> Unit) {
    val style = MaterialTheme.typography.bodyLarge.reading()
    FlowRow(Modifier.fillMaxWidth()) {
        sentences.forEachIndexed { sentenceIndex, sentence ->
            sentence.tokens.forEachIndexed { tokenIndex, token ->
                when {
                    token.isWord -> WordToken(
                        text = token.text,
                        selected = choice == WordChoice(sentenceIndex, tokenIndex),
                        style = style,
                        onClick = { onChoose(sentenceIndex, tokenIndex) },
                    )
                    // the words' own padding already separates them; a space adds just a little
                    token.text.isBlank() -> Spacer(Modifier.width(2.dp))
                    else -> Text(token.text, style = style, modifier = Modifier.padding(vertical = 6.dp))
                }
            }
            Spacer(Modifier.width(6.dp))
        }
    }
}

@Composable
private fun WordToken(text: String, selected: Boolean, style: TextStyle, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Text(
        text = text,
        style = style,
        color = if (selected) colors.onPrimaryContainer else colors.onSurface,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) colors.primaryContainer else Color.Transparent)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 6.dp),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WordFields(state: CaptureUiState, viewModel: CaptureViewModel) {
    Text(state.wordForm, style = MaterialTheme.typography.headlineSmall.reading())
    OutlinedTextField(
        value = state.lemma,
        onValueChange = viewModel::setLemma,
        label = { Text(stringResource(R.string.capture_base_form)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag(CaptureTags.LEMMA),
    )
    OutlinedTextField(
        value = state.translation,
        onValueChange = viewModel::setTranslation,
        label = { Text(stringResource(R.string.capture_translation, Languages.displayName(state.targetLanguage))) },
        supportingText = { Text(stringResource(R.string.capture_no_dictionary)) },
        modifier = Modifier.fillMaxWidth().testTag(CaptureTags.TRANSLATION),
    )
    OutlinedTextField(
        value = state.definition,
        onValueChange = viewModel::setDefinition,
        label = { Text(stringResource(R.string.capture_definition)) },
        modifier = Modifier.fillMaxWidth().testTag(CaptureTags.DEFINITION),
    )
    if (!state.isSentenceMode) {
        OutlinedTextField(
            value = state.sentence,
            onValueChange = viewModel::setSentence,
            label = { Text(stringResource(R.string.capture_sentence)) },
            textStyle = MaterialTheme.typography.bodyLarge.reading(),
            minLines = 2,
            modifier = Modifier.fillMaxWidth().testTag(CaptureTags.SENTENCE),
        )
    }
    OutlinedTextField(
        value = state.sourceTitle,
        onValueChange = viewModel::setSourceTitle,
        label = { Text(stringResource(R.string.capture_source_title)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag(CaptureTags.TITLE),
    )
    val suggestions = state.titleSuggestions.filter { !it.equals(state.sourceTitle.trim(), ignoreCase = true) }.take(4)
    if (suggestions.isNotEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            suggestions.forEach { title ->
                SuggestionChip(
                    onClick = { viewModel.setSourceTitle(title) },
                    label = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                )
            }
        }
    }
    state.existing?.let { existing ->
        Text(
            pluralStringResource(R.plurals.capture_existing, existing.contextCount, existing.contextCount),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
