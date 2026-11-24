package io.github.lenkr0.verba.review

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.lenkr0.verba.ui.review.formatRelativeTime
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.time.ZoneOffset
import java.util.Locale

/** Uses Android's ICU, so it runs on a device. */
@RunWith(AndroidJUnit4::class)
class RelativeTimeTest {
    private val now = 1_767_254_400_000L // 2026-01-01 08:00 UTC
    private val minute = 60_000L
    private val hour = 60 * minute
    private val day = 24 * hour

    private fun en(time: Long) = formatRelativeTime(time, now, Locale.ENGLISH, ZoneOffset.UTC)
    private fun de(time: Long) = formatRelativeTime(time, now, Locale.GERMAN, ZoneOffset.UTC)

    @Test
    fun lowerCaseForTheMiddleOfASentence() {
        assertEquals("in 5 minutes", en(now + 5 * minute - 1_000))
        assertEquals("in 3 hours", en(now + 3 * hour))
        assertEquals("tomorrow", en(now + 20 * hour))
        assertEquals("in 7 days", en(now + 7 * day))
        assertEquals("in 3 weeks", en(now + 21 * day))
        assertEquals("in 4 months", en(now + 120 * day))
        assertEquals("in 3 years", en(now + 1_100 * day))
    }

    @Test
    fun german() {
        assertEquals("in 10 Minuten", de(now + 10 * minute))
        assertEquals("morgen", de(now + day))
        assertEquals("in 7 Tagen", de(now + 7 * day))
    }
}
