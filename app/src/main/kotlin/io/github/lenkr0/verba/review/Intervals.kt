package io.github.lenkr0.verba.review

import kotlin.math.roundToLong

enum class IntervalUnit { Minutes, Hours, Days, Months, Years }

/** An interval as shown on a rating button: whole minutes, hours and days; months and years to one decimal. */
data class IntervalLabel(val unit: IntervalUnit, val value: Double)

fun intervalLabel(ms: Long): IntervalLabel {
    val minutes = ms / 60_000.0
    val days = ms / Fsrs.DAY.toDouble()
    return when {
        minutes < 59.5 -> IntervalLabel(IntervalUnit.Minutes, minutes.roundToLong().coerceAtLeast(1).toDouble())
        minutes < 23.5 * 60 -> IntervalLabel(IntervalUnit.Hours, (minutes / 60).roundToLong().toDouble())
        days < 30 -> IntervalLabel(IntervalUnit.Days, days.roundToLong().coerceAtLeast(1).toDouble())
        days < 365 -> IntervalLabel(IntervalUnit.Months, oneDecimal(days / 30))
        else -> IntervalLabel(IntervalUnit.Years, oneDecimal(days / 365))
    }
}

private fun oneDecimal(value: Double) = (value * 10).roundToLong() / 10.0
