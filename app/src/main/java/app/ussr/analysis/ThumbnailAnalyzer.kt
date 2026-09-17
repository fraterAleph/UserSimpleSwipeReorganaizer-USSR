package app.ussr.analysis

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.Size
import app.ussr.core.analysis.PixelAnalysis
import app.ussr.core.model.VisualSignals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The cheap pass: decode a small thumbnail, reduce it to grayscale and hand the numbers to
 * the pure analysis in :core. Never decodes a full-size bitmap — a 50-megapixel photo and a
 * screenshot cost the same here.
 */
class ThumbnailAnalyzer(private val resolver: ContentResolver) {

    suspend fun analyse(uri: Uri): VisualSignals? = withContext(Dispatchers.IO) {
        val decoded = loadThumbnail(uri) ?: return@withContext null
        // A HARDWARE bitmap lives in graphics memory and has no pixels to read back, so
        // getPixels would throw on it. loadThumbnail can hand one over on some devices.
        val bitmap = decoded.readable()
        try {
            val gray = toGrayscale(bitmap)
            val hashSource = PixelAnalysis.downscale(
                gray = gray,
                srcWidth = bitmap.width,
                srcHeight = bitmap.height,
                dstWidth = PixelAnalysis.HASH_WIDTH,
                dstHeight = PixelAnalysis.HASH_HEIGHT,
            )
            VisualSignals(
                dHash = PixelAnalysis.dHash(hashSource),
                laplacianVariance = PixelAnalysis.laplacianVariance(gray, bitmap.width, bitmap.height),
                meanLuma = PixelAnalysis.meanLuma(gray),
            )
        } finally {
            if (bitmap !== decoded) decoded.recycle()
            bitmap.recycle()
        }
    }

    private fun Bitmap.readable(): Bitmap =
        if (config == Bitmap.Config.HARDWARE) {
            copy(Bitmap.Config.ARGB_8888, false) ?: this
        } else {
            this
        }

    private fun loadThumbnail(uri: Uri): Bitmap? = runCatching {
        // loadThumbnail serves the system's cached thumbnail where one exists, which is why
        // a full library sweep stays in the seconds rather than the minutes.
        resolver.loadThumbnail(uri, Size(THUMB_SIZE, THUMB_SIZE), null)
    }.recoverCatching {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, _, _ ->
            decoder.setTargetSampleSize(8)
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = false
        }
    }.getOrNull()

    private fun toGrayscale(bitmap: Bitmap): IntArray {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return IntArray(pixels.size) { index ->
            val pixel = pixels[index]
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            // Rec. 601 luma, in integer arithmetic.
            (r * 299 + g * 587 + b * 114) / 1000
        }
    }

    private companion object {
        const val THUMB_SIZE = 256
    }
}
