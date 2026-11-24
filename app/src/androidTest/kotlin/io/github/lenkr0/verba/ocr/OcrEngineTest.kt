package io.github.lenkr0.verba.ocr

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.lenkr0.verba.util.ChecksumException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.File

/**
 * The whole pipeline on a device: native library, the real model pack (built
 * into the test APK by Gradle) and test pages – a page of Pepper&Carrot by
 * David Revoy (CC BY 4.0), and pages made for these tests (tools/ocr).
 */
@RunWith(AndroidJUnit4::class)
class OcrEngineTest {
    companion object {
        private val instrumentation = InstrumentationRegistry.getInstrumentation()
        private lateinit var models: OcrModels
        private lateinit var engine: OcrEngine

        @BeforeClass
        @JvmStatic
        fun install() = runBlocking {
            val dir = File(instrumentation.targetContext.cacheDir, "ocr-test")
            models = OcrModels(dir, "Verba-test", CoroutineScope(Dispatchers.Default))
            if (!models.installed.value) {
                instrumentation.context.assets.open("ocr/${OcrModels.PACK.fileName}").use {
                    models.install(it, OcrModels.PACK.sha256)
                }
            }
            engine = OcrEngine(models)
        }

        @AfterClass
        @JvmStatic
        fun close() = runBlocking { engine.close() }
    }

    private fun image(name: String): Bitmap =
        instrumentation.context.assets.open("ocr/$name").use { BitmapFactory.decodeStream(it) }

    private fun texts(name: String) = runBlocking { engine.recognize(image(name)) }.map { it.text }

    @Test
    fun readsJapaneseComic() {
        val texts = texts("pc_ja_e01p02.jpg")
        // Each speech bubble becomes one text, its lines in order
        assertEquals(texts.toString(), 2, texts.size)
        assertTrue(texts.toString(), texts[0].startsWith("うん") && texts[0].endsWith("カンペキ"))
        assertTrue(texts.toString(), texts[1].startsWith("ダメ") && texts[1].endsWith("そんなことしちゃだめなんだから"))
    }

    @Test
    fun readsVerticalColumnsRightToLeft() {
        val texts = texts("syn_ja_vertical.png")
        assertTrue(texts.toString(), "今日はいい天気ですね" in texts)
        assertTrue(texts.toString(), "魔法の薬を作っているところだよ" in texts)
    }

    @Test
    fun readsEnglishScreenshot() {
        val all = texts("syn_en_book.png").joinToString(" ")
        assertTrue(all, "The innkeeper wiped the same glass for the third time" in all)
        assertTrue(all, "Nobody leaves tonight, the old woman said." in all)
    }

    @Test
    fun damagedPackIsRejected() = runBlocking {
        val other = OcrModels(File(instrumentation.targetContext.cacheDir, "ocr-bad"), "Verba-test", CoroutineScope(Dispatchers.Default))
        try {
            other.install(ByteArrayInputStream(byteArrayOf(1, 2, 3)), OcrModels.PACK.sha256)
            fail("installed a damaged pack")
        } catch (_: ChecksumException) {
        }
        assertEquals(false, other.installed.value)
    }
}
