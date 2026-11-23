package io.github.lenkr0.verba.ui.components

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.lenkr0.verba.R
import io.github.lenkr0.verba.language.Languages

/** Picks a language: recently used ones first, then all languages by name. */
@Composable
fun LanguagePickerDialog(
    title: String,
    selected: String,
    recent: List<String>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val recentTags = remember(recent, selected) { (listOf(selected) + recent).distinct() }
    val others = remember(recentTags, locale) {
        Languages.all.filter { it !in recentTags }.sortedBy { Languages.displayName(it, locale) }
    }
    VerbaAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp).selectableGroup()) {
                item { SectionHeader(stringResource(R.string.language_recent)) }
                items(recentTags) { tag ->
                    RadioRow(Languages.displayName(tag, locale), tag == selected) { onSelect(tag) }
                }
                item { SectionHeader(stringResource(R.string.language_all)) }
                items(others) { tag ->
                    RadioRow(Languages.displayName(tag, locale), tag == selected) { onSelect(tag) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
