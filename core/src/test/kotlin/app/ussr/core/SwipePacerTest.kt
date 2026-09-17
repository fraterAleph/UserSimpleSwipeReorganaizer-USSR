package app.ussr.core

import app.ussr.core.pacing.PacingEvent
import app.ussr.core.pacing.PacingLevel
import app.ussr.core.pacing.SwipeDirection
import app.ussr.core.pacing.SwipePacer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SwipePacerTest {

    private val mb = 1024L * 1024

    @Test
    fun `steady swiping builds a combo and reports milestones`() {
        val pacer = SwipePacer()
        var now = 0L
        repeat(10) {
            now += 1_500
            pacer.onDecision(SwipeDirection.Delete, mb, now)
        }
        val state = pacer.state()
        assertEquals(10, state.combo)
        assertEquals(10 * mb, state.reclaimedBytes)
        assertEquals(PacingLevel.Calm, state.level)
        assertTrue(state.events.any { it is PacingEvent.ComboMilestone && it.combo == 10 })
    }

    @Test
    fun `rushing breaks the combo and raises a nudge`() {
        val pacer = SwipePacer()
        var now = 0L
        repeat(6) {
            now += 1_500
            pacer.onDecision(SwipeDirection.Delete, mb, now)
        }
        assertEquals(6, pacer.state().combo)

        // Four decisions in a row under the rush threshold.
        repeat(4) {
            now += 200
            pacer.onDecision(SwipeDirection.Delete, mb, now)
        }
        val state = pacer.state()
        assertEquals(0, state.combo)
        assertEquals(PacingLevel.Nudge, state.level)
        val tooFast = state.events.filterIsInstance<PacingEvent.TooFast>().single()
        assertEquals(9, tooFast.comboLost)
        assertEquals(9, state.longestCombo)
    }

    @Test
    fun `the reclaimed total survives a broken combo`() {
        val pacer = SwipePacer()
        var now = 0L
        repeat(10) {
            now += 100
            pacer.onDecision(SwipeDirection.Delete, mb, now)
        }
        assertEquals(10 * mb, pacer.state().reclaimedBytes)
        assertEquals(10, pacer.state().decisions)
    }

    @Test
    fun `a long run of deletions warns even at a calm pace`() {
        val pacer = SwipePacer()
        var now = 0L
        repeat(25) {
            now += 2_000
            pacer.onDecision(SwipeDirection.Delete, mb, now)
        }
        val state = pacer.state()
        assertEquals(25, state.deleteStreak)
        assertTrue(state.events.any { it is PacingEvent.DeleteStreak && it.streak == 25 })
        assertEquals(PacingLevel.Nudge, state.level)
    }

    @Test
    fun `a keep resets the delete streak but not the combo`() {
        val pacer = SwipePacer()
        var now = 0L
        repeat(5) {
            now += 2_000
            pacer.onDecision(SwipeDirection.Delete, mb, now)
        }
        now += 2_000
        val state = pacer.onDecision(SwipeDirection.Keep, mb, now)
        assertEquals(0, state.deleteStreak)
        assertEquals(6, state.combo)
        assertEquals(5 * mb, state.reclaimedBytes)
    }

    @Test
    fun `every fiftieth card blocks with a checkpoint`() {
        val pacer = SwipePacer()
        var now = 0L
        repeat(50) {
            now += 2_000
            pacer.onDecision(SwipeDirection.Delete, mb, now)
        }
        val state = pacer.state()
        assertEquals(PacingLevel.Checkpoint, state.level)
        val checkpoint = state.events.filterIsInstance<PacingEvent.Checkpoint>().single()
        assertEquals(50, checkpoint.decisions)
        assertEquals(50, checkpoint.pendingDeletions)

        pacer.acknowledge()
        assertEquals(PacingLevel.Calm, pacer.state().level)
    }

    @Test
    fun `hardcore brakes on a pace normal mode allows`() {
        val normal = SwipePacer()
        val hardcore = SwipePacer(SwipePacer.Config.hardcore())
        var now = 0L
        val normalEvents = mutableListOf<PacingEvent>()
        val hardcoreEvents = mutableListOf<PacingEvent>()

        // 800ms per card: unhurried by normal standards, too fast for a deck of favourites.
        repeat(4) {
            now += 800
            normalEvents += normal.onDecision(SwipeDirection.Delete, mb, now).events
            hardcoreEvents += hardcore.onDecision(SwipeDirection.Delete, mb, now).events
        }

        assertTrue(normalEvents.none { it is PacingEvent.TooFast })
        assertEquals(4, normal.state().combo)
        assertTrue(hardcoreEvents.any { it is PacingEvent.TooFast })
        // The brake fired on the third card, so the streak only got going again after it.
        assertEquals(1, hardcore.state().combo)
    }

    @Test
    fun `hardcore stops for a checkpoint four times as often`() {
        val pacer = SwipePacer(SwipePacer.Config.hardcore())
        var now = 0L
        repeat(12) {
            now += 3_000
            pacer.onDecision(SwipeDirection.Delete, mb, now)
        }
        val state = pacer.state()
        assertEquals(PacingLevel.Checkpoint, state.level)
        assertEquals(12, state.events.filterIsInstance<PacingEvent.Checkpoint>().single().decisions)
    }

    @Test
    fun `a long session asks for a rest`() {
        val pacer = SwipePacer(SwipePacer.Config(restAfter = 60, checkpointEvery = 1000))
        var now = 0L
        repeat(60) {
            now += 2_000
            pacer.onDecision(SwipeDirection.Keep, 0, now)
        }
        assertEquals(PacingLevel.Rest, pacer.state().level)
    }

    @Test
    fun `an undo costs the combo and gives the bytes back`() {
        val pacer = SwipePacer()
        var now = 0L
        repeat(5) {
            now += 2_000
            pacer.onDecision(SwipeDirection.Delete, mb, now)
        }
        val state = pacer.onUndo(SwipeDirection.Delete, mb)
        assertEquals(0, state.combo)
        assertEquals(4, state.pendingDeletions)
        assertEquals(4 * mb, state.reclaimedBytes)
        assertEquals(PacingLevel.Nudge, state.level)
    }
}
