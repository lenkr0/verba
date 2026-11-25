package io.github.lenkr0.verba.ui.settings

import android.app.StatusBarManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.graphics.drawable.Icon as SystemIcon
import android.text.format.Formatter
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lenkr0.verba.BuildConfig
import io.github.lenkr0.verba.R
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import io.github.lenkr0.verba.backup.Transfers
import io.github.lenkr0.verba.ocr.OcrModels
import java.time.LocalDate
import io.github.lenkr0.verba.ocr.ReadScreenTileService
import io.github.lenkr0.verba.settings.AppLanguage
import io.github.lenkr0.verba.settings.AppSettings
import io.github.lenkr0.verba.settings.SettingsRepository
import io.github.lenkr0.verba.settings.ThemeMode
import io.github.lenkr0.verba.language.Languages
import io.github.lenkr0.verba.ui.components.ClickRow
import io.github.lenkr0.verba.ui.components.InfoRow
import io.github.lenkr0.verba.ui.components.LanguagePickerDialog
import io.github.lenkr0.verba.ui.components.RadioRow
import io.github.lenkr0.verba.ui.components.SectionHeader
import io.github.lenkr0.verba.ui.components.SwitchRow
import io.github.lenkr0.verba.ui.components.VerbaAlertDialog

private const val SOURCE_URL = "https://github.com/lenkr0/verba"
private const val LICENSE_URL = "https://www.gnu.org/licenses/gpl-3.0.html"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    repository: SettingsRepository,
    ocrModels: OcrModels,
    transfers: Transfers,
    suggestedTargets: List<String>,
    onOpenDictionaries: () -> Unit,
    onBack: () -> Unit,
) {
    val settings by repository.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // A language change recreates the activity, so reading it once is enough
    val language = remember { AppLanguage.current(context) }
    var showLanguageDialog by rememberSaveable { mutableStateOf(false) }
    var showTargetDialog by rememberSaveable { mutableStateOf(false) }
    var editLimit by rememberSaveable { mutableStateOf<Limit?>(null) }
    var confirmDeleteModels by rememberSaveable { mutableStateOf(false) }
    val modelsInstalled by ocrModels.installed.collectAsStateWithLifecycle()
    val modelProgress by ocrModels.progress.collectAsStateWithLifecycle()
    val modelSize = remember { Formatter.formatShortFileSize(context, OcrModels.PACK.size) }
    val today = remember { LocalDate.now().toString() }
    // The system file picker: the user chooses the place, no storage permission
    val backupFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(transfers::backup)
    }
    val restoreFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(transfers::restore)
    }
    val csvFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let(transfers::exportCsv)
    }
    val ankiFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/apkg")) { uri ->
        uri?.let(transfers::exportAnki)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.action_settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            SectionHeader(stringResource(R.string.settings_section_appearance))
            Column(Modifier.selectableGroup()) {
                ThemeMode.entries.forEach { mode ->
                    RadioRow(
                        label = stringResource(mode.label),
                        selected = settings.themeMode == mode,
                        onClick = { repository.setThemeMode(mode) },
                    )
                }
            }
            SwitchRow(
                title = stringResource(R.string.settings_eink),
                summary = stringResource(R.string.settings_eink_summary),
                checked = settings.eInkMode,
                onCheckedChange = repository::setEInkMode,
            )

            SectionHeader(stringResource(R.string.settings_section_language))
            ClickRow(
                title = stringResource(R.string.settings_dictionaries),
                summary = stringResource(R.string.settings_dictionaries_summary),
                onClick = onOpenDictionaries,
            )
            ClickRow(
                title = stringResource(R.string.settings_target_language),
                summary = Languages.displayName(settings.targetLanguage),
                onClick = { showTargetDialog = true },
            )
            ClickRow(
                title = stringResource(R.string.settings_language),
                summary = language?.let(AppLanguage::displayName)
                    ?: stringResource(R.string.settings_language_system),
                onClick = { showLanguageDialog = true },
            )

            SectionHeader(stringResource(R.string.settings_section_ocr))
            ClickRow(
                title = stringResource(R.string.settings_ocr_models),
                summary = when {
                    modelProgress != null -> stringResource(R.string.ocr_models_downloading, ((modelProgress ?: 0f) * 100).toInt())
                    modelsInstalled -> stringResource(R.string.settings_ocr_installed, modelSize)
                    else -> stringResource(R.string.settings_ocr_not_installed, modelSize)
                },
                onClick = {
                    when {
                        modelProgress != null -> Unit
                        modelsInstalled -> confirmDeleteModels = true
                        else -> ocrModels.download()
                    }
                },
            )
            ClickRow(
                title = stringResource(R.string.settings_ocr_tile),
                summary = stringResource(R.string.settings_ocr_tile_summary),
                onClick = { addReadScreenTile(context) },
            )

            SectionHeader(stringResource(R.string.settings_section_review))
            ClickRow(
                title = stringResource(R.string.settings_new_per_day),
                summary = settings.newPerDay.toString(),
                onClick = { editLimit = Limit.New },
            )
            ClickRow(
                title = stringResource(R.string.settings_reviews_per_day),
                summary = settings.reviewsPerDay.toString(),
                onClick = { editLimit = Limit.Reviews },
            )

            SectionHeader(stringResource(R.string.settings_section_backup))
            ClickRow(
                title = stringResource(R.string.settings_backup),
                summary = stringResource(R.string.settings_backup_summary),
                onClick = { backupFile.launch("verba-backup-$today.json") },
            )
            ClickRow(
                title = stringResource(R.string.settings_restore),
                summary = stringResource(R.string.settings_restore_summary),
                onClick = { restoreFile.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) },
            )
            ClickRow(
                title = stringResource(R.string.settings_export_csv),
                summary = stringResource(R.string.settings_export_csv_summary),
                onClick = { csvFile.launch("verba-words-$today.csv") },
            )
            ClickRow(
                title = stringResource(R.string.settings_export_anki),
                summary = stringResource(R.string.settings_export_anki_summary),
                onClick = { ankiFile.launch("verba-$today.apkg") },
            )

            SectionHeader(stringResource(R.string.settings_section_about))
            InfoRow(stringResource(R.string.settings_version), BuildConfig.VERSION_NAME)
            ClickRow(
                title = stringResource(R.string.settings_source),
                summary = SOURCE_URL.removePrefix("https://"),
                external = true,
                onClick = { openUrl(context, SOURCE_URL) },
            )
            ClickRow(
                title = stringResource(R.string.settings_license),
                summary = stringResource(R.string.settings_license_summary),
                external = true,
                onClick = { openUrl(context, LICENSE_URL) },
            )
            InfoRow(stringResource(R.string.settings_privacy), stringResource(R.string.settings_privacy_summary))
        }
    }

    if (showTargetDialog) {
        LanguagePickerDialog(
            title = stringResource(R.string.settings_target_language),
            selected = settings.targetLanguage,
            recent = suggestedTargets,
            onSelect = { tag ->
                showTargetDialog = false
                repository.setTargetLanguage(tag)
            },
            onDismiss = { showTargetDialog = false },
        )
    }

    if (confirmDeleteModels) {
        VerbaAlertDialog(
            onDismissRequest = { confirmDeleteModels = false },
            title = { Text(stringResource(R.string.settings_ocr_delete_title)) },
            text = { Text(stringResource(R.string.settings_ocr_delete_body)) },
            confirmButton = {
                TextButton(onClick = { confirmDeleteModels = false; ocrModels.delete() }) {
                    Text(stringResource(R.string.dictionaries_delete))
                }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteModels = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

    editLimit?.let { limit ->
        NumberDialog(
            title = stringResource(limit.title),
            hint = stringResource(limit.hint),
            value = if (limit == Limit.New) settings.newPerDay else settings.reviewsPerDay,
            onConfirm = { value ->
                editLimit = null
                if (limit == Limit.New) repository.setNewPerDay(value) else repository.setReviewsPerDay(value)
            },
            onDismiss = { editLimit = null },
        )
    }

    if (showLanguageDialog) {
        LanguageDialog(
            current = language,
            onSelect = { tag ->
                showLanguageDialog = false
                AppLanguage.set(context, tag)
            },
            onDismiss = { showLanguageDialog = false },
        )
    }
}

private enum class Limit(@StringRes val title: Int, @StringRes val hint: Int) {
    New(R.string.settings_new_per_day, R.string.settings_new_per_day_hint),
    Reviews(R.string.settings_reviews_per_day, R.string.settings_reviews_per_day_hint),
}

@Composable
private fun NumberDialog(title: String, hint: String, value: Int, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(value.toString()) }
    val number = text.toIntOrNull()?.takeIf { it in 0..AppSettings.MAX_PER_DAY }
    VerbaAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(hint)
                OutlinedTextField(
                    value = text,
                    onValueChange = { new -> text = new.filter(Char::isDigit).take(4) },
                    singleLine = true,
                    isError = number == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { number?.let(onConfirm) }, enabled = number != null) {
                Text(stringResource(R.string.action_ok))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

private val ThemeMode.label: Int
    @StringRes get() = when (this) {
        ThemeMode.System -> R.string.settings_theme_system
        ThemeMode.Light -> R.string.settings_theme_light
        ThemeMode.Dark -> R.string.settings_theme_dark
    }

@Composable
private fun LanguageDialog(current: String?, onSelect: (String?) -> Unit, onDismiss: () -> Unit) {
    VerbaAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_language)) },
        text = {
            Column(Modifier.selectableGroup()) {
                RadioRow(stringResource(R.string.settings_language_system), current == null) { onSelect(null) }
                AppLanguage.supported.forEach { tag ->
                    RadioRow(AppLanguage.displayName(tag), current == tag) { onSelect(tag) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Asks Android to add the "Read screen" tile to Quick Settings (Android shows its own dialog). */
private fun addReadScreenTile(context: Context) {
    context.getSystemService(StatusBarManager::class.java).requestAddTileService(
        ComponentName(context, ReadScreenTileService::class.java),
        context.getString(R.string.tile_read_screen),
        SystemIcon.createWithResource(context, R.drawable.ic_document_scanner),
        context.mainExecutor,
    ) {}
}

private fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, R.string.error_no_browser, Toast.LENGTH_SHORT).show()
    }
}
