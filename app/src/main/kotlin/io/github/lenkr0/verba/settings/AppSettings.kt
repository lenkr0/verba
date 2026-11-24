package io.github.lenkr0.verba.settings

/** [storageKey] is what gets saved, so enum constants can be renamed safely. */
enum class ThemeMode(val storageKey: String) {
    System("system"),
    Light("light"),
    Dark("dark"),
    ;

    companion object {
        fun fromStorage(value: String?): ThemeMode = entries.firstOrNull { it.storageKey == value } ?: System
    }
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.System,
    val eInkMode: Boolean = false,
    /** The user has answered the "E-ink display detected" question (either way). */
    val eInkPromptAnswered: Boolean = false,
    /** Language words are translated into (BCP 47); defaults to the device language. */
    val targetLanguage: String = "en",
    /** Languages of recent lookups, most recent first. */
    val recentLanguages: List<String> = emptyList(),
    /** Words reviewed for the first time per day. */
    val newPerDay: Int = DEFAULT_NEW_PER_DAY,
    /** Reviews of learned words per day; the rest wait for the next day. */
    val reviewsPerDay: Int = DEFAULT_REVIEWS_PER_DAY,
) {
    fun shouldSuggestEInk(isEInkDevice: Boolean): Boolean = isEInkDevice && !eInkMode && !eInkPromptAnswered

    companion object {
        const val DEFAULT_NEW_PER_DAY = 20
        const val DEFAULT_REVIEWS_PER_DAY = 200
        const val MAX_PER_DAY = 9999
    }
}
