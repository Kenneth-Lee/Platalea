package com.kenny.localmanager.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.kenny.localmanager.file.openInputStreamSafe
import com.kenny.localmanager.gpg.GpgHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

private const val MAX_PREVIEW_BYTES = 512 * 1024 // 512KB

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(
    fileUri: String,
    fileName: String,
    isEncrypted: Boolean,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var viewMode by remember { mutableStateOf(0) } // 0 = text, 1 = hex
    var bytesState by remember { mutableStateOf<ByteArray?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(fileUri, isEncrypted) {
        bytesState = null
        loadError = null
        withContext(Dispatchers.IO) {
            val uri = Uri.parse(fileUri)
            val cr = context.contentResolver
            cr.openInputStreamSafe(uri)?.use { raw ->
                val bytes = if (isEncrypted) {
                    GpgHelper.decryptStream(raw) ?: run {
                        loadError = "解密失败或需要密码"
                        return@withContext
                    }
                } else {
                    raw.readBytes(MAX_PREVIEW_BYTES)
                }
                bytesState = bytes
            } ?: run { loadError = "无法打开文件" }
        }
    }

    val content: Result<String> = remember(bytesState) {
        bytesState?.let { Result.success(it.decodeToString()) }
            ?: loadError?.let { Result.failure<String>(IllegalStateException(it)) }
            ?: Result.failure(IllegalStateException("加载中…"))
    }
    val hexLines = remember(bytesState) { bytesState?.toHexLines() ?: emptyList() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(fileName, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        SegmentedButton(
                            selected = viewMode == 0,
                            onClick = { viewMode = 0 },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                            icon = {}
                        ) { Text("文本") }
                        SegmentedButton(
                            selected = viewMode == 1,
                            onClick = { viewMode = 1 },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                            icon = {}
                        ) { Text("十六进制") }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (viewMode) {
                0 -> {
                    content.fold(
                        onSuccess = { text ->
                            SelectionContainer {
                                Column(
                                    Modifier
                                        .fillMaxSize()
                                        .verticalScroll(rememberScrollState())
                                        .padding(16.dp)
                                ) {
                                    Text(
                                        text,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    if (bytesState != null && bytesState!!.size >= MAX_PREVIEW_BYTES) {
                                        Spacer(Modifier.height(8.dp))
                                        Text(
                                            "（仅显示前 ${MAX_PREVIEW_BYTES / 1024} KB）",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        },
                        onFailure = { e ->
                            Text(
                                e.message ?: "无法加载",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    )
                }
                1 -> {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .horizontalScroll(rememberScrollState())
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp)
                    ) {
                        hexLines.forEach { line ->
                            Text(
                                line,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun ByteArray.toHexLines(): List<String> {
    return toList().chunked(16).map { chunk: List<Byte> ->
        val hex = chunk.joinToString(" ") { b -> "%02X".format(b.toInt() and 0xFF) }
        val ascii = chunk.map { b -> if (b.toInt() in 32..126) b.toInt().toChar() else '.' }.joinToString("")
        "%-48s  %s".format(hex, ascii)
    }
}

private fun java.io.InputStream.readBytes(maxLen: Int): ByteArray {
    val out = ByteArrayOutputStream()
    val buf = ByteArray(8192)
    var total = 0
    var n = 0
    while (total < maxLen && read(buf).also { n = it } != -1) {
        out.write(buf, 0, minOf(n, maxLen - total))
        total += n
    }
    return out.toByteArray()
}
