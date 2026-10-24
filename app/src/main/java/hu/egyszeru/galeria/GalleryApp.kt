package hu.egyszeru.galeria

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import hu.egyszeru.galeria.data.ThumbFetcher
import hu.egyszeru.galeria.data.ThumbKeyer

class GalleryApp : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components {
                add(ThumbKeyer())
                add(ThumbFetcher.Factory(this@GalleryApp))
            }
            .crossfade(true)
            .build()
}
