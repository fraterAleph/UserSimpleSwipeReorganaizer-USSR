package app.ussr.core.queue

import app.ussr.core.model.MediaItem
import app.ussr.core.scoring.Category
import app.ussr.core.scoring.TriageMode
import app.ussr.core.scoring.Verdict

/**
 * A card in a deck: the item, why it is here, and whether the app is confident enough to
 * pre-tick it in the batch grid instead of asking for a swipe.
 */
data class Card(
    val item: MediaItem,
    val verdict: Verdict,
    val batchable: Boolean,
)

/**
 * One category's worth of cards, already ordered.
 */
data class Deck(
    val category: Category,
    val cards: List<Card>,
    val mode: TriageMode = TriageMode.Normal,
) {
    val size: Int get() = cards.size
    val reclaimableBytes: Long get() = cards.sumOf { it.item.sizeBytes }
    val batchableCards: List<Card> get() = cards.filter { it.batchable }

    /** Average confidence, so the deck can honestly label itself on the picker screen. */
    val confidence: Double
        get() = if (cards.isEmpty()) 0.0 else cards.sumOf { it.verdict.confidence } / cards.size
}

object QueueBuilder {

    /** Above this deal priority the app is willing to pre-tick an item in the batch grid. */
    const val BATCH_PRIORITY = 0.8

    /** Cards below this never make it into a deck — showing them is wasting the user's time. */
    const val DECK_FLOOR = 0.12

    /**
     * Hardcore shows everything it has, down to a much lower bar. The deck is small and the
     * user opened it deliberately, so hiding cards would just make it look empty.
     */
    const val HARDCORE_FLOOR = 0.02

    /**
     * Build one deck per category, each ordered for the mode it belongs to.
     *
     * In [TriageMode.Normal] the ordering is [Verdict.dealPriority], not the junk score: the
     * first cards of a session are the ones that are simultaneously likely junk *and* cheap
     * to get wrong, which is what makes an early rhythm safe. Anything expensive to lose
     * sinks towards the end even when the score is high, and protected items never appear.
     *
     * [TriageMode.Hardcore] deals exactly the complement — only the protected items, ordered
     * by [Verdict.hardcorePriority] — and never marks a card batchable, because a pre-ticked
     * grid of favourites is precisely the mistake this app exists to avoid.
     */
    fun build(
        items: List<MediaItem>,
        verdicts: Map<Long, Verdict>,
        mode: TriageMode = TriageMode.Normal,
        floor: Double = if (mode == TriageMode.Hardcore) HARDCORE_FLOOR else DECK_FLOOR,
    ): List<Deck> {
        val hardcore = mode == TriageMode.Hardcore
        val cards = items.mapNotNull { item ->
            val verdict = verdicts[item.id] ?: return@mapNotNull null
            if (verdict.protected != hardcore) return@mapNotNull null
            val priority = if (hardcore) verdict.hardcorePriority else verdict.dealPriority
            if (priority < floor) return@mapNotNull null
            Card(
                item = item,
                verdict = verdict,
                batchable = !hardcore && priority >= BATCH_PRIORITY,
            )
        }

        return cards
            .groupBy { it.verdict.category }
            .map { (category, group) ->
                val ordered = if (hardcore) {
                    group.sortedByDescending { it.verdict.hardcorePriority }
                } else {
                    group.sortedByDescending { it.verdict.dealPriority }
                }
                Deck(category, ordered, mode)
            }
            .sortedByDescending { it.confidence * it.size }
    }

    /**
     * Interleave decks into a single stream for users who just want to keep swiping.
     * Categories still come out in confidence order, but the stream alternates so a long
     * run of one kind of card does not turn into autopilot.
     */
    fun interleave(decks: List<Deck>, runLength: Int = 12): List<Card> {
        val cursors = decks.map { it.cards.iterator() }.toMutableList()
        val out = mutableListOf<Card>()
        while (cursors.any { it.hasNext() }) {
            for (cursor in cursors) {
                var taken = 0
                while (cursor.hasNext() && taken < runLength) {
                    out += cursor.next()
                    taken++
                }
            }
            cursors.removeAll { !it.hasNext() }
        }
        return out
    }
}
