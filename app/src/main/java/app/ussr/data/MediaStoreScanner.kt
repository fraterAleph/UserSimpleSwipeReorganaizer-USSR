package app.ussr.data

import android.content.ContentResolver
import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import app.ussr.core.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads the library out of MediaStore. Metadata only — no pixels are touched here, which is
 * why the first deck can be on screen seconds after launch.
 */
class MediaStoreScanner(private val resolver: ContentResolver) {

    suspend fun scan(): List<MediaItem> = withContext(Dispatchers.IO) {
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.RELATIVE_PATH,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.WIDTH,
            MediaStore.Files.FileColumns.HEIGHT,
            MediaStore.Files.FileColumns.DATE_ADDED,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.DATE_TAKEN,
            MediaStore.Files.FileColumns.DURATION,
            MediaStore.Files.FileColumns.IS_FAVORITE,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
        )
        val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (?, ?) AND " +
            "${MediaStore.Files.FileColumns.IS_TRASHED} = 0"
        val args = arrayOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
        )

        val out = ArrayList<MediaItem>(2_048)
        resolver.query(
            collection,
            projection,
            selection,
            args,
            "${MediaStore.Files.FileColumns.DATE_ADDED} DESC",
        )?.use { cursor ->
            val id = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val name = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val path = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.RELATIVE_PATH)
            val mime = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
            val size = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
            val width = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.WIDTH)
            val height = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.HEIGHT)
            val added = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED)
            val modified = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
            val taken = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_TAKEN)
            val duration = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DURATION)
            val favorite = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.IS_FAVORITE)

            while (cursor.moveToNext()) {
                out += MediaItem(
                    id = cursor.getLong(id),
                    displayName = cursor.getString(name).orEmpty(),
                    relativePath = cursor.getString(path).orEmpty(),
                    mimeType = cursor.getString(mime).orEmpty(),
                    sizeBytes = cursor.getLong(size),
                    width = cursor.getInt(width),
                    height = cursor.getInt(height),
                    // MediaStore stores these two in seconds and DATE_TAKEN in milliseconds.
                    dateAddedMs = cursor.getLong(added) * 1_000,
                    dateModifiedMs = cursor.getLong(modified) * 1_000,
                    dateTakenMs = cursor.getLong(taken).takeIf { it > 0 },
                    durationMs = cursor.getLong(duration).takeIf { it > 0 },
                    isFavorite = cursor.getInt(favorite) == 1,
                    // MediaStore exposes no album membership to a third-party app, so this
                    // stays zero; protection leans on IS_FAVORITE and on the edit timestamp.
                    albumCount = 0,
                )
            }
        }
        out
    }

    fun contentUri(item: MediaItem): Uri = ContentUris.withAppendedId(
        MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL),
        item.id,
    )

    /**
     * Resolve ids to uris in their own collection — Images for a photo, Video for a clip.
     *
     * The generic Files collection is fine for reading, but the write requests
     * (createTrashRequest, createFavoriteRequest) are stricter: handing them a Files uri is
     * rejected on some implementations, and the rejection surfaces as an exception rather
     * than a dialog. Ids that MediaStore no longer knows are dropped, because a uri pointing
     * at a file that is already gone poisons the whole request for the ones that remain.
     */
    suspend fun writableUris(ids: List<Long>): List<Uri> = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext emptyList()
        val out = ArrayList<Uri>(ids.size)
        resolver.query(
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL),
            arrayOf(
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.MEDIA_TYPE,
            ),
            "${MediaStore.Files.FileColumns._ID} IN (${ids.joinToString(",")})",
            null,
            null,
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val typeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val collection = when (cursor.getInt(typeColumn)) {
                    MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO ->
                        MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                    else ->
                        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                }
                out += ContentUris.withAppendedId(collection, id)
            }
        }
        out
    }
}
