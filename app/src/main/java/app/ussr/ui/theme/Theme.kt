package app.ussr.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * One fixed palette, built around #4F0014.
 *
 * Deliberately not Material You: dynamic colour would repaint the app in whatever the user's
 * wallpaper happens to be, and the whole point here is a deck of cards that reads the same
 * every time you open it. Dark only, for the same reason — a light variant of this palette
 * would be a different app.
 */
object UssrColors {
    /** The colour the whole thing is built around. */
    val Wine = Color(0xFF4F0014)

    val Ink = Color(0xFF0A0406)
    val Ash = Color(0xFF1A090F)
    val Char = Color(0xFF2A0D16)

    val Blood = Color(0xFFC4123A)
    val Ember = Color(0xFFFF3B5C)

    /** Cream, not white: pure white on this background is a migraine. */
    val Bone = Color(0xFFF2E4D4)
    val Dust = Color(0xFF9A7F86)

    /** Reserved for the combo counter and nothing else, so it always means the same thing. */
    val Gold = Color(0xFFF2B705)

    val Edge = Color(0xFF7A1028)
}

private val Scheme = darkColorScheme(
    primary = UssrColors.Blood,
    onPrimary = UssrColors.Bone,
    primaryContainer = UssrColors.Wine,
    onPrimaryContainer = UssrColors.Bone,
    secondary = UssrColors.Edge,
    onSecondary = UssrColors.Bone,
    secondaryContainer = UssrColors.Char,
    onSecondaryContainer = UssrColors.Bone,
    tertiary = UssrColors.Gold,
    onTertiary = UssrColors.Ink,
    background = UssrColors.Ink,
    onBackground = UssrColors.Bone,
    surface = UssrColors.Ash,
    onSurface = UssrColors.Bone,
    surfaceVariant = UssrColors.Char,
    onSurfaceVariant = UssrColors.Dust,
    outline = UssrColors.Edge,
    outlineVariant = UssrColors.Char,
    error = UssrColors.Ember,
    onError = UssrColors.Ink,
)

/**
 * Monospace throughout, at heavy weights with wide tracking.
 *
 * A real pixel typeface would mean shipping a font file; a bold monospace on this palette
 * gets most of the way there and keeps the APK free of binary assets.
 */
private val PixelType = Typography().run {
    val mono = FontFamily.Monospace
    copy(
        displaySmall = displaySmall.copy(fontFamily = mono, fontWeight = FontWeight.Black, letterSpacing = 2.sp),
        headlineSmall = headlineSmall.copy(fontFamily = mono, fontWeight = FontWeight.Black, letterSpacing = 3.sp),
        headlineMedium = headlineMedium.copy(fontFamily = mono, fontWeight = FontWeight.Black, letterSpacing = 3.sp),
        titleLarge = titleLarge.copy(fontFamily = mono, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp),
        titleMedium = titleMedium.copy(fontFamily = mono, fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
        titleSmall = titleSmall.copy(fontFamily = mono, fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
        bodyLarge = bodyLarge.copy(fontFamily = mono),
        bodyMedium = bodyMedium.copy(fontFamily = mono),
        bodySmall = bodySmall.copy(fontFamily = mono),
        labelLarge = labelLarge.copy(fontFamily = mono, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp),
        labelMedium = labelMedium.copy(fontFamily = mono, fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
        labelSmall = labelSmall.copy(fontFamily = mono, fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
    )
}

/** Almost-square corners: a 2dp radius reads as a chamfered pixel, a 16dp one as a pill. */
private val PixelShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(2.dp),
    medium = RoundedCornerShape(3.dp),
    large = RoundedCornerShape(4.dp),
    extraLarge = RoundedCornerShape(4.dp),
)

/** The counter style: big, gold, centred, used for the combo and nothing else. */
val ComboTextStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Black,
    fontSize = 34.sp,
    letterSpacing = 3.sp,
    color = UssrColors.Gold,
    textAlign = TextAlign.Center,
)

@Composable
fun UssrTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Scheme,
        typography = PixelType,
        shapes = PixelShapes,
        content = content,
    )
}
