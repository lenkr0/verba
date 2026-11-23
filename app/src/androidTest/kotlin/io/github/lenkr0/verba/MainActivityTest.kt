package io.github.lenkr0.verba

import android.content.Context
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.lenkr0.verba.data.Capture
import io.github.lenkr0.verba.settings.ThemeMode
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun text(id: Int) = context.getString(id)

    // Reset before the activity starts (the device may hold data from manual
    // use or other tests) and again afterwards
    @Before
    @After
    fun reset() {
        context.appContainer.database.clearAllTables()
        context.appContainer.settings.apply {
            setEInkMode(false)
            setThemeMode(ThemeMode.System)
        }
    }

    private fun launch() = ActivityScenario.launch(MainActivity::class.java)

    /** Room loads the list on a background thread that Compose's idling doesn't track. */
    private fun waitForText(value: String) =
        compose.waitUntil(timeoutMillis = 5_000) { compose.onAllNodesWithText(value).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun savedWordsAppearInTheList() {
        runBlocking {
            context.appContainer.vocabulary.save(
                Capture(
                    language = "en", targetLanguage = "de", wordForm = "running", lemma = "run",
                    translation = "laufen", definition = "", sentence = "She was running late.",
                    wordStart = 8, wordEnd = 15, sourceTitle = "The Hobbit", sourceApp = null, sourceAppLabel = null,
                ),
            )
        }
        launch()
        waitForText("run")
        compose.onNodeWithText("laufen").assertExists()
        compose.onNodeWithText("The Hobbit").assertExists()
    }

    @Test
    fun homeShowsEmptyStateAndOpensSettings() {
        launch()
        waitForText(text(R.string.home_empty_title))
        compose.onNodeWithContentDescription(text(R.string.action_settings)).performClick()
        compose.onNodeWithText(text(R.string.settings_eink)).assertExists()
    }

    @Test
    fun settingsToggleEInkAndTheme() {
        launch()
        compose.onNodeWithContentDescription(text(R.string.action_settings)).performClick()

        val eInkSwitch = compose.onNode(isToggleable())
        eInkSwitch.assertIsOff()
        compose.onNodeWithText(text(R.string.settings_eink)).performClick()
        eInkSwitch.assertIsOn()

        compose.onNodeWithText(text(R.string.settings_theme_dark)).performClick()
        compose.onNodeWithText(text(R.string.settings_theme_dark)).assertIsSelected()
        check(context.appContainer.settings.settings.value.themeMode == ThemeMode.Dark)
    }

    @Test
    fun backReturnsFromSettingsToHome() {
        launch()
        compose.onNodeWithContentDescription(text(R.string.action_settings)).performClick()
        compose.onNodeWithText(text(R.string.settings_eink)).assertExists()
        Espresso.pressBack()
        waitForText(text(R.string.home_empty_title))
    }
}
