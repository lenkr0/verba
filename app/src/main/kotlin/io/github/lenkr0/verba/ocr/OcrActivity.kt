package io.github.lenkr0.verba.ocr

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.lenkr0.verba.appContainer
import io.github.lenkr0.verba.capture.CaptureActivity
import io.github.lenkr0.verba.ui.theme.VerbaTheme
import io.github.lenkr0.verba.ui.theme.isDarkTheme
import io.github.lenkr0.verba.ui.theme.systemBarsFollow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

/**
 * Text recognition on an image: shared from another app (a screenshot, a
 * page from a manga reader), picked in Verba, or captured with the quick
 * settings tile. Tapping a text opens the capture sheet with it as the
 * sentence. Opens in the task of the app the image came from.
 */
class OcrActivity : ComponentActivity() {
    companion object {
        /** Internal: show the screen capture the tile just took. */
        const val ACTION_READ_SCREEN = "io.github.lenkr0.verba.action.READ_SCREEN"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = appContainer
        val referrerApp = referrer?.takeIf { it.scheme == "android-app" }?.host
        val sourceApp = referrerApp?.takeIf { !it.startsWith(packageName) }
        val loader = loaderFor(intent, ownCall = referrerApp == packageName)

        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle()
            val dark = isDarkTheme(settings.themeMode)
            LaunchedEffect(dark) { systemBarsFollow(dark) }
            VerbaTheme(darkTheme = dark, eInk = settings.eInkMode) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val viewModel = viewModel { OcrViewModel(container.ocrModels, loader, container.scope) }
                    OcrScreen(viewModel, onClose = ::finish, onBlock = { openCapture(it.text, sourceApp) })
                }
            }
        }
    }

    private fun loaderFor(intent: Intent, ownCall: Boolean): suspend () -> Bitmap? {
        // Only the application context goes into the loader: it outlives this activity in the view model
        val app = applicationContext
        return when (intent.action) {
            Intent.ACTION_SEND -> {
                val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                val resolver = app.contentResolver
                return { uri?.let { decodeImage(ImageDecoder.createSource(resolver, it)) } }
            }
            ACTION_READ_SCREEN -> if (ownCall) {
                val capture = ScreenCaptures.take()
                return { capture }
            } else {
                { null }
            }
            else -> { { null } }
        }
    }

    private fun openCapture(text: String, sourceApp: String?) {
        startActivity(
            Intent(this, CaptureActivity::class.java)
                .setAction(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, text)
                .putExtra(CaptureActivity.EXTRA_SOURCE_APP, sourceApp),
        )
    }
}

/** Larger images are scaled down: enough for any text, and bounded memory. */
private const val MAX_SIDE = 4096

private suspend fun decodeImage(source: ImageDecoder.Source): Bitmap = withContext(Dispatchers.IO) {
    ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
        // Software memory: the native code reads the pixels
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        val long = max(info.size.width, info.size.height)
        if (long > MAX_SIDE) {
            val f = MAX_SIDE.toFloat() / long
            decoder.setTargetSize((info.size.width * f).toInt(), (info.size.height * f).toInt())
        }
    }
}
