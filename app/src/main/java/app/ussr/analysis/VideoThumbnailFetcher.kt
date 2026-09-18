package app.ussr.analysis

import android.content.Context
import android.net.Uri
import android.util.Size
import androidx.core.graphics.drawable.toDrawable
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.request.Options
import coil.size.Dimension
import coil.size.pxOrElse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Loads a video's poster frame from MediaStore instead of decoding the file.
 *
 * Coil can pull a frame out of a video itself, but only after buffering the clip somewhere
 * it can seek, and on a content uri that quietly fails often enough that half the deck came
 * back as black rectangles. MediaStore already keeps a thumbnail for every video it knows
 * about and hands it over in one call, so this asks for that instead of re-deriving it.
 *
 * Registered ahead of the video decoder, which stays in place as the fallback for anything
 * MediaStore has no thumbnail for.
 */
class VideoThumbnailFetcher(
    private val context: Context,
    private val uri: Uri,
    private val options: Options,
) : Fetcher {

    override suspend fun fetch(): FetchResult = withContext(Dispatchers.IO) {
        val width = options.size.width.pxOrDefault()
        val height = options.size.height.pxOrDefault()
        val bitmap = context.contentResolver.loadThumbnail(uri, Size(width, height), null)
        DrawableResult(
            drawable = bitmap.toDrawable(context.resources),
            isSampled = true,
            dataSource = DataSource.DISK,
        )
    }

    private fun Dimension.pxOrDefault(): Int = pxOrElse { DEFAULT_EDGE }.coerceAtLeast(64)

    class Factory(private val context: Context) : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            if (data.scheme != "content") return null
            val type = context.contentResolver.getType(data) ?: return null
            if (!type.startsWith("video/")) return null
            return VideoThumbnailFetcher(context, data, options)
        }
    }

    private companion object {
        const val DEFAULT_EDGE = 512
    }
}
