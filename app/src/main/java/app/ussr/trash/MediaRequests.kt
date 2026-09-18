package app.ussr.trash

import android.content.ContentResolver
import android.content.IntentSender
import android.net.Uri
import android.provider.MediaStore
import android.util.Log

/**
 * Every write this app makes to someone else's media goes through here, and every one of
 * them is a system request the user confirms — the app holds read permission only.
 *
 * Both builders return null rather than throwing. The platform rejects a malformed request
 * with an exception, and an exception on the way to a confirmation dialog takes the whole
 * app down with it — which is exactly what the user saw: the button did nothing and the
 * screen went back to the launcher. A null reaches the caller as "no dialog to show" and
 * leaves the queue untouched, so nothing is lost and the failure is visible instead of fatal.
 */
object MediaRequests {

    private const val TAG = "MediaRequests"

    /** Some OEM implementations refuse a single request carrying thousands of uris. */
    private const val CHUNK_SIZE = 250

    fun <T> chunks(items: List<T>, size: Int = CHUNK_SIZE): List<List<T>> = items.chunked(size)

    /**
     * Move items to the system trash. This is the only thing in the app that makes a file
     * disappear, and even here it does not: Android keeps trashed items for 30 days and the
     * user can pull any of them back from their own gallery in the meantime.
     */
    fun trash(resolver: ContentResolver, uris: List<Uri>): IntentSender? {
        if (uris.isEmpty()) return null
        return runCatching {
            MediaStore.createTrashRequest(resolver, uris, true).intentSender
        }.onFailure {
            Log.e(TAG, "createTrashRequest refused ${uris.size} items", it)
        }.getOrNull()
    }

    /**
     * Set the system favourite flag.
     *
     * This is what makes a swipe up mean something outside this app: IS_FAVORITE is a
     * MediaStore column, so the stock gallery and most OEM galleries show the item in their
     * own favourites. Apps that keep a private favourites list of their own — Google Photos
     * among them — will not necessarily pick it up.
     */
    fun favorite(resolver: ContentResolver, uris: List<Uri>): IntentSender? {
        if (uris.isEmpty()) return null
        return runCatching {
            MediaStore.createFavoriteRequest(resolver, uris, true).intentSender
        }.onFailure {
            Log.e(TAG, "createFavoriteRequest refused ${uris.size} items", it)
        }.getOrNull()
    }
}
