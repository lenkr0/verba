package io.github.lenkr0.verba.review

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class ReviewHelpersTest {
    @Test
    fun clozeUsesTheSavedOffsets() {
        assertEquals(
            Cloze("She was ", "running", " late."),
            Cloze.of("She was running late.", "running", 8, 15),
        )
        // Chinese: offsets are UTF-16 indices, the gap covers both characters
        assertEquals(Cloze("我每天", "学习", "中文。"), Cloze.of("我每天学习中文。", "学习", 3, 5))
    }

    @Test
    fun clozeFindsTheWordWhenOffsetsAreUnknown() {
        assertEquals(Cloze("The ", "Wizard", " glared."), Cloze.of("The Wizard glared.", "wizard", -1, -1))
        assertEquals(Cloze("a ", "b", ""), Cloze.of("a b", "b", 2, 99))
    }

    @Test
    fun noClozeWithoutASentenceAroundTheWord() {
        assertNull(Cloze.of("", "run", -1, -1))
        assertNull(Cloze.of("run", "run", 0, 3))
        assertNull(Cloze.of("The wizard glared.", "dragon", -1, -1))
        assertNull(Cloze.of("The wizard glared.", "", -1, -1))
    }

    @Test
    fun intervalLabels() {
        assertEquals(IntervalLabel(IntervalUnit.Minutes, 1.0), intervalLabel(Fsrs.MINUTE))
        assertEquals(IntervalLabel(IntervalUnit.Minutes, 6.0), intervalLabel(330_000))
        assertEquals(IntervalLabel(IntervalUnit.Minutes, 10.0), intervalLabel(10 * Fsrs.MINUTE))
        assertEquals(IntervalLabel(IntervalUnit.Hours, 1.0), intervalLabel(60 * Fsrs.MINUTE))
        assertEquals(IntervalLabel(IntervalUnit.Hours, 3.0), intervalLabel(170 * Fsrs.MINUTE))
        assertEquals(IntervalLabel(IntervalUnit.Days, 1.0), intervalLabel(Fsrs.DAY))
        assertEquals(IntervalLabel(IntervalUnit.Days, 8.0), intervalLabel(8 * Fsrs.DAY))
        assertEquals(IntervalLabel(IntervalUnit.Months, 1.5), intervalLabel(45 * Fsrs.DAY))
        assertEquals(IntervalLabel(IntervalUnit.Years, 1.1), intervalLabel(400 * Fsrs.DAY))
    }

    private val berlin = ZoneId.of("Europe/Berlin")

    private fun at(text: String) = LocalDateTime.parse(text).atZone(berlin).toInstant().toEpochMilli()

    @Test
    fun theDayStartsAtFourInTheMorning() {
        val evening = ReviewDay.of(at("2026-03-10T23:30"), berlin)
        assertEquals(ReviewDay(at("2026-03-10T04:00"), at("2026-03-11T04:00")), evening)
        // Reading after midnight still counts for the evening before
        assertEquals(evening, ReviewDay.of(at("2026-03-11T02:00"), berlin))
        assertEquals(ReviewDay(at("2026-03-11T04:00"), at("2026-03-12T04:00")), ReviewDay.of(at("2026-03-11T04:00"), berlin))
    }

    @Test
    fun daylightSavingDaysAreShorterOrLonger() {
        val spring = ReviewDay.of(at("2026-03-28T12:00"), berlin)
        assertEquals(23 * 3_600_000L, spring.end - spring.start)
        val autumn = ReviewDay.of(at("2026-10-24T12:00"), berlin)
        assertEquals(25 * 3_600_000L, autumn.end - autumn.start)
    }
}
