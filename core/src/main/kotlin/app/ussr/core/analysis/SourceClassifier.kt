package app.ussr.core.analysis

import app.ussr.core.model.MediaItem
import app.ussr.core.model.MediaSource
import java.util.Locale

/**
 * Where a file came from, read off its folder and name. No pixels involved, so this runs
 * over the whole library in the first seconds and is what fills the first swipe queue.
 */
object SourceClassifier {

    fun classify(item: MediaItem): MediaSource {
        val path = item.relativePath.lowercase(Locale.ROOT)
        val name = item.displayName.lowercase(Locale.ROOT)

        return when {
            matchesAny(path, SCREEN_RECORDING_DIRS) || name.startsWith("screen_recording") ->
                MediaSource.ScreenRecording

            matchesAny(path, SCREENSHOT_DIRS) || matchesAnyPrefix(name, SCREENSHOT_PREFIXES) ->
                MediaSource.Screenshot

            matchesAny(path, MESSENGER_DIRS) -> MediaSource.Messenger

            matchesAny(path, SOCIAL_DIRS) -> MediaSource.SocialSave

            matchesAny(path, DOWNLOAD_DIRS) -> MediaSource.Download

            matchesAny(path, CAMERA_DIRS) -> MediaSource.Camera

            else -> MediaSource.Other
        }
    }

    /**
     * A screenshot that the folder name did not give away: a still whose pixel size matches
     * the screen almost exactly. Callers pass the display size they read at runtime.
     */
    fun looksLikeScreenSized(item: MediaItem, screenWidth: Int, screenHeight: Int): Boolean {
        if (item.isVideo || screenWidth <= 0 || screenHeight <= 0) return false
        val portraitMatch = item.width == screenWidth && item.height >= screenHeight - STATUS_BAR_SLACK
        val landscapeMatch = item.width == screenHeight && item.height >= screenWidth - STATUS_BAR_SLACK
        return portraitMatch || landscapeMatch
    }

    private fun matchesAny(path: String, needles: Set<String>) = needles.any { path.contains(it) }

    private fun matchesAnyPrefix(name: String, prefixes: Set<String>) = prefixes.any { name.startsWith(it) }

    private val SCREENSHOT_DIRS = setOf(
        "screenshot", "screenshots", "скриншот", "capture", "screencapture",
    )

    private val SCREEN_RECORDING_DIRS = setOf(
        "screenrecord", "screen recordings", "screen-recorder", "screenrecorder",
    )

    // Covers the media sub-folders the big messengers write into, old and scoped-storage layouts alike.
    private val MESSENGER_DIRS = setOf(
        "whatsapp", "telegram", "viber", "signal", "threema", "wechat", "line/",
        "messenger", "imo", "discord", "slack/media", "max/", "vkmessenger",
    )

    private val SOCIAL_DIRS = setOf(
        "instagram", "tiktok", "twitter", "x/media", "pinterest", "facebook", "reddit", "snapchat", "vk/",
    )

    private val DOWNLOAD_DIRS = setOf("download", "downloads", "bluetooth", "browser")

    private val CAMERA_DIRS = setOf("dcim/camera", "dcim/100", "camera/")

    private val SCREENSHOT_PREFIXES = setOf("screenshot", "screen_shot", "scr_", "screen-")

    private const val STATUS_BAR_SLACK = 200
}
