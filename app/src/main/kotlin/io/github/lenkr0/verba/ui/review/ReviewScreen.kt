package io.github.lenkr0.verba.ui.review

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lenkr0.verba.R
import io.github.lenkr0.verba.data.ContextEntity
import io.github.lenkr0.verba.data.ContextWithSource
import io.github.lenkr0.verba.data.EntryEntity
import io.github.lenkr0.verba.language.Languages
import io.github.lenkr0.verba.review.Cloze
import io.github.lenkr0.verba.review.IntervalUnit
import io.github.lenkr0.verba.review.Rating
import io.github.lenkr0.verba.review.ReviewCounts
import io.github.lenkr0.verba.review.ReviewItem
import io.github.lenkr0.verba.review.intervalLabel
import io.github.lenkr0.verba.ui.theme.LocalEInkMode
import io.github.lenkr0.verba.ui.theme.VerbaTheme
import io.github.lenkr0.verba.ui.theme.reading
import java.text.NumberFormat

/** Test tags of the review screen. */
object ReviewTags {
    const val SHOW_ANSWER = "review_show_answer"
    const val SHOW_HINT = "review_show_hint"
    const val HINT = "review_hint"
    const val SENTENCE = "review_sentence"
    const val ANSWER = "review_answer"
    const val DONE = "review_done"
    const val UNDO = "review_undo"
    fun rating(rating: Rating) = "review_rating_${rating.name.lowercase()}"
}

class ReviewActions(
    val onBack: () -> Unit = {},
    val onReveal: () -> Unit = {},
    val onShowHint: () -> Unit = {},
    val onRate: (Rating) -> Unit = {},
    val onUndo: () -> Unit = {},
    val onEdit: (String) -> Unit = {},
    val onContinue: () -> Unit = {},
)

@Composable
fun ReviewScreen(viewModel: ReviewViewModel, onBack: () -> Unit, onEdit: (String) -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val counts by viewModel.counts.collectAsStateWithLifecycle()
    // Back from editing the word: show it as it is now
    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose {}
    }
    ReviewContent(
        state,
        counts,
        ReviewActions(
            onBack = onBack,
            onReveal = viewModel::reveal,
            onShowHint = viewModel::showHint,
            onRate = viewModel::rate,
            onUndo = viewModel::undo,
            onEdit = onEdit,
            onContinue = viewModel::loadNext,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewContent(state: ReviewUiState, counts: ReviewCounts?, actions: ReviewActions) {
    val item = state.item
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.review_title))
                        val left = counts?.total
                        if (item != null && left != null && left > 0) {
                            Text(
                                pluralStringResource(R.plurals.review_left, left, left),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (state.canUndo) {
                        IconButton(onClick = actions.onUndo, modifier = Modifier.testTag(ReviewTags.UNDO)) {
                            Icon(painterResource(R.drawable.ic_undo), stringResource(R.string.review_undo))
                        }
                    }
                    if (item != null) {
                        IconButton(onClick = { actions.onEdit(item.entry.id) }) {
                            Icon(painterResource(R.drawable.ic_edit), stringResource(R.string.review_edit))
                        }
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.loading -> Box(Modifier.padding(padding))
            item == null -> Done(state.reviewed, counts, actions.onContinue, Modifier.padding(padding))
            else -> Column(Modifier.fillMaxSize().padding(padding)) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    Card(item, state.revealed, state.hintShown, actions.onShowHint)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                    if (state.revealed) {
                        RatingButtons(state.intervals, actions.onRate)
                    } else {
                        Button(
                            onClick = actions.onReveal,
                            modifier = Modifier
                                .widthIn(max = 560.dp)
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .testTag(ReviewTags.SHOW_ANSWER),
                        ) { Text(stringResource(R.string.review_show_answer)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Card(item: ReviewItem, revealed: Boolean, hintShown: Boolean, onShowHint: () -> Unit) {
    val entry = item.entry
    val meaning = entry.translation.ifBlank { entry.definition }
    Column(
        Modifier
            .widthIn(max = 640.dp)
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        val source = item.context?.let { it.sourceTitle ?: it.context.sourceAppLabel }
        Text(
            listOfNotNull(Languages.displayName(entry.language), source).joinToString(" · "),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val cloze = item.cloze
        when {
            cloze != null -> {
                Text(
                    clozeText(cloze, revealed, MaterialTheme.colorScheme.primary),
                    style = MaterialTheme.typography.headlineSmall.reading(),
                    modifier = Modifier.testTag(ReviewTags.SENTENCE),
                )
                if (!revealed && meaning.isNotBlank()) {
                    if (hintShown) {
                        Text(
                            meaning,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag(ReviewTags.HINT),
                        )
                    } else {
                        TextButton(onClick = onShowHint, modifier = Modifier.testTag(ReviewTags.SHOW_HINT)) {
                            Text(stringResource(R.string.review_show_hint))
                        }
                    }
                }
            }
            meaning.isNotBlank() -> {
                Text(stringResource(R.string.review_prompt_meaning), style = MaterialTheme.typography.labelLarge)
                Text(meaning, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.testTag(ReviewTags.SENTENCE))
            }
            else -> {
                Text(stringResource(R.string.review_prompt_word), style = MaterialTheme.typography.labelLarge)
                Text(entry.lemma, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.testTag(ReviewTags.SENTENCE))
            }
        }
        val eInk = LocalEInkMode.current
        AnimatedVisibility(
            visible = revealed,
            enter = if (eInk) EnterTransition.None else fadeIn(),
            exit = ExitTransition.None,
        ) {
            Answer(entry, Modifier.testTag(ReviewTags.ANSWER))
        }
    }
}

@Composable
private fun Answer(entry: EntryEntity, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(bottom = 8.dp))
        Text(entry.lemma, style = MaterialTheme.typography.headlineMedium)
        if (entry.translation.isNotBlank()) {
            Text(entry.translation, style = MaterialTheme.typography.titleMedium)
        }
        if (entry.definition.isNotBlank()) {
            Text(entry.definition, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (entry.translation.isBlank() && entry.definition.isBlank()) {
            Text(
                stringResource(R.string.review_no_translation),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The sentence with a gap, or with the answer underlined once revealed. Underscores print on any display. */
fun clozeText(cloze: Cloze, revealed: Boolean, accent: Color): AnnotatedString =
    buildAnnotatedString {
        append(cloze.before)
        if (revealed) {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = accent, textDecoration = TextDecoration.Underline)) {
                append(cloze.answer)
            }
        } else {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = accent)) { append(GAP) }
        }
        append(cloze.after)
    }

private const val GAP = " _____ "

@Composable
private fun RatingButtons(intervals: Map<Rating, Long>, onRate: (Rating) -> Unit) {
    val height = if (LocalEInkMode.current) 72.dp else 64.dp
    Row(
        Modifier.widthIn(max = 560.dp).fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Rating.entries.forEach { rating ->
            val label = stringResource(rating.label)
            val interval = intervals[rating]?.let { intervalText(it) }.orEmpty()
            val description = stringResource(R.string.rating_with_interval, label, interval)
            val modifier = Modifier
                .weight(1f)
                .heightIn(min = height)
                .testTag(ReviewTags.rating(rating))
                .semantics { contentDescription = description }
            val content: @Composable () -> Unit = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(label, maxLines = 1)
                    Text(interval, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
            }
            val padding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
            if (rating == Rating.Good) {
                Button(onClick = { onRate(rating) }, modifier = modifier, contentPadding = padding) { content() }
            } else {
                OutlinedButton(onClick = { onRate(rating) }, modifier = modifier, contentPadding = padding) { content() }
            }
        }
    }
}

private val Rating.label: Int
    get() = when (this) {
        Rating.Again -> R.string.rating_again
        Rating.Hard -> R.string.rating_hard
        Rating.Good -> R.string.rating_good
        Rating.Easy -> R.string.rating_easy
    }

@Composable
fun intervalText(ms: Long): String {
    val label = intervalLabel(ms)
    val number = NumberFormat.getNumberInstance(LocalConfiguration.current.locales[0]).apply {
        maximumFractionDigits = 1
    }.format(label.value)
    return when (label.unit) {
        IntervalUnit.Minutes -> stringResource(R.string.interval_minutes, label.value.toInt())
        IntervalUnit.Hours -> stringResource(R.string.interval_hours, label.value.toInt())
        IntervalUnit.Days -> stringResource(R.string.interval_days, label.value.toInt())
        IntervalUnit.Months -> stringResource(R.string.interval_months, number)
        IntervalUnit.Years -> stringResource(R.string.interval_years, number)
    }
}

@Composable
private fun Done(reviewed: Int, counts: ReviewCounts?, onContinue: () -> Unit, modifier: Modifier = Modifier) {
    val empty = reviewed == 0 && counts != null && counts.total == 0 && counts.newWaiting == 0 && counts.nextDue == null
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp)
            .testTag(ReviewTags.DONE),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            painterResource(if (empty) R.drawable.ic_auto_stories else R.drawable.ic_task_alt),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(if (empty) R.string.review_empty_title else R.string.review_done_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        val body = MaterialTheme.typography.bodyLarge
        val muted = MaterialTheme.colorScheme.onSurfaceVariant
        if (empty) {
            Text(stringResource(R.string.review_empty_body), style = body, color = muted, textAlign = TextAlign.Center)
        }
        if (reviewed > 0) {
            Text(pluralStringResource(R.plurals.review_done_count, reviewed, reviewed), style = body, color = muted)
        }
        if (counts == null) return@Column
        val now = System.currentTimeMillis()
        if (counts.total > 0) {
            Button(onClick = onContinue, modifier = Modifier.heightIn(min = 56.dp)) {
                Text(stringResource(R.string.review_continue))
            }
        } else {
            counts.nextDue?.takeIf { it > now }?.let { due ->
                Text(stringResource(R.string.review_next, relativeTime(due, now)), style = body, color = muted)
            }
            if (counts.newWaiting > 0) {
                Text(
                    pluralStringResource(R.plurals.review_new_waiting, counts.newWaiting, counts.newWaiting),
                    style = MaterialTheme.typography.bodyMedium,
                    color = muted,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private val previewItem = ReviewItem(
    entry = EntryEntity("1", "en", "de", "linger", "linger", "verweilen, sich aufhalten", "to stay longer than necessary", 0, 0),
    card = null,
    context = ContextWithSource(
        ContextEntity("c", "1", "The smell of coffee lingered in the hallway.", "lingered", 18, 26, null, null, null, 0, 0),
        sourceTitle = "The Hobbit",
    ),
    cloze = Cloze("The smell of coffee ", "lingered", " in the hallway."),
)

private val previewCounts = ReviewCounts(learning = 1, review = 8, new = 3, newWaiting = 12, nextDue = null)

@Preview(name = "Front")
@Composable
private fun FrontPreview() = VerbaTheme(darkTheme = false) {
    ReviewContent(ReviewUiState(loading = false, item = previewItem), previewCounts, ReviewActions())
}

@Preview(name = "Back, E-ink")
@Composable
private fun BackEInkPreview() = VerbaTheme(darkTheme = false, eInk = true) {
    ReviewContent(
        ReviewUiState(
            loading = false,
            item = previewItem,
            revealed = true,
            intervals = mapOf(
                Rating.Again to 60_000L,
                Rating.Hard to 330_000L,
                Rating.Good to 600_000L,
                Rating.Easy to 8 * 86_400_000L,
            ),
            canUndo = true,
        ),
        previewCounts,
        ReviewActions(),
    )
}

@Preview(name = "Done")
@Composable
private fun DonePreview() = VerbaTheme(darkTheme = true) {
    ReviewContent(
        ReviewUiState(loading = false, reviewed = 14),
        ReviewCounts(0, 0, 0, newWaiting = 5, nextDue = System.currentTimeMillis() + 3_600_000),
        ReviewActions(),
    )
}
