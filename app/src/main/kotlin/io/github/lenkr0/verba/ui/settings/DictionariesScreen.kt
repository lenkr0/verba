package io.github.lenkr0.verba.ui.settings

import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lenkr0.verba.R
import io.github.lenkr0.verba.dictionary.CatalogState
import io.github.lenkr0.verba.dictionary.Dictionaries
import io.github.lenkr0.verba.dictionary.DictionaryError
import io.github.lenkr0.verba.dictionary.PackSource
import io.github.lenkr0.verba.language.Languages
import io.github.lenkr0.verba.ui.components.ClickRow
import io.github.lenkr0.verba.ui.components.SectionHeader
import io.github.lenkr0.verba.ui.components.VerbaAlertDialog
import java.text.NumberFormat

/** Test tags for the Dictionaries screen. */
object DictionaryTags {
    fun download(id: String) = "dictionary_download_$id"
    fun delete(id: String) = "dictionary_delete_$id"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DictionariesScreen(dictionaries: Dictionaries, targetLanguage: String, onBack: () -> Unit) {
    val installed by dictionaries.store.installed.collectAsStateWithLifecycle()
    val catalog by dictionaries.catalog.collectAsStateWithLifecycle()
    val progress by dictionaries.progress.collectAsStateWithLifecycle()
    val error by dictionaries.error.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var confirmDelete by rememberSaveable { mutableStateOf<String?>(null) }
    var showSources by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        dictionaries.store.refresh()
        dictionaries.loadCatalog()
    }
    val errorText = error?.let {
        stringResource(
            when (it) {
                DictionaryError.Download -> R.string.dictionaries_error_download
                DictionaryError.Checksum -> R.string.dictionaries_error_checksum
                DictionaryError.InvalidFile -> R.string.dictionaries_error_invalid
            },
        )
    }
    LaunchedEffect(errorText) {
        if (errorText != null) {
            snackbar.showSnackbar(errorText)
            dictionaries.clearError()
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) dictionaries.import { context.contentResolver.openInputStream(uri) }
    }

    val packs = (catalog as? CatalogState.Loaded)?.catalog?.packs.orEmpty()
    val installedIds = installed.map { it.id }.toSet()
    // Packs into your language first, then into English, then the rest
    val available = packs.filter { it.id !in installedIds }
        .sortedWith(compareBy({ it.targetLanguage != targetLanguage }, { it.targetLanguage != "en" }, { it.id }))

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_dictionaries)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.action_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
            item {
                Text(
                    stringResource(R.string.dictionaries_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            if (installed.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.dictionaries_installed)) }
                items(installed, key = { "installed-" + it.id }) { info ->
                    val newer = packs.firstOrNull { it.id == info.id && it.version > info.version }
                    PackRow(
                        title = pairTitle(info.sourceLanguage, info.targetLanguage),
                        subtitle = stringResource(
                            R.string.dictionaries_installed_info,
                            info.version,
                            Formatter.formatShortFileSize(context, info.sizeBytes),
                        ),
                        progress = progress[info.id],
                        onClick = { showSources = info.id },
                    ) {
                        if (newer != null) {
                            TextButton(onClick = { dictionaries.download(newer) }) {
                                Text(stringResource(R.string.dictionaries_update))
                            }
                        }
                        TextButton(
                            onClick = { confirmDelete = info.id },
                            modifier = Modifier.testTag(DictionaryTags.delete(info.id)),
                        ) { Text(stringResource(R.string.dictionaries_delete)) }
                    }
                }
            }
            item { SectionHeader(stringResource(R.string.dictionaries_available)) }
            when (catalog) {
                CatalogState.NotLoaded, CatalogState.Loading -> item {
                    Column(Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.dictionaries_loading), style = MaterialTheme.typography.bodyMedium)
                        LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
                    }
                }
                CatalogState.Failed -> item {
                    Column(Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.dictionaries_failed), style = MaterialTheme.typography.bodyMedium)
                        OutlinedButton(onClick = { dictionaries.loadCatalog(force = true) }, modifier = Modifier.padding(top = 8.dp)) {
                            Text(stringResource(R.string.action_retry))
                        }
                    }
                }
                is CatalogState.Loaded -> items(available, key = { "available-" + it.id }) { pack ->
                    val running = progress[pack.id]
                    PackRow(
                        title = pairTitle(pack.sourceLanguage, pack.targetLanguage),
                        subtitle = stringResource(
                            R.string.dictionaries_download_size,
                            Formatter.formatShortFileSize(context, pack.size),
                            NumberFormat.getIntegerInstance().format(pack.headwords),
                        ),
                        progress = running,
                        onClick = { showSources = pack.id },
                    ) {
                        if (running == null) {
                            OutlinedButton(
                                onClick = { dictionaries.download(pack) },
                                modifier = Modifier.testTag(DictionaryTags.download(pack.id)),
                            ) { Text(stringResource(R.string.dictionaries_download)) }
                        } else {
                            TextButton(onClick = { dictionaries.cancel(pack.id) }) {
                                Text(stringResource(R.string.action_cancel))
                            }
                        }
                    }
                }
            }
            item {
                ClickRow(
                    title = stringResource(R.string.dictionaries_import),
                    summary = stringResource(R.string.dictionaries_import_summary),
                    onClick = { importer.launch(arrayOf("*/*")) },
                )
            }
        }
    }

    confirmDelete?.let { id ->
        val info = installed.firstOrNull { it.id == id }
        if (info != null) {
            VerbaAlertDialog(
                onDismissRequest = { confirmDelete = null },
                title = { Text(stringResource(R.string.dictionaries_delete_title, pairTitle(info.sourceLanguage, info.targetLanguage))) },
                text = { Text(stringResource(R.string.dictionaries_delete_body)) },
                confirmButton = {
                    TextButton(onClick = { confirmDelete = null; dictionaries.delete(id) }) {
                        Text(stringResource(R.string.dictionaries_delete))
                    }
                },
                dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text(stringResource(R.string.action_cancel)) } },
            )
        } else {
            confirmDelete = null
        }
    }

    showSources?.let { id ->
        val sources = installed.firstOrNull { it.id == id }?.sources ?: packs.firstOrNull { it.id == id }?.sources.orEmpty()
        SourcesDialog(sources, onDismiss = { showSources = null })
    }
}

@Composable
private fun pairTitle(source: String, target: String) =
    "${Languages.displayName(source)} → ${Languages.displayName(target)}"

@Composable
private fun PackRow(
    title: String,
    subtitle: String,
    progress: Float?,
    onClick: () -> Unit,
    actions: @Composable () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                actions()
            }
        }
        if (progress != null) {
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        }
    }
}

/** What a pack is made of; CC BY-SA and GPL require this attribution. */
@Composable
private fun SourcesDialog(sources: List<PackSource>, onDismiss: () -> Unit) {
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    VerbaAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dictionaries_sources)) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(sources) { source ->
                    Column {
                        Text(source.name, style = MaterialTheme.typography.titleSmall)
                        if (source.attribution.isNotBlank()) {
                            Text(source.attribution, style = MaterialTheme.typography.bodySmall)
                        }
                        Row {
                            Text(
                                source.license,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable { runCatching { uriHandler.openUri(source.licenseUrl) } },
                            )
                            Text(" · ", style = MaterialTheme.typography.bodySmall)
                            Text(
                                source.url.removePrefix("https://"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .width(220.dp)
                                    .clickable { runCatching { uriHandler.openUri(source.url) } },
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}
