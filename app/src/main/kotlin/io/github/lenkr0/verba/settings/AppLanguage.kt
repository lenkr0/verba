package io.github.lenkr0.verba.settings

import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList
import java.util.Locale

/** Per-app language via the platform LocaleManager (Android 13+), no AppCompat needed. */
object AppLanguage {
    /** Languages with a complete translation; keep in sync with res/values-xx. */
    val supported = listOf("en", "de")

    /** The chosen language tag, or null when the app follows the system language. */
    fun current(context: Context): String? {
        val locales = context.getSystemService(LocaleManager::class.java).applicationLocales
        return if (locales.isEmpty) null else locales[0].language
    }

    /** Changing it recreates the activities in the new language. */
    fun set(context: Context, languageTag: String?) {
        context.getSystemService(LocaleManager::class.java).applicationLocales =
            if (languageTag == null) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(languageTag)
    }

    /** The language's own name, e.g. "Deutsch" for "de". */
    fun displayName(languageTag: String): String {
        val locale = Locale.forLanguageTag(languageTag)
        return locale.getDisplayLanguage(locale).replaceFirstChar { it.titlecase(locale) }
    }
}
