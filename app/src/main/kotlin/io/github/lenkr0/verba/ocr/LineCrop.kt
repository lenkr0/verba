package io.github.lenkr0.verba.ocr

import kotlin.math.ceil

/**
 * How to cut a text line out of the page for the recognizer: an affine map
 * from the line image (width x 48 pixels) back to page coordinates.
 * Vertical columns are turned 90 degrees counter-clockwise, so their first
 * character comes out on the left.
 */
internal class LineCrop(
    /** sx = m0*x + m1*y + m2, sy = m3*x + m4*y + m5 */
    val matrix: FloatArray,
    val width: Int,
    val vertical: Boolean,
) {
    companion object {
        const val HEIGHT = 48
        private const val MAX_WIDTH = 4096

        fun of(q: Quad): LineCrop {
            val across = q.topRight - q.topLeft // along a horizontal line
            val down = q.bottomLeft - q.topLeft // along a vertical column
            val w = q.width
            val h = q.height
            return if (q.isVertical) {
                val width = ceil(h * HEIGHT / w).toInt().coerceIn(1, MAX_WIDTH)
                // x runs down the column, y from its right edge to its left edge
                val m = floatArrayOf(
                    down.x / width, -across.x / HEIGHT, q.topRight.x,
                    down.y / width, -across.y / HEIGHT, q.topRight.y,
                )
                LineCrop(m, width, vertical = true)
            } else {
                val width = ceil(w * HEIGHT / h).toInt().coerceIn(1, MAX_WIDTH)
                val m = floatArrayOf(
                    across.x / width, down.x / HEIGHT, q.topLeft.x,
                    across.y / width, down.y / HEIGHT, q.topLeft.y,
                )
                LineCrop(m, width, vertical = false)
            }
        }
    }

    /** Where a point of the line image lies on the page. */
    fun map(x: Float, y: Float) = Point(
        matrix[0] * x + matrix[1] * y + matrix[2],
        matrix[3] * x + matrix[4] * y + matrix[5],
    )
}
