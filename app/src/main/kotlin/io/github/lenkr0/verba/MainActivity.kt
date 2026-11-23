package io.github.lenkr0.verba

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lenkr0.verba.ui.VerbaNavigation
import io.github.lenkr0.verba.ui.components.VerbaAlertDialog
import io.github.lenkr0.verba.ui.theme.VerbaTheme
import io.github.lenkr0.verba.ui.theme.isDarkTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = appContainer

        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle()
            val dark = isDarkTheme(settings.themeMode)

            // The app's theme can differ from the system's, so the status and
            // navigation bar icons follow the app, not the system
            LaunchedEffect(dark) {
                val style = if (dark) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }

            VerbaTheme(darkTheme = dark, eInk = settings.eInkMode) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    VerbaNavigation(container)
                }
                if (settings.shouldSuggestEInk(container.isEInkDevice)) {
                    EInkSuggestionDialog(
                        onEnable = { container.settings.setEInkMode(true) },
                        onDismiss = container.settings::dismissEInkPrompt,
                    )
                }
            }
        }
    }
}

@Composable
private fun EInkSuggestionDialog(onEnable: () -> Unit, onDismiss: () -> Unit) {
    VerbaAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.eink_prompt_title)) },
        text = { Text(stringResource(R.string.eink_prompt_body)) },
        confirmButton = { TextButton(onClick = onEnable) { Text(stringResource(R.string.eink_prompt_enable)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.eink_prompt_dismiss)) } },
    )
}
