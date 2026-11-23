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

class SettingsRepository(private val store: KeyValueStore) {
    private val state = MutableStateFlow(load())
    val settings: StateFlow<AppSettings> = state.asStateFlow()

    fun setThemeMode(mode: ThemeMode) = save(KEY_THEME to mode.storageKey)

    fun setEInkMode(enabled: Boolean) = save(KEY_EINK to enabled, KEY_EINK_PROMPT_ANSWERED to true)

    fun dismissEInkPrompt() = save(KEY_EINK_PROMPT_ANSWERED to true)

    private fun save(vararg values: Pair<String, Any>) {
        store.write(values.toMap())
        state.value = load()
    }

    private fun load() = AppSettings(
        themeMode = ThemeMode.fromStorage(store.string(KEY_THEME)),
        eInkMode = store.boolean(KEY_EINK) ?: false,
        eInkPromptAnswered = store.boolean(KEY_EINK_PROMPT_ANSWERED) ?: false,
    )

    private companion object {
        const val KEY_THEME = "theme_mode"
        const val KEY_EINK = "eink_mode"
        const val KEY_EINK_PROMPT_ANSWERED = "eink_prompt_answered"
    }
}
