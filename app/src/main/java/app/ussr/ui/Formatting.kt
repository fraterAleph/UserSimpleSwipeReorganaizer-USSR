package app.ussr.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.ussr.R
import app.ussr.core.scoring.Category
import app.ussr.core.scoring.Reason
import java.util.Locale

fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble() / 1024
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    return String.format(Locale.getDefault(), "%.1f %s", value, units[unit])
}

@Composable
fun Category.label(): String = stringResource(
    when (this) {
        Category.ExactDuplicates -> R.string.category_duplicates
        Category.Bursts -> R.string.category_bursts
        Category.BlankOrBlurry -> R.string.category_blurry
        Category.Screenshots -> R.string.category_screenshots
        Category.MessengerAndSocial -> R.string.category_messengers
        Category.HeavyVideos -> R.string.category_heavy
        Category.StaleDownloads -> R.string.category_downloads
        Category.Everything -> R.string.category_everything
    },
)

@Composable
fun Reason.label(): String = stringResource(
    when (this) {
        Reason.ExactDuplicate -> R.string.reason_exact_duplicate
        Reason.NearDuplicate -> R.string.reason_near_duplicate
        Reason.BurstSibling -> R.string.reason_burst
        Reason.Screenshot -> R.string.reason_screenshot
        Reason.ScreenRecording -> R.string.reason_screen_recording
        Reason.MessengerSave -> R.string.reason_messenger
        Reason.SocialSave -> R.string.reason_social
        Reason.Blurry -> R.string.reason_blurry
        Reason.BlankFrame -> R.string.reason_blank
        Reason.Heavy -> R.string.reason_heavy
        Reason.Old -> R.string.reason_old
        Reason.NoTextOnScreenshot -> R.string.reason_no_text
        Reason.LooksLikeDocument -> R.string.reason_document
        Reason.LooksLikeReceipt -> R.string.reason_receipt
        Reason.LooksLikeCode -> R.string.reason_code
        Reason.HasFaces -> R.string.reason_faces
        Reason.Favorite -> R.string.reason_favorite
        Reason.InAlbum -> R.string.reason_album
        Reason.EditedAfterImport -> R.string.reason_edited
    },
)

/** Reasons that argue for keeping the item; shown in a different colour on the card. */
val Reason.isKeepReason: Boolean
    get() = this in setOf(
        Reason.LooksLikeDocument,
        Reason.LooksLikeReceipt,
        Reason.LooksLikeCode,
        Reason.HasFaces,
        Reason.Favorite,
        Reason.InAlbum,
        Reason.EditedAfterImport,
    )
