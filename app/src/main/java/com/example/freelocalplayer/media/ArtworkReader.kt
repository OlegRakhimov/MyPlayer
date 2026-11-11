package com.example.freelocalplayer.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri

/** Extracts embedded album artwork off the main thread, returning null if the file has none. */
object ArtworkReader {
    fun read(context: Context, sourceUri: String): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, Uri.parse(sourceUri))
            val picture = retriever.embeddedPicture ?: return null
            if (picture.size > 12 * 1024 * 1024) return null
            val dimensions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(picture, 0, picture.size, dimensions)
            val sample = generateSequence(1) { it * 2 }
                .first { dimensions.outWidth / it <= 1000 && dimensions.outHeight / it <= 1000 }
            val options = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            BitmapFactory.decodeByteArray(picture, 0, picture.size, options)
        } catch (_: Exception) {
            null
        } finally {
            try { retriever.release() } catch (_: Exception) { /* no resources held */ }
        }
    }
}
