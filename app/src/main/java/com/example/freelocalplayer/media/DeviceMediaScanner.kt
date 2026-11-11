package com.example.freelocalplayer.media

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.example.freelocalplayer.data.MediaEntry

/**
 * Reads only the Android MediaStore collections for which the user granted access.
 * No file-system traversal, private folders, network connection, or MANAGE_EXTERNAL_STORAGE.
 */
object DeviceMediaScanner {
    const val MAX_ITEMS_PER_CATEGORY = 20_000

    fun requiredPermissions(): Array<String> = when {
        Build.VERSION.SDK_INT >= 34 -> arrayOf(
            Manifest.permission.READ_MEDIA_AUDIO,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
        )
        Build.VERSION.SDK_INT >= 33 -> arrayOf(
            Manifest.permission.READ_MEDIA_AUDIO,
            Manifest.permission.READ_MEDIA_VIDEO
        )
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    private fun granted(context: Context, permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun canReadAny(context: Context): Boolean = when {
        Build.VERSION.SDK_INT >= 34 -> granted(context, Manifest.permission.READ_MEDIA_AUDIO) ||
            granted(context, Manifest.permission.READ_MEDIA_VIDEO) ||
            granted(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        Build.VERSION.SDK_INT >= 33 -> granted(context, Manifest.permission.READ_MEDIA_AUDIO) ||
            granted(context, Manifest.permission.READ_MEDIA_VIDEO)
        else -> granted(context, Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    /** Returns visible audio/video files, including those granted individually on Android 14+. */
    fun scan(context: Context): List<MediaEntry> {
        val audioAllowed = if (Build.VERSION.SDK_INT >= 33) {
            granted(context, Manifest.permission.READ_MEDIA_AUDIO)
        } else granted(context, Manifest.permission.READ_EXTERNAL_STORAGE)
        val videoAllowed = if (Build.VERSION.SDK_INT >= 34) {
            granted(context, Manifest.permission.READ_MEDIA_VIDEO) ||
                granted(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        } else if (Build.VERSION.SDK_INT >= 33) {
            granted(context, Manifest.permission.READ_MEDIA_VIDEO)
        } else granted(context, Manifest.permission.READ_EXTERNAL_STORAGE)

        val results = ArrayList<MediaEntry>()
        if (audioAllowed) results += readCollection(context, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, false)
        if (videoAllowed) results += readCollection(context, MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true)
        return results.distinctBy(MediaEntry::uri)
    }

    private fun readCollection(context: Context, collection: android.net.Uri, video: Boolean): List<MediaEntry> {
        val items = ArrayList<MediaEntry>()
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE
        )
        try {
            context.contentResolver.query(
                collection, projection, null, null,
                "${MediaStore.MediaColumns.DISPLAY_NAME} COLLATE NOCASE ASC"
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val titleIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val mimeIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
                while (cursor.moveToNext() && items.size < MAX_ITEMS_PER_CATEGORY) {
                    val mime = cursor.getString(mimeIndex).orEmpty()
                    val id = cursor.getLong(idIndex)
                    val name = cursor.getString(titleIndex)?.takeIf(String::isNotBlank) ?: "Файл $id"
                    val validMime = mime.isBlank() || mime.startsWith(if (video) "video/" else "audio/")
                    // Some vendors index WAV as application/octet-stream in Audio.Media.
                    if (!validMime && (video || !MediaFormatSupport.isWave(name))) continue
                    items += MediaEntry(
                        uri = ContentUris.withAppendedId(collection, id).toString(),
                        title = name,
                        isVideo = video
                    )
                }
            }
        } catch (_: SecurityException) {
            // Permission may have changed between check and query; preserve already imported files.
        } catch (_: IllegalArgumentException) {
            // Some vendor-specific MediaStore implementations reject query/sort fields.
        }
        return items
    }
}
