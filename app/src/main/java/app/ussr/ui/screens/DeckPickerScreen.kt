package app.ussr.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.ussr.R
import app.ussr.core.queue.Deck
import app.ussr.core.scoring.Category
import app.ussr.core.scoring.TriageMode
import app.ussr.ui.TriageUiState
import app.ussr.ui.formatBytes
import app.ussr.ui.label
import app.ussr.ui.theme.PixelButton
import app.ussr.ui.theme.PixelSurface
import app.ussr.ui.theme.UssrColors

/**
 * The picker. Decks are shown with their size and the app's own confidence in them, so the
 * user can start with the pile they trust and leave the ambiguous one for later — which
 * beats one endless stream where attention runs out halfway.
 */
@Composable
fun DeckPickerScreen(
    state: TriageUiState,
    onOpenDeck: (Category?) -> Unit,
    onSetMode: (TriageMode) -> Unit,
    onReview: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
    ) {
        if (state.loading) {
            Scanning(state)
            return@Column
        }

        Text(
            text = stringResource(R.string.picker_title),
            style = MaterialTheme.typography.headlineSmall,
            color = UssrColors.Ember,
        )
        Text(
            text = stringResource(R.string.picker_subject, state.libraryCount),
            style = MaterialTheme.typography.labelSmall,
            color = UssrColors.Edge,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(
                R.string.picker_pending,
                state.pendingDeletions,
                formatBytes(state.pendingBytes),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = UssrColors.Dust,
        )
        Spacer(Modifier.height(14.dp))

        ModeSwitch(state.mode, onSetMode)

        if (state.hardcore) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.mode_hardcore_warning),
                style = MaterialTheme.typography.bodySmall,
                color = UssrColors.Ember,
            )
        }
        Spacer(Modifier.height(16.dp))

        if (state.decks.isEmpty()) {
            Text(
                text = stringResource(
                    if (state.hardcore) R.string.picker_empty_hardcore else R.string.picker_empty,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = UssrColors.Dust,
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 8.dp, end = 6.dp),
        ) {
            itemsIndexed(state.decks, key = { _, deck -> deck.category.name }) { index, deck ->
                DeckRow(
                    deck = deck,
                    caseNumber = index + 1,
                    hardcore = state.hardcore,
                    onClick = { onOpenDeck(deck.category) },
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        PixelButton(
            text = stringResource(R.string.picker_swipe_everything),
            onClick = { onOpenDeck(null) },
            enabled = state.decks.isNotEmpty(),
        )
        Spacer(Modifier.height(8.dp))
        PixelButton(
            text = stringResource(R.string.picker_review),
            onClick = onReview,
            enabled = state.pendingDeletions > 0,
            fill = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}

@Composable
private fun Scanning(state: TriageUiState) {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.scan_title),
            style = MaterialTheme.typography.titleMedium,
            color = UssrColors.Bone,
        )
        Spacer(Modifier.height(14.dp))
        LinearProgressIndicator(
            progress = { state.progress.fraction },
            color = UssrColors.Blood,
            trackColor = UssrColors.Char,
            modifier = Modifier.fillMaxWidth().height(10.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.scan_progress, state.progress.done, state.progress.total),
            style = MaterialTheme.typography.bodySmall,
            color = UssrColors.Dust,
        )
    }
}

/**
 * Normal and hardcore, side by side and always visible.
 *
 * Hardcore is not hidden in a settings screen: the pile it works on is real and grows, and
 * a mode nobody can find is a mode nobody uses. It is labelled for what it is instead —
 * every card in it is something the app would otherwise have refused to show.
 *
 * Built out of two plates rather than Material's SegmentedButton, which rounds its ends into
 * a pill and would be the only curved thing on the screen.
 */
@Composable
private fun ModeSwitch(mode: TriageMode, onSetMode: (TriageMode) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ModeTab(
            text = stringResource(R.string.mode_normal),
            selected = mode == TriageMode.Normal,
            selectedFill = UssrColors.Wine,
            onClick = { onSetMode(TriageMode.Normal) },
            modifier = Modifier.weight(1f),
        )
        ModeTab(
            text = stringResource(R.string.mode_hardcore),
            selected = mode == TriageMode.Hardcore,
            selectedFill = UssrColors.Blood,
            onClick = { onSetMode(TriageMode.Hardcore) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ModeTab(
    text: String,
    selected: Boolean,
    selectedFill: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PixelSurface(
        modifier = modifier.clickable(onClick = onClick),
        fill = if (selected) selectedFill else UssrColors.Ash,
        border = if (selected) UssrColors.Ember else UssrColors.Edge,
        shadowOffset = if (selected) 4.dp else 2.dp,
        contentPadding = PaddingValues(vertical = 12.dp),
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) UssrColors.Bone else UssrColors.Dust,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

@Composable
private fun DeckRow(deck: Deck, caseNumber: Int, hardcore: Boolean, onClick: () -> Unit) {
    PixelSurface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        fill = UssrColors.Ash,
        border = if (hardcore) UssrColors.Ember else UssrColors.Edge,
    ) {
        Column(Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.deck_case_no, caseNumber),
                style = MaterialTheme.typography.labelSmall,
                color = UssrColors.Edge,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = deck.category.label().uppercase(),
                    style = MaterialTheme.typography.titleSmall,
                    color = UssrColors.Bone,
                )
                Text(
                    text = stringResource(R.string.deck_count, deck.size),
                    style = MaterialTheme.typography.titleMedium,
                    color = UssrColors.Ember,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(
                    R.string.deck_summary,
                    formatBytes(deck.reclaimableBytes),
                    (deck.confidence * 100).toInt(),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = UssrColors.Dust,
            )
            Spacer(Modifier.height(6.dp))
            ConfidenceBar(deck.confidence)
            if (deck.batchableCards.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.deck_batchable, deck.batchableCards.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = UssrColors.Gold,
                )
            }
        }
    }
}

/** Ten blocks rather than a smooth bar — a gradient would be the wrong texture here. */
@Composable
private fun ConfidenceBar(confidence: Double) {
    val filled = (confidence * SEGMENTS).toInt().coerceIn(0, SEGMENTS)
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(SEGMENTS) { index ->
            Box(
                Modifier
                    .weight(1f)
                    .height(8.dp)
                    .background(if (index < filled) UssrColors.Blood else UssrColors.Char),
            )
        }
    }
}

private const val SEGMENTS = 10
