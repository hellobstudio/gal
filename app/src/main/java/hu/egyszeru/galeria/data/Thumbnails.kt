package hu.egyszeru.galeria.data

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.util.Size
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.key.Keyer
import coil.request.Options
import coil.size.Dimension

/**
 * Bélyegkép-kérés. A rendszer (MediaStore) saját, gyorsítótárazott bélyegképeit
 * használja, így nagy galériákban is gyors a görgetés, és videóknál is működik.
 */
data class Thumb(val uri: Uri)

class ThumbKeyer : Keyer<Thumb> {
    override fun key(data: Thumb, options: Options): String = "thumb:${data.uri}"
}

class ThumbFetcher(
    private val data: Thumb,
    private val options: Options,
    private val context: Context,
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val w = (options.size.width as? Dimension.Pixels)?.px ?: 512
        val h = (options.size.height as? Dimension.Pixels)?.px ?: 512
        val bitmap = context.contentResolver.loadThumbnail(
            data.uri,
            Size(w.coerceAtLeast(64), h.coerceAtLeast(64)),
            null,
        )
        return DrawableResult(
            drawable = BitmapDrawable(context.resources, bitmap),
            isSampled = true,
            dataSource = DataSource.DISK,
        )
    }

    class Factory(private val context: Context) : Fetcher.Factory<Thumb> {
        override fun create(data: Thumb, options: Options, imageLoader: ImageLoader): Fetcher =
            ThumbFetcher(data, options, context)
    }
}
