package io.github.lenkr0.verba.ocr

import kotlin.math.abs
import kotlin.math.hypot

/**
 * Finds text line boxes in the detection network's probability map, as
 * PaddleOCR's DB post-processing does, without OpenCV: threshold, dilate,
 * connected regions, the smallest rotated rectangle around each region,
 * then enlarge it (the network marks only the core of each line).
 */
internal object TextBoxes {
    private const val THRESHOLD = 0.3f
    private const val BOX_THRESHOLD = 0.5f
    private const val UNCLIP_RATIO = 1.6f
    private const val MIN_SIDE = 3f
    private const val MIN_PIXELS = 9
    private const val MAX_BOXES = 1000

    /** Boxes in map coordinates, top to bottom. */
    fun find(prob: FloatArray, width: Int, height: Int): List<Quad> {
        require(prob.size == width * height)
        val mask = dilated(prob, width, height)
        val visited = BooleanArray(mask.size)
        val stack = IntArrayStack()
        // Per row of the current region: leftmost and rightmost pixel
        val rowMin = IntArray(height) { Int.MAX_VALUE }
        val rowMax = IntArray(height) { Int.MIN_VALUE }
        val boxes = ArrayList<Quad>()

        for (start in mask.indices) {
            if (!mask[start] || visited[start]) continue
            visited[start] = true
            stack.push(start)
            var count = 0
            var sum = 0.0
            var minY = height
            var maxY = -1
            while (stack.isNotEmpty()) {
                val i = stack.pop()
                val x = i % width
                val y = i / width
                count++
                sum += prob[i]
                if (x < rowMin[y]) rowMin[y] = x
                if (x > rowMax[y]) rowMax[y] = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
                for (dy in -1..1) {
                    val ny = y + dy
                    if (ny !in 0..<height) continue
                    for (dx in -1..1) {
                        val nx = x + dx
                        if (nx !in 0..<width) continue
                        val n = ny * width + nx
                        if (mask[n] && !visited[n]) {
                            visited[n] = true
                            stack.push(n)
                        }
                    }
                }
            }
            val corners = ArrayList<Point>((maxY - minY + 1) * 4)
            for (y in minY..maxY) {
                if (rowMin[y] == Int.MAX_VALUE) continue
                corners += Point(rowMin[y].toFloat(), y.toFloat())
                corners += Point(rowMin[y].toFloat(), y + 1f)
                corners += Point(rowMax[y] + 1f, y.toFloat())
                corners += Point(rowMax[y] + 1f, y + 1f)
                rowMin[y] = Int.MAX_VALUE
                rowMax[y] = Int.MIN_VALUE
            }
            if (count < MIN_PIXELS || sum / count < BOX_THRESHOLD) continue
            box(convexHull(corners))?.let(boxes::add)
            if (boxes.size >= MAX_BOXES) break
        }
        return boxes.sortedWith(compareBy({ it.top }, { it.left }))
    }

    /** Threshold, then a 2x2 dilation (PaddleOCR's use_dilation) that joins characters of a line. */
    private fun dilated(prob: FloatArray, width: Int, height: Int): BooleanArray {
        val mask = BooleanArray(prob.size) { prob[it] > THRESHOLD }
        val out = mask.copyOf()
        for (y in 0..<height) {
            for (x in 0..<width) {
                val i = y * width + x
                if (out[i]) continue
                out[i] = (x > 0 && mask[i - 1]) || (y > 0 && mask[i - width]) || (x > 0 && y > 0 && mask[i - width - 1])
            }
        }
        return out
    }

    /** Smallest rotated rectangle around [hull], enlarged; null if too thin. */
    private fun box(hull: List<Point>): Quad? {
        if (hull.size < 3) return null
        var best: Rect? = null
        for (i in hull.indices) {
            val a = hull[i]
            val b = hull[(i + 1) % hull.size]
            val len = hypot(b.x - a.x, b.y - a.y)
            if (len == 0f) continue
            val ux = (b.x - a.x) / len
            val uy = (b.y - a.y) / len
            var u0 = Float.MAX_VALUE
            var u1 = -Float.MAX_VALUE
            var v0 = Float.MAX_VALUE
            var v1 = -Float.MAX_VALUE
            for (p in hull) {
                val u = p.x * ux + p.y * uy
                val v = -p.x * uy + p.y * ux
                if (u < u0) u0 = u
                if (u > u1) u1 = u
                if (v < v0) v0 = v
                if (v > v1) v1 = v
            }
            val rect = Rect(ux, uy, u0, u1, v0, v1)
            if (best == null || rect.area < best.area) best = rect
        }
        val r = best ?: return null
        if (minOf(r.w, r.h) < MIN_SIDE) return null
        // Offset each side by area * ratio / perimeter (PaddleOCR's unclip, for a rectangle)
        val d = r.w * r.h * UNCLIP_RATIO / (2 * (r.w + r.h))
        return r.quad(d)
    }

    private class Rect(val ux: Float, val uy: Float, val u0: Float, val u1: Float, val v0: Float, val v1: Float) {
        val w = u1 - u0
        val h = v1 - v0
        val area = w * h

        /** Corners named as seen on the page: the axis closer to horizontal runs left to right. */
        fun quad(grow: Float): Quad {
            val cu = (u0 + u1) / 2
            val cv = (v0 + v1) / 2
            val center = Point(cu * ux - cv * uy, cu * uy + cv * ux)
            val u = Point(ux, uy)
            val v = Point(-uy, ux)
            val uIsHorizontal = abs(ux) >= abs(uy)
            var right = if (uIsHorizontal) u else v
            var down = if (uIsHorizontal) v else u
            if (right.x < 0) right *= -1f
            if (down.y < 0) down *= -1f
            val halfWidth = (if (uIsHorizontal) w else h) / 2 + grow
            val halfHeight = (if (uIsHorizontal) h else w) / 2 + grow
            return Quad(
                center - right * halfWidth - down * halfHeight,
                center + right * halfWidth - down * halfHeight,
                center + right * halfWidth + down * halfHeight,
                center - right * halfWidth + down * halfHeight,
            )
        }
    }

    /** Andrew's monotone chain; counter-clockwise, no duplicate points. */
    fun convexHull(points: List<Point>): List<Point> {
        val pts = points.distinct().sortedWith(compareBy({ it.x }, { it.y }))
        if (pts.size < 3) return pts
        fun cross(o: Point, a: Point, b: Point) = (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x)
        val lower = ArrayList<Point>()
        for (p in pts) {
            while (lower.size >= 2 && cross(lower[lower.size - 2], lower.last(), p) <= 0) lower.removeAt(lower.lastIndex)
            lower += p
        }
        val upper = ArrayList<Point>()
        for (p in pts.asReversed()) {
            while (upper.size >= 2 && cross(upper[upper.size - 2], upper.last(), p) <= 0) upper.removeAt(upper.lastIndex)
            upper += p
        }
        return lower.dropLast(1) + upper.dropLast(1)
    }

    private class IntArrayStack {
        private var data = IntArray(1024)
        private var size = 0

        fun push(v: Int) {
            if (size == data.size) data = data.copyOf(size * 2)
            data[size++] = v
        }

        fun pop() = data[--size]

        fun isNotEmpty() = size > 0
    }
}
