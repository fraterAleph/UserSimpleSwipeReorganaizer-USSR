package app.ussr.core.pacing

enum class SwipeDirection { Delete, Keep, Favorite, Skip }

/**
 * How hard the app is currently pushing back on the pace.
 *
 * The point is not to score the user. Triage goes wrong the same way every time: the first
 * fifty cards really are junk, the hand learns the gesture, attention leaves, and card
 * fifty-one gets deleted unseen. So the combo counter rewards *how much* was sorted, and
 * rushing costs it — the one thing the streak cannot survive is going too fast.
 */
enum class PacingLevel {
    /** Nothing in the way. */
    Calm,

    /** A visible nudge on the card: slow down, you are not reading these. */
    Nudge,

    /** A blocking checkpoint the user has to dismiss before the deck continues. */
    Checkpoint,

    /** Enough for one sitting — offer the review screen instead of more cards. */
    Rest,
}

sealed interface PacingEvent {
    /** Combo reached a round number worth showing. */
    data class ComboMilestone(val combo: Int) : PacingEvent

    /** The last few decisions came in faster than a human can look at a photo. */
    data class TooFast(val averageIntervalMs: Long, val comboLost: Int) : PacingEvent

    /** A long unbroken run of deletions — no longer looking, just wiping. */
    data class DeleteStreak(val streak: Int) : PacingEvent

    /** A fixed number of cards has gone by; stop and confirm before continuing. */
    data class Checkpoint(val decisions: Int, val pendingDeletions: Int, val reclaimedBytes: Long) : PacingEvent

    /** The session is long enough that accuracy is falling. */
    data class SessionTooLong(val decisions: Int) : PacingEvent
}

data class PacingState(
    val combo: Int = 0,
    val longestCombo: Int = 0,
    val decisions: Int = 0,
    val pendingDeletions: Int = 0,
    val reclaimedBytes: Long = 0,
    val deleteStreak: Int = 0,
    val level: PacingLevel = PacingLevel.Calm,
    val events: List<PacingEvent> = emptyList(),
)

/**
 * Pure state machine: the caller passes a clock reading with every decision, so the whole
 * thing is deterministic and testable without a device.
 */
class SwipePacer(private val config: Config = Config()) {

    data class Config(
        /** A decision faster than this was a reflex, not a look. */
        val rushIntervalMs: Long = 550,
        /** How many consecutive rushed decisions before the brake goes on. */
        val rushRunToBrake: Int = 4,
        /** Deletions in a row that trip the "you stopped looking" warning. */
        val deleteStreakWarning: Int = 25,
        /** Cards between blocking checkpoints. */
        val checkpointEvery: Int = 50,
        /** Decisions after which the app suggests wrapping the session up. */
        val restAfter: Int = 200,
        /** Combo values worth celebrating. */
        val comboMilestones: Set<Int> = setOf(10, 25, 50, 100, 200),
    )

    private var state = PacingState()
    private var lastDecisionAtMs: Long? = null
    private var rushRun = 0
    private var lastCheckpointAt = 0

    fun state(): PacingState = state

    /**
     * Record one swipe. [itemBytes] counts towards the reclaimed total only for deletions.
     */
    fun onDecision(direction: SwipeDirection, itemBytes: Long, nowMs: Long): PacingState {
        val events = mutableListOf<PacingEvent>()
        val interval = lastDecisionAtMs?.let { nowMs - it }
        lastDecisionAtMs = nowMs

        rushRun = if (interval != null && interval < config.rushIntervalMs) rushRun + 1 else 0
        val rushing = rushRun >= config.rushRunToBrake

        var combo = state.combo + 1
        var level = PacingLevel.Calm

        if (rushing) {
            events += PacingEvent.TooFast(
                averageIntervalMs = interval ?: 0,
                comboLost = state.combo,
            )
            combo = 0
            rushRun = 0
            level = PacingLevel.Nudge
        }

        val deleteStreak = if (direction == SwipeDirection.Delete) state.deleteStreak + 1 else 0
        if (deleteStreak > 0 && deleteStreak % config.deleteStreakWarning == 0) {
            events += PacingEvent.DeleteStreak(deleteStreak)
            level = maxLevel(level, PacingLevel.Nudge)
        }

        val decisions = state.decisions + 1
        val pendingDeletions = state.pendingDeletions + if (direction == SwipeDirection.Delete) 1 else 0
        val reclaimed = state.reclaimedBytes + if (direction == SwipeDirection.Delete) itemBytes else 0

        if (combo in config.comboMilestones) {
            events += PacingEvent.ComboMilestone(combo)
        }

        if (decisions - lastCheckpointAt >= config.checkpointEvery) {
            lastCheckpointAt = decisions
            events += PacingEvent.Checkpoint(decisions, pendingDeletions, reclaimed)
            level = maxLevel(level, PacingLevel.Checkpoint)
        }

        if (decisions >= config.restAfter) {
            events += PacingEvent.SessionTooLong(decisions)
            level = maxLevel(level, PacingLevel.Rest)
        }

        state = PacingState(
            combo = combo,
            longestCombo = maxOf(state.longestCombo, combo),
            decisions = decisions,
            pendingDeletions = pendingDeletions,
            reclaimedBytes = reclaimed,
            deleteStreak = deleteStreak,
            level = level,
            events = events,
        )
        return state
    }

    /**
     * Taking a decision back is the clearest signal that the pace outran attention: it
     * breaks the combo and forces the nudge, whatever the timing said.
     */
    fun onUndo(direction: SwipeDirection, itemBytes: Long): PacingState {
        state = state.copy(
            combo = 0,
            decisions = maxOf(0, state.decisions - 1),
            pendingDeletions = if (direction == SwipeDirection.Delete) {
                maxOf(0, state.pendingDeletions - 1)
            } else {
                state.pendingDeletions
            },
            reclaimedBytes = if (direction == SwipeDirection.Delete) {
                maxOf(0, state.reclaimedBytes - itemBytes)
            } else {
                state.reclaimedBytes
            },
            deleteStreak = 0,
            level = PacingLevel.Nudge,
            events = emptyList(),
        )
        rushRun = 0
        return state
    }

    /** The user dismissed a checkpoint or a nudge; cards resume. */
    fun acknowledge(): PacingState {
        state = state.copy(level = PacingLevel.Calm, events = emptyList())
        lastDecisionAtMs = null
        rushRun = 0
        return state
    }

    private fun maxLevel(a: PacingLevel, b: PacingLevel): PacingLevel =
        if (a.ordinal >= b.ordinal) a else b
}
