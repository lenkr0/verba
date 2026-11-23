package io.github.lenkr0.verba.capture

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.lenkr0.verba.appContainer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CaptureActivityTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val container get() = context.appContainer

    @Before
    fun reset() {
        container.database.clearAllTables()
        container.settings.apply {
            setEInkMode(false)
            setTargetLanguage("de")
            noteLanguageUsed("en")
        }
    }

    private fun launch(action: String, text: String): ActivityScenario<CaptureActivity> {
        val intent = Intent(context, CaptureActivity::class.java).setAction(action).setType("text/plain")
        when (action) {
            Intent.ACTION_PROCESS_TEXT -> intent.putExtra(Intent.EXTRA_PROCESS_TEXT, text)
            Intent.ACTION_SEND -> intent.putExtra(Intent.EXTRA_TEXT, text)
            CaptureIntents.ACTION_COLORDICT -> intent.putExtra(CaptureIntents.EXTRA_COLORDICT_QUERY, text)
        }
        return ActivityScenario.launch(intent)
    }

    private fun savedEntries() = runBlocking { container.vocabulary.entryList().first() }

    private fun waitForSaved(count: Int) = compose.waitUntil(timeoutMillis = 5_000) { savedEntries().size == count }

    @Test
    fun sentenceFromSelectionMenu() {
        launch(Intent.ACTION_PROCESS_TEXT, "She was running late again.")
        compose.onNodeWithText("running").performClick()
        compose.onNodeWithTag(CaptureTags.LEMMA).assert(hasText("run"))
        compose.onNodeWithTag(CaptureTags.TRANSLATION).performTextInput("laufen")
        compose.onNodeWithTag(CaptureTags.TITLE).performTextReplacement("The Hobbit")
        compose.onNodeWithTag(CaptureTags.SAVE).performClick()

        waitForSaved(1)
        val item = savedEntries().single()
        assertEquals("run", item.entry.lemma)
        assertEquals("en", item.entry.language)
        assertEquals("de", item.entry.targetLanguage)
        assertEquals("laufen", item.entry.translation)
        assertEquals("She was running late again.", item.latestSentence)
        assertEquals("The Hobbit", item.latestSource)
    }

    @Test
    fun chineseSentenceIsSplitIntoWords() {
        launch(Intent.ACTION_PROCESS_TEXT, "我每天学习中文。")
        compose.onNodeWithText("学习").performClick()
        compose.onNodeWithTag(CaptureTags.LEMMA).assert(hasText("学习"))
        compose.onNodeWithTag(CaptureTags.SAVE).performClick()

        waitForSaved(1)
        val item = savedEntries().single()
        assertEquals("zh", item.entry.language)
        assertEquals("学习", item.latestSentence!!.substring(item.latestWordStart!!, item.latestWordEnd!!))
    }

    @Test
    fun singleWordFromShareAndDictionaryIntent() {
        launch(Intent.ACTION_SEND, "walked")
        compose.onNodeWithTag(CaptureTags.LEMMA).assert(hasText("walk"))
        compose.onNodeWithTag(CaptureTags.SAVE).performClick()
        waitForSaved(1)

        // the same base form again, from a reader's dictionary lookup: added, not duplicated
        launch(CaptureIntents.ACTION_COLORDICT, "walking")
        compose.onNodeWithTag(CaptureTags.LEMMA).assert(hasText("walk"))
        compose.onNodeWithTag(CaptureTags.SAVE).performClick()
        compose.waitUntil(timeoutMillis = 5_000) { savedEntries().singleOrNull()?.contextCount == 2 }
    }

    @Test
    fun eInkSheetWorksWithoutAnimation() {
        container.settings.setEInkMode(true)
        launch(Intent.ACTION_PROCESS_TEXT, "She was running late again.")
        compose.onNodeWithText("running").performClick()
        compose.onNodeWithTag(CaptureTags.SAVE).performClick()
        waitForSaved(1)
    }
}
