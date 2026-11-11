package com.example.freelocalplayer.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.freelocalplayer.data.MediaEntry
import com.example.freelocalplayer.media.MediaThumbnailReader

/** Displays a video frame or embedded album cover in every media list. */
@Composable
internal fun MediaThumbnail(entry: MediaEntry) {
    val context = LocalContext.current.applicationContext
    val thumbnail by produceState<android.graphics.Bitmap?>(
        initialValue = null,
        key1 = entry.uri,
        key2 = entry.isVideo
    ) {
        value = MediaThumbnailReader.load(context, entry.uri, entry.isVideo)
    }
    Box(
        modifier = Modifier
            .size(width = if (entry.isVideo) 76.dp else 56.dp, height = 56.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (thumbnail != null) {
            Image(
                bitmap = thumbnail!!.asImageBitmap(),
                contentDescription = if (entry.isVideo) "Кадр видео: ${entry.title}" else "Обложка: ${entry.title}",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Icon(
                imageVector = if (entry.isVideo) Icons.Default.VideoFile else Icons.Default.Audiotrack,
                contentDescription = null,
                modifier = Modifier.size(26.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
