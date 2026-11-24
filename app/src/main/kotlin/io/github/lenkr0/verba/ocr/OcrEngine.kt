package io.github.lenkr0.verba.ocr

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.coroutineContext
import kotlin.math.roundToInt

/** The native part (libverbaocr: ncnn running the two networks). */
internal object OcrNative {
    val loaded: Boolean = runCatching { System.loadLibrary("verbaocr") }.isSuccess

    @JvmStatic external fun load(detParam: String, detModel: String, recParam: String, recModel: String, threads: Int): Long

    @JvmStatic external fun release(handle: Long)

    @JvmStatic external fun detect(handle: Long, bitmap: Bitmap, width: Int, height: Int): FloatArray?

    @JvmStatic external fun recognize(handle: Long, bitmap: Bitmap, matrix: FloatArray, width: Int): FloatArray?
}

class OcrException(message: String) : Exception(message)

/**
 * Finds and reads text in an image: the detection network marks text, each
 * line is cut out (columns turned) and read by the recognition network.
 * One image at a time; [close] frees the networks (about 30 MB of memory).
 */
class OcrEngine(private val models: OcrModels) {
    private val mutex = Mutex()
    private var handle = 0L
    private var decoder: CtcDecoder? = null

    suspend fun recognize(image: Bitmap): List<TextBlock> = withContext(Dispatchers.Default) {
        mutex.withLock {
            val h = ensureLoaded()
            val bitmap = if (image.config == Bitmap.Config.ARGB_8888) image else image.copy(Bitmap.Config.ARGB_8888, false)
            val (dw, dh) = detectionSize(bitmap.width, bitmap.height)
            val prob = OcrNative.detect(h, bitmap, dw, dh) ?: throw OcrException("detection failed")
            val sx = bitmap.width.toFloat() / dw
            val sy = bitmap.height.toFloat() / dh
            val lines = ArrayList<TextLine>()
            for (box in TextBoxes.find(prob, dw, dh)) {
                coroutineContext.ensureActive()
                val quad = box.scaled(sx, sy)
                val crop = LineCrop.of(quad)
                val steps = OcrNative.recognize(h, bitmap, crop.matrix, crop.width) ?: continue
                val result = decoder!!.decode(steps)
                if (worthKeeping(result)) lines += TextLine(result.text, quad, result.confidence)
            }
            Layout.blocks(lines)
        }
    }

    suspend fun close() = mutex.withLock {
        if (handle != 0L) OcrNative.release(handle)
        handle = 0L
    }

    private fun ensureLoaded(): Long {
        if (handle != 0L) return handle
        if (!OcrNative.loaded) throw OcrException("text recognition is not available on this device")
        if (!models.installed.value) throw OcrException("models not installed")
        val dir = models.packDirectory
        fun path(name: String) = File(dir, name).absolutePath
        val threads = Runtime.getRuntime().availableProcessors().coerceIn(1, MAX_THREADS)
        handle = OcrNative.load(path("det.ncnn.param"), path("det.ncnn.bin"), path("rec.ncnn.param"), path("rec.ncnn.bin"), threads)
        if (handle == 0L) throw OcrException("could not load models")
        decoder = CtcDecoder(File(dir, "dict.txt").readLines())
        return handle
    }

    companion object {
        private const val MIN_CONFIDENCE = 0.5f
        private const val SINGLE_LETTER_CONFIDENCE = 0.9f

        /**
         * Text without any letter (numbers, punctuation, an icon read as "0")
         * has nothing to look up; a lone letter is often a misread drawing.
         */
        internal fun worthKeeping(result: CtcDecoder.Result): Boolean {
            val letters = result.text.count(Char::isLetter)
            return when {
                letters == 0 -> false
                result.text.length == 1 -> result.confidence >= SINGLE_LETTER_CONFIDENCE
                else -> result.confidence >= MIN_CONFIDENCE
            }
        }

        private const val MAX_THREADS = 4
        // Small images are scaled up so their short side has at least this many
        // pixels (text gets big enough), large ones down to this long side.
        private const val MIN_SIDE = 736
        private const val MAX_SIDE = 2400

        /** Size the detection network sees: multiples of 32. */
        internal fun detectionSize(width: Int, height: Int): Pair<Int, Int> {
            var ratio = 1f
            val short = minOf(width, height)
            val long = maxOf(width, height)
            if (short < MIN_SIDE) ratio = MIN_SIDE.toFloat() / short
            if (long * ratio > MAX_SIDE) ratio = MAX_SIDE.toFloat() / long
            fun round32(v: Float) = ((v / 32).roundToInt() * 32).coerceAtLeast(32)
            return round32(width * ratio) to round32(height * ratio)
        }
    }
}
