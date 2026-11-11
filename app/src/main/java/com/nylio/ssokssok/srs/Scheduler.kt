package com.nylio.ssokssok.srs

import com.nylio.ssokssok.data.db.CardState
import com.nylio.ssokssok.data.db.ProgressEntity
import java.util.Locale
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * The three answers the learner can give.
 *
 * Internally they map onto FSRS grades: Hard -> Again (1), Medium -> Hard (2), Easy -> Good (3).
 * "Easy" here means "it came straight back", which is what FSRS calls a normal successful recall.
 */
enum class Rating(val grade: Int, val label: String, val hint: String) {
    HARD(1, "Hard", "Didn't know"),
    MEDIUM(2, "Medium", "Took a moment"),
    EASY(3, "Easy", "Instant");

    companion object {
        fun of(grade: Int) = entries.first { it.grade == grade }
    }
}

const val MINUTE_MS = 60_000L
const val DAY_MS = 86_400_000L

/** FSRS-5 memory model with the published default parameters. */
object Fsrs {
    val W = doubleArrayOf(
        0.40255, 1.18385, 3.173, 15.69105, 7.1949, 0.5345, 1.4604, 0.0046, 1.54575, 0.1192,
        1.01925, 1.9395, 0.11, 0.29605, 2.2698, 0.2315, 2.9898, 0.51655, 0.6621,
    )
    private const val DECAY = -0.5
    private const val FACTOR = 19.0 / 81.0

    fun retrievability(elapsedDays: Double, stability: Double): Double =
        if (stability <= 0) 0.0 else (1 + FACTOR * elapsedDays.coerceAtLeast(0.0) / stability).pow(DECAY)

    fun intervalDays(stability: Double, retention: Double): Double =
        stability / FACTOR * (retention.pow(1 / DECAY) - 1)

    fun initStability(grade: Int): Double = W[grade - 1].coerceAtLeast(0.1)

    fun initDifficulty(grade: Int): Double = (W[4] - exp(W[5] * (grade - 1)) + 1).coerceIn(1.0, 10.0)

    fun nextDifficulty(d: Double, grade: Int): Double {
        val delta = -W[6] * (grade - 3)
        val damped = d + delta * (10 - d) / 9
        val reverted = W[7] * initDifficulty(4) + (1 - W[7]) * damped
        return reverted.coerceIn(1.0, 10.0)
    }

    fun recallStability(d: Double, s: Double, r: Double, grade: Int): Double {
        val hardPenalty = if (grade == 2) W[15] else 1.0
        val easyBonus = if (grade == 4) W[16] else 1.0
        return s * (1 + exp(W[8]) * (11 - d) * s.pow(-W[9]) * (exp(W[10] * (1 - r)) - 1) * hardPenalty * easyBonus)
    }

    fun forgetStability(d: Double, s: Double, r: Double): Double =
        min(W[11] * d.pow(-W[12]) * ((s + 1).pow(W[13]) - 1) * exp(W[14] * (1 - r)), s)

    fun shortTermStability(s: Double, grade: Int): Double = s * exp(W[17] * (grade - 3 + W[18]))
}

data class ScheduleResult(
    val progress: ProgressEntity,
    /** True when the card should come back later in the same session. */
    val requeue: Boolean,
    val delayMs: Long,
)

class Scheduler(
    private val retention: Double = 0.9,
    private val maxIntervalDays: Int = 3650,
    private val random: Random = Random.Default,
) {
    fun schedule(p: ProgressEntity, rating: Rating, now: Long, fuzz: Boolean = true): ScheduleResult {
        val g = rating.grade
        val elapsedDays = p.lastReview?.let { (now - it).toDouble() / DAY_MS } ?: 0.0
        val introduced = p.introducedAt ?: now
        val base = p.copy(reps = p.reps + 1, lastReview = now, introducedAt = introduced)

        return when (p.state) {
            CardState.NEW -> {
                val s = Fsrs.initStability(g)
                val d = Fsrs.initDifficulty(g)
                when (rating) {
                    Rating.HARD -> learning(base, s, d, CardState.LEARNING, step = 0, now, MINUTE_MS)
                    Rating.MEDIUM -> learning(base, s, d, CardState.LEARNING, step = 1, now, 10 * MINUTE_MS)
                    Rating.EASY -> graduate(base, s, d, now, fuzz)
                }
            }

            CardState.LEARNING, CardState.RELEARNING -> {
                val s = memoryAfter(p, g, elapsedDays)
                val d = Fsrs.nextDifficulty(p.difficulty.orInit(g), g)
                when (rating) {
                    Rating.HARD -> learning(base, s, d, p.state, step = 0, now, MINUTE_MS)
                    Rating.MEDIUM ->
                        if (p.step >= 1) graduate(base, s, d, now, fuzz)
                        else learning(base, s, d, p.state, step = 1, now, 10 * MINUTE_MS)
                    Rating.EASY -> graduate(base, s, d, now, fuzz)
                }
            }

            else -> { // REVIEW
                val s = memoryAfter(p, g, elapsedDays)
                val d = Fsrs.nextDifficulty(p.difficulty.orInit(g), g)
                if (rating == Rating.HARD) {
                    learning(base.copy(lapses = p.lapses + 1), s, d, CardState.RELEARNING, step = 0, now, MINUTE_MS)
                } else {
                    graduate(base, s, d, now, fuzz, minDays = if (rating == Rating.EASY) p.scheduledDays + 1 else 1.0)
                }
            }
        }
    }

    /** Delay until the next review for each rating, without fuzz. Used for the button hints. */
    fun preview(p: ProgressEntity, now: Long): Map<Rating, Long> =
        Rating.entries.associateWith { schedule(p, it, now, fuzz = false).delayMs }

    private fun memoryAfter(p: ProgressEntity, grade: Int, elapsedDays: Double): Double {
        val s0 = if (p.stability > 0) p.stability else Fsrs.initStability(grade)
        val d0 = p.difficulty.orInit(grade)
        return if (elapsedDays < 1.0) {
            Fsrs.shortTermStability(s0, grade)
        } else {
            val r = Fsrs.retrievability(elapsedDays, s0)
            if (grade == 1) Fsrs.forgetStability(d0, s0, r) else Fsrs.recallStability(d0, s0, r, grade)
        }.coerceIn(0.1, 36500.0)
    }

    private fun Double.orInit(grade: Int) = if (this > 0) this else Fsrs.initDifficulty(grade)

    private fun learning(
        p: ProgressEntity, s: Double, d: Double, state: Int, step: Int, now: Long, delay: Long,
    ) = ScheduleResult(
        p.copy(state = state, stability = s, difficulty = d, step = step, due = now + delay, scheduledDays = 0.0),
        requeue = true,
        delayMs = delay,
    )

    private fun graduate(
        p: ProgressEntity, s: Double, d: Double, now: Long, fuzz: Boolean, minDays: Double = 1.0,
    ): ScheduleResult {
        var days = Fsrs.intervalDays(s, retention).coerceAtLeast(minDays).coerceIn(1.0, maxIntervalDays.toDouble())
        days = days.roundToInt().toDouble().coerceAtLeast(1.0)
        if (fuzz && days >= 3) {
            val spread = (days * 0.05).coerceAtLeast(1.0)
            days = (days + random.nextDouble(-spread, spread)).roundToInt().toDouble().coerceAtLeast(2.0)
        }
        val delay = (days * DAY_MS).toLong()
        return ScheduleResult(
            p.copy(state = CardState.REVIEW, stability = s, difficulty = d, step = 0, due = now + delay, scheduledDays = days),
            requeue = false,
            delayMs = delay,
        )
    }
}

/** Short human label for a delay: "1m", "10m", "3d", "2mo", "1.5y". */
fun formatDelay(ms: Long): String {
    val minutes = ms / MINUTE_MS
    val days = ms.toDouble() / DAY_MS
    return when {
        minutes < 60 -> "${minutes.coerceAtLeast(1)}m"
        days < 1 -> "${(minutes / 60)}h"
        days < 30 -> "${days.roundToInt()}d"
        days < 365 -> {
            val months = days / 30.0
            if (months < 10) String.format(Locale.US, "%.1fmo", months).replace(".0mo", "mo") else "${months.roundToInt()}mo"
        }
        else -> String.format(Locale.US, "%.1fy", days / 365.0).replace(".0y", "y")
    }
}
