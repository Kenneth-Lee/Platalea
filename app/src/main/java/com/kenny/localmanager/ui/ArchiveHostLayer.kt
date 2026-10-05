package com.kenny.localmanager.ui

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.kenny.localmanager.R
import com.kenny.localmanager.file.createFileWithBytes
import com.kenny.localmanager.file.findChildByName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

// Archive 解包域宿主接线层（批次 A）：执行逻辑与渲染装配自 PlataeaApp 主函数下沉。
// - savePicZipImageToRoot：pic.zip 查看器"保存到根目录"执行逻辑（rootUri 参数化）
// - ArchiveViewerHostLayer：解压完成后全屏查看器的装配
// - EncryptedCacheExitConfirmLayer：加密缓存退出确认对话框（状态由宿主持有，善后动作在 dialog 携带）

internal suspend fun savePicZipImageToRoot(
    context: Context,
    rootUri: String?,
    sourceFile: File,
    fileName: String
): Boolean {
        val targetRoot = rootUri?.let { normalizeContentUriString(it) }
        if (targetRoot == null) {
            Toast.makeText(context, context.getString(R.string.common_select_root_first), Toast.LENGTH_SHORT).show()
            return false
        }
        val bytes = withContext(Dispatchers.IO) {
            runCatching { sourceFile.readBytes() }.getOrNull()
        } ?: run {
            Toast.makeText(context, context.getString(R.string.browser_save_image_read_failed), Toast.LENGTH_SHORT).show()
            return false
        }
        val targetUri = Uri.parse(targetRoot)
        val dotIndex = fileName.lastIndexOf('.')
        val baseName = if (dotIndex > 0) fileName.substring(0, dotIndex) else fileName
        val ext = if (dotIndex > 0) fileName.substring(dotIndex) else ""
        var outName = fileName
        var copyIndex = 1
        while (findChildByName(context, targetUri, outName) != null) {
            outName = "$baseName ($copyIndex)$ext"
            copyIndex++
        }
        val mimeType = when (fileName.substringAfterLast('.', "").lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "bmp" -> "image/bmp"
            else -> "application/octet-stream"
        }
        val ok = withContext(Dispatchers.IO) {
            createFileWithBytes(context, targetUri, targetUri, outName, mimeType, bytes)
        }
        Toast.makeText(context, if (ok) context.getString(R.string.browser_saved_to_root, outName) else context.getString(R.string.viewer_save_failed), Toast.LENGTH_SHORT).show()
        return ok
}

@Composable
internal fun ArchiveViewerHostLayer(
    workflow: ViewerHostWorkflow,
    browserTab: BrowserTabState,
    readerConfig: ReaderConfigView,
    markdownViewerSessionCache: ViewerSessions,
    bookNote: BookNoteTabFeature,
    recordRecentOpen: (type: String, key: String, title: String, uri: String?, playlistId: String?) -> Unit,
    onEncryptedExitRequest: (EncryptedCacheExitDialogState) -> Unit
) {
    val context = LocalContext.current
    ArchiveViewerRouteHost(
        workflow = workflow,
        readerConfig = readerConfig,
        markdownViewerSessionCache = markdownViewerSessionCache,
        bookNote = bookNote,
        recordRecentOpen = { type, key, title, uri, playlistId ->
            recordRecentOpen(type, key, title, uri, playlistId)
        },
        onSavePicZipImage = { sourceFile, fileName ->
            savePicZipImageToRoot(context, browserTab.rootUri, sourceFile, fileName)
        },
        onEncryptedExitRequest = onEncryptedExitRequest
    )
}

@Composable
internal fun EncryptedCacheExitConfirmLayer(
    state: EncryptedCacheExitDialogState?,
    onDismiss: () -> Unit
) {
    state?.let { dialog ->
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(dialog.title) },
            text = {
                Text(
                    stringResource(R.string.browser_delete_cache_exit_prompt),
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            confirmButton = {
                Button(onClick = {
                    dialog.onDelete()
                    onDismiss()
                }) { Text(stringResource(R.string.browser_delete_cache_and_exit)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    dialog.onKeep()
                    onDismiss()
                }) { Text(stringResource(R.string.browser_keep_cache_and_exit)) }
            }
        )
    }
}
