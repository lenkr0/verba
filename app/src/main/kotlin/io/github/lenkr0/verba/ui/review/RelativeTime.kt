package io.github.lenkr0.verba.ui.review

import android.icu.text.DisplayContext
import android.icu.text.RelativeDateTimeFormatter
import android.icu.text.RelativeDateTimeFormatter.AbsoluteUnit
import android.icu.text.RelativeDateTimeFormatter.Direction
import android.icu.text.RelativeDateTimeFormatter.RelativeUnit
import android.icu.util.ULocale
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToLong

/** "in 5 minutes", "tomorrow", "in 3 weeks" … in the app's language, to use mid-sentence. */
@Composable
fun relativeTime(time: Long, now: Long): String =
    formatRelativeTime(time, now, LocalConfiguration.current.locales[0], ZoneId.systemDefault())

fun formatRelativeTime(time: Long, now: Long, locale: Locale, zone: ZoneId): String {
    val format = RelativeDateTimeFormatter.getInstance(
        ULocale.forLocale(locale),
        null,
        RelativeDateTimeFormatter.Style.LONG,
        DisplayContext.CAPITALIZATION_FOR_MIDDLE_OF_SENTENCE,
    )
    val diff = time - now
    if (diff <= 0) return format.format(Direction.PLAIN, AbsoluteUnit.NOW)
    val minutes = ceil(diff / 60_000.0)
    if (minutes < 60) return format.format(minutes, Direction.NEXT, RelativeUnit.MINUTES)
    // Days by the calendar: 23:00 → 08:00 is "tomorrow", not "in 9 hours" … unless it's today
    val days = ChronoUnit.DAYS.between(
        Instant.ofEpochMilli(now).atZone(zone).toLocalDate(),
        Instant.ofEpochMilli(time).atZone(zone).toLocalDate(),
    )
    return when {
        days == 0L -> format.format((diff / 3_600_000.0).roundToLong().coerceAtLeast(1).toDouble(), Direction.NEXT, RelativeUnit.HOURS)
        days == 1L -> format.format(Direction.NEXT, AbsoluteUnit.DAY)
        days < 14 -> format.format(days.toDouble(), Direction.NEXT, RelativeUnit.DAYS)
        days < 60 -> format.format((days / 7.0).roundToLong().toDouble(), Direction.NEXT, RelativeUnit.WEEKS)
        days < 730 -> format.format((days / 30.44).roundToLong().toDouble(), Direction.NEXT, RelativeUnit.MONTHS)
        else -> format.format((days / 365.25).roundToLong().toDouble(), Direction.NEXT, RelativeUnit.YEARS)
    }
}
