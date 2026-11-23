package io.github.lenkr0.verba.capture

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.lenkr0.verba.MainActivity
import io.github.lenkr0.verba.R
import io.github.lenkr0.verba.appContainer
import io.github.lenkr0.verba.ui.theme.VerbaTheme
import io.github.lenkr0.verba.ui.theme.isDarkTheme

/**
 * Opened from the text selection menu of other apps ("Verba"), from share and
 * from the dictionary intents of reading apps. It's transparent: only the
 * bottom sheet shows, over the app the text came from. It never changes the
 * selected text, so the caller always gets RESULT_CANCELED.
 */
class CaptureActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val text = CaptureIntents.text(intent)
        if (text == null) {
            Toast.makeText(this, R.string.capture_no_text, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        // Text selection menus start us for a result, which tells us the caller;
        // otherwise the referrer is the best guess
        val app = CaptureIntents.sourceApp(packageName, callingPackage, referrer?.takeIf { it.scheme == "android-app" }?.host)
        val request = CaptureRequest(text, app, app?.let(::appLabel))
        val container = appContainer

        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle()
            VerbaTheme(darkTheme = isDarkTheme(settings.themeMode), eInk = settings.eInkMode) {
                val viewModel = viewModel { CaptureViewModel(request, container.settings, container.vocabulary, container.dictionaryLookup) }
                CaptureSheet(
                    viewModel = viewModel,
                    onDismiss = ::finish,
                    onSaved = { result, lemma ->
                        val message = if (result.addedToExisting) {
                            getString(R.string.capture_saved_existing, lemma)
                        } else {
                            getString(R.string.capture_saved, lemma)
                        }
                        Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
                        finish()
                    },
                    onGetDictionaries = {
                        startActivity(
                            Intent(this, MainActivity::class.java)
                                .putExtra(MainActivity.EXTRA_OPEN, MainActivity.OPEN_DICTIONARIES)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
                        )
                        finish()
                    },
                )
            }
        }
    }

    private fun appLabel(packageName: String): String? = try {
        val info = packageManager.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
        packageManager.getApplicationLabel(info).toString()
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }
}
