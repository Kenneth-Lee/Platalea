package com.kenny.localmanager.ui

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
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
    val playlistNoteById: Map<String, String>,
    val onOpenRecentItem: (RecentOpenItem) -> Unit,
    val onDeleteRecentItem: (RecentOpenItem) -> Unit,
    val onClearRecentItems: () -> Unit
)

@Composable
fun RecentTabRoute(state: RecentTabRouteState) {
    val context = LocalContext.current

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
                            if (item.type == RECENT_TYPE_PLAYLIST) {
                                val note = item.playlistId?.let { state.playlistNoteById[it] }?.trim().orEmpty()
                                if (note.isNotEmpty()) {
                                    Text(
                                        context.getString(R.string.recent_note_prefix, note),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
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
