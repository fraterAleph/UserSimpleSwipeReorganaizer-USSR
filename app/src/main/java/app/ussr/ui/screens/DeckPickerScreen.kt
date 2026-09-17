package app.ussr.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.ussr.R
import app.ussr.core.queue.Deck
import app.ussr.core.scoring.Category
import app.ussr.ui.TriageUiState
import app.ussr.ui.formatBytes
import app.ussr.ui.label

/**
 * The picker. Decks are shown with their size and the app's own confidence in them, so the
 * user can start with the pile they trust and leave the ambiguous one for later — which
 * beats one endless stream where attention runs out halfway.
 */
@Composable
fun DeckPickerScreen(
    state: TriageUiState,
    onOpenDeck: (Category?) -> Unit,
    onReview: () -> Unit,
) {
    if (state.loading) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.scan_title), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { state.progress.fraction },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.scan_progress, state.progress.done, state.progress.total),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        return
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(
                R.string.picker_pending,
                state.pendingDeletions,
                formatBytes(state.pendingBytes),
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(16.dp))

        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.decks, key = { it.category.name }) { deck ->
                DeckRow(deck, onClick = { onOpenDeck(deck.category) })
            }
        }

        Spacer(Modifier.height(12.dp))
        Button(onClick = { onOpenDeck(null) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.picker_swipe_everything))
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onReview,
            enabled = state.pendingDeletions > 0,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.picker_review))
        }
    }
}

@Composable
private fun DeckRow(deck: Deck, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(deck.category.label(), style = MaterialTheme.typography.titleMedium)
                Text("${deck.size}", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(
                    R.string.deck_summary,
                    formatBytes(deck.reclaimableBytes),
                    (deck.confidence * 100).toInt(),
                ),
                style = MaterialTheme.typography.bodySmall,
            )
            if (deck.batchableCards.isNotEmpty()) {
                Text(
                    stringResource(R.string.deck_batchable, deck.batchableCards.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
