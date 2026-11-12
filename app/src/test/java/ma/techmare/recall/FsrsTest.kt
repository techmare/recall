package ma.techmare.recall

import ma.techmare.recall.fsrs.CardState
import ma.techmare.recall.fsrs.Fsrs
import ma.techmare.recall.fsrs.Grade
import ma.techmare.recall.fsrs.Memory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FsrsTest {
    private val f = Fsrs()
    private val t0 = 1_700_000_000_000L
    private val MIN = 60_000L
    private val DAY = 86_400_000L

    @Test fun newCardLearningSteps() {
        val again = f.next(Memory(), Grade.AGAIN, t0)
        assertEquals(CardState.LEARNING, again.state)
        assertEquals(t0 + 1 * MIN, again.due)

        val good = f.next(Memory(), Grade.GOOD, t0)
        assertEquals(CardState.LEARNING, good.state)
        assertEquals(t0 + 10 * MIN, good.due)

        val easy = f.next(Memory(), Grade.EASY, t0)
        assertEquals(CardState.REVIEW, easy.state)
        assertTrue("easy interval should be days", easy.due - t0 >= 7 * DAY)
    }

    @Test fun graduatesAfterSecondGood() {
        val s1 = f.next(Memory(), Grade.GOOD, t0)
        val s2 = f.next(s1, Grade.GOOD, s1.due)
        assertEquals(CardState.REVIEW, s2.state)
        assertTrue(s2.due - s1.due >= DAY)
    }

    @Test fun intervalsGrowWithGoodAndLapseResets() {
        var m = f.next(f.next(Memory(), Grade.GOOD, t0), Grade.GOOD, t0 + 10 * MIN)
        var prev = 0L
        var now = m.due
        repeat(4) {
            val interval = m.due - (m.lastReview)
            assertTrue("interval should grow", interval > prev)
            prev = interval
            now = m.due
            m = f.next(m, Grade.GOOD, now)
        }
        val lapse = f.next(m, Grade.AGAIN, m.due)
        assertEquals(CardState.RELEARNING, lapse.state)
        assertEquals(1, lapse.lapses)
        assertTrue(lapse.stability < m.stability)
        val back = f.next(lapse, Grade.GOOD, lapse.due)
        assertEquals(CardState.REVIEW, back.state)
    }

    @Test fun gradeOrderingOnReview() {
        val m = f.next(f.next(Memory(), Grade.GOOD, t0), Grade.GOOD, t0 + 10 * MIN)
        val now = m.due
        val hard = f.next(m, Grade.HARD, now).due
        val good = f.next(m, Grade.GOOD, now).due
        val easy = f.next(m, Grade.EASY, now).due
        assertTrue(hard <= good && good < easy)
        println(f.previewLabels(m, now))
        println(f.previewLabels(Memory(), now))
    }
}
