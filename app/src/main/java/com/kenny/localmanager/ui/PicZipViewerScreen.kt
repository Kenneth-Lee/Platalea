package com.kenny.localmanager.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import com.kenny.localmanager.file.extractPicZipImageRange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val BATCH_SIZE = 10
private const val SWIPE_THRESHOLD_DP = 50f
private const val MIN_SCALE = 0.5f
private const val MAX_SCALE = 5f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PicZipViewerScreen(
    contentDir: File,
    imagePaths: List<String>,
    zipFileName: String,
    isEncrypted: Boolean,
    password: CharArray?,
    initialIndex: Int = 0,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cacheDir = contentDir.parentFile!!
    val density = LocalDensity.current

    var currentIndex by remember { mutableStateOf(initialIndex.coerceIn(0, (imagePaths.size - 1).coerceAtLeast(0))) }
    var currentBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var loading by remember { mutableStateOf(true) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var isMaximized by remember { mutableStateOf(false) }
    var showDirectory by remember { mutableStateOf(false) }

    fun loadBitmapForIndex(index: Int) {
        if (index < 0 || index >= imagePaths.size) {
            currentBitmap = null
            return
        }
        val path = imagePaths[index]
        val file = File(contentDir, path)
        scope.launch {
            val bitmap = withContext(Dispatchers.IO) {
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
            withContext(Dispatchers.Main.immediate) {
                if (currentIndex == index) currentBitmap = bitmap
            }
        }
    }

    LaunchedEffect(currentIndex, imagePaths) {
        if (imagePaths.isEmpty()) {
            loading = false
            return@LaunchedEffect
        }
        currentBitmap = null
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

    DisposableEffect(currentIndex, imagePaths.size) {
        onDispose {
            if (imagePaths.isNotEmpty()) {
                scope.launch(Dispatchers.IO) {
                    val idx = currentIndex.coerceIn(0, imagePaths.size - 1)
                    try {
                        File(cacheDir, ".last_index").writeText(idx.toString())
                    } catch (_: Exception) {}
                }
            }
        }
    }

    LaunchedEffect(currentIndex) {
        if (imagePaths.isEmpty()) return@LaunchedEffect
        scope.launch(Dispatchers.IO) {
            val idx = currentIndex.coerceIn(0, imagePaths.size - 1)
            try {
                File(cacheDir, ".last_index").writeText(idx.toString())
            } catch (_: Exception) {}
        }
    }

    val transformableState = rememberTransformableState { zoomChange, offsetChange, _ ->
        scale = (scale * zoomChange).coerceIn(MIN_SCALE, MAX_SCALE)
        offset += offsetChange
        if (scale <= 1.1f) isMaximized = false
    }

    val swipeThresholdPx = with(density) { SWIPE_THRESHOLD_DP.dp.toPx() }
    val isZoomed = scale > 1.1f

    BackHandler(enabled = isZoomed) {
        scale = 1f
        offset = Offset.Zero
        isMaximized = false
    }

    Scaffold(
        topBar = if (isZoomed) { {} } else {
            {
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
                        IconButton(onClick = { showDirectory = true }) {
                            Icon(Icons.Filled.List, contentDescription = "目录")
                        }
                    }
                }
            )
            }
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .then(if (isZoomed) Modifier else Modifier.padding(padding))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (imagePaths.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("压缩包内无图片", style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                Box(
                    Modifier.fillMaxSize()
                ) {
                    currentBitmap?.let { bmp ->
                        // 图片层：transformable 先接收（双指缩放、单指平移），再双击；放大时无 overlay，手势直接到此处
                        Box(
                            Modifier
                                .fillMaxSize()
                                .graphicsLayer(
                                    scaleX = scale,
                                    scaleY = scale,
                                    translationX = offset.x,
                                    translationY = offset.y
                                )
                                .transformable(state = transformableState)
                                .pointerInput(Unit) {
                                    detectTapGestures(onDoubleTap = {
                                        isMaximized = !isMaximized
                                        if (isMaximized) {
                                            scale = 2.5f
                                            offset = Offset.Zero
                                        } else {
                                            scale = 1f
                                            offset = Offset.Zero
                                        }
                                    })
                                }
                        ) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        }
                        // 仅未放大时显示 overlay：水平滑动切图 + 双击放大（避免与 transformable 抢手势）
                        if (!isZoomed) {
                            val dragHolder = remember { object { var total = 0f } }
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .pointerInput(Unit) {
                                        detectTapGestures(onDoubleTap = {
                                            isMaximized = true
                                            scale = 2.5f
                                            offset = Offset.Zero
                                        })
                                    }
                                    .pointerInput(scale) {
                                        detectHorizontalDragGestures(
                                            onHorizontalDrag = { _, dragAmount ->
                                                dragHolder.total += dragAmount
                                            },
                                            onDragEnd = {
                                                val t = dragHolder.total
                                                dragHolder.total = 0f
                                                if (t > swipeThresholdPx && currentIndex > 0) {
                                                    currentIndex = currentIndex - 1
                                                    scale = 1f
                                                    offset = Offset.Zero
                                                } else if (t < -swipeThresholdPx && currentIndex < imagePaths.size - 1) {
                                                    currentIndex = currentIndex + 1
                                                    scale = 1f
                                                    offset = Offset.Zero
                                                }
                                            },
                                            onDragCancel = { dragHolder.total = 0f }
                                        )
                                    }
                            )
                        }
                    } ?: run {
                        if (loading) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                }
                if (!isZoomed) {
                    Row(
                        Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                if (currentIndex > 0) {
                                    currentIndex = currentIndex - 1
                                    scale = 1f
                                    offset = Offset.Zero
                                }
                            },
                            enabled = currentIndex > 0
                        ) {
                            Icon(Icons.Filled.SkipPrevious, contentDescription = "上一张")
                        }
                        Box(Modifier.weight(1f))
                        IconButton(
                            onClick = {
                                if (currentIndex < imagePaths.size - 1) {
                                    currentIndex = currentIndex + 1
                                    scale = 1f
                                    offset = Offset.Zero
                                }
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

    if (showDirectory) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showDirectory = false },
            sheetState = sheetState,
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 32.dp)
            ) {
                itemsIndexed(imagePaths) { index, path ->
                    val name = path.substringAfterLast('/')
                    val selected = index == currentIndex
                    Text(
                        text = "${index + 1}. $name",
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                currentIndex = index
                                scale = 1f
                                offset = Offset.Zero
                                showDirectory = false
                            }
                            .padding(vertical = 12.dp)
                    )
                }
            }
        }
    }
}
