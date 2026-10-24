package hu.egyszeru.galeria.data

import android.net.Uri

data class MediaItem(
    val id: Long,
    val uri: Uri,
    val name: String,
    /** Készítés ideje ezredmásodpercben. */
    val dateTaken: Long,
    val size: Long,
    val mimeType: String,
    val isVideo: Boolean,
    val width: Int,
    val height: Int,
    /** Videó hossza ezredmásodpercben (képnél 0). */
    val duration: Long,
    val bucketId: Long,
    val bucketName: String,
    val relativePath: String,
) {
    /** Fájlnév kiterjesztés nélkül (átnevezéshez). */
    val baseName: String
        get() = name.lastIndexOf('.').let { if (it > 0) name.substring(0, it) else name }

    /** Kiterjesztés ponttal együtt, pl. ".jpg". */
    val extension: String
        get() = name.lastIndexOf('.').let { if (it > 0) name.substring(it) else "" }
}

/** Felhasználó által létrehozott (virtuális) album: a fájlok a helyükön maradnak, csak hivatkozunk rájuk. */
data class UserAlbum(
    val id: Long,
    val name: String,
    val items: Set<Long>,
)
