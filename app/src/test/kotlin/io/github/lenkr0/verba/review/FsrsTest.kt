package io.github.lenkr0.verba.review

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

/** Checks the port against values computed by the reference implementation, py-fsrs 6.3.3. */
class FsrsTest {
    private data class Step(
        val reviewAt: Long,
        val rating: Int,
        val state: Int,
        val step: Int?,
        val stability: Double,
        val difficulty: Double,
        val due: Long,
    )

    // Generated with py-fsrs 6.3.3, Scheduler(enable_fuzzing=False): each review happens at reviewAt.
    private val sequences = mapOf(
        "goodPath" to listOf(
            Step(1767254400000L, 3, 1, 1, 2.3065, 2.118103970459016, 1767255000000L),
            Step(1767255000000L, 3, 2, null, 2.3065, 2.111214235785395, 1767427800000L),
            Step(1767427800000L, 3, 2, null, 10.971048263078135, 2.1043313908464483, 1768378200000L),
            Step(1768378200000L, 3, 2, null, 46.316858440073425, 2.0974554287524403, 1772352600000L),
            Step(1772352600000L, 3, 2, null, 162.99981577472244, 2.0905863426205262, 1786435800000L),
            Step(1786435800000L, 3, 2, null, 497.8765551245907, 2.083724125574744, 1829463000000L),
        ),
        "againHardGoodEasy" to listOf(
            Step(1767254400000L, 1, 1, 0, 0.212, 6.4133, 1767254460000L),
            Step(1767254460000L, 2, 1, 0, 0.212, 7.604209769076838, 1767254790000L),
            Step(1767254790000L, 3, 1, 1, 0.24668918777567272, 7.5918339286046, 1767255390000L),
            Step(1767255390000L, 4, 2, null, 0.48892083828453853, 6.772365417380022, 1767341790000L),
            Step(1767341790000L, 2, 2, null, 1.6934165145475426, 7.842574125582147, 1767514590000L),
            Step(1767514590000L, 1, 3, 0, 0.4677298914348687, 9.276097154871636, 1767515190000L),
            Step(1767515190000L, 3, 2, null, 0.5166501977928463, 9.2620494270136, 1767601590000L),
            Step(1767601590000L, 3, 2, null, 1.3508272345128216, 9.248015746883425, 1767687990000L),
        ),
        "easyFirst" to listOf(
            Step(1767254400000L, 4, 2, null, 8.2956, 1.0, 1767945600000L),
            Step(1767945600000L, 4, 2, null, 65.62422616189994, 1.0, 1773648000000L),
            Step(1773648000000L, 1, 3, 0, 3.5289566665711614, 7.0269895692968385, 1773648600000L),
            Step(1773648600000L, 1, 3, 0, 1.1531580583438599, 9.008020057195637, 1773649200000L),
            Step(1773649200000L, 3, 2, null, 1.2003384183233212, 8.994240406435278, 1773735600000L),
            Step(1773735600000L, 4, 2, null, 3.2629725619077767, 8.643390787959845, 1773994800000L),
        ),
        "lateAndEarly" to listOf(
            Step(1767254400000L, 3, 1, 1, 2.3065, 2.118103970459016, 1767255000000L),
            Step(1767255000000L, 3, 2, null, 2.3065, 2.111214235785395, 1767427800000L),
            Step(1768291800000L, 3, 2, null, 27.097391374722992, 2.1043313908464483, 1770624600000L),
            Step(1770451800000L, 3, 2, null, 97.8172045658066, 2.0974554287524403, 1778919000000L),
            Step(1781511000000L, 1, 3, 0, 4.048520039220351, 7.387715706030851, 1781511600000L),
            Step(1781511900000L, 3, 2, null, 4.048520039220351, 7.375556359621658, 1781857500000L),
            Step(1781857500000L, 2, 2, null, 7.7825394389881275, 8.243000381740053, 1782548700000L),
        ),
        "hardLearning" to listOf(
            Step(1767254400000L, 2, 1, 0, 1.2931, 5.112170705601056, 1767254730000L),
            Step(1767254730000L, 2, 1, 0, 1.2931, 6.7404595108297, 1767255060000L),
            Step(1767255060000L, 3, 1, 1, 1.3358997622047517, 6.728947420615708, 1767255660000L),
            Step(1767255660000L, 2, 1, 1, 1.3358997622047517, 7.813751236051346, 1767256260000L),
            Step(1767256260000L, 3, 2, null, 1.3771622371865915, 7.801165854112133, 1767342660000L),
            Step(1767601860000L, 4, 2, null, 9.569622712310743, 7.051646317524445, 1768465860000L),
        ),
    )
    private val start = 1767254400000L

    @Test
    fun matchesPyFsrs() {
        val fsrs = Fsrs(fuzz = null)
        for ((name, steps) in sequences) {
            var card = fsrs.newCard(start)
            steps.forEachIndexed { index, expected ->
                val where = "$name, review ${index + 1}"
                card = fsrs.review(card, Rating.entries.first { it.value == expected.rating }, expected.reviewAt)
                assertEquals(where, expected.state, card.state.value)
                assertEquals(where, expected.step, card.step)
                assertEquals(where, expected.stability, card.stability!!, 1e-9)
                assertEquals(where, expected.difficulty, card.difficulty!!, 1e-9)
                assertEquals(where, expected.due, card.due)
                assertEquals(where, expected.reviewAt, card.lastReview)
            }
        }
    }

    @Test
    fun previewShowsEachRatingsInterval() {
        val fsrs = Fsrs(fuzz = null)
        val preview = fsrs.preview(fsrs.newCard(start), start)
        assertEquals(Fsrs.MINUTE, preview[Rating.Again])
        assertEquals(330_000L, preview[Rating.Hard])
        assertEquals(10 * Fsrs.MINUTE, preview[Rating.Good])
        assertEquals(8 * Fsrs.DAY, preview[Rating.Easy])
    }

    @Test
    fun fuzzStaysNearTheInterval() {
        val plain = Fsrs(fuzz = null)
        val fuzzy = Fsrs(fuzz = Random(42))
        var card = plain.newCard(start)
        card = plain.review(card, Rating.Easy, start)
        card = plain.review(card, Rating.Good, card.due)
        val exact = plain.review(card, Rating.Good, card.due).due - card.due
        val days = mutableSetOf<Long>()
        repeat(200) {
            val interval = fuzzy.review(card, Rating.Good, card.due).due - card.due
            assertEquals(0L, interval % Fsrs.DAY)
            assertTrue(abs(interval - exact) <= exact / 10 + 2 * Fsrs.DAY)
            days += interval / Fsrs.DAY
        }
        assertTrue("fuzz spreads intervals", days.size > 1)
    }

    @Test
    fun learningStepsAreNeverFuzzed() {
        val fsrs = Fsrs(fuzz = Random(1))
        val card = fsrs.review(fsrs.newCard(start), Rating.Good, start)
        assertEquals(start + 10 * Fsrs.MINUTE, card.due)
    }

    @Test
    fun retrievabilityFallsOverTime() {
        val fsrs = Fsrs(fuzz = null)
        val card = fsrs.review(fsrs.newCard(start), Rating.Easy, start)
        assertEquals(1.0, fsrs.retrievability(card, start), 1e-12)
        val atDue = fsrs.retrievability(card, card.due)
        assertTrue(atDue < 1.0 && atDue > 0.85)
        assertEquals(0.0, fsrs.retrievability(fsrs.newCard(start), start), 0.0)
    }
}
