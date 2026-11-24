package io.github.lenkr0.verba.ocr

import kotlin.math.max
import kotlin.math.min

/** One recognized line (or column of vertical text). */
data class TextLine(val text: String, val quad: Quad, val confidence: Float) {
    val vertical get() = quad.isVertical
    val bounds get() = Bounds.of(quad)
}

/** Lines that belong together – a speech bubble, a paragraph – in reading order. */
data class TextBlock(val lines: List<TextLine>) {
    val vertical get() = lines.first().vertical
    val bounds get() = lines.map { it.bounds }.reduce(Bounds::union)
    val text get() = Layout.join(lines.map { it.text })
}

/**
 * Groups lines into blocks: lines of the same orientation and similar size
 * that are next to each other. Vertical columns read right to left (Japanese
 * and traditional Chinese manga), horizontal lines top to bottom.
 */
internal object Layout {
    /** Lines further apart than this many line heights don't belong together. */
    private const val MAX_GAP = 1.0f
    private const val MAX_SIZE_RATIO = 1.8f

    fun blocks(lines: List<TextLine>): List<TextBlock> {
        val parent = IntArray(lines.size) { it }
        fun find(i: Int): Int {
            var r = i
            while (parent[r] != r) r = parent[r]
            parent[i] = r
            return r
        }
        for (i in lines.indices) {
            for (j in i + 1..<lines.size) {
                if (belongTogether(lines[i], lines[j])) parent[find(i)] = find(j)
            }
        }
        return lines.indices.groupBy(::find).values
            .map { group -> TextBlock(ordered(group.map(lines::get))) }
            .sortedWith(compareBy({ it.bounds.top }, { it.bounds.left }))
    }

    private fun ordered(lines: List<TextLine>): List<TextLine> =
        if (lines.first().vertical) {
            lines.sortedWith(compareBy({ -it.bounds.right }, { it.bounds.top }))
        } else {
            lines.sortedWith(compareBy({ it.bounds.top }, { it.bounds.left }))
        }

    private fun belongTogether(a: TextLine, b: TextLine): Boolean {
        if (a.vertical != b.vertical) return false
        val ab = a.bounds
        val bb = b.bounds
        return if (a.vertical) {
            // Columns side by side: their size is their width
            val size = min(ab.width, bb.width)
            sizeRatio(ab.width, bb.width) && overlap(ab.top, ab.bottom, bb.top, bb.bottom) &&
                gap(ab.left, ab.right, bb.left, bb.right) <= MAX_GAP * size
        } else {
            val size = min(ab.height, bb.height)
            sizeRatio(ab.height, bb.height) && overlap(ab.left, ab.right, bb.left, bb.right) &&
                gap(ab.top, ab.bottom, bb.top, bb.bottom) <= MAX_GAP * size
        }
    }

    private fun sizeRatio(a: Float, b: Float) = max(a, b) <= MAX_SIZE_RATIO * min(a, b)

    private fun overlap(a0: Float, a1: Float, b0: Float, b1: Float) = min(a1, b1) > max(a0, b0)

    private fun gap(a0: Float, a1: Float, b0: Float, b1: Float) = max(0f, max(a0, b0) - min(a1, b1))

    /**
     * Joins lines into one text: with a space between words of space-separated
     * scripts, without for Chinese and Japanese; a word broken with a hyphen
     * at the end of a line is joined again.
     */
    fun join(lines: List<String>): String {
        val out = StringBuilder()
        for (line in lines.map { it.trim() }.filter { it.isNotEmpty() }) {
            if (out.isEmpty()) {
                out.append(line)
                continue
            }
            val last = out.last()
            val next = line.first()
            when {
                last == '-' && out.length >= 2 && out[out.length - 2].isLetter() && next.isLowerCase() -> {
                    out.setLength(out.length - 1)
                    out.append(line)
                }
                spaced(last) && spaced(next) -> out.append(' ').append(line)
                else -> out.append(line)
            }
        }
        return out.toString()
    }

    /** Characters of scripts that separate words with spaces (Latin, Cyrillic, Greek, digits, punctuation). */
    private fun spaced(c: Char): Boolean {
        val block = Character.UnicodeScript.of(c.code)
        return block == Character.UnicodeScript.LATIN || block == Character.UnicodeScript.CYRILLIC ||
            block == Character.UnicodeScript.GREEK || block == Character.UnicodeScript.HANGUL ||
            (block == Character.UnicodeScript.COMMON && c.code < 0x3000)
    }
}
