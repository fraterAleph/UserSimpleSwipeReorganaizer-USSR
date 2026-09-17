package app.ussr.core.model

/**
 * One photo or video as MediaStore describes it, with nothing device-specific attached.
 * Sizes are bytes, times are epoch milliseconds.
 */
data class MediaItem(
    val id: Long,
    val displayName: String,
    val relativePath: String,
    val mimeType: String,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val dateAddedMs: Long,
    val dateModifiedMs: Long,
    val dateTakenMs: Long?,
    val durationMs: Long? = null,
    val isFavorite: Boolean = false,
    val albumCount: Int = 0,
) {
    val isVideo: Boolean get() = mimeType.startsWith("video/")

    /** The moment the shot itself happened, falling back to when the file landed on disk. */
    val capturedAtMs: Long get() = dateTakenMs ?: dateAddedMs

    /** True when the file was touched after it arrived — edited, cropped, marked up. */
    val wasEditedAfterImport: Boolean get() = dateModifiedMs - dateAddedMs > EDIT_SLACK_MS

    private companion object {
        const val EDIT_SLACK_MS = 60_000L
    }
}
