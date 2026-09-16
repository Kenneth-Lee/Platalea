package com.kenny.localmanager.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import com.kenny.localmanager.file.DocumentFileModel
import com.kenny.localmanager.player.PlaybackState
import com.kenny.localmanager.player.ACTION_PLAY
import com.kenny.localmanager.player.EXTRA_PLAYLIST_ID
import com.kenny.localmanager.player.PlaybackService
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kenny.localmanager.R
import com.kenny.localmanager.data.RecentOpenItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class RecentTabRouteState(
    val items: List<RecentOpenItem>,
    val onOpenRecentItem: (RecentOpenItem) -> Unit,
    val onDeleteRecentItem: (RecentOpenItem) -> Unit,
    val onClearRecentItems: () -> Unit
)

@Composable
fun RecentTabRoute(state: RecentTabRouteState) {
    val context = LocalContext.current
    var detailItem by remember { mutableStateOf<RecentOpenItem?>(null) }

    fun recentTypeLabel(item: RecentOpenItem): String {
        return when (item.type) {
            RECENT_TYPE_ZIP_VIEWER -> context.getString(R.string.recent_type_zip_viewer)
            RECENT_TYPE_EPUB_RENDERER -> context.getString(R.string.recent_type_epub_renderer)
            RECENT_TYPE_PDF_VIEWER -> context.getString(R.string.recent_type_pdf_viewer)
            RECENT_TYPE_PLAYLIST -> context.getString(R.string.recent_type_playlist)
            RECENT_TYPE_EXTERNAL_OPEN -> context.getString(R.string.recent_type_external_open)
            else -> item.type
        }
    }

    fun recentItemIcon(item: RecentOpenItem): ImageVector {
        val lower = item.title.lowercase(Locale.getDefault())
        return when {
            item.type == RECENT_TYPE_PLAYLIST -> Icons.AutoMirrored.Filled.QueueMusic
            item.type == RECENT_TYPE_EXTERNAL_OPEN && (lower.endsWith(".mp4") || lower.endsWith(".mkv") || lower.endsWith(".avi")) -> Icons.Default.PlayArrow
            lower.endsWith(".md.zip") || lower.endsWith(".rst.zip") || lower.endsWith(".html.zip") || lower.endsWith(".llm.zip") || lower.endsWith(".zip") -> Icons.Default.Archive
            lower.endsWith(".epub") || lower.endsWith(".txt") || lower.endsWith(".llm") -> Icons.AutoMirrored.Filled.Article
            lower.endsWith(".pdf") -> Icons.Default.PictureAsPdf
            else -> Icons.AutoMirrored.Filled.InsertDriveFile
        }
    }

    detailItem?.let { item ->
        AlertDialog(
            onDismissRequest = { detailItem = null },
            title = { Text(item.title) },
            text = {
                Column {
                    Text("${context.getString(R.string.main_tab_recent)} · ${recentTypeLabel(item)}")
                    if (!item.uri.isNullOrBlank()) {
                        Text(
                            item.uri,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 10
                        )
                    }
                    val timeLabel = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(item.openedAt))
                    Text(
                        timeLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { detailItem = null }) {
                    Text(context.getString(R.string.common_ok))
                }
            }
        )
    }

    if (state.items.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(context.getString(R.string.recent_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = state.onClearRecentItems) {
                Text(context.getString(R.string.recent_clear))
            }
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(state.items, key = { "${it.type}:${it.key}" }) { item ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { state.onOpenRecentItem(item) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            recentItemIcon(item),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                item.title,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val timeLabel = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(item.openedAt))
                            Text(
                                "${recentTypeLabel(item)} · $timeLabel",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { detailItem = item }) {
                            Icon(Icons.Default.Info, contentDescription = context.getString(R.string.context_details))
                        }
                        IconButton(onClick = { state.onDeleteRecentItem(item) }) {
                            Icon(Icons.Default.Delete, contentDescription = context.getString(R.string.recent_delete_item))
                        }
                    }
                }
            }
        }
    }
}


// ===== 最近打开域逻辑（自 FileBrowserApp.kt 平移）=====

internal const val RECENT_TYPE_ZIP_VIEWER = "zip_viewer"


internal const val RECENT_TYPE_HTML_VIEWER = "html_viewer"


internal const val RECENT_TYPE_EPUB_RENDERER = "epub_renderer"


internal const val RECENT_TYPE_PDF_VIEWER = "pdf_viewer"


internal const val RECENT_TYPE_PLAYLIST = "playlist"


internal const val RECENT_TYPE_EXTERNAL_OPEN = "external_open"


private fun buildRecentModel(uri: String, name: String): DocumentFileModel {
    return DocumentFileModel(
        name = name,
        isDirectory = false,
        uri = Uri.parse(uri),
        lastModified = 0L,
        size = 0L
    )
}


internal fun openRecentItemByType(
    context: Context,
    item: RecentOpenItem,
    currentPlaybackState: PlaybackState?,
    recordRecentOpen: (type: String, key: String, title: String, uri: String?, playlistId: String?) -> Unit,
    switchMainTab: (MainTab) -> Unit,
    onMdZipTarget: (DocumentFileModel) -> Unit,
    onHtmlZipTarget: (DocumentFileModel) -> Unit,
    onHtmlTarget: (DocumentFileModel) -> Unit,
    onHtmlUrlTarget: (String) -> Unit,
    onPicZipTarget: (DocumentFileModel) -> Unit,
    onVidZipTarget: (DocumentFileModel) -> Unit,
    onLlmZipTarget: (DocumentFileModel) -> Unit,
    onTxtTarget: (DocumentFileModel) -> Unit,
    onLlmTarget: (DocumentFileModel) -> Unit,
    onEpubTarget: (DocumentFileModel) -> Unit,
    onPdfTarget: (DocumentFileModel) -> Unit
) {
    when (item.type) {
        RECENT_TYPE_PLAYLIST -> {
            val playlistId = item.playlistId
            if (playlistId.isNullOrBlank()) {
                Toast.makeText(context, context.getString(R.string.recent_invalid_missing_playlist_id), Toast.LENGTH_SHORT).show()
                return
            }
            val alreadyPlayingSamePlaylist =
                currentPlaybackState?.playlistId == playlistId && currentPlaybackState.isPlaying
            if (!alreadyPlayingSamePlaylist) {
                val intent = Intent(context, PlaybackService::class.java).apply {
                    action = ACTION_PLAY
                    putExtra(EXTRA_PLAYLIST_ID, playlistId)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
            }
            recordRecentOpen(
                RECENT_TYPE_PLAYLIST,
                playlistId,
                item.title,
                null,
                playlistId
            )
            switchMainTab(MainTab.PLAYER)
        }

        RECENT_TYPE_EXTERNAL_OPEN -> {
            val uri = item.uri
            if (uri.isNullOrBlank()) {
                Toast.makeText(context, context.getString(R.string.recent_invalid_missing_uri), Toast.LENGTH_SHORT).show()
                return
            }
            val opened = launchExternalOpen(context, uri, null, item.title)
            if (!opened) {
                Toast.makeText(context, context.getString(R.string.recent_external_open_failed), Toast.LENGTH_SHORT).show()
                return
            }
            recordRecentOpen(
                RECENT_TYPE_EXTERNAL_OPEN,
                item.key,
                item.title,
                uri,
                null
            )
        }

        RECENT_TYPE_ZIP_VIEWER -> {
            val uri = item.uri
            if (uri.isNullOrBlank()) {
                Toast.makeText(context, context.getString(R.string.recent_invalid_missing_uri), Toast.LENGTH_SHORT).show()
                return
            }
            val model = buildRecentModel(uri, item.title)
            when {
                item.title.endsWith(".md.zip", ignoreCase = true) || item.title.endsWith(".rst.zip", ignoreCase = true) -> onMdZipTarget(model)
                item.title.endsWith(".html.zip", ignoreCase = true) -> onHtmlZipTarget(model)
                item.title.endsWith(".pic.zip", ignoreCase = true) -> onPicZipTarget(model)
                item.title.endsWith(".vid.zip", ignoreCase = true) -> onVidZipTarget(model)
                else -> {
                    Toast.makeText(context, context.getString(R.string.recent_unsupported_zip_type), Toast.LENGTH_SHORT).show()
                    return
                }
            }
        }

        RECENT_TYPE_HTML_VIEWER -> {
            val uri = item.uri
            if (uri.isNullOrBlank()) {
                Toast.makeText(context, context.getString(R.string.recent_invalid_missing_uri), Toast.LENGTH_SHORT).show()
                return
            }
            if (uri.startsWith("http://", ignoreCase = true) || uri.startsWith("https://", ignoreCase = true)) {
                onHtmlUrlTarget(uri)
            } else {
                onHtmlTarget(buildRecentModel(uri, item.title))
            }
        }

        RECENT_TYPE_EPUB_RENDERER -> {
            val uri = item.uri
            if (uri.isNullOrBlank()) {
                Toast.makeText(context, context.getString(R.string.recent_invalid_missing_uri), Toast.LENGTH_SHORT).show()
                return
            }
            val model = buildRecentModel(uri, item.title)
            when {
                item.title.endsWith(".llm.zip", ignoreCase = true) -> onLlmZipTarget(model)
                item.title.endsWith(".txt", ignoreCase = true) -> onTxtTarget(model)
                item.title.endsWith(".llm", ignoreCase = true) -> onLlmTarget(model)
                else -> onEpubTarget(model)
            }
        }

        RECENT_TYPE_PDF_VIEWER -> {
            val uri = item.uri
            if (uri.isNullOrBlank()) {
                Toast.makeText(context, context.getString(R.string.recent_invalid_missing_uri), Toast.LENGTH_SHORT).show()
                return
            }
            onPdfTarget(buildRecentModel(uri, item.title))
        }

        else -> {
            Toast.makeText(context, context.getString(R.string.recent_unknown_type, item.type), Toast.LENGTH_SHORT).show()
        }
    }
}
