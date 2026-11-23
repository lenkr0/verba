package io.github.lenkr0.verba.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lenkr0.verba.BuildConfig
import io.github.lenkr0.verba.R
import io.github.lenkr0.verba.settings.AppLanguage
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

private fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, R.string.error_no_browser, Toast.LENGTH_SHORT).show()
    }
}
