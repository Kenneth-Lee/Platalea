package com.kenny.localmanager.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kenny.localmanager.file.extractPicZipImageRange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val BATCH_SIZE = 10

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PicZipViewerScreen(
    contentDir: File,
    imagePaths: List<String>,
    zipFileName: String,
    isEncrypted: Boolean,
    password: CharArray?,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cacheDir = contentDir.parentFile!!
    var currentIndex by remember { mutableStateOf(0) }
    var currentBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var loading by remember { mutableStateOf(true) }

    fun loadBitmapForIndex(index: Int) {
        if (index < 0 || index >= imagePaths.size) {
            currentBitmap = null
            return
        }
        val path = imagePaths[index]
        val file = File(contentDir, path)
        scope.launch {
            currentBitmap = withContext(Dispatchers.IO) {
                if (!file.exists()) null
                else {
                    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(file.absolutePath, opts)
                    var sampleSize = 1
                    val maxPx = 2048
                    while (opts.outWidth / sampleSize > maxPx || opts.outHeight / sampleSize > maxPx) sampleSize *= 2
                    BitmapFactory.Options().apply { inSampleSize = sampleSize }.let { opt ->
                        BitmapFactory.decodeFile(file.absolutePath, opt)
                    }
                }
            }
        }
    }

    LaunchedEffect(currentIndex, imagePaths) {
        if (imagePaths.isEmpty()) {
            loading = false
            return@LaunchedEffect
        }
        loading = true
        val batchStart = (currentIndex / BATCH_SIZE) * BATCH_SIZE
        val batchEnd = minOf(imagePaths.size, batchStart + BATCH_SIZE)
        withContext(Dispatchers.IO) {
            extractPicZipImageRange(
                context,
                cacheDir,
                contentDir,
                imagePaths,
                batchStart,
                batchEnd,
                if (isEncrypted) password else null
            )
        }
        loading = false
        loadBitmapForIndex(currentIndex)
    }

    LaunchedEffect(currentIndex) {
        if (imagePaths.isEmpty()) return@LaunchedEffect
        val batchStart = (currentIndex / BATCH_SIZE) * BATCH_SIZE
        val nextBatchStart = batchStart + BATCH_SIZE
        val prevBatchStart = batchStart - BATCH_SIZE
        if (currentIndex >= batchStart + BATCH_SIZE - 2 && nextBatchStart < imagePaths.size) {
            scope.launch(Dispatchers.IO) {
                extractPicZipImageRange(
                    context,
                    cacheDir,
                    contentDir,
                    imagePaths,
                    nextBatchStart,
                    minOf(imagePaths.size, nextBatchStart + BATCH_SIZE),
                    if (isEncrypted) password else null
                )
            }
        }
        if (currentIndex <= batchStart + 1 && prevBatchStart >= 0) {
            scope.launch(Dispatchers.IO) {
                extractPicZipImageRange(
                    context,
                    cacheDir,
                    contentDir,
                    imagePaths,
                    prevBatchStart,
                    batchStart,
                    if (isEncrypted) password else null
                )
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        zipFileName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                actions = {
                    if (imagePaths.isNotEmpty()) {
                        Text(
                            "${currentIndex + 1} / ${imagePaths.size}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 8.dp).padding(vertical = 12.dp)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (imagePaths.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("压缩包内无图片", style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                currentBitmap?.let { bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                } ?: run {
                    if (loading) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                }
                Row(
                    Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            if (currentIndex > 0) currentIndex = currentIndex - 1
                        },
                        enabled = currentIndex > 0
                    ) {
                        Icon(Icons.Filled.SkipPrevious, contentDescription = "上一张")
                    }
                    Box(Modifier.weight(1f))
                    IconButton(
                        onClick = {
                            if (currentIndex < imagePaths.size - 1) currentIndex = currentIndex + 1
                        },
                        enabled = currentIndex < imagePaths.size - 1
                    ) {
                        Icon(Icons.Filled.SkipNext, contentDescription = "下一张")
                    }
                }
            }
        }
    }
}
