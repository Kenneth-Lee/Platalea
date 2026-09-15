package com.kenny.localmanager.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.FileProvider
import android.graphics.drawable.Icon
import android.graphics.pdf.PdfRenderer
import android.icu.text.Transliterator
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Base64
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.documentfile.provider.DocumentFile
import androidx.exifinterface.media.ExifInterface
import org.json.JSONObject
import android.widget.Toast
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.kenny.localmanager.R
import com.kenny.localmanager.SHORTCUT_TAB_PLAYER
import com.kenny.localmanager.SHORTCUT_TAB_QUICK_NOTE
import com.kenny.localmanager.data.DirectoryScrollMemory
import com.kenny.localmanager.data.AudioMetaAnnotation
import com.kenny.localmanager.data.Playlist
import com.kenny.localmanager.data.Preferences
import com.kenny.localmanager.data.RecentOpenItem
import com.kenny.localmanager.diagnostics.CrashReportStore
import com.kenny.localmanager.file.DocumentFileModel
import com.kenny.localmanager.file.copyDocumentTo
import com.kenny.localmanager.file.createFileWithBytes
import com.kenny.localmanager.file.deleteDocument
import com.kenny.localmanager.file.findChildByName
import com.kenny.localmanager.file.getDisplayName
import com.kenny.localmanager.file.emptyTrash
import com.kenny.localmanager.file.getTrashUriIfExists
import com.kenny.localmanager.file.quickToggleObfuscation
import com.kenny.localmanager.file.isQuickObfuscatedFileName
import com.kenny.localmanager.file.listChildrenFast
import com.kenny.localmanager.file.listFilesSafe
import com.kenny.localmanager.file.renameDocument
import com.kenny.localmanager.file.compressToZip
import com.kenny.localmanager.file.compressTo7z
import com.kenny.localmanager.file.ArchiveCompressionFormat
import com.kenny.localmanager.file.buildArchiveOutputName
import com.kenny.localmanager.file.compressSingleFileToFormat
import com.kenny.localmanager.file.compressTarArchive
import com.kenny.localmanager.file.unzipToParent
import com.kenny.localmanager.file.UnzipResult
import com.kenny.localmanager.file.getArchiveFirstLevelEntries
import com.kenny.localmanager.file.ZipFirstLevelResult
import com.kenny.localmanager.file.EpubParseResult
import com.kenny.localmanager.file.extractEpubToCache
import com.kenny.localmanager.file.loadEpubFromCache
import com.kenny.localmanager.file.getEpubCacheDir
import com.kenny.localmanager.file.CacheEntry
import com.kenny.localmanager.file.getCacheEntries
import com.kenny.localmanager.file.clearCacheEntry
import com.kenny.localmanager.file.formatSize
import com.kenny.localmanager.file.collectMusicFilesRecursively
import com.kenny.localmanager.file.RecursiveFileSearchCriteria
import com.kenny.localmanager.file.RecursiveFileSearchHit
import com.kenny.localmanager.file.searchFilesRecursively
import com.kenny.localmanager.player.PlaybackService
import com.kenny.localmanager.player.PlaybackState
import com.kenny.localmanager.player.TrackMetadata
import com.kenny.localmanager.player.ACTION_PLAY
import com.kenny.localmanager.player.ACTION_REMOVE_PLAYLIST_TRACK
import com.kenny.localmanager.player.ACTION_RELOAD_PLAYLIST
import com.kenny.localmanager.player.EXTRA_PLAYLIST_ID
import com.kenny.localmanager.player.EXTRA_START_INDEX
import com.kenny.localmanager.player.EXTRA_START_POSITION_MS
import com.kenny.localmanager.gpg.GpgHelper
import com.kenny.localmanager.gpg.findPublicKeyRing
import com.kenny.localmanager.gpg.generateDefaultKey
import com.kenny.localmanager.gpg.KeyInfo
import com.kenny.localmanager.gpg.listGpgKeyFiles
import com.kenny.localmanager.gpg.listPublicKeyInfos
import com.kenny.localmanager.gpg.listSecretKeyInfos
import com.kenny.localmanager.gpg.deleteAllPublicKeys
import com.kenny.localmanager.gpg.deletePublicKeyById
import com.kenny.localmanager.gpg.deleteSecretKeys
import com.kenny.localmanager.gpg.getAllPublicKeyRingsBytes
import com.kenny.localmanager.gpg.getSecretKeyRingBytes
import com.kenny.localmanager.gpg.getSinglePublicKeyRingBytes
import com.kenny.localmanager.gpg.mergePublicKeyRing
import com.kenny.localmanager.gpg.parsePublicKeyRingFromStream
import com.kenny.localmanager.gpg.parseSecretKeyRingFromStream
import com.kenny.localmanager.gpg.saveSecretKeyRing
import com.kenny.localmanager.gpg.getGpgKeyDir
import com.kenny.localmanager.gpg.loadPublicKeyRings
import com.kenny.localmanager.gpg.loadSecretKeyRings
import com.kenny.localmanager.gpg.SecretKeyPasswordCache
import com.kenny.localmanager.dict.isStarDictImportCandidate
import com.kenny.localmanager.requestPinnedTabShortcut
import com.kenny.localmanager.git.cloneToTree
import com.kenny.localmanager.git.commitAndPush
import com.kenny.localmanager.git.copyFileToShare
import com.mpatric.mp3agic.ID3v24Tag
import com.mpatric.mp3agic.Mp3File
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.collect
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.io.File
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

internal const val CONFIG_EXPORT_FILE_NAME = "local_manager_config.json"

internal enum class ConfigExportResult {
    SUCCESS,
    FILE_EXISTS,
    FAILED
}

private data class ExternalAppTarget(
    val packageName: String,
    val label: String
)

private enum class ExternalOpenTypeHint(val mimeType: String, val labelRes: Int) {
    VIDEO("video/*", R.string.browser_external_type_video),
    AUDIO("audio/*", R.string.browser_external_type_audio),
    TEXT("text/plain", R.string.browser_external_type_text),
    OTHER("*/*", R.string.browser_external_type_other)
}

private fun isExternalMimeLowConfidence(mimeType: String): Boolean {
    val normalized = mimeType.trim().lowercase(Locale.ROOT)
    return normalized.isBlank() || normalized == "*/*" || normalized == "application/octet-stream"
}

internal const val RECENT_TYPE_ZIP_VIEWER = "zip_viewer"
internal const val RECENT_TYPE_HTML_VIEWER = "html_viewer"
internal const val RECENT_TYPE_EPUB_RENDERER = "epub_renderer"
internal const val RECENT_TYPE_PDF_VIEWER = "pdf_viewer"
internal const val RECENT_TYPE_PLAYLIST = "playlist"
internal const val RECENT_TYPE_EXTERNAL_OPEN = "external_open"
internal typealias RunProgressBlock = suspend (
    label: String,
    total: Int?,
    block: suspend ((Int) -> Unit) -> Unit
) -> Unit

internal enum class MainTab(val key: String, val labelRes: Int) {
    DIRECTORY("directory", R.string.main_tab_directory),
    RECENT("recent", R.string.main_tab_recent),
    PLAYER("player", R.string.main_tab_player),
    FTP("ftp", R.string.main_tab_ftp),
    LOCAL_SHARE("local_share", R.string.main_tab_local_share),
    FAMILY_NETWORK("family_network", R.string.main_tab_family_network),
    CONFIG("config", R.string.main_tab_config),
    GIT_SHARE("git_share", R.string.main_tab_git_share),
    GIT_PROJECTS("git_projects", R.string.main_tab_git_projects),
    BOOK_NOTE("book_note", R.string.main_tab_book_note),
    QUICK_NOTE("quick_note", R.string.main_tab_quick_note),
    QUICK_CRYPTO("quick_crypto", R.string.main_tab_quick_crypto),
    DICTIONARY("dictionary", R.string.main_tab_dictionary),
    ABOUT("about", R.string.main_menu_about);

    companion object {
        fun fromKey(key: String?): MainTab {
            return values().firstOrNull { it.key == key } ?: DIRECTORY
        }

        fun fromLaunchTarget(target: String?): MainTab? {
            if (target.isNullOrBlank()) return null
            return values().firstOrNull { it.key == target }
        }
    }
}

private fun mainTabIcon(tab: MainTab): ImageVector {
    return when (tab) {
        MainTab.DIRECTORY -> Icons.Default.Folder
        MainTab.RECENT -> Icons.AutoMirrored.Filled.List
        MainTab.PLAYER -> Icons.AutoMirrored.Filled.QueueMusic
        MainTab.FTP -> Icons.Default.Wifi
        MainTab.LOCAL_SHARE -> Icons.Default.Devices
        MainTab.FAMILY_NETWORK -> Icons.Default.Home
        MainTab.CONFIG -> Icons.Default.Settings
        MainTab.GIT_SHARE -> Icons.Default.Share
        MainTab.GIT_PROJECTS -> Icons.Default.FolderOpen
        MainTab.BOOK_NOTE -> Icons.AutoMirrored.Filled.Article
        MainTab.QUICK_NOTE -> Icons.Default.Edit
        MainTab.QUICK_CRYPTO -> Icons.Default.Lock
        MainTab.DICTIONARY -> Icons.AutoMirrored.Filled.MenuBook
        MainTab.ABOUT -> Icons.Default.Info
    }
}

private fun buildRecentModel(uri: String, name: String): DocumentFileModel {
    return DocumentFileModel(
        name = name,
        isDirectory = false,
        uri = Uri.parse(uri),
        lastModified = 0L,
        size = 0L
    )
}




private fun openRecentItemByType(
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


private suspend fun runWithUiProgress(
    setProgressState: (OperationProgress?) -> Unit,
    label: String,
    total: Int? = null,
    block: suspend ((Int) -> Unit) -> Unit
) {
    setProgressState(OperationProgress(label, 0, total))
    delay(50)
    try {
        val setProgress: (Int) -> Unit = { i -> setProgressState(OperationProgress(label, i, total)) }
        block(setProgress)
    } finally {
        setProgressState(null)
    }
}

internal fun dedupeAudioList(audioList: List<DocumentFileModel>): List<DocumentFileModel> {
    val seen = LinkedHashSet<String>()
    return audioList.filter { seen.add(it.uri.toString()) }
}

private fun notifyPlaybackTrackRemoved(context: Context, playlistId: String, removedIndex: Int) {
    val intent = Intent(context, PlaybackService::class.java).apply {
        action = ACTION_REMOVE_PLAYLIST_TRACK
        putExtra(EXTRA_PLAYLIST_ID, playlistId)
        putExtra(EXTRA_START_INDEX, removedIndex)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
}

private fun notifyPlaybackPlaylistReloaded(
    context: Context,
    playlistId: String,
    startIndex: Int,
    startPositionMs: Int
) {
    val intent = Intent(context, PlaybackService::class.java).apply {
        action = ACTION_RELOAD_PLAYLIST
        putExtra(EXTRA_PLAYLIST_ID, playlistId)
        putExtra(EXTRA_START_INDEX, startIndex)
        putExtra(EXTRA_START_POSITION_MS, startPositionMs)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
}

internal suspend fun removePlaylistTrackAt(
    context: Context,
    prefs: Preferences,
    pl: Playlist,
    trackIndex: Int,
    playbackState: PlaybackState?,
    deleteSourceFile: Boolean,
    onStopPlayback: () -> Unit
) {
    if (trackIndex !in pl.uris.indices) return
    if (deleteSourceFile && pl.isDirectorySource) {
        val deleted = context.contentResolver.deleteDocument(Uri.parse(pl.uris[trackIndex]))
        if (!deleted) {
            Toast.makeText(context, context.getString(R.string.browser_delete_source_failed), Toast.LENGTH_SHORT).show()
            return
        }
    }
    val uris = pl.uris.toMutableList().apply { removeAt(trackIndex) }
    val names = pl.names.toMutableList().apply {
        if (trackIndex in indices) removeAt(trackIndex)
    }
    prefs.updatePlaylist(pl.copy(uris = uris, names = names))
    if (playbackState?.playlistId == pl.id) {
        if (uris.isEmpty()) {
            onStopPlayback()
        } else {
            notifyPlaybackTrackRemoved(context, pl.id, trackIndex)
        }
    }
}

internal fun syncPlaybackAfterPlaylistUpdate(
    context: Context,
    playlistId: String,
    previousUris: List<String>,
    previousTrackIndex: Int,
    previousPositionMs: Int,
    updated: Playlist,
    playbackState: PlaybackState?,
    onStopPlayback: () -> Unit
) {
    val state = playbackState ?: return
    if (state.playlistId != playlistId) return
    if (updated.uris.isEmpty()) {
        onStopPlayback()
        return
    }
    val currentUri = previousUris.getOrNull(previousTrackIndex)
    val newIndex = currentUri?.let { uri -> updated.uris.indexOf(uri) } ?: -1
    if (newIndex >= 0) {
        notifyPlaybackPlaylistReloaded(context, playlistId, newIndex, previousPositionMs)
    } else {
        notifyPlaybackPlaylistReloaded(context, playlistId, 0, 0)
    }
}

internal suspend fun createDirectoryPlaybackPlaylistAndStart(
    context: Context,
    prefs: Preferences,
    directoryUri: String,
    directoryName: String
): Playlist? {
    val audioList = withContext(Dispatchers.IO) {
        collectMusicFilesRecursively(context, directoryUri).map { it.model }
    }
    if (audioList.isEmpty()) return null
    return createNewPlaybackPlaylistAndStart(
        context = context,
        prefs = prefs,
        audioList = audioList,
        playlistName = directoryName.ifBlank {
            context.getString(
                R.string.player_playlist_default_name,
                java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
            )
        },
        sourceType = Playlist.SOURCE_TYPE_DIRECTORY,
        sourceUri = directoryUri
    )
}

@Composable
private fun ScrollableMainTabBar(
    activeMainTab: MainTab,
    onSwitchMainTab: (MainTab) -> Unit
) {
    val context = LocalContext.current
    fun trySwitchTab(tab: MainTab) {
        onSwitchMainTab(tab)
    }
    val fixedTabs = listOf(MainTab.DIRECTORY, MainTab.RECENT)
    val candidateTabs = listOf(
        MainTab.CONFIG,
        MainTab.GIT_PROJECTS,
        MainTab.BOOK_NOTE,
        MainTab.QUICK_NOTE,
        MainTab.QUICK_CRYPTO,
        MainTab.PLAYER,
        MainTab.DICTIONARY,
        MainTab.FTP,
        MainTab.LOCAL_SHARE,
        MainTab.FAMILY_NETWORK,
        MainTab.GIT_SHARE,
        MainTab.ABOUT
    )
    var candidateUsageOrder by remember { mutableStateOf(candidateTabs) }
    var overflowExpanded by remember { mutableStateOf(false) }
    var pendingOverflowPromotion by remember { mutableStateOf<MainTab?>(null) }
    var previousActiveTab by remember { mutableStateOf<MainTab?>(null) }

    fun moveCandidateToFront(tab: MainTab) {
        if (tab !in candidateTabs) return
        candidateUsageOrder = listOf(tab) + candidateUsageOrder.filter { it != tab }
    }

    LaunchedEffect(activeMainTab) {
        if (previousActiveTab == activeMainTab) return@LaunchedEffect
        val pending = pendingOverflowPromotion
        if (pending != null && activeMainTab != pending) {
            moveCandidateToFront(pending)
            pendingOverflowPromotion = null
        }
        if (activeMainTab in candidateTabs) {
            moveCandidateToFront(activeMainTab)
        }
        previousActiveTab = activeMainTab
    }

    val dynamicTabs = candidateUsageOrder.take(2)
    val overflowTabs = candidateTabs.filter { it !in dynamicTabs }

    Surface(tonalElevation = 2.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            fixedTabs.forEach { tab ->
                val selected = activeMainTab == tab
                val label = context.getString(tab.labelRes)
                OutlinedButton(
                    onClick = { trySwitchTab(tab) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(4.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                        contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(mainTabIcon(tab), contentDescription = label, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.height(2.dp))
                        Text(
                            label,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            dynamicTabs.forEach { tab ->
                val selected = activeMainTab == tab
                val label = context.getString(tab.labelRes)
                OutlinedButton(
                    onClick = { trySwitchTab(tab) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(4.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                        contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(mainTabIcon(tab), contentDescription = label, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.height(2.dp))
                        Text(
                            label,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                val overflowSelected = activeMainTab in overflowTabs
                OutlinedButton(
                    onClick = { overflowExpanded = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(4.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (overflowSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                        contentColor = if (overflowSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.MoreVert, contentDescription = context.getString(R.string.main_tab_more), modifier = Modifier.size(16.dp))
                        Spacer(Modifier.height(2.dp))
                        Text(
                            context.getString(R.string.main_tab_more),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                DropdownMenu(
                    expanded = overflowExpanded,
                    onDismissRequest = { overflowExpanded = false }
                ) {
                    overflowTabs.forEach { tab ->
                        val label = context.getString(tab.labelRes)
                        DropdownMenuItem(
                            text = { Text(label) },
                            leadingIcon = {
                                Icon(
                                    mainTabIcon(tab),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            onClick = {
                                overflowExpanded = false
                                pendingOverflowPromotion = tab
                                trySwitchTab(tab)
                            }
                        )
                    }
                }
            }
        }
    }
}


@Composable
private fun fileItemSubtitle(model: DocumentFileModel): String {
    val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(model.lastModified))
    return if (model.displaySize.isNotEmpty()) "${model.displaySize}  $dateStr" else dateStr
}

internal fun formatPlaybackTime(ms: Int): String {
    val totalSeconds = (ms.coerceAtLeast(0) / 1000)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}

internal fun formatTrackMetadata(context: Context, metadata: TrackMetadata): String {
    val parts = buildList {
        metadata.artist?.takeIf { it.isNotBlank() }?.let { add(context.getString(R.string.browser_metadata_artist, it)) }
        metadata.album?.takeIf { it.isNotBlank() }?.let { add(context.getString(R.string.browser_metadata_album, it)) }
        metadata.albumArtist
            ?.takeIf { it.isNotBlank() && it != metadata.artist }
            ?.let { add(context.getString(R.string.browser_metadata_album_artist, it)) }
        metadata.year?.takeIf { it.isNotBlank() }?.let { add(it) }
        metadata.genre?.takeIf { it.isNotBlank() }?.let { add(it) }
    }
    return parts.joinToString(" · ")
}

internal enum class PlayerTrackFilterField {
    FILE_NAME,
    TITLE,
    ALBUM,
    ARTIST
}

internal data class PlayerTrackEntry(
    val originalIndex: Int,
    val uri: String,
    val name: String,
    val metadata: TrackMetadata?
)

internal data class DuplicateSongGroup(
    val key: String,
    val displayTitle: String,
    val entries: List<PlayerTrackEntry>
)

private val duplicateTraditionalToSimplified: Transliterator? = runCatching {
    Transliterator.getInstance("Traditional-Simplified")
}.getOrNull()

private val duplicateLeadingNumberPattern = Regex("""^\s*(?:第\s*)?(?:\d+|[一二三四五六七八九十百千零〇两]+)[\s._-]*""")
private val duplicateBracketPattern = Regex("""[\[(（【][^\]）】]*?[\])）】]""")
private val duplicateWhitespacePattern = Regex("""\s+""")

private fun normalizeDuplicateSongTitle(raw: String, splitOnDash: Boolean): String {
    var text = raw.trim()
    if (text.isEmpty()) return ""
    if (splitOnDash) {
        val parts = text.split(Regex("""\s*[-—–_]+\s*""")).filter { it.isNotBlank() }
        if (parts.size >= 2) {
            text = parts.last()
        }
    }
    text = duplicateLeadingNumberPattern.replace(text, "")
    text = duplicateBracketPattern.replace(text, " ")
    text = duplicateTraditionalToSimplified?.transliterate(text) ?: text
    text = Normalizer.normalize(text, Normalizer.Form.NFKC)
    text = text.replace(Regex("""[\u200B-\u200D\uFEFF]"""), "")
    text = text.lowercase(Locale.ROOT)
    return duplicateWhitespacePattern.replace(text, " ").trim()
}

private fun duplicateSongKeyFromTitle(title: String?): String {
    return title?.takeIf { it.isNotBlank() }?.let { normalizeDuplicateSongTitle(it, splitOnDash = false) }.orEmpty()
}

internal fun duplicateSongKeyFromFileName(name: String): String {
    val baseName = name.substringBeforeLast('.')
    return normalizeDuplicateSongTitle(baseName, splitOnDash = true)
}

internal suspend fun findDuplicateSongGroups(
    context: Context,
    playlist: Playlist,
    metadataCache: Map<String, TrackMetadata>
): List<DuplicateSongGroup> = withContext(Dispatchers.IO) {
    val cache = metadataCache.toMutableMap()
    val grouped = linkedMapOf<String, MutableList<PlayerTrackEntry>>()
    playlist.uris.forEachIndexed { index, uri ->
        val name = playlist.names.getOrElse(index) { uri.substringAfterLast('/') }
        val metadata = cache[uri] ?: readTrackMetadata(context, uri).also { cache[uri] = it }
        val titleKey = duplicateSongKeyFromTitle(metadata.title)
        val key = if (titleKey.isNotBlank()) titleKey else duplicateSongKeyFromFileName(name)
        if (key.isNotBlank()) {
            grouped.getOrPut(key) { mutableListOf() }.add(
                PlayerTrackEntry(
                    originalIndex = index,
                    uri = uri,
                    name = name,
                    metadata = metadata
                )
            )
        }
    }
    grouped.mapNotNull { (key, entries) ->
        if (entries.size < 2) return@mapNotNull null
        val sorted = entries.sortedBy { it.originalIndex }
        DuplicateSongGroup(
            key = key,
            displayTitle = sorted.first().metadata?.title?.takeIf { it.isNotBlank() }
                ?: sorted.first().name.substringBeforeLast('.').ifBlank { sorted.first().name },
            entries = sorted
        )
    }.sortedWith(
        compareByDescending<DuplicateSongGroup> { it.entries.size }
            .thenBy { it.displayTitle.lowercase(Locale.ROOT) }
    )
}

internal fun readTrackMetadata(context: Context, uriString: String): TrackMetadata {
    return runCatching {
        val uri = Uri.parse(uriString)
        MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(context, uri)
            TrackMetadata(
                title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.trim()?.takeIf { it.isNotEmpty() },
                artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.trim()?.takeIf { it.isNotEmpty() },
                album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.trim()?.takeIf { it.isNotEmpty() },
                albumArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)?.trim()?.takeIf { it.isNotEmpty() },
                genre = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)?.trim()?.takeIf { it.isNotEmpty() },
                year = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)?.trim()?.takeIf { it.isNotEmpty() }
            )
        }
    }.getOrElse { TrackMetadata() }
}

internal enum class DetailMetadataKind {
    AUDIO,
    VIDEO,
    IMAGE,
    PDF,
    EPUB,
    ARCHIVE,
    UNKNOWN
}

private data class DetailMetadataRow(
    val label: String,
    val value: String,
    val isSection: Boolean = false
)

internal data class EditableImageExif(
    val takenTime: String = "",
    val artist: String = "",
    val description: String = "",
    val software: String = "",
    val copyright: String = ""
)

internal data class EditableAudioAnnotation(
    val title: String = "",
    val artist: String = "",
    val album: String = ""
)

private const val EXIF_TAG_XP_AUTHOR = "XPAuthor"
private const val EXIF_TAG_XP_COMMENT = "XPComment"
private const val EXIF_TAG_XP_SOFTWARE = "XPSoftware"
private const val EXIF_TAG_XP_COPYRIGHT = "XPCopyright"
private const val LM_EXIF_USER_COMMENT_PREFIX = "LM_EXIF_B64_V1:"
private const val LM_EXIF_SOFTWARE_MARKER_PREFIX = " [LMEXIF:"
private const val LM_EXIF_SOFTWARE_MARKER_SUFFIX = "]"

private data class LmExifPayload(
    val artist: String,
    val description: String,
    val software: String,
    val copyright: String
)

internal data class EditableImageExifValidation(
    val takenTimeError: String? = null,
    val artistError: String? = null,
    val descriptionError: String? = null,
    val softwareError: String? = null,
    val copyrightError: String? = null
) {
    val hasError: Boolean
        get() = !takenTimeError.isNullOrBlank() ||
            !artistError.isNullOrBlank() ||
            !descriptionError.isNullOrBlank() ||
            !softwareError.isNullOrBlank() ||
            !copyrightError.isNullOrBlank()
}

internal data class EditableAudioAnnotationValidation(
    val titleError: String? = null,
    val artistError: String? = null,
    val albumError: String? = null
) {
    val hasError: Boolean
        get() = !titleError.isNullOrBlank() || !artistError.isNullOrBlank() || !albumError.isNullOrBlank()
}

private const val EDITABLE_EXIF_TEXT_MAX_LENGTH = 512

internal fun inferDetailMetadataKind(
    fileName: String,
    mimeType: String,
    isDirectory: Boolean
): DetailMetadataKind {
    if (isDirectory) return DetailMetadataKind.UNKNOWN
    val normalizedMime = mimeType.lowercase(Locale.ROOT)
    val ext = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
    if (normalizedMime.startsWith("audio/")) return DetailMetadataKind.AUDIO
    if (normalizedMime.startsWith("video/")) return DetailMetadataKind.VIDEO
    if (normalizedMime.startsWith("image/")) return DetailMetadataKind.IMAGE
    if (normalizedMime == "application/pdf" || ext == "pdf") return DetailMetadataKind.PDF
    if (normalizedMime == "application/epub+zip" || ext == "epub") return DetailMetadataKind.EPUB
    if (isExtractableArchive(fileName)) return DetailMetadataKind.ARCHIVE
    return when (ext) {
        "mp3", "m4a", "flac", "ogg", "wav", "aac" -> DetailMetadataKind.AUDIO
        "mp4", "mkv", "webm", "mov", "avi" -> DetailMetadataKind.VIDEO
        "jpg", "jpeg", "png", "webp", "heic", "bmp", "gif" -> DetailMetadataKind.IMAGE
        else -> DetailMetadataKind.UNKNOWN
    }
}

private fun appendMetaIfNotBlank(
    rows: MutableList<DetailMetadataRow>,
    label: String,
    raw: String?
) {
    val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return
    rows += DetailMetadataRow(label, value)
}

private fun appendMetaSection(
    rows: MutableList<DetailMetadataRow>,
    label: String
) {
    rows += DetailMetadataRow(label = label, value = "", isSection = true)
}

private fun formatBitrate(raw: String?): String? {
    val bps = raw?.trim()?.toLongOrNull() ?: return raw?.trim()?.takeIf { it.isNotEmpty() }
    if (bps <= 0) return null
    return "${(bps / 1000.0).toInt()} kbps"
}

private fun formatSampleRate(raw: String?): String? {
    val hz = raw?.trim()?.toLongOrNull() ?: return raw?.trim()?.takeIf { it.isNotEmpty() }
    if (hz <= 0) return null
    return "${hz} Hz"
}

internal fun formatDurationMs(raw: String?): String? {
    val ms = raw?.trim()?.toLongOrNull() ?: return raw?.trim()?.takeIf { it.isNotEmpty() }
    if (ms < 0) return null
    return formatPlaybackTime(ms.toInt())
}

private fun formatDurationUs(rawUs: Long?): String? {
    val us = rawUs ?: return null
    if (us < 0) return null
    val ms = (us / 1000L).coerceAtMost(Int.MAX_VALUE.toLong())
    return formatPlaybackTime(ms.toInt())
}

private fun formatVideoResolution(widthRaw: String?, heightRaw: String?): String? {
    val width = widthRaw?.trim()?.toIntOrNull()
    val height = heightRaw?.trim()?.toIntOrNull()
    if (width == null || height == null || width <= 0 || height <= 0) return null
    return "${width} x ${height}"
}

private fun formatExifExposureTime(raw: String?): String? {
    val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val seconds = value.toDoubleOrNull()
    if (seconds != null && seconds > 0) {
        return if (seconds < 1.0) {
            val denominator = (1.0 / seconds).toInt().coerceAtLeast(1)
            "1/${denominator} s"
        } else {
            "${"%.3f".format(Locale.getDefault(), seconds)} s"
        }
    }
    return value
}

private fun formatExifFocalLength(raw: String?): String? {
    val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val mm = value.toDoubleOrNull()
    return if (mm != null && mm > 0) {
        "${"%.1f".format(Locale.getDefault(), mm)} mm"
    } else {
        value
    }
}

private fun formatExifAperture(raw: String?): String? {
    val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val fNumber = value.toDoubleOrNull()
    return if (fNumber != null && fNumber > 0) {
        "f/${"%.1f".format(Locale.getDefault(), fNumber)}"
    } else {
        value
    }
}

private fun formatExifGps(exif: ExifInterface): String? {
    var lat = exif.getAttributeDouble(ExifInterface.TAG_GPS_LATITUDE, Double.NaN)
    var lon = exif.getAttributeDouble(ExifInterface.TAG_GPS_LONGITUDE, Double.NaN)
    if (lat.isNaN() || lon.isNaN()) return null
    val latRef = exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE_REF)?.trim()?.uppercase(Locale.ROOT)
    val lonRef = exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF)?.trim()?.uppercase(Locale.ROOT)
    if (latRef == "S") lat = -kotlin.math.abs(lat)
    if (lonRef == "W") lon = -kotlin.math.abs(lon)
    return "${"%.6f".format(Locale.getDefault(), lat)}, ${"%.6f".format(Locale.getDefault(), lon)}"
}

private fun formatExifOrientation(raw: String?): String? {
    val orientation = raw?.trim()?.toIntOrNull() ?: return raw?.trim()?.takeIf { it.isNotEmpty() }
    return when (orientation) {
        ExifInterface.ORIENTATION_NORMAL -> "0°"
        ExifInterface.ORIENTATION_ROTATE_90 -> "90°"
        ExifInterface.ORIENTATION_ROTATE_180 -> "180°"
        ExifInterface.ORIENTATION_ROTATE_270 -> "270°"
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> "flip-horizontal"
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> "flip-vertical"
        ExifInterface.ORIENTATION_TRANSPOSE -> "transpose"
        ExifInterface.ORIENTATION_TRANSVERSE -> "transverse"
        else -> orientation.toString()
    }
}

private fun formatExifWhiteBalance(raw: String?): String? {
    return when (raw?.trim()) {
        "0" -> "auto"
        "1" -> "manual"
        else -> raw?.trim()?.takeIf { it.isNotEmpty() }
    }
}

private fun formatExifFlash(raw: String?): String? {
    val value = raw?.trim()?.toIntOrNull() ?: return raw?.trim()?.takeIf { it.isNotEmpty() }
    return if ((value and 0x1) == 1) "fired" else "not-fired"
}

private fun formatExifMeteringMode(raw: String?): String? {
    return when (raw?.trim()) {
        "0" -> "unknown"
        "1" -> "average"
        "2" -> "center-weighted"
        "3" -> "spot"
        "4" -> "multi-spot"
        "5" -> "pattern"
        "6" -> "partial"
        "255" -> "other"
        else -> raw?.trim()?.takeIf { it.isNotEmpty() }
    }
}

private fun formatExifExposureCompensation(raw: String?): String? {
    val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val ev = value.toDoubleOrNull()
    return if (ev != null) {
        "${"%.2f".format(Locale.getDefault(), ev)} EV"
    } else {
        value
    }
}

private fun formatExifColorSpace(raw: String?): String? {
    return when (raw?.trim()) {
        "1" -> "sRGB"
        "65535" -> "uncalibrated"
        else -> raw?.trim()?.takeIf { it.isNotEmpty() }
    }
}

private fun formatExifAltitude(exif: ExifInterface): String? {
    val raw = exif.getAttributeDouble(ExifInterface.TAG_GPS_ALTITUDE, Double.NaN)
    if (raw.isNaN()) return null
    var altitude = raw
    val ref = exif.getAttributeInt(ExifInterface.TAG_GPS_ALTITUDE_REF, 0)
    if (ref == 1) altitude = -kotlin.math.abs(altitude)
    return "${"%.1f".format(Locale.getDefault(), altitude)} m"
}

private fun normalizeExifDateTime(value: String): String {
    val trimmed = value.trim()
    if (trimmed.isEmpty()) return ""
    // 允许用户输入 YYYY-MM-DD HH:MM:SS，保存前转成 EXIF 常用的 YYYY:MM:DD HH:MM:SS
    val datePart = trimmed.take(10)
    val normalizedDatePart = if (datePart.length == 10 && datePart[4] == '-' && datePart[7] == '-') {
        datePart.replace('-', ':')
    } else {
        datePart
    }
    return if (trimmed.length > 10) {
        normalizedDatePart + trimmed.substring(10)
    } else {
        normalizedDatePart
    }
}

private fun isStrictValidExifDateTime(value: String): Boolean {
    val normalized = normalizeExifDateTime(value)
    if (normalized.isBlank()) return true
    val pattern = Regex("^\\d{4}:\\d{2}:\\d{2} \\d{2}:\\d{2}:\\d{2}$")
    if (!pattern.matches(normalized)) return false
    val parser = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).apply { isLenient = false }
    return runCatching { parser.parse(normalized) != null }.getOrElse { false }
}

internal fun validateEditableImageExifForm(context: Context, data: EditableImageExif): EditableImageExifValidation {
    val takenTimeError = if (!isStrictValidExifDateTime(data.takenTime)) {
        context.getString(R.string.browser_meta_taken_time_invalid)
    } else {
        null
    }

    fun lengthError(labelRes: Int, value: String): String? {
        val trimmed = value.trim()
        if (trimmed.length <= EDITABLE_EXIF_TEXT_MAX_LENGTH) return null
        return context.getString(
            R.string.browser_meta_field_too_long,
            context.getString(labelRes),
            EDITABLE_EXIF_TEXT_MAX_LENGTH
        )
    }

    return EditableImageExifValidation(
        takenTimeError = takenTimeError,
        artistError = lengthError(R.string.browser_meta_artist_label, data.artist),
        descriptionError = lengthError(R.string.browser_meta_description_label, data.description),
        softwareError = lengthError(R.string.browser_meta_software_label, data.software),
        copyrightError = lengthError(R.string.browser_meta_copyright_label, data.copyright)
    )
}

internal fun validateEditableAudioAnnotationForm(context: Context, data: EditableAudioAnnotation): EditableAudioAnnotationValidation {
    fun lengthError(labelRes: Int, value: String): String? {
        val trimmed = value.trim()
        if (trimmed.length <= EDITABLE_EXIF_TEXT_MAX_LENGTH) return null
        return context.getString(
            R.string.browser_meta_field_too_long,
            context.getString(labelRes),
            EDITABLE_EXIF_TEXT_MAX_LENGTH
        )
    }
    return EditableAudioAnnotationValidation(
        titleError = lengthError(R.string.browser_meta_title_label, data.title),
        artistError = lengthError(R.string.browser_meta_artist_label, data.artist),
        albumError = lengthError(R.string.browser_meta_album_label, data.album)
    )
}

internal fun compareChangedImageExifFields(before: EditableImageExif, expected: EditableImageExif, actual: EditableImageExif): List<String> {
    val changed = mutableListOf<String>()
    val checks = listOf(
        "takenTime" to { before.takenTime.trim() != expected.takenTime.trim() },
        "artist" to { before.artist.trim() != expected.artist.trim() },
        "description" to { before.description.trim() != expected.description.trim() },
        "software" to { before.software.trim() != expected.software.trim() },
        "copyright" to { before.copyright.trim() != expected.copyright.trim() }
    )
    checks.forEach { (field, changedField) ->
        if (changedField()) changed += field
    }
    return changed
}

internal fun compareChangedAudioAnnotationFields(before: EditableAudioAnnotation, expected: EditableAudioAnnotation, actual: EditableAudioAnnotation): List<String> {
    val changed = mutableListOf<String>()
    val checks = listOf(
        "title" to { before.title.trim() != expected.title.trim() },
        "artist" to { before.artist.trim() != expected.artist.trim() },
        "album" to { before.album.trim() != expected.album.trim() }
    )
    checks.forEach { (field, changedField) ->
        if (changedField()) changed += field
    }
    return changed
}

internal fun mergeEditableImageExifForBatch(current: EditableImageExif, patch: EditableImageExif): EditableImageExif {
    return current.copy(
        takenTime = patch.takenTime.trim().ifBlank { current.takenTime },
        artist = patch.artist.trim().ifBlank { current.artist },
        description = patch.description.trim().ifBlank { current.description },
        software = patch.software.trim().ifBlank { current.software },
        copyright = patch.copyright.trim().ifBlank { current.copyright }
    )
}

internal fun mergeEditableAudioAnnotationForBatch(current: EditableAudioAnnotation, patch: EditableAudioAnnotation): EditableAudioAnnotation {
    return current.copy(
        title = patch.title.trim().ifBlank { current.title },
        artist = patch.artist.trim().ifBlank { current.artist },
        album = patch.album.trim().ifBlank { current.album }
    )
}


internal fun readEditableAudioAnnotation(context: Context, uri: Uri): EditableAudioAnnotation {
    return runCatching {
        MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(context, uri)
            EditableAudioAnnotation(
                title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.trim().orEmpty(),
                artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.trim().orEmpty(),
                album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.trim().orEmpty()
            )
        }
    }.getOrElse { EditableAudioAnnotation() }
}

internal fun isMp3AudioFile(fileName: String, mimeType: String): Boolean {
    val ext = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
    val mt = mimeType.trim().lowercase(Locale.ROOT)
    return ext == "mp3" || mt == "audio/mpeg" || mt == "audio/mp3"
}

internal fun supportsJaudiotaggerWrite(fileName: String, mimeType: String): Boolean {
    val ext = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
    val mt = mimeType.trim().lowercase(Locale.ROOT)
    return when {
        ext in setOf("m4a", "mp4", "flac", "ogg") -> true
        mt == "audio/mp4" || mt == "audio/x-m4a" || mt == "audio/flac" || mt == "audio/ogg" || mt == "application/ogg" -> true
        else -> false
    }
}

internal fun saveEditableAudioAnnotationToMp3(
    context: Context,
    uri: Uri,
    data: EditableAudioAnnotation
): String? {
    return try {
        val tempIn = File.createTempFile("audio_meta_in_", ".mp3", context.cacheDir)
        val tempOut = File.createTempFile("audio_meta_out_", ".mp3", context.cacheDir)
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempIn.outputStream().use { output -> input.copyTo(output) }
            } ?: return "openInputStream returned null"

            val before = readEditableAudioAnnotation(context, uri)
            val mp3 = Mp3File(tempIn.absolutePath)
            val tag = mp3.id3v2Tag ?: ID3v24Tag()
            tag.title = data.title.trim()
            tag.artist = data.artist.trim()
            tag.album = data.album.trim()
            mp3.id3v2Tag = tag
            mp3.save(tempOut.absolutePath)

            val writeDone = runCatching {
                context.contentResolver.openOutputStream(uri, "rwt")?.use { out ->
                    tempOut.inputStream().use { input -> input.copyTo(out) }
                }
            }.getOrNull() != null

            if (!writeDone) {
                context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                    tempOut.inputStream().use { input -> input.copyTo(out) }
                } ?: return "openOutputStream returned null (rwt/wt)"
            }

            val readBack = readEditableAudioAnnotation(context, uri)
            val changedFields = compareChangedAudioAnnotationFields(before, data, readBack)
            val mismatch = changedFields.filter { field ->
                when (field) {
                    "title" -> readBack.title.trim() != data.title.trim()
                    "artist" -> readBack.artist.trim() != data.artist.trim()
                    "album" -> readBack.album.trim() != data.album.trim()
                    else -> false
                }
            }
            if (mismatch.isNotEmpty()) {
                return "post-save verification mismatch: ${mismatch.joinToString(",")}; readBack=$readBack"
            }
            null
        } finally {
            runCatching { tempIn.delete() }
            runCatching { tempOut.delete() }
        }
    } catch (e: Exception) {
        "${e.javaClass.simpleName}: ${e.message ?: "unknown error"}"
    }
}

internal fun saveEditableAudioAnnotationWithJaudiotagger(
    context: Context,
    uri: Uri,
    data: EditableAudioAnnotation,
    fileName: String
): String? {
    return try {
        val ext = fileName.substringAfterLast('.', "").ifBlank { "audio" }
        val tempIn = File.createTempFile("audio_meta_in_", ".${ext}", context.cacheDir)
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempIn.outputStream().use { output -> input.copyTo(output) }
            } ?: return "openInputStream returned null"

            val before = readEditableAudioAnnotation(context, uri)
            val audioFile = AudioFileIO.read(tempIn)
            val tag = audioFile.tagOrCreateAndSetDefault

            fun setOrClear(field: FieldKey, value: String) {
                val normalized = value.trim()
                if (normalized.isBlank()) {
                    tag.deleteField(field)
                } else {
                    tag.setField(field, normalized)
                }
            }

            setOrClear(FieldKey.TITLE, data.title)
            setOrClear(FieldKey.ARTIST, data.artist)
            setOrClear(FieldKey.ALBUM, data.album)
            audioFile.commit()

            val writeDone = runCatching {
                context.contentResolver.openOutputStream(uri, "rwt")?.use { out ->
                    tempIn.inputStream().use { input -> input.copyTo(out) }
                }
            }.getOrNull() != null

            if (!writeDone) {
                context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                    tempIn.inputStream().use { input -> input.copyTo(out) }
                } ?: return "openOutputStream returned null (rwt/wt)"
            }

            val readBack = readEditableAudioAnnotation(context, uri)
            val changedFields = compareChangedAudioAnnotationFields(before, data, readBack)
            val mismatch = changedFields.filter { field ->
                when (field) {
                    "title" -> readBack.title.trim() != data.title.trim()
                    "artist" -> readBack.artist.trim() != data.artist.trim()
                    "album" -> readBack.album.trim() != data.album.trim()
                    else -> false
                }
            }
            if (mismatch.isNotEmpty()) {
                return "post-save verification mismatch: ${mismatch.joinToString(",")}; readBack=$readBack"
            }

            null
        } finally {
            runCatching { tempIn.delete() }
        }
    } catch (e: Exception) {
        "${e.javaClass.simpleName}: ${e.message ?: "unknown error"}"
    }
}

private fun applyAudioMetaAnnotationRows(
    context: Context,
    rows: List<DetailMetadataRow>,
    annotation: AudioMetaAnnotation?
): List<DetailMetadataRow> {
    if (annotation == null) return rows
    val title = annotation.title.trim()
    val artist = annotation.artist.trim()
    val album = annotation.album.trim()
    if (title.isBlank() && artist.isBlank() && album.isBlank()) return rows

    val titleLabel = context.getString(R.string.browser_meta_title_label)
    val artistLabel = context.getString(R.string.browser_meta_artist_label)
    val albumLabel = context.getString(R.string.browser_meta_album_label)
    val updated = rows.map { row ->
        if (row.isSection) return@map row
        when (row.label) {
            titleLabel -> if (title.isNotBlank()) row.copy(value = title) else row
            artistLabel -> if (artist.isNotBlank()) row.copy(value = artist) else row
            albumLabel -> if (album.isNotBlank()) row.copy(value = album) else row
            else -> row
        }
    }
    val result = updated.toMutableList()
    val hasTitle = updated.any { !it.isSection && it.label == titleLabel }
    val hasArtist = updated.any { !it.isSection && it.label == artistLabel }
    val hasAlbum = updated.any { !it.isSection && it.label == albumLabel }
    if (title.isNotBlank() && !hasTitle) result += DetailMetadataRow(titleLabel, title)
    if (artist.isNotBlank() && !hasArtist) result += DetailMetadataRow(artistLabel, artist)
    if (album.isNotBlank() && !hasAlbum) result += DetailMetadataRow(albumLabel, album)
    return result
}

private fun decodeXpExifText(exif: ExifInterface, tag: String): String? {
    fun decodeUtf16Le(raw: ByteArray): String? {
        if (raw.isEmpty()) return null
        var end = raw.size
        while (end >= 2 && raw[end - 1] == 0.toByte() && raw[end - 2] == 0.toByte()) {
            end -= 2
        }
        if (end <= 0) return null
        return runCatching {
            String(raw, 0, end, Charsets.UTF_16LE).trim().ifEmpty { null }
        }.getOrNull()
    }

    exif.getAttributeBytes(tag)?.let { bytes ->
        decodeUtf16Le(bytes)?.let { return it }
    }

    val rawAttr = exif.getAttribute(tag)?.trim().orEmpty()
    if (rawAttr.isBlank()) return null
    val parsedBytes = runCatching {
        rawAttr.split(',')
            .mapNotNull { token -> token.trim().takeIf { it.isNotEmpty() }?.toIntOrNull() }
            .map { (it and 0xFF).toByte() }
            .toByteArray()
    }.getOrNull()
    if (parsedBytes != null && parsedBytes.isNotEmpty()) {
        decodeUtf16Le(parsedBytes)?.let { return it }
    }
    return rawAttr.ifEmpty { null }
}

private fun encodeXpExifText(value: String): String {
    val bytes = (value + "\u0000").toByteArray(Charsets.UTF_16LE)
    return bytes.joinToString(separator = ",") { (it.toInt() and 0xFF).toString() }
}

private fun readExifTextWithUnicodeFallback(
    exif: ExifInterface,
    standardTag: String,
    xpTag: String? = null
): String {
    val standard = exif.getAttribute(standardTag)?.trim().orEmpty()
    val xp = xpTag?.let { decodeXpExifText(exif, it) }.orEmpty()
    return when {
        xp.isNotBlank() -> xp
        standard.isNotBlank() -> standard
        else -> ""
    }
}

private fun writeExifTextWithUnicodeSupport(
    exif: ExifInterface,
    standardTag: String,
    value: String,
    xpTag: String? = null
) {
    val normalized = value.trim()
    if (normalized.isBlank()) {
        exif.setAttribute(standardTag, null)
        xpTag?.let { exif.setAttribute(it, null) }
        return
    }
    exif.setAttribute(standardTag, normalized)
    xpTag?.let { exif.setAttribute(it, encodeXpExifText(normalized)) }
}

private fun decodeLmExifPayloadToken(token: String): LmExifPayload? {
    val normalized = token.trim().takeIf { it.isNotEmpty() } ?: return null
    val jsonText = runCatching {
        val bytes = Base64.decode(normalized, Base64.URL_SAFE or Base64.NO_WRAP)
        String(bytes, Charsets.UTF_8)
    }.getOrNull() ?: return null
    return runCatching {
        val json = JSONObject(jsonText)
        LmExifPayload(
            artist = json.optString("artist").trim(),
            description = json.optString("description").trim(),
            software = json.optString("software").trim(),
            copyright = json.optString("copyright").trim()
        )
    }.getOrNull()
}

private fun encodeLmExifPayloadToken(data: EditableImageExif): String? {
    val payload = JSONObject().apply {
        put("artist", data.artist.trim())
        put("description", data.description.trim())
        put("software", data.software.trim())
        put("copyright", data.copyright.trim())
    }
    val hasAny = payload.optString("artist").isNotBlank() ||
        payload.optString("description").isNotBlank() ||
        payload.optString("software").isNotBlank() ||
        payload.optString("copyright").isNotBlank()
    if (!hasAny) return null
    return Base64.encodeToString(payload.toString().toByteArray(Charsets.UTF_8), Base64.URL_SAFE or Base64.NO_WRAP)
}

private fun splitSoftwareAndPayloadToken(rawSoftware: String?): Pair<String, String?> {
    val value = rawSoftware?.trim().orEmpty()
    if (value.isEmpty()) return "" to null
    val start = value.lastIndexOf(LM_EXIF_SOFTWARE_MARKER_PREFIX)
    if (start < 0) return value to null
    val end = value.indexOf(LM_EXIF_SOFTWARE_MARKER_SUFFIX, start + LM_EXIF_SOFTWARE_MARKER_PREFIX.length)
    if (end <= start) return value to null
    val token = value.substring(start + LM_EXIF_SOFTWARE_MARKER_PREFIX.length, end).trim()
    val clean = (value.substring(0, start) + value.substring(end + LM_EXIF_SOFTWARE_MARKER_SUFFIX.length)).trim()
    return clean to token.takeIf { it.isNotEmpty() }
}

private fun readLmExifPayload(exif: ExifInterface): LmExifPayload? {
    val raw = exif.getAttribute(ExifInterface.TAG_USER_COMMENT)?.trim().orEmpty()
    if (raw.startsWith(LM_EXIF_USER_COMMENT_PREFIX)) {
        val token = raw.removePrefix(LM_EXIF_USER_COMMENT_PREFIX)
        decodeLmExifPayloadToken(token)?.let { return it }
    }
    val (_, tokenFromSoftware) = splitSoftwareAndPayloadToken(exif.getAttribute(ExifInterface.TAG_SOFTWARE))
    return tokenFromSoftware?.let { decodeLmExifPayloadToken(it) }
}

private fun writeLmExifPayload(exif: ExifInterface, data: EditableImageExif) {
    val token = encodeLmExifPayloadToken(data)
    val (cleanSoftware, _) = splitSoftwareAndPayloadToken(exif.getAttribute(ExifInterface.TAG_SOFTWARE))
    if (token == null) {
        val existing = exif.getAttribute(ExifInterface.TAG_USER_COMMENT)?.trim().orEmpty()
        if (existing.startsWith(LM_EXIF_USER_COMMENT_PREFIX)) {
            exif.setAttribute(ExifInterface.TAG_USER_COMMENT, null)
        }
        writeExifTextWithUnicodeSupport(exif, ExifInterface.TAG_SOFTWARE, cleanSoftware, EXIF_TAG_XP_SOFTWARE)
        return
    }
    exif.setAttribute(ExifInterface.TAG_USER_COMMENT, LM_EXIF_USER_COMMENT_PREFIX + token)
    val softwareWithMarker = if (data.software.trim().isBlank()) {
        "${LM_EXIF_SOFTWARE_MARKER_PREFIX}${token}${LM_EXIF_SOFTWARE_MARKER_SUFFIX}"
    } else {
        "${data.software.trim()}${LM_EXIF_SOFTWARE_MARKER_PREFIX}${token}${LM_EXIF_SOFTWARE_MARKER_SUFFIX}"
    }
    writeExifTextWithUnicodeSupport(exif, ExifInterface.TAG_SOFTWARE, softwareWithMarker, EXIF_TAG_XP_SOFTWARE)
}

internal fun readEditableImageExif(context: Context, uri: Uri): EditableImageExif {
    context.contentResolver.openInputStream(uri)?.use { input ->
        val exif = ExifInterface(input)
        val lmPayload = readLmExifPayload(exif)
        return EditableImageExif(
            takenTime = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL).orEmpty(),
            artist = lmPayload?.artist?.takeIf { it.isNotBlank() }
                ?: readExifTextWithUnicodeFallback(exif, ExifInterface.TAG_ARTIST, EXIF_TAG_XP_AUTHOR),
            description = lmPayload?.description?.takeIf { it.isNotBlank() }
                ?: readExifTextWithUnicodeFallback(exif, ExifInterface.TAG_IMAGE_DESCRIPTION, EXIF_TAG_XP_COMMENT),
            software = lmPayload?.software?.takeIf { it.isNotBlank() }
                ?: splitSoftwareAndPayloadToken(readExifTextWithUnicodeFallback(exif, ExifInterface.TAG_SOFTWARE, EXIF_TAG_XP_SOFTWARE)).first,
            copyright = lmPayload?.copyright?.takeIf { it.isNotBlank() }
                ?: readExifTextWithUnicodeFallback(exif, ExifInterface.TAG_COPYRIGHT, EXIF_TAG_XP_COPYRIGHT)
        )
    }
    return EditableImageExif()
}

internal fun saveEditableImageExif(
    context: Context,
    uri: Uri,
    data: EditableImageExif
): String? {
    return try {
        val before = readEditableImageExif(context, uri)
        val pfd = context.contentResolver.openFileDescriptor(uri, "rw")
            ?: return "openFileDescriptor returned null (rw)"
        pfd.use {
            val exif = ExifInterface(it.fileDescriptor)
            exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, normalizeExifDateTime(data.takenTime).ifBlank { null })
            writeExifTextWithUnicodeSupport(exif, ExifInterface.TAG_ARTIST, data.artist, EXIF_TAG_XP_AUTHOR)
            writeExifTextWithUnicodeSupport(exif, ExifInterface.TAG_IMAGE_DESCRIPTION, data.description, EXIF_TAG_XP_COMMENT)
            writeExifTextWithUnicodeSupport(exif, ExifInterface.TAG_COPYRIGHT, data.copyright, EXIF_TAG_XP_COPYRIGHT)
            writeLmExifPayload(exif, data)
            exif.saveAttributes()
        }
        val readBack = readEditableImageExif(context, uri)
        val changedFields = compareChangedImageExifFields(before, data, readBack)
        val mismatch = changedFields.filter { field ->
            when (field) {
                "takenTime" -> readBack.takenTime.trim() != data.takenTime.trim()
                "artist" -> readBack.artist.trim() != data.artist.trim()
                "description" -> readBack.description.trim() != data.description.trim()
                "software" -> readBack.software.trim() != data.software.trim()
                "copyright" -> readBack.copyright.trim() != data.copyright.trim()
                else -> false
            }
        }
        if (mismatch.isNotEmpty()) {
            return "post-save verification mismatch: ${mismatch.joinToString(",")}; readBack=${readBack}"
        }
        null
    } catch (e: Exception) {
        "${e.javaClass.simpleName}: ${e.message ?: "unknown error"}"
    }
}

private fun MediaFormat.stringOrNull(key: String): String? {
    return if (containsKey(key)) getString(key)?.trim()?.takeIf { it.isNotEmpty() } else null
}

private fun MediaFormat.intOrNull(key: String): Int? {
    return if (containsKey(key)) runCatching { getInteger(key) }.getOrNull() else null
}

private fun MediaFormat.floatOrNull(key: String): Float? {
    return if (containsKey(key)) runCatching { getFloat(key) }.getOrNull() else null
}

private fun MediaFormat.longOrNull(key: String): Long? {
    return if (containsKey(key)) runCatching { getLong(key) }.getOrNull() else null
}

private fun loadVideoTrackMetadataRows(context: Context, uri: Uri): List<DetailMetadataRow> {
    val extractor = MediaExtractor()
    return try {
        extractor.setDataSource(context, uri, null)
        val rows = mutableListOf<DetailMetadataRow>()
        var hasVideoTrack = false
        var hasAudioTrack = false
        for (index in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(index)
            val mime = format.stringOrNull(MediaFormat.KEY_MIME).orEmpty()
            if (mime.startsWith("video/")) {
                if (!hasVideoTrack) {
                    appendMetaSection(rows, context.getString(R.string.browser_meta_group_video_tracks))
                    hasVideoTrack = true
                }
                appendMetaIfNotBlank(
                    rows,
                    context.getString(R.string.browser_meta_video_track_mime_label, index + 1),
                    mime
                )
                appendMetaIfNotBlank(
                    rows,
                    context.getString(R.string.browser_meta_video_track_resolution_label, index + 1),
                    formatVideoResolution(
                        format.intOrNull(MediaFormat.KEY_WIDTH)?.toString(),
                        format.intOrNull(MediaFormat.KEY_HEIGHT)?.toString()
                    )
                )
                val frameRate = format.floatOrNull(MediaFormat.KEY_FRAME_RATE)
                    ?: format.intOrNull(MediaFormat.KEY_FRAME_RATE)?.toFloat()
                appendMetaIfNotBlank(
                    rows,
                    context.getString(R.string.browser_meta_video_track_fps_label, index + 1),
                    frameRate?.let { "${"%.2f".format(Locale.getDefault(), it)} fps" }
                )
                appendMetaIfNotBlank(
                    rows,
                    context.getString(R.string.browser_meta_video_track_bitrate_label, index + 1),
                    formatBitrate(format.intOrNull(MediaFormat.KEY_BIT_RATE)?.toString())
                )
                appendMetaIfNotBlank(
                    rows,
                    context.getString(R.string.browser_meta_video_track_duration_label, index + 1),
                    formatDurationUs(format.longOrNull(MediaFormat.KEY_DURATION))
                )
            } else if (mime.startsWith("audio/")) {
                if (!hasAudioTrack) {
                    appendMetaSection(rows, context.getString(R.string.browser_meta_group_audio_tracks))
                    hasAudioTrack = true
                }
                appendMetaIfNotBlank(
                    rows,
                    context.getString(R.string.browser_meta_audio_track_mime_label, index + 1),
                    mime
                )
                appendMetaIfNotBlank(
                    rows,
                    context.getString(R.string.browser_meta_audio_track_sample_rate_label, index + 1),
                    formatSampleRate(format.intOrNull(MediaFormat.KEY_SAMPLE_RATE)?.toString())
                )
                appendMetaIfNotBlank(
                    rows,
                    context.getString(R.string.browser_meta_audio_track_channel_count_label, index + 1),
                    format.intOrNull(MediaFormat.KEY_CHANNEL_COUNT)?.toString()
                )
                appendMetaIfNotBlank(
                    rows,
                    context.getString(R.string.browser_meta_audio_track_bitrate_label, index + 1),
                    formatBitrate(format.intOrNull(MediaFormat.KEY_BIT_RATE)?.toString())
                )
                appendMetaIfNotBlank(
                    rows,
                    context.getString(R.string.browser_meta_audio_track_duration_label, index + 1),
                    formatDurationUs(format.longOrNull(MediaFormat.KEY_DURATION))
                )
            }
        }
        rows
    } finally {
        runCatching { extractor.release() }
    }
}

private fun loadAudioTrackMetadataRows(context: Context, uri: Uri): List<DetailMetadataRow> {
    val extractor = MediaExtractor()
    return try {
        extractor.setDataSource(context, uri, null)
        val rows = mutableListOf<DetailMetadataRow>()
        var hasAudioTrack = false
        for (index in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(index)
            val mime = format.stringOrNull(MediaFormat.KEY_MIME).orEmpty()
            if (!mime.startsWith("audio/")) continue
            if (!hasAudioTrack) {
                appendMetaSection(rows, context.getString(R.string.browser_meta_group_audio_tracks))
                hasAudioTrack = true
            }
            appendMetaIfNotBlank(
                rows,
                context.getString(R.string.browser_meta_audio_track_mime_label, index + 1),
                mime
            )
            appendMetaIfNotBlank(
                rows,
                context.getString(R.string.browser_meta_audio_track_sample_rate_label, index + 1),
                formatSampleRate(format.intOrNull(MediaFormat.KEY_SAMPLE_RATE)?.toString())
            )
            appendMetaIfNotBlank(
                rows,
                context.getString(R.string.browser_meta_audio_track_channel_count_label, index + 1),
                format.intOrNull(MediaFormat.KEY_CHANNEL_COUNT)?.toString()
            )
            appendMetaIfNotBlank(
                rows,
                context.getString(R.string.browser_meta_audio_track_bitrate_label, index + 1),
                formatBitrate(format.intOrNull(MediaFormat.KEY_BIT_RATE)?.toString())
            )
            appendMetaIfNotBlank(
                rows,
                context.getString(R.string.browser_meta_audio_track_duration_label, index + 1),
                formatDurationUs(format.longOrNull(MediaFormat.KEY_DURATION))
            )
        }
        rows
    } finally {
        runCatching { extractor.release() }
    }
}

private fun loadTypeSpecificMetadataRows(
    context: Context,
    uri: Uri,
    fileName: String,
    mimeType: String,
    isDirectory: Boolean
): List<DetailMetadataRow> {
    if (isDirectory) return emptyList()
    return when (inferDetailMetadataKind(fileName, mimeType, isDirectory)) {
        DetailMetadataKind.AUDIO -> runCatching {
            MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource(context, uri)
                val rows = mutableListOf<DetailMetadataRow>()
                appendMetaSection(rows, context.getString(R.string.browser_meta_group_audio_tags))
                appendMetaIfNotBlank(rows, context.getString(R.string.browser_meta_title_label), retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE))
                appendMetaIfNotBlank(rows, context.getString(R.string.browser_meta_artist_label), retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST))
                appendMetaIfNotBlank(rows, context.getString(R.string.browser_meta_album_label), retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM))
                appendMetaIfNotBlank(rows, context.getString(R.string.browser_meta_album_artist_label), retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST))
                appendMetaIfNotBlank(rows, context.getString(R.string.browser_meta_genre_label), retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE))
                appendMetaIfNotBlank(rows, context.getString(R.string.browser_meta_year_label), retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR))
                appendMetaIfNotBlank(rows, context.getString(R.string.browser_meta_author_label), retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_AUTHOR))
                appendMetaIfNotBlank(rows, context.getString(R.string.browser_meta_composer_label), retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COMPOSER))
                appendMetaIfNotBlank(rows, context.getString(R.string.browser_meta_writer_label), retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_WRITER))
                appendMetaIfNotBlank(rows, context.getString(R.string.browser_meta_track_number_label), retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER))
                appendMetaIfNotBlank(rows, context.getString(R.string.browser_meta_disc_number_label), retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER))
                appendMetaIfNotBlank(rows, context.getString(R.string.browser_meta_total_tracks_label), retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_NUM_TRACKS))
                appendMetaIfNotBlank(rows, context.getString(R.string.browser_meta_recorded_date_label), retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE))

                appendMetaSection(rows, context.getString(R.string.browser_meta_group_audio_technical))
                appendMetaIfNotBlank(rows, context.getString(R.string.browser_meta_audio_mime_label), retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE))
                appendMetaIfNotBlank(rows, context.getString(R.string.browser_meta_duration_label), formatDurationMs(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)))
                appendMetaIfNotBlank(rows, context.getString(R.string.browser_meta_bitrate_label), formatBitrate(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    appendMetaIfNotBlank(rows, context.getString(R.string.browser_meta_sample_rate_label), formatSampleRate(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)))
                    appendMetaIfNotBlank(rows, context.getString(R.string.browser_meta_bits_per_sample_label), retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITS_PER_SAMPLE))
                }
                rows += loadAudioTrackMetadataRows(context, uri)
                rows
            }
        }.getOrElse { emptyList() }

        DetailMetadataKind.VIDEO -> runCatching {
            MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource(context, uri)
                val rows = mutableListOf<DetailMetadataRow>()
                appendMetaIfNotBlank(rows, context.getString(R.string.browser_meta_duration_label), formatDurationMs(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)))
                appendMetaIfNotBlank(rows, context.getString(R.string.browser_meta_bitrate_label), formatBitrate(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)))
                appendMetaIfNotBlank(
                    rows,
                    context.getString(R.string.browser_meta_resolution_label),
                    formatVideoResolution(
                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH),
                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                    )
                )
                appendMetaIfNotBlank(rows, context.getString(R.string.browser_meta_rotation_label), retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION))
                rows += loadVideoTrackMetadataRows(context, uri)
                rows
            }
        }.getOrElse { emptyList() }

        DetailMetadataKind.IMAGE -> runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val rows = mutableListOf<DetailMetadataRow>()
                val exif = ExifInterface(input)
                val width = exif.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, 0)
                    .takeIf { it > 0 }
                    ?: exif.getAttributeInt(ExifInterface.TAG_PIXEL_X_DIMENSION, 0).takeIf { it > 0 }
                val height = exif.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, 0)
                    .takeIf { it > 0 }
                    ?: exif.getAttributeInt(ExifInterface.TAG_PIXEL_Y_DIMENSION, 0).takeIf { it > 0 }
                if (width != null && height != null) {
                    appendMetaIfNotBlank(
                        rows,
                        context.getString(R.string.browser_meta_resolution_label),
                        "${width} x ${height}"
                    )
                }
                val lmPayload = readLmExifPayload(exif)
                appendMetaIfNotBlank(rows, context.getString(R.string.browser_meta_taken_time_label), exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL))
                appendMetaIfNotBlank(
                    rows,
                    context.getString(R.string.browser_meta_artist_label),
                    lmPayload?.artist?.takeIf { it.isNotBlank() }
                        ?: readExifTextWithUnicodeFallback(exif, ExifInterface.TAG_ARTIST, EXIF_TAG_XP_AUTHOR)
                )
                appendMetaIfNotBlank(
                    rows,
                    context.getString(R.string.browser_meta_description_label),
                    lmPayload?.description?.takeIf { it.isNotBlank() }
                        ?: readExifTextWithUnicodeFallback(exif, ExifInterface.TAG_IMAGE_DESCRIPTION, EXIF_TAG_XP_COMMENT)
                )
                appendMetaIfNotBlank(rows, context.getString(R.string.browser_meta_camera_make_label), exif.getAttribute(ExifInterface.TAG_MAKE))
                appendMetaIfNotBlank(rows, context.getString(R.string.browser_meta_camera_model_label), exif.getAttribute(ExifInterface.TAG_MODEL))
                appendMetaIfNotBlank(rows, context.getString(R.string.browser_meta_lens_model_label), exif.getAttribute(ExifInterface.TAG_LENS_MODEL))
                appendMetaIfNotBlank(rows, context.getString(R.string.browser_meta_iso_label), exif.getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY))
                appendMetaIfNotBlank(rows, context.getString(R.string.browser_meta_exposure_label), formatExifExposureTime(exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME)))
                appendMetaIfNotBlank(rows, context.getString(R.string.browser_meta_aperture_label), formatExifAperture(exif.getAttribute(ExifInterface.TAG_F_NUMBER)))
                appendMetaIfNotBlank(rows, context.getString(R.string.browser_meta_focal_length_label), formatExifFocalLength(exif.getAttribute(ExifInterface.TAG_FOCAL_LENGTH)))
                appendMetaIfNotBlank(rows, context.getString(R.string.browser_meta_orientation_label), formatExifOrientation(exif.getAttribute(ExifInterface.TAG_ORIENTATION)))
                appendMetaIfNotBlank(rows, context.getString(R.string.browser_meta_white_balance_label), formatExifWhiteBalance(exif.getAttribute(ExifInterface.TAG_WHITE_BALANCE)))
                appendMetaIfNotBlank(rows, context.getString(R.string.browser_meta_flash_label), formatExifFlash(exif.getAttribute(ExifInterface.TAG_FLASH)))
                appendMetaIfNotBlank(rows, context.getString(R.string.browser_meta_metering_mode_label), formatExifMeteringMode(exif.getAttribute(ExifInterface.TAG_METERING_MODE)))
                appendMetaIfNotBlank(rows, context.getString(R.string.browser_meta_exposure_compensation_label), formatExifExposureCompensation(exif.getAttribute(ExifInterface.TAG_EXPOSURE_BIAS_VALUE)))
                appendMetaIfNotBlank(rows, context.getString(R.string.browser_meta_color_space_label), formatExifColorSpace(exif.getAttribute(ExifInterface.TAG_COLOR_SPACE)))
                appendMetaIfNotBlank(
                    rows,
                    context.getString(R.string.browser_meta_software_label),
                    lmPayload?.software?.takeIf { it.isNotBlank() }
                        ?: splitSoftwareAndPayloadToken(readExifTextWithUnicodeFallback(exif, ExifInterface.TAG_SOFTWARE, EXIF_TAG_XP_SOFTWARE)).first
                )
                appendMetaIfNotBlank(
                    rows,
                    context.getString(R.string.browser_meta_copyright_label),
                    lmPayload?.copyright?.takeIf { it.isNotBlank() }
                        ?: readExifTextWithUnicodeFallback(exif, ExifInterface.TAG_COPYRIGHT, EXIF_TAG_XP_COPYRIGHT)
                )
                appendMetaIfNotBlank(rows, context.getString(R.string.browser_meta_gps_label), formatExifGps(exif))
                appendMetaIfNotBlank(rows, context.getString(R.string.browser_meta_altitude_label), formatExifAltitude(exif))
                rows
            } ?: emptyList()
        }.getOrElse { emptyList() }

        DetailMetadataKind.PDF -> runCatching {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    val rows = mutableListOf<DetailMetadataRow>()
                    appendMetaIfNotBlank(rows, context.getString(R.string.browser_meta_page_count_label), renderer.pageCount.toString())
                    rows
                }
            } ?: emptyList()
        }.getOrElse { emptyList() }

        DetailMetadataKind.EPUB -> runCatching {
            val parseResult = runCatching {
                val cacheDir = getEpubCacheDir(context, uri)
                loadEpubFromCache(context, cacheDir)?.let { EpubParseResult.Success(it) }
                    ?: extractEpubToCache(context, uri, null, fileName) { }
            }.getOrElse { error ->
                throw IllegalStateException("${error.javaClass.simpleName}: ${error.message ?: "unknown error"}")
            }

            when (parseResult) {
                is EpubParseResult.Success -> {
                    val rows = mutableListOf<DetailMetadataRow>()
                    appendMetaSection(rows, context.getString(R.string.browser_meta_group_epub_book))
                    appendMetaIfNotBlank(rows, context.getString(R.string.browser_meta_title_label), parseResult.result.bookInfo.title)
                    appendMetaIfNotBlank(rows, context.getString(R.string.browser_meta_author_label), parseResult.result.bookInfo.author)
                    appendMetaIfNotBlank(rows, context.getString(R.string.browser_meta_language_label), parseResult.result.bookInfo.language)
                    appendMetaIfNotBlank(rows, context.getString(R.string.browser_meta_chapter_count_label), parseResult.result.chapters.size.toString())
                    rows
                }

                is EpubParseResult.Error -> {
                    val detail = parseResult.detail?.trim().orEmpty()
                    val message = parseResult.message.trim()
                    val reason = if (detail.isNotEmpty()) "$message ($detail)" else message
                    throw IllegalStateException(reason)
                }
            }
        }.getOrElse { error ->
            throw IllegalStateException(error.message ?: error.javaClass.simpleName)
        }

        DetailMetadataKind.ARCHIVE,
        DetailMetadataKind.UNKNOWN -> emptyList()
    }
}

internal data class PlaylistTrackTransfer(
    val source: Playlist,
    val trackIndex: Int,
    val move: Boolean
)

internal fun normalizeContentUriString(s: String): String =
    com.kenny.localmanager.file.normalizeContentUriString(s)

private val sevenZSplitPartRegex = Regex("(?i)^(.*\\.7z)\\.(\\d{3})$")

internal fun fileExtensionKey(name: String): String? {
    val dotIndex = name.lastIndexOf('.')
    if (dotIndex <= 0 || dotIndex >= name.lastIndex) return null
    return name.substring(dotIndex + 1).trim().lowercase().ifBlank { null }
}

private val externalOpenVideoExts = setOf(
    "rmvb", "rm", "rv", "ram", "mp4", "m4v", "mkv", "avi", "mov", "wmv", "flv",
    "ts", "m2ts", "mts", "webm", "3gp", "mpeg", "mpg", "vob", "asf"
)
private val externalOpenAudioExts = setOf(
    "mp3", "flac", "wav", "m4a", "aac", "ogg", "opus", "wma", "ape", "amr", "aiff", "alac"
)
private val externalOpenImageExts = setOf(
    "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "tiff", "tif", "svg"
)
private val externalOpenTextExts = setOf(
    "txt", "md", "rst", "log", "json", "xml", "yaml", "yml", "csv", "ini", "toml"
)
private val externalOpenArchiveMimeByExt = mapOf(
    "zip" to "application/zip",
    "7z" to "application/x-7z-compressed",
    "rar" to "application/vnd.rar",
    "tar" to "application/x-tar",
    "gz" to "application/gzip",
    "bz2" to "application/x-bzip2",
    "xz" to "application/x-xz",
    "pdf" to "application/pdf",
    "epub" to "application/epub+zip",
    "apk" to "application/vnd.android.package-archive",
    "srt" to "application/x-subrip",
    "ass" to "text/x-ssa",
    "ssa" to "text/x-ssa"
)

private fun toExternalLaunchUri(context: Context, uri: Uri): Uri {
    if (uri.scheme != "file") return uri
    val path = uri.path ?: return uri
    val file = java.io.File(path)
    if (!file.exists()) return uri
    return runCatching {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }.getOrElse { uri }
}

private fun guessExternalOpenMimeType(context: Context, uri: Uri, fileNameHint: String?): String {
    val resolverMime = context.contentResolver.getType(uri)?.trim()?.lowercase(Locale.ROOT)
    val validResolverMime = resolverMime
        ?.takeIf { it.isNotBlank() && it != "application/octet-stream" && it != "*/*" }
    if (validResolverMime != null) return validResolverMime

    val candidateName = fileNameHint
        ?.takeIf { it.isNotBlank() }
        ?: context.contentResolver.getDisplayName(uri)
    val ext = candidateName
        ?.substringAfterLast('.', "")
        ?.trim()
        ?.lowercase(Locale.ROOT)
        .orEmpty()
    if (ext.isNotEmpty()) {
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)?.let { return it }
        externalOpenArchiveMimeByExt[ext]?.let { return it }
        if (ext in externalOpenVideoExts) return "video/*"
        if (ext in externalOpenAudioExts) return "audio/*"
        if (ext in externalOpenImageExts) return "image/*"
        if (ext in externalOpenTextExts) return "text/plain"
    }
    return resolverMime?.takeIf { it.isNotBlank() } ?: "*/*"
}

private fun buildExternalOpenIntent(
    context: Context,
    uri: Uri,
    packageName: String? = null,
    fileNameHint: String? = null,
    forcedMimeType: String? = null
): Intent {
    val externalUri = toExternalLaunchUri(context, uri)
    val mimeType = forcedMimeType?.takeIf { it.isNotBlank() }
        ?: guessExternalOpenMimeType(context, externalUri, fileNameHint)
    return Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(externalUri, mimeType)
        if (!packageName.isNullOrBlank()) setPackage(packageName)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

@Suppress("DEPRECATION")
private fun queryExternalOpenTargets(
    context: Context,
    uriString: String,
    fileNameHint: String? = null,
    forcedMimeType: String? = null
): List<ExternalAppTarget> {
    val packageManager = context.packageManager
    return packageManager.queryIntentActivities(
        buildExternalOpenIntent(context, Uri.parse(uriString), fileNameHint = fileNameHint, forcedMimeType = forcedMimeType),
        PackageManager.MATCH_DEFAULT_ONLY
    ).asSequence()
        .mapNotNull { resolveInfo ->
            val packageName = resolveInfo.activityInfo?.packageName?.trim().orEmpty()
            if (packageName.isBlank() || packageName == context.packageName) return@mapNotNull null
            val label = runCatching { resolveInfo.loadLabel(packageManager)?.toString() }.getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?: packageName
            ExternalAppTarget(packageName = packageName, label = label)
        }
        .distinctBy { it.packageName }
        .sortedBy { it.label.lowercase(Locale.getDefault()) }
        .toList()
}

internal fun launchExternalOpen(
    context: Context,
    uriString: String,
    packageName: String?,
    fileNameHint: String? = null,
    forcedMimeType: String? = null
): Boolean {
    return runCatching {
        context.startActivity(buildExternalOpenIntent(context, Uri.parse(uriString), packageName, fileNameHint, forcedMimeType))
        true
    }.getOrElse { false }
}

internal fun pathFromRoot(context: Context, rootUri: String?, currentUri: String): String {
    if (rootUri == null) return currentUri
    val rootDoc = DocumentFile.fromTreeUri(context, Uri.parse(normalizeContentUriString(rootUri))) ?: return currentUri
    val currentDoc = if (currentUri.contains("/tree/")) DocumentFile.fromTreeUri(context, Uri.parse(currentUri))
        else DocumentFile.fromSingleUri(context, Uri.parse(currentUri))
    val current = currentDoc ?: return currentUri
    val parts = mutableListOf<String>()
    var cursor: DocumentFile? = current
    while (cursor != null) {
        if (cursor.uri == rootDoc.uri) break
        parts.add(0, cursor.name ?: "?")
        cursor = cursor.parentFile
    }
    return "/" + parts.joinToString("/")
}

private fun humanReadableRootUri(uriString: String): String {
    val normalized = normalizeContentUriString(uriString)
    return runCatching {
        val uri = Uri.parse(normalized)
        val treeId = DocumentsContract.getTreeDocumentId(uri)
        val decoded = Uri.decode(treeId)
        val parts = decoded.split(':', limit = 2)
        val volume = parts.getOrNull(0)?.trim().orEmpty()
        val relative = parts.getOrNull(1)?.trim().orEmpty()
        when {
            volume.equals("home", ignoreCase = true) -> {
                if (relative.isNotBlank()) "/sdcard/Documents/$relative" else "/sdcard/Documents"
            }
            volume.equals("primary", ignoreCase = true) -> {
                if (relative.isNotBlank()) "/sdcard/$relative" else "/sdcard"
            }
            relative.isNotBlank() -> "/$relative"
            volume.isNotBlank() -> volume
            else -> decoded.ifBlank { normalized }
        }
    }.getOrElse {
        normalized.substringAfterLast('/').substringAfterLast('%').ifBlank { normalized }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileBrowserApp(
    initialFileUri: androidx.compose.runtime.MutableState<String?>? = null,
    initialLaunchTarget: String? = null
) {
    val context = LocalContext.current
    val prefs = remember { Preferences(context) }

    // AppShell + DirectoryRoot state (long-lived while the app shell is active)
    // 目录域共享数据对象（AppShell 分层设计 §2.4）：root、当前路径、刷新信号
    val browserTab = remember { BrowserTabState(context, prefs) }
    var recentRootUris by remember { mutableStateOf<List<String>>(emptyList()) }
    var initialDirUri by remember { mutableStateOf<String?>(null) }

    // Transient viewer / editor routes (short-lived feature flows)
    var pendingSaveFileUri by remember { mutableStateOf<String?>(null) }
    var showOverwriteConfirm by remember { mutableStateOf<Pair<String, String>?>(null) } // (sourceUri, fileName)
    var saveInProgress by remember { mutableStateOf(false) }
    // 查看器类临时 Route：统一由 TransientRoute 建模，渲染在 TransientViewerRouteHost 中
    var transientRoute by remember { mutableStateOf<TransientRoute?>(null) }
    val markdownViewerSessionCache = remember { MarkdownViewerSessionCache() }
    // Crypto 域（GPG 加解密 / .pass 保护查看编辑）单槽工作流状态
    val crypto = rememberCryptoWorkflow()
    var encryptedCacheExitDialog by remember { mutableStateOf<EncryptedCacheExitDialogState?>(null) }

    // AppShell navigation + shared UI surfaces
    var currentMainTab by remember { mutableStateOf<MainTab?>(null) }
    var showFileDetail by remember { mutableStateOf<DocumentFileModel?>(null) }
    var saveCompletedToken by remember { mutableStateOf(0) }
    var recentOpenItems by remember { mutableStateOf<List<RecentOpenItem>>(emptyList()) }

    // App shell commands / helpers
    val scope = rememberCoroutineScope()
    val networkServiceTabs = remember {
        setOf(MainTab.FTP, MainTab.FAMILY_NETWORK, MainTab.LOCAL_SHARE)
    }
    fun isNetworkServiceTab(tab: MainTab?): Boolean = tab in networkServiceTabs

    // Startup behavior
    val launchShortcutTarget = MainTab.fromLaunchTarget(initialLaunchTarget)
    val bypassExitDoubleConfirm = launchShortcutTarget == MainTab.PLAYER || launchShortcutTarget == MainTab.QUICK_NOTE

    var startupDecryptKeyEnabled by remember { mutableStateOf(false) }
    var hasSecretKeyFile by remember { mutableStateOf(false) }
    var unlockedByStartup by remember { mutableStateOf(false) }
    LaunchedEffect(prefs) {
        prefs.startupDecryptKey.collect { startupDecryptKeyEnabled = it }
    }
    LaunchedEffect(Unit) {
        hasSecretKeyFile = withContext(Dispatchers.IO) {
            File(getGpgKeyDir(context), "secring.gpg").exists()
        }
    }
    val showUnlockGate = startupDecryptKeyEnabled && hasSecretKeyFile && !unlockedByStartup

    var viewerPreviewBytes by remember { mutableStateOf(4096) }
    LaunchedEffect(prefs) {
        prefs.viewerPreviewBytes.collect { viewerPreviewBytes = it }
    }
    LaunchedEffect(prefs) {
        prefs.recentOpenItems.collect { recentOpenItems = it }
    }
    LaunchedEffect(Unit) {
        browserTab.restoreRoot()
    }
    LaunchedEffect(browserTab.rootUri, initialDirUri) {
        browserTab.ensureCurrentUri(initialDirUri)
    }
    LaunchedEffect(prefs) {
        prefs.filterVisible.collect { browserTab.filterVisible = it }
    }
    LaunchedEffect(prefs) {
        prefs.hideDotFiles.collect { browserTab.hideDotFiles = it }
    }
    LaunchedEffect(prefs) {
        prefs.externalOpenByExtension.collect { browserTab.preferredExternalPackages = it }
    }
    LaunchedEffect(prefs) {
        prefs.recentRootUris.collect { recentRootUris = it }
    }
    LaunchedEffect(browserTab.rootUri) {
        markdownViewerSessionCache.clear()
    }
    LaunchedEffect(initialFileUri?.value) {
        val uriStr = initialFileUri?.value ?: return@LaunchedEffect
        initialFileUri.value = null
        pendingSaveFileUri = uriStr
    }

    // App 级 Tab 命令入口：所有跨域"切到某 Tab"的请求统一走这里，避免各处直接改 currentMainTab
    fun persistTabChoice(tab: MainTab, previousTab: MainTab?) {
        scope.launch {
            val previousNonServer = previousTab
            if (isNetworkServiceTab(tab)) {
                if (previousNonServer != null && !isNetworkServiceTab(previousNonServer)) {
                    prefs.setLastNonServerMainTab(previousNonServer.key)
                }
            } else {
                prefs.setLastNonServerMainTab(tab.key)
            }
            prefs.setLastMainTab(tab.key)
        }
    }

    fun switchMainTab(tab: MainTab) {
        if (currentMainTab == tab) return
        val previousTab = currentMainTab
        currentMainTab = tab
        persistTabChoice(tab, previousTab)
    }

    fun requestOpenMainTab(tab: MainTab) {
        switchMainTab(tab)
    }

    fun requestOpenPlayerTab() {
        requestOpenMainTab(MainTab.PLAYER)
    }

    fun requestOpenAboutTab() {
        requestOpenMainTab(MainTab.ABOUT)
    }

    LaunchedEffect(prefs, initialLaunchTarget) {
        if (CrashReportStore.hasUnseenCrash(context)) {
            Toast.makeText(context, context.getString(R.string.crash_report_found_toast), Toast.LENGTH_SHORT).show()
            withContext(Dispatchers.IO) {
                CrashReportStore.exportLatestToRootAndClear(context)
            }
            requestOpenAboutTab()
            CrashReportStore.markLatestSeen(context)
            return@LaunchedEffect
        }
        val launchTab = MainTab.fromLaunchTarget(initialLaunchTarget)
        val savedTab = MainTab.fromKey(prefs.lastMainTab.first())
        val savedNonServerTab = MainTab.fromKey(prefs.lastNonServerMainTab.first())
        val restoreTab = if (launchTab != null) {
            launchTab
        } else if (isNetworkServiceTab(savedTab)) {
            savedNonServerTab
        } else {
            savedTab
        }
        currentMainTab = restoreTab
    }

    fun recordRecentOpen(
        type: String,
        key: String,
        title: String,
        uri: String? = null,
        playlistId: String? = null
    ) {
        scope.launch {
            prefs.addRecentOpenItem(
                type = type,
                key = key,
                title = title,
                uri = uri,
                playlistId = playlistId
            )
        }
    }

    // Archive 阅读器域：压缩包 / 文档阅读的临时 Route（请求 -> 密码 -> 解压 -> 查看器）
    val archiveReader = rememberArchiveReaderWorkflow(context, scope)

    suspend fun savePicZipImageToRoot(sourceFile: File, fileName: String): Boolean {
        val targetRoot = browserTab.rootUri?.let { normalizeContentUriString(it) }
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


    val quickNoteController = rememberQuickNoteTabController(
        rootUri = browserTab.rootUri,
        isActive = currentMainTab == MainTab.QUICK_NOTE,
        markdownViewerSessionCache = markdownViewerSessionCache
    )
    val bookNoteController = rememberBookNoteTabController(
        rootUri = browserTab.rootUri,
        isActive = currentMainTab == MainTab.BOOK_NOTE,
        markdownViewerSessionCache = markdownViewerSessionCache
    )

    // Config 域：配置导入 / 导出工作流（独立状态持有者，写盘后通过回调刷新目录）
    val configImportExportWorkflow = rememberConfigImportExportWorkflow(
        prefs = prefs,
        rootUriProvider = { browserTab.rootUri },
        onConfigFileWritten = { browserTab.refresh() }
    )

    val treeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            val normalized = normalizeContentUriString(uri.toString())
            val previousRoot = browserTab.rootUri?.let { normalizeContentUriString(it) }
            scope.launch {
                prefs.recordRecentRootSwitch(previousRoot, normalized)
                prefs.setRootUri(normalized)
            }
            browserTab.backStack.clear()
            browserTab.changeRoot(normalized, refresh = false)
            initialDirUri = normalized
        }
    }

    LaunchedEffect(pendingSaveFileUri, browserTab.rootUri) {
        val sourceUriStr = pendingSaveFileUri ?: return@LaunchedEffect
        val targetRoot = browserTab.rootUri?.let { normalizeContentUriString(it) } ?: return@LaunchedEffect
        if (showOverwriteConfirm != null || saveInProgress) return@LaunchedEffect
        val ctx = context
        val sourceUri = Uri.parse(sourceUriStr)
        val sourceDoc = DocumentFile.fromSingleUri(ctx, sourceUri) ?: run {
            pendingSaveFileUri = null
            Toast.makeText(ctx, ctx.getString(R.string.browser_cannot_read_file), Toast.LENGTH_SHORT).show()
            return@LaunchedEffect
        }
        val fileName = sourceDoc.name ?: run {
            pendingSaveFileUri = null
            Toast.makeText(ctx, ctx.getString(R.string.browser_cannot_get_filename), Toast.LENGTH_SHORT).show()
            return@LaunchedEffect
        }
        val targetUri = Uri.parse(targetRoot)
        val existingUri = findChildByName(ctx, targetUri, fileName)
        if (existingUri != null) {
            showOverwriteConfirm = sourceUriStr to fileName
        } else {
            saveInProgress = true
            val treeUri = Uri.parse(targetRoot)
            val copied = withContext(Dispatchers.IO) {
                copyDocumentTo(ctx, sourceUri, targetUri, treeUri)
            }
            saveInProgress = false
            pendingSaveFileUri = null
            if (copied != null) {
                val dir = normalizeContentUriString(targetRoot)
                initialDirUri = dir
                browserTab.navigateTo(dir)
                saveCompletedToken++
                Toast.makeText(ctx, ctx.getString(R.string.browser_saved_to_current_dir), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(ctx, ctx.getString(R.string.viewer_save_failed), Toast.LENGTH_SHORT).show()
            }
        }
    }

    @Composable
    fun MainTabsRootContent() {
        val displayUri = browserTab.currentUri ?: initialDirUri ?: browserTab.rootUri!!
        val activeMainTab = currentMainTab ?: MainTab.DIRECTORY

        fun requestOpenQuickNoteTabFromDirectory() {
            requestOpenMainTab(MainTab.QUICK_NOTE)
            quickNoteController.requestOpenWithCachedPassword(true)
        }

        var bulletinForwardPayload by remember { mutableStateOf<BulletinForwardPayload?>(null) }

        // App-level transient dialogs / playback-target workflow
        var showCacheManagementDialog by remember { mutableStateOf(false) }
        var showKeyManagementDialog by remember { mutableStateOf(false) }
        LaunchedEffect(saveCompletedToken) { if (saveCompletedToken > 0) browserTab.refresh() }
        var lastBackPressTime by remember { mutableStateOf(0L) }
        fun finishCurrentActivity() {
            (context as? Activity)?.finish()
        }

        fun requestExitApp() {
            if (activeMainTab == MainTab.QUICK_NOTE) {
                if (quickNoteController.inProgress) {
                    Toast.makeText(context, context.getString(R.string.browser_quick_note_busy), Toast.LENGTH_SHORT).show()
                    return
                }
                quickNoteController.persistIfNeeded(context.getString(R.string.quick_note_persist_reason_exit)) { saved ->
                    if (saved) {
                        finishCurrentActivity()
                    }
                }
                return
            }
            if (bypassExitDoubleConfirm) {
                finishCurrentActivity()
            } else {
                val now = System.currentTimeMillis()
                if (now - lastBackPressTime < 2000) finishCurrentActivity()
                else {
                    lastBackPressTime = now
                    Toast.makeText(context, context.getString(R.string.browser_press_again_to_exit), Toast.LENGTH_SHORT).show()
                }
            }
        }

        var showChangeRootConfirm by remember { mutableStateOf(false) }
        var showRootSwitchDialog by remember { mutableStateOf(false) }
        var quickMaskOp by remember { mutableStateOf<DocumentFileModel?>(null) }
        var quickObfuscatePassword by remember { mutableStateOf("") }
        var quickObfuscatePasswordConfirm by remember { mutableStateOf("") }
        var quickObfuscateInProgress by remember { mutableStateOf(false) }
        var batchMaskOp by remember { mutableStateOf<List<DocumentFileModel>?>(null) }
        var batchObfuscatePassword by remember { mutableStateOf("") }
        var batchObfuscatePasswordConfirm by remember { mutableStateOf("") }
        var batchObfuscateInProgress by remember { mutableStateOf(false) }

        // Long-running operation status
        var progressOp by remember { mutableStateOf<OperationProgress?>(null) }
        var progressCancelAction by remember { mutableStateOf<(() -> Unit)?>(null) }

        // DirectoryRoot derived UI state
        var cachedTrashUri by remember { mutableStateOf<Uri?>(null) }
        LaunchedEffect(browserTab.rootUri) {
            cachedTrashUri = browserTab.rootUri?.let { r ->
                withContext(Dispatchers.IO) {
                    val root = Uri.parse(normalizeContentUriString(r))
                    getTrashUriIfExists(context, root, root)
                }
            }
        }

        // Tab domain services
        val ftpManager = remember { com.kenny.localmanager.ftp.FtpServerManager(context) }
        val localShareManager = remember { com.kenny.localmanager.ftp.FtpServerManager(context) }
        val familyNetworkManager = remember { com.kenny.localmanager.family.FamilyNetworkManager(context) }
        // 网络服务域共享配置（FTP / WebUI / 家庭网络）：状态所有权在 NetworkServiceConfigState
        val networkServiceConfig = rememberNetworkServiceConfigState(prefs)
            var showGitConfigDialog by remember { mutableStateOf(false) }
        var showPubkeyShareScreen by remember { mutableStateOf(false) }
        // Git 分享临时流程（目录文件共享到 Git 仓库）
        var shareFileToGitTarget by remember { mutableStateOf<DocumentFileModel?>(null) }
        val shareGitLogs = remember { mutableStateListOf<String>() }
        var shareGitInProgress by remember { mutableStateOf(false) }
        var shareGitDone by remember { mutableStateOf(false) }

        // Archive 操作向导域：解压 / 压缩向导（表单状态在 ArchiveOperationWorkflowState，执行编排留宿主）
        val archiveOps = rememberArchiveOperationWorkflow(context, scope)

        // PlayerTab 域：播放器 tab 功能对象（接口面：MainTabFeature + PlayerTabApi，实现不可见）
        val playerTab = playerTabFeature(
            context = context,
            prefs = prefs,
            scope = scope,
            autoplayShortcut = launchShortcutTarget == MainTab.PLAYER,
            onRequestExitApp = { requestExitApp() },
            recordRecentOpen = { type, key, title, uri, playlistId -> recordRecentOpen(type, key, title, uri, playlistId) },
            onBatchFinished = { browserTab.finishPendingList() },
            onOpenPlayerTab = { requestOpenPlayerTab() },
        )

        LaunchedEffect(prefs) {
            val legacyExtra = prefs.familyNetworkExtraRoles.first()
            if (!legacyExtra.isNullOrBlank()) {
                familyNetworkManager.migrateUserRolesFromLegacyConfig(legacyExtra)
                prefs.setFamilyNetworkExtraRoles(null)
            }
        }
        LaunchedEffect(activeMainTab) {
            withContext(Dispatchers.IO) {
                if (activeMainTab != MainTab.FTP) {
                    ftpManager.stop()
                }
                if (activeMainTab != MainTab.LOCAL_SHARE) {
                    localShareManager.stop()
                }
            }
        }
        // AppShell 编排：网络服务配置变化时同步到家庭网络服务
        LaunchedEffect(
            networkServiceConfig.ftpPassword,
            networkServiceConfig.localNetworkServiceEnabled,
            networkServiceConfig.familyNetworkUserName,
            networkServiceConfig.familyNetworkAvatarId,
            networkServiceConfig.familyNetworkHostName
        ) {
            familyNetworkManager.configure(
                networkServiceConfig.ftpPassword,
                networkServiceConfig.localNetworkServiceEnabled,
                networkServiceConfig.familyNetworkUserName.ifBlank { null },
                networkServiceConfig.familyNetworkAvatarId.ifBlank { null },
                networkServiceConfig.familyNetworkHostName.ifBlank { null }
            )
        }
        LaunchedEffect(activeMainTab) {
            familyNetworkManager.setFamilyTabActive(activeMainTab == MainTab.FAMILY_NETWORK)
        }
        DisposableEffect(familyNetworkManager) {
            onDispose {
                familyNetworkManager.destroy()
            }
        }
        BackHandler {
            when {
                progressOp != null -> {
                    progressCancelAction?.invoke()
                }
                batchMaskOp != null -> { batchMaskOp = null; batchObfuscatePassword = ""; batchObfuscatePasswordConfirm = "" }
                quickMaskOp != null -> { quickMaskOp = null; quickObfuscatePassword = ""; quickObfuscatePasswordConfirm = "" }
                crypto.passEdit != null -> crypto.closePassEdit()
                crypto.request != null -> {
                    val guarded = crypto.request is CryptoRequest.PassView || crypto.request is CryptoRequest.PassEdit
                    if (!guarded || !crypto.inProgress) crypto.close()
                }
                showChangeRootConfirm -> showChangeRootConfirm = false
                showKeyManagementDialog -> showKeyManagementDialog = false
                showCacheManagementDialog -> showCacheManagementDialog = false
                showGitConfigDialog -> showGitConfigDialog = false
                browserTab.showPendingDeleteConfirm -> browserTab.showPendingDeleteConfirm = false
                browserTab.showPendingList -> browserTab.showPendingList = false
                familyNetworkManager.state.value.openBoardSession != null -> familyNetworkManager.closeBulletinBoard()
                activeMainTab != MainTab.DIRECTORY -> requestExitApp()
                archiveOps.request != null -> archiveOps.close()
                archiveReader.request?.kind in ArchiveReaderCancelableKinds -> archiveReader.cancelRequest()
                transientRoute is TransientRoute.PdfViewer -> transientRoute = null
                browserTab.canGoBack -> browserTab.goBack()
                else -> {
                    requestExitApp()
                }
            }
        }
            var startupDecryptKey by remember { mutableStateOf(false) }
        LaunchedEffect(prefs) {
            prefs.startupDecryptKey.collect { startupDecryptKey = it }
        }
        val copyMoveLog: ((String) -> Unit)? = null
        suspend fun runWithProgress(
            label: String,
            total: Int? = null,
            block: suspend ((Int) -> Unit) -> Unit
        ) {
            progressCancelAction = null
            try {
                runWithUiProgress(
                    setProgressState = { progressOp = it },
                    label = label,
                    total = total,
                    block = block
                )
            } finally {
                progressCancelAction = null
            }
        }

        fun compressArchiveItems(
            sourceUris: List<Uri>,
            parentDirUri: Uri,
            treeUri: Uri?,
            baseName: String,
            format: ArchiveCompressionFormat,
            password: CharArray?,
            setProgress: (Int, Int) -> Unit,
            shouldCancel: () -> Boolean = { false }
        ): Boolean {
            if (sourceUris.isEmpty()) return false
            val outputName = buildArchiveOutputName(baseName, format)
            if (format.compressesSingleFileOnly) {
                return sourceUris.all { uri ->
                    val docName = DocumentFile.fromSingleUri(context, uri)?.name ?: "archive"
                    compressSingleFileToFormat(
                        context = context,
                        sourceUri = uri,
                        parentDirUri = parentDirUri,
                        treeUri = treeUri,
                        outputFileName = buildArchiveOutputName(docName, format),
                        format = format,
                        setProgress = setProgress,
                        shouldCancel = shouldCancel
                    )
                }
            }
            return when (format) {
                ArchiveCompressionFormat.ZIP -> compressToZip(context, sourceUris, parentDirUri, treeUri, outputName, password, setProgress, shouldCancel)
                ArchiveCompressionFormat.SEVEN_Z -> compressTo7z(context, sourceUris, parentDirUri, treeUri, outputName, password, setProgress, shouldCancel)
                ArchiveCompressionFormat.TAR -> compressTarArchive(context, sourceUris, parentDirUri, treeUri, outputName, format, password, setProgress, shouldCancel)
                ArchiveCompressionFormat.TAR_GZ -> compressTarArchive(context, sourceUris, parentDirUri, treeUri, outputName, format, password, setProgress, shouldCancel)
                ArchiveCompressionFormat.TAR_BZ2 -> compressTarArchive(context, sourceUris, parentDirUri, treeUri, outputName, format, password, setProgress, shouldCancel)
                ArchiveCompressionFormat.TAR_XZ -> compressTarArchive(context, sourceUris, parentDirUri, treeUri, outputName, format, password, setProgress, shouldCancel)
                ArchiveCompressionFormat.GZ -> false
                ArchiveCompressionFormat.BZ2 -> false
                ArchiveCompressionFormat.XZ -> false
            }
        }

        val finishPendingListAndReturn: () -> Unit = {
            browserTab.finishPendingList()
        }

        // Archive 操作向导域：确认后的文件操作编排（进度、取消、目录刷新留宿主）
        fun executeArchiveOperation() {
            when (val req = archiveOps.request) {
                is ArchiveOperationRequest.Unzip -> {
                    val target = req.target
                    val parentDirUri = Uri.parse(displayUri)
                    val treeUri = browserTab.rootUri?.let { Uri.parse(normalizeContentUriString(it)) }
                    scope.launch {
                        val cancelFlag = OperationCancelFlag()
                        progressCancelAction = { cancelFlag.requestCancel() }
                        progressOp = OperationProgress(context.getString(R.string.browser_op_extract), 0, null)
                        delay(50)
                        val pwd = if (archiveOps.encrypted == true) archiveOps.password.toCharArray() else null
                        val result = withContext(Dispatchers.IO) {
                        unzipToParent(
                        context,
                        target.uri,
                        parentDirUri,
                        treeUri,
                        target.name,
                        pwd,
                        setProgress = { cur, tot ->
                        scope.launch(Dispatchers.Main.immediate) { progressOp = OperationProgress(context.getString(R.string.browser_op_extract), cur, tot) }
                        },
                        shouldCancel = { cancelFlag.isCancelled() }
                        )
                        }
                        // 延迟再关闭进度条，避免 setProgress 的 Main.immediate 晚于本行执行导致进度条不消失
                        delay(120)
                        progressOp = null
                        progressCancelAction = null
                        when (result) {
                        is UnzipResult.Success -> {
                        archiveOps.close()
                        Toast.makeText(context, context.getString(R.string.browser_extract_complete), Toast.LENGTH_SHORT).show()
                        browserTab.refresh()
                        }
                        is UnzipResult.Cancelled -> {
                        Toast.makeText(context, context.getString(R.string.browser_operation_cancelled), Toast.LENGTH_SHORT).show()
                        }
                        is UnzipResult.PasswordRequired -> {
                        // 需要密码但未提供，显示密码输入框让用户重试
                        archiveOps.markUnzipPasswordRequired()
                        Toast.makeText(context, context.getString(R.string.browser_enter_password), Toast.LENGTH_SHORT).show()
                        }
                        is UnzipResult.WrongPassword -> {
                        // 密码错误，保留对话框让用户重试
                        archiveOps.markUnzipPasswordRequired()
                        Toast.makeText(context, context.getString(R.string.browser_wrong_password_retry), Toast.LENGTH_SHORT).show()
                        }
                        is UnzipResult.CorruptedFile -> {
                        archiveOps.close()
                        Toast.makeText(context, context.getString(R.string.browser_extract_failed_corrupt), Toast.LENGTH_SHORT).show()
                        }
                        is UnzipResult.UnsupportedFormat -> {
                        archiveOps.close()
                        Toast.makeText(context, context.getString(R.string.browser_extract_failed_format), Toast.LENGTH_SHORT).show()
                        }
                        is UnzipResult.IOError -> {
                        archiveOps.close()
                        val msg = result.message ?: context.getString(R.string.common_unknown_error)
                        Toast.makeText(context, context.getString(R.string.browser_extract_failed_detail, msg), Toast.LENGTH_SHORT).show()
                        }
                        }
                    }
                }
                is ArchiveOperationRequest.CompressFile -> {
                    val target = req.target
                    val format = archiveOps.format
                    val archiveBaseName = if (target.name.contains(".")) target.name.substringBeforeLast(".") else target.name
                    val outputName = buildArchiveOutputName(archiveBaseName, format)
                    val pwd = archiveOps.password.ifBlank { null }?.toCharArray()
                    val itemUris = listOf(target.uri)
                    val parentDirUri = Uri.parse(displayUri)
                    val treeUri = browserTab.rootUri?.let { Uri.parse(normalizeContentUriString(it)) }
                    scope.launch {
                        val cancelFlag = OperationCancelFlag()
                        progressCancelAction = { cancelFlag.requestCancel() }
                        progressOp = OperationProgress(context.getString(R.string.browser_op_compress), 0, 1)
                        delay(50)
                        val ok = withContext(Dispatchers.IO) {
                        if (format.compressesSingleFileOnly) {
                        compressSingleFileToFormat(context, target.uri, parentDirUri, treeUri, outputName, format, { cur, tot ->
                        scope.launch(Dispatchers.Main.immediate) { progressOp = OperationProgress(context.getString(R.string.browser_op_compress), cur, tot) }
                        }, shouldCancel = { cancelFlag.isCancelled() })
                        } else {
                        compressArchiveItems(itemUris, parentDirUri, treeUri, archiveBaseName, format, pwd, { cur, tot ->
                        scope.launch(Dispatchers.Main.immediate) { progressOp = OperationProgress(context.getString(R.string.browser_op_compress), cur, tot) }
                        }, shouldCancel = { cancelFlag.isCancelled() })
                        }
                        }
                        delay(120)
                        progressOp = null
                        progressCancelAction = null
                        archiveOps.close()
                        if (ok) {
                        Toast.makeText(context, context.getString(R.string.browser_compress_complete), Toast.LENGTH_SHORT).show()
                        browserTab.refresh()
                        } else {
                        Toast.makeText(
                        context,
                        if (cancelFlag.isCancelled()) context.getString(R.string.browser_operation_cancelled) else context.getString(R.string.browser_compress_failed),
                        Toast.LENGTH_SHORT
                        ).show()
                        }
                    }
                }
                is ArchiveOperationRequest.CompressPending -> {
                    val items = req.items
                    val currentDirUri = Uri.parse(displayUri)
                    val rootTreeUri = browserTab.rootUri?.let { Uri.parse(normalizeContentUriString(it)) }
                    when (req.fixedFormat) {
                        null -> {
                            val format = archiveOps.format
                            val pwd = archiveOps.password.ifBlank { null }?.toCharArray()
                            val generatedPendingArchiveName = buildArchiveOutputName("archive", format)
                            scope.launch {
                        val cancelFlag = OperationCancelFlag()
                        progressCancelAction = { cancelFlag.requestCancel() }
                        progressOp = OperationProgress(context.getString(R.string.browser_op_compress), 0, items.size)
                        delay(50)
                        val ok = withContext(Dispatchers.IO) {
                        if (format.compressesSingleFileOnly) {
                        val allOk = items.all { item ->
                        compressSingleFileToFormat(context, item.uri, currentDirUri, rootTreeUri, buildArchiveOutputName(item.name, format), format, { cur, tot ->
                        scope.launch(Dispatchers.Main.immediate) { progressOp = OperationProgress(context.getString(R.string.browser_op_compress), cur, tot) }
                        }, shouldCancel = { cancelFlag.isCancelled() })
                        }
                        allOk
                        } else {
                        compressArchiveItems(items.map { it.uri }, currentDirUri, rootTreeUri, "archive", format, pwd, { cur, tot ->
                        scope.launch(Dispatchers.Main.immediate) { progressOp = OperationProgress(context.getString(R.string.browser_op_compress), cur, tot) }
                        }, shouldCancel = { cancelFlag.isCancelled() })
                        }
                        }
                        delay(120)
                        progressOp = null
                        progressCancelAction = null
                        archiveOps.close()
                        if (ok) {
                        finishPendingListAndReturn()
                        Toast.makeText(context, context.getString(R.string.browser_compress_done, generatedPendingArchiveName), Toast.LENGTH_SHORT).show()
                        browserTab.refresh()
                        } else {
                        Toast.makeText(
                        context,
                        if (cancelFlag.isCancelled()) context.getString(R.string.browser_operation_cancelled) else context.getString(R.string.browser_compress_failed),
                        Toast.LENGTH_SHORT
                        ).show()
                        }
                            }
                        }
                        ArchiveCompressionFormat.ZIP -> {
                            val zipName = archiveOps.pendingName.trim().let {
                                if (it.endsWith(".zip", ignoreCase = true)) it else "$it.zip"
                            }
                            val pwd = archiveOps.password.ifBlank { null }?.toCharArray()
                            scope.launch {
                        val cancelFlag = OperationCancelFlag()
                        progressCancelAction = { cancelFlag.requestCancel() }
                        progressOp = OperationProgress(context.getString(R.string.browser_op_compress), 0, items.size)
                        delay(50)
                        val ok = withContext(Dispatchers.IO) {
                        compressToZip(
                        context,
                        items.map { it.uri },
                        currentDirUri,
                        rootTreeUri,
                        zipName,
                        pwd,
                        setProgress = { cur, tot ->
                        scope.launch(Dispatchers.Main.immediate) {
                        progressOp = OperationProgress(context.getString(R.string.browser_op_compress), cur, tot)
                        }
                        },
                        shouldCancel = { cancelFlag.isCancelled() }
                        )
                        }
                        delay(120)
                        progressOp = null
                        progressCancelAction = null
                        archiveOps.close()
                        if (ok) {
                        finishPendingListAndReturn()
                        Toast.makeText(context, context.getString(R.string.browser_compress_done, zipName), Toast.LENGTH_SHORT).show()
                        browserTab.refresh()
                        } else {
                        Toast.makeText(
                        context,
                        if (cancelFlag.isCancelled()) context.getString(R.string.browser_operation_cancelled) else context.getString(R.string.browser_compress_failed),
                        Toast.LENGTH_SHORT
                        ).show()
                        }
                            }
                        }
                        ArchiveCompressionFormat.SEVEN_Z -> {
                            val sevenZName = archiveOps.pendingName.trim().let {
                                if (it.endsWith(".7z", ignoreCase = true)) it else "$it.7z"
                            }
                            val pwd = archiveOps.password.ifBlank { null }?.toCharArray()
                            scope.launch {
                        val cancelFlag = OperationCancelFlag()
                        progressCancelAction = { cancelFlag.requestCancel() }
                        progressOp = OperationProgress(context.getString(R.string.browser_op_compress), 0, items.size)
                        delay(50)
                        var timeout = false
                        val ok = withContext(Dispatchers.IO) {
                        val executor = Executors.newSingleThreadExecutor()
                        try {
                        val future = executor.submit<Boolean> {
                        compressTo7z(
                        context,
                        items.map { it.uri },
                        currentDirUri,
                        rootTreeUri,
                        sevenZName,
                        pwd,
                        setProgress = { cur, tot ->
                        scope.launch(Dispatchers.Main.immediate) {
                        progressOp = OperationProgress(context.getString(R.string.browser_op_compress), cur, tot)
                        }
                        },
                        shouldCancel = { cancelFlag.isCancelled() }
                        )
                        }
                        future.get(2, TimeUnit.MINUTES)
                        } catch (_: TimeoutException) {
                        timeout = true
                        false
                        } finally {
                        executor.shutdownNow()
                        }
                        }
                        delay(120)
                        progressOp = null
                        progressCancelAction = null
                        archiveOps.close()
                        if (ok) {
                        browserTab.finishPendingList()
                        Toast.makeText(context, context.getString(R.string.browser_compress_done, sevenZName), Toast.LENGTH_SHORT).show()
                        browserTab.refresh()
                        } else {
                        val message = when {
                        cancelFlag.isCancelled() -> context.getString(R.string.browser_operation_cancelled)
                        timeout -> context.getString(R.string.browser_compress_timeout)
                        else -> context.getString(R.string.browser_compress_failed)
                        }
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        }
                            }
                        }
                        else -> { }
                    }
                }
                null -> { }
            }
        }




        val ftpRootUri = browserTab.rootUri
        Column(Modifier.fillMaxSize()) {
            if (crypto.pubEncryptInProgress || saveInProgress) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(stringResource(R.string.viewer_saving), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(4.dp))
            }
            progressOp?.let { OperationProgressDialog(it, progressCancelAction) }
            Box(Modifier.weight(1f)) {
        val browserTabHostDependencies = BrowserTabHostDependencies(
            scope = scope,
            archiveReader = archiveReader,
            archiveOps = archiveOps,
            crypto = crypto,
            configImportExportWorkflow = configImportExportWorkflow,
            markdownViewerSessionCache = markdownViewerSessionCache,
            playbackState = playerTab.playbackState,
            initialDirUri = initialDirUri,
            cachedTrashUri = cachedTrashUri,
            finishPendingListAndReturn = finishPendingListAndReturn,
            setTransientRoute = { transientRoute = it },
            recordRecentOpen = { type, key, title, uri, playlistId -> recordRecentOpen(type, key, title, uri, playlistId) },
            onChangeRoot = { showRootSwitchDialog = true },
            onShowFileDetail = { showFileDetail = it },
            onQuickMask = { model ->
                quickMaskOp = model
                quickObfuscatePassword = ""
                quickObfuscatePasswordConfirm = ""
            },
            onShareToGit = { model -> shareFileToGitTarget = model },
            onForwardToBulletin = { payload -> bulletinForwardPayload = payload },
            onBatchMask = { list -> batchMaskOp = list },
            onAddToPlayback = { audioList -> playerTab.requestAddToPlayback(audioList) },
            onOpenQuickNote = { requestOpenQuickNoteTabFromDirectory() },
            requestOpenPlayerTab = { requestOpenPlayerTab() },
            runWithProgress = { label, total, block -> runWithProgress(label, total, block) },
            onSetProgressCancel = { action -> progressCancelAction = action },
        )
                MainTabContentHost(
                    activeMainTab = activeMainTab,
                    features = listOf(
            mainTabFeature(
                MainTab.DIRECTORY,
                { BrowserTabHost(state = browserTab, deps = browserTabHostDependencies) }
            ),
            mainTabFeature(MainTab.RECENT) {
                RecentTabRoute(
                    RecentTabRouteState(
                        items = recentOpenItems.take(30),
                        onOpenRecentItem = { item ->
                        openRecentItemByType(
                            context = context,
                            item = item,
                            currentPlaybackState = playerTab.playbackState,
                            recordRecentOpen = { type, key, title, uri, playlistId ->
                                recordRecentOpen(
                                    type = type,
                                    key = key,
                                    title = title,
                                    uri = uri,
                                    playlistId = playlistId
                                )
                            },
                            switchMainTab = { requestOpenMainTab(it) },
                            onMdZipTarget = { archiveReader.open(ArchiveReaderKind.MD_ZIP, it) },
                            onHtmlZipTarget = { archiveReader.open(ArchiveReaderKind.HTML_ZIP, it) },
                            onHtmlTarget = { transientRoute = htmlViewerRouteFromFile(it.uri.toString(), it.name) },
                            onHtmlUrlTarget = { url ->
                                transientRoute = htmlViewerRouteFromUrl(url)
                            },
                            onPicZipTarget = { archiveReader.open(ArchiveReaderKind.PIC_ZIP, it) },
                            onVidZipTarget = { archiveReader.open(ArchiveReaderKind.VID_ZIP, it) },
                            onLlmZipTarget = { archiveReader.open(ArchiveReaderKind.LLM_ZIP, it) },
                            onTxtTarget = { archiveReader.open(ArchiveReaderKind.TXT, it) },
                            onLlmTarget = { archiveReader.open(ArchiveReaderKind.LLM, it) },
                            onEpubTarget = { archiveReader.open(ArchiveReaderKind.EPUB, it) },
                            onPdfTarget = { transientRoute = TransientRoute.PdfViewer(it.uri.toString(), it.name) }
                        )
                    },
                        onDeleteRecentItem = { item ->
                        scope.launch {
                            prefs.removeRecentOpenItem(item.type, item.key)
                        }
                    },
                        onClearRecentItems = {
                        scope.launch {
                            prefs.clearRecentOpenItems()
                        }
                    }
                    )
                )
            },
            mainTabFeature(
                MainTab.FTP,
                {
                        FtpTabRoute(
                            FtpTabRouteState(
                                rootUri = ftpRootUri,
                                displayUri = displayUri,
                                ftpManager = ftpManager,
                                ftpPort = networkServiceConfig.ftpPort,
                                ftpPassword = networkServiceConfig.ftpPassword,
                                networkServiceTimeoutMinutes = networkServiceConfig.networkServiceTimeoutMinutes,
                                onRequestExitApp = { requestExitApp() }
                            )
                        )
                    }
            ),
            mainTabFeature(
                MainTab.LOCAL_SHARE,
                {
                        WebUiTabRoute(
                            WebUiTabRouteState(
                                rootUri = ftpRootUri,
                                displayUri = displayUri,
                                manager = localShareManager,
                                port = networkServiceConfig.ftpPort,
                                password = networkServiceConfig.ftpPassword,
                                networkServiceTimeoutMinutes = networkServiceConfig.networkServiceTimeoutMinutes,
                                onRequestExitApp = { requestExitApp() }
                            )
                        )
                    }
            ),
            mainTabFeature(
                MainTab.FAMILY_NETWORK,
                {
                        FamilyNetworkScreen(
                            manager = familyNetworkManager,
                            networkPassword = networkServiceConfig.ftpPassword,
                            localServiceEnabled = networkServiceConfig.localNetworkServiceEnabled,
                            familyNetworkUserName = networkServiceConfig.familyNetworkUserName,
                            familyNetworkAvatarId = networkServiceConfig.familyNetworkAvatarId,
                            familyNetworkHostName = networkServiceConfig.familyNetworkHostName,
                            timeoutMinutes = networkServiceConfig.networkServiceTimeoutMinutes,
                            rootUri = browserTab.rootUri ?: initialDirUri,
                            hideDotFiles = browserTab.hideDotFiles,
                            onDismiss = { requestExitApp() }
                        )
                    }
            ),
            mainTabFeature(
                MainTab.CONFIG,
                {
                        ConfigTabRoute(
                            ConfigTabRouteState(
                                filterVisible = browserTab.filterVisible,
                                onFilterVisibleChange = { scope.launch { prefs.setFilterVisible(it) } },
                                hideDotFiles = browserTab.hideDotFiles,
                                onHideDotFilesChange = { scope.launch { prefs.setHideDotFiles(it) } },
                                startupDecryptKey = startupDecryptKey,
                                onStartupDecryptKeyChange = { enabled ->
                                    scope.launch { prefs.setStartupDecryptKey(enabled) }
                                    if (!enabled) SecretKeyPasswordCache.clear()
                                },
                                viewerPreviewBytes = viewerPreviewBytes,
                                onViewerPreviewBytesChange = { scope.launch { prefs.setViewerPreviewBytes(it) } },
                                ftpPassword = networkServiceConfig.ftpPassword ?: "",
                                onFtpPasswordChange = { s -> networkServiceConfig.setFtpPassword(s.ifBlank { null }) },
                                networkServiceTimeoutMinutes = networkServiceConfig.networkServiceTimeoutMinutes,
                                onNetworkServiceTimeoutMinutesChange = { networkServiceConfig.setNetworkServiceTimeoutMinutes(it) },
                                localNetworkServiceEnabled = networkServiceConfig.localNetworkServiceEnabled,
                                onLocalNetworkServiceEnabledChange = { enabled -> networkServiceConfig.setLocalNetworkServiceEnabled(enabled) },
                                familyNetworkUserName = networkServiceConfig.familyNetworkUserName,
                                onFamilyNetworkUserNameChange = { name -> networkServiceConfig.setFamilyNetworkUserName(name) },
                                familyNetworkAvatarId = networkServiceConfig.familyNetworkAvatarId,
                                onFamilyNetworkAvatarIdChange = { avatarId -> networkServiceConfig.setFamilyNetworkAvatarId(avatarId) },
                                familyNetworkHostName = networkServiceConfig.familyNetworkHostName,
                                onFamilyNetworkHostNameChange = { name -> networkServiceConfig.setFamilyNetworkHostName(name) },
                                onOpenGitConfig = { showGitConfigDialog = true },
                                onManageKeys = { showKeyManagementDialog = true },
                                onOpenCacheManagement = { showCacheManagementDialog = true },
                                onExportConfig = { configImportExportWorkflow.openExportDialog() },
                                onCreatePlayerShortcut = {
                                    val playerErr = requestPinnedTabShortcut(context, SHORTCUT_TAB_PLAYER)
                                    if (playerErr == null) {
                                        Toast.makeText(context, context.getString(R.string.config_player_shortcut_requested), Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, context.getString(R.string.config_player_shortcut_failed, playerErr), Toast.LENGTH_LONG).show()
                                    }
                                },
                                onCreateQuickNoteShortcut = {
                                    val quickNoteErr = requestPinnedTabShortcut(context, SHORTCUT_TAB_QUICK_NOTE)
                                    if (quickNoteErr == null) {
                                        Toast.makeText(context, context.getString(R.string.config_quick_note_shortcut_requested), Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, context.getString(R.string.config_quick_note_shortcut_failed, quickNoteErr), Toast.LENGTH_LONG).show()
                                    }
                                }
                            )
                        )
                    }
            ),
            mainTabFeature(
                MainTab.GIT_SHARE,
                {
                        GitShareTabRoute(
                            GitShareTabRouteState(
                                prefs = prefs,
                                rootUri = browserTab.rootUri,
                                onRequestExitApp = { requestExitApp() }
                            )
                        )
                    }
            ),
            mainTabFeature(
                MainTab.GIT_PROJECTS,
                {
                        GitProjectsTabRoute(
                            GitProjectsTabRouteState(
                                prefs = prefs,
                                rootUri = browserTab.rootUri
                            )
                        )
                    }
            ),
            mainTabFeature(
                MainTab.BOOK_NOTE,
                {
                        BookNoteTabRoute(controller = bookNoteController)
                    }
            ),
            mainTabFeature(
                MainTab.QUICK_NOTE,
                {
                        QuickNoteTabRoute(prefs = prefs, controller = quickNoteController)
                    }
            ),
            mainTabFeature(
                MainTab.QUICK_CRYPTO,
                {
                        QuickCryptoTabRoute()
                    }
            ),
            mainTabFeature(
                MainTab.DICTIONARY,
                {
                        DictionaryTabRoute(
                            DictionaryTabRouteState(
                                onRequestExitApp = { requestExitApp() }
                            )
                        )
                    }
            ),
            mainTabFeature(
                MainTab.ABOUT,
                {
                        AboutTabRoute()
                    }
            ),
            playerTab
                    )
                )
            

            ScrollableMainTabBar(
                activeMainTab = activeMainTab,
                onSwitchMainTab = { requestOpenMainTab(it) }
            )
        }
        bulletinForwardPayload?.let { payload ->
            BulletinForwardTargetFlow(
                manager = familyNetworkManager,
                payload = payload,
                localServiceEnabled = networkServiceConfig.localNetworkServiceEnabled,
                keepEphemeralSession = activeMainTab != MainTab.FAMILY_NETWORK,
                onDismiss = { bulletinForwardPayload = null },
                onComplete = {
                    finishPendingListAndReturn()
                    bulletinForwardPayload = null
                }
            )
        }
        // Config 域：配置导入 / 导出工作流对话框（状态与逻辑在 ConfigImportExportWorkflowState 中）
        ConfigImportExportDialogs(state = configImportExportWorkflow)
        if (showCacheManagementDialog) {
            CacheManagementDialog(context = context, onDismiss = { showCacheManagementDialog = false })
        }
        if (showRootSwitchDialog) {
            AlertDialog(
                onDismissRequest = { showRootSwitchDialog = false },
                title = { Text(context.getString(R.string.root_switch_title)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(context.getString(R.string.root_switch_hint))
                        if (recentRootUris.isEmpty()) {
                            Text(context.getString(R.string.root_switch_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            recentRootUris.forEach { recentUri ->
                                val displayName = humanReadableRootUri(recentUri)
                                OutlinedButton(
                                    onClick = {
                                        val normalized = normalizeContentUriString(recentUri)
                                        val previousRoot = browserTab.rootUri?.let { normalizeContentUriString(it) }
                                        showRootSwitchDialog = false
                                        if (previousRoot == normalized) return@OutlinedButton
                                        scope.launch {
                                            prefs.recordRecentRootSwitch(previousRoot, normalized)
                                            prefs.setRootUri(normalized)
                                        }
                                        browserTab.changeRoot(normalized)
                                        initialDirUri = normalized
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        displayName,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        showRootSwitchDialog = false
                        val r = browserTab.rootUri
                        if (r == null) {
                            treeLauncher.launch(null)
                            return@Button
                        }
                        scope.launch {
                            val count = withContext(Dispatchers.IO) {
                                cachedTrashUri?.let { trash ->
                                    val doc = if (trash.toString().contains("/tree/")) {
                                        DocumentFile.fromTreeUri(context, trash)
                                    } else {
                                        DocumentFile.fromSingleUri(context, trash)
                                    }
                                    doc?.listFilesSafe()?.size ?: 0
                                } ?: 0
                            }
                            withContext(Dispatchers.Main.immediate) {
                                if (count > 0) showChangeRootConfirm = true
                                else treeLauncher.launch(null)
                            }
                        }
                    }) { Text(stringResource(R.string.browser_select_new_root)) }
                },
                dismissButton = {
                    TextButton(onClick = { showRootSwitchDialog = false }) { Text(stringResource(R.string.common_cancel)) }
                }
            )
        }
        if (showGitConfigDialog) {
            GitConfigDialog(
                prefs = prefs,
                rootUri = browserTab.rootUri?.let { normalizeContentUriString(it) },
                onDismiss = {
                    showGitConfigDialog = false
                    browserTab.refresh()
                }
            )
        }
        if (showKeyManagementDialog) {
            KeyManagementDialog(
                context = context,
                onDismiss = { showKeyManagementDialog = false },
                onKeysChanged = { browserTab.refresh() },
                onOpenPubkeyShare = {
                    showKeyManagementDialog = false
                    showPubkeyShareScreen = true
                }
            )
        }
        if (showPubkeyShareScreen) {
            PubkeyShareScreen(
                prefs = prefs,
                rootUri = browserTab.rootUri?.let { normalizeContentUriString(it) },
                onDismiss = {
                    showPubkeyShareScreen = false
                    browserTab.refresh()
                }
            )
        }
        // 共享文件到 Git
        shareFileToGitTarget?.let { model ->
            AlertDialog(
                onDismissRequest = { shareFileToGitTarget = null },
                title = { Text(stringResource(R.string.browser_share_to_git_title)) },
                text = { Text(stringResource(R.string.browser_share_to_git_confirm, model.name)) },
                confirmButton = {
                    TextButton(onClick = {
                        val target = model
                        shareFileToGitTarget = null
                        val r = browserTab.rootUri?.let { normalizeContentUriString(it) }
                        if (r == null) {
                            Toast.makeText(context, context.getString(R.string.common_select_root_first), Toast.LENGTH_SHORT).show()
                            return@TextButton
                        }
                        shareGitLogs.clear()
                        shareGitInProgress = true
                        shareGitDone = false
                        scope.launch {
                            val repoUrl = prefs.gitRepoUrl.first() ?: ""
                            val userName = prefs.gitUserName.first() ?: ""
                            val httpsPassword = prefs.gitHttpsPassword.first() ?: ""
                            if (repoUrl.isBlank()) {
                                shareGitLogs.add(context.getString(R.string.browser_share_git_error_configure))
                                shareGitInProgress = false
                                shareGitDone = true
                                return@launch
                            }
                            // 同步
                            shareGitLogs.add(context.getString(R.string.browser_share_git_syncing))
                            val syncResult = withContext(Dispatchers.IO) {
                                cloneToTree(context, r, repoUrl,
                                    userName = userName.ifBlank { null },
                                    httpsPassword = httpsPassword.ifBlank { null },
                                    log = { msg -> shareGitLogs.add(msg) })
                            }
                            if (syncResult.isFailure) {
                                shareGitLogs.add(context.getString(R.string.browser_share_git_error_sync, syncResult.exceptionOrNull()?.message ?: ""))
                                shareGitInProgress = false
                                shareGitDone = true
                                return@launch
                            }
                            // 复制到 share
                            shareGitLogs.add(context.getString(R.string.browser_share_git_copying))
                            val copied = withContext(Dispatchers.IO) {
                                copyFileToShare(context, r, target.uri, target.name)
                            }
                            if (!copied) {
                                shareGitLogs.add(context.getString(R.string.browser_share_git_error_copy))
                                shareGitInProgress = false
                                shareGitDone = true
                                return@launch
                            }
                            shareGitLogs.add(context.getString(R.string.browser_share_git_copied))
                            // 提交推送
                            shareGitLogs.add(context.getString(R.string.browser_git_committing_pushing))
                            val pushResult = withContext(Dispatchers.IO) {
                                commitAndPush(context, r, repoUrl,
                                    commitMessage = context.getString(R.string.browser_share_git_commit_message, target.name),
                                    userName = userName.ifBlank { null },
                                    httpsPassword = httpsPassword.ifBlank { null },
                                    log = { msg -> shareGitLogs.add(msg) })
                            }
                            if (pushResult.isSuccess) {
                                shareGitLogs.add(context.getString(R.string.browser_share_git_success))
                            } else {
                                shareGitLogs.add(context.getString(R.string.browser_share_git_error_push, pushResult.exceptionOrNull()?.message ?: ""))
                            }
                            shareGitInProgress = false
                            shareGitDone = true
                        }
                    }) { Text(stringResource(R.string.browser_share_action)) }
                },
                dismissButton = {
                    TextButton(onClick = { shareFileToGitTarget = null }) { Text(stringResource(R.string.common_cancel)) }
                }
            )
        }
        // 共享到 Git 日志窗口
        if (shareGitLogs.isNotEmpty() && (shareGitInProgress || shareGitDone)) {
            AlertDialog(
                onDismissRequest = { if (!shareGitInProgress) { shareGitDone = false; shareGitLogs.clear() } },
                title = { Text(stringResource(R.string.browser_share_to_git_title)) },
                text = {
                    val logScrollState = rememberScrollState()
                    LaunchedEffect(shareGitLogs.size) {
                        logScrollState.animateScrollTo(logScrollState.maxValue)
                    }
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                            .verticalScroll(logScrollState)
                    ) {
                        for (line in shareGitLogs) {
                            Text(
                                line,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (line.startsWith(context.getString(R.string.browser_error_prefix)) || line.startsWith(context.getString(R.string.browser_debug_prefix)))
                                    MaterialTheme.colorScheme.error
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                confirmButton = {
                    if (shareGitInProgress) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else {
                        TextButton(onClick = { shareGitDone = false; shareGitLogs.clear() }) { Text(stringResource(R.string.common_close)) }
                    }
                }
            )
        }
        quickMaskOp?.let { model ->
            QuickObfuscatePasswordDialog(
                isMasked = isQuickObfuscatedFileName(model.name),
                fileName = model.name,
                password = quickObfuscatePassword,
                confirmPassword = quickObfuscatePasswordConfirm,
                inProgress = quickObfuscateInProgress,
                onPasswordChange = { quickObfuscatePassword = it },
                onConfirmPasswordChange = { quickObfuscatePasswordConfirm = it },
                onConfirm = { pwd ->
                    quickObfuscateInProgress = true
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) {
                            quickToggleObfuscation(context, model.uri, pwd.toCharArray())
                        }
                        quickObfuscateInProgress = false
                        quickMaskOp = null
                        quickObfuscatePassword = ""
                        quickObfuscatePasswordConfirm = ""
                        if (ok) {
                            Toast.makeText(context, context.getString(R.string.browser_mask_done, model.name), Toast.LENGTH_SHORT).show()
                            browserTab.refresh()
                        } else {
                            Toast.makeText(context, context.getString(R.string.browser_mask_failed, model.name), Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onDismiss = {
                    quickMaskOp = null
                    quickObfuscatePassword = ""
                    quickObfuscatePasswordConfirm = ""
                }
            )
        }
        batchMaskOp?.let { list ->
            QuickObfuscatePasswordDialog(
                isMasked = list.all { isQuickObfuscatedFileName(it.name) },
                fileName = context.getString(R.string.browser_file_count, list.size),
                password = batchObfuscatePassword,
                confirmPassword = batchObfuscatePasswordConfirm,
                inProgress = batchObfuscateInProgress,
                onPasswordChange = { batchObfuscatePassword = it },
                onConfirmPasswordChange = { batchObfuscatePasswordConfirm = it },
                onConfirm = { pwd ->
                    batchObfuscateInProgress = true
                    scope.launch {
                        runWithProgress(context.getString(R.string.pending_list_action_mask), list.size) { setProgress ->
                            withContext(Dispatchers.IO) {
                                list.forEachIndexed { index, model ->
                                    quickToggleObfuscation(context, model.uri, pwd.toCharArray())
                                    withContext(Dispatchers.Main.immediate) { setProgress(index + 1) }
                                }
                            }
                        }
                        batchObfuscateInProgress = false
                        batchMaskOp = null
                        batchObfuscatePassword = ""
                        batchObfuscatePasswordConfirm = ""
                        Toast.makeText(context, context.getString(R.string.browser_masked_count, list.size), Toast.LENGTH_SHORT).show()
                        finishPendingListAndReturn()
                        browserTab.refresh()
                    }
                },
                onDismiss = {
                    batchMaskOp = null
                    batchObfuscatePassword = ""
                    batchObfuscatePasswordConfirm = ""
                }
            )
        }
        // Crypto 域对话框：.pass 保护/查看/编辑与 GPG 加解密向导（状态在 CryptoWorkflowState，执行编排经宿主注入）
        CryptoDialogs(
            workflow = crypto,
            scope = scope,
            rootUri = browserTab.rootUri,
            currentDirUri = displayUri,
            runWithProgress = { label, total, block -> runWithProgress(label, total, block) },
            onRefresh = { browserTab.refresh() },
            onBatchFinished = { finishPendingListAndReturn() }
        )

        TabPasswordPromptDialog(
            state = quickNoteController.passwordPromptState,
            fileName = QUICK_NOTE_GPG_FILE_NAME
        )
        TabPasswordPromptDialog(
            state = bookNoteController.passwordPromptState,
            fileName = QUICK_NOTE_GPG_FILE_NAME
        )
        // Archive 阅读器域：密码 / 进度 / 解析错误对话框（状态与流程在 ArchiveReaderWorkflowState）
        ArchiveReaderDialogs(archiveReader)
        // Archive 操作向导域：解压 / 压缩表单对话框（确认后由宿主执行文件操作）
        ArchiveOperationDialogs(
            workflow = archiveOps,
            onConfirm = { executeArchiveOperation() }
        )

        if (showChangeRootConfirm && browserTab.rootUri != null) {
            val r = browserTab.rootUri!!
            val root = Uri.parse(normalizeContentUriString(r))
            AlertDialog(
                onDismissRequest = { showChangeRootConfirm = false },
                title = { Text(stringResource(R.string.browser_change_root_title)) },
                text = { Text(stringResource(R.string.browser_change_root_trash_prompt)) },
                confirmButton = {
                    Button(onClick = {
                        scope.launch {
                            showChangeRootConfirm = false
                            runWithProgress(context.getString(R.string.main_menu_empty_trash), null) { _ ->
                                val ok = withContext(Dispatchers.IO) { emptyTrash(context, root, root) }
                                if (ok) Toast.makeText(context, context.getString(R.string.browser_trash_emptied), Toast.LENGTH_SHORT).show()
                                treeLauncher.launch(null)
                                browserTab.refresh()
                            }
                        }
                    }) { Text(stringResource(R.string.browser_empty_then_change)) }
                },
                dismissButton = {
                    Row {
                        TextButton(onClick = {
                            showChangeRootConfirm = false
                            treeLauncher.launch(null)
                        }) { Text(stringResource(R.string.browser_change_directly)) }
                        TextButton(onClick = { showChangeRootConfirm = false }) { Text(stringResource(R.string.common_cancel)) }
                    }
                }
            )
        }
    }
    when {
        showUnlockGate -> {
            BackHandler { (context as? Activity)?.finish() }
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                var unlockPwd by remember { mutableStateOf("") }
                var unlockInProgress by remember { mutableStateOf(false) }
                var unlockError by remember { mutableStateOf<String?>(null) }
                Column(
                    modifier = Modifier.widthIn(max = 320.dp).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(
                        stringResource(R.string.browser_startup_decrypt_enabled),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        stringResource(R.string.browser_enter_private_key_unlock),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    ReliablePasswordInputField(
                        value = unlockPwd,
                        onValueChange = { unlockPwd = it; unlockError = null },
                        label = { Text(stringResource(R.string.browser_private_key_password_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !unlockInProgress
                    )
                    unlockError?.let { err ->
                        Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    Button(
                        onClick = {
                            if (unlockPwd.isEmpty() || unlockInProgress) return@Button
                            unlockInProgress = true
                            unlockError = null
                            scope.launch {
                                val ok = withContext(Dispatchers.IO) {
                                    GpgHelper.tryUnlockSecretKey(context, unlockPwd.toCharArray())
                                }
                                unlockInProgress = false
                                if (ok) {
                                    SecretKeyPasswordCache.set(unlockPwd.toCharArray())
                                    unlockedByStartup = true
                                } else {
                                    unlockError = context.getString(R.string.browser_password_wrong_or_decrypt_failed)
                                }
                            }
                        },
                        enabled = unlockPwd.isNotEmpty() && !unlockInProgress,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (unlockInProgress) {
                            CircularProgressIndicator(Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Text(stringResource(R.string.browser_unlock_action))
                        }
                    }
                }
            }
        }
        browserTab.rootUri == null && initialDirUri == null && showOverwriteConfirm == null -> {
            var lastBackPressTime by remember { mutableStateOf(0L) }
            BackHandler {
                val now = System.currentTimeMillis()
                if (now - lastBackPressTime < 2000) (context as? Activity)?.finish()
                else {
                    lastBackPressTime = now
                    Toast.makeText(context, context.getString(R.string.browser_press_again_to_exit), Toast.LENGTH_SHORT).show()
                }
            }
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (pendingSaveFileUri != null) stringResource(R.string.browser_external_open_save_prompt) else stringResource(R.string.browser_select_root_prompt),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { treeLauncher.launch(null) }) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, Modifier.size(20.dp))
                        Spacer(Modifier.size(8.dp))
                        Text(if (pendingSaveFileUri != null) stringResource(R.string.browser_select_save_location) else stringResource(R.string.browser_select_root_button))
                    }
                }
            }
        }
        transientRoute != null -> {
            TransientViewerRouteHost(
                route = transientRoute!!,
                prefs = prefs,
                markdownViewerSessionCache = markdownViewerSessionCache,
                bookNoteController = bookNoteController,
                recordRecentOpen = { type, key, title, uri, playlistId ->
                    recordRecentOpen(
                        type = type,
                        key = key,
                        title = title,
                        uri = uri,
                        playlistId = playlistId
                    )
                },
                onRouteChange = { transientRoute = it }
            )
        }
        archiveReader.viewer != null -> {
            // Archive 阅读器域：解压完成后的全屏查看器（渲染与退出善后在 ArchiveViewerRouteHost）
            ArchiveViewerRouteHost(
                workflow = archiveReader,
                markdownViewerSessionCache = markdownViewerSessionCache,
                bookNoteController = bookNoteController,
                recordRecentOpen = { type, key, title, uri, playlistId ->
                    recordRecentOpen(
                        type = type,
                        key = key,
                        title = title,
                        uri = uri,
                        playlistId = playlistId
                    )
                },
                onSavePicZipImage = { sourceFile, fileName ->
                    savePicZipImageToRoot(sourceFile, fileName)
                },
                onEncryptedExitRequest = { encryptedCacheExitDialog = it }
            )
        }
        crypto.passContent != null -> {
            val content = crypto.passContent!!
            BackHandler { crypto.closePassContent() }
            PassContentViewerScreen(
                innerFileName = content.innerFileName,
                decryptedBytes = content.decryptedBytes,
                onBack = { crypto.closePassContent() }
            )
        }
        crypto.passEdit != null -> {
            val state = crypto.passEdit!!
            BackHandler { crypto.closePassEdit() }
            PassEditScreen(
                fileName = state.innerName,
                initialDecryptedBytes = state.decryptedBytes,
                onSave = { newBytes ->
                    val ctx = context
                    val fileModel = state.fileModel
                    val dirUri = state.dirUri
                    val treeUri = state.treeUri
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) {
                            val secRings = loadSecretKeyRings(ctx) ?: return@withContext false
                            val defaultKeyId = secRings.iterator().asSequence().firstOrNull()?.publicKey?.keyID ?: return@withContext false
                            val pubRings = loadPublicKeyRings(ctx) ?: return@withContext false
                            val pubKeyRing = findPublicKeyRing(pubRings, defaultKeyId) ?: return@withContext false
                            val encrypted = GpgHelper.encryptWithPublicKey(newBytes, pubKeyRing, fileModel.name) ?: return@withContext false
                            try { ctx.contentResolver.deleteDocument(fileModel.uri) } catch (_: Exception) { }
                            createFileWithBytes(ctx, Uri.parse(dirUri), treeUri, fileModel.name, "application/octet-stream", encrypted)
                        }
                        crypto.closePassEdit()
                        browserTab.refresh()
                        Toast.makeText(ctx, if (ok) ctx.getString(R.string.browser_saved_and_reencrypted) else ctx.getString(R.string.viewer_save_failed), Toast.LENGTH_SHORT).show()
                    }
                },
                onBack = { crypto.closePassEdit() }
            )
        }
        else -> MainTabsRootContent()
    }
    // Archive 阅读器域：加密缓存退出确认（渲染在顶层，确保全屏查看器激活时也能弹出）
    encryptedCacheExitDialog?.let { dialog ->
        AlertDialog(
            onDismissRequest = { encryptedCacheExitDialog = null },
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
                    encryptedCacheExitDialog = null
                }) { Text(stringResource(R.string.browser_delete_cache_and_exit)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    dialog.onKeep()
                    encryptedCacheExitDialog = null
                }) { Text(stringResource(R.string.browser_keep_cache_and_exit)) }
            }
        )
    }
    showOverwriteConfirm?.let { (sourceUriStr, fileName) ->
        val targetRoot = browserTab.rootUri?.let { normalizeContentUriString(it) } ?: return@let
        AlertDialog(
            onDismissRequest = {
                showOverwriteConfirm = null
                pendingSaveFileUri = null
                initialDirUri = targetRoot
            },
            title = { Text(stringResource(R.string.browser_file_exists_title)) },
            text = { Text(stringResource(R.string.browser_file_exists_overwrite, fileName)) },
            confirmButton = {
                Button(onClick = {
                    val ctx = context
                    scope.launch {
                        saveInProgress = true
                        val targetUri = Uri.parse(targetRoot)
                        val existingUri = findChildByName(ctx, targetUri, fileName)
                        if (existingUri != null) ctx.contentResolver.deleteDocument(existingUri)
                        val copied = withContext(Dispatchers.IO) {
                            copyDocumentTo(ctx, Uri.parse(sourceUriStr), targetUri, targetUri)
                        }
                        saveInProgress = false
                        showOverwriteConfirm = null
                        pendingSaveFileUri = null
                        if (copied != null) {
                            val dir = normalizeContentUriString(targetRoot)
                            initialDirUri = dir
                            browserTab.navigateTo(dir)
                            saveCompletedToken++
                            Toast.makeText(ctx, ctx.getString(R.string.browser_overwrite_save), Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(ctx, ctx.getString(R.string.viewer_save_failed), Toast.LENGTH_SHORT).show()
                        }
                    }
                }) { Text(stringResource(R.string.file_share_overwrite_action)) }
            },
                dismissButton = {
                    TextButton(onClick = {
                        showOverwriteConfirm = null
                        pendingSaveFileUri = null
                        initialDirUri = normalizeContentUriString(targetRoot)
                    }) { Text(stringResource(R.string.browser_do_not_overwrite)) }
            }
        )
    }

}
}

/** 统一进度：label 文案，total 为 null 表示不定型进度，否则为 X/total 项 */
private data class OperationProgress(val label: String, val current: Int = 0, val total: Int? = null)

internal class OperationCancelFlag {
    @Volatile
    private var cancelled: Boolean = false

    fun requestCancel() {
        cancelled = true
    }

    fun isCancelled(): Boolean = cancelled
}

@Composable
private fun OperationProgressDialog(progress: OperationProgress, onCancel: (() -> Unit)? = null) {
    Dialog(onDismissRequest = { }) {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, tonalElevation = 6.dp) {
            Column(Modifier.padding(24.dp)) {
                Text(stringResource(R.string.browser_progress_in_progress, progress.label), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(16.dp))
                if (progress.total != null && progress.total > 0) {
                    val frac = (progress.current.toFloat() / progress.total).coerceIn(0f, 1f)
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), progress = { frac })
                    Spacer(Modifier.height(8.dp))
                    val progressText = if (progress.total >= 1000) {
                        String.format(Locale.getDefault(), "%.1f%%", frac * 100f)
                    } else {
                        stringResource(R.string.browser_progress_items, progress.current, progress.total ?: 0)
                    }
                    Text(progressText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.browser_processing), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (onCancel != null) {
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onCancel) {
                            Text(stringResource(R.string.common_cancel))
                        }
                    }
                }
            }
        }
    }
}

/** 密码保护解密后的内容，暂存于内存中供渲染。 */
/** 判断文件名是否为压缩 Markdown/RST 文件（.md.zip 或 .rst.zip）。 */
private fun isCompressedMarkdown(name: String): Boolean =
    name.endsWith(".md.zip", ignoreCase = true) || name.endsWith(".rst.zip", ignoreCase = true)


/** 判断文件名是否为压缩 HTML 文件（.html.zip）。 */
private fun isCompressedHtml(name: String): Boolean =
    name.endsWith(".html.zip", ignoreCase = true)

/** 判断文件名是否为压缩 LLM 包（.llm.zip）。 */
private fun isCompressedLlmZip(name: String): Boolean =
    name.endsWith(".llm.zip", ignoreCase = true)

/** 判断文件名是否为 .pic.zip 图片压缩包。 */
private fun isPicZip(name: String): Boolean =
    name.endsWith(".pic.zip", ignoreCase = true)

/** 判断文件名是否为 .vid.zip 视频压缩包。 */
internal fun isVidZip(name: String): Boolean =
    name.endsWith(".vid.zip", ignoreCase = true)

/** 判断文件名是否为可解压压缩包（zip / rar / 7z / tar 及常见压缩格式）。 */
internal fun isExtractableArchive(name: String): Boolean =
    (name.endsWith(".zip", ignoreCase = true) && !isPicZip(name) && !isVidZip(name)) ||
        name.endsWith(".rar", ignoreCase = true) ||
        name.endsWith(".7z", ignoreCase = true) ||
        sevenZSplitPartRegex.matches(name) ||
        name.endsWith(".tar", ignoreCase = true) ||
        name.endsWith(".tar.gz", ignoreCase = true) ||
        name.endsWith(".tgz", ignoreCase = true) ||
        name.endsWith(".tar.bz2", ignoreCase = true) ||
        name.endsWith(".tbz2", ignoreCase = true) ||
        name.endsWith(".tar.xz", ignoreCase = true) ||
        name.endsWith(".txz", ignoreCase = true) ||
        (name.endsWith(".gz", ignoreCase = true) &&
            !name.endsWith(".tar.gz", ignoreCase = true) &&
            !name.endsWith(".tgz", ignoreCase = true)) ||
        (name.endsWith(".bz2", ignoreCase = true) &&
            !name.endsWith(".tar.bz2", ignoreCase = true) &&
            !name.endsWith(".tbz2", ignoreCase = true)) ||
        (name.endsWith(".xz", ignoreCase = true) &&
            !name.endsWith(".tar.xz", ignoreCase = true) &&
            !name.endsWith(".txz", ignoreCase = true))

/** 判断文件名是否为 EPUB 文件。 */
private fun isEpubFile(name: String): Boolean =
    name.endsWith(".epub", ignoreCase = true)

/** 判断文件名是否为 TXT 文件。 */
private fun isTxtFile(name: String): Boolean =
    name.endsWith(".txt", ignoreCase = true)

/** 判断文件名是否为 LLM 对话文件。 */
private fun isLlmFile(name: String): Boolean =
    name.endsWith(".llm", ignoreCase = true)

/** 判断文件名是否为 PDF 文件。 */
private fun isPdfFile(name: String): Boolean =
    name.endsWith(".pdf", ignoreCase = true)

private fun getCompressedPayloadExtension(name: String): String? {
    if (!name.endsWith(".zip", ignoreCase = true)) return null
    val withoutZip = name.dropLast(4)
    val dotIndex = withoutZip.lastIndexOf('.')
    if (dotIndex <= 0 || dotIndex >= withoutZip.length - 1) return null
    return withoutZip.substring(dotIndex + 1)
}

/** 判断文件名是否为图片文件（目录图库查看用，包含 SVG）。 */
internal fun isGalleryImageFile(name: String): Boolean =
    name.endsWith(".jpg", ignoreCase = true) ||
        name.endsWith(".jpeg", ignoreCase = true) ||
        name.endsWith(".png", ignoreCase = true) ||
        name.endsWith(".gif", ignoreCase = true) ||
        name.endsWith(".webp", ignoreCase = true) ||
        name.endsWith(".bmp", ignoreCase = true) ||
        name.endsWith(".svg", ignoreCase = true)

/** 判断文件名是否为 HTML 文件。 */
internal fun isHtmlFile(name: String): Boolean =
    name.endsWith(".html", ignoreCase = true) || name.endsWith(".htm", ignoreCase = true)

/** 直接编辑 .pass 时的状态：解密后的文件信息，用于编辑界面和存盘时重加密。 */
private sealed class GpgMethod {
    object Symmetric : GpgMethod()
    object SecretKeyDec : GpgMethod()
}

internal data class CachedDir(val items: List<DocumentFileModel>)

private enum class FileSortOrder(@androidx.annotation.StringRes val labelRes: Int) {
    NAME(R.string.browser_name_field),
    TIME(R.string.browser_modified_field),
    SIZE(R.string.browser_size_field)
}

private fun fileListComparator(sortOrder: FileSortOrder, ascending: Boolean): Comparator<DocumentFileModel> {
    val dirFirst = compareBy<DocumentFileModel> { !it.isDirectory }
    val nameThen = compareBy<DocumentFileModel> { it.name.lowercase() }
    return when (sortOrder) {
        FileSortOrder.NAME -> dirFirst.then(if (ascending) nameThen else nameThen.reversed())
        FileSortOrder.TIME -> dirFirst.then(
            if (ascending) compareBy { it.lastModified } else compareByDescending { it.lastModified }
        ).then(nameThen)
        FileSortOrder.SIZE -> dirFirst.then(
            if (ascending) compareBy { it.size } else compareByDescending { it.size }
        ).then(nameThen)
    }
}

private fun parseFileSearchSize(context: Context, raw: String): Long? {
    val normalized = raw.trim()
    if (normalized.isEmpty()) return null
    val match = Regex("""^([0-9]+(?:\.[0-9]+)?)\s*([kmgt]?b?)?$""", RegexOption.IGNORE_CASE).matchEntire(normalized)
        ?: throw IllegalArgumentException(context.getString(R.string.browser_size_format_invalid, raw))
    val value = match.groupValues[1].toDoubleOrNull()
        ?: throw IllegalArgumentException(context.getString(R.string.browser_size_invalid, raw))
    val unit = match.groupValues[2].lowercase(Locale.getDefault())
    val multiplier = when (unit) {
        "", "b" -> 1.0
        "k", "kb" -> 1024.0
        "m", "mb" -> 1024.0 * 1024
        "g", "gb" -> 1024.0 * 1024 * 1024
        "t", "tb" -> 1024.0 * 1024 * 1024 * 1024
        else -> throw IllegalArgumentException(context.getString(R.string.browser_size_unit_unsupported, unit))
    }
    val bytes = value * multiplier
    if (!bytes.isFinite() || bytes < 0.0) {
        throw IllegalArgumentException(context.getString(R.string.browser_size_must_non_negative, raw))
    }
    return bytes.toLong()
}

private fun parseFileSearchDate(context: Context, raw: String, endOfDay: Boolean): Long? {
    val normalized = raw.trim()
    if (normalized.isEmpty()) return null
    val parser = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply { isLenient = false }
    val parsed = parser.parse(normalized)
        ?: throw IllegalArgumentException(context.getString(R.string.browser_date_format_invalid, raw))
    if (!endOfDay) return parsed.time
    return Calendar.getInstance().apply {
        time = parsed
        set(Calendar.HOUR_OF_DAY, 23)
        set(Calendar.MINUTE, 59)
        set(Calendar.SECOND, 59)
        set(Calendar.MILLISECOND, 999)
    }.timeInMillis
}

@Composable
private fun FileSearchDialog(
    namePattern: String,
    minSize: String,
    maxSize: String,
    modifiedAfter: String,
    modifiedBefore: String,
    searching: Boolean,
    error: String?,
    onNamePatternChange: (String) -> Unit,
    onMinSizeChange: (String) -> Unit,
    onMaxSizeChange: (String) -> Unit,
    onModifiedAfterChange: (String) -> Unit,
    onModifiedBeforeChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(stringResource(R.string.browser_file_search_title), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = namePattern,
                    onValueChange = onNamePatternChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.browser_filename_regex_label)) },
                    placeholder = { Text(stringResource(R.string.browser_regex_example)) },
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = minSize,
                    onValueChange = onMinSizeChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.browser_min_size_label)) },
                    placeholder = { Text(stringResource(R.string.browser_size_example_mb)) },
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = maxSize,
                    onValueChange = onMaxSizeChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.browser_max_size_label)) },
                    placeholder = { Text(stringResource(R.string.browser_size_example_large)) },
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = modifiedAfter,
                    onValueChange = onModifiedAfterChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.browser_min_mtime_label)) },
                    placeholder = { Text("yyyy-MM-dd") },
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = modifiedBefore,
                    onValueChange = onModifiedBeforeChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.browser_max_mtime_label)) },
                    placeholder = { Text("yyyy-MM-dd") },
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.browser_recursive_search_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    SelectionContainer {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onClear, enabled = !searching) { Text(stringResource(R.string.browser_clear_filter)) }
                    TextButton(onClick = onDismiss, enabled = !searching) { Text(stringResource(R.string.common_close)) }
                    TextButton(onClick = onSearch, enabled = !searching) {
                        Text(if (searching) stringResource(R.string.browser_search_in_progress) else stringResource(R.string.browser_start_search))
                    }
                }
            }
        }
    }
}

@Composable
private fun FileSearchResultsDialog(
    results: List<RecursiveFileSearchHit>,
    pendingUris: Set<String>,
    onAddAll: () -> Unit,
    onAddOne: (DocumentFileModel) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val timeFormatter = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .heightIn(max = 640.dp)
            ) {
                Text(stringResource(R.string.browser_search_results, results.size), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onAddAll, enabled = results.any { it.model.uri.toString() !in pendingUris }) {
                        Text(stringResource(R.string.browser_add_all_to_pending))
                    }
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) }
                }
                Spacer(Modifier.height(8.dp))
                if (results.isEmpty()) {
                    Text(stringResource(R.string.browser_no_matching_files_dot), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(results, key = { it.model.uri.toString() }) { item ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(item.model.name, style = MaterialTheme.typography.titleSmall)
                                    Spacer(Modifier.height(4.dp))
                                    SelectionContainer {
                                        Text(
                                            item.relativePath,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        context.getString(R.string.browser_search_result_size_modified, item.model.displaySize.ifBlank { context.getString(R.string.browser_unknown) }, timeFormatter.format(Date(item.model.lastModified))),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                        TextButton(
                                            onClick = { onAddOne(item.model) },
                                            enabled = item.model.uri.toString() !in pendingUris
                                        ) {
                                            Text(if (item.model.uri.toString() in pendingUris) stringResource(R.string.browser_already_in_pending) else stringResource(R.string.browser_add_to_pending))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FileBrowserScreen(
    modifier: Modifier = Modifier,
    currentUri: String,
    refreshTrigger: Int,
    dirCache: Map<String, CachedDir>,
    onCacheDir: (uri: String, items: List<DocumentFileModel>) -> Unit,
    pendingList: List<DocumentFileModel>,
    rootUri: String?,
    listState: LazyListState,
    onNavigate: (String) -> Unit,
    onBack: () -> Unit,
    canGoBack: Boolean,
    onEmptyTrash: (() -> Unit)? = null,
    onRestoreFromTrash: ((DocumentFileModel) -> Unit)? = null,
    isViewingTrash: Boolean = false,
    filterVisible: Boolean = true,
    hideDotFiles: Boolean = false,
    preferredExternalPackages: Map<String, String> = emptyMap(),
    onOpenFile: (uri: String, name: String, isEncrypted: Boolean) -> Unit,
    onOpenWithOtherApp: (uri: String, name: String, packageName: String?, rememberChoice: Boolean, forcedMimeType: String?) -> Boolean = { _, _, _, _, _ -> false },
    onClearExternalOpenPreference: (name: String) -> Unit = {},
    onAddToPendingList: (DocumentFileModel) -> Unit,
    onRemoveFromPendingList: (DocumentFileModel) -> Unit,
    onCopyPendingToCurrentDir: () -> Unit = {},
    onMovePendingToCurrentDir: () -> Unit = {},
    onShowPendingList: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onChangeRoot: () -> Unit = {},
    onCreateQuickNote: () -> Unit = {},
    onCreateMusicPlaylist: (String, String) -> Unit = { _, _ -> },
    onShareFileToGit: ((DocumentFileModel) -> Unit)? = null,
    onForwardToBulletin: ((List<DocumentFileModel>) -> Unit)? = null,
    onOpenMarkdownView: (uri: String, name: String, encrypted: Boolean) -> Unit = { _, _, _ -> },
    onRequestGpgDecrypt: (DocumentFileModel, String) -> Unit,
    onRequestGpgEncrypt: (DocumentFileModel, String) -> Unit,
    onRequestQuickMask: ((DocumentFileModel) -> Unit)? = null,
    onConfirmDelete: ((DocumentFileModel, Boolean) -> Unit)? = null,
    onUnzipRequest: (DocumentFileModel) -> Unit = {},
    onRequestMdZipView: (DocumentFileModel) -> Unit = {},
    onRequestHtmlView: (DocumentFileModel) -> Unit = {},
    onRequestHtmlZipView: (DocumentFileModel) -> Unit = {},
    onRequestOpenUrl: (String) -> Unit = {},
    onRequestLlmZipView: (DocumentFileModel) -> Unit = {},
    onRequestEpubView: (DocumentFileModel) -> Unit = {},
    onRequestPicZipView: (DocumentFileModel) -> Unit = {},
    onRequestVidZipView: (DocumentFileModel) -> Unit = {},
    onRequestDirectoryImageView: (DocumentFileModel, String, List<DocumentFileModel>) -> Unit = { _, _, _ -> },
    onRequestPdfView: (DocumentFileModel) -> Unit = {},
    onCompressArchiveRequest: (DocumentFileModel) -> Unit = {},
    onRequestPassProtect: ((DocumentFileModel) -> Unit)? = null,
    onRequestPassView: (DocumentFileModel) -> Unit = {},
    onRequestPassEdit: (DocumentFileModel, String) -> Unit = { _, _ -> },
    onRequestTxtView: (DocumentFileModel) -> Unit = {},
    onRequestLlmView: (DocumentFileModel) -> Unit = {},
    onRequestImportConfig: ((DocumentFileModel) -> Unit)? = null,
    onRequestImportStarDict: ((DocumentFileModel) -> Unit)? = null,
    playbackState: PlaybackState? = null,
    onOpenPlaybackScreen: () -> Unit = {}
) {
    val directoryScrollMinItems = 30
    val context = LocalContext.current
    val prefs = remember { Preferences(context) }
    val scope = rememberCoroutineScope()
    var items by remember(currentUri) { mutableStateOf<List<DocumentFileModel>>(emptyList()) }
    var loading by remember(currentUri) { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var actionTarget by remember { mutableStateOf<DocumentFileModel?>(null) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameValue by remember { mutableStateOf("") }
    var showContextMenu by remember { mutableStateOf(false) }
    var contextMenuTarget by remember { mutableStateOf<DocumentFileModel?>(null) }
    var showCreateFileDialog by remember { mutableStateOf(false) }
    var showCreateDirDialog by remember { mutableStateOf(false) }
    var newFileName by remember { mutableStateOf("") }
    var newDirName by remember { mutableStateOf("") }
    var showDeleteConfirm by remember { mutableStateOf<DocumentFileModel?>(null) }
    var showFileDetail by remember { mutableStateOf<DocumentFileModel?>(null) }
    var filterText by remember { mutableStateOf("") }
    var sortOrder by remember { mutableStateOf(FileSortOrder.NAME) }
    var sortAscending by remember { mutableStateOf(true) }
    var showSortMenu by remember { mutableStateOf(false) }
    var externalOpenTarget by remember { mutableStateOf<DocumentFileModel?>(null) }
    var externalOpenOptions by remember { mutableStateOf<List<ExternalAppTarget>>(emptyList()) }
    var externalOpenForcedMimeType by remember { mutableStateOf<String?>(null) }
    var externalOpenShowTypeChooser by remember { mutableStateOf(false) }
    var showOpenUrlDialog by remember { mutableStateOf(false) }
    var openUrlInput by remember { mutableStateOf("") }
    var openUrlError by remember { mutableStateOf<String?>(null) }
    var showFileSearchDialog by remember { mutableStateOf(false) }
    var showFileSearchResultsDialog by remember { mutableStateOf(false) }
    var fileSearchNamePattern by remember { mutableStateOf("") }
    var fileSearchMinSize by remember { mutableStateOf("") }
    var fileSearchMaxSize by remember { mutableStateOf("") }
    var fileSearchModifiedAfter by remember { mutableStateOf("") }
    var fileSearchModifiedBefore by remember { mutableStateOf("") }
    var fileSearchResults by remember { mutableStateOf<List<RecursiveFileSearchHit>>(emptyList()) }
    var fileSearchRunning by remember { mutableStateOf(false) }
    var fileSearchError by remember { mutableStateOf<String?>(null) }

    fun showExternalOpenDialog(target: DocumentFileModel) {
        externalOpenTarget = target
        externalOpenForcedMimeType = null
        val autoMime = guessExternalOpenMimeType(context, toExternalLaunchUri(context, target.uri), target.name)
        val options = queryExternalOpenTargets(context, target.uri.toString(), target.name)
        externalOpenOptions = options
        externalOpenShowTypeChooser = options.isEmpty() || isExternalMimeLowConfidence(autoMime)
    }

    fun normalizeUserInputUrl(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val normalized = if (Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://").containsMatchIn(trimmed)) trimmed else "https://$trimmed"
        val parsed = runCatching { Uri.parse(normalized) }.getOrNull() ?: return null
        val scheme = parsed.scheme?.lowercase()
        val host = parsed.host?.trim().orEmpty()
        return if ((scheme == "http" || scheme == "https") && host.isNotEmpty()) normalized else null
    }

    val currentDirectoryName = remember(currentUri) {
        val uri = Uri.parse(currentUri)
        val doc = DocumentFile.fromTreeUri(context, uri) ?: DocumentFile.fromSingleUri(context, uri)
        doc?.name ?: context.getString(R.string.directory_root_name)
    }

    fun clearFileSearchInputs() {
        fileSearchNamePattern = ""
        fileSearchMinSize = ""
        fileSearchMaxSize = ""
        fileSearchModifiedAfter = ""
        fileSearchModifiedBefore = ""
        fileSearchError = null
    }

    fun addSearchResultsToPending(targets: List<DocumentFileModel>) {
        val existingUris = pendingList.map { it.uri.toString() }.toHashSet()
        val toAdd = targets.filter { existingUris.add(it.uri.toString()) }
        toAdd.forEach(onAddToPendingList)
        Toast.makeText(context, if (toAdd.isEmpty()) context.getString(R.string.browser_no_new_pending_items) else context.getString(R.string.main_menu_add_filtered_done, toAdd.size), Toast.LENGTH_SHORT).show()
    }

    fun runFileSearch() {
        val nameRegex = try {
            fileSearchNamePattern.trim().takeIf { it.isNotEmpty() }?.let(::Regex)
        } catch (error: Exception) {
            fileSearchError = context.getString(R.string.browser_filename_regex_invalid, error.message ?: error.javaClass.simpleName)
            return
        }
        val minSizeBytes = try {
            parseFileSearchSize(context, fileSearchMinSize)
        } catch (error: Exception) {
            fileSearchError = error.message ?: context.getString(R.string.browser_min_size_invalid)
            return
        }
        val maxSizeBytes = try {
            parseFileSearchSize(context, fileSearchMaxSize)
        } catch (error: Exception) {
            fileSearchError = error.message ?: context.getString(R.string.browser_max_size_invalid)
            return
        }
        val modifiedAfterMillis = try {
            parseFileSearchDate(context, fileSearchModifiedAfter, endOfDay = false)
        } catch (error: Exception) {
            fileSearchError = error.message ?: context.getString(R.string.browser_mtime_min_invalid)
            return
        }
        val modifiedBeforeMillis = try {
            parseFileSearchDate(context, fileSearchModifiedBefore, endOfDay = true)
        } catch (error: Exception) {
            fileSearchError = error.message ?: context.getString(R.string.browser_mtime_max_invalid)
            return
        }
        if (minSizeBytes != null && maxSizeBytes != null && minSizeBytes > maxSizeBytes) {
            fileSearchError = context.getString(R.string.browser_size_range_invalid)
            return
        }
        if (modifiedAfterMillis != null && modifiedBeforeMillis != null && modifiedAfterMillis > modifiedBeforeMillis) {
            fileSearchError = context.getString(R.string.browser_mtime_range_invalid)
            return
        }
        scope.launch {
            fileSearchRunning = true
            fileSearchError = null
            try {
                val criteria = RecursiveFileSearchCriteria(
                    nameRegex = nameRegex,
                    minSizeBytes = minSizeBytes,
                    maxSizeBytes = maxSizeBytes,
                    modifiedAfterMillis = modifiedAfterMillis,
                    modifiedBeforeMillis = modifiedBeforeMillis
                )
                val results = withContext(Dispatchers.IO) {
                    searchFilesRecursively(context, currentUri, criteria)
                }
                fileSearchResults = results
                showFileSearchDialog = false
                showFileSearchResultsDialog = true
                if (results.isEmpty()) {
                    Toast.makeText(context, context.getString(R.string.browser_no_matching_files), Toast.LENGTH_SHORT).show()
                }
            } catch (error: Exception) {
                fileSearchError = context.getString(R.string.browser_search_failed, error.message ?: error.javaClass.simpleName)
            } finally {
                fileSearchRunning = false
            }
        }
    }

    LaunchedEffect(currentUri, refreshTrigger) {
        val normalizedUri = normalizeContentUriString(currentUri)
        val cached = dirCache[normalizedUri]
        if (cached != null) {
            items = cached.items
            loading = false
            error = null
            launch {
                try {
                    val result = withContext(Dispatchers.IO) {
                        listChildrenFast(context, currentUri)
                    }
                    items = result
                    onCacheDir(normalizedUri, result)
                } catch (e: Exception) {
                    error = e.message ?: context.getString(R.string.browser_loading_failed)
                    items = emptyList()
                }
            }
            return@LaunchedEffect
        }
        loading = true
        error = null
        try {
            val result = withContext(Dispatchers.IO) {
                listChildrenFast(context, currentUri)
            }
            items = result
            onCacheDir(normalizedUri, result)
        } catch (e: Exception) {
            error = e.message ?: context.getString(R.string.browser_loading_failed)
            items = emptyList()
        }
        loading = false
    }

    val sortedItems = remember(items, sortOrder, sortAscending) {
        items.sortedWith(fileListComparator(sortOrder, sortAscending))
    }
    val filteredItems = remember(sortedItems, filterText, filterVisible, hideDotFiles) {
        var list = sortedItems
        if (hideDotFiles) list = list.filter { !it.name.startsWith(".") }
        if (filterVisible && filterText.isNotBlank()) {
            list = runCatching { Regex(filterText) }.getOrNull()?.let { regex ->
                list.filter { regex.containsMatchIn(it.name) }
            } ?: list
        }
        list
    }
    val displayItems = filteredItems
    val galleryImageItems = remember(sortedItems, hideDotFiles) {
        sortedItems.filter { item ->
            !item.isDirectory &&
                isGalleryImageFile(item.name) &&
                (!hideDotFiles || !item.name.startsWith("."))
        }
    }

    fun defaultOpenMenuLabel(target: DocumentFileModel): String {
        if (target.isDirectory) return context.getString(R.string.context_default_enter_directory)
        return when {
            isCompressedMarkdown(target.name) -> context.getString(R.string.context_view_zip_markdown)
            isHtmlFile(target.name) -> context.getString(R.string.context_default_open_html)
            isCompressedHtml(target.name) -> context.getString(R.string.context_view_zip_html)
            isCompressedLlmZip(target.name) -> context.getString(R.string.context_view_zip_llm)
            isEpubFile(target.name) -> context.getString(R.string.context_default_open_epub)
            isTxtFile(target.name) -> context.getString(R.string.context_default_open_txt)
            isLlmFile(target.name) -> context.getString(R.string.context_default_open_llm)
            isGalleryImageFile(target.name) -> context.getString(R.string.context_view_images)
            isPicZip(target.name) -> context.getString(R.string.context_view_compressed_images)
            isVidZip(target.name) -> context.getString(R.string.context_view_videos)
            isPdfFile(target.name) -> context.getString(R.string.context_default_open_pdf)
            isExtractableArchive(target.name) -> context.getString(R.string.browser_extract_action)
            target.name.endsWith(".gpg", ignoreCase = true) -> context.getString(R.string.context_gpg_decrypt)
            target.name.endsWith(".pass", ignoreCase = true) -> context.getString(R.string.context_view_password)
            target.name.endsWith(".md", ignoreCase = true) || target.name.endsWith(".rst", ignoreCase = true) -> context.getString(R.string.context_render_markdown)
            else -> {
                val preferredPackage = fileExtensionKey(target.name)?.let { preferredExternalPackages[it] }
                if (preferredPackage != null) context.getString(R.string.context_default_open_with_preferred)
                else context.getString(R.string.context_open_with_internal_viewer)
            }
        }
    }

    fun openItemByDefault(target: DocumentFileModel) {
        if (target.isDirectory) {
            onNavigate(target.uri.toString())
            return
        }
        when {
            isCompressedMarkdown(target.name) ->
                onRequestMdZipView(target)
            isHtmlFile(target.name) ->
                onRequestHtmlView(target)
            isCompressedHtml(target.name) ->
                onRequestHtmlZipView(target)
            isCompressedLlmZip(target.name) ->
                onRequestLlmZipView(target)
            isEpubFile(target.name) ->
                onRequestEpubView(target)
            isTxtFile(target.name) ->
                onRequestTxtView(target)
            isLlmFile(target.name) ->
                onRequestLlmView(target)
            isGalleryImageFile(target.name) ->
                onRequestDirectoryImageView(target, currentUri, galleryImageItems)
            isPicZip(target.name) ->
                onRequestPicZipView(target)
            isVidZip(target.name) ->
                onRequestVidZipView(target)
            isPdfFile(target.name) ->
                onRequestPdfView(target)
            isExtractableArchive(target.name) ->
                onUnzipRequest(target)
            target.name.endsWith(".gpg", ignoreCase = true) ->
                onRequestGpgDecrypt(target, currentUri)
            target.name.endsWith(".pass", ignoreCase = true) ->
                onRequestPassView(target)
            target.name.endsWith(".md", ignoreCase = true) || target.name.endsWith(".rst", ignoreCase = true) ->
                onOpenMarkdownView(target.uri.toString(), target.name, false)
            else -> {
                val preferredPackage = fileExtensionKey(target.name)?.let { preferredExternalPackages[it] }
                if (preferredPackage != null && onOpenWithOtherApp(target.uri.toString(), target.name, preferredPackage, false, null)) {
                    Unit
                } else {
                    val encrypted = target.name.endsWith(".gpg", ignoreCase = true)
                    onOpenFile(target.uri.toString(), target.name, encrypted)
                }
            }
        }
    }

    fun buildDirectoryHeadFingerprint(list: List<DocumentFileModel>): String {
        if (list.isEmpty()) return "0"
        val head = list.asSequence()
            .take(8)
            .joinToString("|") { "${it.name}#${it.lastModified}" }
        return "${list.size}:$head"
    }

    suspend fun persistDirectoryScrollMemory(uriToSave: String, itemsToSave: List<DocumentFileModel>, wasLoading: Boolean) {
        if (wasLoading) return
        if (itemsToSave.size < directoryScrollMinItems) {
            prefs.removeDirectoryScrollMemory(uriToSave)
            return
        }
        val maxIndex = (itemsToSave.size - 1).coerceAtLeast(0)
        val index = listState.firstVisibleItemIndex.coerceIn(0, maxIndex)
        val anchorItem = itemsToSave.getOrNull(index)
        val now = System.currentTimeMillis()
        prefs.setDirectoryScrollMemory(
            DirectoryScrollMemory(
                dirUri = uriToSave,
                anchorUri = anchorItem?.uri?.toString(),
                anchorName = anchorItem?.name.orEmpty(),
                index = index,
                offset = listState.firstVisibleItemScrollOffset.coerceAtLeast(0),
                itemCount = itemsToSave.size,
                headFingerprint = buildDirectoryHeadFingerprint(itemsToSave),
                savedAt = now,
                lastAccessAt = now
            )
        )
    }

    var restoredForUri by remember(currentUri) { mutableStateOf(false) }
    LaunchedEffect(currentUri, loading, displayItems) {
        if (restoredForUri || loading) return@LaunchedEffect
        restoredForUri = true
        if (displayItems.size < directoryScrollMinItems) return@LaunchedEffect
        val memory = withContext(Dispatchers.IO) { prefs.getDirectoryScrollMemory(currentUri) } ?: return@LaunchedEffect
        val fallbackMaxIndex = (displayItems.size - 1).coerceAtLeast(0)
        val anchorIndex = memory.anchorUri?.let { uri ->
            displayItems.indexOfFirst { it.uri.toString() == uri }
        } ?: -1
        val nameAnchorIndex = if (anchorIndex >= 0 || memory.anchorName.isBlank()) {
            -1
        } else {
            displayItems.indexOfFirst { it.name == memory.anchorName }
        }
        val currentFingerprint = buildDirectoryHeadFingerprint(displayItems)
        val targetIndex = when {
            anchorIndex >= 0 -> anchorIndex
            nameAnchorIndex >= 0 -> nameAnchorIndex
            memory.headFingerprint == currentFingerprint -> memory.index.coerceIn(0, fallbackMaxIndex)
            else -> -1
        }
        if (targetIndex < 0) return@LaunchedEffect
        val targetOffset = memory.offset.coerceAtLeast(0)
        runCatching {
            listState.scrollToItem(targetIndex, targetOffset)
        }
    }

    val latestCurrentUriState = androidx.compose.runtime.rememberUpdatedState(currentUri)
    val latestDisplayItemsState = androidx.compose.runtime.rememberUpdatedState(displayItems)
    val latestLoadingState = androidx.compose.runtime.rememberUpdatedState(loading)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, currentUri) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                val uriToSave = latestCurrentUriState.value
                val itemsToSave = latestDisplayItemsState.value
                val wasLoading = latestLoadingState.value
                scope.launch(Dispatchers.IO) {
                    persistDirectoryScrollMemory(uriToSave, itemsToSave, wasLoading)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            val uriToSave = latestCurrentUriState.value
            val itemsToSave = latestDisplayItemsState.value
            val wasLoading = latestLoadingState.value
            runBlocking {
                withContext(Dispatchers.IO) {
                    persistDirectoryScrollMemory(uriToSave, itemsToSave, wasLoading)
                }
            }
        }
    }

    var showFabMenu by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showQuickCopyConfirm by remember { mutableStateOf(false) }
    var showQuickMoveConfirm by remember { mutableStateOf(false) }
    Scaffold(
        modifier = modifier,
        topBar = {
            Column(Modifier.fillMaxWidth()) {
                TopAppBar(
                    title = {
                        val doc = DocumentFile.fromTreeUri(context, Uri.parse(currentUri))
                            ?: DocumentFile.fromSingleUri(context, Uri.parse(currentUri))
                        Text(
                            doc?.name ?: context.getString(R.string.directory_root_name),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        if (canGoBack) {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = context.getString(R.string.common_back))
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = onRefresh) {
                            Icon(Icons.Default.Refresh, contentDescription = context.getString(R.string.common_refresh))
                        }
                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = stringResource(R.string.browser_sort_desc))
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                FileSortOrder.entries.forEach { order ->
                                    val isCurrent = sortOrder == order
                                    val direction = if (isCurrent) (if (sortAscending) " ↑" else " ↓") else ""
                                    DropdownMenuItem(
                                        text = { Text(stringResource(order.labelRes) + direction) },
                                        onClick = {
                                            if (isCurrent) {
                                                sortAscending = !sortAscending
                                            } else {
                                                sortOrder = order
                                                sortAscending = true
                                            }
                                            showSortMenu = false
                                        }
                                    )
                                }
                            }
                        }
                        IconButton(onClick = { showOverflowMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = context.getString(R.string.main_menu))
                        }
                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(context.getString(R.string.config_change_root)) },
                                leadingIcon = { Icon(Icons.Default.FolderOpen, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    onChangeRoot()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(context.getString(R.string.main_menu_open_url)) },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    showOpenUrlDialog = true
                                    openUrlError = null
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.browser_generate_dir_playlist)) },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    onCreateMusicPlaylist(currentUri, currentDirectoryName)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.browser_file_search_title)) },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    fileSearchError = null
                                    showFileSearchDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(context.getString(R.string.main_menu_add_filtered_to_pending)) },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    val toAdd = filteredItems.filter { item -> !pendingList.any { it.uri == item.uri } }
                                    toAdd.forEach { onAddToPendingList(it) }
                                    Toast.makeText(context, context.getString(R.string.main_menu_add_filtered_done, toAdd.size), Toast.LENGTH_SHORT).show()
                                }
                            )
                            onEmptyTrash?.let { empty ->
                                DropdownMenuItem(
                                    text = { Text(context.getString(R.string.main_menu_empty_trash)) },
                                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                                    onClick = {
                                        showOverflowMenu = false
                                        empty()
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(context.getString(R.string.debug_trigger_crash)) },
                                leadingIcon = { Icon(Icons.Default.Warning, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    throw IllegalStateException("debug crash trigger from FileBrowserApp")
                                }
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val totalCount = items.size
                    val visibleCount = displayItems.size
                    val countLabel = if (visibleCount != totalCount) {
                        context.getString(R.string.browser_directory_item_count_filtered, visibleCount, totalCount)
                    } else {
                        context.getString(R.string.browser_directory_item_count, totalCount)
                    }
                    Text(
                        text = countLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (filterVisible) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = filterText,
                            onValueChange = { filterText = it },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            placeholder = { Text(context.getString(R.string.directory_filter_placeholder)) },
                            label = null
                        )
                        if (filterText.isNotEmpty()) {
                            IconButton(onClick = { filterText = "" }) {
                                Icon(Icons.Default.RemoveCircle, contentDescription = context.getString(R.string.directory_filter_clear), Modifier.size(20.dp))
                            }
                        }
                    }
                }
                playbackState?.let { state ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .clickable { onOpenPlaybackScreen() },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            context.getString(R.string.directory_now_playing, state.trackName, state.trackIndex + 1, state.totalTracks),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (!loading && error == null) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    if (pendingList.isNotEmpty()) {
                        FloatingActionButton(
                            onClick = { showQuickCopyConfirm = true },
                            containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.72f),
                            contentColor = MaterialTheme.colorScheme.onSecondary
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = context.getString(R.string.fab_copy_to_current))
                        }
                        FloatingActionButton(
                            onClick = { showQuickMoveConfirm = true },
                            containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.72f),
                            contentColor = MaterialTheme.colorScheme.onSecondary
                        ) {
                            Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = context.getString(R.string.fab_move_to_current))
                        }
                        FloatingActionButton(
                            onClick = { onShowPendingList(true) },
                            containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.72f),
                            contentColor = MaterialTheme.colorScheme.onTertiary
                        ) {
                            Icon(Icons.AutoMirrored.Filled.List, contentDescription = context.getString(R.string.fab_pending_list))
                        }
                    }
                    FloatingActionButton(
                        onClick = { showFabMenu = true },
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.72f),
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ) {
                        Icon(Icons.Default.Add, contentDescription = context.getString(R.string.fab_new))
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                error != null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            error!!,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                else -> {
                    Column(Modifier.fillMaxSize()) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp, 8.dp, 8.dp, 88.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                        items(
                            count = displayItems.size,
                            key = { displayItems[it].uri.toString() }
                        ) { index ->
                            val item = displayItems[index]
                            FileItem(
                                model = item,
                                hasPreferredExternalApp = fileExtensionKey(item.name)?.let { preferredExternalPackages.containsKey(it) } == true,
                                isInPendingList = pendingList.any { it.uri == item.uri },
                                onClick = { openItemByDefault(item) },
                                onLongClick = {
                                    contextMenuTarget = item
                                    showContextMenu = true
                                },
                                onDoubleClick = {
                                    if (pendingList.any { it.uri == item.uri }) onRemoveFromPendingList(item)
                                    else onAddToPendingList(item)
                                }
                            )
                        }
                        }
                    }
                }
            }
        }
    }

    if (showOpenUrlDialog) {
        AlertDialog(
            onDismissRequest = {
                showOpenUrlDialog = false
                openUrlError = null
            },
            title = { Text(context.getString(R.string.main_menu_open_url)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = openUrlInput,
                        onValueChange = {
                            openUrlInput = it
                            if (openUrlError != null) openUrlError = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(context.getString(R.string.open_url_label)) },
                        placeholder = { Text(context.getString(R.string.open_url_placeholder)) },
                        singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = context.getString(R.string.open_url_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    openUrlError?.let { error ->
                        Spacer(Modifier.height(8.dp))
                        SelectionContainer {
                            Text(error, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val normalized = normalizeUserInputUrl(openUrlInput)
                    if (normalized == null) {
                        openUrlError = context.getString(R.string.open_url_invalid)
                        return@TextButton
                    }
                    showOpenUrlDialog = false
                    openUrlError = null
                    onRequestOpenUrl(normalized)
                }) {
                    Text(context.getString(R.string.open_url_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showOpenUrlDialog = false
                    openUrlError = null
                }) {
                    Text(context.getString(R.string.common_close))
                }
            }
        )
    }

    if (showFileSearchDialog) {
        FileSearchDialog(
            namePattern = fileSearchNamePattern,
            minSize = fileSearchMinSize,
            maxSize = fileSearchMaxSize,
            modifiedAfter = fileSearchModifiedAfter,
            modifiedBefore = fileSearchModifiedBefore,
            searching = fileSearchRunning,
            error = fileSearchError,
            onNamePatternChange = {
                fileSearchNamePattern = it
                if (fileSearchError != null) fileSearchError = null
            },
            onMinSizeChange = {
                fileSearchMinSize = it
                if (fileSearchError != null) fileSearchError = null
            },
            onMaxSizeChange = {
                fileSearchMaxSize = it
                if (fileSearchError != null) fileSearchError = null
            },
            onModifiedAfterChange = {
                fileSearchModifiedAfter = it
                if (fileSearchError != null) fileSearchError = null
            },
            onModifiedBeforeChange = {
                fileSearchModifiedBefore = it
                if (fileSearchError != null) fileSearchError = null
            },
            onSearch = { runFileSearch() },
            onClear = { clearFileSearchInputs() },
            onDismiss = {
                if (!fileSearchRunning) showFileSearchDialog = false
            }
        )
    }

    if (showFileSearchResultsDialog) {
        FileSearchResultsDialog(
            results = fileSearchResults,
            pendingUris = pendingList.map { it.uri.toString() }.toSet(),
            onAddAll = { addSearchResultsToPending(fileSearchResults.map { it.model }) },
            onAddOne = { addSearchResultsToPending(listOf(it)) },
            onDismiss = { showFileSearchResultsDialog = false }
        )
    }

    if (showContextMenu && contextMenuTarget != null) {
        val menuTarget = contextMenuTarget!!
        Dialog(onDismissRequest = { showContextMenu = false; contextMenuTarget = null }) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    Text(
                        menuTarget.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(16.dp))
                    TextButton(
                        onClick = {
                            showContextMenu = false
                            openItemByDefault(menuTarget)
                            contextMenuTarget = null
                        }
                    ) {
                        Text(
                            defaultOpenMenuLabel(menuTarget),
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (!menuTarget.isDirectory) {
                        TextButton(
                            onClick = {
                                showContextMenu = false
                                showExternalOpenDialog(menuTarget)
                                contextMenuTarget = null
                            }
                        ) { Text(context.getString(R.string.context_open_with_other_app), color = MaterialTheme.colorScheme.onSurface) }
                        TextButton(
                            onClick = {
                                showContextMenu = false
                                onClearExternalOpenPreference(menuTarget.name)
                                onOpenFile(menuTarget.uri.toString(), menuTarget.name, false)
                                contextMenuTarget = null
                            }
                        ) { Text(context.getString(R.string.context_open_with_internal_viewer), color = MaterialTheme.colorScheme.onSurface) }
                        if (menuTarget.name.endsWith(".md", ignoreCase = true) || menuTarget.name.endsWith(".rst", ignoreCase = true)) {
                            TextButton(
                                onClick = {
                                    showContextMenu = false
                                    onOpenMarkdownView(menuTarget.uri.toString(), menuTarget.name, false)
                                    contextMenuTarget = null
                                }
                                ) { Text(context.getString(R.string.context_render_markdown), color = MaterialTheme.colorScheme.onSurface) }
                        }
                        if (menuTarget.name.endsWith(".zip", ignoreCase = true)) {
                            if (isCompressedMarkdown(menuTarget.name)) {
                                TextButton(
                                    onClick = {
                                        showContextMenu = false
                                        onRequestMdZipView(menuTarget)
                                        contextMenuTarget = null
                                    }
                                ) { Text(context.getString(R.string.context_view_zip_markdown), color = MaterialTheme.colorScheme.onSurface) }
                            }
                            if (isCompressedHtml(menuTarget.name)) {
                                TextButton(
                                    onClick = {
                                        showContextMenu = false
                                        onRequestHtmlZipView(menuTarget)
                                        contextMenuTarget = null
                                    }
                                ) { Text(context.getString(R.string.context_view_zip_html), color = MaterialTheme.colorScheme.onSurface) }
                            }
                            if (isCompressedLlmZip(menuTarget.name)) {
                                TextButton(
                                    onClick = {
                                        showContextMenu = false
                                        onRequestLlmZipView(menuTarget)
                                        contextMenuTarget = null
                                    }
                                ) { Text(context.getString(R.string.context_view_zip_llm), color = MaterialTheme.colorScheme.onSurface) }
                            }
                            if (isPicZip(menuTarget.name)) {
                                TextButton(
                                    onClick = {
                                        showContextMenu = false
                                        onRequestPicZipView(menuTarget)
                                        contextMenuTarget = null
                                    }
                                ) { Text(context.getString(R.string.context_view_compressed_images), color = MaterialTheme.colorScheme.onSurface) }
                            }
                            if (isVidZip(menuTarget.name)) {
                                TextButton(
                                    onClick = {
                                        showContextMenu = false
                                        onRequestVidZipView(menuTarget)
                                        contextMenuTarget = null
                                    }
                                ) { Text(context.getString(R.string.context_view_videos), color = MaterialTheme.colorScheme.onSurface) }
                            }
                        }
                        if (!menuTarget.name.endsWith(".pass", ignoreCase = true)) {
                            TextButton(
                                onClick = {
                                    showContextMenu = false
                                    onRequestGpgEncrypt(menuTarget, currentUri)
                                    contextMenuTarget = null
                                }
                            ) { Text(context.getString(R.string.context_gpg_encrypt), color = MaterialTheme.colorScheme.onSurface) }
                        }
                        if ((menuTarget.name.endsWith(".md", ignoreCase = true) || menuTarget.name.endsWith(".rst", ignoreCase = true)
                            || menuTarget.name.endsWith(".txt", ignoreCase = true))
                            && onRequestPassProtect != null) {
                            TextButton(
                                onClick = {
                                    showContextMenu = false
                                    onRequestPassProtect(menuTarget)
                                    contextMenuTarget = null
                                }
                            ) { Text(context.getString(R.string.context_pass_protect), color = MaterialTheme.colorScheme.onSurface) }
                        }
                        if (menuTarget.name.endsWith(".pass", ignoreCase = true)) {
                            TextButton(
                                onClick = {
                                    showContextMenu = false
                                    onRequestPassView(menuTarget)
                                    contextMenuTarget = null
                                }
                            ) { Text(context.getString(R.string.context_view_password), color = MaterialTheme.colorScheme.onSurface) }
                            TextButton(
                                onClick = {
                                    showContextMenu = false
                                    onRequestPassEdit(menuTarget, currentUri)
                                    contextMenuTarget = null
                                }
                            ) { Text(context.getString(R.string.context_edit_directly), color = MaterialTheme.colorScheme.onSurface) }
                        }
                        if (menuTarget.name.endsWith(".json", ignoreCase = true) && onRequestImportConfig != null) {
                            TextButton(
                                onClick = {
                                    showContextMenu = false
                                    onRequestImportConfig(menuTarget)
                                    contextMenuTarget = null
                                }
                            ) { Text(context.getString(R.string.context_import_config), color = MaterialTheme.colorScheme.onSurface) }
                        }
                        if (isStarDictImportCandidate(menuTarget.name) && onRequestImportStarDict != null) {
                            TextButton(
                                onClick = {
                                    showContextMenu = false
                                    onRequestImportStarDict(menuTarget)
                                    contextMenuTarget = null
                                }
                            ) { Text(context.getString(R.string.context_import_stardict), color = MaterialTheme.colorScheme.onSurface) }
                        }
                    } else {
                        TextButton(
                            onClick = {
                                showContextMenu = false
                                onCreateMusicPlaylist(menuTarget.uri.toString(), menuTarget.name)
                                contextMenuTarget = null
                            }
                        ) { Text(context.getString(R.string.browser_generate_music_playlist), color = MaterialTheme.colorScheme.onSurface) }
                    }
                    if (isViewingTrash && onRestoreFromTrash != null) {
                        TextButton(
                            onClick = {
                                showContextMenu = false
                                onRestoreFromTrash(menuTarget)
                                contextMenuTarget = null
                            }
                        ) { Text(context.getString(R.string.context_restore), color = MaterialTheme.colorScheme.primary) }
                    }
                    if (!menuTarget.isDirectory) {
                        onRequestQuickMask?.let { fn ->
                            TextButton(
                                onClick = {
                                    showContextMenu = false
                                    fn(menuTarget)
                                    contextMenuTarget = null
                                }
                            ) { Text(context.getString(R.string.context_quick_mask), color = MaterialTheme.colorScheme.onSurface) }
                        }
                    }
                    TextButton(
                        onClick = {
                            showContextMenu = false
                            onCompressArchiveRequest(menuTarget)
                            contextMenuTarget = null
                        }
                    ) { Text(context.getString(R.string.context_compress_archive), color = MaterialTheme.colorScheme.onSurface) }
                    if (!menuTarget.isDirectory && onShareFileToGit != null) {
                        TextButton(
                            onClick = {
                                showContextMenu = false
                                onShareFileToGit.invoke(menuTarget)
                                contextMenuTarget = null
                            }
                        ) { Text(context.getString(R.string.context_share_to_git), color = MaterialTheme.colorScheme.onSurface) }
                    }
                    if (onForwardToBulletin != null) {
                        TextButton(
                            onClick = {
                                showContextMenu = false
                                onForwardToBulletin.invoke(listOf(menuTarget))
                                contextMenuTarget = null
                            }
                        ) { Text(context.getString(R.string.context_forward_to_bulletin), color = MaterialTheme.colorScheme.onSurface) }
                    }
                    TextButton(
                        onClick = {
                            showContextMenu = false
                            if (pendingList.any { it.uri == menuTarget.uri }) onRemoveFromPendingList(menuTarget)
                            else onAddToPendingList(menuTarget)
                            contextMenuTarget = null
                        }
                    ) {
                        Text(
                            if (pendingList.any { it.uri == menuTarget.uri }) context.getString(R.string.context_remove_from_pending) else context.getString(R.string.context_add_to_pending),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    TextButton(
                        onClick = {
                            showContextMenu = false
                            showFileDetail = menuTarget
                            contextMenuTarget = null
                        }
                    ) { Text(context.getString(R.string.context_details), color = MaterialTheme.colorScheme.onSurface) }
                    TextButton(
                        onClick = {
                            showContextMenu = false
                            contextMenuTarget = null
                            showDeleteConfirm = menuTarget
                        }
                    ) { Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error) }
                    TextButton(onClick = { showContextMenu = false; contextMenuTarget = null }) {
                        Text(stringResource(R.string.common_cancel), color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }

    if (externalOpenTarget != null) {
        val target = externalOpenTarget!!
        Dialog(onDismissRequest = {
            externalOpenTarget = null
            externalOpenOptions = emptyList()
            externalOpenForcedMimeType = null
            externalOpenShowTypeChooser = false
        }) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                        .heightIn(max = 480.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(context.getString(R.string.context_choose_open_app), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        target.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(16.dp))
                    if (externalOpenShowTypeChooser) {
                        Text(
                            stringResource(R.string.browser_external_type_prompt),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        ExternalOpenTypeHint.entries.forEach { hint ->
                            TextButton(
                                onClick = {
                                    externalOpenForcedMimeType = hint.mimeType
                                    externalOpenOptions = queryExternalOpenTargets(
                                        context,
                                        target.uri.toString(),
                                        target.name,
                                        hint.mimeType
                                    )
                                    if (externalOpenOptions.isEmpty()) {
                                        Toast.makeText(context, context.getString(R.string.browser_no_app_to_open), Toast.LENGTH_SHORT).show()
                                        externalOpenShowTypeChooser = true
                                    } else {
                                        externalOpenShowTypeChooser = false
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(hint.labelRes), color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                        if (externalOpenOptions.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            TextButton(
                                onClick = {
                                    externalOpenForcedMimeType = null
                                    externalOpenShowTypeChooser = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(R.string.browser_external_use_auto_list), color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    } else {
                        TextButton(
                            onClick = { externalOpenShowTypeChooser = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.browser_external_filter_by_type), color = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(Modifier.height(8.dp))
                        externalOpenOptions.forEach { option ->
                            TextButton(
                                onClick = {
                                    val opened = onOpenWithOtherApp(
                                        target.uri.toString(),
                                        target.name,
                                        option.packageName,
                                        true,
                                        externalOpenForcedMimeType
                                    )
                                    if (opened) {
                                        externalOpenTarget = null
                                        externalOpenOptions = emptyList()
                                        externalOpenForcedMimeType = null
                                        externalOpenShowTypeChooser = false
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.fillMaxWidth()) {
                                    Text(option.label, color = MaterialTheme.colorScheme.onSurface)
                                    Text(
                                        option.packageName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = {
                            externalOpenTarget = null
                            externalOpenOptions = emptyList()
                            externalOpenForcedMimeType = null
                            externalOpenShowTypeChooser = false
                        }) {
                            Text(context.getString(R.string.common_cancel))
                        }
                    }
                }
            }
        }
    }

    if (showFabMenu) {
        Dialog(onDismissRequest = { showFabMenu = false }) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Column(Modifier.padding(24.dp)) {
                    Text(context.getString(R.string.new_menu_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(16.dp))
                    TextButton(
                        onClick = {
                            showFabMenu = false
                            newFileName = ""
                            showCreateFileDialog = true
                        }
                    ) { Text(context.getString(R.string.new_menu_file), color = MaterialTheme.colorScheme.onSurface) }
                    TextButton(
                        onClick = {
                            showFabMenu = false
                            newDirName = ""
                            showCreateDirDialog = true
                        }
                    ) { Text(context.getString(R.string.new_menu_folder), color = MaterialTheme.colorScheme.onSurface) }
                    TextButton(
                        onClick = {
                            showFabMenu = false
                            onCreateQuickNote()
                        }
                    ) { Text(context.getString(R.string.new_menu_quick_note), color = MaterialTheme.colorScheme.onSurface) }
                    TextButton(onClick = { showFabMenu = false }) { Text(context.getString(R.string.common_cancel), color = MaterialTheme.colorScheme.onSurface) }
                }
            }
        }
    }

    if (showCreateFileDialog) {
        val createFileFocus = remember { FocusRequester() }
        LaunchedEffect(Unit) {
            delay(150)
            createFileFocus.requestFocus()
        }
        AlertDialog(
            onDismissRequest = { showCreateFileDialog = false },
            title = { Text(context.getString(R.string.create_file_title)) },
            text = {
                OutlinedTextField(
                    value = newFileName,
                    onValueChange = { newFileName = it },
                    label = { Text(context.getString(R.string.create_file_name)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(createFileFocus),
                    placeholder = { Text(context.getString(R.string.create_file_placeholder)) }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = newFileName.trim()
                        if (name.isNotEmpty()) {
                            val uri = Uri.parse(currentUri)
                            val dir = if (currentUri.contains("/tree/")) {
                                DocumentFile.fromTreeUri(context, uri)
                            } else {
                                DocumentFile.fromSingleUri(context, uri)
                            }
                            when {
                                dir == null || !dir.isDirectory -> Toast.makeText(context, context.getString(R.string.create_access_failed), Toast.LENGTH_SHORT).show()
                                dir.createFile("application/octet-stream", name) == null -> Toast.makeText(context, context.getString(R.string.create_failed), Toast.LENGTH_SHORT).show()
                                else -> { showCreateFileDialog = false; onRefresh() }
                            }
                        }
                    }
                ) { Text(context.getString(R.string.common_create)) }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFileDialog = false }) { Text(context.getString(R.string.common_cancel)) }
            }
        )
    }

    if (showCreateDirDialog) {
        val createDirFocus = remember { FocusRequester() }
        LaunchedEffect(Unit) {
            delay(150)
            createDirFocus.requestFocus()
        }
        AlertDialog(
            onDismissRequest = { showCreateDirDialog = false },
            title = { Text(context.getString(R.string.create_folder_title)) },
            text = {
                OutlinedTextField(
                    value = newDirName,
                    onValueChange = { newDirName = it },
                    label = { Text(context.getString(R.string.create_folder_name)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(createDirFocus),
                    placeholder = { Text(context.getString(R.string.create_folder_placeholder)) }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = newDirName.trim()
                        if (name.isNotEmpty()) {
                            val uri = Uri.parse(currentUri)
                            val dir = if (currentUri.contains("/tree/")) {
                                DocumentFile.fromTreeUri(context, uri)
                            } else {
                                DocumentFile.fromSingleUri(context, uri)
                            }
                            when {
                                dir == null || !dir.isDirectory -> Toast.makeText(context, context.getString(R.string.create_access_failed), Toast.LENGTH_SHORT).show()
                                dir.createDirectory(name) == null -> Toast.makeText(context, context.getString(R.string.create_failed), Toast.LENGTH_SHORT).show()
                                else -> { showCreateDirDialog = false; onRefresh() }
                            }
                        }
                    }
                ) { Text(context.getString(R.string.common_create)) }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDirDialog = false }) { Text(context.getString(R.string.common_cancel)) }
            }
        )
    }

    if (showDeleteConfirm != null) {
        val target = showDeleteConfirm!!
        var deletePermanently by remember { mutableStateOf(false) }
        val hasRoot = rootUri != null
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text(stringResource(R.string.browser_confirm_delete_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.browser_delete_confirm_single, target.name), color = MaterialTheme.colorScheme.onSurface)
                    if (hasRoot) {
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.RadioButton(
                                selected = !deletePermanently,
                                onClick = { deletePermanently = false }
                            )
                            Spacer(Modifier.size(8.dp))
                            Text(stringResource(R.string.browser_move_to_trash_recoverable), style = MaterialTheme.typography.bodyMedium)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.RadioButton(
                                selected = deletePermanently,
                                onClick = { deletePermanently = true }
                            )
                            Spacer(Modifier.size(8.dp))
                            Text(stringResource(R.string.browser_delete_permanently), style = MaterialTheme.typography.bodyMedium)
                        }
                    } else {
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.browser_no_trash_permanent_delete), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = null
                        onConfirmDelete?.invoke(target, deletePermanently)
                    }
                ) { Text(if (hasRoot && !deletePermanently) stringResource(R.string.browser_moved_to_trash) else stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }

    if (showRenameDialog && actionTarget != null) {
        var target = actionTarget!!
        val renameFocus = remember { FocusRequester() }
        val keyboardController = LocalSoftwareKeyboardController.current
        LaunchedEffect(Unit) {
            delay(100)
            renameFocus.requestFocus()
            keyboardController?.show()
        }
        AlertDialog(
            onDismissRequest = {
                showRenameDialog = false
                actionTarget = null
            },
            title = {
                Text(
                    stringResource(R.string.browser_archive_operation, target.name),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            text = {
                Column {
                    Text(stringResource(R.string.browser_rename_to), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = renameValue,
                        onValueChange = { renameValue = it },
                        label = { Text(stringResource(R.string.browser_new_name_label)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(renameFocus)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        try {
                            val newName = renameValue.trim()
                            if (newName.isEmpty()) return@TextButton
                            val renamedUri = context.contentResolver.renameDocument(target.uri, newName)
                            showRenameDialog = false
                            actionTarget = null
                            if (renamedUri != null) {
                                items = items.map {
                                    if (it.uri == target.uri) it.copy(name = newName, uri = renamedUri) else it
                                }
                            } else {
                                Toast.makeText(context, context.getString(R.string.browser_rename_failed), Toast.LENGTH_SHORT).show()
                            }
                        } catch (_: Exception) {}
                    }
                ) { Text(stringResource(R.string.browser_rename_title)) }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false; actionTarget = null }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }

    if (showFileDetail != null) {
        val detailTarget = showFileDetail!!
        val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        var detailRenameValue by remember(detailTarget.uri) { mutableStateOf(detailTarget.name) }
        var detailMeta by remember(detailTarget.uri) { mutableStateOf<Map<String, String>>(emptyMap()) }
        val detailRenameFocus = remember { FocusRequester() }
        val isArchiveFile = isExtractableArchive(detailTarget.name)
        var zipDetailResult by remember(detailTarget.uri) { mutableStateOf<ZipFirstLevelResult?>(null) }
        var zipDetailPassword by remember(detailTarget.uri) { mutableStateOf("") }
        var zipDetailShowPasswordInput by remember(detailTarget.uri) { mutableStateOf(false) }
        var showTypeSpecificMeta by remember(detailTarget.uri) { mutableStateOf(false) }
        var typeSpecificMetaLoaded by remember(detailTarget.uri) { mutableStateOf(false) }
        var typeSpecificMetaLoading by remember(detailTarget.uri) { mutableStateOf(false) }
        var typeSpecificMetaRows by remember(detailTarget.uri) { mutableStateOf<List<DetailMetadataRow>>(emptyList()) }
        var typeSpecificMetaError by remember(detailTarget.uri) { mutableStateOf<String?>(null) }
        var showImageExifEditDialog by remember(detailTarget.uri) { mutableStateOf(false) }
        var imageExifLoading by remember(detailTarget.uri) { mutableStateOf(false) }
        var imageExifSaving by remember(detailTarget.uri) { mutableStateOf(false) }
        var imageExifSaveError by remember(detailTarget.uri) { mutableStateOf<String?>(null) }
        var imageExifForm by remember(detailTarget.uri) { mutableStateOf(EditableImageExif()) }
        var imageExifValidation by remember(detailTarget.uri) { mutableStateOf(EditableImageExifValidation()) }
        var showAudioMetaEditDialog by remember(detailTarget.uri) { mutableStateOf(false) }
        var audioMetaLoading by remember(detailTarget.uri) { mutableStateOf(false) }
        var audioMetaSaving by remember(detailTarget.uri) { mutableStateOf(false) }
        var audioMetaSaveError by remember(detailTarget.uri) { mutableStateOf<String?>(null) }
        var audioMetaForm by remember(detailTarget.uri) { mutableStateOf(EditableAudioAnnotation()) }
        var audioMetaValidation by remember(detailTarget.uri) { mutableStateOf(EditableAudioAnnotationValidation()) }
        val detailScope = rememberCoroutineScope()

        LaunchedEffect(detailTarget.uri) {
            withContext(Dispatchers.IO) {
                try {
                    val uri = detailTarget.uri
                    val projection = arrayOf(
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        DocumentsContract.Document.COLUMN_MIME_TYPE,
                        DocumentsContract.Document.COLUMN_SIZE,
                        DocumentsContract.Document.COLUMN_LAST_MODIFIED
                    )
                    val meta = mutableMapOf<String, String>()
                    context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            for (i in 0 until cursor.columnCount) {
                                val colName = cursor.getColumnName(i)
                                val value = cursor.getString(i) ?: continue
                                meta[colName] = value
                            }
                        }
                    }
                    detailMeta = meta
                } catch (_: Exception) {}
            }
        }

        val docId = detailMeta[DocumentsContract.Document.COLUMN_DOCUMENT_ID]
        val fullPath = if (docId != null) {
            val parts = docId.split(":", limit = 2)
            if (parts.size == 2) "/${parts[1]}" else docId
        } else {
            detailTarget.uri.path ?: detailTarget.uri.toString()
        }
        val displayPath = if (detailTarget.isDirectory) {
            fullPath
        } else {
            fullPath.substringBeforeLast('/', fullPath).ifEmpty { "/" }
        }
        val mimeType = detailMeta[DocumentsContract.Document.COLUMN_MIME_TYPE] ?: ""
        val sizeStr = detailTarget.displaySize.ifEmpty {
            detailMeta[DocumentsContract.Document.COLUMN_SIZE]?.toLongOrNull()?.let { sz ->
                when {
                    sz < 1024 -> "$sz B"
                    sz < 1024 * 1024 -> "%.1f KB".format(sz / 1024.0)
                    sz < 1024 * 1024 * 1024 -> "%.1f MB".format(sz / (1024.0 * 1024))
                    else -> "%.1f GB".format(sz / (1024.0 * 1024 * 1024))
                }
            } ?: ""
        }
        val fmt = remember {
            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
        }
        val lastModifiedStr = if (detailTarget.lastModified > 0) fmt.format(java.util.Date(detailTarget.lastModified)) else ""
        val lastModifiedFromMeta = detailMeta[DocumentsContract.Document.COLUMN_LAST_MODIFIED]?.toLongOrNull()
        val lastModifiedMetaStr = if (lastModifiedFromMeta != null && lastModifiedFromMeta > 0) fmt.format(java.util.Date(lastModifiedFromMeta)) else ""
        val displayModified = lastModifiedStr.ifEmpty { lastModifiedMetaStr }
        val detailMetaKind = inferDetailMetadataKind(detailTarget.name, mimeType, detailTarget.isDirectory)

        suspend fun loadTypeSpecificMetadataNow(forceReload: Boolean = false) {
            if (!showTypeSpecificMeta || detailTarget.isDirectory) return
            if (!forceReload && typeSpecificMetaLoaded) return
            typeSpecificMetaLoading = true
            typeSpecificMetaError = null
            val rows = try {
                withContext(Dispatchers.IO) {
                    loadTypeSpecificMetadataRows(
                        context = context,
                        uri = detailTarget.uri,
                        fileName = detailTarget.name,
                        mimeType = mimeType,
                        isDirectory = detailTarget.isDirectory
                    )
                }
            } catch (e: Exception) {
                typeSpecificMetaError = e.message ?: e.javaClass.simpleName
                emptyList()
            }
            val mergedRows = if (detailMetaKind == DetailMetadataKind.AUDIO) {
                val annotation = withContext(Dispatchers.IO) {
                    prefs.getAudioMetaAnnotation(detailTarget.uri.toString())
                }
                applyAudioMetaAnnotationRows(context, rows, annotation)
            } else {
                rows
            }
            typeSpecificMetaRows = mergedRows
            typeSpecificMetaLoaded = true
            typeSpecificMetaLoading = false

            if (isArchiveFile && zipDetailResult == null) {
                zipDetailResult = withContext(Dispatchers.IO) {
                    getArchiveFirstLevelEntries(context, detailTarget.uri, detailTarget.name, null)
                }
            }
        }

        LaunchedEffect(showTypeSpecificMeta, detailTarget.uri, mimeType) {
            loadTypeSpecificMetadataNow(forceReload = false)
        }

        Dialog(onDismissRequest = { showFileDetail = null }) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(stringResource(R.string.context_details), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(16.dp))

                    Text(stringResource(R.string.browser_full_path), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SelectionContainer(modifier = Modifier.weight(1f)) {
                            Text(displayPath, style = MaterialTheme.typography.bodyMedium)
                        }
                        IconButton(onClick = {
                            clipboardManager?.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.browser_full_path), displayPath))
                            Toast.makeText(context, context.getString(R.string.browser_path_copied), Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.browser_copy_path), Modifier.size(20.dp))
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.browser_rename_title), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = detailRenameValue,
                            onValueChange = { detailRenameValue = it },
                            singleLine = false,
                            minLines = 1,
                            maxLines = 6,
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(detailRenameFocus),
                            textStyle = MaterialTheme.typography.bodyMedium
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = {
                                    val newName = detailRenameValue.trim()
                                    if (newName.isNotEmpty() && newName != detailTarget.name) {
                                        try {
                                            val renamedUri = context.contentResolver.renameDocument(detailTarget.uri, newName)
                                            if (renamedUri != null) {
                                                items = items.map {
                                                    if (it.uri == detailTarget.uri) it.copy(name = newName, uri = renamedUri) else it
                                                }
                                                showFileDetail = showFileDetail?.copy(name = newName, uri = renamedUri)
                                                Toast.makeText(context, context.getString(R.string.browser_renamed), Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, context.getString(R.string.browser_rename_failed), Toast.LENGTH_SHORT).show()
                                            }
                                        } catch (_: Exception) {
                                            Toast.makeText(context, context.getString(R.string.browser_rename_failed), Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                enabled = detailRenameValue.trim().let { it.isNotEmpty() && it != detailTarget.name }
                            ) { Text(stringResource(R.string.common_ok)) }
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.browser_file_properties_title), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))

                    @Composable
                    fun MetaRow(label: String, value: String) {
                        if (value.isNotEmpty()) {
                            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                                Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(80.dp))
                                SelectionContainer(modifier = Modifier.weight(1f)) {
                                    Text(value, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }

                    MetaRow(context.getString(R.string.browser_type_field), if (detailTarget.isDirectory) context.getString(R.string.browser_folder_type) else mimeType.ifEmpty { context.getString(R.string.browser_file_type) })
                    if (!detailTarget.isDirectory) MetaRow(context.getString(R.string.browser_size_field), sizeStr)
                    MetaRow(context.getString(R.string.browser_modified_field), displayModified)

                    if (!detailTarget.isDirectory) {
                        Spacer(Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                stringResource(R.string.browser_type_specific_section_title),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            TextButton(onClick = { showTypeSpecificMeta = !showTypeSpecificMeta }) {
                                Text(
                                    if (showTypeSpecificMeta) {
                                        stringResource(R.string.browser_type_specific_collapse)
                                    } else {
                                        stringResource(R.string.browser_type_specific_expand)
                                    }
                                )
                            }
                        }

                        if (showTypeSpecificMeta) {
                            Spacer(Modifier.height(8.dp))
                            if (detailMetaKind == DetailMetadataKind.IMAGE) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            detailScope.launch {
                                                imageExifLoading = true
                                                imageExifSaveError = null
                                                imageExifValidation = EditableImageExifValidation()
                                                imageExifForm = withContext(Dispatchers.IO) {
                                                    readEditableImageExif(context, detailTarget.uri)
                                                }
                                                imageExifLoading = false
                                                showImageExifEditDialog = true
                                            }
                                        }
                                    ) {
                                        Text(stringResource(R.string.browser_type_specific_edit_image_meta))
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                            } else if (detailMetaKind == DetailMetadataKind.AUDIO) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            detailScope.launch {
                                                audioMetaLoading = true
                                                audioMetaSaveError = null
                                                audioMetaValidation = EditableAudioAnnotationValidation()
                                                val annotation = withContext(Dispatchers.IO) {
                                                    prefs.getAudioMetaAnnotation(detailTarget.uri.toString())
                                                }
                                                val fromFile = withContext(Dispatchers.IO) {
                                                    readEditableAudioAnnotation(context, detailTarget.uri)
                                                }
                                                audioMetaForm = EditableAudioAnnotation(
                                                    title = annotation?.title?.takeIf { it.isNotBlank() } ?: fromFile.title,
                                                    artist = annotation?.artist?.takeIf { it.isNotBlank() } ?: fromFile.artist,
                                                    album = annotation?.album?.takeIf { it.isNotBlank() } ?: fromFile.album
                                                )
                                                audioMetaLoading = false
                                                showAudioMetaEditDialog = true
                                            }
                                        }
                                    ) {
                                        Text(stringResource(R.string.browser_type_specific_edit_audio_meta))
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                            }
                            when {
                                typeSpecificMetaLoading -> {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(Modifier.size(20.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            stringResource(R.string.browser_type_specific_loading),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                typeSpecificMetaError != null -> {
                                    Text(
                                        stringResource(R.string.browser_type_specific_error, typeSpecificMetaError ?: ""),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                                typeSpecificMetaRows.isNotEmpty() -> {
                                    typeSpecificMetaRows.forEach { row ->
                                        if (row.isSection) {
                                            Spacer(Modifier.height(8.dp))
                                            Text(
                                                text = row.label,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        } else {
                                            MetaRow(row.label, row.value)
                                        }
                                    }
                                }
                                else -> {
                                    Text(
                                        stringResource(R.string.browser_type_specific_empty),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            if (isArchiveFile) {
                                Spacer(Modifier.height(12.dp))
                                Text(stringResource(R.string.browser_archive_contents), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(8.dp))
                                when (val r = zipDetailResult) {
                                    is ZipFirstLevelResult.Ok -> {
                                        if (r.entries.isEmpty()) {
                                            Text(stringResource(R.string.browser_empty_placeholder), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        } else {
                                            Column(Modifier.fillMaxWidth().heightIn(max = 200.dp).verticalScroll(rememberScrollState())) {
                                                r.entries.forEach { entry ->
                                                    Text(entry, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                                                }
                                            }
                                        }
                                    }
                                    is ZipFirstLevelResult.OkSingleDir -> {
                                        Column(Modifier.fillMaxWidth().heightIn(max = 200.dp).verticalScroll(rememberScrollState())) {
                                            Text("${r.rootDirName}/", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                            if (r.children.isEmpty()) {
                                                Text(stringResource(R.string.browser_empty_placeholder_indent), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            } else {
                                                r.children.forEach { child ->
                                                    Text("  $child", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                                                }
                                            }
                                        }
                                    }
                                    ZipFirstLevelResult.Encrypted -> {
                                        if (!zipDetailShowPasswordInput) {
                                            OutlinedButton(onClick = { zipDetailShowPasswordInput = true }) {
                                                Text(stringResource(R.string.browser_enter_password_view))
                                            }
                                        } else {
                                            Column(Modifier.fillMaxWidth()) {
                                                ReliablePasswordInputField(
                                                    value = zipDetailPassword,
                                                    onValueChange = { zipDetailPassword = it },
                                                    label = { Text(stringResource(R.string.browser_enter_password)) },
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                                Spacer(Modifier.height(8.dp))
                                                Row(horizontalArrangement = Arrangement.End) {
                                                    TextButton(onClick = { zipDetailShowPasswordInput = false; zipDetailPassword = "" }) { Text(stringResource(R.string.common_cancel)) }
                                                    Spacer(Modifier.width(8.dp))
                                                    Button(
                                                        onClick = {
                                                            detailScope.launch {
                                                                val res = withContext(Dispatchers.IO) {
                                                                    getArchiveFirstLevelEntries(context, detailTarget.uri, detailTarget.name, zipDetailPassword.toCharArray())
                                                                }
                                                                zipDetailResult = res
                                                                if (res is ZipFirstLevelResult.Error) {
                                                                    Toast.makeText(context, context.getString(R.string.browser_password_wrong_or_unreadable), Toast.LENGTH_SHORT).show()
                                                                }
                                                            }
                                                        },
                                                        enabled = zipDetailPassword.isNotEmpty()
                                                    ) { Text(stringResource(R.string.common_ok)) }
                                                }
                                            }
                                        }
                                    }
                                    ZipFirstLevelResult.Error -> {
                                        Text(stringResource(R.string.browser_cannot_read_archive), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                    }
                                    null -> {
                                        CircularProgressIndicator(Modifier.size(24.dp))
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showFileDetail = null }) { Text(stringResource(R.string.common_close)) }
                    }
                }
            }
        }

        if (showImageExifEditDialog) {
            AlertDialog(
                onDismissRequest = {
                    if (!imageExifSaving) showImageExifEditDialog = false
                },
                title = { Text(stringResource(R.string.browser_type_specific_edit_image_meta_title)) },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    ) {
                        if (imageExifLoading) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.browser_type_specific_loading))
                            }
                        } else {
                            OutlinedTextField(
                                value = imageExifForm.takenTime,
                                onValueChange = {
                                    imageExifForm = imageExifForm.copy(takenTime = it)
                                    imageExifValidation = imageExifValidation.copy(takenTimeError = null)
                                },
                                label = { Text(stringResource(R.string.browser_meta_taken_time_label)) },
                                isError = !imageExifValidation.takenTimeError.isNullOrBlank(),
                                supportingText = {
                                    Text(
                                        imageExifValidation.takenTimeError
                                            ?: stringResource(R.string.browser_meta_taken_time_hint)
                                    )
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = imageExifForm.artist,
                                onValueChange = {
                                    imageExifForm = imageExifForm.copy(artist = it)
                                    imageExifValidation = imageExifValidation.copy(artistError = null)
                                },
                                label = { Text(stringResource(R.string.browser_meta_artist_label)) },
                                isError = !imageExifValidation.artistError.isNullOrBlank(),
                                supportingText = {
                                    imageExifValidation.artistError?.let { Text(it) }
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = imageExifForm.description,
                                onValueChange = {
                                    imageExifForm = imageExifForm.copy(description = it)
                                    imageExifValidation = imageExifValidation.copy(descriptionError = null)
                                },
                                label = { Text(stringResource(R.string.browser_meta_description_label)) },
                                isError = !imageExifValidation.descriptionError.isNullOrBlank(),
                                supportingText = {
                                    imageExifValidation.descriptionError?.let { Text(it) }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = imageExifForm.software,
                                onValueChange = {
                                    imageExifForm = imageExifForm.copy(software = it)
                                    imageExifValidation = imageExifValidation.copy(softwareError = null)
                                },
                                label = { Text(stringResource(R.string.browser_meta_software_label)) },
                                isError = !imageExifValidation.softwareError.isNullOrBlank(),
                                supportingText = {
                                    imageExifValidation.softwareError?.let { Text(it) }
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = imageExifForm.copyright,
                                onValueChange = {
                                    imageExifForm = imageExifForm.copy(copyright = it)
                                    imageExifValidation = imageExifValidation.copy(copyrightError = null)
                                },
                                label = { Text(stringResource(R.string.browser_meta_copyright_label)) },
                                isError = !imageExifValidation.copyrightError.isNullOrBlank(),
                                supportingText = {
                                    imageExifValidation.copyrightError?.let { Text(it) }
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            imageExifSaveError?.takeIf { it.isNotBlank() }?.let { errorText ->
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = stringResource(R.string.browser_type_specific_save_error, errorText),
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (imageExifLoading || imageExifSaving) return@TextButton
                            val validation = validateEditableImageExifForm(context, imageExifForm)
                            imageExifValidation = validation
                            if (validation.hasError) {
                                imageExifSaveError = context.getString(R.string.browser_meta_fix_form_errors)
                                return@TextButton
                            }
                            detailScope.launch {
                                imageExifSaving = true
                                imageExifSaveError = null
                                val saveError = withContext(Dispatchers.IO) {
                                    saveEditableImageExif(context, detailTarget.uri, imageExifForm)
                                }
                                imageExifSaving = false
                                if (saveError == null) {
                                    showImageExifEditDialog = false
                                    Toast.makeText(context, context.getString(R.string.browser_type_specific_save_success), Toast.LENGTH_SHORT).show()
                                    typeSpecificMetaLoaded = false
                                    loadTypeSpecificMetadataNow(forceReload = true)
                                } else {
                                    imageExifSaveError = saveError
                                }
                            }
                        },
                        enabled = !imageExifLoading && !imageExifSaving
                    ) {
                        Text(if (imageExifSaving) stringResource(R.string.browser_type_specific_saving) else stringResource(R.string.common_save))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { if (!imageExifSaving) showImageExifEditDialog = false },
                        enabled = !imageExifSaving
                    ) {
                        Text(stringResource(R.string.common_cancel))
                    }
                }
            )
        }

        if (showAudioMetaEditDialog) {
            AlertDialog(
                onDismissRequest = {
                    if (!audioMetaSaving) showAudioMetaEditDialog = false
                },
                title = { Text(stringResource(R.string.browser_type_specific_edit_audio_meta_title)) },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    ) {
                        if (audioMetaLoading) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.browser_type_specific_loading))
                            }
                        } else {
                            OutlinedTextField(
                                value = audioMetaForm.title,
                                onValueChange = {
                                    audioMetaForm = audioMetaForm.copy(title = it)
                                    audioMetaValidation = audioMetaValidation.copy(titleError = null)
                                },
                                label = { Text(stringResource(R.string.browser_meta_title_label)) },
                                isError = !audioMetaValidation.titleError.isNullOrBlank(),
                                supportingText = {
                                    audioMetaValidation.titleError?.let { Text(it) }
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = audioMetaForm.artist,
                                onValueChange = {
                                    audioMetaForm = audioMetaForm.copy(artist = it)
                                    audioMetaValidation = audioMetaValidation.copy(artistError = null)
                                },
                                label = { Text(stringResource(R.string.browser_meta_artist_label)) },
                                isError = !audioMetaValidation.artistError.isNullOrBlank(),
                                supportingText = {
                                    audioMetaValidation.artistError?.let { Text(it) }
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = audioMetaForm.album,
                                onValueChange = {
                                    audioMetaForm = audioMetaForm.copy(album = it)
                                    audioMetaValidation = audioMetaValidation.copy(albumError = null)
                                },
                                label = { Text(stringResource(R.string.browser_meta_album_label)) },
                                isError = !audioMetaValidation.albumError.isNullOrBlank(),
                                supportingText = {
                                    audioMetaValidation.albumError?.let { Text(it) }
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            audioMetaSaveError?.takeIf { it.isNotBlank() }?.let { errorText ->
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = stringResource(R.string.browser_type_specific_save_error, errorText),
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (audioMetaLoading || audioMetaSaving) return@TextButton
                            val validation = validateEditableAudioAnnotationForm(context, audioMetaForm)
                            audioMetaValidation = validation
                            if (validation.hasError) {
                                audioMetaSaveError = context.getString(R.string.browser_meta_fix_form_errors)
                                return@TextButton
                            }
                            detailScope.launch {
                                audioMetaSaving = true
                                audioMetaSaveError = null
                                val saveError = withContext(Dispatchers.IO) {
                                    if (isMp3AudioFile(detailTarget.name, mimeType)) {
                                        val writeError = saveEditableAudioAnnotationToMp3(
                                            context,
                                            detailTarget.uri,
                                            audioMetaForm
                                        )
                                        if (writeError == null) {
                                            prefs.setAudioMetaAnnotation(detailTarget.uri.toString(), null)
                                        }
                                        writeError
                                    } else if (supportsJaudiotaggerWrite(detailTarget.name, mimeType)) {
                                        val writeError = saveEditableAudioAnnotationWithJaudiotagger(
                                            context,
                                            detailTarget.uri,
                                            audioMetaForm,
                                            detailTarget.name
                                        )
                                        if (writeError == null) {
                                            prefs.setAudioMetaAnnotation(detailTarget.uri.toString(), null)
                                        }
                                        writeError
                                    } else {
                                        runCatching {
                                            prefs.setAudioMetaAnnotation(
                                                detailTarget.uri.toString(),
                                                AudioMetaAnnotation(
                                                    title = audioMetaForm.title,
                                                    artist = audioMetaForm.artist,
                                                    album = audioMetaForm.album
                                                )
                                            )
                                        }.exceptionOrNull()?.let { e ->
                                            "${e.javaClass.simpleName}: ${e.message ?: "unknown error"}"
                                        }
                                    }
                                }
                                audioMetaSaving = false
                                if (saveError == null) {
                                    showAudioMetaEditDialog = false
                                    Toast.makeText(context, context.getString(R.string.browser_type_specific_save_success), Toast.LENGTH_SHORT).show()
                                    typeSpecificMetaLoaded = false
                                    loadTypeSpecificMetadataNow(forceReload = true)
                                } else {
                                    audioMetaSaveError = saveError
                                }
                            }
                        },
                        enabled = !audioMetaLoading && !audioMetaSaving
                    ) {
                        Text(if (audioMetaSaving) stringResource(R.string.browser_type_specific_saving) else stringResource(R.string.common_save))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { if (!audioMetaSaving) showAudioMetaEditDialog = false },
                        enabled = !audioMetaSaving
                    ) {
                        Text(stringResource(R.string.common_cancel))
                    }
                }
            )
        }
    }

    if (showQuickCopyConfirm || showQuickMoveConfirm) {
        val isCopy = showQuickCopyConfirm
        val title = if (isCopy) context.getString(R.string.browser_confirm_copy_title) else context.getString(R.string.browser_confirm_move_title)
        val actionLabel = if (isCopy) context.getString(R.string.pending_list_action_copy) else context.getString(R.string.pending_list_action_move)
        AlertDialog(
            onDismissRequest = {
                showQuickCopyConfirm = false
                showQuickMoveConfirm = false
            },
            title = { Text(title) },
            text = {
                Column {
                    Text(
                        context.getString(R.string.browser_pending_compress_confirm, pendingList.size),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    val preview = pendingList.take(20)
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        preview.forEach { item ->
                            Text(
                                item.name,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (pendingList.size > preview.size) {
                            Text(
                                context.getString(R.string.browser_pending_more_items, pendingList.size - preview.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showQuickCopyConfirm = false
                        showQuickMoveConfirm = false
                        if (isCopy) onCopyPendingToCurrentDir() else onMovePendingToCurrentDir()
                    }
                ) { Text(actionLabel) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showQuickCopyConfirm = false
                        showQuickMoveConfirm = false
                    }
                ) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileItem(
    model: DocumentFileModel,
    hasPreferredExternalApp: Boolean = false,
    isInPendingList: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDoubleClick: (() -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    var pendingClickJob by remember { mutableStateOf<Job?>(null) }
    val compressedPayloadExt = getCompressedPayloadExtension(model.name)
    val showCompressedTag = !model.isDirectory && compressedPayloadExt != null
    val iconName = if (showCompressedTag) "payload.${compressedPayloadExt}" else model.name
    val icon = when {
        model.isDirectory -> Icons.Default.Folder
        iconName.endsWith(".gpg", ignoreCase = true) -> Icons.Default.Lock
        iconName.endsWith(".pass", ignoreCase = true) -> Icons.Default.Lock
        iconName.endsWith(".qx", ignoreCase = true) -> Icons.Default.LockOpen
        iconName.endsWith(".pic", ignoreCase = true) || iconName.endsWith(".png", ignoreCase = true) || iconName.endsWith(".jpg", ignoreCase = true) || iconName.endsWith(".jpeg", ignoreCase = true) || iconName.endsWith(".gif", ignoreCase = true) || iconName.endsWith(".webp", ignoreCase = true) || iconName.endsWith(".bmp", ignoreCase = true) || iconName.endsWith(".heic", ignoreCase = true) -> Icons.Default.Image
        iconName.endsWith(".vid", ignoreCase = true) || iconName.endsWith(".mp4", ignoreCase = true) || iconName.endsWith(".mkv", ignoreCase = true) || iconName.endsWith(".avi", ignoreCase = true) || iconName.endsWith(".mov", ignoreCase = true) || iconName.endsWith(".webm", ignoreCase = true) -> Icons.Default.PlayArrow
        iconName.endsWith(".html", ignoreCase = true) || iconName.endsWith(".htm", ignoreCase = true) -> Icons.AutoMirrored.Filled.Article
        isEpubFile(iconName) || isTxtFile(iconName) || isLlmFile(iconName) -> Icons.AutoMirrored.Filled.MenuBook
        isPdfFile(iconName) -> Icons.Default.PictureAsPdf
        isExtractableArchive(iconName) -> Icons.Default.Archive
        iconName.endsWith(".md", ignoreCase = true) || iconName.endsWith(".rst", ignoreCase = true) -> Icons.Default.Description
        else -> Icons.AutoMirrored.Filled.InsertDriveFile
    }
    val iconTint = when {
        model.isDirectory -> MaterialTheme.colorScheme.primary
        iconName.endsWith(".gpg", ignoreCase = true) -> Color.Red
        iconName.endsWith(".pass", ignoreCase = true) -> Color.Red
        iconName.endsWith(".qx", ignoreCase = true) -> Color.Red
        iconName.endsWith(".pic", ignoreCase = true) || iconName.endsWith(".png", ignoreCase = true) || iconName.endsWith(".jpg", ignoreCase = true) || iconName.endsWith(".jpeg", ignoreCase = true) || iconName.endsWith(".gif", ignoreCase = true) || iconName.endsWith(".webp", ignoreCase = true) || iconName.endsWith(".bmp", ignoreCase = true) || iconName.endsWith(".heic", ignoreCase = true) -> MaterialTheme.colorScheme.tertiary
        iconName.endsWith(".vid", ignoreCase = true) || iconName.endsWith(".mp4", ignoreCase = true) || iconName.endsWith(".mkv", ignoreCase = true) || iconName.endsWith(".avi", ignoreCase = true) || iconName.endsWith(".mov", ignoreCase = true) || iconName.endsWith(".webm", ignoreCase = true) -> MaterialTheme.colorScheme.secondary
        isEpubFile(iconName) -> Color(0xFF8B4513)
        isPdfFile(iconName) -> Color(0xFFD32F2F)
        isExtractableArchive(iconName) -> MaterialTheme.colorScheme.tertiary
        iconName.endsWith(".md", ignoreCase = true) || iconName.endsWith(".rst", ignoreCase = true) -> Color.Blue
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val clickableOnClick: () -> Unit = if (onDoubleClick != null) {
        {
            if (pendingClickJob != null) {
                pendingClickJob?.cancel()
                pendingClickJob = null
                onDoubleClick()
            } else {
                pendingClickJob = scope.launch {
                    delay(300)
                    pendingClickJob = null
                    onClick()
                }
            }
        }
    } else {
        { onClick() }
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = clickableOnClick, onLongClick = onLongClick)
            .padding(4.dp),
        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(32.dp)) {
                Icon(
                    icon,
                    contentDescription = null,
                    Modifier
                        .size(32.dp)
                        .align(Alignment.Center),
                    tint = iconTint
                )
                if (showCompressedTag) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(14.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.tertiaryContainer)
                            .border(1.dp, MaterialTheme.colorScheme.surface, RoundedCornerShape(4.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Archive,
                            contentDescription = null,
                            modifier = Modifier.size(10.dp),
                            tint = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
                if (hasPreferredExternalApp) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(8.dp)
                            .clip(RoundedCornerShape(99.dp))
                            .background(MaterialTheme.colorScheme.secondary)
                    )
                }
            }
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    model.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = fileItemSubtitle(model),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isInPendingList) {
                Spacer(Modifier.size(8.dp))
                Icon(
                    Icons.AutoMirrored.Filled.PlaylistAdd,
                    contentDescription = stringResource(R.string.browser_already_in_pending_list),
                    Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickObfuscatePasswordDialog(
    isMasked: Boolean,
    fileName: String,
    password: String,
    confirmPassword: String,
    inProgress: Boolean = false,
    onPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val passwordLabelText = stringResource(R.string.browser_enter_password)
    val passwordConfirmed = isRequiredPasswordConfirmed(password, confirmPassword)
    Dialog(onDismissRequest = { if (!inProgress) onDismiss() }) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(Modifier.padding(24.dp)) {
                Text(
                    context.getString(R.string.context_quick_mask),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(fileName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Text(
                    if (isMasked) {
                        context.getString(R.string.browser_mask_remove_hint)
                    } else {
                        context.getString(R.string.browser_mask_add_hint)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                PasswordConfirmationFields(
                    password = password,
                    confirmPassword = confirmPassword,
                    onPasswordChange = onPasswordChange,
                    onConfirmPasswordChange = onConfirmPasswordChange,
                    passwordLabel = passwordLabelText,
                    confirmLabel = context.getString(R.string.browser_password_confirm_label),
                    enabled = !inProgress,
                    allowBlank = false
                )
                if (inProgress) {
                    Spacer(Modifier.height(16.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.browser_masking), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(24.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss, enabled = !inProgress) { Text(stringResource(R.string.common_cancel)) }
                    Button(onClick = { onConfirm(password) }, enabled = !inProgress && passwordConfirmed) { Text(stringResource(R.string.common_ok)) }
                }
            }
        }
    }
}

private fun isRequiredPasswordConfirmed(password: String, confirmPassword: String): Boolean {
    return password.isNotBlank() && password == confirmPassword
}

internal fun isOptionalPasswordConfirmed(password: String, confirmPassword: String): Boolean {
    return password.isBlank() || password == confirmPassword
}

@Composable
private fun PasswordConfirmationFields(
    password: String,
    confirmPassword: String,
    onPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    passwordLabel: String,
    confirmLabel: String,
    enabled: Boolean,
    allowBlank: Boolean
) {
    val showMismatch = password.isNotEmpty() && confirmPassword.isNotEmpty() && (
        !password.startsWith(confirmPassword) ||
            (confirmPassword.length >= password.length && password != confirmPassword)
        )
    val showConfirmField = password.isNotEmpty() || !allowBlank

    ReliablePasswordInputField(
        value = password,
        onValueChange = onPasswordChange,
        label = { Text(passwordLabel) },
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled
    )
    if (showConfirmField) {
        Spacer(Modifier.height(12.dp))
        ReliablePasswordInputField(
            value = confirmPassword,
            onValueChange = onConfirmPasswordChange,
            label = { Text(confirmLabel) },
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled
        )
    }

    if (showConfirmField) {
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp)
        ) {
            if (showMismatch) {
                Text(
                    stringResource(R.string.browser_password_mismatch),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

private fun readClipboardPassword(context: Context): String? {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
    val clip = clipboard.primaryClip ?: return null
    if (clip.itemCount <= 0) return null
    val text = clip.getItemAt(0).coerceToText(context)?.toString() ?: return null
    return text.ifBlank { null }
}

private enum class PasswordKeyCategory { DIGIT, UPPER, LOWER, SYMBOL }

private val digitKeys = "1234567890".toList()
private val upperKeys = ('A'..'Z').toList()
private val lowerKeys = ('a'..'z').toList()
private val symbolKeys = listOf(
    '~', '!', '@', '#', '$', '%', '^', '&', '*',
    '(', ')', '_', '+', '-', '=', '[', ']', '{', '}',
    '|', ';', ':', '\'', '"', ',', '.', '<', '>', '/', '?', '`', '\\'
)

private fun categoryPages(category: PasswordKeyCategory): List<List<Char>> {
    val keys = when (category) {
        PasswordKeyCategory.DIGIT -> digitKeys
        PasswordKeyCategory.UPPER -> upperKeys
        PasswordKeyCategory.LOWER -> lowerKeys
        PasswordKeyCategory.SYMBOL -> symbolKeys
    }
    return keys.chunked(30)
}

@Composable
private fun PasswordGridPage(
    keys: List<Char>,
    enabled: Boolean,
    onPress: (Char) -> Unit
) {
    val rows = keys.chunked(5)
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        rows.forEach { rowKeys ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                rowKeys.forEach { key ->
                    OutlinedButton(
                        onClick = { onPress(key) },
                        enabled = enabled,
                        modifier = Modifier
                            .padding(horizontal = 2.dp, vertical = 1.dp)
                            .width(50.dp)
                            .height(32.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(key.toString(), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
internal fun ReliablePasswordInputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: @Composable (() -> Unit),
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    supportingText: @Composable (() -> Unit)? = null
) {
    val context = LocalContext.current
    var keyboardVisible by remember { mutableStateOf(false) }
    var category by remember { mutableStateOf(PasswordKeyCategory.DIGIT) }
    var pageIndex by remember { mutableIntStateOf(0) }
    val pages = remember(category) { categoryPages(category) }
    if (pageIndex > pages.lastIndex) pageIndex = 0
    val pageKeys = pages.getOrElse(pageIndex) { emptyList() }
    val maskedValue = when {
        value.isEmpty() -> ""
        value.length <= 36 -> "*".repeat(value.length)
        else -> "${"*".repeat(36)}…(${value.length})"
    }

    Column(modifier = modifier) {
        Box(modifier = Modifier.padding(bottom = 6.dp)) {
            label()
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .clickable(enabled = enabled) { keyboardVisible = true }
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Text(
                text = if (maskedValue.isEmpty()) stringResource(R.string.browser_empty_placeholder) else maskedValue,
                color = if (maskedValue.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        supportingText?.invoke()
    }

    if (keyboardVisible) {
        Dialog(
            onDismissRequest = { keyboardVisible = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.BottomCenter
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(430.dp),
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(56.dp)
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(999.dp))
                                    .border(0.dp, Color.Transparent, RoundedCornerShape(999.dp))
                            )
                            Box(
                                modifier = Modifier
                                    .width(56.dp)
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(MaterialTheme.colorScheme.outlineVariant)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier.height(32.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    text = if (maskedValue.isEmpty()) stringResource(R.string.browser_empty_placeholder) else maskedValue,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (maskedValue.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(
                                    onClick = {
                                        val pasted = readClipboardPassword(context)
                                        if (pasted == null) {
                                            Toast.makeText(context, context.getString(R.string.pwd_kb_clipboard_empty), Toast.LENGTH_SHORT).show()
                                        } else {
                                            onValueChange(pasted)
                                            Toast.makeText(context, context.getString(R.string.pwd_kb_pasted), Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    enabled = enabled,
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(stringResource(R.string.pwd_kb_paste))
                                }
                                TextButton(onClick = { keyboardVisible = false }, enabled = enabled) {
                                    Text(stringResource(R.string.pwd_kb_done))
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            OutlinedButton(
                                onClick = { category = PasswordKeyCategory.DIGIT; pageIndex = 0 },
                                enabled = enabled,
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp)
                            ) { Text(stringResource(R.string.pwd_kb_digits), style = MaterialTheme.typography.labelSmall) }
                            OutlinedButton(
                                onClick = { category = PasswordKeyCategory.UPPER; pageIndex = 0 },
                                enabled = enabled,
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp)
                            ) { Text(stringResource(R.string.pwd_kb_upper), style = MaterialTheme.typography.labelSmall) }
                            OutlinedButton(
                                onClick = { category = PasswordKeyCategory.LOWER; pageIndex = 0 },
                                enabled = enabled,
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp)
                            ) { Text(stringResource(R.string.pwd_kb_lower), style = MaterialTheme.typography.labelSmall) }
                            OutlinedButton(
                                onClick = { category = PasswordKeyCategory.SYMBOL; pageIndex = 0 },
                                enabled = enabled,
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp)
                            ) { Text(stringResource(R.string.pwd_kb_symbols), style = MaterialTheme.typography.labelSmall) }
                        }

                        if (pages.size > 1) {
                            Spacer(Modifier.height(2.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                pages.indices.forEach { idx ->
                                    OutlinedButton(
                                        onClick = { pageIndex = idx },
                                        enabled = enabled,
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = if (idx == pageIndex) stringResource(R.string.pwd_kb_subclass_active, idx + 1) else stringResource(R.string.pwd_kb_subclass, idx + 1),
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(4.dp))
                        PasswordGridPage(
                            keys = pageKeys,
                            enabled = enabled,
                            onPress = { ch -> onValueChange(value + ch) }
                        )

                        Spacer(Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            OutlinedButton(
                                onClick = { onValueChange(value + " ") },
                                enabled = enabled,
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp)
                            ) { Text(stringResource(R.string.pwd_kb_space), style = MaterialTheme.typography.labelSmall) }
                            OutlinedButton(
                                onClick = { if (value.isNotEmpty()) onValueChange(value.dropLast(1)) },
                                enabled = enabled && value.isNotEmpty(),
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp)
                            ) { Text(stringResource(R.string.pwd_kb_backspace), style = MaterialTheme.typography.labelSmall) }
                            OutlinedButton(
                                onClick = { onValueChange("") },
                                enabled = enabled && value.isNotEmpty(),
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp)
                            ) { Text(stringResource(R.string.pwd_kb_clear), style = MaterialTheme.typography.labelSmall) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GpgPasswordDialog(
    isDecrypt: Boolean,
    fileName: String,
    password: String,
    passwordLabel: String? = null,
    inProgress: Boolean = false,
    onPasswordChange: (String) -> Unit,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val resolvedPasswordLabel = passwordLabel ?: stringResource(R.string.browser_enter_password)
    Dialog(onDismissRequest = { if (!inProgress) onDismiss() }) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(Modifier.padding(24.dp)) {
                Text(
                    if (isDecrypt) stringResource(R.string.context_gpg_decrypt) else stringResource(R.string.context_gpg_encrypt),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(fileName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                ReliablePasswordInputField(
                    value = password,
                    onValueChange = onPasswordChange,
                    label = { Text(resolvedPasswordLabel) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !inProgress
                )
                if (inProgress) {
                    Spacer(Modifier.height(16.dp))
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (isDecrypt) stringResource(R.string.browser_decrypting) else stringResource(R.string.browser_encrypting),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(24.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss, enabled = !inProgress) { Text(stringResource(R.string.common_cancel)) }
                    Button(
                        onClick = { onConfirm(password) },
                        enabled = !inProgress
                    ) { Text(stringResource(R.string.common_ok)) }
                }
            }
        }
    }
}

@Composable
fun GpgEncryptDialog(
    fileName: String,
    keys: List<Pair<Long, String>>,
    selectedKeyId: Long?,
    password: String,
    confirmPassword: String,
    inProgress: Boolean = false,
    onSelectPassword: () -> Unit,
    onSelectKey: (Long) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onConfirm: (String, Long?) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val passwordLabelText = stringResource(R.string.browser_enter_password)
    val useSymmetric = selectedKeyId == null
    val symmetricPasswordReady = isRequiredPasswordConfirmed(password, confirmPassword)
    Dialog(onDismissRequest = { if (!inProgress) onDismiss() }) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(Modifier.padding(24.dp)) {
                Text(
                    context.getString(R.string.context_gpg_encrypt),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(fileName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !inProgress) { onSelectPassword() },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.material3.RadioButton(
                        selected = selectedKeyId == null,
                        onClick = if (inProgress) null else onSelectPassword
                    )
                    Text(stringResource(R.string.browser_gpg_use_symmetric_encrypt), style = MaterialTheme.typography.bodyMedium)
                }
                if (useSymmetric) {
                    Spacer(Modifier.height(12.dp))
                    PasswordConfirmationFields(
                        password = password,
                        confirmPassword = confirmPassword,
                        onPasswordChange = onPasswordChange,
                        onConfirmPasswordChange = onConfirmPasswordChange,
                        passwordLabel = passwordLabelText,
                        confirmLabel = context.getString(R.string.browser_password_confirm_label),
                        enabled = !inProgress,
                        allowBlank = false
                    )
                } else {
                    ReliablePasswordInputField(
                        value = password,
                        onValueChange = onPasswordChange,
                        label = { Text(stringResource(R.string.browser_enter_password)) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !inProgress
                    )
                }
                if (keys.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.browser_gpg_or_select_public_key), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(8.dp))
                    keys.forEach { (keyId, desc) ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !inProgress) { onSelectKey(keyId) },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.material3.RadioButton(
                                selected = selectedKeyId == keyId,
                                onClick = if (inProgress) null else { { onSelectKey(keyId) } }
                            )
                            Text(desc, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
                if (inProgress) {
                    Spacer(Modifier.height(16.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.browser_encrypting), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(24.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss, enabled = !inProgress) { Text(stringResource(R.string.common_cancel)) }
                    Button(
                        onClick = { onConfirm(password, selectedKeyId) },
                        enabled = !inProgress && (selectedKeyId != null || symmetricPasswordReady)
                    ) { Text(stringResource(R.string.common_ok)) }
                }
            }
        }
    }
}

/** 直接编辑 .pass 文件：解密后显示可编辑文本，存盘时若已修改则重新加密写回。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PassEditScreen(
    fileName: String,
    initialDecryptedBytes: ByteArray,
    onSave: (ByteArray) -> Unit,
    onBack: () -> Unit
) {
    val scrollState = rememberScrollState()
    var textContent by remember(initialDecryptedBytes) {
        mutableStateOf(initialDecryptedBytes.decodeToString())
    }
    var saveInProgress by remember { mutableStateOf(false) }

    BackHandler { if (!saveInProgress) onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(fileName, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = { if (!saveInProgress) onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                    if (saveInProgress) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    TextButton(
                        onClick = {
                            if (saveInProgress) return@TextButton
                            val newBytes = textContent.encodeToByteArray()
                            if (newBytes.contentEquals(initialDecryptedBytes)) {
                                onBack()
                            } else {
                                saveInProgress = true
                                onSave(newBytes)
                            }
                        },
                        enabled = !saveInProgress
                    ) { Text(stringResource(R.string.common_save)) }
                }
            )
        }
    ) { padding ->
        OutlinedTextField(
            value = textContent,
            onValueChange = { textContent = it },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState),
            minLines = 20,
            maxLines = Int.MAX_VALUE
        )
    }
}

@Composable
fun GpgMethodDialog(
    isDecrypt: Boolean,
    fileName: String,
    hasPublicKeys: Boolean,
    hasSecretKeys: Boolean,
    onSymmetric: () -> Unit,
    onPublicKey: () -> Unit,
    onSecretKey: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(Modifier.padding(24.dp)) {
                Text(
                    if (isDecrypt) context.getString(R.string.context_gpg_decrypt) else context.getString(R.string.context_gpg_encrypt),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(fileName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                if (isDecrypt) {
                    Button(onClick = onSymmetric, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.browser_gpg_password_decrypt)) }
                    if (hasSecretKeys) {
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = onSecretKey, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.browser_gpg_secret_key_decrypt)) }
                    }
                } else {
                    Button(onClick = onSymmetric, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.browser_gpg_symmetric_encrypt)) }
                    if (hasPublicKeys) {
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = onPublicKey, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.browser_op_public_key_encrypt)) }
                    }
                }
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
            }
        }
    }
}

@Composable
fun GpgPublicKeyPickerDialog(
    keys: List<Pair<Long, String>>,
    fileName: String,
    onConfirm: (keyId: Long, keyDesc: String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedKeyId by remember { mutableStateOf<Long?>(keys.firstOrNull()?.first) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(Modifier.padding(24.dp)) {
                Text(stringResource(R.string.browser_op_public_key_encrypt), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                Text(fileName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                keys.forEach { (keyId, desc) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { selectedKeyId = keyId },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.RadioButton(
                            selected = selectedKeyId == keyId,
                            onClick = { selectedKeyId = keyId }
                        )
                        Text(desc, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp))
                    }
                }
                Spacer(Modifier.height(24.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
                    Button(onClick = {
                        val kid = selectedKeyId
                        val desc = keys.find { it.first == kid }?.second ?: ""
                        if (kid != null) onConfirm(kid, desc)
                    }) { Text(stringResource(R.string.quick_crypto_action_encrypt_short)) }
                }
            }
        }
    }
}
@Composable
fun CacheManagementDialog(
    context: Context,
    onDismiss: () -> Unit
) {
    var entries by remember { mutableStateOf<List<CacheEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        entries = withContext(Dispatchers.IO) { getCacheEntries(context) }
        loading = false
    }
    fun refresh() {
        scope.launch {
            entries = withContext(Dispatchers.IO) { getCacheEntries(context) }
        }
    }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                Modifier
                    .padding(24.dp)
                    .widthIn(max = 400.dp)
            ) {
                Text(stringResource(R.string.cache_management_title), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(16.dp))
                if (loading) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.padding(24.dp))
                    }
                } else {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        entries.forEach { entry ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(entry.displayName, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                                    if (entry.description.isNotBlank()) {
                                        Text(
                                            entry.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(formatSize(entry.sizeBytes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                TextButton(
                                    onClick = {
                                        scope.launch {
                                            withContext(Dispatchers.IO) { clearCacheEntry(context, entry) }
                                            refresh()
                                            Toast.makeText(context, context.getString(R.string.cache_cleared_entry, entry.displayName), Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                ) { Text(stringResource(R.string.cache_clear_entry)) }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                if (!loading && entries.isNotEmpty()) {
                    Button(
                        onClick = {
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    entries.forEach { clearCacheEntry(context, it) }
                                }
                                refresh()
                                Toast.makeText(context, context.getString(R.string.cache_clear_all_done), Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.cache_clear_all)) }
                    Spacer(Modifier.height(8.dp))
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) }
            }
        }
    }
}

private sealed class PendingDelete {
    object Secret : PendingDelete()
    object AllPublic : PendingDelete()
    data class SinglePublic(val keyId: Long) : PendingDelete()
}

@Composable
fun KeyManagementDialog(
    context: Context,
    onDismiss: () -> Unit,
    onKeysChanged: () -> Unit = {},
    onOpenPubkeyShare: (() -> Unit)? = null
) {
    var publicKeys by remember { mutableStateOf<List<KeyInfo>>(emptyList()) }
    var secretKeys by remember { mutableStateOf<List<KeyInfo>>(emptyList()) }
    var keyFiles by remember { mutableStateOf(emptyList<Pair<String, Long>>()) }
    var loading by remember { mutableStateOf(true) }
    var refreshTrigger by remember { mutableStateOf(0) }
    var importAsPrivate by remember { mutableStateOf(true) }
    var pendingDelete by remember { mutableStateOf<PendingDelete?>(null) }
    var deleteConfirmInput by remember { mutableStateOf("") }
    var pendingExportFilename by remember { mutableStateOf("") }
    var pendingExportSecret by remember { mutableStateOf(false) }
    var pendingExportKeyId by remember { mutableStateOf<Long?>(null) }
    var showGenerateKeyDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val keyImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val asPriv = importAsPrivate
            scope.launch {
                val (ok, err) = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        val bytes = input.readBytes()
                        if (asPriv) {
                            parseSecretKeyRingFromStream(bytes.inputStream())?.let { saveSecretKeyRing(context, it) }
                                ?: Pair(false, context.getString(R.string.gpg_parse_secret_failed))
                        } else {
                            parsePublicKeyRingFromStream(bytes.inputStream())?.let { mergePublicKeyRing(context, it) }
                                ?: Pair(false, context.getString(R.string.gpg_parse_public_failed))
                        }
                    } ?: Pair(false, context.getString(R.string.gpg_read_file_failed))
                }
                if (ok) {
                    Toast.makeText(context, context.getString(R.string.gpg_import_success), Toast.LENGTH_SHORT).show()
                    refreshTrigger++
                    onKeysChanged()
                } else {
                    Toast.makeText(context, context.getString(R.string.gpg_import_failed, err), Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    val keyExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pgp-keys")) { uri ->
        if (uri != null && pendingExportFilename.isNotEmpty()) {
            scope.launch {
                val bytes = withContext(Dispatchers.IO) {
                    when {
                        pendingExportSecret -> getSecretKeyRingBytes(context)
                        pendingExportKeyId != null -> getSinglePublicKeyRingBytes(context, pendingExportKeyId!!)
                        else -> getAllPublicKeyRingsBytes(context)
                    }
                }
                if (bytes != null) {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                    }
                    Toast.makeText(context, context.getString(R.string.gpg_export_success), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, context.getString(R.string.gpg_export_failed), Toast.LENGTH_SHORT).show()
                }
                pendingExportFilename = ""
                pendingExportKeyId = null
                pendingExportSecret = false
            }
        }
    }
    LaunchedEffect(refreshTrigger) {
        loading = true
        withContext(Dispatchers.IO) {
            val pubRings = loadPublicKeyRings(context)
            val secRings = loadSecretKeyRings(context)
            publicKeys = listPublicKeyInfos(pubRings)
            secretKeys = listSecretKeyInfos(secRings)
            keyFiles = listGpgKeyFiles(context)
        }
        loading = false
    }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(Modifier.padding(24.dp)) {
                Text(stringResource(R.string.gpg_keys_title), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                Text(stringResource(R.string.gpg_keys_subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { showGenerateKeyDialog = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.gpg_generate_keypair)) }
                if (onOpenPubkeyShare != null) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { onDismiss(); onOpenPubkeyShare() }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.gpg_pubkey_share_git)) }
                }
                Spacer(Modifier.height(16.dp))
                if (loading) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(32.dp))
                    }
                } else {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.gpg_public_keys_section), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                            Row {
                                TextButton(onClick = { importAsPrivate = false; keyImportLauncher.launch(arrayOf("application/pgp-keys", "application/octet-stream", "*/*")) }) { Text(stringResource(R.string.gpg_import_public_key)) }
                                if (publicKeys.isNotEmpty()) {
                                    TextButton(onClick = { pendingDelete = PendingDelete.AllPublic; deleteConfirmInput = "" }) { Text(stringResource(R.string.gpg_delete_all_public_keys)) }
                                    TextButton(onClick = {
                                        pendingExportFilename = "pubring.asc"
                                        pendingExportSecret = false
                                        pendingExportKeyId = null
                                        keyExportLauncher.launch("pubring.asc")
                                    }) { Text(stringResource(R.string.gpg_export_all_public_keys)) }
                                }
                            }
                        }
                        if (publicKeys.isEmpty()) Text(stringResource(R.string.gpg_none), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        else publicKeys.forEachIndexed { _, k ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("${k.keyIdHex} ${k.primaryUserId}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                                val keyId = k.keyId
                                TextButton(onClick = {
                                        pendingExportFilename = "pubkey_${k.keyIdHex.replace("0x", "")}.asc"
                                        pendingExportSecret = false
                                        pendingExportKeyId = keyId
                                        keyExportLauncher.launch(pendingExportFilename)
                                }) { Text(stringResource(R.string.gpg_export_key), style = MaterialTheme.typography.labelSmall) }
                                if (publicKeys.size > 1) {
                                    TextButton(onClick = { pendingDelete = PendingDelete.SinglePublic(keyId); deleteConfirmInput = "" }) { Text(stringResource(R.string.gpg_delete_key_short), style = MaterialTheme.typography.labelSmall) }
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.gpg_secret_keys_section), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                            Row {
                                TextButton(onClick = { importAsPrivate = true; keyImportLauncher.launch(arrayOf("application/pgp-keys", "application/octet-stream", "*/*")) }) { Text(stringResource(R.string.gpg_import_secret_key)) }
                                if (secretKeys.isNotEmpty()) {
                                    TextButton(onClick = {
                                        pendingExportFilename = "secring.asc"
                                        pendingExportSecret = true
                                        pendingExportKeyId = null
                                        keyExportLauncher.launch("secring.asc")
                                    }) { Text(stringResource(R.string.gpg_export_secret_key)) }
                                    TextButton(onClick = { pendingDelete = PendingDelete.Secret; deleteConfirmInput = "" }) { Text(stringResource(R.string.common_delete)) }
                                }
                            }
                        }
                        if (secretKeys.isEmpty()) Text(stringResource(R.string.gpg_none), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        else secretKeys.forEach { k ->
                            Text("${k.keyIdHex} ${k.primaryUserId}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(stringResource(R.string.gpg_key_files_section), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        if (keyFiles.isEmpty()) Text(stringResource(R.string.gpg_none), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        else for (pair in keyFiles) {
                            Text("${pair.first} (${pair.second} B)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) }
            }
        }
    }
    pendingDelete?.let { pending ->
        val (title, message) = when (pending) {
            is PendingDelete.Secret -> context.getString(R.string.gpg_confirm_delete_secret_title) to context.getString(R.string.gpg_confirm_delete_secret_message)
            is PendingDelete.AllPublic -> context.getString(R.string.gpg_confirm_delete_all_public_title) to context.getString(R.string.gpg_confirm_delete_all_public_message)
            is PendingDelete.SinglePublic -> context.getString(R.string.gpg_confirm_delete_public_title) to context.getString(R.string.gpg_confirm_delete_public_message)
        }
        AlertDialog(
            onDismissRequest = { pendingDelete = null; deleteConfirmInput = "" },
            title = { Text(title) },
            text = {
                Column {
                    Text(message)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = deleteConfirmInput,
                        onValueChange = { deleteConfirmInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.gpg_confirm_delete_input_label)) },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (deleteConfirmInput.trim().lowercase() != "yes") return@Button
                        scope.launch {
                            val ok = withContext(Dispatchers.IO) {
                                when (pending) {
                                    is PendingDelete.Secret -> deleteSecretKeys(context)
                                    is PendingDelete.AllPublic -> deleteAllPublicKeys(context)
                                    is PendingDelete.SinglePublic -> deletePublicKeyById(context, pending.keyId).first
                                }
                            }
                            if (ok) {
                                Toast.makeText(context, context.getString(R.string.browser_deleted), Toast.LENGTH_SHORT).show()
                                refreshTrigger++
                                onKeysChanged()
                            } else {
                                Toast.makeText(context, context.getString(R.string.common_delete_failed), Toast.LENGTH_SHORT).show()
                            }
                            pendingDelete = null
                            deleteConfirmInput = ""
                        }
                    },
                    enabled = deleteConfirmInput.trim().lowercase() == "yes"
                ) { Text(stringResource(R.string.gpg_confirm_delete_action)) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null; deleteConfirmInput = "" }) { Text(stringResource(R.string.common_cancel)) } }
        )
    }
    if (showGenerateKeyDialog) {
        GenerateKeyDialog(
            context = context,
            hasExistingKeys = secretKeys.isNotEmpty() || publicKeys.isNotEmpty(),
            onDismiss = { showGenerateKeyDialog = false },
            onSuccess = { refreshTrigger++; onKeysChanged() }
        )
    }
}

@Composable
fun GenerateKeyDialog(
    context: Context,
    hasExistingKeys: Boolean,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit = {}
) {
    var identity by remember { mutableStateOf("") }
    var passphrase by remember { mutableStateOf("") }
    var confirmPassphrase by remember { mutableStateOf("") }
    var confirmKeyRisk by remember { mutableStateOf(false) }
    var generatingInProgress by remember { mutableStateOf(false) }
    val passphraseConfirmed = isOptionalPasswordConfirmed(passphrase, confirmPassphrase)
    val scope = rememberCoroutineScope()
    Dialog(onDismissRequest = { if (!generatingInProgress) onDismiss() }) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(Modifier.padding(24.dp)) {
                Text(stringResource(R.string.gpg_generate_title), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = identity,
                    onValueChange = { if (!generatingInProgress) identity = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.gpg_user_id_label)) },
                    singleLine = true,
                    enabled = !generatingInProgress
                )
                Spacer(Modifier.height(8.dp))
                PasswordConfirmationFields(
                    password = passphrase,
                    confirmPassword = confirmPassphrase,
                    onPasswordChange = { if (!generatingInProgress) passphrase = it },
                    onConfirmPasswordChange = { if (!generatingInProgress) confirmPassphrase = it },
                    passwordLabel = context.getString(R.string.gpg_key_protect_password_label),
                    confirmLabel = context.getString(R.string.gpg_key_protect_password_confirm),
                    enabled = !generatingInProgress,
                    allowBlank = true
                )
                if (hasExistingKeys) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.gpg_generate_overwrite_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = confirmKeyRisk,
                            onCheckedChange = { if (!generatingInProgress) confirmKeyRisk = it },
                            enabled = !generatingInProgress
                        )
                        Text(
                            stringResource(R.string.gpg_generate_risk_ack),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                if (generatingInProgress) {
                    Spacer(Modifier.height(16.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.gpg_generating), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(24.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss, enabled = !generatingInProgress) { Text(stringResource(R.string.common_cancel)) }
                    Button(
                        onClick = {
                            if (identity.isNotBlank() && !generatingInProgress) {
                                generatingInProgress = true
                                scope.launch {
                                    val (ok, errMsg) = withContext(Dispatchers.IO) {
                                        generateDefaultKey(context, identity.trim(), passphrase.toCharArray())
                                    }
                                    generatingInProgress = false
                                    if (ok) {
                                        Toast.makeText(context, context.getString(R.string.gpg_key_generated), Toast.LENGTH_SHORT).show()
                                        onSuccess()
                                        onDismiss()
                                    } else {
                                        Toast.makeText(context, context.getString(R.string.gpg_generate_failed, errMsg ?: context.getString(R.string.common_unknown_error)), Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        },
                        enabled = !generatingInProgress && passphraseConfirmed && (!hasExistingKeys || confirmKeyRisk)
                    ) { Text(stringResource(R.string.gpg_generate_action)) }
                }
            }
        }
    }
}

