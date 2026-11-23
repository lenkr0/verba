package io.github.lenkr0.verba.capture

import android.app.SearchManager
import android.content.Intent

/** The text another app handed to Verba, and that app if Android told us. */
data class CaptureRequest(
    val text: String,
    val sourceApp: String? = null,
    val sourceAppLabel: String? = null,
)

object CaptureIntents {
    /** ColorDict's lookup action; readers such as Moon+ Reader and KOReader send it. */
    const val ACTION_COLORDICT = "colordict.intent.action.SEARCH"
    const val EXTRA_COLORDICT_QUERY = "EXTRA_QUERY"

    /** Aard 2's lookup action, also offered by some readers. */
    const val ACTION_AARD2 = "aard2.lookup"

    /** Longer selections are cut; nobody looks up words in a whole chapter. */
    const val MAX_LENGTH = 3000

    /** Packages that hand text on rather than being its source. */
    private val relays = setOf("android", "com.android.intentresolver", "com.android.systemui")

    fun text(intent: Intent): String? {
        val raw: CharSequence? = when (intent.action) {
            Intent.ACTION_PROCESS_TEXT -> intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)
            Intent.ACTION_SEND -> intent.getCharSequenceExtra(Intent.EXTRA_TEXT)
            ACTION_COLORDICT -> intent.getStringExtra(EXTRA_COLORDICT_QUERY)
            ACTION_AARD2 -> intent.getStringExtra(SearchManager.QUERY)
            else -> null
        }
        return raw?.toString()?.trim()?.take(MAX_LENGTH)?.takeIf { it.isNotEmpty() }
    }

    /** The first candidate that is a real app other than Verba itself. */
    fun sourceApp(ownPackage: String, vararg candidates: String?): String? =
        candidates.firstOrNull { it != null && it.isNotBlank() && it !in relays && !it.startsWith(ownPackage) }
}
