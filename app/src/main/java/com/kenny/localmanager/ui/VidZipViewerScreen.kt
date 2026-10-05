package com.kenny.localmanager.ui

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kenny.localmanager.R
import com.kenny.localmanager.file.extractVidZipVideoRange
import com.kenny.localmanager.file.formatSize
import com.kenny.localmanager.file.getVidZipEntrySize
import com.kenny.localmanager.file.launchExternalOpen
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ============================================================================
// .vid.zip 查看器：解压完成后的视频列表（按需解压单个视频后调外部播放器打开）。
// 加密退出善后（缓存删除确认）由宿主 ArchiveViewerRouteHost 负责，本组件只管列表 UI。
// ============================================================================

@Composable
internal fun VidZipViewerScreen(
    contentDir: File,
    videoPaths: List<String>,
    zipUri: Uri,
    isEncrypted: Boolean,
    password: CharArray?,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

Surface(
    modifier = Modifier.fillMaxSize(),
    color = MaterialTheme.colorScheme.background
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.browser_view_vid_zip_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { onClose() }) {
                Text(stringResource(R.string.common_close))
            }
        }
        Spacer(Modifier.height(12.dp))
        if (videoPaths.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.browser_no_video_in_archive),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(videoPaths.size) { index ->
                    val path = videoPaths[index]
                    val file = File(contentDir, path)
                    val isReady = file.exists()
                    var extracting by remember(path, zipUri) { mutableStateOf(false) }
                    var itemProgress by remember(path, zipUri) { mutableIntStateOf(0) }
                    val entrySize = remember(path, zipUri, isEncrypted, password) {
                        getVidZipEntrySize(context, zipUri, path, if (isEncrypted) password else null)
                    }
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TextButton(
                            onClick = {
                                if (extracting || isReady) {
                                    if (!isReady) return@TextButton
                                }
                                scope.launch {
                                    if (!isReady) {
                                        extracting = true
                                        itemProgress = 0
                                        val ok = withContext(Dispatchers.IO) {
                                            extractVidZipVideoRange(
                                                context,
                                                contentDir.parentFile ?: return@withContext false,
                                                contentDir,
                                                videoPaths,
                                                index,
                                                index + 1,
                                                if (isEncrypted) password else null,
                                                zipUri,
                                                onProgress = { copied, total ->
                                                    if (total > 0L) {
                                                        val percent = ((copied.toFloat() / total.toFloat()) * 100f).toInt().coerceIn(0, 100)
                                                        scope.launch(Dispatchers.Main.immediate) {
                                                            itemProgress = percent
                                                        }
                                                    }
                                                }
                                            )
                                        }
                                        extracting = false
                                        itemProgress = 100
                                        if (!ok) {
                                            Toast.makeText(context, context.getString(R.string.browser_extract_failed), Toast.LENGTH_SHORT).show()
                                            return@launch
                                        }
                                    }
                                    val targetFile = File(contentDir, path)
                                    val opened = launchExternalOpen(context, android.net.Uri.fromFile(targetFile).toString(), null, targetFile.name)
                                    if (!opened) {
                                        Toast.makeText(context, context.getString(R.string.browser_no_app_to_open), Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !extracting
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    path,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(Modifier.height(2.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = if (isReady) {
                                            stringResource(R.string.browser_vid_zip_cached)
                                        } else {
                                            stringResource(R.string.browser_vid_zip_not_cached)
                                        },
                                        color = if (isReady) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    if (entrySize > 0L) {
                                        Text(
                                            text = stringResource(R.string.browser_vid_zip_size, formatSize(entrySize)),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                        }
                        if (extracting) {
                            Spacer(Modifier.height(4.dp))
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(R.string.browser_extracting_file, path.substringAfterLast('/')),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "$itemProgress%",
                                        color = MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    progress = { itemProgress / 100f },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
}