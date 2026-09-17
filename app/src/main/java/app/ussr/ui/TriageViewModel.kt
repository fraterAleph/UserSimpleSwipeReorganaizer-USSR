package app.ussr.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.ussr.ServiceLocator
import app.ussr.core.model.MediaItem
import app.ussr.core.pacing.PacingLevel
import app.ussr.core.pacing.PacingState
import app.ussr.core.pacing.SwipeDirection
import app.ussr.core.pacing.SwipePacer
import app.ussr.core.queue.Card
import app.ussr.core.queue.Deck
import app.ussr.core.queue.QueueBuilder
import app.ussr.core.scoring.Category
import app.ussr.core.scoring.TriageMode
import app.ussr.data.DecisionKind
import app.ussr.work.ContentAnalysisWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SweepProgress(val done: Int, val total: Int) {
    val fraction: Float get() = if (total == 0) 1f else done.toFloat() / total
}

data class TriageUiState(
    val loading: Boolean = true,
    val mode: TriageMode = TriageMode.Normal,
    val progress: SweepProgress = SweepProgress(0, 0),
    val decks: List<Deck> = emptyList(),
    val activeCategory: Category? = null,
    val cards: List<Card> = emptyList(),
    val cursor: Int = 0,
    val pacing: PacingState = PacingState(),
    val pendingDeletions: Int = 0,
    val pendingBytes: Long = 0,
    val lastDecision: LastDecision? = null,
) {
    val current: Card? get() = cards.getOrNull(cursor)
    val next: Card? get() = cards.getOrNull(cursor + 1)
    val remaining: Int get() = (cards.size - cursor).coerceAtLeast(0)
    val blocked: Boolean get() = pacing.level == PacingLevel.Checkpoint || pacing.level == PacingLevel.Rest
    val hardcore: Boolean get() = mode == TriageMode.Hardcore
}

data class LastDecision(val item: MediaItem, val direction: SwipeDirection)

class TriageViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ServiceLocator.repository(application)
    private var pacer = SwipePacer()
    private var library: List<MediaItem> = emptyList()

    private val _state = MutableStateFlow(TriageUiState())
    val state: StateFlow<TriageUiState> = _state.asStateFlow()

    /**
     * Cheap sweep first, decks straight after. The ML worker is only enqueued afterwards so
     * the user is already swiping while it waits for a charger.
     */
    fun start() {
        if (!_state.value.loading && _state.value.decks.isNotEmpty()) return
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true)
            library = repository.sweep { done, total ->
                _state.value = _state.value.copy(progress = SweepProgress(done, total))
            }
            val decks = repository.decks(library, _state.value.mode)
            _state.value = _state.value.copy(loading = false, decks = decks)
            refreshPending()
            ContentAnalysisWorker.enqueue(getApplication())
        }
    }

    /**
     * Switch between the two halves of the library. Any deck open at the time is dropped:
     * the modes never mix, and carrying cards across would be exactly that.
     */
    fun setMode(mode: TriageMode) {
        if (mode == _state.value.mode) return
        pacer = pacerFor(mode)
        _state.value = _state.value.copy(
            mode = mode,
            activeCategory = null,
            cards = emptyList(),
            cursor = 0,
            pacing = pacer.state(),
            lastDecision = null,
        )
        viewModelScope.launch {
            _state.value = _state.value.copy(decks = repository.decks(library, mode))
        }
    }

    fun openDeck(category: Category?) {
        val decks = _state.value.decks
        val cards = if (category == null) {
            QueueBuilder.interleave(decks)
        } else {
            decks.firstOrNull { it.category == category }?.cards.orEmpty()
        }
        pacer = pacerFor(_state.value.mode)
        _state.value = _state.value.copy(
            activeCategory = category,
            cards = cards,
            cursor = 0,
            pacing = pacer.state(),
            lastDecision = null,
        )
    }

    fun closeDeck() {
        _state.value = _state.value.copy(activeCategory = null, cards = emptyList(), cursor = 0)
    }

    fun swipe(direction: SwipeDirection) {
        val card = _state.value.current ?: return
        val kind = when (direction) {
            SwipeDirection.Delete -> DecisionKind.Delete
            SwipeDirection.Keep -> DecisionKind.Keep
            SwipeDirection.Favorite -> DecisionKind.Favorite
            SwipeDirection.Skip -> DecisionKind.Skip
        }
        val pacing = pacer.onDecision(direction, card.item.sizeBytes, System.currentTimeMillis())
        _state.value = _state.value.copy(
            cursor = _state.value.cursor + 1,
            pacing = pacing,
            lastDecision = LastDecision(card.item, direction),
        )
        viewModelScope.launch {
            repository.record(card.item, kind)
            refreshPending()
        }
    }

    /** Takes the last swipe back, and tells the pacer about it — an undo always breaks the combo. */
    fun undo() {
        val last = _state.value.lastDecision ?: return
        val pacing = pacer.onUndo(last.direction, last.item.sizeBytes)
        _state.value = _state.value.copy(
            cursor = (_state.value.cursor - 1).coerceAtLeast(0),
            pacing = pacing,
            lastDecision = null,
        )
        viewModelScope.launch {
            repository.undo(last.item)
            refreshPending()
        }
    }

    /** The user dismissed a checkpoint or a slow-down notice; dealing resumes. */
    fun acknowledgePacing() {
        _state.value = _state.value.copy(pacing = pacer.acknowledge())
    }

    /**
     * Batch mode: accept every pre-ticked card in the current deck at once. Hardcore never
     * marks a card batchable, so this can only ever act on ordinary clutter.
     */
    fun acceptBatch(ids: Set<Long>) {
        val cards = _state.value.cards.filter { it.item.id in ids && it.batchable }
        if (cards.isEmpty()) return
        val accepted = cards.mapTo(HashSet()) { it.item.id }
        viewModelScope.launch {
            cards.forEach { repository.record(it.item, DecisionKind.Delete) }
            val remaining = _state.value.cards.filterNot { it.item.id in accepted }
            _state.value = _state.value.copy(cards = remaining, cursor = 0, lastDecision = null)
            refreshPending()
        }
    }

    suspend fun pendingDeletionIds(): List<Long> = repository.pendingDeletionsNow().map { it.mediaId }

    /** Called once the system trash sheet came back with a yes. */
    fun onTrashConfirmed(ids: List<Long>) {
        viewModelScope.launch {
            repository.markCommitted(ids)
            library = repository.sweep()
            _state.value = _state.value.copy(decks = repository.decks(library, _state.value.mode))
            refreshPending()
        }
    }

    private fun pacerFor(mode: TriageMode) = when (mode) {
        TriageMode.Normal -> SwipePacer()
        TriageMode.Hardcore -> SwipePacer(SwipePacer.Config.hardcore())
    }

    private suspend fun refreshPending() {
        val pending = repository.pendingDeletionsNow()
        _state.value = _state.value.copy(
            pendingDeletions = pending.size,
            pendingBytes = pending.sumOf { it.sizeBytes },
        )
    }
}
