package app.ussr.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.ussr.R
import app.ussr.core.pacing.PacingEvent
import app.ussr.core.pacing.PacingLevel
import app.ussr.core.pacing.SwipeDirection
import app.ussr.core.queue.Card as TriageCard
import app.ussr.ui.TriageUiState
import app.ussr.ui.formatBytes
import app.ussr.ui.isKeepReason
import app.ussr.ui.label
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * The deck. One card at a time, the next one peeking underneath, and the pacing layer on
 * top of both — see [PacingBanner] and [PacingDialog] for the part that pushes back when
 * the swiping gets faster than the looking.
 */
@Composable
fun SwipeScreen(
    state: TriageUiState,
    contentUri: (Long) -> Any,
    onSwipe: (SwipeDirection) -> Unit,
    onUndo: () -> Unit,
    onAcknowledge: () -> Unit,
    onReview: () -> Unit,
    onBack: () -> Unit,
) {
    val card = state.current
    if (card == null) {
        DeckFinished(state, onReview = onReview, onBack = onBack)
        return
    }

    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }
    val threshold = with(LocalDensity.current) { 120.dp.toPx() }

    // A new card always starts centred, whatever the last gesture left behind.
    LaunchedEffect(card.item.id) {
        offsetX.snapTo(0f)
        offsetY.snapTo(0f)
    }

    fun commit(direction: SwipeDirection) {
        scope.launch {
            val target = when (direction) {
                SwipeDirection.Delete -> -threshold * 8 to 0f
                SwipeDirection.Keep -> threshold * 8 to 0f
                SwipeDirection.Favorite -> 0f to -threshold * 8
                SwipeDirection.Skip -> 0f to threshold * 8
            }
            launch { offsetX.animateTo(target.first, tween(180)) }
            offsetY.animateTo(target.second, tween(180))
            onSwipe(direction)
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        SessionHeader(state)

        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            state.next?.let { next ->
                CardFace(
                    card = next,
                    model = contentUri(next.item.id),
                    modifier = Modifier
                        .graphicsLayer { scaleX = 0.94f; scaleY = 0.94f }
                        .alpha(0.6f),
                )
            }

            CardFace(
                card = card,
                model = contentUri(card.item.id),
                modifier = Modifier
                    .graphicsLayer {
                        translationX = offsetX.value
                        translationY = offsetY.value
                        rotationZ = offsetX.value / 40f
                    }
                    .pointerInput(card.item.id, state.blocked) {
                        if (state.blocked) return@pointerInput
                        detectDragGestures(
                            onDragEnd = {
                                val x = offsetX.value
                                val y = offsetY.value
                                when {
                                    x < -threshold -> commit(SwipeDirection.Delete)
                                    x > threshold -> commit(SwipeDirection.Keep)
                                    y < -threshold -> commit(SwipeDirection.Favorite)
                                    y > threshold -> commit(SwipeDirection.Skip)
                                    else -> scope.launch {
                                        launch { offsetX.animateTo(0f, tween(150)) }
                                        offsetY.animateTo(0f, tween(150))
                                    }
                                }
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                scope.launch {
                                    offsetX.snapTo(offsetX.value + amount.x)
                                    offsetY.snapTo(offsetY.value + amount.y)
                                }
                            },
                        )
                    },
            )

            SwipeHint(offsetX.value, offsetY.value, threshold)
        }

        PacingBanner(state)

        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalIconButton(onClick = { commit(SwipeDirection.Delete) }, enabled = !state.blocked) {
                Icon(Icons.Filled.Close, stringResource(R.string.action_delete))
            }
            FilledTonalIconButton(onClick = onUndo, enabled = state.lastDecision != null) {
                Icon(Icons.Filled.Undo, stringResource(R.string.action_undo))
            }
            FilledTonalIconButton(onClick = { commit(SwipeDirection.Favorite) }, enabled = !state.blocked) {
                Icon(Icons.Filled.Favorite, stringResource(R.string.action_favorite))
            }
            FilledTonalIconButton(onClick = { commit(SwipeDirection.Keep) }, enabled = !state.blocked) {
                Icon(Icons.Filled.Check, stringResource(R.string.action_keep))
            }
        }
    }

    PacingDialog(state, onAcknowledge = onAcknowledge, onReview = onReview)
}

@Composable
private fun SessionHeader(state: TriageUiState) {
    Column(Modifier.fillMaxWidth()) {
        if (state.hardcore) {
            // The deck looks the same as the normal one, so the header has to say plainly
            // that every card in it is something the app would otherwise have protected.
            Text(
                text = stringResource(R.string.mode_hardcore_active),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.deck_remaining, state.remaining),
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                text = stringResource(
                    R.string.deck_pending,
                    state.pendingDeletions,
                    formatBytes(state.pendingBytes),
                ),
                style = MaterialTheme.typography.labelLarge,
            )
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = {
                if (state.cards.isEmpty()) 0f else state.cursor.toFloat() / state.cards.size
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun CardFace(card: TriageCard, model: Any, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxSize(),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
    ) {
        Box(Modifier.fillMaxSize()) {
            AsyncImage(
                model = model,
                contentDescription = card.item.displayName,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                    .padding(12.dp),
            ) {
                Text(card.item.displayName, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = "${formatBytes(card.item.sizeBytes)} · ${card.item.width}x${card.item.height}",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(4.dp))
                // Why this card is here, in the app's own words. A card that cannot explain
                // itself has no business asking for a decision.
                card.verdict.reasons.take(3).forEach { reason ->
                    Text(
                        text = reason.label(),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (reason.isKeepReason) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SwipeHint(x: Float, y: Float, threshold: Float) {
    val horizontal = abs(x) > abs(y)
    val progress = (if (horizontal) abs(x) else abs(y)) / threshold
    if (progress < 0.15f) return

    val (text, color) = when {
        horizontal && x < 0 -> stringResource(R.string.action_delete) to MaterialTheme.colorScheme.error
        horizontal -> stringResource(R.string.action_keep) to MaterialTheme.colorScheme.primary
        y < 0 -> stringResource(R.string.action_favorite) to MaterialTheme.colorScheme.tertiary
        else -> stringResource(R.string.action_skip) to MaterialTheme.colorScheme.outline
    }
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Black,
        color = color,
        modifier = Modifier.alpha(progress.coerceIn(0f, 1f)),
    )
}

/**
 * The combo counter, and the brake.
 *
 * The combo deliberately counts decisions, not speed, and the one thing that resets it is
 * going too fast — so the streak rewards sorting a lot while making haste the only way to
 * lose it. Reclaimed megabytes survive a broken combo, because the work was still done.
 */
@Composable
private fun PacingBanner(state: TriageUiState) {
    val pacing = state.pacing
    val tooFast = pacing.events.filterIsInstance<PacingEvent.TooFast>().firstOrNull()
    val streak = pacing.events.filterIsInstance<PacingEvent.DeleteStreak>().firstOrNull()
    val milestone = pacing.events.filterIsInstance<PacingEvent.ComboMilestone>().firstOrNull()

    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.combo_counter, pacing.combo),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (pacing.combo == 0) {
                    MaterialTheme.colorScheme.outline
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
            Text(
                text = stringResource(R.string.combo_best, pacing.longestCombo),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.outline,
            )
        }

        AnimatedVisibility(visible = tooFast != null) {
            WarningLine(
                text = stringResource(R.string.pacing_too_fast, tooFast?.comboLost ?: 0),
                color = MaterialTheme.colorScheme.error,
            )
        }
        AnimatedVisibility(visible = streak != null) {
            WarningLine(
                text = stringResource(R.string.pacing_delete_streak, streak?.streak ?: 0),
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
        AnimatedVisibility(visible = milestone != null && tooFast == null) {
            WarningLine(
                text = stringResource(R.string.pacing_combo, milestone?.combo ?: 0),
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun WarningLine(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = color,
        modifier = Modifier.padding(top = 4.dp),
    )
}

/**
 * The blocking half of the pacing layer: every fiftieth card, and at the end of a long
 * session, the deck stops until the user says to go on.
 */
@Composable
private fun PacingDialog(state: TriageUiState, onAcknowledge: () -> Unit, onReview: () -> Unit) {
    if (!state.blocked) return
    val checkpoint = state.pacing.events.filterIsInstance<PacingEvent.Checkpoint>().firstOrNull()
    val resting = state.pacing.level == PacingLevel.Rest

    AlertDialog(
        onDismissRequest = onAcknowledge,
        title = {
            Text(
                if (resting) {
                    stringResource(R.string.checkpoint_rest_title)
                } else {
                    stringResource(R.string.checkpoint_title, checkpoint?.decisions ?: 0)
                },
            )
        },
        text = {
            Text(
                stringResource(
                    R.string.checkpoint_body,
                    state.pendingDeletions,
                    formatBytes(state.pendingBytes),
                ),
            )
        },
        confirmButton = {
            Button(onClick = onReview) { Text(stringResource(R.string.checkpoint_review)) }
        },
        dismissButton = {
            TextButton(onClick = onAcknowledge) { Text(stringResource(R.string.checkpoint_continue)) }
        },
    )
}

@Composable
private fun DeckFinished(state: TriageUiState, onReview: () -> Unit, onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.deck_done_title), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(
                R.string.deck_done_body,
                state.pacing.decisions,
                state.pendingDeletions,
                formatBytes(state.pendingBytes),
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onReview) { Text(stringResource(R.string.checkpoint_review)) }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) }
    }
}
