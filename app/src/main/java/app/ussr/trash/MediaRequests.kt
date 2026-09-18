package app.ussr.trash

import android.content.ContentResolver
import android.content.ContentUris
import android.content.IntentSender
import android.net.Uri
import android.provider.MediaStore

/**
 * Every write this app makes to someone else's media goes through here, and every one of
 * them is a system request the user confirms — the app holds read permission only.
 */
object MediaRequests {

    /** Some OEM implementations refuse a single request carrying thousands of uris. */
    private const val CHUNK_SIZE = 250

    fun uris(ids: List<Long>): List<Uri> = ids.map {
        ContentUris.withAppendedId(MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL), it)
    }

    fun chunks(ids: List<Long>, size: Int = CHUNK_SIZE): List<List<Long>> = ids.chunked(size)

    /**
     * Move items to the system trash. This is the only thing in the app that makes a file
     * disappear, and even here it does not: Android keeps trashed items for 30 days and the
     * user can pull any of them back from their own gallery in the meantime.
     */
    fun trash(resolver: ContentResolver, ids: List<Long>): IntentSender? {
        if (ids.isEmpty()) return null
        return MediaStore.createTrashRequest(resolver, uris(ids), true).intentSender
    }

    /**
     * Set the system favourite flag.
     *
     * This is what makes a swipe up mean something outside this app: IS_FAVORITE is a
     * MediaStore column, so the stock gallery and most OEM galleries show the item in their
     * own favourites. Apps that keep a private favourites list of their own — Google Photos
     * among them — will not necessarily pick it up.
     */
    fun favorite(resolver: ContentResolver, ids: List<Long>): IntentSender? {
        if (ids.isEmpty()) return null
        return MediaStore.createFavoriteRequest(resolver, uris(ids), true).intentSender
    }
}
