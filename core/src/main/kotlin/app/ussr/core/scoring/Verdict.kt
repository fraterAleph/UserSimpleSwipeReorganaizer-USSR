package app.ussr.core.scoring

/**
 * Why the app thinks what it thinks. Every verdict carries its reasons so the card can say
 * "a second copy of a photo you kept" instead of a bare percentage.
 */
enum class Reason {
    ExactDuplicate,
    NearDuplicate,
    BurstSibling,
    Screenshot,
    ScreenRecording,
    MessengerSave,
    SocialSave,
    Blurry,
    BlankFrame,
    Heavy,
    Old,
    NoTextOnScreenshot,
    LooksLikeDocument,
    LooksLikeReceipt,
    LooksLikeCode,
    HasFaces,
    Favorite,
    InAlbum,
    EditedAfterImport,
}

/** The bucket a card is dealt from. Queues are per-category so a session has an end in sight. */
enum class Category {
    ExactDuplicates,
    Bursts,
    BlankOrBlurry,
    Screenshots,
    MessengerAndSocial,
    HeavyVideos,
    StaleDownloads,
    Everything,
}

/**
 * @param junkScore how likely the item is worth deleting, 0..1.
 * @param confidence how sure the analysis is of that score, 0..1. Kept separate on purpose:
 *   "probably junk but we barely looked at it" must not outrank "certainly a duplicate".
 * @param costOfError how bad a wrong deletion would be, 0..1.
 * @param protected true when the item is never offered for deletion at all.
 */
data class Verdict(
    val itemId: Long,
    val junkScore: Double,
    val confidence: Double,
    val costOfError: Double,
    val category: Category,
    val reasons: List<Reason>,
    val protected: Boolean = false,
) {
    /**
     * Where the card lands in the queue. Deliberately not the junk score: the first cards a
     * tired human sees should be the ones that are both obvious and cheap to get wrong.
     */
    val dealPriority: Double
        get() = if (protected) 0.0 else junkScore * confidence * (1.0 - costOfError)
}
