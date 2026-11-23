package io.github.lenkr0.verba.ui.home

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.lenkr0.verba.R
import io.github.lenkr0.verba.data.EntryEntity
import io.github.lenkr0.verba.data.EntryListItem
import io.github.lenkr0.verba.language.Languages
import io.github.lenkr0.verba.ui.components.VerbaCard
import io.github.lenkr0.verba.ui.theme.VerbaTheme
import io.github.lenkr0.verba.ui.theme.reading

/** [entries] is null while loading. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(entries: List<EntryListItem>?, onOpenSettings: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            painterResource(R.drawable.ic_settings),
                            contentDescription = stringResource(R.string.action_settings),
                        )
                    }
                },
            )
        },
    ) { padding ->
        when {
            entries == null -> Box(Modifier.padding(padding))
            entries.isEmpty() -> EmptyState(Modifier.padding(padding))
            else -> EntryList(entries, padding)
        }
    }
}

@Composable
private fun EntryList(entries: List<EntryListItem>, padding: PaddingValues) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
        items(entries, key = { it.entry.id }) { item ->
            EntryRow(item)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun EntryRow(item: EntryListItem) {
    val entry = item.entry
    Column(
        Modifier
            .fillMaxWidth()
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
            Text(entry.translation, style = MaterialTheme.typography.bodyMedium)
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
private fun highlighted(sentence: String, start: Int, end: Int): AnnotatedString = buildAnnotatedString {
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
private fun HomeListPreview() = VerbaTheme(darkTheme = false) { HomeScreen(previewEntries, onOpenSettings = {}) }

@Preview(name = "Empty, E-ink")
@Composable
private fun HomeEmptyEInkPreview() = VerbaTheme(darkTheme = false, eInk = true) { HomeScreen(emptyList(), onOpenSettings = {}) }
