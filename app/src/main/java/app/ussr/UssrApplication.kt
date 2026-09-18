package app.ussr

import android.app.Application
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
     * Coil decodes images out of the box and nothing else. Without [VideoFrameDecoder] every
     * video in the deck renders as an empty black card, which is exactly what it did.
     *
     * The memory cache is capped low deliberately: cards are full-screen bitmaps and the deck
     * only ever shows two at a time, so a large cache buys nothing and costs the background
     * analysis its headroom.
     */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .components { add(VideoFrameDecoder.Factory()) }
        .memoryCache {
            MemoryCache.Builder(this)
                .maxSizePercent(0.15)
                .build()
        }
        .crossfade(true)
        .build()
}
