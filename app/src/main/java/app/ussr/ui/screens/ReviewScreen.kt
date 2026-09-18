package app.ussr.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.ussr.R
import app.ussr.data.DecisionEntity
import app.ussr.ui.formatBytes
import app.ussr.ui.theme.PixelButton
import app.ussr.ui.theme.PixelSurface
import app.ussr.ui.theme.UssrColors
import coil.compose.AsyncImage

/**
 * The last look before anything moves: the write-off sheet. Everything swiped left is laid
 * out with the total it frees, and confirming hands the list to the system, which keeps it
 * in the trash for 30 days.
 *
 * Favourites ride along here too. Marking someone else's media is a write, so it needs the
 * same confirmation a deletion does, and collecting them is better than interrupting every
 * swipe up with a system dialog.
 */
@Composable
fun ReviewScreen(
    pending: List<DecisionEntity>,
    pendingFavorites: Int,
    contentUri: (Long) -> Any,
    onConfirm: () -> Unit,
    onBack: () -> Unit,
) {
    // Without this the system back button closes the app instead of returning to the deck.
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
            border = UssrColors.Ember,
        ) {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.review_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = UssrColors.Bone,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(
                        R.string.review_summary,
                        pending.size,
                        formatBytes(pending.sumOf { it.sizeBytes }),
                    ),
                    style = MaterialTheme.typography.titleSmall,
                    color = UssrColors.Ember,
                )
                if (pendingFavorites > 0) {
                    Text(
                        text = stringResource(R.string.review_favorites, pendingFavorites),
                        style = MaterialTheme.typography.bodySmall,
                        color = UssrColors.Gold,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.review_trash_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = UssrColors.Dust,
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        if (pending.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.review_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = UssrColors.Dust,
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(90.dp),
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(bottom = 8.dp),
            ) {
                items(pending, key = { it.mediaId }) { row ->
                    AsyncImage(
                        model = contentUri(row.mediaId),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .aspectRatio(1f)
                            .background(UssrColors.Char)
                            .border(2.dp, UssrColors.Edge),
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        PixelButton(
            text = stringResource(R.string.review_confirm, pending.size),
            onClick = onConfirm,
            enabled = pending.isNotEmpty() || pendingFavorites > 0,
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
