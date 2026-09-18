package app.ussr.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.ussr.R
import app.ussr.core.pacing.SwipeDirection
import app.ussr.ui.LastDecision
import app.ussr.ui.formatBytes
import app.ussr.ui.theme.PixelButton
import app.ussr.ui.theme.PixelSurface
import app.ussr.ui.theme.UssrColors
import coil.compose.AsyncImage

/**
 * The journal: everything decided since this deck was opened, newest on top, laid out as a
 * drawer of hanging files pulled part-way out.
 *
 * Each row is a folder tab whose spine is coloured by where the file went — red for written
 * off, gold for favourited, bone for kept, grey for deferred — so the shape of the session
 * reads at a glance without labels. Rows step sideways as they recede into the drawer, which
 * is the same idea as a receding stack of windows: depth stands for time.
 *
 * Tapping any row unwinds the session back to that point. Not "undo one" — everything from
 * that file onward comes back, and that file is the next card dealt. Which is what you
 * actually want when the thing you regret is six swipes behind you.
 */
@Composable
fun JournalScreen(
    history: List<LastDecision>,
    contentUri: (Long) -> Any,
    onRewindTo: (Int) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
    ) {
        PixelSurface(
            modifier = Modifier.fillMaxWidth(),
            fill = UssrColors.Ash,
            border = UssrColors.Edge,
        ) {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.journal_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = UssrColors.Bone,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.journal_hint, history.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = UssrColors.Dust,
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        if (history.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.journal_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = UssrColors.Dust,
                )
            }
        } else {
            // Newest first, but the index handed back is the one in the original order,
            // because that is what the rewind counts from.
            val newestFirst = history.withIndex().reversed()
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(bottom = 8.dp, end = 8.dp),
            ) {
                itemsIndexed(newestFirst) { depth, (index, entry) ->
                    JournalRow(
                        entry = entry,
                        model = contentUri(entry.item.id),
                        depth = depth,
                        onClick = { onRewindTo(index) },
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        PixelButton(
            text = stringResource(R.string.journal_rewind_all),
            onClick = { onRewindTo(0) },
            enabled = history.isNotEmpty(),
            fill = UssrColors.Blood,
        )
        Spacer(Modifier.height(8.dp))
        PixelButton(
            text = stringResource(R.string.action_back),
            onClick = onBack,
            fill = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}

@Composable
private fun JournalRow(
    entry: LastDecision,
    model: Any,
    depth: Int,
    onClick: () -> Unit,
) {
    val spine = entry.direction.spineColour()
    // Each row sits a little further into the drawer than the one above it, up to a limit —
    // past a dozen the stagger would eat the row instead of suggesting depth.
    val inset = (depth.coerceAtMost(MAX_STAGGER) * 4).dp

    Row(
        Modifier
            .fillMaxWidth()
            .offset(x = inset)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The hanging-file tab: a coloured spine down the left edge of the row.
        Box(
            Modifier
                .width(6.dp)
                .height(ROW_HEIGHT)
                .background(spine),
        )
        PixelSurface(
            modifier = Modifier.weight(1f).height(ROW_HEIGHT),
            fill = UssrColors.Ash,
            border = UssrColors.Edge,
            borderWidth = 2.dp,
            shadowOffset = 3.dp,
            contentPadding = PaddingValues(6.dp),
        ) {
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = model,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(48.dp)
                        .background(UssrColors.Char),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                    Text(
                        text = entry.item.displayName,
                        style = MaterialTheme.typography.bodySmall,
                        color = UssrColors.Bone,
                        maxLines = 1,
                    )
                    Text(
                        text = "${entry.direction.verdictLabel()} · ${formatBytes(entry.item.sizeBytes)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = spine,
                    )
                }
            }
        }
    }
}

@Composable
private fun SwipeDirection.verdictLabel(): String = stringResource(
    when (this) {
        SwipeDirection.Delete -> R.string.stamp_delete
        SwipeDirection.Keep -> R.string.stamp_keep
        SwipeDirection.Favorite -> R.string.stamp_favorite
        SwipeDirection.Skip -> R.string.stamp_skip
    },
)

private fun SwipeDirection.spineColour(): Color = when (this) {
    SwipeDirection.Delete -> UssrColors.Blood
    SwipeDirection.Keep -> UssrColors.Bone
    SwipeDirection.Favorite -> UssrColors.Gold
    SwipeDirection.Skip -> UssrColors.Dust
}

private val ROW_HEIGHT = 64.dp
private const val MAX_STAGGER = 12
