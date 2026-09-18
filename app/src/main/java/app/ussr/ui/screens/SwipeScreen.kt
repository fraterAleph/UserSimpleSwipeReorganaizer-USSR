package app.ussr.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.unit.dp
import app.ussr.R
import app.ussr.core.pacing.PacingEvent
import app.ussr.core.pacing.PacingLevel
import app.ussr.core.pacing.SwipeDirection
import app.ussr.core.queue.Card as TriageCard
import app.ussr.ui.TriageUiState
import app.ussr.ui.formatBytes
import app.ussr.ui.formatDuration
import app.ussr.ui.isKeepReason
import app.ussr.ui.label
import app.ussr.ui.theme.PixelButton
import app.ussr.ui.theme.PixelStat
import app.ussr.ui.theme.PixelSurface
import app.ussr.ui.theme.UssrColors
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * The deck: one file at a time, the next one showing underneath, and the pacing layer on top
 * of both — see [PacingBanner] and [PacingDialog] for the part that pushes back when the
 * swiping gets faster than the looking.
 */
@Composable
fun SwipeScreen(
    state: TriageUiState,
    contentUri: (Long) -> Any,
    onSwipe: (SwipeDirection) -> Unit,
    onUndo: () -> Unit,
    onAcknowledge: () -> Unit,
    onBatch: (Set<Long>) -> Unit,
    onReturnAnimationDone: () -> Unit,
    onOpenJournal: () -> Unit,
    onReview: () -> Unit,
    onBack: () -> Unit,
) {
    // Without this the system back button leaves the activity and the app disappears from
    // under the user mid-session.
    BackHandler(onBack = onBack)

    val card = state.current
    if (card == null) {
        DeckFinished(state, onReview = onReview, onBack = onBack)
        return
    }

    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }
    val threshold = with(LocalDensity.current) { 110.dp.toPx() }

    // A card normally starts centred. One that has just been undone flies back in from the
    // edge it was thrown off, so the undo reads as a reversal rather than a jump cut.
    LaunchedEffect(card.item.id, state.returningFrom) {
        val from = state.returningFrom
        if (from == null) {
            offsetX.snapTo(0f)
            offsetY.snapTo(0f)
        } else {
            val (x, y) = when (from) {
                SwipeDirection.Delete -> -threshold * 8 to 0f
                SwipeDirection.Keep -> threshold * 8 to 0f
                SwipeDirection.Favorite -> 0f to -threshold * 8
                SwipeDirection.Skip -> 0f to threshold * 8
            }
            offsetX.snapTo(x)
            offsetY.snapTo(y)
            launch { offsetX.animateTo(0f, tween(320)) }
            offsetY.animateTo(0f, tween(320))
            onReturnAnimationDone()
        }
    }

    fun commit(direction: SwipeDirection) {
        scope.launch {
            val target = when (direction) {
                SwipeDirection.Delete -> -threshold * 8 to 0f
                SwipeDirection.Keep -> threshold * 8 to 0f
                SwipeDirection.Favorite -> 0f to -threshold * 8
                SwipeDirection.Skip -> 0f to threshold * 8
            }
            launch { offsetX.animateTo(target.first, tween(160)) }
            offsetY.animateTo(target.second, tween(160))
            onSwipe(direction)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        SessionHeader(state)
        Spacer(Modifier.height(10.dp))

        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            state.next?.let { next ->
                CardFace(
                    card = next,
                    model = contentUri(next.item.id),
                    hardcore = state.hardcore,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { scaleX = 0.95f; scaleY = 0.95f; translationY = 14f }
                        .alpha(0.45f),
                )
            }

            CardFace(
                card = card,
                model = contentUri(card.item.id),
                hardcore = state.hardcore,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = offsetX.value
                        translationY = offsetY.value
                        rotationZ = offsetX.value / 45f
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
                                        launch { offsetX.animateTo(0f, tween(140)) }
                                        offsetY.animateTo(0f, tween(140))
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

            SwipeStamp(offsetX.value, offsetY.value, threshold)
        }

        Spacer(Modifier.height(10.dp))
        PacingBanner(state)
        Spacer(Modifier.height(8.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PixelButton(
                text = stringResource(R.string.action_delete),
                onClick = { commit(SwipeDirection.Delete) },
                enabled = !state.blocked,
                fill = UssrColors.Blood,
                modifier = Modifier.weight(1f),
            )
            // Tap takes one decision back; hold opens the journal, where the session can be
            // unwound to any point in it. The single step covers the common slip, the
            // journal covers "the one I want back was six cards ago".
            PixelButton(
                text = stringResource(R.string.action_undo),
                onClick = onUndo,
                enabled = state.lastDecision != null,
                fill = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.weight(1f),
                onLongClick = onOpenJournal,
            )
            PixelButton(
                text = stringResource(R.string.action_keep),
                onClick = { commit(SwipeDirection.Keep) },
                enabled = !state.blocked,
                fill = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(8.dp))
        PixelButton(
            text = stringResource(R.string.action_favorite),
            onClick = { commit(SwipeDirection.Favorite) },
            enabled = !state.blocked,
            fill = MaterialTheme.colorScheme.surfaceVariant,
            textColor = UssrColors.Gold,
            onLongClick = onOpenJournal,
        )

        // The batch escape hatch. A thousand duplicates should not cost a thousand gestures,
        // and hardcore never marks anything batchable, so this cannot reach a favourite.
        val batchable = state.cards.drop(state.cursor).filter { it.batchable }
        if (batchable.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            PixelButton(
                text = stringResource(R.string.action_batch, batchable.size),
                onClick = { onBatch(batchable.mapTo(HashSet()) { it.item.id }) },
                enabled = !state.blocked,
                fill = MaterialTheme.colorScheme.surfaceVariant,
            )
        }
    }

    PacingDialog(state, onAcknowledge = onAcknowledge, onReview = onReview)
}

@Composable
private fun SessionHeader(state: TriageUiState) {
    Column(Modifier.fillMaxWidth()) {
        if (state.hardcore) {
            // The deck looks the same as the normal one, so the header has to say plainly
            // that every file in it is one the app would otherwise have protected.
            Text(
                text = stringResource(R.string.mode_hardcore_active),
                style = MaterialTheme.typography.labelSmall,
                color = UssrColors.Ember,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            PixelStat(
                label = stringResource(R.string.stat_left),
                value = state.remaining.toString(),
            )
            PixelStat(
                label = stringResource(R.string.stat_queued),
                value = "${state.pendingDeletions} / ${formatBytes(state.pendingBytes)}",
                valueColor = UssrColors.Ember,
            )
        }
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { if (state.cards.isEmpty()) 0f else state.cursor.toFloat() / state.cards.size },
            color = UssrColors.Blood,
            trackColor = UssrColors.Char,
            modifier = Modifier.fillMaxWidth().height(6.dp),
        )
    }
}

@Composable
private fun CardFace(
    card: TriageCard,
    model: Any,
    hardcore: Boolean,
    modifier: Modifier = Modifier,
) {
    PixelSurface(
        modifier = modifier,
        fill = UssrColors.Ash,
        border = if (hardcore) UssrColors.Ember else UssrColors.Edge,
        borderWidth = 4.dp,
        shadowOffset = 7.dp,
        contentPadding = PaddingValues(0.dp),
    ) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth().background(UssrColors.Ink)) {
                AsyncImage(
                    model = model,
                    contentDescription = card.item.displayName,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
                // A video's first frame looks like a still, so the card has to say which it
                // is and how long it runs — otherwise the only way to tell is the extension.
                if (card.item.isVideo) {
                    PixelSurface(
                        modifier = Modifier.align(Alignment.TopEnd).padding(10.dp),
                        fill = UssrColors.Ink,
                        border = UssrColors.Gold,
                        borderWidth = 2.dp,
                        shadowOffset = 3.dp,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        fillWidth = false,
                    ) {
                        Text(
                            text = stringResource(
                                R.string.badge_video,
                                formatDuration(card.item.durationMs),
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = UssrColors.Gold,
                        )
                    }
                }
            }
            DossierBlock(card)
        }
    }
}

/** The lower half of a card, laid out like the front sheet of a case file. */
@Composable
private fun DossierBlock(card: TriageCard) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(UssrColors.Char)
            .padding(12.dp),
    ) {
        DossierField(stringResource(R.string.field_subject), card.item.displayName)
        DossierField(
            label = stringResource(R.string.field_measurements),
            value = "${formatBytes(card.item.sizeBytes)} · ${card.item.width}x${card.item.height}",
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.field_grounds),
            style = MaterialTheme.typography.labelSmall,
            color = UssrColors.Edge,
        )
        // Why this file is on the desk, in the app's own words. A card that cannot explain
        // itself has no business asking for a decision.
        card.verdict.reasons.take(3).forEach { reason ->
            Text(
                text = "— ${reason.label()}",
                style = MaterialTheme.typography.bodySmall,
                color = if (reason.isKeepReason) UssrColors.Gold else UssrColors.Dust,
            )
        }
    }
}

@Composable
private fun DossierField(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = UssrColors.Edge,
            modifier = Modifier.padding(end = 8.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = UssrColors.Bone,
        )
    }
}

/** The verdict stamped across the file as it is dragged, in the colour of that decision. */
@Composable
private fun SwipeStamp(x: Float, y: Float, threshold: Float) {
    val horizontal = abs(x) > abs(y)
    val progress = (if (horizontal) abs(x) else abs(y)) / threshold
    if (progress < 0.15f) return

    val (text, color) = when {
        horizontal && x < 0 -> stringResource(R.string.stamp_delete) to UssrColors.Ember
        horizontal -> stringResource(R.string.stamp_keep) to UssrColors.Bone
        y < 0 -> stringResource(R.string.stamp_favorite) to UssrColors.Gold
        else -> stringResource(R.string.stamp_skip) to UssrColors.Dust
    }
    PixelSurface(
        modifier = Modifier.alpha(progress.coerceIn(0f, 1f)),
        fill = UssrColors.Ink,
        border = color,
        borderWidth = 4.dp,
        shadowOffset = 6.dp,
        shadow = color.copy(alpha = 0.4f),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
        fillWidth = false,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.headlineMedium,
            color = color,
        )
    }
}

/**
 * The brake.
 *
 * There used to be a combo counter here. It is gone from the screen on purpose — a number
 * that goes up is an invitation to make it go up faster, which is the exact failure this
 * layer exists to prevent. The streak still runs underneath and still decides when these
 * warnings fire; the user just never sees a score to chase.
 */
@Composable
private fun PacingBanner(state: TriageUiState) {
    val events = state.pacing.events
    val tooFast = events.filterIsInstance<PacingEvent.TooFast>().firstOrNull()
    val streak = events.filterIsInstance<PacingEvent.DeleteStreak>().firstOrNull()
    val steady = events.filterIsInstance<PacingEvent.ComboMilestone>().firstOrNull()

    Column(Modifier.fillMaxWidth()) {
        AnimatedVisibility(visible = tooFast != null) {
            WarningLine(stringResource(R.string.pacing_too_fast), UssrColors.Ember)
        }
        AnimatedVisibility(visible = streak != null) {
            WarningLine(stringResource(R.string.pacing_delete_streak, streak?.streak ?: 0), UssrColors.Gold)
        }
        AnimatedVisibility(visible = steady != null && tooFast == null) {
            WarningLine(stringResource(R.string.pacing_steady), UssrColors.Dust)
        }
    }
}

@Composable
private fun WarningLine(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = color,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}

/**
 * The blocking half of the pacing layer: every fiftieth file in normal mode, every twelfth
 * in hardcore, and at the end of a long session, the deck stops until the user says go on.
 */
@Composable
private fun PacingDialog(state: TriageUiState, onAcknowledge: () -> Unit, onReview: () -> Unit) {
    if (!state.blocked) return
    val checkpoint = state.pacing.events.filterIsInstance<PacingEvent.Checkpoint>().firstOrNull()
    val resting = state.pacing.level == PacingLevel.Rest

    AlertDialog(
        onDismissRequest = onAcknowledge,
        containerColor = UssrColors.Ash,
        titleContentColor = UssrColors.Bone,
        textContentColor = UssrColors.Dust,
        title = {
            Text(
                text = if (resting) {
                    stringResource(R.string.checkpoint_rest_title)
                } else {
                    stringResource(R.string.checkpoint_title, checkpoint?.decisions ?: 0)
                },
                style = MaterialTheme.typography.titleMedium,
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
            TextButton(onClick = onReview) {
                Text(stringResource(R.string.checkpoint_review), color = UssrColors.Ember)
            }
        },
        dismissButton = {
            TextButton(onClick = onAcknowledge) {
                Text(stringResource(R.string.checkpoint_continue), color = UssrColors.Dust)
            }
        },
    )
}

@Composable
private fun DeckFinished(state: TriageUiState, onReview: () -> Unit, onBack: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PixelSurface(fill = UssrColors.Ash, border = UssrColors.Edge) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    stringResource(R.string.deck_done_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = UssrColors.Bone,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(
                        R.string.deck_done_body,
                        state.pacing.decisions,
                        state.pendingDeletions,
                        formatBytes(state.pendingBytes),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = UssrColors.Dust,
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        PixelButton(stringResource(R.string.checkpoint_review), onReview)
        Spacer(Modifier.height(8.dp))
        PixelButton(
            text = stringResource(R.string.action_back),
            onClick = onBack,
            fill = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}
