package io.github.lenkr0.verba.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.lenkr0.verba.R
import io.github.lenkr0.verba.data.EntryEntity
import io.github.lenkr0.verba.data.EntryListItem
import io.github.lenkr0.verba.data.LanguageCount
import io.github.lenkr0.verba.data.ListFilter
import io.github.lenkr0.verba.data.PendingDeletion
import io.github.lenkr0.verba.data.SourceCount
import io.github.lenkr0.verba.language.Languages
import io.github.lenkr0.verba.ui.components.VerbaCard
import io.github.lenkr0.verba.ui.theme.VerbaTheme
import io.github.lenkr0.verba.ui.theme.reading

/** Test tags of the word list. */
object HomeTags {
    const val SEARCH_FIELD = "home_search_field"
    fun entry(id: String) = "home_entry_$id"
}

/** Everything the word list shows; [entries] is null while loading. */
data class HomeState(
    val entries: List<EntryListItem>?,
    val filter: ListFilter = ListFilter(),
    val languages: List<LanguageCount> = emptyList(),
    val sources: List<SourceCount> = emptyList(),
    val pendingDeletion: PendingDeletion? = null,
)

class HomeActions(
    val onOpenSettings: () -> Unit = {},
    val onOpenEntry: (String) -> Unit = {},
    val onQuery: (String) -> Unit = {},
    val onToggleLanguage: (String) -> Unit = {},
    val onToggleSource: (String) -> Unit = {},
    val onUndo: (PendingDeletion) -> Unit = {},
    val onUndoDismissed: () -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(state: HomeState, actions: HomeActions) {
    var searching by rememberSaveable { mutableStateOf(state.filter.query.isNotEmpty()) }
    val snackbar = remember { SnackbarHostState() }
    val deletedText = state.pendingDeletion?.let { stringResource(R.string.home_deleted, it.lemma) }
    val undoText = stringResource(R.string.action_undo)
    LaunchedEffect(state.pendingDeletion) {
        val deletion = state.pendingDeletion ?: return@LaunchedEffect
        val result = snackbar.showSnackbar(deletedText.orEmpty(), undoText, duration = SnackbarDuration.Long)
        if (result == SnackbarResult.ActionPerformed) actions.onUndo(deletion) else actions.onUndoDismissed()
    }

    Scaffold(
        topBar = {
            if (searching) {
                SearchBar(
                    query = state.filter.query,
                    onQuery = actions.onQuery,
                    onClose = {
                        actions.onQuery("")
                        searching = false
                    },
                )
            } else {
                TopAppBar(
                    title = { Text(stringResource(R.string.app_name)) },
                    actions = {
                        if (!state.entries.isNullOrEmpty() || state.filter != ListFilter()) {
                            IconButton(onClick = { searching = true }) {
                                Icon(painterResource(R.drawable.ic_search), stringResource(R.string.action_search))
                            }
                        }
                        IconButton(onClick = actions.onOpenSettings) {
                            Icon(painterResource(R.drawable.ic_settings), stringResource(R.string.action_settings))
                        }
                    },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val filtering = state.filter != ListFilter()
        when {
            state.entries == null -> Box(Modifier.padding(padding))
            state.entries.isEmpty() && !filtering -> EmptyState(Modifier.padding(padding))
            else -> Column(Modifier.padding(padding)) {
                Filters(state, actions)
                if (state.entries.isEmpty()) {
                    Text(
                        stringResource(R.string.home_no_results),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp),
                    )
                } else {
                    EntryList(state.entries, actions.onOpenEntry)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchBar(query: String, onQuery: (String) -> Unit, onClose: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    TopAppBar(
        title = {
            TextField(
                value = query,
                onValueChange = onQuery,
                placeholder = { Text(stringResource(R.string.home_search_hint), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus)
                    .testTag(HomeTags.SEARCH_FIELD),
            )
        },
        actions = {
            IconButton(onClick = onClose) {
                Icon(painterResource(R.drawable.ic_close), stringResource(R.string.action_close))
            }
        },
    )
}

/** Chips for languages (when there is more than one) and books. Tapping a selected chip clears it. */
@Composable
private fun Filters(state: HomeState, actions: HomeActions) {
    val languages = state.languages.takeIf { it.size > 1 }.orEmpty()
    if (languages.isEmpty() && state.sources.isEmpty()) return
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(bottom = 4.dp),
    ) {
        items(languages, key = { "lang-" + it.language }) { language ->
            FilterChip(
                selected = state.filter.language == language.language,
                onClick = { actions.onToggleLanguage(language.language) },
                label = { Text("${Languages.displayName(language.language)} · ${language.entryCount}") },
            )
        }
        items(state.sources, key = { "source-" + it.id }) { source ->
            FilterChip(
                selected = state.filter.sourceId == source.id,
                onClick = { actions.onToggleSource(source.id) },
                label = { Text("${source.title} · ${source.entryCount}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                modifier = Modifier.widthIn(max = 240.dp),
            )
        }
    }
}

@Composable
private fun EntryList(entries: List<EntryListItem>, onOpen: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxSize()) {
        items(entries, key = { it.entry.id }) { item ->
            EntryRow(item, onClick = { onOpen(item.entry.id) })
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun EntryRow(item: EntryListItem, onClick: () -> Unit) {
    val entry = item.entry
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(HomeTags.entry(entry.id))
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(entry.lemma, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(
                "${Languages.shortLabel(entry.language)} → ${Languages.shortLabel(entry.targetLanguage)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (entry.translation.isNotBlank()) {
            Text(entry.translation, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        val sentence = item.latestSentence
        if (!sentence.isNullOrBlank()) {
            Text(
                highlighted(sentence, item.latestWordStart ?: -1, item.latestWordEnd ?: -1),
                style = MaterialTheme.typography.bodyMedium.reading(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val meta = listOfNotNull(
            item.latestSource,
            pluralStringResource(R.plurals.home_sentences, item.contextCount, item.contextCount)
                .takeIf { item.contextCount > 1 },
        )
        if (meta.isNotEmpty()) {
            Text(
                meta.joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The sentence with the looked-up word in bold. */
fun highlighted(sentence: String, start: Int, end: Int): AnnotatedString = buildAnnotatedString {
    if (start < 0 || end > sentence.length || start >= end) {
        append(sentence)
        return@buildAnnotatedString
    }
    append(sentence.substring(0, start))
    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(sentence.substring(start, end)) }
    append(sentence.substring(end))
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 560.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(32.dp))
            Icon(
                painterResource(R.drawable.ic_auto_stories),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(56.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.home_empty_title), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.home_empty_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(32.dp))
            VerbaCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.home_steps_title), style = MaterialTheme.typography.titleMedium)
                    listOf(R.string.home_step_select, R.string.home_step_translate, R.string.home_step_learn)
                        .forEachIndexed { index, text -> Step(number = index + 1, text = stringResource(text)) }
                }
            }
        }
    }
}

@Composable
private fun Step(number: Int, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                number.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 12.dp))
    }
}

private val previewEntries = listOf(
    EntryListItem(
        EntryEntity("1", "en", "de", "run", "run", "laufen, rennen", "", 0, 0),
        "She was running late for the train.", 8, 15, "The Hobbit", 2,
    ),
    EntryListItem(
        EntryEntity("2", "zh", "de", "学习", "学习", "lernen", "", 0, 0),
        "我每天学习中文。", 3, 5, "三体", 1,
    ),
)

@Preview(name = "List")
@Composable
private fun HomeListPreview() = VerbaTheme(darkTheme = false) {
    HomeScreen(
        HomeState(
            entries = previewEntries,
            languages = listOf(LanguageCount("en", 12), LanguageCount("zh", 3)),
            sources = listOf(SourceCount("s1", "The Hobbit", 9), SourceCount("s2", "三体", 3)),
        ),
        HomeActions(),
    )
}

@Preview(name = "Empty, E-ink")
@Composable
private fun HomeEmptyEInkPreview() = VerbaTheme(darkTheme = false, eInk = true) {
    HomeScreen(HomeState(entries = emptyList()), HomeActions())
}
