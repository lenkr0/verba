package io.github.lenkr0.verba.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrPipelineTest {
    private class Map(val width: Int, val height: Int) {
        val prob = FloatArray(width * height)

        fun fill(x0: Int, y0: Int, x1: Int, y1: Int, p: Float = 0.9f) {
            for (y in y0..y1) for (x in x0..x1) prob[y * width + x] = p
        }

        fun boxes() = TextBoxes.find(prob, width, height)
    }

    private fun assertNear(expected: Float, actual: Float, tolerance: Float = 0.6f) =
        assertEquals(expected.toDouble(), actual.toDouble(), tolerance.toDouble())

    @Test
    fun lineBoxIsEnlargedLikePaddleOcr() {
        val map = Map(100, 40).apply { fill(20, 15, 69, 24) }
        val box = map.boxes().single()
        // Dilation adds a pixel right and below: 51 x 11, then each side grows by
        // 51 * 11 * 1.6 / (2 * 62) = 7.24
        assertNear(12.76f, box.left)
        assertNear(78.24f, box.right)
        assertNear(7.76f, box.top)
        assertNear(33.24f, box.bottom)
        assertFalse(box.isVertical)
    }

    @Test
    fun separateLinesTopToBottom() {
        val map = Map(120, 80).apply {
            fill(10, 50, 100, 59)
            fill(10, 10, 60, 19)
        }
        val boxes = map.boxes()
        assertEquals(2, boxes.size)
        assertTrue(boxes[0].top < boxes[1].top)
    }

    @Test
    fun weakOrTinyRegionsAreDropped() {
        val map = Map(100, 40).apply {
            fill(10, 10, 60, 20, p = 0.4f) // above the threshold, but not confident enough
            fill(80, 30, 81, 31) // 4 pixels
        }
        assertEquals(emptyList<Quad>(), map.boxes())
    }

    @Test
    fun slantedLineKeepsItsAngle() {
        val map = Map(200, 120)
        for (x in 20..180) {
            val y = (20 + 0.2f * x).toInt()
            map.fill(x, y, x, y + 7)
        }
        val box = map.boxes().single()
        val slope = (box.topRight.y - box.topLeft.y) / (box.topRight.x - box.topLeft.x)
        assertNear(0.2f, slope, tolerance = 0.03f)
        assertTrue(box.topLeft.x < box.topRight.x && box.topLeft.y < box.bottomLeft.y)
    }

    @Test
    fun columnIsVertical() {
        val box = Map(60, 100).apply { fill(20, 10, 29, 79) }.boxes().single()
        assertTrue(box.isVertical)
        assertTrue(box.topLeft.x < box.topRight.x && box.topLeft.y < box.bottomLeft.y)
    }

    @Test
    fun horizontalCropMapsCorners() {
        val q = Quad(Point(10f, 20f), Point(110f, 20f), Point(110f, 40f), Point(10f, 40f))
        val crop = LineCrop.of(q)
        assertFalse(crop.vertical)
        assertEquals(240, crop.width) // 100 * 48 / 20
        assertEquals(Point(10f, 20f), crop.map(0f, 0f))
        assertEquals(Point(110f, 20f), crop.map(240f, 0f))
        assertEquals(Point(10f, 40f), crop.map(0f, 48f))
    }

    @Test
    fun verticalCropTurnsColumnCounterClockwise() {
        val q = Quad(Point(50f, 10f), Point(70f, 10f), Point(70f, 110f), Point(50f, 110f))
        val crop = LineCrop.of(q)
        assertTrue(crop.vertical)
        assertEquals(240, crop.width) // 100 * 48 / 20
        // Left edge of the line image = top of the column; its top row = the column's right edge
        assertEquals(Point(70f, 10f), crop.map(0f, 0f))
        assertEquals(Point(70f, 110f), crop.map(240f, 0f))
        assertEquals(Point(50f, 10f), crop.map(0f, 48f))
    }

    @Test
    fun ctcCollapsesRepeatsAndBlanks() {
        val decoder = CtcDecoder(listOf("a", "b", "c"))
        val steps = floatArrayOf(1f, 0.9f, 1f, 0.8f, 0f, 0.99f, 1f, 0.7f, 2f, 0.6f, 4f, 0.9f, 3f, 0.5f)
        val result = decoder.decode(steps)
        assertEquals("aab c", result.text)
        assertNear(0.72f, result.confidence, tolerance = 0.001f)
        assertEquals(CtcDecoder.Result("", 0f), decoder.decode(floatArrayOf(0f, 0.9f, 0f, 0.8f)))
    }

    @Test
    fun linesJoinPerScript() {
        assertEquals("Don't even think about it.", Layout.join(listOf("Don't even think", "about it.")))
        assertEquals("そんなことしちゃだめなんだから", Layout.join(listOf("そんなことしちゃ", "だめなんだから")))
        assertEquals("continue", Layout.join(listOf("con-", "tinue")))
        assertEquals("Hallo Welt", Layout.join(listOf(" Hallo ", "", "Welt")))
    }

    private fun line(text: String, l: Float, t: Float, r: Float, b: Float) =
        TextLine(text, Quad(Point(l, t), Point(r, t), Point(r, b), Point(l, b)), 0.9f)

    @Test
    fun bubbleLinesFormOneBlock() {
        val blocks = Layout.blocks(
            listOf(
                line("about it.", 30f, 130f, 110f, 150f),
                line("Don't even think", 10f, 105f, 130f, 125f),
                line("NO!", 300f, 400f, 340f, 420f),
            ),
        )
        assertEquals(listOf("Don't even think about it.", "NO!"), blocks.map { it.text })
    }

    @Test
    fun columnsReadRightToLeft() {
        val blocks = Layout.blocks(
            listOf(
                line("ですね", 40f, 10f, 60f, 100f),
                line("今日は", 100f, 10f, 120f, 90f),
                line("いい天気", 70f, 10f, 90f, 110f),
            ),
        )
        assertEquals(listOf("今日はいい天気ですね"), blocks.map { it.text })
        assertTrue(blocks.single().vertical)
    }

    @Test
    fun noiseIsDropped() {
        fun keep(text: String, confidence: Float) = OcrEngine.worthKeeping(CtcDecoder.Result(text, confidence))
        assertFalse(keep("0", 0.8f)) // a battery icon
        assertFalse(keep("18", 0.99f))
        assertFalse(keep("?!", 0.95f))
        assertFalse(keep("A", 0.6f)) // a drawing
        assertTrue(keep("A", 0.95f))
        assertTrue(keep("何", 0.95f))
        assertTrue(keep("うーん", 0.8f))
        assertFalse(keep("taCm", 0.3f))
    }

    @Test
    fun detectionSizeIsAMultipleOf32() {
        assertEquals(1088 to 2400, OcrEngine.detectionSize(1080, 2400))
        assertEquals(1216 to 736, OcrEngine.detectionSize(500, 300))
        assertEquals(2400 to 1792, OcrEngine.detectionSize(4000, 3000))
    }
}
