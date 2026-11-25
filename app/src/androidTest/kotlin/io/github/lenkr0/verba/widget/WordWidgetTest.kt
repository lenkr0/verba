package io.github.lenkr0.verba.widget

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.text.Spanned
import android.text.style.StyleSpan
import android.view.View
import android.widget.FrameLayout
import android.widget.RemoteViews
import android.widget.TextView
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.lenkr0.verba.MainActivity
import io.github.lenkr0.verba.R
import io.github.lenkr0.verba.appContainer
import io.github.lenkr0.verba.data.Capture
import io.github.lenkr0.verba.data.EntryListItem
import io.github.lenkr0.verba.ui.entry.EntryTags
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class WordWidgetTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val container get() = context.appContainer

    @Before
    fun reset() {
        container.database.clearAllTables()
        container.settings.setEInkMode(false)
    }

    private fun save(lemma: String, sentence: String, word: String, translation: String) = runBlocking {
        container.vocabulary.save(
            Capture(
                language = "en", targetLanguage = "de", wordForm = word, lemma = lemma, translation = translation,
                definition = "", sentence = sentence, wordStart = sentence.indexOf(word),
                wordEnd = sentence.indexOf(word) + word.length, sourceTitle = null, sourceApp = null, sourceAppLabel = null,
            ),
        ).entryId
    }

    private fun todaysWord(date: LocalDate): EntryListItem {
        val items = runBlocking { container.vocabulary.entryList().first() }
        val id = WordOfTheDay.pick(items.map { it.entry.id }, date)
        return items.single { it.entry.id == id }
    }

    /** The widget as the launcher would show it. */
    private fun render(views: RemoteViews): View {
        lateinit var view: View
        InstrumentationRegistry.getInstrumentation().runOnMainSync { view = views.apply(context, FrameLayout(context)) }
        return view
    }

    private fun View.text(id: Int) = findViewById<TextView>(id)

    @Test
    fun showsTodaysWordWithItsSentence() {
        save("linger", "The smell lingered in the hall.", "lingered", "verweilen")
        save("glare", "The old wizard glared.", "glared", "starren")
        val item = todaysWord(LocalDate.of(2026, 11, 25))
        val view = render(WordWidget.views(context, item, eInk = false))

        assertEquals(item.entry.lemma, view.text(R.id.widget_word).text.toString())
        assertEquals(item.entry.translation, view.text(R.id.widget_translation).text.toString())
        val sentence = view.text(R.id.widget_sentence).text
        assertEquals(item.latestSentence, sentence.toString())
        // The word is bold in its sentence
        val bold = (sentence as Spanned).getSpans(0, sentence.length, StyleSpan::class.java).single()
        assertEquals(item.latestWordStart, sentence.getSpanStart(bold))
    }

    @Test
    fun emptyWidgetInvitesToSaveWords() {
        val view = render(WordWidget.views(context, null, eInk = false))
        assertEquals(context.getString(R.string.widget_empty_title), view.text(R.id.widget_word).text.toString())
        assertEquals(View.GONE, view.text(R.id.widget_translation).visibility)
    }

    @Test
    fun eInkIsInkOnPaper() {
        save("linger", "The smell lingered in the hall.", "lingered", "verweilen")
        val view = render(WordWidget.views(context, todaysWord(LocalDate.now()), eInk = true))
        for (id in listOf(R.id.widget_label, R.id.widget_word, R.id.widget_translation, R.id.widget_sentence)) {
            assertEquals(Color.BLACK, view.text(id).currentTextColor)
        }
    }

    @Test
    fun refreshWithoutPlacedWidgetsIsHarmless() = runBlocking {
        save("linger", "The smell lingered in the hall.", "lingered", "verweilen")
        WordWidget.refresh(context)
    }

    @Test
    fun tappingOpensTheWordsPage() {
        val id = save("linger", "The smell lingered in the hall.", "lingered", "verweilen")
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_ENTRY, id))
        compose.waitUntil(5_000) { compose.onAllNodesWithTag(EntryTags.LEMMA).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag(EntryTags.LEMMA).assert(hasText("linger"))
    }
}
