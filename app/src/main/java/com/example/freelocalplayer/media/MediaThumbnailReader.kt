package com.example.freelocalplayer.media

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.LruCache
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlin.math.max

/**
 * On-device, small image previews. No network or disk cache.
 *
 * The LRU retains no more than 6 MiB of decoded bitmaps. Work is limited to
 * one decoder at a time. Android memory pressure clears the entire cache.
 * On-screen Compose images may still retain their own references until disposed.
 */
object MediaThumbnailReader {
    private const val MAX_EMBEDDED_IMAGE_BYTES = 4 * 1024 * 1024
    private const val MAX_BITMAP_EDGE = 192
    private const val MAX_CACHE_BYTES = 6 * 1024 * 1024

    private val decodingSlots = Semaphore(1)
    private val thumbnailCache = object : LruCache<String, Bitmap>(MAX_CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = max(1, value.byteCount)
    }

    private val callbackLock = Any()
    private var callbackRegistered = false
    private val memoryCallback = object : ComponentCallbacks2 {
        override fun onConfigurationChanged(newConfig: Configuration) = Unit

        override fun onLowMemory() {
            synchronized(thumbnailCache) { thumbnailCache.evictAll() }
        }

        override fun onTrimMemory(level: Int) {
            // TRIM_MEMORY_RUNNING_LOW (10) and UI_HIDDEN (20) or more severe.
            // A cached thumbnail is disposable: always allow Android to reclaim it.
            if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
                synchronized(thumbnailCache) { thumbnailCache.evictAll() }
            }
        }
    }

    private fun ensureMemoryCallback(context: Context) {
        synchronized(callbackLock) {
            if (!callbackRegistered) {
                context.applicationContext.registerComponentCallbacks(memoryCallback)
                callbackRegistered = true
            }
        }
    }

    suspend fun load(context: Context, sourceUri: String, isVideo: Boolean): Bitmap? =
        withContext(Dispatchers.IO) {
            ensureMemoryCallback(context)
            val key = (if (isVideo) "video|" else "audio|") + sourceUri
            synchronized(thumbnailCache) { thumbnailCache.get(key) }?.let { return@withContext it }

            decodingSlots.withPermit {
                synchronized(thumbnailCache) { thumbnailCache.get(key) }?.let { return@withPermit it }
                val uri = Uri.parse(sourceUri)
                val retriever = MediaMetadataRetriever()
                val extracted = try {
                    retriever.setDataSource(context, uri)
                    if (isVideo) readVideoFrame(retriever) else readAudioArtwork(retriever)
                } catch (_: Exception) {
                    null
                } finally {
                    try { retriever.release() } catch (_: Exception) { }
                }

                val image = extracted ?: readSystemThumbnail(context, uri)
                if (image != null) {
                    synchronized(thumbnailCache) { thumbnailCache.put(key, image) }
                }
                image
            }
        }

    private fun readSystemThumbnail(context: Context, uri: Uri): Bitmap? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.contentResolver.loadThumbnail(uri, Size(MAX_BITMAP_EDGE, MAX_BITMAP_EDGE), null)
        } else null
    } catch (_: Exception) {
        null
    }

    private fun readVideoFrame(retriever: MediaMetadataRetriever): Bitmap? {
        val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toLongOrNull() ?: 0L
        val timeMs = if (durationMs > 0L) {
            (durationMs / 5L).coerceIn(0L, minOf(durationMs, 30_000L))
        } else {
            1_000L
        }
        return retriever.getScaledFrameAtTime(
            timeMs * 1_000L,
            MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
            MAX_BITMAP_EDGE,
            108
        ) ?: retriever.getScaledFrameAtTime(
            0L,
            MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
            MAX_BITMAP_EDGE,
            108
        )
    }

    private fun readAudioArtwork(retriever: MediaMetadataRetriever): Bitmap? {
        val bytes = retriever.embeddedPicture ?: return null
        if (bytes.isEmpty() || bytes.size > MAX_EMBEDDED_IMAGE_BYTES) return null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val largest = max(bounds.outWidth, bounds.outHeight)
        if (largest <= 0 || largest > 20_000) return null
        var sampleSize = 1
        while (largest / sampleSize > MAX_BITMAP_EDGE) sampleSize *= 2
        return BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
            }
        )
    }
}
