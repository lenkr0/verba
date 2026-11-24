package io.github.lenkr0.verba.ocr

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** Image coordinates: pixel (i, j) covers [i, i+1] x [j, j+1]. */
data class Point(val x: Float, val y: Float) {
    operator fun plus(o: Point) = Point(x + o.x, y + o.y)
    operator fun minus(o: Point) = Point(x - o.x, y - o.y)
    operator fun times(f: Float) = Point(x * f, y * f)
    fun distanceTo(o: Point) = hypot(x - o.x, y - o.y)
}

/** A box around a text line; may be rotated. Corners as seen upright on the page. */
data class Quad(val topLeft: Point, val topRight: Point, val bottomRight: Point, val bottomLeft: Point) {
    val width get() = max(topLeft.distanceTo(topRight), bottomLeft.distanceTo(bottomRight))
    val height get() = max(topLeft.distanceTo(bottomLeft), topRight.distanceTo(bottomRight))
    val left get() = min(min(topLeft.x, topRight.x), min(bottomRight.x, bottomLeft.x))
    val right get() = max(max(topLeft.x, topRight.x), max(bottomRight.x, bottomLeft.x))
    val top get() = min(min(topLeft.y, topRight.y), min(bottomRight.y, bottomLeft.y))
    val bottom get() = max(max(topLeft.y, topRight.y), max(bottomRight.y, bottomLeft.y))

    /** Columns of vertical text are much taller than wide. */
    val isVertical get() = height >= VERTICAL_RATIO * width

    fun scaled(sx: Float, sy: Float) = Quad(
        Point(topLeft.x * sx, topLeft.y * sy),
        Point(topRight.x * sx, topRight.y * sy),
        Point(bottomRight.x * sx, bottomRight.y * sy),
        Point(bottomLeft.x * sx, bottomLeft.y * sy),
    )

    companion object {
        /** PaddleOCR turns crops at least 1.5 times taller than wide by 90 degrees. */
        const val VERTICAL_RATIO = 1.5f
    }
}

/** An axis-aligned rectangle, e.g. the area of a text block. */
data class Bounds(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top

    fun contains(x: Float, y: Float) = x in left..right && y in top..bottom

    fun union(o: Bounds) = Bounds(min(left, o.left), min(top, o.top), max(right, o.right), max(bottom, o.bottom))

    companion object {
        fun of(q: Quad) = Bounds(q.left, q.top, q.right, q.bottom)
    }
}
