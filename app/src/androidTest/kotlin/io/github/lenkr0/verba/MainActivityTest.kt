package io.github.lenkr0.verba

import android.content.Context
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.lenkr0.verba.settings.ThemeMode
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun text(id: Int) = context.getString(id)

    // Before and after: the device may hold settings from manual use or other tests
    @Before
    @After
    fun resetSettings() {
        context.appContainer.settings.apply {
            setEInkMode(false)
            setThemeMode(ThemeMode.System)
        }
    }

    @Test
    fun homeShowsEmptyStateAndOpensSettings() {
        compose.onNodeWithText(text(R.string.home_empty_title)).assertExists()
        compose.onNodeWithContentDescription(text(R.string.action_settings)).performClick()
        compose.onNodeWithText(text(R.string.settings_eink)).assertExists()
    }

    @Test
    fun settingsToggleEInkAndTheme() {
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
        compose.onNodeWithContentDescription(text(R.string.action_settings)).performClick()
        compose.onNodeWithText(text(R.string.settings_eink)).assertExists()
        Espresso.pressBack()
        compose.onNodeWithText(text(R.string.home_empty_title)).assertExists()
    }
}
