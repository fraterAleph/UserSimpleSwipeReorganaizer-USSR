package app.ussr.core.model

/**
 * What the cheap pixel pass found. Filled for every item during the first sweep.
 */
data class VisualSignals(
    /** 64-bit difference hash of a 9x8 grayscale downscale. */
    val dHash: Long,
    /** Variance of the Laplacian on a grayscale downscale — low means blurry. */
    val laplacianVariance: Double,
    /** Mean grayscale level, 0..255. Near-zero or near-255 means a blank frame. */
    val meanLuma: Double,
)

/**
 * What the ML pass found. Null until the background pass reaches the item, and the
 * scorer is expected to work without it.
 */
data class ContentSignals(
    /** Text ML Kit recognised, already joined and trimmed. */
    val text: String,
    /** Number of recognised text blocks — a proxy for "this is a page, not a caption". */
    val textBlockCount: Int,
    /** Image labels above the confidence floor, lowercase. */
    val labels: List<String>,
    /** Faces ML Kit found, when face detection ran. */
    val faceCount: Int = 0,
)

enum class MediaSource {
    Camera,
    Screenshot,
    ScreenRecording,
    Messenger,
    Download,
    SocialSave,
    Other,
}
