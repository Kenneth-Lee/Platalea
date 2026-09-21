package com.kenny.localmanager.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import android.graphics.drawable.Icon
import android.icu.text.Transliterator
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
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
import com.kenny.localmanager.R
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
import com.kenny.localmanager.file.listFilesSafe
import com.kenny.localmanager.file.compressToZip
import com.kenny.localmanager.file.compressTo7z
import com.kenny.localmanager.file.ArchiveCompressionFormat
import com.kenny.localmanager.file.buildArchiveOutputName
import com.kenny.localmanager.file.compressSingleFileToFormat
import com.kenny.localmanager.file.compressTarArchive
import com.kenny.localmanager.file.unzipToParent
import com.kenny.localmanager.file.UnzipResult
import com.kenny.localmanager.file.CacheEntry
import com.kenny.localmanager.file.getCacheEntries
import com.kenny.localmanager.file.clearCacheEntry
import com.kenny.localmanager.file.formatSize
import com.kenny.localmanager.file.collectMusicFilesRecursively
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
import com.kenny.localmanager.git.cloneToTree
import com.kenny.localmanager.git.commitAndPush
import com.kenny.localmanager.git.copyFileToShare
import com.mpatric.mp3agic.ID3v24Tag
import com.mpatric.mp3agic.Mp3File
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.collect
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.io.File
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal const val CONFIG_EXPORT_FILE_NAME = "local_manager_config.json"

internal enum class ConfigExportResult {
    SUCCESS,
    FILE_EXISTS,
    FAILED
}

internal typealias RunProgressBlock = suspend (
    label: String,
    total: Int?,
    block: suspend ((Int) -> Unit) -> Unit
) -> Unit

/**
 * tab 的退出恢复策略（AppShell 编排约定）：
 * - PERSISTENT：常规 tab，退出时保存、下次启动恢复到它
 * - TRANSIENT：会话型 tab（网络服务等），退出不作为恢复目标，
 *   启动恢复时回落到最近的 PERSISTENT tab
 */
internal enum class TabExitRestorePolicy { PERSISTENT, TRANSIENT }

internal enum class MainTab(
    val key: String,
    val labelRes: Int,
    val exitRestorePolicy: TabExitRestorePolicy = TabExitRestorePolicy.PERSISTENT
) {
    DIRECTORY("directory", R.string.main_tab_directory),
    RECENT("recent", R.string.main_tab_recent),
    PLAYER("player", R.string.main_tab_player),
    FTP("ftp", R.string.main_tab_ftp, TabExitRestorePolicy.TRANSIENT),
    LOCAL_SHARE("local_share", R.string.main_tab_local_share, TabExitRestorePolicy.TRANSIENT),
    FAMILY_NETWORK("family_network", R.string.main_tab_family_network, TabExitRestorePolicy.TRANSIENT),
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

internal const val EXIF_TAG_XP_AUTHOR = "XPAuthor"
internal const val EXIF_TAG_XP_COMMENT = "XPComment"
internal const val EXIF_TAG_XP_SOFTWARE = "XPSoftware"
internal const val EXIF_TAG_XP_COPYRIGHT = "XPCopyright"
internal const val LM_EXIF_USER_COMMENT_PREFIX = "LM_EXIF_B64_V1:"
internal const val LM_EXIF_SOFTWARE_MARKER_PREFIX = " [LMEXIF:"
internal const val LM_EXIF_SOFTWARE_MARKER_SUFFIX = "]"

internal data class LmExifPayload(
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


internal fun guessExternalOpenMimeType(context: Context, uri: Uri, fileNameHint: String?): String {
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


internal fun decodeXpExifText(exif: ExifInterface, tag: String): String? {
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


internal val externalOpenVideoExts = setOf(
    "rmvb", "rm", "rv", "ram", "mp4", "m4v", "mkv", "avi", "mov", "wmv", "flv",
    "ts", "m2ts", "mts", "webm", "3gp", "mpeg", "mpg", "vob", "asf"
)
internal val externalOpenAudioExts = setOf(
    "mp3", "flac", "wav", "m4a", "aac", "ogg", "opus", "wma", "ape", "amr", "aiff", "alac"
)
internal val externalOpenImageExts = setOf(
    "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "tiff", "tif", "svg"
)
internal val externalOpenTextExts = setOf(
    "txt", "md", "rst", "log", "json", "xml", "yaml", "yml", "csv", "ini", "toml"
)
internal val externalOpenArchiveMimeByExt = mapOf(
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

internal fun encodeXpExifText(value: String): String {
    val bytes = (value + "\u0000").toByteArray(Charsets.UTF_16LE)
    return bytes.joinToString(separator = ",") { (it.toInt() and 0xFF).toString() }
}






internal fun decodeLmExifPayloadToken(token: String): LmExifPayload? {
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


internal fun encodeLmExifPayloadToken(data: EditableImageExif): String? {
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





internal fun normalizeExifDateTime(value: String): String {
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


internal fun readExifTextWithUnicodeFallback(
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


internal fun readLmExifPayload(exif: ExifInterface): LmExifPayload? {
    val raw = exif.getAttribute(ExifInterface.TAG_USER_COMMENT)?.trim().orEmpty()
    if (raw.startsWith(LM_EXIF_USER_COMMENT_PREFIX)) {
        val token = raw.removePrefix(LM_EXIF_USER_COMMENT_PREFIX)
        decodeLmExifPayloadToken(token)?.let { return it }
    }
    val (_, tokenFromSoftware) = splitSoftwareAndPayloadToken(exif.getAttribute(ExifInterface.TAG_SOFTWARE))
    return tokenFromSoftware?.let { decodeLmExifPayloadToken(it) }
}


internal fun splitSoftwareAndPayloadToken(rawSoftware: String?): Pair<String, String?> {
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


internal fun toExternalLaunchUri(context: Context, uri: Uri): Uri {
    if (uri.scheme != "file") return uri
    val path = uri.path ?: return uri
    val file = java.io.File(path)
    if (!file.exists()) return uri
    return runCatching {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }.getOrElse { uri }
}


internal fun writeExifTextWithUnicodeSupport(
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


internal fun writeLmExifPayload(exif: ExifInterface, data: EditableImageExif) {
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


internal fun isStrictValidExifDateTime(value: String): Boolean {
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

internal data class PlaylistTrackTransfer(
    val source: Playlist,
    val trackIndex: Int,
    val move: Boolean
)

internal fun normalizeContentUriString(s: String): String =
    com.kenny.localmanager.file.normalizeContentUriString(s)

internal fun fileExtensionKey(name: String): String? {
    val dotIndex = name.lastIndexOf('.')
    if (dotIndex <= 0 || dotIndex >= name.lastIndex) return null
    return name.substring(dotIndex + 1).trim().lowercase().ifBlank { null }
}


internal fun buildExternalOpenIntent(
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
fun PlataleaApp(
    initialFileUri: androidx.compose.runtime.MutableState<String?>? = null,
    initialLaunchTarget: String? = null
) {
    val context = LocalContext.current
    // 配置功能（域服务）：持久配置唯一创建入口；各域经窄投影消费（F4 批次逐域替换 legacy）
    val config = rememberConfigService(context)
    val prefs = config.legacy

    // AppShell + DirectoryRoot state (long-lived while the app shell is active)
    // 目录域共享数据对象（AppShell 分层设计 §2.4）：root、当前路径、刷新信号
    var recentRootUris by remember { mutableStateOf<List<String>>(emptyList()) }
    var initialDirUri by remember { mutableStateOf<String?>(null) }
    val browserTab = rememberBrowserTabState(context, config.browser, initialDirUri)

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
            val previousTab = previousTab
            if (tab.exitRestorePolicy == TabExitRestorePolicy.TRANSIENT) {
                if (previousTab != null && previousTab.exitRestorePolicy == TabExitRestorePolicy.PERSISTENT) {
                    prefs.setLastNonServerMainTab(previousTab.key)
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

    LaunchedEffect(prefs, initialLaunchTarget) {
        if (CrashReportStore.hasUnseenCrash(context)) {
            Toast.makeText(context, context.getString(R.string.crash_report_found_toast), Toast.LENGTH_SHORT).show()
            withContext(Dispatchers.IO) {
                CrashReportStore.exportLatestToRootAndClear(context)
            }
            switchMainTab(MainTab.ABOUT)
            CrashReportStore.markLatestSeen(context)
            return@LaunchedEffect
        }
        val launchTab = MainTab.fromLaunchTarget(initialLaunchTarget)
        val savedTab = MainTab.fromKey(prefs.lastMainTab.first())
        val savedNonServerTab = MainTab.fromKey(prefs.lastNonServerMainTab.first())
        val restoreTab = if (launchTab != null) {
            launchTab
        } else if (savedTab.exitRestorePolicy == TabExitRestorePolicy.TRANSIENT) {
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


    val quickNoteTab = quickNoteTabFeature(
        config = config.quickNote,
        rootUri = browserTab.rootUri,
        isActive = currentMainTab == MainTab.QUICK_NOTE,
        markdownViewerSessionCache = markdownViewerSessionCache
    )
    val bookNoteTab = bookNoteTabFeature(
        rootUri = browserTab.rootUri,
        isActive = currentMainTab == MainTab.BOOK_NOTE,
        markdownViewerSessionCache = markdownViewerSessionCache
    )

    // Config 域：配置导入 / 导出工作流（独立状态持有者，写盘后通过回调刷新目录）

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
            quickNoteTab.requestOpenWithCachedPassword()
        }

        var bulletinForwardPayload by remember { mutableStateOf<BulletinForwardPayload?>(null) }

        // App-level transient dialogs / playback-target workflow
        LaunchedEffect(saveCompletedToken) { if (saveCompletedToken > 0) browserTab.refresh() }
        var lastBackPressTime by remember { mutableStateOf(0L) }
        fun finishCurrentActivity() {
            (context as? Activity)?.finish()
        }

        fun requestExitApp() {
            if (activeMainTab == MainTab.QUICK_NOTE) {
                quickNoteTab.requestExitApp(context) { finishCurrentActivity() }
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

        // 网络服务域（FTP / WebUI / 家庭网络）：三服务对象 + 共享配置 + 生命周期接线全部内聚于工厂
        val networkServices = networkServicesTabs(
            context = context,
            config = config.network,
            activeMainTab = currentMainTab ?: MainTab.DIRECTORY,
            rootUri = browserTab.rootUri,
            displayUri = browserTab.currentUri ?: initialDirUri ?: browserTab.rootUri!!,
            familyRootUri = browserTab.rootUri ?: initialDirUri,
            hideDotFiles = browserTab.hideDotFiles,
            onRequestExitApp = { requestExitApp() }
        )
        // Config 域：设置 tab 功能对象（设置项镜像、对话框群、导入导出 workflow 内聚于工厂）
        val configTab = configTabFeature(
            context = context,
            config = config.editing,
            gitConfig = config.git,
            scope = scope,
            networkConfig = networkServices.config,
            browserRootUri = { browserTab.rootUri },
            onFilesChanged = { browserTab.refresh() }
        )
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
            config = config.player,
            scope = scope,
            autoplayShortcut = launchShortcutTarget == MainTab.PLAYER,
            onRequestExitApp = { requestExitApp() },
            recordRecentOpen = { type, key, title, uri, playlistId -> recordRecentOpen(type, key, title, uri, playlistId) },
            onBatchFinished = { browserTab.finishPendingList() },
            onOpenPlayerTab = { switchMainTab(MainTab.PLAYER) },
        )

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
                configTab.hasOpenConfigDialog -> configTab.closeTopConfigDialog()
                browserTab.showPendingDeleteConfirm -> browserTab.showPendingDeleteConfirm = false
                browserTab.showPendingList -> browserTab.showPendingList = false
                networkServices.family.isBulletinBoardOpen -> networkServices.family.closeBulletinBoard()
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
            configImportExportWorkflow = configTab.importExport,
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
            onAddDirectoryToPlayback = { uri, name -> playerTab.requestCreateDirectoryPlaylist(uri, name) },
            onOpenQuickNote = { requestOpenQuickNoteTabFromDirectory() },
            requestOpenPlayerTab = { switchMainTab(MainTab.PLAYER) },
            runWithProgress = { label, total, block -> runWithProgress(label, total, block) },
            onSetProgressCancel = { action -> progressCancelAction = action },
        )
        val directoryTab = browserTabFeature(state = browserTab, deps = browserTabHostDependencies)
                MainTabContentHost(
                    activeMainTab = activeMainTab,
                    features = listOf(
            directoryTab,
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
            networkServices.ftp,
            networkServices.webUi,
            networkServices.family,
            configTab,
            mainTabFeature(
                MainTab.GIT_SHARE,
                {
                        GitShareTabRoute(
                            GitShareTabRouteState(
                                config = config.git,
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
                                config = config.git,
                                rootUri = browserTab.rootUri
                            )
                        )
                    }
            ),
            bookNoteTab,
            quickNoteTab,
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
                                config = config.dictionary,
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
            } // （修复）Box(weight 1f) 闭合：原版此处闭合后 TabBar 才能位于 Box 之下、Column 之内

            ScrollableMainTabBar(
                activeMainTab = activeMainTab,
                onSwitchMainTab = { requestOpenMainTab(it) }
            )
        }
        bulletinForwardPayload?.let { payload ->
            BulletinForwardTargetFlow(
                manager = networkServices.familyNetworkManager,
                payload = payload,
                localServiceEnabled = networkServices.config.localNetworkServiceEnabled,
                keepEphemeralSession = !networkServices.familyNetworkForeground,
                onDismiss = { bulletinForwardPayload = null },
                onComplete = {
                    finishPendingListAndReturn()
                    bulletinForwardPayload = null
                }
            )
        }
        // Config 域：配置导入 / 导出工作流对话框（状态与逻辑在 ConfigImportExportWorkflowState 中）
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
                            val repoUrl = config.git.gitRepoUrl.first() ?: ""
                            val userName = config.git.gitUserName.first() ?: ""
                            val httpsPassword = config.git.gitHttpsPassword.first() ?: ""
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
    // （修复）MainTabsRootContent 局部函数定义至此闭合。
    // 5b3f9ec 平移时丢失了此闭合括号，导致下方顶层 when 被吞进定义体，
    // else -> MainTabsRootContent() 成为永不可达的递归调用，主界面组合为空（白屏）。
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
                bookNote = bookNoteTab,
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
                bookNote = bookNoteTab,
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

