package app.ussr.trash

import android.content.ContentResolver
import android.content.ContentUris
import android.content.IntentSender
import android.net.Uri
import android.provider.MediaStore

/**
 * The only place in the app that can make a file disappear, and even here it does not:
 * [MediaStore.createTrashRequest] moves items into the system trash, where Android keeps
 * them for 30 days before deleting them itself. The user still confirms in a system sheet,
 * and can pull anything back from the gallery's Trash in the meantime.
 */
object TrashRequest {

    fun uris(ids: List<Long>): List<Uri> = ids.map {
        ContentUris.withAppendedId(MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL), it)
    }

    /**
     * Build the system confirmation. Null when there is nothing to trash.
     *
     * Items are sent in chunks: a single request with thousands of uris is refused by some
     * OEM implementations, and a smaller sheet is easier to read besides.
     */
    fun create(resolver: ContentResolver, ids: List<Long>): IntentSender? {
        if (ids.isEmpty()) return null
        return MediaStore.createTrashRequest(resolver, uris(ids), true).intentSender
    }

    fun chunks(ids: List<Long>, size: Int = CHUNK_SIZE): List<List<Long>> = ids.chunked(size)

    private const val CHUNK_SIZE = 250
}
