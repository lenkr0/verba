package io.github.lenkr0.verba

import android.content.Context
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.lenkr0.verba.data.Capture
import io.github.lenkr0.verba.settings.ThemeMode
import io.github.lenkr0.verba.ui.entry.EntryTags
import io.github.lenkr0.verba.ui.home.HomeTags
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WordListTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val container get() = context.appContainer
    private fun text(id: Int, vararg args: Any) = context.getString(id, *args)

    @Before
    fun reset() {
        container.database.clearAllTables()
        container.deletions.clear()
        container.settings.apply {
            setEInkMode(false)
            setThemeMode(ThemeMode.System)
            setTargetLanguage("de")
        }
    }

    private fun save(lemma: String, sentence: String, word: String, translation: String, title: String?): String =
        runBlocking {
            container.vocabulary.save(
                Capture(
                    language = "en", targetLanguage = "de", wordForm = word, lemma = lemma, translation = translation,
                    definition = "", sentence = sentence, wordStart = sentence.indexOf(word),
                    wordEnd = sentence.indexOf(word) + word.length, sourceTitle = title, sourceApp = null,
                    sourceAppLabel = null,
                ),
            ).entryId
        }

    private fun waitFor(text: String) =
        compose.waitUntil(5_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    private fun waitGone(text: String) =
        compose.waitUntil(5_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty() }

    private fun launch() = ActivityScenario.launch(MainActivity::class.java)

    @Test
    fun searchAndFilterByBook() {
        save("run", "She was running late.", "running", "laufen", "The Hobbit")
        save("glare", "The old wizard glared.", "glared", "starren", "Dune")
        launch()
        waitFor("glare")

        compose.onNodeWithContentDescription(text(R.string.action_search)).performClick()
        compose.onNodeWithTag(HomeTags.SEARCH_FIELD).performTextInput("lauf")
        waitGone("glare")
        compose.onNodeWithText("run").assertExists()
        compose.onNodeWithContentDescription(text(R.string.action_close)).performClick()
        waitFor("glare")

        compose.onNodeWithText("Dune · 1").performClick()
        waitGone("run")
        compose.onNodeWithText("glare").assertExists()
    }

    @Test
    fun editTranslationInTheDetails() {
        val id = save("run", "She was running late.", "running", "laufen", null)
        launch()
        waitFor("run")
        compose.onNodeWithTag(HomeTags.entry(id)).performClick()
        compose.onNodeWithTag(EntryTags.TRANSLATION).performTextReplacement("laufen; rennen")
        compose.onNodeWithTag(EntryTags.SAVE).performClick()
        compose.waitUntil(5_000) { runBlocking { container.vocabulary.entry(id).first()?.translation } == "laufen; rennen" }
    }

    @Test
    fun deleteAndUndo() {
        val id = save("run", "She was running late.", "running", "laufen", null)
        launch()
        waitFor("run")
        compose.onNodeWithTag(HomeTags.entry(id)).performClick()
        compose.onNodeWithTag(EntryTags.DELETE).performClick()
        compose.onNodeWithText(text(R.string.dictionaries_delete)).performClick()

        waitFor(text(R.string.home_deleted, "run"))
        compose.onNodeWithText(text(R.string.action_undo)).performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag(HomeTags.entry(id))).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun likelyDuplicateIsMergedFromTheDetails() {
        val running = save("running", "She was running late.", "running", "laufend", null)
        val run = save("run", "He ran home.", "ran", "laufen", null)
        launch()
        waitFor("running")
        compose.onNodeWithTag(HomeTags.entry(running)).performClick()
        waitFor(text(R.string.entry_duplicate_hint, "run"))
        compose.onNodeWithText(text(R.string.entry_merge_into)).performClick()

        // the merged-into word opens, with both sentences
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasTestTag(EntryTags.TRANSLATION) and hasText("laufen; laufend")).fetchSemanticsNodes().isNotEmpty()
        }
        val list = runBlocking { container.vocabulary.entryList().first() }
        assertEquals(listOf(run), list.map { it.entry.id })
        assertEquals(2, list.single().contextCount)
    }
}
