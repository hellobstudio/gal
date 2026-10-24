package hu.egyszeru.galeria.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.database.ContentObserver
import android.net.Uri
import android.os.Bundle
import android.provider.BaseColumns
import android.provider.MediaStore
import android.provider.MediaStore.MediaColumns

/**
 * Minden fájlművelet a rendszer MediaStore-ján keresztül megy, így az app
 * nem igényel "minden fájl elérése" engedélyt.
 */
class MediaRepository(context: Context) {

    private val resolver = context.applicationContext.contentResolver

    private val imagesUri: Uri = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    private val videosUri: Uri = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)

    private val projection = arrayOf(
        BaseColumns._ID,
        MediaColumns.DISPLAY_NAME,
        MediaColumns.MIME_TYPE,
        MediaColumns.SIZE,
        MediaColumns.DATE_TAKEN,
        MediaColumns.DATE_MODIFIED,
        MediaColumns.DATE_ADDED,
        MediaColumns.WIDTH,
        MediaColumns.HEIGHT,
        MediaColumns.DURATION,
        MediaColumns.BUCKET_ID,
        MediaColumns.BUCKET_DISPLAY_NAME,
        MediaColumns.RELATIVE_PATH,
    )

    /** Összes kép és videó, legújabb elöl. [trashed] = true esetén a lomtár tartalma. */
    fun query(trashed: Boolean): List<MediaItem> {
        val out = ArrayList<MediaItem>()
        runCatching { queryCollection(imagesUri, isVideo = false, trashed = trashed, out = out) }
        runCatching { queryCollection(videosUri, isVideo = true, trashed = trashed, out = out) }
        out.sortByDescending { it.dateTaken }
        return out
    }

    private fun queryCollection(collection: Uri, isVideo: Boolean, trashed: Boolean, out: MutableList<MediaItem>) {
        val args = Bundle().apply {
            putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, if (trashed) MediaStore.MATCH_ONLY else MediaStore.MATCH_EXCLUDE)
            putInt(MediaStore.QUERY_ARG_MATCH_PENDING, MediaStore.MATCH_EXCLUDE)
        }
        resolver.query(collection, projection, args, null)?.use { c ->
            val iId = c.getColumnIndexOrThrow(BaseColumns._ID)
            val iName = c.getColumnIndexOrThrow(MediaColumns.DISPLAY_NAME)
            val iMime = c.getColumnIndexOrThrow(MediaColumns.MIME_TYPE)
            val iSize = c.getColumnIndexOrThrow(MediaColumns.SIZE)
            val iTaken = c.getColumnIndexOrThrow(MediaColumns.DATE_TAKEN)
            val iModified = c.getColumnIndexOrThrow(MediaColumns.DATE_MODIFIED)
            val iAdded = c.getColumnIndexOrThrow(MediaColumns.DATE_ADDED)
            val iWidth = c.getColumnIndexOrThrow(MediaColumns.WIDTH)
            val iHeight = c.getColumnIndexOrThrow(MediaColumns.HEIGHT)
            val iDuration = c.getColumnIndexOrThrow(MediaColumns.DURATION)
            val iBucketId = c.getColumnIndexOrThrow(MediaColumns.BUCKET_ID)
            val iBucketName = c.getColumnIndexOrThrow(MediaColumns.BUCKET_DISPLAY_NAME)
            val iPath = c.getColumnIndexOrThrow(MediaColumns.RELATIVE_PATH)

            while (c.moveToNext()) {
                val id = c.getLong(iId)
                val taken = c.getLong(iTaken)
                val modified = c.getLong(iModified) * 1000L
                val date = when {
                    taken > 0 -> taken
                    modified > 0 -> modified
                    else -> c.getLong(iAdded) * 1000L
                }
                out += MediaItem(
                    id = id,
                    uri = ContentUris.withAppendedId(collection, id),
                    name = c.getString(iName) ?: "",
                    dateTaken = date,
                    size = c.getLong(iSize),
                    mimeType = c.getString(iMime) ?: "",
                    isVideo = isVideo,
                    width = c.getInt(iWidth),
                    height = c.getInt(iHeight),
                    duration = c.getLong(iDuration),
                    bucketId = c.getLong(iBucketId),
                    bucketName = c.getString(iBucketName) ?: "",
                    relativePath = c.getString(iPath) ?: "",
                )
            }
        }
    }

    fun registerObserver(observer: ContentObserver) {
        resolver.registerContentObserver(imagesUri, true, observer)
        resolver.registerContentObserver(videosUri, true, observer)
    }

    fun unregisterObserver(observer: ContentObserver) = resolver.unregisterContentObserver(observer)

    /** Lomtárba helyezés / visszaállítás. A rendszer kéri a felhasználó jóváhagyását. */
    fun trashRequest(uris: List<Uri>, trash: Boolean): IntentSender =
        MediaStore.createTrashRequest(resolver, uris, trash).intentSender

    /** Végleges törlés. */
    fun deleteRequest(uris: List<Uri>): IntentSender =
        MediaStore.createDeleteRequest(resolver, uris).intentSender

    /** Írási jog kérése (átnevezéshez). */
    fun writeRequest(uris: List<Uri>): IntentSender =
        MediaStore.createWriteRequest(resolver, uris).intentSender

    /** Átnevezés – csak sikeres [writeRequest] után hívható. */
    fun rename(uri: Uri, newName: String): Boolean {
        val values = ContentValues().apply { put(MediaColumns.DISPLAY_NAME, newName) }
        return resolver.update(uri, values, null, null) > 0
    }
}
