package io.github.lenkr0.verba

import android.content.Context
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.lenkr0.verba.data.Capture
import io.github.lenkr0.verba.data.LearningStatus
import io.github.lenkr0.verba.review.Rating
import io.github.lenkr0.verba.settings.AppSettings
import io.github.lenkr0.verba.settings.ThemeMode
import io.github.lenkr0.verba.ui.home.HomeTags
import io.github.lenkr0.verba.ui.review.ReviewTags
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReviewTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val container get() = context.appContainer
    private fun text(id: Int, vararg args: Any) = context.getString(id, *args)
    private fun plural(id: Int, count: Int) = context.resources.getQuantityString(id, count, count)

    @Before
    fun reset() {
        container.database.clearAllTables()
        container.deletions.clear()
        container.settings.apply {
            setEInkMode(false)
            setThemeMode(ThemeMode.System)
            setTargetLanguage("de")
            setNewPerDay(AppSettings.DEFAULT_NEW_PER_DAY)
            setReviewsPerDay(AppSettings.DEFAULT_REVIEWS_PER_DAY)
        }
    }

    private fun save(lemma: String, sentence: String, word: String, translation: String): String = runBlocking {
        container.vocabulary.save(
            Capture(
                language = "en", targetLanguage = "de", wordForm = word, lemma = lemma, translation = translation,
                definition = "", sentence = sentence, wordStart = sentence.indexOf(word),
                wordEnd = sentence.indexOf(word) + word.length, sourceTitle = "The Hobbit", sourceApp = null,
                sourceAppLabel = null,
            ),
        ).entryId
    }

    private fun waitForTag(tag: String) =
        compose.waitUntil(5_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }

    private fun waitFor(text: String) =
        compose.waitUntil(5_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    private fun launch() = ActivityScenario.launch(MainActivity::class.java)

    private fun answer(rating: Rating) {
        waitForTag(ReviewTags.SHOW_ANSWER)
        compose.onNodeWithTag(ReviewTags.SHOW_ANSWER).performClick()
        waitForTag(ReviewTags.rating(rating))
        compose.onNodeWithTag(ReviewTags.rating(rating)).performClick()
    }

    @Test
    fun reviewAWordWithItsSentence() {
        val id = save("linger", "The smell lingered in the hall.", "lingered", "verweilen")
        launch()
        waitForTag(HomeTags.START_REVIEW)
        compose.onNodeWithText(plural(R.plurals.home_review_due, 1)).assertExists()
        compose.onNodeWithTag(HomeTags.START_REVIEW).performClick()

        waitForTag(ReviewTags.SENTENCE)
        compose.onNodeWithTag(ReviewTags.SENTENCE).assertTextContains("The smell", substring = true)
        compose.onNodeWithTag(ReviewTags.SENTENCE).assertTextContains("_____", substring = true)
        compose.onNodeWithTag(ReviewTags.SHOW_HINT).performClick()
        waitForTag(ReviewTags.HINT)
        compose.onNodeWithTag(ReviewTags.HINT).assertTextContains("verweilen")

        compose.onNodeWithTag(ReviewTags.SHOW_ANSWER).performClick()
        waitForTag(ReviewTags.ANSWER)
        compose.onNodeWithTag(ReviewTags.SENTENCE).assertTextContains("The smell lingered in the hall.")
        compose.onNode(hasText("linger") and hasAnyAncestor(hasTestTag(ReviewTags.ANSWER))).assertExists()
        compose.onNodeWithText(text(R.string.interval_minutes, 10)).assertExists()

        // Good keeps it in the learning steps (back in 10 minutes, shown early as it is the last card)
        compose.onNodeWithTag(ReviewTags.rating(Rating.Good)).performClick()
        answer(Rating.Easy)
        waitForTag(ReviewTags.DONE)
        compose.onNodeWithText(plural(R.plurals.review_done_count, 2)).assertExists()
        assertEquals(2, runBlocking { container.database.reviewDao().logs(id) }.size)

        // Undo brings the last card back
        compose.onNodeWithTag(ReviewTags.UNDO).performClick()
        waitForTag(ReviewTags.SHOW_ANSWER)
        assertEquals(1, runBlocking { container.database.reviewDao().logs(id) }.size)
    }

    @Test
    fun eInkRatingButtonsAreLarge() {
        container.settings.setEInkMode(true)
        save("linger", "The smell lingered in the hall.", "lingered", "verweilen")
        launch()
        waitForTag(HomeTags.START_REVIEW)
        compose.onNodeWithTag(HomeTags.START_REVIEW).performClick()
        waitForTag(ReviewTags.SHOW_ANSWER)
        compose.onNodeWithTag(ReviewTags.SHOW_ANSWER).assertHeightIsAtLeast(56.dp).performClick()
        waitForTag(ReviewTags.rating(Rating.Again))
        Rating.entries.forEach { compose.onNodeWithTag(ReviewTags.rating(it)).assertHeightIsAtLeast(72.dp) }
    }

    @Test
    fun filterTheWordListByLearningStatus() {
        save("linger", "The smell lingered in the hall.", "lingered", "verweilen")
        save("glare", "The old wizard glared.", "glared", "starren")
        launch()
        waitForTag(HomeTags.START_REVIEW)
        compose.onNodeWithTag(HomeTags.START_REVIEW).performClick()
        answer(Rating.Easy) // "linger", saved first
        waitForTag(ReviewTags.SHOW_ANSWER)
        compose.onNode(hasContentDescription(text(R.string.action_back))).performClick()

        waitForTag(HomeTags.status(LearningStatus.Learning))
        compose.onNodeWithTag(HomeTags.status(LearningStatus.Learning)).performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("glare").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("linger").assertExists()
        compose.onNodeWithTag(HomeTags.status(LearningStatus.New)).performClick()
        waitFor("glare")
        compose.waitUntil(5_000) { compose.onAllNodesWithText("linger").fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun dailyLimitInTheSettings() {
        save("linger", "The smell lingered in the hall.", "lingered", "verweilen")
        launch()
        waitForTag(HomeTags.START_REVIEW)
        compose.onNodeWithTag(HomeTags.START_REVIEW).assertExists()

        compose.onNode(hasContentDescription(text(R.string.action_settings))).performClick()
        waitFor(text(R.string.settings_new_per_day))
        // Below the fold on small screens
        compose.onNodeWithText(text(R.string.settings_new_per_day)).performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasSetTextAction()).performTextReplacement("0")
        compose.onNodeWithText(text(R.string.action_ok)).performClick()
        compose.waitUntil(5_000) { container.settings.settings.value.newPerDay == 0 }
        compose.onNode(hasContentDescription(text(R.string.action_back))).performClick()

        waitFor(text(R.string.review_nothing_due))
        compose.onNodeWithTag(HomeTags.START_REVIEW).assertDoesNotExist()
    }
}
