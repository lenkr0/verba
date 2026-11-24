package io.github.lenkr0.verba.ui.entry

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lenkr0.verba.R
import io.github.lenkr0.verba.data.ContextWithSource
import io.github.lenkr0.verba.data.EntryEntity
import io.github.lenkr0.verba.language.Languages
import io.github.lenkr0.verba.ui.components.SectionHeader
import io.github.lenkr0.verba.ui.components.VerbaAlertDialog
import io.github.lenkr0.verba.ui.components.VerbaCard
import io.github.lenkr0.verba.ui.home.highlighted
import io.github.lenkr0.verba.ui.theme.reading
import java.text.DateFormat
import java.util.Date

/** Test tags of the detail screen. */
object EntryTags {
    const val LEMMA = "entry_lemma"
    const val TRANSLATION = "entry_translation"
    const val DEFINITION = "entry_definition"
    const val SAVE = "entry_save"
    const val DELETE = "entry_delete"
    const val MERGE = "entry_merge"
    const val MERGE_SEARCH = "entry_merge_search"
    fun sentence(id: String) = "entry_sentence_$id"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryScreen(
    viewModel: EntryViewModel,
    onBack: () -> Unit,
    onOpenEntry: (String) -> Unit,
) {
    val entry by viewModel.entry.collectAsStateWithLifecycle()
    val contexts by viewModel.contexts.collectAsStateWithLifecycle()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var showMerge by rememberSaveable { mutableStateOf(false) }
    var deleteSentence by rememberSaveable { mutableStateOf<String?>(null) }
    val current = entry

    // Merged or deleted elsewhere: nothing left to show here
    LaunchedEffect(current?.deletedAt) { if (current?.deletedAt != null) onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(current?.lemma.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = { showMerge = true }, modifier = Modifier.testTag(EntryTags.MERGE)) {
                        Icon(painterResource(R.drawable.ic_call_merge), stringResource(R.string.entry_merge))
                    }
                    IconButton(onClick = { confirmDelete = true }, modifier = Modifier.testTag(EntryTags.DELETE)) {
                        Icon(painterResource(R.drawable.ic_delete), stringResource(R.string.entry_delete))
                    }
                },
            )
        },
    ) { padding ->
        if (current == null) return@Scaffold
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
        ) {
            item { Fields(current, viewModel) }
            viewModel.duplicates.firstOrNull()?.let { duplicate ->
                item {
                    VerbaCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.entry_duplicate_hint, duplicate.lemma),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { viewModel.mergeInto(duplicate.id, onOpenEntry) }) {
                                Text(stringResource(R.string.entry_merge_into))
                            }
                        }
                    }
                }
            }
            item { SectionHeader("${stringResource(R.string.entry_sentences)} · ${contexts.size}") }
            items(contexts, key = { it.context.id }) { context ->
                SentenceRow(context, onDelete = { deleteSentence = context.context.id })
            }
        }
    }

    if (confirmDelete && current != null) {
        VerbaAlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.entry_delete_title, current.lemma)) },
            text = { Text(stringResource(R.string.entry_delete_body)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; viewModel.delete(onBack) }) {
                    Text(stringResource(R.string.dictionaries_delete))
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    deleteSentence?.let { id ->
        VerbaAlertDialog(
            onDismissRequest = { deleteSentence = null },
            title = { Text(stringResource(R.string.entry_delete_sentence_title)) },
            text = { Text(contexts.firstOrNull { it.context.id == id }?.context?.sentence.orEmpty()) },
            confirmButton = {
                TextButton(onClick = { deleteSentence = null; viewModel.deleteContext(id) }) {
                    Text(stringResource(R.string.dictionaries_delete))
                }
            },
            dismissButton = { TextButton(onClick = { deleteSentence = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    if (showMerge && current != null) {
        MergeDialog(current, viewModel, onDismiss = { showMerge = false }, onPick = { other ->
            showMerge = false
            viewModel.mergeInto(other.id, onOpenEntry)
        })
    }
    viewModel.conflict?.let { other ->
        VerbaAlertDialog(
            onDismissRequest = viewModel::dismissConflict,
            title = { Text(stringResource(R.string.entry_conflict_title, other.lemma)) },
            text = { Text(stringResource(R.string.entry_conflict_body)) },
            confirmButton = {
                TextButton(onClick = { viewModel.mergeInto(other.id, onOpenEntry) }) {
                    Text(stringResource(R.string.entry_merge_confirm))
                }
            },
            dismissButton = { TextButton(onClick = viewModel::dismissConflict) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun Fields(entry: EntryEntity, viewModel: EntryViewModel) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "${Languages.displayName(entry.language)} → ${Languages.displayName(entry.targetLanguage)}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = viewModel.lemma,
            onValueChange = viewModel::onLemmaChange,
            label = { Text(stringResource(R.string.capture_base_form)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag(EntryTags.LEMMA),
        )
        OutlinedTextField(
            value = viewModel.translation,
            onValueChange = viewModel::onTranslationChange,
            label = { Text(stringResource(R.string.capture_translation, Languages.displayName(entry.targetLanguage))) },
            modifier = Modifier.fillMaxWidth().testTag(EntryTags.TRANSLATION),
        )
        OutlinedTextField(
            value = viewModel.definition,
            onValueChange = viewModel::onDefinitionChange,
            label = { Text(stringResource(R.string.capture_definition)) },
            modifier = Modifier.fillMaxWidth().testTag(EntryTags.DEFINITION),
        )
        Button(
            onClick = viewModel::save,
            enabled = viewModel.hasChanges(entry) && viewModel.lemma.isNotBlank(),
            modifier = Modifier.align(Alignment.End).testTag(EntryTags.SAVE),
        ) { Text(stringResource(R.string.entry_save)) }
    }
}

@Composable
private fun SentenceRow(item: ContextWithSource, onDelete: () -> Unit) {
    val context = item.context
    val locale = LocalConfiguration.current.locales[0]
    val date = DateFormat.getDateInstance(DateFormat.MEDIUM, locale).format(Date(context.createdAt))
    Row(
        Modifier
            .fillMaxWidth()
            .testTag(EntryTags.sentence(context.id))
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                if (context.sentence.isBlank()) highlighted(context.wordForm, 0, context.wordForm.length)
                else highlighted(context.sentence, context.wordStart, context.wordEnd),
                style = MaterialTheme.typography.bodyLarge.reading(),
            )
            Text(
                listOfNotNull(item.sourceTitle, context.sourceAppLabel, date).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                painterResource(R.drawable.ic_delete),
                stringResource(R.string.entry_delete_sentence),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Picks the word to merge this one into: a search over the other words of the same language. */
@Composable
private fun MergeDialog(entry: EntryEntity, viewModel: EntryViewModel, onDismiss: () -> Unit, onPick: (EntryEntity) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    // Not saveable (entries are no Parcelables): the search runs again anyway
    var candidates by remember { mutableStateOf(emptyList<EntryEntity>()) }
    LaunchedEffect(query) { candidates = viewModel.mergeCandidates(query) }
    VerbaAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.entry_merge_title, entry.lemma)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.entry_merge_body, entry.lemma), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringResource(R.string.entry_merge_search)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag(EntryTags.MERGE_SEARCH),
                )
                if (candidates.isEmpty()) {
                    Text(
                        stringResource(R.string.entry_merge_none),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LazyColumn(Modifier.heightIn(max = 280.dp)) {
                    items(candidates, key = { it.id }) { other ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onPick(other) }
                                .padding(vertical = 10.dp),
                        ) {
                            Text(other.lemma, style = MaterialTheme.typography.titleSmall)
                            if (other.translation.isNotBlank()) {
                                Text(
                                    other.translation,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
