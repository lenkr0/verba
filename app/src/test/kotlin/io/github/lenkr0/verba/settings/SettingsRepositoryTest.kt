package io.github.lenkr0.verba.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsRepositoryTest {
    private class InMemoryStore : KeyValueStore {
        val values = mutableMapOf<String, Any>()
        override fun string(key: String) = values[key] as String?
        override fun boolean(key: String) = values[key] as Boolean?
        override fun write(values: Map<String, Any>) = this.values.putAll(values)
    }

    @Test
    fun defaultsWhenNothingIsStored() {
        val settings = SettingsRepository(InMemoryStore()).settings.value
        assertEquals(AppSettings(), settings)
    }

    @Test
    fun themeModeIsStoredAndReloaded() {
        val store = InMemoryStore()
        SettingsRepository(store).setThemeMode(ThemeMode.Dark)
        assertEquals("dark", store.values["theme_mode"])
        assertEquals(ThemeMode.Dark, SettingsRepository(store).settings.value.themeMode)
    }

    @Test
    fun dailyLimitsAreStoredWithinBounds() {
        val store = InMemoryStore()
        SettingsRepository(store).apply {
            setNewPerDay(5)
            setReviewsPerDay(-3)
        }
        val reloaded = SettingsRepository(store).settings.value
        assertEquals(5, reloaded.newPerDay)
        assertEquals(0, reloaded.reviewsPerDay)
        SettingsRepository(store).setReviewsPerDay(1_000_000)
        assertEquals(AppSettings.MAX_PER_DAY, SettingsRepository(store).settings.value.reviewsPerDay)
    }

    @Test
    fun unknownStoredThemeFallsBackToSystem() {
        assertEquals(ThemeMode.System, ThemeMode.fromStorage("sepia"))
        assertEquals(ThemeMode.System, ThemeMode.fromStorage(null))
    }

    @Test
    fun turningOnEInkAnswersTheSuggestion() {
        val repository = SettingsRepository(InMemoryStore())
        repository.setEInkMode(true)
        val settings = repository.settings.value
        assertTrue(settings.eInkMode)
        assertTrue(settings.eInkPromptAnswered)
    }

    @Test
    fun targetLanguageDefaultsToTheDeviceLanguageUntilChosen() {
        val store = InMemoryStore()
        assertEquals("de", SettingsRepository(store, defaultTargetLanguage = "de").settings.value.targetLanguage)
        SettingsRepository(store, defaultTargetLanguage = "de").setTargetLanguage("en")
        assertEquals("en", SettingsRepository(store, defaultTargetLanguage = "de").settings.value.targetLanguage)
    }

    @Test
    fun recentLanguagesKeepTheLastFiveMostRecentFirst() {
        val repository = SettingsRepository(InMemoryStore())
        listOf("en", "zh", "ja", "fr", "es", "ko", "zh").forEach(repository::noteLanguageUsed)
        assertEquals(listOf("zh", "ko", "es", "fr", "ja"), repository.settings.value.recentLanguages)
    }

    @Test
    fun suggestionOnlyOnEInkDevicesAndOnlyOnce() {
        val repository = SettingsRepository(InMemoryStore())
        assertFalse(repository.settings.value.shouldSuggestEInk(isEInkDevice = false))
        assertTrue(repository.settings.value.shouldSuggestEInk(isEInkDevice = true))
        repository.dismissEInkPrompt()
        assertFalse(repository.settings.value.shouldSuggestEInk(isEInkDevice = true))
        assertFalse(repository.settings.value.eInkMode)
    }
}
