package ma.techmare.recall.fsrs

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToLong

/** Card learning state, stored as an Int in the database. */
object CardState {
    const val NEW = 0
    const val LEARNING = 1
    const val REVIEW = 2
    const val RELEARNING = 3
}

enum class Grade(val value: Int) { AGAIN(1), HARD(2), GOOD(3), EASY(4) }

/** The scheduling part of a card. */
data class Memory(
    val state: Int = CardState.NEW,
    val stability: Double = 0.0,
    val difficulty: Double = 0.0,
    val due: Long = 0L,          // epoch millis
    val lastReview: Long = 0L,   // epoch millis, 0 = never
    val reps: Int = 0,
    val lapses: Int = 0,
    val step: Int = 0,
)

/**
 * FSRS-5 scheduler (the algorithm modern Anki uses), with Anki-style
 * learning steps for new and forgotten cards.
 */
class Fsrs(
    private val desiredRetention: Double = 0.9,
    private val maximumIntervalDays: Int = 36500,
    private val learningStepsMin: List<Int> = listOf(1, 10),
    private val relearningStepsMin: List<Int> = listOf(10),
    private val w: DoubleArray = DEFAULT_W,
) {
    companion object {
        val DEFAULT_W = doubleArrayOf(
            0.40255, 1.18385, 3.173, 15.69105, 7.1949, 0.5345, 1.4604, 0.0046,
            1.54575, 0.1192, 1.01925, 1.9395, 0.11, 0.29605, 2.2698, 0.2315,
            2.9898, 0.51655, 0.6621,
        )
        private const val DECAY = -0.5
        private const val FACTOR = 19.0 / 81.0
        const val MINUTE = 60_000L
        const val DAY = 86_400_000L
    }

    fun retrievability(elapsedDays: Double, stability: Double): Double =
        if (stability <= 0) 0.0 else (1 + FACTOR * elapsedDays / stability).pow(DECAY)

    fun intervalDays(stability: Double): Int {
        val raw = stability / FACTOR * (desiredRetention.pow(1 / DECAY) - 1)
        return raw.roundToLong().toInt().coerceIn(1, maximumIntervalDays)
    }

    private fun initStability(g: Int) = max(w[g - 1], 0.1)

    private fun initDifficulty(g: Int) = (w[4] - exp(w[5] * (g - 1)) + 1).coerceIn(1.0, 10.0)

    private fun nextDifficulty(d: Double, g: Int): Double {
        val delta = -w[6] * (g - 3)
        val dPrime = d + delta * (10 - d) / 9
        val reverted = w[7] * initDifficulty(4) + (1 - w[7]) * dPrime
        return reverted.coerceIn(1.0, 10.0)
    }

    private fun recallStability(d: Double, s: Double, r: Double, g: Int): Double {
        val hard = if (g == 2) w[15] else 1.0
        val easy = if (g == 4) w[16] else 1.0
        return s * (exp(w[8]) * (11 - d) * s.pow(-w[9]) * (exp(w[10] * (1 - r)) - 1) * hard * easy + 1)
    }

    private fun forgetStability(d: Double, s: Double, r: Double): Double {
        val sf = w[11] * d.pow(-w[12]) * ((s + 1).pow(w[13]) - 1) * exp(w[14] * (1 - r))
        return min(sf, s / exp(w[17] * w[18]))
    }

    private fun shortTermStability(s: Double, g: Int) = max(s * exp(w[17] * (g - 3 + w[18])), 0.1)

    /** Result of grading a card now. */
    fun next(m: Memory, grade: Grade, now: Long): Memory {
        val g = grade.value
        val elapsed = if (m.lastReview == 0L) 0.0 else (now - m.lastReview).toDouble() / DAY

        // Update memory model.
        val (s, d) = when {
            m.state == CardState.NEW -> initStability(g) to initDifficulty(g)
            elapsed < 1.0 -> shortTermStability(m.stability, g) to nextDifficulty(m.difficulty, g)
            else -> {
                val r = retrievability(elapsed, m.stability)
                val ns = if (g == 1) forgetStability(m.difficulty, m.stability, r)
                else recallStability(m.difficulty, m.stability, r, g)
                ns to nextDifficulty(m.difficulty, g)
            }
        }

        val base = m.copy(stability = s, difficulty = d, lastReview = now, reps = m.reps + 1)

        return when (m.state) {
            CardState.NEW, CardState.LEARNING -> stepped(base, g, now, learningStepsMin, CardState.LEARNING,
                if (m.state == CardState.NEW) -1 else m.step)
            CardState.RELEARNING -> stepped(base, g, now, relearningStepsMin, CardState.RELEARNING, m.step)
            else -> { // REVIEW
                if (g == 1) {
                    if (relearningStepsMin.isEmpty()) scheduleReview(base.copy(lapses = m.lapses + 1), now)
                    else base.copy(state = CardState.RELEARNING, step = 0, lapses = m.lapses + 1,
                        due = now + relearningStepsMin[0] * MINUTE)
                } else scheduleReview(base, now)
            }
        }
    }

    private fun stepped(base: Memory, g: Int, now: Long, steps: List<Int>, learnState: Int, current: Int): Memory {
        if (steps.isEmpty() || g == 4) return scheduleReview(base, now)
        val cur = current.coerceAtLeast(0)
        return when (g) {
            1 -> base.copy(state = learnState, step = 0, due = now + steps[0] * MINUTE)
            2 -> {
                // Hard: repeat the current step (first step: halfway to the next one).
                val mins = if (cur == 0 && steps.size > 1) (steps[0] + steps[1]) / 2.0
                else if (cur == 0) steps[0] * 1.5 else steps[cur].toDouble()
                base.copy(state = learnState, step = cur, due = now + (mins * MINUTE).toLong())
            }
            else -> { // Good
                val nextStep = if (current < 0) 1 else cur + 1
                if (current < 0 && steps.size > 1) base.copy(state = learnState, step = 1, due = now + steps[1] * MINUTE)
                else if (nextStep < steps.size) base.copy(state = learnState, step = nextStep, due = now + steps[nextStep] * MINUTE)
                else scheduleReview(base, now)
            }
        }
    }

    private fun scheduleReview(m: Memory, now: Long): Memory {
        val days = intervalDays(m.stability)
        return m.copy(state = CardState.REVIEW, step = 0, due = now + days * DAY)
    }

    /** Short labels for the four grade buttons ("1 min", "2 days"…). */
    fun previewLabels(m: Memory, now: Long): List<String> =
        Grade.entries.map { formatInterval(next(m, it, now).due - now) }
}

fun formatInterval(ms: Long): String {
    val min = ms / 60_000.0
    return when {
        min < 60 -> "${max(1, min.roundToLong().toInt())} min"
        min < 60 * 24 -> "${(min / 60).roundToLong()} h"
        min < 60 * 24 * 30 -> {
            val d = (min / 1440).roundToLong()
            if (d == 1L) "1 day" else "$d days"
        }
        min < 60 * 24 * 365 -> "${"%.1f".format(min / 43_200).removeSuffix(".0")} mo"
        else -> "${"%.1f".format(min / 525_600).removeSuffix(".0")} yr"
    }
}
