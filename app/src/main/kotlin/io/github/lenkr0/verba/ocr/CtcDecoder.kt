package io.github.lenkr0.verba.ocr

/**
 * Turns the recognizer's output into text (greedy CTC decoding): per time
 * step the most likely class; repeats collapse, class 0 is "nothing here".
 * Classes 1..n are the dictionary's characters, n+1 is a space.
 */
internal class CtcDecoder(private val dictionary: List<String>) {
    data class Result(val text: String, val confidence: Float)

    /** [steps] = [class, probability, class, probability, ...] */
    fun decode(steps: FloatArray): Result {
        val text = StringBuilder()
        var sum = 0f
        var count = 0
        var last = BLANK
        for (t in 0..<steps.size / 2) {
            val cls = steps[2 * t].toInt()
            if (cls != BLANK && cls != last) {
                text.append(if (cls - 1 < dictionary.size) dictionary[cls - 1] else " ")
                sum += steps[2 * t + 1]
                count++
            }
            last = cls
        }
        return Result(text.toString().trim(), if (count == 0) 0f else sum / count)
    }

    private companion object {
        const val BLANK = 0
    }
}
