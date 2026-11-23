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
) {
    fun shouldSuggestEInk(isEInkDevice: Boolean): Boolean = isEInkDevice && !eInkMode && !eInkPromptAnswered
}
