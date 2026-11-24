package io.github.lenkr0.verba.review

import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

enum class Rating(val value: Int) { Again(1), Hard(2), Good(3), Easy(4) }

/** FSRS card states, with py-fsrs' numbers (stored in the database). A new card starts in Learning. */
enum class CardState(val value: Int) {
    Learning(1), Review(2), Relearning(3);

    companion object {
        fun of(value: Int) = entries.firstOrNull { it.value == value } ?: Review
    }
}

/** Scheduling data of one card. Times are epoch milliseconds (UTC). */
data class FsrsCard(
    val state: CardState,
    val step: Int?,
    val stability: Double?,
    val difficulty: Double?,
    val due: Long,
    val lastReview: Long?,
)

/**
 * FSRS-6, the spaced repetition algorithm Anki uses, ported from the
 * reference implementation py-fsrs 6.3.3 (MIT, github.com/open-spaced-repetition/py-fsrs)
 * and tested against values it computes (FsrsTest).
 *
 * Learning steps keep new and forgotten cards in short intervals the same
 * day; after that, intervals follow the predicted memory stability so that
 * a card is due when the chance of remembering it drops to [desiredRetention].
 */
class Fsrs(
    private val w: DoubleArray = DEFAULT_PARAMETERS,
    private val desiredRetention: Double = 0.9,
    private val learningSteps: List<Long> = listOf(MINUTE, 10 * MINUTE),
    private val relearningSteps: List<Long> = listOf(10 * MINUTE),
    private val maximumInterval: Int = 36_500,
    /** Random spread of review intervals (≥ 3 days), so cards learned together don't stay together. Null: none. */
    private val fuzz: Random? = Random.Default,
) {
    init {
        require(w.size == 21) { "FSRS-6 needs 21 parameters" }
    }

    private val decay = -w[20]
    private val factor = 0.9.pow(1 / decay) - 1

    fun newCard(now: Long) = FsrsCard(CardState.Learning, step = 0, stability = null, difficulty = null, due = now, lastReview = null)

    /** Probability of remembering the card at [now]. */
    fun retrievability(card: FsrsCard, now: Long): Double {
        val last = card.lastReview ?: return 0.0
        val stability = card.stability ?: return 0.0
        val elapsedDays = max(0L, Math.floorDiv(now - last, DAY))
        return (1 + factor * elapsedDays / stability).pow(decay)
    }

    /** The card after a review with [rating] at [now]. */
    fun review(card: FsrsCard, rating: Rating, now: Long): FsrsCard {
        val daysSinceLast = card.lastReview?.let { Math.floorDiv(now - it, DAY) }
        var state = card.state
        var step = card.step
        var stability = card.stability
        var difficulty = card.difficulty
        val interval: Long

        fun updateMemory() {
            val s = stability!!
            val d = difficulty!!
            stability = if (daysSinceLast != null && daysSinceLast < 1) {
                shortTermStability(s, rating)
            } else {
                nextStability(d, s, retrievability(card, now), rating)
            }
            difficulty = nextDifficulty(d, rating)
        }

        fun reviewInterval(): Long = DAY * nextInterval(stability!!)

        when (card.state) {
            CardState.Learning, CardState.Relearning -> {
                val steps = if (card.state == CardState.Learning) learningSteps else relearningSteps
                if (stability == null || difficulty == null) {
                    stability = initialStability(rating)
                    difficulty = clampDifficulty(initialDifficulty(rating))
                } else {
                    updateMemory()
                }
                val currentStep = step ?: 0
                if (steps.isEmpty() || (currentStep >= steps.size && rating != Rating.Again)) {
                    state = CardState.Review
                    step = null
                    interval = reviewInterval()
                } else {
                    when (rating) {
                        Rating.Again -> {
                            step = 0
                            interval = steps[0]
                        }
                        Rating.Hard -> interval = when {
                            currentStep == 0 && steps.size == 1 -> (steps[0] * 1.5).toLong()
                            currentStep == 0 -> (steps[0] + steps[1]) / 2
                            else -> steps[currentStep]
                        }
                        Rating.Good -> if (currentStep + 1 == steps.size) {
                            state = CardState.Review
                            step = null
                            interval = reviewInterval()
                        } else {
                            step = currentStep + 1
                            interval = steps[currentStep + 1]
                        }
                        Rating.Easy -> {
                            state = CardState.Review
                            step = null
                            interval = reviewInterval()
                        }
                    }
                }
            }
            CardState.Review -> {
                updateMemory()
                if (rating == Rating.Again && relearningSteps.isNotEmpty()) {
                    state = CardState.Relearning
                    step = 0
                    interval = relearningSteps[0]
                } else {
                    interval = reviewInterval()
                }
            }
        }
        val finalInterval = if (fuzz != null && state == CardState.Review) fuzzed(interval) else interval
        return FsrsCard(state, step, stability, difficulty, due = now + finalInterval, lastReview = now)
    }

    /** What each rating would schedule, for the labels on the rating buttons. */
    fun preview(card: FsrsCard, now: Long): Map<Rating, Long> =
        Rating.entries.associateWith { rating ->
            withoutFuzz().review(card, rating, now).due - now
        }

    private fun withoutFuzz() = Fsrs(w, desiredRetention, learningSteps, relearningSteps, maximumInterval, fuzz = null)

    private fun clampDifficulty(d: Double) = min(max(d, MIN_DIFFICULTY), MAX_DIFFICULTY)

    private fun clampStability(s: Double) = max(s, STABILITY_MIN)

    private fun initialStability(rating: Rating) = clampStability(w[rating.value - 1])

    private fun initialDifficulty(rating: Rating) = w[4] - exp(w[5] * (rating.value - 1)) + 1

    /** Days until retrievability falls to the desired retention; Python's round() (half to even). */
    private fun nextInterval(stability: Double): Long {
        val days = (stability / factor) * (desiredRetention.pow(1 / decay) - 1)
        return Math.rint(days).toLong().coerceAtLeast(1).coerceAtMost(maximumInterval.toLong())
    }

    private fun shortTermStability(stability: Double, rating: Rating): Double {
        var increase = exp(w[17] * (rating.value - 3 + w[18])) * stability.pow(-w[19])
        if (rating != Rating.Again) increase = max(increase, 1.0)
        return clampStability(stability * increase)
    }

    private fun nextDifficulty(difficulty: Double, rating: Rating): Double {
        val delta = -(w[6] * (rating.value - 3))
        val damped = difficulty + (10.0 - difficulty) * delta / 9.0
        val reverted = w[7] * initialDifficulty(Rating.Easy) + (1 - w[7]) * damped
        return clampDifficulty(reverted)
    }

    private fun nextStability(difficulty: Double, stability: Double, retrievability: Double, rating: Rating): Double {
        val next = if (rating == Rating.Again) {
            val longTerm = w[11] * difficulty.pow(-w[12]) * ((stability + 1).pow(w[13]) - 1) * exp((1 - retrievability) * w[14])
            val shortTerm = stability / exp(w[17] * w[18])
            min(longTerm, shortTerm)
        } else {
            val hardPenalty = if (rating == Rating.Hard) w[15] else 1.0
            val easyBonus = if (rating == Rating.Easy) w[16] else 1.0
            stability * (1 + exp(w[8]) * (11 - difficulty) * stability.pow(-w[9]) *
                (exp((1 - retrievability) * w[10]) - 1) * hardPenalty * easyBonus)
        }
        return clampStability(next)
    }

    /** py-fsrs' fuzz: a random whole number of days in a range that grows with the interval. */
    private fun fuzzed(interval: Long): Long {
        val days = interval / DAY
        if (days < 2.5) return interval
        var delta = 1.0
        for ((start, end, f) in FUZZ_RANGES) {
            delta += f * max(min(days.toDouble(), end) - start, 0.0)
        }
        var minIvl = Math.rint(days - delta).toLong()
        var maxIvl = Math.rint(days + delta).toLong()
        minIvl = max(2, minIvl)
        maxIvl = min(maxIvl, maximumInterval.toLong())
        minIvl = min(minIvl, maxIvl)
        val chosen = min(floor(fuzz!!.nextDouble() * (maxIvl - minIvl + 1) + minIvl).toLong(), maximumInterval.toLong())
        return chosen * DAY
    }

    companion object {
        const val MINUTE = 60_000L
        const val DAY = 86_400_000L
        private const val STABILITY_MIN = 0.001
        private const val MIN_DIFFICULTY = 1.0
        private const val MAX_DIFFICULTY = 10.0

        val DEFAULT_PARAMETERS = doubleArrayOf(
            0.212, 1.2931, 2.3065, 8.2956, 6.4133, 0.8334, 3.0194, 0.001, 1.8722, 0.1666, 0.796,
            1.4835, 0.0614, 0.2629, 1.6483, 0.6014, 1.8729, 0.5425, 0.0912, 0.0658, 0.1542,
        )

        private val FUZZ_RANGES = listOf(
            Triple(2.5, 7.0, 0.15),
            Triple(7.0, 20.0, 0.1),
            Triple(20.0, Double.POSITIVE_INFINITY, 0.05),
        )
    }
}
