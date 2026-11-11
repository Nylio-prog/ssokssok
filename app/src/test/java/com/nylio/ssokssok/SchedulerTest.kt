package com.nylio.ssokssok

import com.nylio.ssokssok.data.db.CardState
import com.nylio.ssokssok.data.db.ProgressEntity
import com.nylio.ssokssok.srs.DAY_MS
import com.nylio.ssokssok.srs.MINUTE_MS
import com.nylio.ssokssok.srs.Rating
import com.nylio.ssokssok.srs.Scheduler
import com.nylio.ssokssok.srs.formatDelay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SchedulerTest {
    private val scheduler = Scheduler(retention = 0.9)
    private val t0 = 1_700_000_000_000L
    private val fresh = ProgressEntity(cardId = 1, direction = 0)

    @Test
    fun newCardHardComesBackInTheSession() {
        val r = scheduler.schedule(fresh, Rating.HARD, t0, fuzz = false)
        assertTrue(r.requeue)
        assertEquals(CardState.LEARNING, r.progress.state)
        assertEquals(t0 + MINUTE_MS, r.progress.due)
        assertEquals(t0, r.progress.introducedAt)
    }

    @Test
    fun newCardEasyGraduatesForDays() {
        val r = scheduler.schedule(fresh, Rating.EASY, t0, fuzz = false)
        assertFalse(r.requeue)
        assertEquals(CardState.REVIEW, r.progress.state)
        assertEquals(3.0, r.progress.scheduledDays, 0.0)
    }

    @Test
    fun mediumTwiceInLearningGraduates() {
        val a = scheduler.schedule(fresh, Rating.MEDIUM, t0, fuzz = false)
        assertTrue(a.requeue)
        val b = scheduler.schedule(a.progress, Rating.MEDIUM, t0 + 10 * MINUTE_MS, fuzz = false)
        assertFalse(b.requeue)
        assertEquals(CardState.REVIEW, b.progress.state)
        assertTrue(b.progress.scheduledDays >= 1.0)
    }

    @Test
    fun intervalsGrowWithEasyAndOrderByRating() {
        var p = scheduler.schedule(fresh, Rating.EASY, t0, fuzz = false).progress
        var now = t0
        var last = p.scheduledDays
        repeat(4) {
            now += (p.scheduledDays * DAY_MS).toLong()
            val preview = scheduler.preview(p, now)
            assertTrue(preview[Rating.HARD]!! < preview[Rating.MEDIUM]!!)
            assertTrue(preview[Rating.MEDIUM]!! < preview[Rating.EASY]!!)
            p = scheduler.schedule(p, Rating.EASY, now, fuzz = false).progress
            assertTrue("interval should grow: $last -> ${p.scheduledDays}", p.scheduledDays > last)
            last = p.scheduledDays
        }
    }

    @Test
    fun lapseGoesToRelearningAndCountsLapse() {
        val review = scheduler.schedule(fresh, Rating.EASY, t0, fuzz = false).progress
        val now = t0 + (review.scheduledDays * DAY_MS).toLong()
        val r = scheduler.schedule(review, Rating.HARD, now, fuzz = false)
        assertEquals(CardState.RELEARNING, r.progress.state)
        assertEquals(1, r.progress.lapses)
        assertTrue(r.requeue)
        assertTrue(r.progress.stability <= review.stability)
    }

    @Test
    fun formatsDelays() {
        assertEquals("1m", formatDelay(MINUTE_MS))
        assertEquals("10m", formatDelay(10 * MINUTE_MS))
        assertEquals("3d", formatDelay(3 * DAY_MS))
        assertEquals("2mo", formatDelay(60 * DAY_MS))
        assertEquals("1.5y", formatDelay((365 * 1.5 * DAY_MS).toLong()))
    }
}
