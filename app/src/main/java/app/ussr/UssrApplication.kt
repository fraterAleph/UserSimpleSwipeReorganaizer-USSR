package app.ussr

import android.app.Application
import app.ussr.analysis.VideoThumbnailFetcher
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import coil.memory.MemoryCache

class UssrApplication : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        // Warm the database off the first frame; everything else is built lazily.
        ServiceLocator.database(this)
    }

    /**
     * Coil decodes images out of the box and nothing else, so every video in the deck came
     * back as a black card. Two things are registered for them, in order: MediaStore's own
     * thumbnail, which it already has for anything in the library and serves in one call,
     * and [VideoFrameDecoder] behind it for whatever MediaStore has no thumbnail for.
     *
     * The memory cache is capped low deliberately: cards are full-screen bitmaps and the deck
     * only ever shows two at a time, so a large cache buys nothing and costs the background
     * analysis its headroom.
     */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .components {
            add(VideoThumbnailFetcher.Factory(this@UssrApplication))
            add(VideoFrameDecoder.Factory())
        }
        .memoryCache {
            MemoryCache.Builder(this)
                .maxSizePercent(0.15)
                .build()
        }
        .crossfade(true)
        .build()
}
