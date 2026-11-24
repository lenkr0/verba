package io.github.lenkr0.verba.settings

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Minimal key-value storage, so the repository can be unit-tested without Android. */
interface KeyValueStore {
    fun string(key: String): String?
    fun boolean(key: String): Boolean?
    fun write(values: Map<String, Any>)
}

class SharedPreferencesStore(private val prefs: SharedPreferences) : KeyValueStore {
    override fun string(key: String): String? = prefs.getString(key, null)

    override fun boolean(key: String): Boolean? = if (prefs.contains(key)) prefs.getBoolean(key, false) else null

    override fun write(values: Map<String, Any>) = prefs.edit {
        values.forEach { (key, value) ->
            when (value) {
                is String -> putString(key, value)
                is Boolean -> putBoolean(key, value)
                else -> error("Unsupported value type for $key: ${value::class}")
            }
        }
    }
}

class SettingsRepository(
    private val store: KeyValueStore,
    /** Used until the user picks a target language. */
    private val defaultTargetLanguage: String = "en",
) {
    private val state = MutableStateFlow(load())
    val settings: StateFlow<AppSettings> = state.asStateFlow()

    fun setThemeMode(mode: ThemeMode) = save(KEY_THEME to mode.storageKey)

    fun setEInkMode(enabled: Boolean) = save(KEY_EINK to enabled, KEY_EINK_PROMPT_ANSWERED to true)

    fun dismissEInkPrompt() = save(KEY_EINK_PROMPT_ANSWERED to true)

    fun setTargetLanguage(language: String) = save(KEY_TARGET_LANGUAGE to language)

    fun setNewPerDay(count: Int) = save(KEY_NEW_PER_DAY to count.coerceIn(0, AppSettings.MAX_PER_DAY).toString())

    fun setReviewsPerDay(count: Int) = save(KEY_REVIEWS_PER_DAY to count.coerceIn(0, AppSettings.MAX_PER_DAY).toString())

    /** Moves [language] to the front of the recent languages (keeps at most [MAX_RECENT]). */
    fun noteLanguageUsed(language: String) {
        val recent = (listOf(language) + state.value.recentLanguages.filter { it != language }).take(MAX_RECENT)
        save(KEY_RECENT_LANGUAGES to recent.joinToString(","))
    }

    private fun save(vararg values: Pair<String, Any>) {
        store.write(values.toMap())
        state.value = load()
    }

    private fun load() = AppSettings(
        themeMode = ThemeMode.fromStorage(store.string(KEY_THEME)),
        eInkMode = store.boolean(KEY_EINK) ?: false,
        eInkPromptAnswered = store.boolean(KEY_EINK_PROMPT_ANSWERED) ?: false,
        targetLanguage = store.string(KEY_TARGET_LANGUAGE) ?: defaultTargetLanguage,
        recentLanguages = store.string(KEY_RECENT_LANGUAGES)?.split(',')?.filter { it.isNotBlank() }.orEmpty(),
        newPerDay = store.string(KEY_NEW_PER_DAY)?.toIntOrNull() ?: AppSettings.DEFAULT_NEW_PER_DAY,
        reviewsPerDay = store.string(KEY_REVIEWS_PER_DAY)?.toIntOrNull() ?: AppSettings.DEFAULT_REVIEWS_PER_DAY,
    )

    private companion object {
        const val KEY_THEME = "theme_mode"
        const val KEY_EINK = "eink_mode"
        const val KEY_EINK_PROMPT_ANSWERED = "eink_prompt_answered"
        const val KEY_TARGET_LANGUAGE = "target_language"
        const val KEY_RECENT_LANGUAGES = "recent_languages"
        const val KEY_NEW_PER_DAY = "new_per_day"
        const val KEY_REVIEWS_PER_DAY = "reviews_per_day"
        const val MAX_RECENT = 5
    }
}
