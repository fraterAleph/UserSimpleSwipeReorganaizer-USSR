package app.ussr.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The card chrome the whole app is built from: a flat fill, a thick hard border and a solid
 * offset block behind it standing in for a shadow.
 *
 * No blur and no elevation anywhere — a soft Material shadow is the one thing that would
 * break the look, because gradients are exactly what a pixel style does not have.
 */
@Composable
fun PixelSurface(
    modifier: Modifier = Modifier,
    fill: Color = MaterialTheme.colorScheme.surface,
    border: Color = MaterialTheme.colorScheme.outline,
    borderWidth: Dp = 3.dp,
    shadowOffset: Dp = 5.dp,
    shadow: Color = UssrColors.Wine,
    contentPadding: PaddingValues = PaddingValues(14.dp),
    /** False lets the plate shrink to its content, which is what the drag stamp wants. */
    fillWidth: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(3.dp)
    Box(modifier) {
        // The hard shadow is a second rectangle, not a blur.
        Box(
            Modifier
                .matchParentSize()
                .offset(x = shadowOffset, y = shadowOffset)
                .background(shadow, shape),
        )
        Box(
            Modifier
                .matchParentSize()
                .background(fill, shape)
                .border(BorderStroke(borderWidth, border), shape),
        )
        Box(
            modifier = if (fillWidth) {
                Modifier.fillMaxWidth().padding(contentPadding)
            } else {
                Modifier.padding(contentPadding)
            },
            content = content,
        )
    }
}

/**
 * A chunky rectangular button. Material's own buttons round their corners and lift on press;
 * this one just sits there like a key on a machine.
 */
@Composable
fun PixelButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    fill: Color = MaterialTheme.colorScheme.primary,
    textColor: Color = MaterialTheme.colorScheme.onPrimary,
) {
    PixelSurface(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.4f)
            .clickable(enabled = enabled, onClick = onClick),
        fill = fill,
        border = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.35f),
        shadowOffset = 4.dp,
        contentPadding = PaddingValues(vertical = 14.dp, horizontal = 16.dp),
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = textColor,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

/** A small stat plate for the header row: label above, value below, no decoration. */
@Composable
fun PixelStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Box(modifier) {
        Column {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = valueColor,
            )
        }
    }
}
