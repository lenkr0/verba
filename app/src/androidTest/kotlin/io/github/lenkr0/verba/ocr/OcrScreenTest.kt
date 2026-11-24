package io.github.lenkr0.verba.ocr

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.lenkr0.verba.MainActivity
import io.github.lenkr0.verba.appContainer
import io.github.lenkr0.verba.capture.CaptureTags
import io.github.lenkr0.verba.settings.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** From a screen capture to a saved word: the recognized speech bubble becomes the sentence. */
@RunWith(AndroidJUnit4::class)
class OcrScreenTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val container get() = context.appContainer
    private val testAssets = InstrumentationRegistry.getInstrumentation().context.assets

    @Before
    fun reset() = runBlocking {
        container.database.clearAllTables()
        container.settings.apply {
            setEInkMode(false)
            setThemeMode(ThemeMode.System)
            setTargetLanguage("de")
        }
        if (!container.ocrModels.installed.value) {
            testAssets.open("ocr/${OcrModels.PACK.fileName}").use { container.ocrModels.install(it, OcrModels.PACK.sha256) }
        }
    }

    /** Opens the text recognition screen the way the tile does, with [image] as the capture. */
    private fun readScreen(image: String) {
        ScreenCaptures.put(testAssets.open("ocr/$image").use { BitmapFactory.decodeStream(it) })
        ActivityScenario.launch(MainActivity::class.java).onActivity {
            it.startActivity(Intent(it, OcrActivity::class.java).setAction(OcrActivity.ACTION_READ_SCREEN))
        }
    }

    private fun waitForTag(tag: String) =
        compose.waitUntil(15_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun tappingABubbleSavesTheWordWithTheBubbleAsSentence() {
        readScreen("syn_ja_vertical.png")
        waitForTag(OcrTags.block(0))
        compose.onNode(hasContentDescription("今日はいい天気ですね")).performClick()

        compose.waitUntil(5_000) { compose.onAllNodesWithText("天気").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("天気").performClick()
        compose.onNodeWithTag(CaptureTags.LEMMA).assert(hasText("天気"))
        compose.onNodeWithTag(CaptureTags.SAVE).performClick()

        compose.waitUntil(5_000) { runBlocking { container.vocabulary.entryList().first() }.isNotEmpty() }
        val saved = runBlocking { container.vocabulary.entryList().first() }.single()
        assertEquals("天気", saved.entry.lemma)
        assertEquals("今日はいい天気ですね", saved.latestSentence)
    }

    @Test
    fun textModeListsTheBubbles() {
        readScreen("syn_ja_vertical.png")
        waitForTag(OcrTags.TEXT_MODE)
        compose.onNodeWithTag(OcrTags.TEXT_MODE).performClick()
        waitForTag(OcrTags.row(0))
        compose.onNodeWithText("魔法の薬を作っているところだよ").assertExists()
    }

    @Test
    fun modelsAreOfferedFirst() {
        container.ocrModels.delete()
        compose.waitUntil(5_000) { !container.ocrModels.installed.value }
        readScreen("syn_en_book.png")
        waitForTag(OcrTags.DOWNLOAD)
        // Installing them (here from the test APK instead of the network) continues with the image
        runBlocking { testAssets.open("ocr/${OcrModels.PACK.fileName}").use { container.ocrModels.install(it, OcrModels.PACK.sha256) } }
        waitForTag(OcrTags.TEXT_MODE)
    }
}
