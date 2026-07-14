package com.kenny.localmanager.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Switch
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.documentfile.provider.DocumentFile
import android.widget.Toast
import android.provider.DocumentsContract
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.kenny.localmanager.MainActivity
import com.kenny.localmanager.R
import com.kenny.localmanager.SHORTCUT_TAB_PLAYER
import com.kenny.localmanager.SHORTCUT_TAB_QUICK_NOTE
import com.kenny.localmanager.data.ConfigExportCategory
import com.kenny.localmanager.data.ConfigPlaylistImportMode
import com.kenny.localmanager.data.PLAYER_AUDIO_ENGINE_EXO_PLAYER
import com.kenny.localmanager.data.PLAYER_AUDIO_ENGINE_MEDIA_PLAYER
import com.kenny.localmanager.data.PLAYER_AUDIO_PRESET_BASS
import com.kenny.localmanager.data.PLAYER_AUDIO_PRESET_CAR
import com.kenny.localmanager.data.PLAYER_AUDIO_PRESET_FLAT
import com.kenny.localmanager.data.PLAYER_AUDIO_PRESET_HEADPHONE
import com.kenny.localmanager.data.PLAYER_AUDIO_PRESET_VOCAL
import com.kenny.localmanager.data.PlayerAudioSettings
import com.kenny.localmanager.data.PlayerListBookmark
import com.kenny.localmanager.data.Playlist
import com.kenny.localmanager.data.configJsonCategories
import com.kenny.localmanager.data.configJsonContainsKeys
import com.kenny.localmanager.data.configJsonPlaylistCount
import com.kenny.localmanager.data.exportConfig
import com.kenny.localmanager.data.importConfig
import com.kenny.localmanager.data.Preferences
import com.kenny.localmanager.data.RecentOpenItem
import com.kenny.localmanager.file.DocumentFileModel
import com.kenny.localmanager.file.DirectoryAccessException
import com.kenny.localmanager.file.copyDocumentTo
import com.kenny.localmanager.file.createFileWithBytes
import com.kenny.localmanager.file.findChildByName
import com.kenny.localmanager.file.getDirectoryToOpen
import com.kenny.localmanager.file.deleteDocument
import com.kenny.localmanager.file.emptyTrash
import com.kenny.localmanager.file.getTrashUriIfExists
import com.kenny.localmanager.file.isInsideDirectory
import com.kenny.localmanager.file.moveToTrash
import com.kenny.localmanager.file.quickObfuscate
import com.kenny.localmanager.file.quickDeobfuscate
import com.kenny.localmanager.file.isQuickObfuscatedFileName
import com.kenny.localmanager.file.restoreFromTrash
import com.kenny.localmanager.file.listChildrenFast
import com.kenny.localmanager.file.listFilesSafe
import com.kenny.localmanager.file.openInputStreamSafe
import com.kenny.localmanager.file.moveDocumentTo
import com.kenny.localmanager.file.renameDocument
import com.kenny.localmanager.file.toModel
import com.kenny.localmanager.file.compressToZip
import com.kenny.localmanager.file.compressTo7z
import com.kenny.localmanager.file.unzipToParent
import com.kenny.localmanager.file.UnzipResult
import com.kenny.localmanager.file.isArchiveEncrypted
import com.kenny.localmanager.file.getArchiveFirstLevelEntries
import com.kenny.localmanager.file.isRarV5Archive
import com.kenny.localmanager.file.ZipFirstLevelResult
import com.kenny.localmanager.file.extractMdZipToCache
import com.kenny.localmanager.file.getMdZipCacheDir
import com.kenny.localmanager.file.getMdZipCacheTimestamp
import com.kenny.localmanager.file.findMdZipCacheTarget
import com.kenny.localmanager.file.MdZipExtractResult
import com.kenny.localmanager.file.isMdZipCacheEncrypted
import com.kenny.localmanager.file.cleanMdZipCache
import com.kenny.localmanager.file.cleanHtmlZipCache
import com.kenny.localmanager.file.getHtmlZipCacheDir
import com.kenny.localmanager.file.getHtmlZipCacheTimestamp
import com.kenny.localmanager.file.findHtmlZipIndexFile
import com.kenny.localmanager.file.isHtmlZipCacheEncrypted
import com.kenny.localmanager.file.HtmlZipExtractResult
import com.kenny.localmanager.file.extractHtmlZipToCache
import com.kenny.localmanager.file.extractHtmlZipToCacheWithProgress
import com.kenny.localmanager.file.HtmlZipParseResult
import com.kenny.localmanager.file.EpubExtractResult
import com.kenny.localmanager.file.EpubParseResult
import com.kenny.localmanager.file.extractEpubToCache
import com.kenny.localmanager.file.extractLlmZipToCache
import com.kenny.localmanager.file.prepareTxtAsEpub
import com.kenny.localmanager.file.prepareLlmAsEpub
import com.kenny.localmanager.file.loadEpubFromCache
import com.kenny.localmanager.file.getEpubChapterFile
import com.kenny.localmanager.file.getEpubCacheDir
import com.kenny.localmanager.file.getEpubCacheTimestamp
import com.kenny.localmanager.file.isEpubCacheEncrypted
import com.kenny.localmanager.file.cleanEpubCache
import com.kenny.localmanager.file.PicZipExtractResult
import com.kenny.localmanager.file.extractPicZipToCache
import com.kenny.localmanager.file.getPicZipCacheDir
import com.kenny.localmanager.file.tryPicZipPassword
import com.kenny.localmanager.file.tryArchivePassword
import com.kenny.localmanager.file.getPicZipCacheTimestamp
import com.kenny.localmanager.file.isPicZipCacheEncrypted
import com.kenny.localmanager.file.cleanPicZipCache
import com.kenny.localmanager.file.CacheEntry
import com.kenny.localmanager.file.getCacheEntries
import com.kenny.localmanager.file.clearCacheEntry
import com.kenny.localmanager.file.isMusicFileName
import com.kenny.localmanager.file.formatSize
import com.kenny.localmanager.file.collectMusicFilesRecursively
import com.kenny.localmanager.file.RecursiveFileSearchCriteria
import com.kenny.localmanager.file.RecursiveFileSearchHit
import com.kenny.localmanager.file.searchFilesRecursively
import com.kenny.localmanager.ui.EpubViewerScreen
import com.kenny.localmanager.ui.PicZipViewerScreen
import com.kenny.localmanager.ui.PdfViewerScreen
import com.kenny.localmanager.player.PlaylistSortOrder
import com.kenny.localmanager.player.PlaylistTrackSortField
import com.kenny.localmanager.player.PlaylistTrackSorter
import com.kenny.localmanager.player.PlaybackService
import com.kenny.localmanager.player.PlaybackState
import com.kenny.localmanager.player.PlaybackTimeFormat
import com.kenny.localmanager.player.TrackMetadata
import com.kenny.localmanager.player.ACTION_NEXT
import com.kenny.localmanager.player.ACTION_PAUSE
import com.kenny.localmanager.player.ACTION_PLAY
import com.kenny.localmanager.player.ACTION_PREV
import com.kenny.localmanager.player.ACTION_REMOVE_PLAYLIST_TRACK
import com.kenny.localmanager.player.ACTION_RELOAD_PLAYLIST
import com.kenny.localmanager.player.ACTION_RESUME
import com.kenny.localmanager.player.ACTION_STOP
import com.kenny.localmanager.player.EXTRA_DIR_URI
import com.kenny.localmanager.player.EXTRA_NAMES
import com.kenny.localmanager.player.ACTION_SEEK
import com.kenny.localmanager.player.EXTRA_PLAYLIST_ID
import com.kenny.localmanager.player.EXTRA_POSITION_MS
import com.kenny.localmanager.player.EXTRA_START_INDEX
import com.kenny.localmanager.player.EXTRA_START_POSITION_MS
import com.kenny.localmanager.player.EXTRA_URIS
import com.kenny.localmanager.gpg.GpgHelper
import com.kenny.localmanager.gpg.GpgHelper.GpgEncryptedKind
import com.kenny.localmanager.gpg.findPublicKeyRing
import com.kenny.localmanager.gpg.generateDefaultKey
import com.kenny.localmanager.gpg.listEncryptionPublicKeyRings
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
import com.kenny.localmanager.dict.importStarDict
import com.kenny.localmanager.dict.isStarDictImportCandidate
import com.kenny.localmanager.requestPinnedTabShortcut
import com.kenny.localmanager.git.cloneToTree
import com.kenny.localmanager.git.commitAndPush
import com.kenny.localmanager.git.copyFileToShare
import com.kenny.localmanager.family.DEFAULT_DOWNLOAD_CHUNK_SIZE_BYTES
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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private const val CONFIG_EXPORT_FILE_NAME = "local_manager_config.json"

private enum class ConfigExportResult {
    SUCCESS,
    FILE_EXISTS,
    FAILED
}

private data class ExternalAppTarget(
    val packageName: String,
    val label: String
)

internal const val RECENT_TYPE_ZIP_VIEWER = "zip_viewer"
internal const val RECENT_TYPE_HTML_VIEWER = "html_viewer"
internal const val RECENT_TYPE_EPUB_RENDERER = "epub_renderer"
internal const val RECENT_TYPE_PDF_VIEWER = "pdf_viewer"
internal const val RECENT_TYPE_PLAYLIST = "playlist"
internal const val RECENT_TYPE_EXTERNAL_OPEN = "external_open"
private typealias RunProgressBlock = suspend (
    label: String,
    total: Int?,
    block: suspend ((Int) -> Unit) -> Unit
) -> Unit

internal enum class MainTab(val key: String, val labelRes: Int) {
    DIRECTORY("directory", R.string.main_tab_directory),
    RECENT("recent", R.string.main_tab_recent),
    PLAYER("player", R.string.main_tab_player),
    FTP("ftp", R.string.main_tab_ftp),
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

private fun AudioDeviceInfo.isPrivatePlaybackCandidate(): Boolean {
    return when (type) {
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        AudioDeviceInfo.TYPE_BLE_HEADSET,
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_HEARING_AID -> true
        else -> false
    }
}

private fun AudioDeviceInfo.debugLabel(): String {
    val typeName = when (type) {
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "BLUETOOTH_A2DP"
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "BLUETOOTH_SCO"
        AudioDeviceInfo.TYPE_BLE_BROADCAST -> "BLE_BROADCAST"
        AudioDeviceInfo.TYPE_BLE_HEADSET -> "BLE_HEADSET"
        AudioDeviceInfo.TYPE_BLE_SPEAKER -> "BLE_SPEAKER"
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "BUILTIN_EARPIECE"
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "BUILTIN_SPEAKER"
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE -> "BUILTIN_SPEAKER_SAFE"
        AudioDeviceInfo.TYPE_DOCK -> "DOCK"
        AudioDeviceInfo.TYPE_HDMI -> "HDMI"
        AudioDeviceInfo.TYPE_HDMI_ARC -> "HDMI_ARC"
        AudioDeviceInfo.TYPE_HDMI_EARC -> "HDMI_EARC"
        AudioDeviceInfo.TYPE_HEARING_AID -> "HEARING_AID"
        AudioDeviceInfo.TYPE_LINE_ANALOG -> "LINE_ANALOG"
        AudioDeviceInfo.TYPE_LINE_DIGITAL -> "LINE_DIGITAL"
        AudioDeviceInfo.TYPE_AUX_LINE -> "AUX_LINE"
        AudioDeviceInfo.TYPE_USB_ACCESSORY -> "USB_ACCESSORY"
        AudioDeviceInfo.TYPE_USB_DEVICE -> "USB_DEVICE"
        AudioDeviceInfo.TYPE_USB_HEADSET -> "USB_HEADSET"
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "WIRED_HEADPHONES"
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> "WIRED_HEADSET"
        else -> "TYPE_$type"
    }
    return "$typeName(id=$id,name=${getProductName()})"
}

private fun Context.hasPrivatePlaybackOutput(): Boolean {
    val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            val privateOutputs = outputs.filter { it.isPrivatePlaybackCandidate() }
            Log.i(
                "FileBrowserApp",
                "Shortcut autoplay route check: private=${privateOutputs.map { it.debugLabel() }}, all=${outputs.map { it.debugLabel() }}"
            )
            privateOutputs.isNotEmpty()
        } else {
            @Suppress("DEPRECATION")
            (audioManager.isBluetoothA2dpOn || audioManager.isBluetoothScoOn || audioManager.isWiredHeadsetOn)
        }
    } catch (_: Exception) {
        false
    }
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
            val opened = launchExternalOpen(context, uri, null)
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

private fun doCopyPendingToCurrentDir(
    context: Context,
    scope: kotlinx.coroutines.CoroutineScope,
    displayUri: String,
    pendingList: List<DocumentFileModel>,
    rootUri: String?,
    copyMoveLog: ((String) -> Unit)?,
    runWithProgress: RunProgressBlock,
    onSuccessAll: () -> Unit,
    onRefreshDone: () -> Unit
) {
    val targetDirUri = normalizeContentUriString(displayUri)
    copyMoveLog?.invoke(context.getString(R.string.browser_log_copy_target, targetDirUri))
    val treeUri = rootUri?.let { Uri.parse(normalizeContentUriString(it)) }
    scope.launch {
        runWithProgress(context.getString(R.string.pending_list_action_copy), pendingList.size) { setProgress ->
            val (ok, fail) = withContext(Dispatchers.IO) {
                var o = 0
                var f = 0
                pendingList.forEachIndexed { index, model ->
                    if (copyDocumentTo(context, model.uri, Uri.parse(targetDirUri), treeUri, copyMoveLog) != null) o++
                    else f++
                    withContext(Dispatchers.Main.immediate) { setProgress(index + 1) }
                }
                Pair(o, f)
            }
            copyMoveLog?.invoke(context.getString(R.string.browser_log_copy_result, ok, fail))
            if (fail == 0 && ok > 0) {
                onSuccessAll()
                Toast.makeText(context, context.getString(R.string.browser_copy_all_ok, ok), Toast.LENGTH_SHORT).show()
            } else if (ok > 0) {
                Toast.makeText(context, context.getString(R.string.browser_copy_partial, ok, fail), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, context.getString(R.string.browser_copy_failed), Toast.LENGTH_SHORT).show()
            }
            onRefreshDone()
        }
    }
}

private fun doMovePendingToCurrentDir(
    context: Context,
    scope: kotlinx.coroutines.CoroutineScope,
    displayUri: String,
    pendingList: List<DocumentFileModel>,
    rootUri: String?,
    copyMoveLog: ((String) -> Unit)?,
    runWithProgress: RunProgressBlock,
    onSuccessAll: () -> Unit,
    onRefreshDone: () -> Unit
) {
    val targetDirUri = normalizeContentUriString(displayUri)
    copyMoveLog?.invoke(context.getString(R.string.browser_log_move_target, targetDirUri))
    val treeUri = rootUri?.let { Uri.parse(normalizeContentUriString(it)) }
    scope.launch {
        runWithProgress(context.getString(R.string.pending_list_action_move), pendingList.size) { setProgress ->
            val (ok, fail) = withContext(Dispatchers.IO) {
                var o = 0
                var f = 0
                pendingList.forEachIndexed { index, model ->
                    if (moveDocumentTo(context, model.uri, Uri.parse(targetDirUri), treeUri, copyMoveLog)) o++
                    else f++
                    withContext(Dispatchers.Main.immediate) { setProgress(index + 1) }
                }
                Pair(o, f)
            }
            copyMoveLog?.invoke(context.getString(R.string.browser_log_move_result, ok, fail))
            if (fail == 0 && ok > 0) {
                onSuccessAll()
                Toast.makeText(context, context.getString(R.string.browser_move_all_ok, ok), Toast.LENGTH_SHORT).show()
            } else if (ok > 0) {
                Toast.makeText(context, context.getString(R.string.browser_move_partial, ok, fail), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, context.getString(R.string.browser_move_failed), Toast.LENGTH_SHORT).show()
            }
            onRefreshDone()
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

private fun dedupeAudioList(audioList: List<DocumentFileModel>): List<DocumentFileModel> {
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

private suspend fun removePlaylistTrackAt(
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

private suspend fun refreshDirectoryPlaylistFromSource(
    context: Context,
    pl: Playlist
): Playlist? {
    val sourceUri = pl.sourceUri ?: return null
    val audioList = withContext(Dispatchers.IO) {
        collectMusicFilesRecursively(context, sourceUri).map { it.model }
    }
    val deduped = dedupeAudioList(audioList)
    return pl.copy(
        uris = deduped.map { it.uri.toString() },
        names = deduped.map { it.name }
    )
}

private fun syncPlaybackAfterPlaylistUpdate(
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

private suspend fun resortPlaylistTracks(
    context: Context,
    prefs: Preferences,
    pl: Playlist,
    field: PlaylistTrackSortField,
    order: PlaylistSortOrder,
    playbackState: PlaybackState?,
    onStopPlayback: () -> Unit
): Playlist {
    val previousUris = pl.uris
    val previousTrackIndex = playbackState?.takeIf { it.playlistId == pl.id }?.trackIndex ?: 0
    val previousPositionMs = playbackState?.takeIf { it.playlistId == pl.id }?.positionMs ?: 0

    var working = pl
    if (pl.isDirectorySource) {
        working = refreshDirectoryPlaylistFromSource(context, pl) ?: return pl
    } else if (pl.uris.size < 2) {
        return pl
    }

    val updated = if (working.uris.size >= 2) {
        withContext(Dispatchers.IO) {
            PlaylistTrackSorter.sortTracks(context, working, field, order)
        }
    } else {
        working
    }
    prefs.updatePlaylist(updated)
    syncPlaybackAfterPlaylistUpdate(
        context = context,
        playlistId = pl.id,
        previousUris = previousUris,
        previousTrackIndex = previousTrackIndex,
        previousPositionMs = previousPositionMs,
        updated = updated,
        playbackState = playbackState,
        onStopPlayback = onStopPlayback
    )
    return updated
}

private suspend fun createNewPlaybackPlaylistAndStart(
    context: Context,
    prefs: Preferences,
    audioList: List<DocumentFileModel>,
    playlistName: String? = null,
    sourceType: String = Playlist.SOURCE_TYPE_MANUAL,
    sourceUri: String? = null
): Playlist? {
    val uniqueAudioList = dedupeAudioList(audioList)
    if (uniqueAudioList.isEmpty()) return null
    val playlist = Playlist(
        id = java.util.UUID.randomUUID().toString(),
        name = playlistName ?: context.getString(
            R.string.player_playlist_default_name,
            java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
        ),
        uris = uniqueAudioList.map { it.uri.toString() },
        names = uniqueAudioList.map { it.name },
        sourceType = sourceType,
        sourceUri = sourceUri
    )
    withContext(Dispatchers.IO) { prefs.addPlaylist(playlist) }
    val intent = Intent(context, PlaybackService::class.java).apply {
        action = ACTION_PLAY
        putExtra(EXTRA_PLAYLIST_ID, playlist.id)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
    return playlist
}

private suspend fun createDirectoryPlaybackPlaylistAndStart(
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

private suspend fun appendToPlaybackPlaylistAndStart(
    context: Context,
    prefs: Preferences,
    target: Playlist,
    audioList: List<DocumentFileModel>
): String? {
    if (audioList.isEmpty()) return null
    val result = withContext(Dispatchers.IO) {
        prefs.appendTracksToPlaylist(
            target.id,
            audioList.map { it.uri.toString() },
            audioList.map { it.name }
        )
    }
    val intent = Intent(context, PlaybackService::class.java).apply {
        action = ACTION_PLAY
        putExtra(EXTRA_PLAYLIST_ID, target.id)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
    return when {
        result.appendedCount > 0 && result.skippedCount > 0 ->
            context.getString(R.string.player_append_started_with_skips, result.appendedCount, target.displayName(context.getString(R.string.player_unnamed_playlist)), result.skippedCount)
        result.appendedCount > 0 ->
            context.getString(R.string.player_append_started, result.appendedCount, target.displayName(context.getString(R.string.player_unnamed_playlist)))
        result.skippedCount > 0 ->
            context.getString(R.string.player_already_exists_started, target.displayName(context.getString(R.string.player_unnamed_playlist)))
        else ->
            context.getString(R.string.player_started_playlist, target.displayName(context.getString(R.string.player_unnamed_playlist)))
    }
}

private suspend fun detectGpgDecryptModeForOp(context: Context, op: GpgOpState): GpgDecryptUiMode = withContext(Dispatchers.IO) {
    val files = when (op) {
        is GpgOpState.Decrypt -> listOf(op.fileModel)
        is GpgOpState.BatchDecrypt -> op.list
        else -> emptyList()
    }
    var hasSymmetric = false
    var hasPublicKey = false
    files.forEach { fileModel ->
        val kind = context.contentResolver.openInputStreamSafe(fileModel.uri)?.use { input ->
            GpgHelper.detectEncryptedKind(input)
        } ?: GpgEncryptedKind.UNKNOWN
        when (kind) {
            GpgEncryptedKind.PUBLIC_KEY -> hasPublicKey = true
            GpgEncryptedKind.SYMMETRIC, GpgEncryptedKind.UNKNOWN -> hasSymmetric = true
        }
    }
    when {
        hasSymmetric && hasPublicKey -> GpgDecryptUiMode.MIXED
        hasPublicKey -> GpgDecryptUiMode.SECRET_KEY
        else -> GpgDecryptUiMode.SYMMETRIC
    }
}

private suspend fun decryptGpgFileToDir(
    context: Context,
    fileModel: DocumentFileModel,
    targetDirUri: String,
    treeUri: Uri?,
    symmetricPassword: CharArray?,
    keyPassphrase: CharArray?
): Boolean = withContext(Dispatchers.IO) {
    val encBytes = context.contentResolver.openInputStreamSafe(fileModel.uri)?.use { it.readBytes() } ?: return@withContext false
    val kind = GpgHelper.detectEncryptedKind(java.io.ByteArrayInputStream(encBytes))
    val decrypted = when (kind) {
        GpgEncryptedKind.PUBLIC_KEY -> {
            val secretRings = loadSecretKeyRings(context) ?: return@withContext false
            val effectiveKeyPass = keyPassphrase ?: SecretKeyPasswordCache.get() ?: CharArray(0)
            GpgHelper.decryptWithSecretKey(
                java.io.ByteArrayInputStream(encBytes),
                secretRings,
                effectiveKeyPass
            ) { }
        }
        GpgEncryptedKind.SYMMETRIC, GpgEncryptedKind.UNKNOWN -> {
            val pwd = symmetricPassword ?: return@withContext false
            GpgHelper.decryptSymmetric(
                java.io.ByteArrayInputStream(encBytes),
                pwd
            ) { }
        }
    } ?: return@withContext false
    val outName = fileModel.name.removeSuffix(".gpg").ifEmpty { fileModel.name + ".dec" }
    createFileWithBytes(context, Uri.parse(targetDirUri), treeUri, outName, "application/octet-stream", decrypted)
}

private suspend fun encryptGpgFileToDir(
    context: Context,
    fileModel: DocumentFileModel,
    targetDirUri: String,
    treeUri: Uri?,
    symmetricPassword: CharArray?,
    publicKeyId: Long?
): Boolean = withContext(Dispatchers.IO) {
    val plain = context.contentResolver.openInputStreamSafe(fileModel.uri)?.use { it.readBytes() } ?: return@withContext false
    if (publicKeyId != null) {
        val pubRings = loadPublicKeyRings(context) ?: return@withContext false
        val pubKeyRing = findPublicKeyRing(pubRings, publicKeyId) ?: return@withContext false
        val keyDesc = listEncryptionPublicKeyRings(pubRings).find { it.first == publicKeyId }?.second.orEmpty()
        val safeName = keyDesc.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(40).trim()
        val baseName = fileModel.name.removeSuffix(".gpg")
        val outName = if (safeName.isNotEmpty()) "${baseName}_${safeName}.gpg" else "${baseName}.gpg"
        val encrypted = GpgHelper.encryptWithPublicKey(plain, pubKeyRing, fileModel.name) ?: return@withContext false
        createFileWithBytes(context, Uri.parse(targetDirUri), treeUri, outName, "application/octet-stream", encrypted)
    } else {
        val pwd = symmetricPassword ?: return@withContext false
        val encrypted = GpgHelper.encryptSymmetric(plain, pwd, fileModel.name) { } ?: return@withContext false
        createFileWithBytes(context, Uri.parse(targetDirUri), treeUri, fileModel.name + ".gpg", "application/octet-stream", encrypted)
    }
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
private fun MainTabContentHost(
    activeMainTab: MainTab,
    recentOpenItems: List<RecentOpenItem>,
    onOpenRecentItem: (RecentOpenItem) -> Unit,
    onDeleteRecentItem: (RecentOpenItem) -> Unit,
    onClearRecentItems: () -> Unit,
    directoryContent: @Composable () -> Unit,
    ftpContent: @Composable () -> Unit,
    familyNetworkContent: @Composable () -> Unit,
    configContent: @Composable () -> Unit,
    gitShareContent: @Composable () -> Unit,
    gitProjectsContent: @Composable () -> Unit,
    playerContent: @Composable () -> Unit,
    bookNoteContent: @Composable () -> Unit,
    quickNoteContent: @Composable () -> Unit,
    quickCryptoContent: @Composable () -> Unit,
    dictionaryContent: @Composable () -> Unit,
    aboutContent: @Composable () -> Unit
) {
    when (activeMainTab) {
        MainTab.DIRECTORY -> directoryContent()
        MainTab.RECENT -> RecentTabRoute(
            RecentTabRouteState(
                items = recentOpenItems.take(30),
                onOpenRecentItem = onOpenRecentItem,
                onDeleteRecentItem = onDeleteRecentItem,
                onClearRecentItems = onClearRecentItems
            )
        )
        MainTab.FTP -> ftpContent()
        MainTab.FAMILY_NETWORK -> familyNetworkContent()
        MainTab.CONFIG -> configContent()
        MainTab.GIT_SHARE -> gitShareContent()
        MainTab.GIT_PROJECTS -> gitProjectsContent()
        MainTab.PLAYER -> playerContent()
        MainTab.BOOK_NOTE -> bookNoteContent()
        MainTab.QUICK_NOTE -> quickNoteContent()
        MainTab.QUICK_CRYPTO -> quickCryptoContent()
        MainTab.DICTIONARY -> dictionaryContent()
        MainTab.ABOUT -> aboutContent()
    }
}

private fun fileItemSubtitle(model: DocumentFileModel): String {
    val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(model.lastModified))
    return if (model.displaySize.isNotEmpty()) "${model.displaySize}  $dateStr" else dateStr
}

private fun formatPlaybackTime(ms: Int): String {
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

private fun formatTrackMetadata(context: Context, metadata: TrackMetadata): String {
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

private data class PlaylistTrackTransfer(
    val source: Playlist,
    val trackIndex: Int,
    val move: Boolean
)

@Composable
private fun PlaybackBottomControlBar(
    isPlaying: Boolean,
    enabled: Boolean,
    onPrev: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit
) {
    val context = LocalContext.current
    Surface(tonalElevation = 3.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onPrev,
                enabled = enabled,
                modifier = Modifier.height(52.dp)
            ) {
                Icon(Icons.Default.SkipPrevious, contentDescription = context.getString(R.string.player_prev_desc))
            }
            Button(
                onClick = onPlayPause,
                enabled = enabled,
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
            ) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) context.getString(R.string.player_pause_desc) else context.getString(R.string.player_play_desc),
                    modifier = Modifier.size(24.dp)
                )
            }
            OutlinedButton(
                onClick = onNext,
                enabled = enabled,
                modifier = Modifier.height(52.dp)
            ) {
                Icon(Icons.Default.SkipNext, contentDescription = context.getString(R.string.player_next_desc))
            }
        }
    }
}

internal fun normalizeContentUriString(s: String): String =
    com.kenny.localmanager.file.normalizeContentUriString(s)

private fun fileExtensionKey(name: String): String? {
    val dotIndex = name.lastIndexOf('.')
    if (dotIndex <= 0 || dotIndex >= name.lastIndex) return null
    return name.substring(dotIndex + 1).trim().lowercase().ifBlank { null }
}

private fun buildExternalOpenIntent(context: Context, uri: Uri, packageName: String? = null): Intent {
    val mimeType = context.contentResolver.getType(uri)
    return Intent(Intent.ACTION_VIEW).apply {
        if (mimeType.isNullOrBlank()) data = uri else setDataAndType(uri, mimeType)
        if (!packageName.isNullOrBlank()) setPackage(packageName)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

@Suppress("DEPRECATION")
private fun queryExternalOpenTargets(context: Context, uriString: String): List<ExternalAppTarget> {
    val packageManager = context.packageManager
    return packageManager.queryIntentActivities(
        buildExternalOpenIntent(context, Uri.parse(uriString)),
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

private fun launchExternalOpen(context: Context, uriString: String, packageName: String?): Boolean {
    return runCatching {
        context.startActivity(buildExternalOpenIntent(context, Uri.parse(uriString), packageName))
        true
    }.getOrElse { false }
}

private fun pathFromRoot(context: Context, rootUri: String?, currentUri: String): String {
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
    FileBrowserAppScreen(
        initialFileUri = initialFileUri,
        initialLaunchTarget = initialLaunchTarget
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FileBrowserAppScreen(
    initialFileUri: androidx.compose.runtime.MutableState<String?>?,
    initialLaunchTarget: String?
) {
    val context = LocalContext.current
    val prefs = remember { Preferences(context) }
    var rootUri by remember { mutableStateOf<String?>(null) }
    var recentRootUris by remember { mutableStateOf<List<String>>(emptyList()) }
    var initialDirUri by remember { mutableStateOf<String?>(null) }
    var pendingSaveFileUri by remember { mutableStateOf<String?>(null) }
    var showOverwriteConfirm by remember { mutableStateOf<Pair<String, String>?>(null) } // (sourceUri, fileName)
    var saveInProgress by remember { mutableStateOf(false) }
    var viewingFile by remember { mutableStateOf<Triple<String, String, Boolean>?>(null) }
    var markdownViewFile by remember { mutableStateOf<Triple<String, String, Boolean>?>(null) }
    var htmlViewState by remember { mutableStateOf<HtmlViewState?>(null) }
    val markdownViewerSessionCache = remember { MarkdownViewerSessionCache() }
    var passContentView by remember { mutableStateOf<PassDecryptedContent?>(null) }
    var passEditRequest by remember { mutableStateOf<Pair<DocumentFileModel, String>?>(null) }
    var passEditPassword by remember { mutableStateOf("") }
    var passEditInProgress by remember { mutableStateOf(false) }
    var passEditTriedCache by remember { mutableStateOf(false) }
    var passEditState by remember { mutableStateOf<PassEditState?>(null) }
    var refreshTrigger by remember { mutableStateOf(0) }
    var mdZipViewState by remember { mutableStateOf<MdZipViewState?>(null) }
    var picZipViewState by remember { mutableStateOf<PicZipViewState?>(null) }
    var htmlZipViewState by remember { mutableStateOf<HtmlZipViewState?>(null) }
    var epubViewState by remember { mutableStateOf<EpubViewState?>(null) }
    var encryptedCacheExitDialog by remember { mutableStateOf<EncryptedCacheExitDialogState?>(null) }
    var pdfViewState by remember { mutableStateOf<Pair<String, String>?>(null) } // (uri, fileName)
    var currentMainTab by remember { mutableStateOf<MainTab?>(null) }
    var currentUri by remember { mutableStateOf<String?>(null) }
    val fileBrowserBackStack = remember { mutableStateListOf<String>() }
    val fileListLazyState = rememberLazyListState()
    var viewerPreviewBytes by remember { mutableStateOf(4096) }
    var saveCompletedToken by remember { mutableStateOf(0) }
    var preferredExternalPackages by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var recentOpenItems by remember { mutableStateOf<List<RecentOpenItem>>(emptyList()) }
    val scope = rememberCoroutineScope()
    val launchShortcutTarget = MainTab.fromLaunchTarget(initialLaunchTarget)
    val bypassExitDoubleConfirm = launchShortcutTarget == MainTab.PLAYER || launchShortcutTarget == MainTab.QUICK_NOTE
    var playerShortcutAutoplayPending by remember(initialLaunchTarget) {
        mutableStateOf(launchShortcutTarget == MainTab.PLAYER)
    }

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

    LaunchedEffect(prefs) {
        prefs.viewerPreviewBytes.collect { viewerPreviewBytes = it }
    }
    LaunchedEffect(prefs) {
        prefs.externalOpenByExtension.collect { preferredExternalPackages = it }
    }
    LaunchedEffect(prefs) {
        prefs.recentOpenItems.collect { recentOpenItems = it }
    }
    LaunchedEffect(Unit) {
        rootUri = prefs.rootUri.first()?.let { normalizeContentUriString(it) }
    }
    LaunchedEffect(prefs) {
        prefs.recentRootUris.collect { recentRootUris = it }
    }
    LaunchedEffect(rootUri, initialDirUri) {
        if (rootUri != null) {
            val normalizedRoot = normalizeContentUriString(rootUri!!)
            val target = initialDirUri?.let { normalizeContentUriString(it) } ?: normalizedRoot
            if (currentUri == null) {
                currentUri = target
            } else if (!currentUri!!.startsWith(normalizedRoot)) {
                currentUri = target
            }
        }
    }
    LaunchedEffect(rootUri) {
        markdownViewerSessionCache.clear()
    }
    LaunchedEffect(initialFileUri?.value) {
        val uriStr = initialFileUri?.value ?: return@LaunchedEffect
        initialFileUri.value = null
        pendingSaveFileUri = uriStr
    }

    LaunchedEffect(prefs, initialLaunchTarget) {
        val launchTab = MainTab.fromLaunchTarget(initialLaunchTarget)
        val savedTab = MainTab.fromKey(prefs.lastMainTab.first())
        currentMainTab = launchTab ?: savedTab
    }

    fun switchMainTab(tab: MainTab) {
        if (currentMainTab == tab) return
        currentMainTab = tab
        scope.launch { prefs.setLastMainTab(tab.key) }
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

    suspend fun savePicZipImageToRoot(sourceFile: File, fileName: String): Boolean {
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

    suspend fun openDirectoryImageGallery(
        currentItem: DocumentFileModel,
        currentDirUri: String,
        siblingImages: List<DocumentFileModel>
    ) {
        val normalizedImages = siblingImages
            .filter { !it.isDirectory && isGalleryImageFile(it.name) }
            .distinctBy { it.uri.toString() }
        if (normalizedImages.isEmpty()) {
            Toast.makeText(context, context.getString(R.string.browser_no_images_in_dir), Toast.LENGTH_SHORT).show()
            return
        }
        val initialIndex = normalizedImages.indexOfFirst { it.uri == currentItem.uri }
        if (initialIndex < 0) {
            Toast.makeText(context, context.getString(R.string.browser_image_position_failed, currentItem.name), Toast.LENGTH_SHORT).show()
            return
        }
        val galleryRoot = File(context.cacheDir, "pic_dir_gallery")
        val galleryKey = currentDirUri.hashCode().toUInt().toString(16)
        val cacheDir = File(galleryRoot, galleryKey)
        val contentDir = File(cacheDir, "content")
        withContext(Dispatchers.IO) {
            if (cacheDir.exists()) {
                cacheDir.deleteRecursively()
            }
            contentDir.mkdirs()
            normalizedImages.forEachIndexed { index, model ->
                val targetFile = File(contentDir, model.name)
                val bytes = context.contentResolver.openInputStreamSafe(model.uri)?.use { it.readBytes() }
                    ?: throw IllegalStateException(context.getString(R.string.browser_image_read_failed, index + 1, model.name, model.uri))
                targetFile.outputStream().use { it.write(bytes) }
            }
            File(cacheDir, ".last_index").writeText(initialIndex.toString())
        }
        val dirTitle = DocumentFile.fromTreeUri(context, Uri.parse(currentDirUri))?.name
            ?: DocumentFile.fromSingleUri(context, Uri.parse(currentDirUri))?.name
            ?: currentItem.name
        picZipViewState = PicZipViewState(
            contentDir = contentDir,
            imagePaths = normalizedImages.map { it.name },
            zipFileName = dirTitle,
            zipUri = currentItem.uri,
            isEncrypted = false,
            password = null,
            initialIndex = initialIndex,
            recordRecent = false,
            cleanupCacheDir = cacheDir
        )
    }

    val quickNoteController = rememberQuickNoteTabController(
        rootUri = rootUri,
        isActive = currentMainTab == MainTab.QUICK_NOTE,
        markdownViewerSessionCache = markdownViewerSessionCache
    )
    val bookNoteController = rememberBookNoteTabController(
        rootUri = rootUri,
        isActive = currentMainTab == MainTab.BOOK_NOTE,
        markdownViewerSessionCache = markdownViewerSessionCache
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
            val previousRoot = rootUri?.let { normalizeContentUriString(it) }
            scope.launch {
                prefs.recordRecentRootSwitch(previousRoot, normalized)
                prefs.setRootUri(normalized)
            }
            fileBrowserBackStack.clear()
            rootUri = normalized
            initialDirUri = normalized
            currentUri = normalized
        }
    }

    LaunchedEffect(pendingSaveFileUri, rootUri) {
        val sourceUriStr = pendingSaveFileUri ?: return@LaunchedEffect
        val targetRoot = rootUri?.let { normalizeContentUriString(it) } ?: return@LaunchedEffect
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
                currentUri = dir
                saveCompletedToken++
                Toast.makeText(ctx, ctx.getString(R.string.browser_saved_to_current_dir), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(ctx, ctx.getString(R.string.viewer_save_failed), Toast.LENGTH_SHORT).show()
            }
        }
    }

    @Composable
    fun MainTabsRootContent() {
        val displayUri = currentUri ?: initialDirUri ?: rootUri!!
        val activeMainTab = currentMainTab ?: MainTab.DIRECTORY
        val pendingList = remember { mutableStateListOf<DocumentFileModel>() }
        var showPendingList by remember { mutableStateOf(false) }
        var bulletinForwardPayload by remember { mutableStateOf<BulletinForwardPayload?>(null) }

        var showPendingDeleteConfirm by remember { mutableStateOf(false) }
        var showPlaybackTargetDialog by remember { mutableStateOf(false) }
        var showImportCategoryDialog by remember { mutableStateOf(false) }
        var showImportKeyConfirmDialog by remember { mutableStateOf(false) }
        var showImportPlaylistConfirmDialog by remember { mutableStateOf(false) }
        var pendingImportJson by remember { mutableStateOf<String?>(null) }
        var pendingImportAvailableCategories by remember { mutableStateOf<Set<ConfigExportCategory>>(emptySet()) }
        var pendingImportSelectedCategories by remember { mutableStateOf<Set<ConfigExportCategory>>(emptySet()) }
        var pendingImportPlaylistCount by remember { mutableStateOf(0) }
        var pendingImportPlaylistMode by remember { mutableStateOf(ConfigPlaylistImportMode.OVERWRITE) }
        var pendingPlaybackAudioList by remember { mutableStateOf<List<DocumentFileModel>>(emptyList()) }
        var showCacheManagementDialog by remember { mutableStateOf(false) }
        var showKeyManagementDialog by remember { mutableStateOf(false) }
        LaunchedEffect(saveCompletedToken) { if (saveCompletedToken > 0) refreshTrigger++ }
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
        var gpgState by remember { mutableStateOf<GpgOpState?>(null) }
        var gpgPassword by remember { mutableStateOf("") }
        var gpgPasswordConfirm by remember { mutableStateOf("") }
        var gpgDecryptMode by remember { mutableStateOf<GpgDecryptUiMode?>(null) }
        var gpgDecryptAutoTried by remember { mutableStateOf(false) }
        var gpgEncryptSelectedKeyId by remember { mutableStateOf<Long?>(null) }
        var gpgPubEncryptInProgress by remember { mutableStateOf(false) }
        var showChangeRootConfirm by remember { mutableStateOf(false) }
        var showRootSwitchDialog by remember { mutableStateOf(false) }
        var quickObfuscateOp by remember { mutableStateOf<Pair<DocumentFileModel, Boolean>?>(null) }
        var quickObfuscatePassword by remember { mutableStateOf("") }
        var quickObfuscatePasswordConfirm by remember { mutableStateOf("") }
        var quickObfuscateInProgress by remember { mutableStateOf(false) }
        var batchObfuscateOp by remember { mutableStateOf<Pair<List<DocumentFileModel>, Boolean>?>(null) }
        var batchObfuscatePassword by remember { mutableStateOf("") }
        var batchObfuscatePasswordConfirm by remember { mutableStateOf("") }
        var batchObfuscateInProgress by remember { mutableStateOf(false) }
        var passProtectTarget by remember { mutableStateOf<DocumentFileModel?>(null) }
        var passProtectInProgress by remember { mutableStateOf(false) }
        var passViewTarget by remember { mutableStateOf<DocumentFileModel?>(null) }
        var passViewPassword by remember { mutableStateOf("") }
        var passViewInProgress by remember { mutableStateOf(false) }
        var passViewTriedCache by remember { mutableStateOf(false) }
        var progressOp by remember { mutableStateOf<OperationProgress?>(null) }
        var currentDirPath by remember { mutableStateOf("") }
        LaunchedEffect(displayUri, rootUri) {
            currentDirPath = withContext(Dispatchers.IO) {
                pathFromRoot(context, rootUri, displayUri)
            }
        }
        var cachedTrashUri by remember { mutableStateOf<Uri?>(null) }
        LaunchedEffect(rootUri) {
            cachedTrashUri = rootUri?.let { r ->
                withContext(Dispatchers.IO) {
                    val root = Uri.parse(normalizeContentUriString(r))
                    getTrashUriIfExists(context, root, root)
                }
            }
        }
        var dirCache by remember { mutableStateOf<Map<String, CachedDir>>(emptyMap()) }
        LaunchedEffect(rootUri) {
            dirCache = emptyMap()
        }
        val ftpManager = remember { com.kenny.localmanager.ftp.FtpServerManager(context) }
        val familyNetworkManager = remember { com.kenny.localmanager.family.FamilyNetworkManager(context) }
        var ftpPort by remember { mutableStateOf(2121) }
        var ftpPassword by remember { mutableStateOf<String?>(null) }
        var networkServiceTimeoutMinutes by remember { mutableStateOf(0) }
        var localNetworkServiceEnabled by remember { mutableStateOf(true) }
        var familyNetworkUserName by remember { mutableStateOf("") }
        var familyNetworkHostName by remember { mutableStateOf("") }
        var filterVisible by remember { mutableStateOf(true) }
        var showGitConfigDialog by remember { mutableStateOf(false) }
        var showExportConfigDialog by remember { mutableStateOf(false) }
        var exportConfigFileName by remember { mutableStateOf(CONFIG_EXPORT_FILE_NAME) }
        var exportGitCategory by remember { mutableStateOf(true) }
        var exportMusicCategory by remember { mutableStateOf(true) }
        var exportRecentCategory by remember { mutableStateOf(true) }
        var exportEpubCategory by remember { mutableStateOf(true) }
        var exportGpgCategory by remember { mutableStateOf(true) }
        var exportOtherCategory by remember { mutableStateOf(true) }
        var showPubkeyShareScreen by remember { mutableStateOf(false) }
        var shareFileToGitTarget by remember { mutableStateOf<DocumentFileModel?>(null) }
        val shareGitLogs = remember { mutableStateListOf<String>() }
        var shareGitInProgress by remember { mutableStateOf(false) }
        var shareGitDone by remember { mutableStateOf(false) }
        var zipUnzipTarget by remember { mutableStateOf<DocumentFileModel?>(null) }
        var zipCompressTarget by remember { mutableStateOf<DocumentFileModel?>(null) }
        var sevenZCompressTarget by remember { mutableStateOf<DocumentFileModel?>(null) }
        var zipUnzipPassword by remember { mutableStateOf("") }
        var zipUnzipEncrypted by remember { mutableStateOf<Boolean?>(null) }
        var zipCompressPassword by remember { mutableStateOf("") }
        var zipCompressPasswordConfirm by remember { mutableStateOf("") }
        var sevenZCompressPassword by remember { mutableStateOf("") }
        var sevenZCompressPasswordConfirm by remember { mutableStateOf("") }
        var mdZipTarget by remember { mutableStateOf<DocumentFileModel?>(null) }
        var mdZipEncrypted by remember { mutableStateOf<Boolean?>(null) }
        var mdZipPassword by remember { mutableStateOf("") }
        var mdZipInProgress by remember { mutableStateOf(false) }
        var htmlZipTarget by remember { mutableStateOf<DocumentFileModel?>(null) }
        var htmlZipEncrypted by remember { mutableStateOf<Boolean?>(null) }
        var htmlZipPassword by remember { mutableStateOf("") }
        var htmlZipInProgress by remember { mutableStateOf(false) }
        var htmlZipLog by remember { mutableStateOf("") }
        var htmlZipLoadError by remember { mutableStateOf<String?>(null) }
        var llmZipTarget by remember { mutableStateOf<DocumentFileModel?>(null) }
        var llmZipEncrypted by remember { mutableStateOf<Boolean?>(null) }
        var llmZipPassword by remember { mutableStateOf("") }
        var llmZipInProgress by remember { mutableStateOf(false) }
        var epubTarget by remember { mutableStateOf<DocumentFileModel?>(null) }
        var epubEncrypted by remember { mutableStateOf<Boolean?>(null) }
        var epubPassword by remember { mutableStateOf("") }
        var epubInProgress by remember { mutableStateOf(false) }
        var epubLog by remember { mutableStateOf("") }
        var epubLoadError by remember { mutableStateOf<String?>(null) }
        var txtTarget by remember { mutableStateOf<DocumentFileModel?>(null) }
        var txtInProgress by remember { mutableStateOf(false) }
        var txtLoadError by remember { mutableStateOf<String?>(null) }
        var llmTarget by remember { mutableStateOf<DocumentFileModel?>(null) }
        var llmInProgress by remember { mutableStateOf(false) }
        var llmLoadError by remember { mutableStateOf<String?>(null) }
        var picZipTarget by remember { mutableStateOf<DocumentFileModel?>(null) }
        var picZipEncrypted by remember { mutableStateOf<Boolean?>(null) }
        var picZipPassword by remember { mutableStateOf("") }
        var picZipInProgress by remember { mutableStateOf(false) }
        var showPendingCompressToZip by remember { mutableStateOf(false) }
        var showPendingCompressTo7z by remember { mutableStateOf(false) }
        var pendingCompressZipName by remember { mutableStateOf("") }
        var pendingCompressPassword by remember { mutableStateOf("") }
        var pendingCompressPasswordConfirm by remember { mutableStateOf("") }
        var pendingCompress7zName by remember { mutableStateOf("") }
        var pendingCompress7zPassword by remember { mutableStateOf("") }
        var pendingCompress7zPasswordConfirm by remember { mutableStateOf("") }
        var playbackState by remember { mutableStateOf<PlaybackState?>(null) }
        var playlists by remember { mutableStateOf<List<Playlist>>(emptyList()) }
        var playlistsLoaded by remember { mutableStateOf(false) }
        var playerLastPlaylistId by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(Unit) {
            PlaybackService.playbackState.collect { playbackState = it }
        }
        LaunchedEffect(prefs) {
            prefs.playlists.collect {
                playlists = it
                playlistsLoaded = true
            }
        }
        LaunchedEffect(prefs) {
            prefs.playerLastPlaylistId.collect { playerLastPlaylistId = it }
        }
        LaunchedEffect(
            playerShortcutAutoplayPending,
            currentMainTab,
            playbackState?.playlistId,
            playbackState?.isPlaying,
            playerLastPlaylistId,
            playlistsLoaded,
            playlists
        ) {
            if (!playerShortcutAutoplayPending) return@LaunchedEffect
            if (currentMainTab != MainTab.PLAYER) return@LaunchedEffect
            if (!context.hasPrivatePlaybackOutput()) {
                playerShortcutAutoplayPending = false
                return@LaunchedEffect
            }
            if (playbackState?.isPlaying == true) {
                playerShortcutAutoplayPending = false
                return@LaunchedEffect
            }

            val resumeIntent = when {
                playbackState != null -> Intent(context, PlaybackService::class.java).setAction(ACTION_RESUME)
                playerLastPlaylistId.isNullOrBlank() -> {
                    playerShortcutAutoplayPending = false
                    return@LaunchedEffect
                }
                !playlistsLoaded -> return@LaunchedEffect
                playlists.none { it.id == playerLastPlaylistId } -> {
                    playerShortcutAutoplayPending = false
                    return@LaunchedEffect
                }
                else -> Intent(context, PlaybackService::class.java).apply {
                    action = ACTION_PLAY
                    putExtra(EXTRA_PLAYLIST_ID, playerLastPlaylistId)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(resumeIntent)
            } else {
                context.startService(resumeIntent)
            }
            playerShortcutAutoplayPending = false
        }
        LaunchedEffect(zipUnzipTarget) {
            zipUnzipEncrypted = null
            zipUnzipPassword = ""
            val target = zipUnzipTarget ?: return@LaunchedEffect
            if (target.name.endsWith(".rar", ignoreCase = true)) {
                val rarV5 = withContext(Dispatchers.IO) { isRarV5Archive(context, target.uri) }
                if (rarV5) {
                    Toast.makeText(context, context.getString(R.string.browser_rar5_unsupported), Toast.LENGTH_LONG).show()
                    zipUnzipTarget = null
                    return@LaunchedEffect
                }
            }
            zipUnzipEncrypted = withContext(Dispatchers.IO) { isArchiveEncrypted(context, target.uri, target.name) }
        }
        LaunchedEffect(mdZipTarget) {
            mdZipEncrypted = null
            mdZipPassword = ""
            mdZipInProgress = false
            val target = mdZipTarget ?: return@LaunchedEffect
            // 先检查是否有有效缓存（非加密时）
            val cacheDir = getMdZipCacheDir(context, target.uri)
            val cacheTs = getMdZipCacheTimestamp(cacheDir)
            if (cacheTs > 0 && !isMdZipCacheEncrypted(cacheDir) && cacheTs >= target.lastModified) {
                val contentDir = java.io.File(cacheDir, "content")
                val hasContent = contentDir.exists() &&
                    (contentDir.listFiles()?.isNotEmpty() == true)
                if (hasContent) {
                    val isRstZip = target.name.endsWith(".rst.zip", ignoreCase = true)
                    val cachedTarget = findMdZipCacheTarget(context, cacheDir, isRstZip)
                    mdZipTarget = null
                    mdZipViewState = MdZipViewState(
                        targetFile = cachedTarget,
                        contentDir = contentDir,
                        zipFileName = target.name,
                        zipUri = target.uri,
                        isEncrypted = false
                    )
                    return@LaunchedEffect
                }
                // 缓存内容为空，废弃并重新解压
                cacheDir.deleteRecursively()
            }
            mdZipEncrypted = withContext(Dispatchers.IO) { isArchiveEncrypted(context, target.uri, target.name) }
            // 非加密的直接解压
            if (mdZipEncrypted == false) {
                mdZipInProgress = true
                val result = withContext(Dispatchers.IO) { extractMdZipToCache(context, target.uri, null, target.name) }
                mdZipInProgress = false
                mdZipTarget = null
                if (result != null) {
                    mdZipViewState = MdZipViewState(
                        targetFile = result.targetFile,  // 可能为 null
                        contentDir = result.contentDir,
                        zipFileName = target.name,
                        zipUri = target.uri,
                        isEncrypted = false
                    )
                } else {
                    Toast.makeText(context, context.getString(R.string.browser_extract_failed), Toast.LENGTH_SHORT).show()
                }
            }
        }
        LaunchedEffect(htmlZipTarget) {
            htmlZipEncrypted = null
            htmlZipPassword = ""
            htmlZipInProgress = false
            htmlZipLog = context.getString(R.string.browser_html_zip_preparing, htmlZipTarget?.name ?: "")
            htmlZipLoadError = null
            val target = htmlZipTarget ?: return@LaunchedEffect
            val cacheDir = getHtmlZipCacheDir(context, target.uri)
            val cacheTs = getHtmlZipCacheTimestamp(cacheDir)
            if (cacheTs > 0 && !isHtmlZipCacheEncrypted(cacheDir) && cacheTs >= target.lastModified) {
                val contentDir = java.io.File(cacheDir, "content")
                if (contentDir.exists() && contentDir.listFiles()?.isNotEmpty() == true) {
                    val cachedIndex = findHtmlZipIndexFile(contentDir)
                    htmlZipTarget = null
                    htmlZipViewState = HtmlZipViewState(
                        indexFile = cachedIndex,
                        contentDir = contentDir,
                        zipFileName = target.name,
                        zipUri = target.uri,
                        isEncrypted = false
                    )
                    return@LaunchedEffect
                }
                cacheDir.deleteRecursively()
            }
            htmlZipLog += context.getString(R.string.browser_checking_encryption)
            htmlZipEncrypted = withContext(Dispatchers.IO) { isArchiveEncrypted(context, target.uri, target.name) }
            htmlZipLog += context.getString(R.string.browser_encryption_status, htmlZipEncrypted.toString())
            if (htmlZipEncrypted == false) {
                htmlZipInProgress = true
                val result = withContext(Dispatchers.IO) {
                    extractHtmlZipToCacheWithProgress(context, target.uri, null, target.name) { log ->
                        htmlZipLog += "$log\n"
                    }
                }
                htmlZipInProgress = false
                htmlZipTarget = null
                when (result) {
                    is HtmlZipParseResult.Success -> {
                        htmlZipViewState = HtmlZipViewState(
                            indexFile = result.result.indexFile,
                            contentDir = result.result.contentDir,
                            zipFileName = target.name,
                            zipUri = target.uri,
                            isEncrypted = false
                        )
                    }
                    is HtmlZipParseResult.Error -> {
                        val detail = result.detail?.let { "\n$it" } ?: ""
                        htmlZipLog += context.getString(R.string.browser_log_error_detail, result.message ?: "", detail)
                        htmlZipLoadError = "${result.message}$detail"
                    }
                }
            } else if (htmlZipEncrypted == true) {
                htmlZipLog += context.getString(R.string.browser_file_encrypted_need_password)
            }
        }
        LaunchedEffect(llmZipTarget) {
            llmZipEncrypted = null
            llmZipPassword = ""
            llmZipInProgress = false
            val target = llmZipTarget ?: return@LaunchedEffect
            val cacheDir = getEpubCacheDir(context, target.uri)
            val cacheTs = getEpubCacheTimestamp(cacheDir)
            if (cacheTs > 0 && !isEpubCacheEncrypted(cacheDir) && cacheTs >= target.lastModified) {
                val cached = loadEpubFromCache(context, cacheDir)
                if (cached != null) {
                    llmZipTarget = null
                    epubViewState = EpubViewState(
                        extractResult = cached,
                        zipFileName = target.name,
                        epubUri = target.uri,
                        isEncrypted = false
                    )
                    return@LaunchedEffect
                }
                cacheDir.deleteRecursively()
            }
            llmZipEncrypted = withContext(Dispatchers.IO) { isArchiveEncrypted(context, target.uri, target.name) }
            if (llmZipEncrypted == false) {
                llmZipInProgress = true
                val result = withContext(Dispatchers.IO) {
                    extractLlmZipToCache(context, target.uri, null, target.name)
                }
                llmZipInProgress = false
                llmZipTarget = null
                if (result != null) {
                    epubViewState = EpubViewState(
                        extractResult = result,
                        zipFileName = target.name,
                        epubUri = target.uri,
                        isEncrypted = false
                    )
                } else {
                    Toast.makeText(context, context.getString(R.string.browser_no_txt_llm_found), Toast.LENGTH_LONG).show()
                }
            }
        }
        LaunchedEffect(epubTarget) {
            val target = epubTarget ?: return@LaunchedEffect
            epubEncrypted = null
            epubPassword = ""
            epubInProgress = true
            epubLog = context.getString(R.string.browser_html_zip_preparing, target.name)
            epubLoadError = null
            try {
                var cachedResult: EpubExtractResult? = null
                var encrypted: Boolean? = null
                withContext(Dispatchers.IO) {
                    epubLog += context.getString(R.string.browser_checking_cache)
                    val cacheDir = getEpubCacheDir(context, target.uri)
                    val cacheTs = getEpubCacheTimestamp(cacheDir)
                    if (cacheTs > 0 && !isEpubCacheEncrypted(cacheDir) && cacheTs >= target.lastModified) {
                        cachedResult = loadEpubFromCache(context, cacheDir)
                        if (cachedResult != null) {
                            epubLog += context.getString(R.string.browser_using_cache)
                            return@withContext
                        }
                        cacheDir.deleteRecursively()
                    }
                    epubLog += context.getString(R.string.browser_checking_encryption)
                    encrypted = isArchiveEncrypted(context, target.uri, target.name)
                }
                val cached = cachedResult
                if (cached != null) {
                    epubViewState = EpubViewState(
                        extractResult = cached,
                        zipFileName = target.name,
                        epubUri = target.uri,
                        isEncrypted = false
                    )
                    epubTarget = null
                    epubInProgress = false
                    return@LaunchedEffect
                }
                epubEncrypted = encrypted
                if (encrypted == false) {
                    val result = withContext(Dispatchers.IO) {
                        extractEpubToCache(context, target.uri, null, target.name) { log ->
                            epubLog += "$log\n"
                        }
                    }
                    when (result) {
                        is EpubParseResult.Success -> {
                            epubViewState = EpubViewState(
                                extractResult = result.result,
                                zipFileName = target.name,
                                epubUri = target.uri,
                                isEncrypted = false
                            )
                            epubTarget = null
                            epubInProgress = false
                        }
                        is EpubParseResult.Error -> {
                            val detail = result.detail?.let { "\n$it" } ?: ""
                            epubLog += context.getString(R.string.browser_log_error_detail, result.message ?: "", detail)
                            epubLoadError = "${result.message}$detail"
                            epubInProgress = false
                            // 保留 epubTarget 以便显示错误对话框
                        }
                    }
                } else {
                    epubLog += context.getString(R.string.browser_file_encrypted_need_password)
                    epubInProgress = false
                }
            } catch (e: Exception) {
                epubLog += context.getString(R.string.browser_log_exception, e.javaClass.simpleName, e.message ?: "")
                epubLoadError = "${e.javaClass.simpleName}: ${e.message}"
                epubInProgress = false
            }
        }
        // 处理 TXT 文件，转换为 EPUB 格式查看
        LaunchedEffect(txtTarget) {
            val target = txtTarget ?: return@LaunchedEffect
            txtInProgress = true
            txtLoadError = null
            try {
                val cacheDir = getEpubCacheDir(context, target.uri)
                val cacheTs = getEpubCacheTimestamp(cacheDir)

                var result: EpubExtractResult? = null

                // 检查是否有有效缓存
                if (cacheTs > 0 && cacheTs >= target.lastModified) {
                    result = loadEpubFromCache(context, cacheDir)
                    // 如果缓存加载失败，清除旧缓存
                    if (result == null) {
                        Log.w("FileBrowserApp", "TXT cache load failed, clearing old cache")
                        cacheDir.deleteRecursively()
                    }
                }

                // 如果没有有效缓存，重新生成
                if (result == null) {
                    // 直接由 URI 生成统一书本缓存（epub_cache/<key>）
                    result = prepareTxtAsEpub(context, target.uri, target.name)
                }

                if (result != null) {
                    epubViewState = EpubViewState(
                        extractResult = result,
                        zipFileName = target.name,
                        epubUri = target.uri,
                        isEncrypted = false
                    )
                    txtTarget = null
                } else {
                    txtLoadError = context.getString(R.string.browser_txt_parse_failed)
                }
            } catch (e: Exception) {
                Log.e("FileBrowserApp", "TXT processing failed", e)
                txtLoadError = "${e.javaClass.simpleName}: ${e.message}"
            }
            txtInProgress = false
        }
        // 处理 LLM 对话文件，转换为 EPUB 格式查看
        LaunchedEffect(llmTarget) {
            val target = llmTarget ?: return@LaunchedEffect
            llmInProgress = true
            llmLoadError = null
            try {
                val cacheDir = getEpubCacheDir(context, target.uri)
                val cacheTs = getEpubCacheTimestamp(cacheDir)

                var result: EpubExtractResult? = null

                // 检查是否有有效缓存
                if (cacheTs > 0 && cacheTs >= target.lastModified) {
                    result = loadEpubFromCache(context, cacheDir)
                    // 如果缓存加载失败，清除旧缓存
                    if (result == null) {
                        Log.w("FileBrowserApp", "LLM cache load failed, clearing old cache")
                        cacheDir.deleteRecursively()
                    }
                }

                // 如果没有有效缓存，重新生成
                if (result == null) {
                    // 直接由 URI 生成统一书本缓存（epub_cache/<key>）
                    result = prepareLlmAsEpub(context, target.uri, target.name)
                }

                val finalResult = result
                if (finalResult != null) {
                    epubViewState = EpubViewState(
                        extractResult = finalResult,
                        zipFileName = target.name,
                        epubUri = target.uri,
                        isEncrypted = false
                    )
                    llmTarget = null
                } else {
                    llmLoadError = context.getString(R.string.browser_llm_parse_failed)
                }
            } catch (e: Exception) {
                Log.e("FileBrowserApp", "LLM processing failed", e)
                llmLoadError = "${e.javaClass.simpleName}: ${e.message}"
            }
            llmInProgress = false
        }
        LaunchedEffect(picZipTarget) {
            picZipEncrypted = null
            picZipPassword = ""
            picZipInProgress = false
            val target = picZipTarget ?: return@LaunchedEffect
            val cacheDir = getPicZipCacheDir(context, target.uri)
            val cacheTs = getPicZipCacheTimestamp(cacheDir)
            if (cacheTs > 0 && !isPicZipCacheEncrypted(cacheDir) && cacheTs >= target.lastModified) {
                val contentDir = java.io.File(cacheDir, "content")
                val listFile = java.io.File(cacheDir, ".image_list")
                if (contentDir.exists() && listFile.exists()) {
                    val paths = listFile.readText().lineSequence().filter { it.isNotBlank() }.toList()
                    val lastIndexFile = java.io.File(cacheDir, ".last_index")
                    val initialIndex = lastIndexFile.takeIf { it.exists() }?.readText()?.toIntOrNull()?.coerceIn(0, paths.size - 1) ?: 0
                    picZipTarget = null
                    picZipViewState = PicZipViewState(
                        contentDir = contentDir,
                        imagePaths = paths,
                        zipFileName = target.name,
                        zipUri = target.uri,
                        isEncrypted = false,
                        password = null,
                        initialIndex = initialIndex
                    )
                    return@LaunchedEffect
                }
                cacheDir.deleteRecursively()
            }
            picZipEncrypted = withContext(Dispatchers.IO) { isArchiveEncrypted(context, target.uri, target.name) }
            if (picZipEncrypted == false) {
                picZipInProgress = true
                val result = withContext(Dispatchers.IO) { extractPicZipToCache(context, target.uri, null, target.name) }
                if (result != null) {
                    picZipTarget = null
                    picZipViewState = PicZipViewState(
                        contentDir = result.contentDir,
                        imagePaths = result.imagePaths,
                        zipFileName = target.name,
                        zipUri = target.uri,
                        isEncrypted = false,
                        password = null,
                        initialIndex = 0
                    )
                } else {
                    picZipTarget = null
                    Toast.makeText(context, context.getString(R.string.browser_extract_failed), Toast.LENGTH_SHORT).show()
                }
                picZipInProgress = false
            }
        }
        LaunchedEffect(prefs) {
            prefs.ftpPort.collect { ftpPort = it }
        }
        LaunchedEffect(prefs) {
            prefs.ftpPassword.collect { ftpPassword = it }
        }
        LaunchedEffect(prefs) {
            prefs.networkServiceTimeoutMinutes.collect { networkServiceTimeoutMinutes = it }
        }
        LaunchedEffect(prefs) {
            prefs.localNetworkServiceEnabled.collect { localNetworkServiceEnabled = it }
        }
        LaunchedEffect(prefs) {
            prefs.familyNetworkUserName.collect { familyNetworkUserName = it.orEmpty() }
        }
        LaunchedEffect(prefs) {
            prefs.familyNetworkHostName.collect { familyNetworkHostName = it.orEmpty() }
        }
        LaunchedEffect(prefs) {
            val legacyExtra = prefs.familyNetworkExtraRoles.first()
            if (!legacyExtra.isNullOrBlank()) {
                familyNetworkManager.migrateUserRolesFromLegacyConfig(legacyExtra)
                prefs.setFamilyNetworkExtraRoles(null)
            }
        }
        LaunchedEffect(prefs) {
            prefs.filterVisible.collect { filterVisible = it }
        }
        LaunchedEffect(activeMainTab) {
            if (activeMainTab != MainTab.FTP) {
                ftpManager.stop()
            }
        }
        LaunchedEffect(ftpPassword, localNetworkServiceEnabled, familyNetworkUserName, familyNetworkHostName) {
            familyNetworkManager.configure(
                ftpPassword,
                localNetworkServiceEnabled,
                familyNetworkUserName.ifBlank { null },
                familyNetworkHostName.ifBlank { null }
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
                progressOp != null -> { } // 不响应返回，防止误触
                batchObfuscateOp != null -> { batchObfuscateOp = null; batchObfuscatePassword = ""; batchObfuscatePasswordConfirm = "" }
                quickObfuscateOp != null -> { quickObfuscateOp = null; quickObfuscatePassword = ""; quickObfuscatePasswordConfirm = "" }
                passProtectTarget != null -> { passProtectTarget = null }
                passViewTarget != null -> { if (!passViewInProgress) { passViewTarget = null; passViewPassword = "" } }
                passEditRequest != null -> { if (!passEditInProgress) { passEditRequest = null; passEditPassword = "" } }
                passEditState != null -> passEditState = null
                showChangeRootConfirm -> showChangeRootConfirm = false
                gpgState != null -> {
                    gpgState = null
                    gpgPassword = ""
                    gpgPasswordConfirm = ""
                    gpgDecryptMode = null
                    gpgDecryptAutoTried = false
                    gpgEncryptSelectedKeyId = null
                }
                showKeyManagementDialog -> showKeyManagementDialog = false
                showCacheManagementDialog -> showCacheManagementDialog = false
                showGitConfigDialog -> showGitConfigDialog = false
                showPendingDeleteConfirm -> showPendingDeleteConfirm = false
                showPendingList -> showPendingList = false
                familyNetworkManager.state.value.openBoardSession != null -> familyNetworkManager.closeBulletinBoard()
                activeMainTab != MainTab.DIRECTORY -> requestExitApp()
                zipUnzipTarget != null -> zipUnzipTarget = null
                mdZipTarget != null -> { if (!mdZipInProgress) { mdZipTarget = null; mdZipPassword = "" } }
                htmlZipTarget != null -> { if (!htmlZipInProgress) { htmlZipTarget = null; htmlZipPassword = "" } }
                llmZipTarget != null -> { if (!llmZipInProgress) { llmZipTarget = null; llmZipPassword = "" } }
                epubTarget != null -> { if (!epubInProgress) { epubTarget = null; epubPassword = "" } }
                picZipTarget != null -> { if (!picZipInProgress) { picZipTarget = null; picZipPassword = "" } }
                pdfViewState != null -> pdfViewState = null
                zipCompressTarget != null -> zipCompressTarget = null
                sevenZCompressTarget != null -> sevenZCompressTarget = null
                showPendingCompressToZip -> showPendingCompressToZip = false
                showPendingCompressTo7z -> showPendingCompressTo7z = false
                fileBrowserBackStack.isNotEmpty() -> currentUri = fileBrowserBackStack.removeAt(fileBrowserBackStack.lastIndex)
                else -> {
                    requestExitApp()
                }
            }
        }
        var hideDotFiles by remember { mutableStateOf(false) }
        var startupDecryptKey by remember { mutableStateOf(false) }
        LaunchedEffect(prefs) {
            prefs.hideDotFiles.collect { hideDotFiles = it }
        }
        LaunchedEffect(prefs) {
            prefs.startupDecryptKey.collect { startupDecryptKey = it }
        }
        val copyMoveLog: ((String) -> Unit)? = null
        suspend fun runWithProgress(
            label: String,
            total: Int? = null,
            block: suspend ((Int) -> Unit) -> Unit
        ) {
            runWithUiProgress(
                setProgressState = { progressOp = it },
                label = label,
                total = total,
                block = block
            )
        }

        fun clearPendingConfigImportState() {
            pendingImportJson = null
            pendingImportAvailableCategories = emptySet()
            pendingImportSelectedCategories = emptySet()
            pendingImportPlaylistCount = 0
            pendingImportPlaylistMode = ConfigPlaylistImportMode.OVERWRITE
            showImportCategoryDialog = false
            showImportKeyConfirmDialog = false
            showImportPlaylistConfirmDialog = false
        }

        fun clearPendingPlaybackTargetState() {
            pendingPlaybackAudioList = emptyList()
            showPlaybackTargetDialog = false
        }

        suspend fun createNewPlaybackPlaylist(audioList: List<DocumentFileModel>) {
            val playlist = createNewPlaybackPlaylistAndStart(context, prefs, audioList) ?: return
            recordRecentOpen(
                type = RECENT_TYPE_PLAYLIST,
                key = playlist.id,
                title = playlist.displayName(context.getString(R.string.player_unnamed_playlist)),
                playlistId = playlist.id
            )
            pendingList.clear()
            switchMainTab(MainTab.PLAYER)
            showPendingList = false
            clearPendingPlaybackTargetState()
            Toast.makeText(context, context.getString(R.string.browser_playlist_created, audioList.size), Toast.LENGTH_SHORT).show()
        }

        suspend fun appendToPlaybackPlaylist(target: Playlist, audioList: List<DocumentFileModel>) {
            val msg = appendToPlaybackPlaylistAndStart(context, prefs, target, audioList)
            if (msg == null) {
                Toast.makeText(context, context.getString(R.string.browser_add_to_playlist_failed), Toast.LENGTH_SHORT).show()
                return
            }
            recordRecentOpen(
                type = RECENT_TYPE_PLAYLIST,
                key = target.id,
                title = target.name,
                playlistId = target.id
            )
            pendingList.clear()
            switchMainTab(MainTab.PLAYER)
            showPendingList = false
            clearPendingPlaybackTargetState()
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }

        suspend fun exportConfigToRoot(): ConfigExportResult {
            val targetRoot = rootUri?.let { normalizeContentUriString(it) } ?: return ConfigExportResult.FAILED
            val targetUri = Uri.parse(targetRoot)
            val jsonBytes = exportConfig(context, prefs).toByteArray(Charsets.UTF_8)
            val existing = findChildByName(context, targetUri, CONFIG_EXPORT_FILE_NAME)
            if (existing != null) return ConfigExportResult.FILE_EXISTS
            return if (createFileWithBytes(
                context,
                targetUri,
                targetUri,
                CONFIG_EXPORT_FILE_NAME,
                "application/json",
                jsonBytes
            )) ConfigExportResult.SUCCESS else ConfigExportResult.FAILED
        }

        fun openExportConfigDialog() {
            exportConfigFileName = CONFIG_EXPORT_FILE_NAME
            exportGitCategory = true
            exportMusicCategory = true
            exportRecentCategory = true
            exportEpubCategory = true
            exportGpgCategory = true
            exportOtherCategory = true
            showExportConfigDialog = true
        }

        fun normalizeExportFileName(input: String): String? {
            val trimmed = input.trim()
            if (trimmed.isBlank() || trimmed.contains('/') || trimmed.contains('\\')) return null
            return if (trimmed.endsWith(".json", ignoreCase = true)) trimmed else "$trimmed.json"
        }

        suspend fun exportConfigToRoot(fileName: String, categories: Set<ConfigExportCategory>): ConfigExportResult {
            if (categories.isEmpty()) return ConfigExportResult.FAILED
            val targetRoot = rootUri?.let { normalizeContentUriString(it) } ?: return ConfigExportResult.FAILED
            val targetUri = Uri.parse(targetRoot)
            val jsonBytes = exportConfig(context, prefs, categories).toByteArray(Charsets.UTF_8)
            val existing = findChildByName(context, targetUri, fileName)
            if (existing != null) return ConfigExportResult.FILE_EXISTS
            return if (createFileWithBytes(
                context,
                targetUri,
                targetUri,
                fileName,
                "application/json",
                jsonBytes
            )) ConfigExportResult.SUCCESS else ConfigExportResult.FAILED
        }

        suspend fun performConfigImport(
            jsonString: String,
            importKeys: Boolean,
            categories: Set<ConfigExportCategory>
        ) {
            val ok = importConfig(
                context,
                prefs,
                jsonString,
                importKeys = importKeys,
                playlistImportMode = pendingImportPlaylistMode,
                categories = categories
            )
            refreshTrigger++
            val msg = when {
                !ok -> context.getString(R.string.browser_config_import_json_failed)
                ConfigExportCategory.MUSIC in categories && pendingImportPlaylistCount > 0 && pendingImportPlaylistMode == ConfigPlaylistImportMode.APPEND && importKeys -> context.getString(R.string.browser_config_imported_music_append_keys)
                ConfigExportCategory.MUSIC in categories && pendingImportPlaylistCount > 0 && pendingImportPlaylistMode == ConfigPlaylistImportMode.APPEND -> context.getString(R.string.browser_config_imported_music_append)
                ConfigExportCategory.MUSIC in categories && pendingImportPlaylistCount > 0 && importKeys -> context.getString(R.string.browser_config_imported_music_replace_keys)
                ConfigExportCategory.MUSIC in categories && pendingImportPlaylistCount > 0 -> context.getString(R.string.browser_config_imported_music_replace)
                ConfigExportCategory.GPG in categories && importKeys -> context.getString(R.string.browser_config_imported_with_keys)
                pendingImportPlaylistCount > 0 && pendingImportPlaylistMode == ConfigPlaylistImportMode.APPEND -> context.getString(R.string.browser_config_imported_playlist_append)
                else -> context.getString(R.string.browser_config_imported)
            }
            clearPendingConfigImportState()
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }

        fun continueConfigImportAfterPlaylistChoice() {
            val jsonString = pendingImportJson ?: return
            val selectedCategories = pendingImportSelectedCategories
            if (ConfigExportCategory.GPG in selectedCategories && configJsonContainsKeys(jsonString)) {
                showImportKeyConfirmDialog = true
            } else {
                scope.launch { performConfigImport(jsonString, importKeys = false, categories = selectedCategories) }
            }
        }

        fun continueConfigImportAfterCategoryChoice() {
            val jsonString = pendingImportJson ?: return
            val selectedCategories = pendingImportSelectedCategories
            scope.launch {
                val hasExistingPlaylists = withContext(Dispatchers.IO) { prefs.playlists.first().isNotEmpty() }
                val playlistCount = if (ConfigExportCategory.MUSIC in selectedCategories) configJsonPlaylistCount(jsonString) else 0
                pendingImportPlaylistCount = playlistCount
                if (playlistCount > 0 && hasExistingPlaylists) {
                    pendingImportPlaylistMode = ConfigPlaylistImportMode.OVERWRITE
                    showImportPlaylistConfirmDialog = true
                } else {
                    pendingImportPlaylistMode = ConfigPlaylistImportMode.OVERWRITE
                    continueConfigImportAfterPlaylistChoice()
                }
            }
        }

        fun startConfigImport(jsonString: String) {
            if (jsonString.isBlank()) {
                Toast.makeText(context, context.getString(R.string.browser_config_import_read_failed), Toast.LENGTH_SHORT).show()
                return
            }
            val categories = configJsonCategories(jsonString)
            if (categories.isEmpty()) {
                Toast.makeText(context, context.getString(R.string.browser_config_import_no_category), Toast.LENGTH_SHORT).show()
                return
            }
            pendingImportJson = jsonString
            pendingImportAvailableCategories = categories
            pendingImportSelectedCategories = categories
            pendingImportPlaylistCount = 0
            pendingImportPlaylistMode = ConfigPlaylistImportMode.OVERWRITE
            showImportCategoryDialog = true
        }

        val doCopyHere: () -> Unit = {
            doCopyPendingToCurrentDir(
                context = context,
                scope = scope,
                displayUri = displayUri,
                pendingList = pendingList.toList(),
                rootUri = rootUri,
                copyMoveLog = copyMoveLog,
                runWithProgress = { label, total, block -> runWithProgress(label, total, block) },
                onSuccessAll = {
                    pendingList.clear()
                    showPendingList = false
                },
                onRefreshDone = { refreshTrigger++ }
            )
        }
        val doMoveHere: () -> Unit = {
            doMovePendingToCurrentDir(
                context = context,
                scope = scope,
                displayUri = displayUri,
                pendingList = pendingList.toList(),
                rootUri = rootUri,
                copyMoveLog = copyMoveLog,
                runWithProgress = { label, total, block -> runWithProgress(label, total, block) },
                onSuccessAll = {
                    pendingList.clear()
                    showPendingList = false
                },
                onRefreshDone = { refreshTrigger++ }
            )
        }
        val finishPendingListAndReturn: () -> Unit = {
            pendingList.clear()
            showPendingList = false
        }

        val ftpRootUri = rootUri
        Column(Modifier.fillMaxSize()) {
            if (gpgPubEncryptInProgress || saveInProgress) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(stringResource(R.string.viewer_saving), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(4.dp))
            }
            progressOp?.let { OperationProgressDialog(it) }
            Box(Modifier.weight(1f)) {
                MainTabContentHost(
                    activeMainTab = activeMainTab,
                    recentOpenItems = recentOpenItems,
                    onOpenRecentItem = { item ->
                        openRecentItemByType(
                            context = context,
                            item = item,
                            currentPlaybackState = playbackState,
                            recordRecentOpen = { type, key, title, uri, playlistId ->
                                recordRecentOpen(
                                    type = type,
                                    key = key,
                                    title = title,
                                    uri = uri,
                                    playlistId = playlistId
                                )
                            },
                            switchMainTab = { switchMainTab(it) },
                            onMdZipTarget = { mdZipTarget = it },
                            onHtmlZipTarget = { htmlZipTarget = it },
                            onHtmlTarget = {
                                htmlViewState = HtmlViewState(
                                    location = HtmlViewerLocation(
                                        initialUrl = it.uri.toString(),
                                        title = it.name,
                                        localFileUri = it.uri.toString()
                                    ),
                                    recentKey = it.uri.toString(),
                                    recentUri = it.uri.toString(),
                                    recentTitle = it.name
                                )
                            },
                            onHtmlUrlTarget = { url ->
                                htmlViewState = HtmlViewState(
                                    location = HtmlViewerLocation(
                                        initialUrl = url,
                                        title = url,
                                        localFileUri = null
                                    ),
                                    recentKey = url,
                                    recentUri = url,
                                    recentTitle = url
                                )
                            },
                            onPicZipTarget = { picZipTarget = it },
                            onLlmZipTarget = { llmZipTarget = it },
                            onTxtTarget = { txtTarget = it },
                            onLlmTarget = { llmTarget = it },
                            onEpubTarget = { epubTarget = it },
                            onPdfTarget = { pdfViewState = Pair(it.uri.toString(), it.name) }
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
                    },
                    directoryContent = {
                        DirectoryTabRoute(
                            DirectoryTabRouteState(
                                modifier = Modifier.fillMaxSize(),
                                currentUri = displayUri,
                                refreshTrigger = refreshTrigger,
                                dirCache = dirCache,
                                onCacheDir = { uri, items -> dirCache = dirCache + (uri to CachedDir(items)) },
                                pendingList = pendingList,
                                rootUri = rootUri,
                                listState = fileListLazyState,
                                onNavigate = { uri ->
                                    fileBrowserBackStack.add(displayUri)
                                    currentUri = normalizeContentUriString(uri)
                                },
                                onBack = {
                                    if (fileBrowserBackStack.isNotEmpty()) {
                                        currentUri = fileBrowserBackStack.removeAt(fileBrowserBackStack.lastIndex)
                                    }
                                },
                                canGoBack = fileBrowserBackStack.isNotEmpty(),
                                onEmptyTrash = rootUri?.let { r ->
                                    {
                                        scope.launch {
                                            val root = Uri.parse(normalizeContentUriString(r))
                                            runWithProgress(context.getString(R.string.main_menu_empty_trash), null) { _ ->
                                                val ok = withContext(Dispatchers.IO) { emptyTrash(context, root, root) }
                                                if (ok) {
                                                    Toast.makeText(context, context.getString(R.string.browser_trash_emptied), Toast.LENGTH_SHORT).show()
                                                    refreshTrigger++
                                                } else {
                                                    Toast.makeText(context, context.getString(R.string.browser_empty_trash_failed), Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    }
                                },
                                onChangeRoot = { showRootSwitchDialog = true },
                                onRestoreFromTrash = rootUri?.let { r ->
                                    { model ->
                                        scope.launch {
                                            val root = Uri.parse(normalizeContentUriString(r))
                                            runWithProgress(context.getString(R.string.context_restore), null) { _ ->
                                                val ok = withContext(Dispatchers.IO) { restoreFromTrash(context, model.uri, root, root) }
                                                if (ok) {
                                                    Toast.makeText(context, context.getString(R.string.browser_restored_to_root), Toast.LENGTH_SHORT).show()
                                                    refreshTrigger++
                                                } else {
                                                    Toast.makeText(context, context.getString(R.string.browser_restore_failed), Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    }
                                },
                                filterVisible = filterVisible,
                                hideDotFiles = hideDotFiles,
                                isViewingTrash = cachedTrashUri != null && (displayUri == cachedTrashUri.toString() || isInsideDirectory(Uri.parse(displayUri), cachedTrashUri!!)),
                                preferredExternalPackages = preferredExternalPackages,
                                onOpenFile = { uri, name, _ ->
                                    viewingFile = Triple(uri, name, false)
                                },
                                onOpenWithOtherApp = { uri, name, packageName, rememberChoice ->
                                    val opened = launchExternalOpen(context, uri, packageName)
                                    if (opened) {
                                        recordRecentOpen(
                                            type = RECENT_TYPE_EXTERNAL_OPEN,
                                            key = uri,
                                            title = name,
                                            uri = uri
                                        )
                                        if (rememberChoice) {
                                            val extension = fileExtensionKey(name)
                                            if (extension != null && !packageName.isNullOrBlank()) {
                                                scope.launch { prefs.setExternalOpenPackageForExtension(extension, packageName) }
                                            }
                                        }
                                    } else {
                                        val extension = fileExtensionKey(name)
                                        if (!packageName.isNullOrBlank() && extension != null) {
                                            scope.launch { prefs.clearExternalOpenPackageForExtension(extension) }
                                            Toast.makeText(context, context.getString(R.string.browser_external_app_unavailable), Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, context.getString(R.string.browser_no_app_to_open), Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                    opened
                                },
                                onClearExternalOpenPreference = { name ->
                                    val extension = fileExtensionKey(name)
                                    if (extension != null) {
                                        scope.launch { prefs.clearExternalOpenPackageForExtension(extension) }
                                    }
                                },
                                onOpenMarkdownView = { uri, name, encrypted ->
                                    markdownViewFile = Triple(uri, name, encrypted)
                                },
                                onAddToPendingList = { item -> pendingList.add(item) },
                                onRemoveFromPendingList = { item -> pendingList.remove(item) },
                                onCopyPendingToCurrentDir = doCopyHere,
                                onMovePendingToCurrentDir = doMoveHere,
                                onShowPendingList = { show -> showPendingList = show },
                                onRefresh = { refreshTrigger++ },
                                onCreateQuickNote = {
                                    switchMainTab(MainTab.QUICK_NOTE)
                                    quickNoteController.requestOpenWithCachedPassword(true)
                                },
                                onCreateMusicPlaylist = { uri, name ->
                                    scope.launch {
                                        val playlist = withContext(Dispatchers.IO) {
                                            createDirectoryPlaybackPlaylistAndStart(context, prefs, uri, name)
                                        }
                                        if (playlist == null) {
                                            Toast.makeText(context, context.getString(R.string.browser_no_music_in_dir), Toast.LENGTH_SHORT).show()
                                        } else {
                                            recordRecentOpen(
                                                type = RECENT_TYPE_PLAYLIST,
                                                key = playlist.id,
                                                title = playlist.displayName(context.getString(R.string.player_unnamed_playlist)),
                                                playlistId = playlist.id
                                            )
                                            switchMainTab(MainTab.PLAYER)
                                            Toast.makeText(context, context.getString(R.string.browser_playlist_from_dir_started), Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                onRequestGpgDecrypt = { fileModel, dirUri ->
                                    gpgPassword = ""
                                    gpgDecryptMode = null
                                    gpgDecryptAutoTried = false
                                    gpgEncryptSelectedKeyId = null
                                    gpgState = GpgOpState.Decrypt(fileModel, dirUri)
                                },
                                onRequestGpgEncrypt = { fileModel, dirUri ->
                                    gpgPassword = ""
                                    gpgDecryptMode = null
                                    gpgDecryptAutoTried = false
                                    gpgEncryptSelectedKeyId = null
                                    gpgState = GpgOpState.Encrypt(fileModel, dirUri)
                                },
                                onRequestQuickObfuscate = { model ->
                                    quickObfuscateOp = model to true
                                    quickObfuscatePassword = ""
                                },
                                onRequestQuickDeobfuscate = { model ->
                                    quickObfuscateOp = model to false
                                    quickObfuscatePassword = ""
                                },
                                onConfirmDelete = { model, deletePermanently ->
                                    scope.launch {
                                        val label = if (deletePermanently) context.getString(R.string.common_delete) else context.getString(R.string.browser_moved_to_trash)
                                        runWithProgress(label, null) { _ ->
                                            val ok = withContext(Dispatchers.IO) {
                                                if (deletePermanently) {
                                                    context.contentResolver.deleteDocument(model.uri)
                                                } else {
                                                    val root = rootUri?.let { Uri.parse(normalizeContentUriString(it)) } ?: return@withContext false
                                                    moveToTrash(context, model.uri, root, root)
                                                }
                                            }
                                            if (ok) {
                                                Toast.makeText(context, if (deletePermanently) context.getString(R.string.browser_deleted) else context.getString(R.string.browser_moved_to_trash), Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, context.getString(R.string.browser_operation_failed), Toast.LENGTH_SHORT).show()
                                            }
                                            refreshTrigger++
                                        }
                                    }
                                },
                                onShareFileToGit = { model ->
                                    shareFileToGitTarget = model
                                },
                                onForwardToBulletin = { items ->
                                    if (items.isEmpty()) return@DirectoryTabRouteState
                                    bulletinForwardPayload = BulletinForwardPayload.Files(items)
                                },
                                onUnzipRequest = { zipUnzipTarget = it },
                                onRequestMdZipView = { mdZipTarget = it },
                                onRequestHtmlView = {
                                    htmlViewState = HtmlViewState(
                                        location = HtmlViewerLocation(
                                            initialUrl = it.uri.toString(),
                                            title = it.name,
                                            localFileUri = it.uri.toString()
                                        ),
                                        recentKey = it.uri.toString(),
                                        recentUri = it.uri.toString(),
                                        recentTitle = it.name
                                    )
                                },
                                onRequestHtmlZipView = { htmlZipTarget = it },
                                onRequestOpenUrl = { url ->
                                    htmlViewState = HtmlViewState(
                                        location = HtmlViewerLocation(
                                            initialUrl = url,
                                            title = url,
                                            localFileUri = null
                                        ),
                                        recentKey = url,
                                        recentUri = url,
                                        recentTitle = url
                                    )
                                },
                                onRequestLlmZipView = { llmZipTarget = it },
                                onRequestEpubView = { epubTarget = it },
                                onRequestTxtView = { txtTarget = it },
                                onRequestLlmView = { llmTarget = it },
                                onRequestPicZipView = { picZipTarget = it },
                                onRequestDirectoryImageView = { item, dirUri, siblings ->
                                    scope.launch {
                                        runCatching {
                                            openDirectoryImageGallery(item, dirUri, siblings)
                                        }.onFailure { error ->
                                            Toast.makeText(
                                                context,
                                                context.getString(R.string.browser_open_dir_images_failed, error.message ?: error.javaClass.simpleName),
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    }
                                },
                                onRequestPdfView = { pdfViewState = Pair(it.uri.toString(), it.name) },
                                onCompressToZipRequest = { zipCompressTarget = it },
                                onCompressTo7zRequest = { sevenZCompressTarget = it },
                                onRequestPassProtect = { model -> passProtectTarget = model },
                                onRequestPassView = { model ->
                                    passViewTarget = model
                                    passViewPassword = ""
                                    passViewTriedCache = false
                                },
                                onRequestPassEdit = { model, dirUri ->
                                    passEditRequest = Pair(model, dirUri)
                                    passEditTriedCache = false
                                },
                                onRequestImportConfig = { model ->
                                    scope.launch {
                                        val jsonString = withContext(Dispatchers.IO) {
                                            try {
                                                context.contentResolver.openInputStream(model.uri)?.use { it.bufferedReader().readText() } ?: ""
                                            } catch (_: Exception) {
                                                ""
                                            }
                                        }
                                        startConfigImport(jsonString)
                                    }
                                },
                                onRequestImportStarDict = { model ->
                                    scope.launch {
                                        val result = withContext(Dispatchers.IO) {
                                            importStarDict(context, model.uri, model.name)
                                        }
                                        result.onSuccess {
                                            val message = if (it.evicted != null) {
                                                context.getString(R.string.browser_dict_imported_evicted, it.imported.name, it.imported.wordCount, it.evicted.name)
                                            } else {
                                                context.getString(R.string.browser_dict_imported, it.imported.name, it.imported.wordCount)
                                            }
                                            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                        }.onFailure {
                                            Toast.makeText(context, context.getString(R.string.browser_dict_import_failed, it.message ?: context.getString(R.string.common_unknown_error)), Toast.LENGTH_LONG).show()
                                        }
                                    }
                                },
                                playbackState = playbackState,
                                onOpenPlaybackScreen = { switchMainTab(MainTab.PLAYER) }
                            )
                        )
                    },
                    ftpContent = {
                        FtpTabRoute(
                            FtpTabRouteState(
                                rootUri = ftpRootUri,
                                displayUri = displayUri,
                                ftpManager = ftpManager,
                                ftpPort = ftpPort,
                                ftpPassword = ftpPassword,
                                networkServiceTimeoutMinutes = networkServiceTimeoutMinutes,
                                onRequestExitApp = { requestExitApp() }
                            )
                        )
                    },
                    familyNetworkContent = {
                        FamilyNetworkScreen(
                            manager = familyNetworkManager,
                            networkPassword = ftpPassword,
                            localServiceEnabled = localNetworkServiceEnabled,
                            familyNetworkUserName = familyNetworkUserName,
                            familyNetworkHostName = familyNetworkHostName,
                            timeoutMinutes = networkServiceTimeoutMinutes,
                            rootUri = rootUri ?: initialDirUri,
                            hideDotFiles = hideDotFiles,
                            onDismiss = { requestExitApp() }
                        )
                    },
                    configContent = {
                        ConfigTabRoute(
                            ConfigTabRouteState(
                                filterVisible = filterVisible,
                                onFilterVisibleChange = { scope.launch { prefs.setFilterVisible(it) } },
                                hideDotFiles = hideDotFiles,
                                onHideDotFilesChange = { scope.launch { prefs.setHideDotFiles(it) } },
                                startupDecryptKey = startupDecryptKey,
                                onStartupDecryptKeyChange = { enabled ->
                                    scope.launch { prefs.setStartupDecryptKey(enabled) }
                                    if (!enabled) SecretKeyPasswordCache.clear()
                                },
                                viewerPreviewBytes = viewerPreviewBytes,
                                onViewerPreviewBytesChange = { scope.launch { prefs.setViewerPreviewBytes(it) } },
                                ftpPassword = ftpPassword ?: "",
                                onFtpPasswordChange = { s ->
                                    ftpPassword = s.ifBlank { null }
                                    scope.launch { prefs.setFtpPassword(s.ifBlank { null }) }
                                    familyNetworkManager.configure(
                                        s.ifBlank { null },
                                        localNetworkServiceEnabled,
                                        familyNetworkUserName.ifBlank { null },
                                        familyNetworkHostName.ifBlank { null }
                                    )
                                },
                                networkServiceTimeoutMinutes = networkServiceTimeoutMinutes,
                                onNetworkServiceTimeoutMinutesChange = { scope.launch { prefs.setNetworkServiceTimeoutMinutes(it) } },
                                localNetworkServiceEnabled = localNetworkServiceEnabled,
                                onLocalNetworkServiceEnabledChange = { enabled ->
                                    localNetworkServiceEnabled = enabled
                                    scope.launch { prefs.setLocalNetworkServiceEnabled(enabled) }
                                    familyNetworkManager.configure(
                                        ftpPassword,
                                        enabled,
                                        familyNetworkUserName.ifBlank { null },
                                        familyNetworkHostName.ifBlank { null }
                                    )
                                },
                                familyNetworkUserName = familyNetworkUserName,
                                onFamilyNetworkUserNameChange = { name ->
                                    familyNetworkUserName = name
                                    scope.launch { prefs.setFamilyNetworkUserName(name.ifBlank { null }) }
                                    familyNetworkManager.configure(
                                        ftpPassword,
                                        localNetworkServiceEnabled,
                                        name.ifBlank { null },
                                        familyNetworkHostName.ifBlank { null }
                                    )
                                },
                                familyNetworkHostName = familyNetworkHostName,
                                onFamilyNetworkHostNameChange = { name ->
                                    familyNetworkHostName = name
                                    scope.launch { prefs.setFamilyNetworkHostName(name.ifBlank { null }) }
                                    familyNetworkManager.configure(
                                        ftpPassword,
                                        localNetworkServiceEnabled,
                                        familyNetworkUserName.ifBlank { null },
                                        name.ifBlank { null }
                                    )
                                },
                                onOpenGitConfig = { showGitConfigDialog = true },
                                onManageKeys = { showKeyManagementDialog = true },
                                onOpenCacheManagement = { showCacheManagementDialog = true },
                                onExportConfig = { openExportConfigDialog() },
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
                    },
                    gitShareContent = {
                        GitShareTabRoute(
                            GitShareTabRouteState(
                                prefs = prefs,
                                rootUri = rootUri,
                                onRequestExitApp = { requestExitApp() }
                            )
                        )
                    },
                    gitProjectsContent = {
                        GitProjectsTabRoute(
                            GitProjectsTabRouteState(
                                prefs = prefs,
                                rootUri = rootUri
                            )
                        )
                    },
                    playerContent = {
                        PlayerTabRoute(
                            PlayerTabRouteState(
                                context = context,
                                prefs = prefs,
                                playbackState = playbackState,
                                onRequestExitApp = { requestExitApp() }
                            )
                        )
                    },
                    bookNoteContent = {
                        BookNoteTabRoute(controller = bookNoteController)
                    },
                    quickNoteContent = {
                        QuickNoteTabRoute(prefs = prefs, controller = quickNoteController)
                    },
                    quickCryptoContent = {
                        QuickCryptoTabRoute()
                    },
                    dictionaryContent = {
                        DictionaryTabRoute(
                            DictionaryTabRouteState(
                                onRequestExitApp = { requestExitApp() }
                            )
                        )
                    },
                    aboutContent = {
                        AboutTabRoute()
                    }
                )
            }

            ScrollableMainTabBar(
                activeMainTab = activeMainTab,
                onSwitchMainTab = { switchMainTab(it) }
            )
        }
        if (showPendingList) {
            PendingListScreen(
                pendingList = pendingList,
                currentDirPath = currentDirPath,
                rootUri = rootUri,
                onRemove = { pendingList.remove(it) },
                onCopyHere = doCopyHere,
                onMoveHere = doMoveHere,
                onRequestDelete = { showPendingDeleteConfirm = true },
                onRequestForwardToBulletin = {
                    if (pendingList.isEmpty()) {
                        Toast.makeText(context, context.getString(R.string.browser_list_empty), Toast.LENGTH_SHORT).show()
                    } else {
                        bulletinForwardPayload = BulletinForwardPayload.Files(pendingList.toList())
                    }
                },
                onRequestBatchObfuscate = {
                    val list = pendingList.filter { !it.isDirectory && !isQuickObfuscatedFileName(it.name) }
                    if (list.isEmpty()) Toast.makeText(context, context.getString(R.string.browser_no_obfuscate_candidates), Toast.LENGTH_SHORT).show()
                    else batchObfuscateOp = list to true
                },
                onRequestBatchDeobfuscate = {
                    val list = pendingList.filter { !it.isDirectory && isQuickObfuscatedFileName(it.name) }
                    if (list.isEmpty()) Toast.makeText(context, context.getString(R.string.browser_no_deobfuscate_candidates), Toast.LENGTH_SHORT).show()
                    else batchObfuscateOp = list to false
                },
                onRequestBatchGpgEncrypt = {
                    if (pendingList.any { it.name.endsWith(".gpg", ignoreCase = true) }) {
                        Toast.makeText(context, context.getString(R.string.browser_batch_encrypt_has_gpg), Toast.LENGTH_SHORT).show()
                    } else {
                        val list = pendingList.filter { !it.isDirectory }
                        if (list.isEmpty()) Toast.makeText(context, context.getString(R.string.browser_no_encrypt_candidates), Toast.LENGTH_SHORT).show()
                        else {
                            gpgPassword = ""
                            gpgDecryptMode = null
                            gpgDecryptAutoTried = false
                            gpgEncryptSelectedKeyId = null
                            gpgState = GpgOpState.BatchEncrypt(list, displayUri)
                        }
                    }
                },
                onRequestBatchGpgDecrypt = {
                    if (pendingList.any { !it.name.endsWith(".gpg", ignoreCase = true) }) {
                        Toast.makeText(context, context.getString(R.string.browser_batch_decrypt_has_non_gpg), Toast.LENGTH_SHORT).show()
                    } else {
                        val list = pendingList.filter { !it.isDirectory }
                        if (list.isEmpty()) Toast.makeText(context, context.getString(R.string.browser_no_decrypt_candidates), Toast.LENGTH_SHORT).show()
                        else {
                            gpgPassword = ""
                            gpgDecryptMode = null
                            gpgDecryptAutoTried = false
                            gpgEncryptSelectedKeyId = null
                            gpgState = GpgOpState.BatchDecrypt(list, displayUri)
                        }
                    }
                },
                onRequestCompressToZip = {
                    if (pendingList.isEmpty()) {
                        Toast.makeText(context, context.getString(R.string.browser_list_empty), Toast.LENGTH_SHORT).show()
                    } else {
                        val ts = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(java.util.Date())
                        pendingCompressZipName = "archive_$ts.zip"
                        pendingCompressPassword = ""
                        showPendingCompressToZip = true
                    }
                },
                onRequestCompressTo7z = {
                    if (pendingList.isEmpty()) {
                        Toast.makeText(context, context.getString(R.string.browser_list_empty), Toast.LENGTH_SHORT).show()
                    } else {
                        val ts = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(java.util.Date())
                        pendingCompress7zName = "archive_$ts.7z"
                        pendingCompress7zPassword = ""
                        showPendingCompressTo7z = true
                    }
                },
                onClearFilteredList = { toRemove ->
                    val snapshot = toRemove.toList()
                    pendingList.removeAll(snapshot)
                },
                onAddToPlayback = { audioList ->
                    if (audioList.isEmpty()) return@PendingListScreen
                    pendingPlaybackAudioList = audioList
                    if (playlists.isEmpty()) {
                        scope.launch { createNewPlaybackPlaylist(audioList) }
                    } else {
                        showPlaybackTargetDialog = true
                    }
                },
                onDismiss = { showPendingList = false }
            )
        }
        bulletinForwardPayload?.let { payload ->
            BulletinForwardTargetFlow(
                manager = familyNetworkManager,
                payload = payload,
                localServiceEnabled = localNetworkServiceEnabled,
                keepEphemeralSession = activeMainTab != MainTab.FAMILY_NETWORK,
                onDismiss = { bulletinForwardPayload = null },
                onComplete = {
                    finishPendingListAndReturn()
                    bulletinForwardPayload = null
                }
            )
        }
        if (showPlaybackTargetDialog && pendingPlaybackAudioList.isNotEmpty()) {
            AlertDialog(
                onDismissRequest = { clearPendingPlaybackTargetState() },
                title = { Text(context.getString(R.string.player_add_to_playback_title)) },
                text = {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            context.getString(R.string.player_add_to_playback_prompt, pendingPlaybackAudioList.size),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(12.dp))
                        val manualPlaylists = playlists.filterNot { it.isDirectorySource }
                        manualPlaylists.forEach { playlist ->
                            OutlinedButton(
                                onClick = {
                                    scope.launch { appendToPlaybackPlaylist(playlist, pendingPlaybackAudioList) }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.fillMaxWidth()) {
                                    Text(playlist.displayName(), color = MaterialTheme.colorScheme.onSurface)
                                    Text(
                                        context.getString(R.string.player_track_count, playlist.trackCount),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                        if (manualPlaylists.isEmpty()) {
                            Text(
                                context.getString(R.string.browser_no_normal_playlist_target),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        scope.launch { createNewPlaybackPlaylist(pendingPlaybackAudioList) }
                    }) { Text(context.getString(R.string.player_new_list)) }
                },
                dismissButton = {
                    TextButton(onClick = { clearPendingPlaybackTargetState() }) { Text(context.getString(R.string.common_cancel)) }
                }
            )
        }
        if (showPendingDeleteConfirm && pendingList.isNotEmpty()) {
            val toDelete = pendingList.toList()
            var deletePermanently by remember { mutableStateOf(false) }
            val hasRoot = rootUri != null
            AlertDialog(
                onDismissRequest = { showPendingDeleteConfirm = false },
                title = { Text(stringResource(R.string.browser_confirm_delete_title)) },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        Text(stringResource(R.string.browser_confirm_delete_count, toDelete.size), color = MaterialTheme.colorScheme.onSurface)
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
                        Spacer(Modifier.height(12.dp))
                        toDelete.forEach { item ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (item.isDirectory) Icons.Default.Folder else Icons.AutoMirrored.Filled.InsertDriveFile,
                                    contentDescription = null,
                                    Modifier.size(20.dp),
                                    tint = if (item.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.size(8.dp))
                                Text(item.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            scope.launch {
                                val label = if (deletePermanently) context.getString(R.string.common_delete) else context.getString(R.string.browser_moved_to_trash)
                                val root = rootUri?.let { Uri.parse(normalizeContentUriString(it)) }
                                runWithProgress(label, toDelete.size) { setProgress ->
                                    withContext(Dispatchers.IO) {
                                        toDelete.forEachIndexed { index, model ->
                                            if (deletePermanently) {
                                                context.contentResolver.deleteDocument(model.uri)
                                            } else {
                                                if (root != null) moveToTrash(context, model.uri, root, root)
                                            }
                                            withContext(Dispatchers.Main.immediate) { setProgress(index + 1) }
                                        }
                                    }
                                }
                                refreshTrigger++
                                showPendingDeleteConfirm = false
                                finishPendingListAndReturn()
                                Toast.makeText(context, if (deletePermanently) context.getString(R.string.browser_deleted_count, toDelete.size) else context.getString(R.string.browser_moved_to_trash_count, toDelete.size), Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) { Text(if (hasRoot && !deletePermanently) stringResource(R.string.browser_moved_to_trash) else stringResource(R.string.browser_confirm_delete_action)) }
                },
                dismissButton = { TextButton(onClick = { showPendingDeleteConfirm = false }) { Text(stringResource(R.string.common_cancel)) } }
            )
        }
        if (showImportCategoryDialog && pendingImportJson != null) {
            AlertDialog(
                onDismissRequest = { clearPendingConfigImportState() },
                title = { Text(context.getString(R.string.config_import_category_title)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = context.getString(R.string.config_import_category_hint),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (ConfigExportCategory.GIT in pendingImportAvailableCategories) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = ConfigExportCategory.GIT in pendingImportSelectedCategories,
                                    onCheckedChange = { checked ->
                                        pendingImportSelectedCategories = if (checked) pendingImportSelectedCategories + ConfigExportCategory.GIT else pendingImportSelectedCategories - ConfigExportCategory.GIT
                                    }
                                )
                                Text(context.getString(R.string.config_export_category_git))
                            }
                        }
                        if (ConfigExportCategory.MUSIC in pendingImportAvailableCategories) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = ConfigExportCategory.MUSIC in pendingImportSelectedCategories,
                                    onCheckedChange = { checked ->
                                        pendingImportSelectedCategories = if (checked) pendingImportSelectedCategories + ConfigExportCategory.MUSIC else pendingImportSelectedCategories - ConfigExportCategory.MUSIC
                                    }
                                )
                                Text(context.getString(R.string.config_export_category_music))
                            }
                        }
                        if (ConfigExportCategory.RECENT in pendingImportAvailableCategories) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = ConfigExportCategory.RECENT in pendingImportSelectedCategories,
                                    onCheckedChange = { checked ->
                                        pendingImportSelectedCategories = if (checked) pendingImportSelectedCategories + ConfigExportCategory.RECENT else pendingImportSelectedCategories - ConfigExportCategory.RECENT
                                    }
                                )
                                Text(context.getString(R.string.config_export_category_recent))
                            }
                        }
                        if (ConfigExportCategory.EPUB in pendingImportAvailableCategories) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = ConfigExportCategory.EPUB in pendingImportSelectedCategories,
                                    onCheckedChange = { checked ->
                                        pendingImportSelectedCategories = if (checked) pendingImportSelectedCategories + ConfigExportCategory.EPUB else pendingImportSelectedCategories - ConfigExportCategory.EPUB
                                    }
                                )
                                Text(context.getString(R.string.config_export_category_epub))
                            }
                        }
                        if (ConfigExportCategory.GPG in pendingImportAvailableCategories) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = ConfigExportCategory.GPG in pendingImportSelectedCategories,
                                    onCheckedChange = { checked ->
                                        pendingImportSelectedCategories = if (checked) pendingImportSelectedCategories + ConfigExportCategory.GPG else pendingImportSelectedCategories - ConfigExportCategory.GPG
                                    }
                                )
                                Text(context.getString(R.string.config_export_category_gpg))
                            }
                        }
                        if (ConfigExportCategory.OTHER in pendingImportAvailableCategories) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = ConfigExportCategory.OTHER in pendingImportSelectedCategories,
                                    onCheckedChange = { checked ->
                                        pendingImportSelectedCategories = if (checked) pendingImportSelectedCategories + ConfigExportCategory.OTHER else pendingImportSelectedCategories - ConfigExportCategory.OTHER
                                    }
                                )
                                Text(context.getString(R.string.config_export_category_other))
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        if (pendingImportSelectedCategories.isEmpty()) {
                            Toast.makeText(context, context.getString(R.string.config_import_select_one_category), Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        showImportCategoryDialog = false
                        continueConfigImportAfterCategoryChoice()
                    }) { Text(context.getString(R.string.common_continue)) }
                },
                dismissButton = {
                    TextButton(onClick = { clearPendingConfigImportState() }) { Text(context.getString(R.string.common_cancel)) }
                }
            )
        }
        if (showImportPlaylistConfirmDialog && pendingImportJson != null) {
            AlertDialog(
                onDismissRequest = { clearPendingConfigImportState() },
                title = { Text(stringResource(R.string.browser_import_playlist_title)) },
                text = {
                    Text(
                        stringResource(R.string.browser_import_playlist_prompt, pendingImportPlaylistCount),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        showImportPlaylistConfirmDialog = false
                        pendingImportPlaylistMode = ConfigPlaylistImportMode.APPEND
                        continueConfigImportAfterPlaylistChoice()
                    }) { Text(stringResource(R.string.browser_append)) }
                },
                dismissButton = {
                    Row {
                        TextButton(onClick = {
                            showImportPlaylistConfirmDialog = false
                            pendingImportPlaylistMode = ConfigPlaylistImportMode.OVERWRITE
                            continueConfigImportAfterPlaylistChoice()
                        }) { Text(stringResource(R.string.file_share_overwrite_action)) }
                        TextButton(onClick = { clearPendingConfigImportState() }) { Text(stringResource(R.string.common_cancel)) }
                    }
                }
            )
        }
        if (showImportKeyConfirmDialog && pendingImportJson != null) {
            val jsonToImport = pendingImportJson!!
            AlertDialog(
                onDismissRequest = {
                    clearPendingConfigImportState()
                },
                title = { Text(stringResource(R.string.config_import_category_title)) },
                text = {
                    Text(
                        stringResource(R.string.browser_import_config_keys_prompt),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showImportKeyConfirmDialog = false
                            scope.launch {
                                performConfigImport(jsonToImport, importKeys = true, categories = pendingImportSelectedCategories)
                            }
                        }
                    ) { Text(stringResource(R.string.browser_replace_all)) }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showImportKeyConfirmDialog = false
                            scope.launch {
                                performConfigImport(jsonToImport, importKeys = false, categories = pendingImportSelectedCategories)
                            }
                        }
                    ) { Text(stringResource(R.string.browser_skip_keys)) }
                }
            )
        }
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
                                        val previousRoot = rootUri?.let { normalizeContentUriString(it) }
                                        showRootSwitchDialog = false
                                        if (previousRoot == normalized) return@OutlinedButton
                                        scope.launch {
                                            prefs.recordRecentRootSwitch(previousRoot, normalized)
                                            prefs.setRootUri(normalized)
                                        }
                                        rootUri = normalized
                                        initialDirUri = normalized
                                        currentUri = normalized
                                        refreshTrigger++
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
                        val r = rootUri
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
        if (showExportConfigDialog) {
            AlertDialog(
                onDismissRequest = { showExportConfigDialog = false },
                title = { Text(context.getString(R.string.config_export_dialog_title)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = exportConfigFileName,
                            onValueChange = { exportConfigFileName = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text(context.getString(R.string.config_export_file_name_label)) },
                            placeholder = { Text(CONFIG_EXPORT_FILE_NAME) }
                        )
                        Text(
                            text = context.getString(R.string.config_export_category_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = exportGitCategory, onCheckedChange = { exportGitCategory = it })
                            Text(context.getString(R.string.config_export_category_git))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = exportMusicCategory, onCheckedChange = { exportMusicCategory = it })
                            Text(context.getString(R.string.config_export_category_music))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = exportRecentCategory, onCheckedChange = { exportRecentCategory = it })
                            Text(context.getString(R.string.config_export_category_recent))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = exportEpubCategory, onCheckedChange = { exportEpubCategory = it })
                            Text(context.getString(R.string.config_export_category_epub))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = exportGpgCategory, onCheckedChange = { exportGpgCategory = it })
                            Text(context.getString(R.string.config_export_category_gpg))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = exportOtherCategory, onCheckedChange = { exportOtherCategory = it })
                            Text(context.getString(R.string.config_export_category_other))
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        val normalizedFileName = normalizeExportFileName(exportConfigFileName)
                        if (normalizedFileName == null) {
                            Toast.makeText(context, context.getString(R.string.config_export_invalid_file_name), Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        val categories = buildSet {
                            if (exportGitCategory) add(ConfigExportCategory.GIT)
                            if (exportMusicCategory) add(ConfigExportCategory.MUSIC)
                            if (exportRecentCategory) add(ConfigExportCategory.RECENT)
                            if (exportEpubCategory) add(ConfigExportCategory.EPUB)
                            if (exportGpgCategory) add(ConfigExportCategory.GPG)
                            if (exportOtherCategory) add(ConfigExportCategory.OTHER)
                        }
                        if (categories.isEmpty()) {
                            Toast.makeText(context, context.getString(R.string.config_export_select_one_category), Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        showExportConfigDialog = false
                        scope.launch {
                            when (exportConfigToRoot(normalizedFileName, categories)) {
                                ConfigExportResult.SUCCESS -> {
                                    Toast.makeText(context, context.getString(R.string.config_export_success), Toast.LENGTH_SHORT).show()
                                    refreshTrigger++
                                }
                                ConfigExportResult.FILE_EXISTS -> {
                                    Toast.makeText(context, context.getString(R.string.config_export_file_exists, normalizedFileName), Toast.LENGTH_LONG).show()
                                }
                                ConfigExportResult.FAILED -> {
                                    Toast.makeText(context, context.getString(R.string.config_export_failed), Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }) {
                        Text(context.getString(R.string.config_export))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showExportConfigDialog = false }) {
                        Text(context.getString(R.string.common_cancel))
                    }
                }
            )
        }
        if (showGitConfigDialog) {
            GitConfigDialog(
                prefs = prefs,
                rootUri = rootUri?.let { normalizeContentUriString(it) },
                onDismiss = {
                    showGitConfigDialog = false
                    refreshTrigger++
                }
            )
        }
        if (showKeyManagementDialog) {
            KeyManagementDialog(
                context = context,
                onDismiss = { showKeyManagementDialog = false },
                onKeysChanged = { refreshTrigger++ },
                onOpenPubkeyShare = {
                    showKeyManagementDialog = false
                    showPubkeyShareScreen = true
                }
            )
        }
        if (showPubkeyShareScreen) {
            PubkeyShareScreen(
                prefs = prefs,
                rootUri = rootUri?.let { normalizeContentUriString(it) },
                onDismiss = {
                    showPubkeyShareScreen = false
                    refreshTrigger++
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
                        val r = rootUri?.let { normalizeContentUriString(it) }
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
        quickObfuscateOp?.let { (model, isObfuscate) ->
            QuickObfuscatePasswordDialog(
                isObfuscate = isObfuscate,
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
                            if (isObfuscate) quickObfuscate(context, model.uri, pwd.toCharArray())
                            else quickDeobfuscate(context, model.uri, pwd.toCharArray())
                        }
                        quickObfuscateInProgress = false
                        quickObfuscateOp = null
                        quickObfuscatePassword = ""
                        quickObfuscatePasswordConfirm = ""
                        if (ok) {
                            Toast.makeText(context, if (isObfuscate) context.getString(R.string.browser_obfuscated_badge) else context.getString(R.string.pending_list_action_deobfuscate), Toast.LENGTH_SHORT).show()
                            refreshTrigger++
                        } else {
                            Toast.makeText(context, if (isObfuscate) context.getString(R.string.browser_obfuscate_failed) else context.getString(R.string.browser_deobfuscate_failed), Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onDismiss = {
                    quickObfuscateOp = null
                    quickObfuscatePassword = ""
                    quickObfuscatePasswordConfirm = ""
                }
            )
        }
        batchObfuscateOp?.let { (list, isObfuscate) ->
            QuickObfuscatePasswordDialog(
                isObfuscate = isObfuscate,
                fileName = context.getString(R.string.browser_file_count, list.size),
                password = batchObfuscatePassword,
                confirmPassword = batchObfuscatePasswordConfirm,
                inProgress = batchObfuscateInProgress,
                onPasswordChange = { batchObfuscatePassword = it },
                onConfirmPasswordChange = { batchObfuscatePasswordConfirm = it },
                onConfirm = { pwd ->
                    batchObfuscateInProgress = true
                    scope.launch {
                        runWithProgress(if (isObfuscate) context.getString(R.string.pending_list_action_obfuscate) else context.getString(R.string.pending_list_action_deobfuscate), list.size) { setProgress ->
                            withContext(Dispatchers.IO) {
                                list.forEachIndexed { index, model ->
                                    if (isObfuscate) quickObfuscate(context, model.uri, pwd.toCharArray())
                                    else quickDeobfuscate(context, model.uri, pwd.toCharArray())
                                    withContext(Dispatchers.Main.immediate) { setProgress(index + 1) }
                                }
                            }
                        }
                        batchObfuscateInProgress = false
                        batchObfuscateOp = null
                        batchObfuscatePassword = ""
                        batchObfuscatePasswordConfirm = ""
                        Toast.makeText(context, if (isObfuscate) context.getString(R.string.browser_obfuscated_count, list.size) else context.getString(R.string.browser_deobfuscated_count, list.size), Toast.LENGTH_SHORT).show()
                        finishPendingListAndReturn()
                        refreshTrigger++
                    }
                },
                onDismiss = {
                    batchObfuscateOp = null
                    batchObfuscatePassword = ""
                    batchObfuscatePasswordConfirm = ""
                }
            )
        }
        // ---- 密码保护：加密 md/rst -> .pass ----
        passProtectTarget?.let { model ->
            AlertDialog(
                onDismissRequest = { if (!passProtectInProgress) passProtectTarget = null },
                title = { Text(stringResource(R.string.context_pass_protect)) },
                text = { Text(stringResource(R.string.browser_pass_protect_confirm, model.name, model.name)) },
                confirmButton = {
                    Button(
                        onClick = {
                            passProtectInProgress = true
                            val ctx = context
                            val dirUri = normalizeContentUriString(displayUri)
                            val treeUri = rootUri?.let { Uri.parse(normalizeContentUriString(it)) }
                            scope.launch {
                                val ok = withContext(Dispatchers.IO) {
                                    val secRings = loadSecretKeyRings(ctx)
                                    if (secRings == null) {
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(ctx, ctx.getString(R.string.browser_no_default_key_generate), Toast.LENGTH_LONG).show()
                                        }
                                        return@withContext false
                                    }
                                    val defaultKeyId = secRings.iterator().asSequence().firstOrNull()?.publicKey?.keyID
                                    if (defaultKeyId == null) {
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(ctx, ctx.getString(R.string.browser_no_default_key), Toast.LENGTH_LONG).show()
                                        }
                                        return@withContext false
                                    }
                                    val pubRings = loadPublicKeyRings(ctx)
                                    val pubKeyRing = findPublicKeyRing(pubRings, defaultKeyId)
                                    if (pubKeyRing == null) {
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(ctx, ctx.getString(R.string.browser_no_default_public_key), Toast.LENGTH_LONG).show()
                                        }
                                        return@withContext false
                                    }
                                    val plain = ctx.contentResolver.openInputStreamSafe(model.uri)?.use { it.readBytes() }
                                        ?: return@withContext false
                                    val encrypted = GpgHelper.encryptWithPublicKey(plain, pubKeyRing, model.name)
                                        ?: return@withContext false
                                    val outName = model.name + ".pass"
                                    val created = createFileWithBytes(ctx, Uri.parse(dirUri), treeUri, outName, "application/octet-stream", encrypted)
                                    if (created) {
                                        ctx.contentResolver.deleteDocument(model.uri)
                                    }
                                    created
                                }
                                passProtectInProgress = false
                                passProtectTarget = null
                                if (ok) {
                                    Toast.makeText(ctx, ctx.getString(R.string.browser_encrypted_as_pass, model.name), Toast.LENGTH_SHORT).show()
                                    refreshTrigger++
                                } else {
                                    Toast.makeText(ctx, ctx.getString(R.string.browser_pass_protect_failed), Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        enabled = !passProtectInProgress
                    ) { Text(if (passProtectInProgress) stringResource(R.string.browser_encrypting) else stringResource(R.string.common_ok)) }
                },
                dismissButton = {
                    TextButton(
                        onClick = { passProtectTarget = null },
                        enabled = !passProtectInProgress
                    ) { Text(stringResource(R.string.common_cancel)) }
                }
            )
        }
        // ---- 密码保护：查看 .pass 文件 ----
        passViewTarget?.let { model ->
            val secRings = loadSecretKeyRings(context)
            if (secRings == null) {
                AlertDialog(
                    onDismissRequest = { passViewTarget = null },
                    title = { Text(stringResource(R.string.context_view_password)) },
                    text = { Text(stringResource(R.string.browser_no_default_secret_key)) },
                    confirmButton = {
                        Button(onClick = { passViewTarget = null }) { Text(stringResource(R.string.common_ok)) }
                    }
                )
            } else {
                LaunchedEffect(model) {
                    if (passViewTriedCache) return@LaunchedEffect
                    val cached = SecretKeyPasswordCache.get() ?: run { passViewTriedCache = true; return@LaunchedEffect }
                    passViewTriedCache = true
                    passViewInProgress = true
                    val decrypted = withContext(Dispatchers.IO) {
                        val rings = loadSecretKeyRings(context) ?: return@withContext null
                        context.contentResolver.openInputStreamSafe(model.uri)?.use { input ->
                            GpgHelper.decryptWithSecretKey(input, rings, cached) { _ -> }
                        }
                    }
                    passViewInProgress = false
                    if (decrypted != null) {
                        passViewTarget = null
                        val innerName = model.name.removeSuffix(".pass").removeSuffix(".PASS")
                        passContentView = PassDecryptedContent(innerName, decrypted)
                    } else {
                        Toast.makeText(context, context.getString(R.string.browser_decrypt_check_password), Toast.LENGTH_SHORT).show()
                    }
                }
                if (SecretKeyPasswordCache.get() == null || passViewTriedCache) {
                GpgPasswordDialog(
                    isDecrypt = true,
                    fileName = model.name,
                    password = passViewPassword,
                    passwordLabel = context.getString(R.string.browser_key_password_label),
                    inProgress = passViewInProgress,
                    onPasswordChange = { if (!passViewInProgress) passViewPassword = it },
                    onConfirm = { pwd ->
                        if (passViewInProgress) return@GpgPasswordDialog
                        passViewInProgress = true
                        val ctx = context
                        scope.launch {
                            val decrypted = withContext(Dispatchers.IO) {
                                val rings = loadSecretKeyRings(ctx) ?: return@withContext null
                                ctx.contentResolver.openInputStreamSafe(model.uri)?.use { input ->
                                    GpgHelper.decryptWithSecretKey(
                                        input, rings, pwd.toCharArray()
                                    ) { _ -> }
                                }
                            }
                            passViewInProgress = false
                            if (decrypted != null) {
                                passViewTarget = null
                                passViewPassword = ""
                                // 根据内层扩展名确定渲染方式
                                val innerName = model.name.removeSuffix(".pass").removeSuffix(".PASS")
                                passContentView = PassDecryptedContent(innerName, decrypted)
                            } else {
                                Toast.makeText(ctx, ctx.getString(R.string.browser_decrypt_check_password), Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    onDismiss = {
                        if (!passViewInProgress) {
                            passViewTarget = null
                            passViewPassword = ""
                        }
                    }
                )
                }
            }
        }
        // ---- 密码保护：直接编辑 .pass 文件（解密后进入编辑界面） ----
        passEditRequest?.let { (model, dirUri) ->
            val secRings = loadSecretKeyRings(context)
            if (secRings == null) {
                AlertDialog(
                    onDismissRequest = { passEditRequest = null },
                    title = { Text(stringResource(R.string.context_edit_directly)) },
                    text = { Text(stringResource(R.string.browser_no_default_secret_key)) },
                    confirmButton = {
                        Button(onClick = { passEditRequest = null }) { Text(stringResource(R.string.common_ok)) }
                    }
                )
            } else {
                LaunchedEffect(model) {
                    if (passEditTriedCache) return@LaunchedEffect
                    val cached = SecretKeyPasswordCache.get() ?: run { passEditTriedCache = true; return@LaunchedEffect }
                    passEditTriedCache = true
                    passEditInProgress = true
                    val decrypted = withContext(Dispatchers.IO) {
                        val rings = loadSecretKeyRings(context) ?: return@withContext null
                        context.contentResolver.openInputStreamSafe(model.uri)?.use { input ->
                            GpgHelper.decryptWithSecretKey(input, rings, cached) { _ -> }
                        }
                    }
                    passEditInProgress = false
                    if (decrypted != null) {
                        passEditRequest = null
                        val innerName = model.name.removeSuffix(".pass").removeSuffix(".PASS")
                        val tree = rootUri?.let { Uri.parse(normalizeContentUriString(it)) }
                        passEditState = PassEditState(model, dirUri, tree, innerName, decrypted)
                    } else {
                        Toast.makeText(context, context.getString(R.string.browser_decrypt_check_password), Toast.LENGTH_SHORT).show()
                    }
                }
                if (SecretKeyPasswordCache.get() == null || passEditTriedCache) {
                GpgPasswordDialog(
                    isDecrypt = true,
                    fileName = model.name,
                    password = passEditPassword,
                    passwordLabel = context.getString(R.string.browser_key_password_label),
                    inProgress = passEditInProgress,
                    onPasswordChange = { if (!passEditInProgress) passEditPassword = it },
                    onConfirm = { pwd ->
                        if (passEditInProgress) return@GpgPasswordDialog
                        passEditInProgress = true
                        val ctx = context
                        scope.launch {
                            val decrypted = withContext(Dispatchers.IO) {
                                val rings = loadSecretKeyRings(ctx) ?: return@withContext null
                                ctx.contentResolver.openInputStreamSafe(model.uri)?.use { input ->
                                    GpgHelper.decryptWithSecretKey(
                                        input, rings, pwd.toCharArray()
                                    ) { _ -> }
                                }
                            }
                            passEditInProgress = false
                            if (decrypted != null) {
                                passEditRequest = null
                                val innerName = model.name.removeSuffix(".pass").removeSuffix(".PASS")
                                val tree = rootUri?.let { Uri.parse(normalizeContentUriString(it)) }
                                passEditState = PassEditState(model, dirUri, tree, innerName, decrypted)
                            } else {
                                Toast.makeText(ctx, ctx.getString(R.string.browser_decrypt_check_password), Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    onDismiss = {
                        if (!passEditInProgress) {
                            passEditRequest = null
                        }
                    }
                )
                }
            }
        }
        TabPasswordPromptDialog(
            state = quickNoteController.passwordPromptState,
            fileName = QUICK_NOTE_GPG_FILE_NAME
        )
        TabPasswordPromptDialog(
            state = bookNoteController.passwordPromptState,
            fileName = QUICK_NOTE_GPG_FILE_NAME
        )
        zipUnzipTarget?.let { target ->
            val parentDirUri = Uri.parse(displayUri)
            val treeUri = rootUri?.let { Uri.parse(normalizeContentUriString(it)) }
            val encrypted = zipUnzipEncrypted
            AlertDialog(
                onDismissRequest = { zipUnzipTarget = null; zipUnzipPassword = "" },
                title = { Text(stringResource(R.string.browser_extract_archive_title)) },
                text = {
                    Column {
                        Text(stringResource(R.string.browser_extract_archive_confirm, target.name), color = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(20.dp)
                        ) {
                            when (encrypted) {
                                true -> Text(
                                    context.getString(R.string.browser_archive_encrypted_prompt),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                null -> Text(
                                    context.getString(R.string.browser_detecting_encryption),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                else -> { }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        if (encrypted != false) {
                            ReliablePasswordInputField(
                                value = zipUnzipPassword,
                                onValueChange = { zipUnzipPassword = it },
                                label = { Text(stringResource(R.string.browser_archive_password_label)) },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = encrypted == true
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (encrypted == true && zipUnzipPassword.isBlank()) return@Button
                            scope.launch {
                                progressOp = OperationProgress(context.getString(R.string.browser_op_extract), 0, null)
                                delay(50)
                                val pwd = if (encrypted == true) zipUnzipPassword.toCharArray() else null
                                val result = withContext(Dispatchers.IO) {
                                    unzipToParent(
                                        context,
                                        target.uri,
                                        parentDirUri,
                                        treeUri,
                                        target.name,
                                        pwd
                                    ) { cur, tot ->
                                        scope.launch(Dispatchers.Main.immediate) { progressOp = OperationProgress(context.getString(R.string.browser_op_extract), cur, tot) }
                                    }
                                }
                                // 延迟再关闭进度条，避免 setProgress 的 Main.immediate 晚于本行执行导致进度条不消失
                                delay(120)
                                progressOp = null
                                when (result) {
                                    is UnzipResult.Success -> {
                                        zipUnzipTarget = null
                                        zipUnzipPassword = ""
                                        zipUnzipEncrypted = null
                                        Toast.makeText(context, context.getString(R.string.browser_extract_complete), Toast.LENGTH_SHORT).show()
                                        refreshTrigger++
                                    }
                                    is UnzipResult.PasswordRequired -> {
                                        // 需要密码但未提供，显示密码输入框让用户重试
                                        zipUnzipEncrypted = true
                                        Toast.makeText(context, context.getString(R.string.browser_enter_password), Toast.LENGTH_SHORT).show()
                                    }
                                    is UnzipResult.WrongPassword -> {
                                        // 密码错误，保留对话框让用户重试
                                        zipUnzipEncrypted = true
                                        Toast.makeText(context, context.getString(R.string.browser_wrong_password_retry), Toast.LENGTH_SHORT).show()
                                    }
                                    is UnzipResult.CorruptedFile -> {
                                        zipUnzipTarget = null
                                        zipUnzipPassword = ""
                                        zipUnzipEncrypted = null
                                        Toast.makeText(context, context.getString(R.string.browser_extract_failed_corrupt), Toast.LENGTH_SHORT).show()
                                    }
                                    is UnzipResult.UnsupportedFormat -> {
                                        zipUnzipTarget = null
                                        zipUnzipPassword = ""
                                        zipUnzipEncrypted = null
                                        Toast.makeText(context, context.getString(R.string.browser_extract_failed_format), Toast.LENGTH_SHORT).show()
                                    }
                                    is UnzipResult.IOError -> {
                                        zipUnzipTarget = null
                                        zipUnzipPassword = ""
                                        zipUnzipEncrypted = null
                                        val msg = result.message ?: context.getString(R.string.common_unknown_error)
                                        Toast.makeText(context, context.getString(R.string.browser_extract_failed_detail, msg), Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                    ) { Text(stringResource(R.string.browser_extract_action)) }
                },
                dismissButton = { TextButton(onClick = { zipUnzipTarget = null; zipUnzipPassword = "" }) { Text(stringResource(R.string.common_cancel)) } }
            )
        }
        // ---- .md.zip 密码输入对话框（仅加密 zip 时弹出） ----
        mdZipTarget?.let { target ->
            val encrypted = mdZipEncrypted
            if (encrypted == true) {
                AlertDialog(
                    onDismissRequest = { if (!mdZipInProgress) { mdZipTarget = null; mdZipPassword = "" } },
                    title = { Text(stringResource(R.string.context_view_zip_markdown)) },
                    text = {
                        Column {
                            Text(stringResource(R.string.browser_zip_encrypted_prompt, target.name), color = MaterialTheme.colorScheme.onSurface)
                            Spacer(Modifier.height(12.dp))
                            ReliablePasswordInputField(
                                value = mdZipPassword,
                                onValueChange = { if (!mdZipInProgress) mdZipPassword = it },
                                label = { Text(stringResource(R.string.browser_zip_password_label)) },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !mdZipInProgress
                            )
                            if (mdZipInProgress) {
                                Spacer(Modifier.height(8.dp))
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (mdZipInProgress || mdZipPassword.isBlank()) return@Button
                                mdZipInProgress = true
                                val pwd = mdZipPassword.toCharArray()
                                scope.launch {
                                    val result = withContext(Dispatchers.IO) {
                                        val cacheDir = getMdZipCacheDir(context, target.uri)
                                        val cacheTs = getMdZipCacheTimestamp(cacheDir)
                                        val contentDir = java.io.File(cacheDir, "content")
                                        val isRstZip = target.name.endsWith(".rst.zip", ignoreCase = true)
                                        if (
                                            cacheTs > 0 &&
                                            isMdZipCacheEncrypted(cacheDir) &&
                                            cacheTs >= target.lastModified &&
                                            contentDir.exists() &&
                                            contentDir.listFiles()?.isNotEmpty() == true &&
                                            tryArchivePassword(context, target.uri, pwd)
                                        ) {
                                            MdZipExtractResult(
                                                cacheDir = cacheDir,
                                                contentDir = contentDir,
                                                targetFile = findMdZipCacheTarget(context, cacheDir, isRstZip),
                                                isEncrypted = true
                                            )
                                        } else {
                                            extractMdZipToCache(context, target.uri, pwd, target.name)
                                        }
                                    }
                                    mdZipInProgress = false
                                    if (result != null) {
                                        mdZipTarget = null
                                        mdZipPassword = ""
                                        mdZipViewState = MdZipViewState(
                                            targetFile = result.targetFile,  // 可能为 null
                                            contentDir = result.contentDir,
                                            zipFileName = target.name,
                                            zipUri = target.uri,
                                            isEncrypted = true
                                        )
                                    } else {
                                        Toast.makeText(context, context.getString(R.string.browser_extract_failed_password), Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        ) { Text(stringResource(R.string.common_ok)) }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { if (!mdZipInProgress) { mdZipTarget = null; mdZipPassword = "" } }
                        ) { Text(stringResource(R.string.common_cancel)) }
                    }
                )
            } else if (encrypted == null) {
                // 正在检测加密状态或正在解压非加密 zip
                AlertDialog(
                    onDismissRequest = {},
                    title = { Text(stringResource(R.string.context_view_zip_markdown)) },
                    text = {
                        Column {
                            Text(stringResource(R.string.browser_processing_file, target.name), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    },
                    confirmButton = {}
                )
            }
        }
        // ---- .html.zip 密码输入对话框 ----
        htmlZipTarget?.let { target ->
            val encrypted = htmlZipEncrypted
            if (encrypted == true) {
                AlertDialog(
                    onDismissRequest = { if (!htmlZipInProgress) { htmlZipTarget = null; htmlZipPassword = "" } },
                    title = { Text(stringResource(R.string.context_view_zip_html)) },
                    text = {
                        Column {
                            Text(stringResource(R.string.browser_zip_encrypted_prompt, target.name), color = MaterialTheme.colorScheme.onSurface)
                            Spacer(Modifier.height(12.dp))
                            ReliablePasswordInputField(
                                value = htmlZipPassword,
                                onValueChange = { if (!htmlZipInProgress) htmlZipPassword = it },
                                label = { Text(stringResource(R.string.browser_zip_password_label)) },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !htmlZipInProgress
                            )
                            if (htmlZipInProgress) {
                                Spacer(Modifier.height(8.dp))
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (htmlZipInProgress || htmlZipPassword.isBlank()) return@Button
                                htmlZipInProgress = true
                                val pwd = htmlZipPassword.toCharArray()
                                scope.launch {
                                    val result = withContext(Dispatchers.IO) {
                                        val cacheDir = getHtmlZipCacheDir(context, target.uri)
                                        val cacheTs = getHtmlZipCacheTimestamp(cacheDir)
                                        val contentDir = java.io.File(cacheDir, "content")
                                        if (
                                            cacheTs > 0 &&
                                            isHtmlZipCacheEncrypted(cacheDir) &&
                                            cacheTs >= target.lastModified &&
                                            contentDir.exists() &&
                                            contentDir.listFiles()?.isNotEmpty() == true &&
                                            tryArchivePassword(context, target.uri, pwd)
                                        ) {
                                            HtmlZipExtractResult(
                                                cacheDir = cacheDir,
                                                contentDir = contentDir,
                                                indexFile = findHtmlZipIndexFile(contentDir),
                                                isEncrypted = true
                                            )
                                        } else {
                                            extractHtmlZipToCache(context, target.uri, pwd, target.name)
                                        }
                                    }
                                    htmlZipInProgress = false
                                    if (result != null) {
                                        htmlZipTarget = null
                                        htmlZipPassword = ""
                                        htmlZipViewState = HtmlZipViewState(
                                            indexFile = result.indexFile,
                                            contentDir = result.contentDir,
                                            zipFileName = target.name,
                                            zipUri = target.uri,
                                            isEncrypted = true
                                        )
                                    } else {
                                        Toast.makeText(context, context.getString(R.string.browser_extract_failed_password), Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        ) { Text(stringResource(R.string.common_ok)) }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { if (!htmlZipInProgress) { htmlZipTarget = null; htmlZipPassword = "" } }
                        ) { Text(stringResource(R.string.common_cancel)) }
                    }
                )
            } else if (encrypted == null) {
                // 加载中或显示错误
                AlertDialog(
                    onDismissRequest = {},
                    title = { Text(stringResource(R.string.context_view_zip_html)) },
                    text = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 100.dp, max = 400.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            if (htmlZipInProgress) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(modifier = Modifier.width(18.dp).height(18.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.browser_loading_dots), style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                htmlZipLog,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            )
                        }
                    },
                    confirmButton = {
                        if (htmlZipLoadError != null) {
                            TextButton(onClick = { htmlZipTarget = null; htmlZipLoadError = null; htmlZipLog = "" }) {
                                Text(stringResource(R.string.common_close))
                            }
                        }
                    }
                )
            }
        }
        // ---- .llm.zip 密码输入对话框 ----
        llmZipTarget?.let { target ->
            val encrypted = llmZipEncrypted
            if (encrypted == true) {
                AlertDialog(
                    onDismissRequest = { if (!llmZipInProgress) { llmZipTarget = null; llmZipPassword = "" } },
                    title = { Text(stringResource(R.string.context_view_zip_llm)) },
                    text = {
                        Column {
                            Text(stringResource(R.string.browser_zip_encrypted_prompt, target.name), color = MaterialTheme.colorScheme.onSurface)
                            Spacer(Modifier.height(12.dp))
                            ReliablePasswordInputField(
                                value = llmZipPassword,
                                onValueChange = { if (!llmZipInProgress) llmZipPassword = it },
                                label = { Text(stringResource(R.string.browser_zip_password_label)) },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !llmZipInProgress
                            )
                            if (llmZipInProgress) {
                                Spacer(Modifier.height(8.dp))
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (llmZipInProgress || llmZipPassword.isBlank()) return@Button
                                llmZipInProgress = true
                                val pwd = llmZipPassword.toCharArray()
                                scope.launch {
                                    val result = withContext(Dispatchers.IO) {
                                        val cacheDir = getEpubCacheDir(context, target.uri)
                                        val cacheTs = getEpubCacheTimestamp(cacheDir)
                                        if (
                                            cacheTs > 0 &&
                                            isEpubCacheEncrypted(cacheDir) &&
                                            cacheTs >= target.lastModified &&
                                            tryArchivePassword(context, target.uri, pwd)
                                        ) {
                                            loadEpubFromCache(context, cacheDir)
                                        } else {
                                            extractLlmZipToCache(context, target.uri, pwd, target.name)
                                        }
                                    }
                                    llmZipInProgress = false
                                    if (result != null) {
                                        llmZipTarget = null
                                        llmZipPassword = ""
                                        epubViewState = EpubViewState(
                                            extractResult = result,
                                            zipFileName = target.name,
                                            epubUri = target.uri,
                                            isEncrypted = true
                                        )
                                    } else {
                                        Toast.makeText(context, context.getString(R.string.browser_extract_failed_password_content), Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        ) { Text(stringResource(R.string.common_ok)) }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { if (!llmZipInProgress) { llmZipTarget = null; llmZipPassword = "" } }
                        ) { Text(stringResource(R.string.common_cancel)) }
                    }
                )
            } else if (encrypted == null) {
                AlertDialog(
                    onDismissRequest = {},
                    title = { Text(stringResource(R.string.context_view_zip_llm)) },
                    text = {
                        Column {
                            Text(stringResource(R.string.browser_processing_file, target.name), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    },
                    confirmButton = {}
                )
            }
        }
        // ---- .epub 密码输入对话框 ----
        epubTarget?.let { target ->
            val encrypted = epubEncrypted
            if (encrypted == true) {
                AlertDialog(
                    onDismissRequest = { if (!epubInProgress) { epubTarget = null; epubPassword = "" } },
                    title = { Text(stringResource(R.string.browser_view_epub_title)) },
                    text = {
                        Column {
                            Text(stringResource(R.string.browser_zip_encrypted_prompt, target.name), color = MaterialTheme.colorScheme.onSurface)
                            Spacer(Modifier.height(12.dp))
                            ReliablePasswordInputField(
                                value = epubPassword,
                                onValueChange = { if (!epubInProgress) epubPassword = it },
                                label = { Text(stringResource(R.string.browser_zip_password_label)) },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !epubInProgress
                            )
                            if (epubInProgress) {
                                Spacer(Modifier.height(8.dp))
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (epubInProgress || epubPassword.isBlank()) return@Button
                                epubInProgress = true
                                epubLog = context.getString(R.string.browser_epub_decrypt_start)
                                val pwd = epubPassword.toCharArray()
                                scope.launch {
                                    val result = withContext(Dispatchers.IO) {
                                        val cacheDir = getEpubCacheDir(context, target.uri)
                                        val cacheTs = getEpubCacheTimestamp(cacheDir)
                                        if (
                                            cacheTs > 0 &&
                                            isEpubCacheEncrypted(cacheDir) &&
                                            cacheTs >= target.lastModified &&
                                            tryArchivePassword(context, target.uri, pwd)
                                        ) {
                                            loadEpubFromCache(context, cacheDir)?.let { EpubParseResult.Success(it) }
                                                ?: extractEpubToCache(context, target.uri, pwd, target.name) { log ->
                                                    epubLog += "$log\n"
                                                }
                                        } else {
                                            extractEpubToCache(context, target.uri, pwd, target.name) { log ->
                                                epubLog += "$log\n"
                                            }
                                        }
                                    }
                                    epubInProgress = false
                                    when (result) {
                                        is EpubParseResult.Success -> {
                                            epubTarget = null
                                            epubPassword = ""
                                            epubViewState = EpubViewState(
                                                extractResult = result.result,
                                                zipFileName = target.name,
                                                epubUri = target.uri,
                                                isEncrypted = true
                                            )
                                        }
                                        is EpubParseResult.Error -> {
                                            val detail = result.detail?.let { "\n$it" } ?: ""
                                            epubLog += context.getString(R.string.browser_log_error_detail, result.message ?: "", detail)
                                            epubLoadError = "${result.message}$detail"
                                        }
                                    }
                                }
                            }
                        ) { Text(stringResource(R.string.common_ok)) }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { if (!epubInProgress) { epubTarget = null; epubPassword = "" } }
                        ) { Text(stringResource(R.string.common_cancel)) }
                    }
                )
            } else {
                // encrypted == null 或 encrypted == false，都显示加载对话框
                AlertDialog(
                    onDismissRequest = {},
                    title = { Text(stringResource(R.string.browser_open_epub_title)) },
                    text = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 100.dp, max = 400.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            if (epubInProgress) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(modifier = Modifier.width(18.dp).height(18.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.browser_loading_dots), style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                epubLog,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            )
                        }
                    },
                    confirmButton = {
                        if (epubLoadError != null) {
                            TextButton(onClick = { epubTarget = null; epubLoadError = null; epubLog = "" }) {
                                Text(stringResource(R.string.common_close))
                            }
                        }
                    }
                )
            }
        }
        // ---- EPUB 错误对话框（独立于epubTarget） ----
        epubLoadError?.let { _ ->
            if (epubTarget == null) {
                AlertDialog(
                    onDismissRequest = { epubLoadError = null; epubLog = "" },
                    title = { Text(stringResource(R.string.browser_epub_parse_failed_title)) },
                    text = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 400.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                epubLog,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { epubLoadError = null; epubLog = "" }) { Text(stringResource(R.string.common_ok)) }
                    }
                )
            }
        }
        // ---- TXT 错误对话框 ----
        txtLoadError?.let { error ->
            AlertDialog(
                onDismissRequest = { txtLoadError = null; txtTarget = null },
                title = { Text(stringResource(R.string.browser_txt_parse_failed_title)) },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp)
                    ) {
                        Text(
                            error,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { txtLoadError = null; txtTarget = null }) { Text(stringResource(R.string.common_ok)) }
                }
            )
        }
        // ---- LLM 错误对话框 ----
        llmLoadError?.let { error ->
            AlertDialog(
                onDismissRequest = { llmLoadError = null; llmTarget = null },
                title = { Text(stringResource(R.string.browser_llm_parse_failed_title)) },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp)
                    ) {
                        Text(
                            error,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { llmLoadError = null; llmTarget = null }) { Text(stringResource(R.string.common_ok)) }
                }
            )
        }
        // ---- .pic.zip 密码输入对话框 ----
        picZipTarget?.let { target ->
            val encrypted = picZipEncrypted
            if (encrypted == true) {
                AlertDialog(
                    onDismissRequest = { if (!picZipInProgress) { picZipTarget = null; picZipPassword = "" } },
                    title = { Text(stringResource(R.string.browser_view_pic_zip_title)) },
                    text = {
                        Column {
                            Text(stringResource(R.string.browser_zip_encrypted_prompt, target.name), color = MaterialTheme.colorScheme.onSurface)
                            Spacer(Modifier.height(12.dp))
                            ReliablePasswordInputField(
                                value = picZipPassword,
                                onValueChange = { if (!picZipInProgress) picZipPassword = it },
                                label = { Text(stringResource(R.string.browser_zip_password_label)) },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !picZipInProgress
                            )
                            if (picZipInProgress) {
                                Spacer(Modifier.height(8.dp))
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (picZipInProgress || picZipPassword.isBlank()) return@Button
                                picZipInProgress = true
                                val pwd = picZipPassword.toCharArray()
                                scope.launch {
                                    var result: PicZipExtractResult? = null
                                    var cachedInitialIndex = 0
                                    withContext(Dispatchers.IO) {
                                        val cacheDir = getPicZipCacheDir(context, target.uri)
                                        val contentDir = java.io.File(cacheDir, "content")
                                        val listFile = java.io.File(cacheDir, ".image_list")
                                        if (contentDir.exists() && listFile.exists() && tryPicZipPassword(context, target.uri, pwd)) {
                                            val paths = listFile.readText().lineSequence().filter { it.isNotBlank() }.toList()
                                            val lastIndexFile = java.io.File(cacheDir, ".last_index")
                                            cachedInitialIndex = lastIndexFile.takeIf { it.exists() }?.readText()?.toIntOrNull()?.coerceIn(0, (paths.size - 1).coerceAtLeast(0)) ?: 0
                                            result = PicZipExtractResult(cacheDir, contentDir, paths, true)
                                        }
                                        if (result == null) {
                                            result = extractPicZipToCache(context, target.uri, pwd, target.name)
                                        }
                                    }
                                    val res = result
                                    if (res != null) {
                                        picZipTarget = null
                                        picZipPassword = ""
                                        picZipViewState = PicZipViewState(
                                            contentDir = res.contentDir,
                                            imagePaths = res.imagePaths,
                                            zipFileName = target.name,
                                            zipUri = target.uri,
                                            isEncrypted = true,
                                            password = pwd,
                                            initialIndex = cachedInitialIndex
                                        )
                                    } else {
                                        Toast.makeText(context, context.getString(R.string.browser_extract_failed_password), Toast.LENGTH_SHORT).show()
                                    }
                                    picZipInProgress = false
                                }
                            }
                        ) { Text(stringResource(R.string.common_ok)) }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { if (!picZipInProgress) { picZipTarget = null; picZipPassword = "" } }
                        ) { Text(stringResource(R.string.common_cancel)) }
                    }
                )
            } else if (encrypted == null) {
                AlertDialog(
                    onDismissRequest = {},
                    title = { Text(stringResource(R.string.browser_view_pic_zip_title)) },
                    text = {
                        Column {
                            Text(stringResource(R.string.browser_processing_file, target.name), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    },
                    confirmButton = {}
                )
            } else if (encrypted == false && picZipInProgress) {
                AlertDialog(
                    onDismissRequest = {},
                    title = { Text(stringResource(R.string.browser_view_pic_zip_title)) },
                    text = {
                        Column {
                            Text(stringResource(R.string.browser_extracting_file, target.name), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    },
                    confirmButton = {}
                )
            }
        }
        zipCompressTarget?.let { target ->
            val suggestedZipName = if (target.name.contains(".")) "${target.name.substringBeforeLast(".")}.zip" else "${target.name}.zip"
            val parentDirUri = Uri.parse(displayUri)
            val treeUri = rootUri?.let { Uri.parse(normalizeContentUriString(it)) }
            val zipCompressPasswordReady = isOptionalPasswordConfirmed(zipCompressPassword, zipCompressPasswordConfirm)
            AlertDialog(
                onDismissRequest = {
                    zipCompressTarget = null
                    zipCompressPassword = ""
                    zipCompressPasswordConfirm = ""
                },
                title = { Text(stringResource(R.string.context_compress_zip)) },
                text = {
                    Column {
                        Text(stringResource(R.string.browser_compress_zip_confirm, target.name, suggestedZipName), color = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.height(12.dp))
                        PasswordConfirmationFields(
                            password = zipCompressPassword,
                            confirmPassword = zipCompressPasswordConfirm,
                            onPasswordChange = {
                                zipCompressPassword = it
                                if (it.isEmpty()) zipCompressPasswordConfirm = ""
                            },
                            onConfirmPasswordChange = { zipCompressPasswordConfirm = it },
                            passwordLabel = context.getString(R.string.browser_password_optional_label),
                            confirmLabel = context.getString(R.string.browser_password_confirm_label),
                            enabled = true,
                            allowBlank = true
                        )
                    }
                },
                confirmButton = {
                    Button(
                        enabled = zipCompressPasswordReady,
                        onClick = {
                            scope.launch {
                                progressOp = OperationProgress(context.getString(R.string.browser_op_compress), 0, 1)
                                delay(50)
                                val pwd = zipCompressPassword.ifBlank { null }?.toCharArray()
                                val ok = withContext(Dispatchers.IO) {
                                    compressToZip(
                                        context,
                                        listOf(target.uri),
                                        parentDirUri,
                                        treeUri,
                                        suggestedZipName,
                                        pwd
                                    ) { cur, tot ->
                                        scope.launch(Dispatchers.Main.immediate) { progressOp = OperationProgress(context.getString(R.string.browser_op_compress), cur, tot) }
                                    }
                                }
                                delay(120)
                                progressOp = null
                                zipCompressTarget = null
                                zipCompressPassword = ""
                                zipCompressPasswordConfirm = ""
                                if (ok) {
                                    Toast.makeText(context, context.getString(R.string.browser_compress_complete), Toast.LENGTH_SHORT).show()
                                    refreshTrigger++
                                } else {
                                    Toast.makeText(context, context.getString(R.string.browser_compress_failed), Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    ) { Text(stringResource(R.string.browser_compress_action)) }
                },
                dismissButton = {
                    TextButton(onClick = {
                        zipCompressTarget = null
                        zipCompressPassword = ""
                        zipCompressPasswordConfirm = ""
                    }) { Text(stringResource(R.string.common_cancel)) }
                }
            )
        }
        sevenZCompressTarget?.let { target ->
            val suggested7zName = if (target.name.contains(".")) "${target.name.substringBeforeLast(".")}.7z" else "${target.name}.7z"
            val parentDirUri = Uri.parse(displayUri)
            val treeUri = rootUri?.let { Uri.parse(normalizeContentUriString(it)) }
            val sevenZCompressPasswordReady = isOptionalPasswordConfirmed(sevenZCompressPassword, sevenZCompressPasswordConfirm)
            AlertDialog(
                onDismissRequest = {
                    sevenZCompressTarget = null
                    sevenZCompressPassword = ""
                    sevenZCompressPasswordConfirm = ""
                },
                title = { Text(stringResource(R.string.context_compress_7z)) },
                text = {
                    Column {
                        Text(stringResource(R.string.browser_compress_7z_confirm, target.name, suggested7zName), color = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.height(12.dp))
                        PasswordConfirmationFields(
                            password = sevenZCompressPassword,
                            confirmPassword = sevenZCompressPasswordConfirm,
                            onPasswordChange = {
                                sevenZCompressPassword = it
                                if (it.isEmpty()) sevenZCompressPasswordConfirm = ""
                            },
                            onConfirmPasswordChange = { sevenZCompressPasswordConfirm = it },
                            passwordLabel = context.getString(R.string.browser_password_optional_label),
                            confirmLabel = context.getString(R.string.browser_password_confirm_label),
                            enabled = true,
                            allowBlank = true
                        )
                    }
                },
                confirmButton = {
                    Button(
                        enabled = sevenZCompressPasswordReady,
                        onClick = {
                            scope.launch {
                                progressOp = OperationProgress(context.getString(R.string.browser_op_compress), 0, 1)
                                delay(50)
                                val pwd = sevenZCompressPassword.ifBlank { null }?.toCharArray()
                                var timeout = false
                                val ok = withContext(Dispatchers.IO) {
                                    val executor = Executors.newSingleThreadExecutor()
                                    try {
                                        val future = executor.submit<Boolean> {
                                            compressTo7z(
                                                context,
                                                listOf(target.uri),
                                                parentDirUri,
                                                treeUri,
                                                suggested7zName,
                                                pwd
                                            ) { cur, tot ->
                                                scope.launch(Dispatchers.Main.immediate) { progressOp = OperationProgress(context.getString(R.string.browser_op_compress), cur, tot) }
                                            }
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
                                sevenZCompressTarget = null
                                sevenZCompressPassword = ""
                                sevenZCompressPasswordConfirm = ""
                                if (ok) {
                                    Toast.makeText(context, context.getString(R.string.browser_compress_complete), Toast.LENGTH_SHORT).show()
                                    refreshTrigger++
                                } else {
                                    Toast.makeText(context, if (timeout) context.getString(R.string.browser_compress_timeout) else context.getString(R.string.browser_compress_failed), Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    ) { Text(stringResource(R.string.browser_compress_action)) }
                },
                dismissButton = {
                    TextButton(onClick = {
                        sevenZCompressTarget = null
                        sevenZCompressPassword = ""
                        sevenZCompressPasswordConfirm = ""
                    }) { Text(stringResource(R.string.common_cancel)) }
                }
            )
        }
        if (showPendingCompressToZip && pendingList.isNotEmpty()) {
            val rootTreeUri = rootUri?.let { Uri.parse(normalizeContentUriString(it)) }
            val currentDirUri = Uri.parse(displayUri)
            val pendingZipPasswordReady = isOptionalPasswordConfirmed(pendingCompressPassword, pendingCompressPasswordConfirm)
            AlertDialog(
                onDismissRequest = { showPendingCompressToZip = false },
                title = { Text(stringResource(R.string.browser_compress_pending_zip_title)) },
                text = {
                    Column {
                        Text(
                            stringResource(R.string.browser_pending_compress_zip_desc, pendingList.size),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = pendingCompressZipName,
                            onValueChange = { pendingCompressZipName = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.browser_filename_label)) },
                            singleLine = true
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = pendingCompressPassword,
                            onValueChange = {
                                pendingCompressPassword = it
                                if (it.isEmpty()) pendingCompressPasswordConfirm = ""
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.browser_password_optional_label)) },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation()
                        )
                        if (pendingCompressPassword.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = pendingCompressPasswordConfirm,
                                onValueChange = { pendingCompressPasswordConfirm = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(stringResource(R.string.browser_password_confirm_label)) },
                                singleLine = true,
                                isError = pendingCompressPasswordConfirm.isNotEmpty() && pendingCompressPassword != pendingCompressPasswordConfirm,
                                visualTransformation = PasswordVisualTransformation()
                            )
                            if (pendingCompressPasswordConfirm.isNotEmpty() && pendingCompressPassword != pendingCompressPasswordConfirm) {
                                Spacer(Modifier.height(4.dp))
                                Text(stringResource(R.string.browser_password_mismatch), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        enabled = pendingCompressZipName.isNotBlank() && pendingZipPasswordReady,
                        onClick = {
                            val zipName = pendingCompressZipName.trim().let {
                                if (it.endsWith(".zip", ignoreCase = true)) it else "$it.zip"
                            }
                            val items = pendingList.toList()
                            val pwd = pendingCompressPassword.ifBlank { null }?.toCharArray()
                            showPendingCompressToZip = false
                            scope.launch {
                                progressOp = OperationProgress(context.getString(R.string.browser_op_compress), 0, items.size)
                                delay(50)
                                val ok = withContext(Dispatchers.IO) {
                                    compressToZip(
                                        context,
                                        items.map { it.uri },
                                        currentDirUri,
                                        rootTreeUri,
                                        zipName,
                                        pwd
                                    ) { cur, tot ->
                                        scope.launch(Dispatchers.Main.immediate) {
                                            progressOp = OperationProgress(context.getString(R.string.browser_op_compress), cur, tot)
                                        }
                                    }
                                }
                                delay(120)
                                progressOp = null
                                pendingCompressZipName = ""
                                pendingCompressPassword = ""
                                pendingCompressPasswordConfirm = ""
                                if (ok) {
                                    finishPendingListAndReturn()
                                    Toast.makeText(context, context.getString(R.string.browser_compress_done, zipName), Toast.LENGTH_SHORT).show()
                                    refreshTrigger++
                                } else {
                                    Toast.makeText(context, context.getString(R.string.browser_compress_failed), Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    ) { Text(stringResource(R.string.browser_compress_action)) }
                },
                dismissButton = {
                    TextButton(onClick = { showPendingCompressToZip = false }) { Text(stringResource(R.string.common_cancel)) }
                }
            )
        }
        if (showPendingCompressTo7z && pendingList.isNotEmpty()) {
            val rootTreeUri = rootUri?.let { Uri.parse(normalizeContentUriString(it)) }
            val currentDirUri = Uri.parse(displayUri)
            val pendingSevenZPasswordReady = isOptionalPasswordConfirmed(pendingCompress7zPassword, pendingCompress7zPasswordConfirm)
            AlertDialog(
                onDismissRequest = { showPendingCompressTo7z = false },
                title = { Text(stringResource(R.string.browser_compress_pending_7z_title)) },
                text = {
                    Column {
                        Text(
                            stringResource(R.string.browser_pending_compress_7z_desc, pendingList.size),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = pendingCompress7zName,
                            onValueChange = { pendingCompress7zName = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.browser_filename_label)) },
                            singleLine = true
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = pendingCompress7zPassword,
                            onValueChange = {
                                pendingCompress7zPassword = it
                                if (it.isEmpty()) pendingCompress7zPasswordConfirm = ""
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.browser_password_optional_label)) },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation()
                        )
                        if (pendingCompress7zPassword.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = pendingCompress7zPasswordConfirm,
                                onValueChange = { pendingCompress7zPasswordConfirm = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(stringResource(R.string.browser_password_confirm_label)) },
                                singleLine = true,
                                isError = pendingCompress7zPasswordConfirm.isNotEmpty() && pendingCompress7zPassword != pendingCompress7zPasswordConfirm,
                                visualTransformation = PasswordVisualTransformation()
                            )
                            if (pendingCompress7zPasswordConfirm.isNotEmpty() && pendingCompress7zPassword != pendingCompress7zPasswordConfirm) {
                                Spacer(Modifier.height(4.dp))
                                Text(stringResource(R.string.browser_password_mismatch), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        enabled = pendingCompress7zName.isNotBlank() && pendingSevenZPasswordReady,
                        onClick = {
                            val sevenZName = pendingCompress7zName.trim().let {
                                if (it.endsWith(".7z", ignoreCase = true)) it else "$it.7z"
                            }
                            val items = pendingList.toList()
                            val pwd = pendingCompress7zPassword.ifBlank { null }?.toCharArray()
                            showPendingCompressTo7z = false
                            scope.launch {
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
                                                pwd
                                            ) { cur, tot ->
                                                scope.launch(Dispatchers.Main.immediate) {
                                                    progressOp = OperationProgress(context.getString(R.string.browser_op_compress), cur, tot)
                                                }
                                            }
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
                                pendingCompress7zName = ""
                                pendingCompress7zPassword = ""
                                pendingCompress7zPasswordConfirm = ""
                                if (ok) {
                                    pendingList.clear()
                                    showPendingList = false
                                    Toast.makeText(context, context.getString(R.string.browser_compress_done, sevenZName), Toast.LENGTH_SHORT).show()
                                    refreshTrigger++
                                } else {
                                    Toast.makeText(context, if (timeout) context.getString(R.string.browser_compress_timeout) else context.getString(R.string.browser_compress_failed), Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    ) { Text(stringResource(R.string.browser_compress_action)) }
                },
                dismissButton = {
                    TextButton(onClick = { showPendingCompressTo7z = false }) { Text(stringResource(R.string.common_cancel)) }
                }
            )
        }
        if (showChangeRootConfirm && rootUri != null) {
            val r = rootUri!!
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
                                refreshTrigger++
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
        var gpgInProgress by remember { mutableStateOf(false) }
        gpgState?.let { op ->
            val ctx = context
            val dirUri = normalizeContentUriString(op.dirUri)
            val treeUri = rootUri?.let { Uri.parse(normalizeContentUriString(it)) }

            fun resetGpgUiState() {
                gpgState = null
                gpgPassword = ""
                gpgPasswordConfirm = ""
                gpgDecryptMode = null
                gpgDecryptAutoTried = false
                gpgEncryptSelectedKeyId = null
            }

            if (op.isDecrypt) {
                LaunchedEffect(op) {
                    gpgDecryptMode = detectGpgDecryptModeForOp(context, op)
                    gpgDecryptAutoTried = SecretKeyPasswordCache.get() == null
                }

                if (gpgDecryptMode == GpgDecryptUiMode.SECRET_KEY && !gpgDecryptAutoTried && SecretKeyPasswordCache.get() != null) {
                    LaunchedEffect(op, gpgDecryptMode) {
                        gpgDecryptAutoTried = true
                        gpgInProgress = true
                        val autoKeyPass = SecretKeyPasswordCache.get() ?: return@LaunchedEffect
                        val ok = when (op) {
                            is GpgOpState.BatchDecrypt -> {
                                var allOk = true
                                runWithProgress(context.getString(R.string.quick_crypto_action_decrypt_short), op.list.size) { setProgress ->
                                    op.list.forEachIndexed { index, fileModel ->
                                        if (!decryptGpgFileToDir(context, fileModel, dirUri, treeUri, symmetricPassword = null, keyPassphrase = autoKeyPass)) {
                                            allOk = false
                                        }
                                        setProgress(index + 1)
                                    }
                                }
                                allOk
                            }
                            is GpgOpState.Decrypt -> decryptGpgFileToDir(context, op.fileModel, dirUri, treeUri, symmetricPassword = null, keyPassphrase = autoKeyPass)
                            else -> false
                        }
                        gpgInProgress = false
                        if (ok) {
                            resetGpgUiState()
                            refreshTrigger++
                            if (op is GpgOpState.BatchDecrypt) {
                                pendingList.clear()
                                showPendingList = false
                                Toast.makeText(ctx, ctx.getString(R.string.browser_decrypted_count, op.list.size), Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(ctx, ctx.getString(R.string.browser_decrypt_complete), Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }

                val decryptMode = gpgDecryptMode
                if (decryptMode != null && (decryptMode != GpgDecryptUiMode.SECRET_KEY || gpgDecryptAutoTried)) {
                    GpgPasswordDialog(
                        isDecrypt = true,
                        fileName = op.displayName,
                        password = gpgPassword,
                        passwordLabel = when (decryptMode) {
                            GpgDecryptUiMode.SECRET_KEY -> context.getString(R.string.browser_key_password_label)
                            GpgDecryptUiMode.MIXED -> context.getString(R.string.browser_password_gpg_auto)
                            GpgDecryptUiMode.SYMMETRIC -> context.getString(R.string.browser_enter_password)
                        },
                        inProgress = gpgInProgress,
                        onPasswordChange = { if (!gpgInProgress) gpgPassword = it },
                        onConfirm = { pwd ->
                            if (gpgInProgress) return@GpgPasswordDialog
                            gpgInProgress = true
                            scope.launch {
                                try {
                                    val pwdChars = pwd.toCharArray()
                                    val ok = when (op) {
                                        is GpgOpState.BatchDecrypt -> {
                                            runWithProgress(context.getString(R.string.quick_crypto_action_decrypt_short), op.list.size) { setProgress ->
                                                op.list.forEachIndexed { index, fileModel ->
                                                    decryptGpgFileToDir(
                                                        context,
                                                        fileModel,
                                                        dirUri,
                                                        treeUri,
                                                        symmetricPassword = if (decryptMode == GpgDecryptUiMode.SECRET_KEY) null else pwdChars,
                                                        keyPassphrase = when (decryptMode) {
                                                            GpgDecryptUiMode.SYMMETRIC -> null
                                                            GpgDecryptUiMode.SECRET_KEY -> pwdChars
                                                            GpgDecryptUiMode.MIXED -> SecretKeyPasswordCache.get() ?: pwdChars
                                                        }
                                                    )
                                                    setProgress(index + 1)
                                                }
                                            }
                                            true
                                        }
                                        is GpgOpState.Decrypt -> decryptGpgFileToDir(
                                            context,
                                            op.fileModel,
                                            dirUri,
                                            treeUri,
                                            symmetricPassword = if (decryptMode == GpgDecryptUiMode.SECRET_KEY) null else pwdChars,
                                            keyPassphrase = when (decryptMode) {
                                                GpgDecryptUiMode.SYMMETRIC -> null
                                                GpgDecryptUiMode.SECRET_KEY -> pwdChars
                                                GpgDecryptUiMode.MIXED -> SecretKeyPasswordCache.get() ?: pwdChars
                                            }
                                        )
                                        else -> false
                                    }
                                    if (ok) {
                                        if (op is GpgOpState.BatchDecrypt) {
                                            pendingList.clear()
                                            showPendingList = false
                                            Toast.makeText(ctx, ctx.getString(R.string.browser_decrypted_count, op.list.size), Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(ctx, ctx.getString(R.string.browser_decrypt_complete), Toast.LENGTH_SHORT).show()
                                        }
                                        resetGpgUiState()
                                        refreshTrigger++
                                    } else {
                                        Toast.makeText(ctx, ctx.getString(R.string.browser_decrypt_failed), Toast.LENGTH_LONG).show()
                                    }
                                } finally {
                                    gpgInProgress = false
                                }
                            }
                        },
                        onDismiss = { if (!gpgInProgress) resetGpgUiState() }
                    )
                }
            } else {
                val pubRings = loadPublicKeyRings(context)
                val keys = listEncryptionPublicKeyRings(pubRings)
                GpgEncryptDialog(
                    fileName = op.displayName,
                    keys = keys,
                    selectedKeyId = gpgEncryptSelectedKeyId,
                    password = gpgPassword,
                    confirmPassword = gpgPasswordConfirm,
                    inProgress = gpgInProgress || gpgPubEncryptInProgress,
                    onSelectPassword = {
                        if (!gpgInProgress && !gpgPubEncryptInProgress) {
                            gpgEncryptSelectedKeyId = null
                            gpgPasswordConfirm = ""
                        }
                    },
                    onSelectKey = { keyId ->
                        if (!gpgInProgress && !gpgPubEncryptInProgress) {
                            gpgEncryptSelectedKeyId = keyId
                            gpgPasswordConfirm = ""
                        }
                    },
                    onPasswordChange = {
                        if (!gpgInProgress && !gpgPubEncryptInProgress) {
                            gpgPassword = it
                            gpgPasswordConfirm = ""
                            if (it.isNotEmpty()) gpgEncryptSelectedKeyId = null
                        }
                    },
                    onConfirmPasswordChange = {
                        if (!gpgInProgress && !gpgPubEncryptInProgress) {
                            gpgPasswordConfirm = it
                        }
                    },
                    onConfirm = { password, keyId ->
                        if (gpgInProgress || gpgPubEncryptInProgress) return@GpgEncryptDialog
                        if (keyId == null && password.isBlank()) return@GpgEncryptDialog
                        gpgInProgress = keyId == null
                        gpgPubEncryptInProgress = keyId != null
                        scope.launch {
                            try {
                                val pwdChars = password.toCharArray()
                                val ok = when (op) {
                                    is GpgOpState.BatchEncrypt -> {
                                        runWithProgress(if (keyId != null) context.getString(R.string.browser_op_public_key_encrypt) else context.getString(R.string.quick_crypto_action_encrypt_short), op.list.size) { setProgress ->
                                            op.list.forEachIndexed { index, fileModel ->
                                                encryptGpgFileToDir(
                                                    context,
                                                    fileModel,
                                                    dirUri,
                                                    treeUri,
                                                    symmetricPassword = if (keyId == null) pwdChars else null,
                                                    publicKeyId = keyId
                                                )
                                                setProgress(index + 1)
                                            }
                                        }
                                        true
                                    }
                                    is GpgOpState.Encrypt -> encryptGpgFileToDir(
                                        context,
                                        op.fileModel,
                                        dirUri,
                                        treeUri,
                                        symmetricPassword = if (keyId == null) pwdChars else null,
                                        publicKeyId = keyId
                                    )
                                    else -> false
                                }
                                if (ok) {
                                    if (op is GpgOpState.BatchEncrypt) {
                                        pendingList.clear()
                                        showPendingList = false
                                        Toast.makeText(ctx, if (keyId != null) ctx.getString(R.string.browser_public_key_encrypted_count, op.list.size) else ctx.getString(R.string.browser_encrypted_count, op.list.size), Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(ctx, if (keyId != null) ctx.getString(R.string.browser_op_public_key_encrypt) else ctx.getString(R.string.browser_encrypt_complete), Toast.LENGTH_SHORT).show()
                                    }
                                    resetGpgUiState()
                                    gpgPasswordConfirm = ""
                                    refreshTrigger++
                                } else {
                                    Toast.makeText(ctx, ctx.getString(R.string.browser_encrypt_failed), Toast.LENGTH_LONG).show()
                                }
                            } finally {
                                gpgInProgress = false
                                gpgPubEncryptInProgress = false
                            }
                        }
                    },
                    onDismiss = { if (!gpgInProgress && !gpgPubEncryptInProgress) resetGpgUiState() }
                )
            }
        }
    }

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
                Button(onClick = dialog.onDelete) { Text(stringResource(R.string.browser_delete_cache_and_exit)) }
            },
            dismissButton = {
                TextButton(onClick = dialog.onKeep) { Text(stringResource(R.string.browser_keep_cache_and_exit)) }
            }
        )
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
        rootUri == null && initialDirUri == null && showOverwriteConfirm == null -> {
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
        markdownViewFile != null -> {
            val (uri, name, isEncrypted) = markdownViewFile!!
            BackHandler { markdownViewFile = null }
            MarkdownViewerScreen(
                initialFileUri = uri,
                initialFileName = name,
                isEncrypted = isEncrypted,
                sessionCache = markdownViewerSessionCache,
                onBack = { markdownViewFile = null },
                onOpenFile = { openUri, openName, openEncrypted ->
                    when {
                        openName.endsWith(".md", ignoreCase = true) || openName.endsWith(".rst", ignoreCase = true) -> {
                            markdownViewFile = Triple(openUri, openName, openEncrypted)
                        }
                        isHtmlFile(openName) -> {
                            htmlViewState = HtmlViewState(
                                location = HtmlViewerLocation(
                                    initialUrl = openUri,
                                    title = openName,
                                    localFileUri = openUri
                                ),
                                recentKey = openUri,
                                recentUri = openUri,
                                recentTitle = openName
                            )
                            markdownViewFile = null
                        }
                        else -> {
                            viewingFile = Triple(openUri, openName, openEncrypted)
                            markdownViewFile = null
                        }
                    }
                }
            )
        }
        htmlViewState != null -> {
            val state = htmlViewState!!
            LaunchedEffect(state.recentKey) {
                recordRecentOpen(
                    type = RECENT_TYPE_HTML_VIEWER,
                    key = state.recentKey,
                    title = state.recentTitle,
                    uri = state.recentUri
                )
            }
            BackHandler { htmlViewState = null }
            HtmlViewerScreen(
                initialLocation = state.location,
                onBack = { htmlViewState = null }
            )
        }
        mdZipViewState != null -> {
            val state = mdZipViewState!!
            LaunchedEffect(state.zipUri) {
                recordRecentOpen(
                    type = RECENT_TYPE_ZIP_VIEWER,
                    key = state.zipUri.toString(),
                    title = state.zipFileName,
                    uri = state.zipUri.toString()
                )
            }
            BackHandler {
                if (state.isEncrypted) {
                    encryptedCacheExitDialog = EncryptedCacheExitDialogState(
                        title = context.getString(R.string.browser_exit_encrypted_md),
                        onDelete = {
                            cleanMdZipCache(context, state.zipUri)
                            mdZipViewState = null
                            encryptedCacheExitDialog = null
                        },
                        onKeep = {
                            mdZipViewState = null
                            encryptedCacheExitDialog = null
                        }
                    )
                } else {
                    mdZipViewState = null
                }
            }
            MdZipViewerScreen(
                initialTargetFile = state.targetFile,
                contentDir = state.contentDir,
                zipFileName = state.zipFileName,
                sessionCache = markdownViewerSessionCache,
                onBack = {
                    if (state.isEncrypted) {
                        encryptedCacheExitDialog = EncryptedCacheExitDialogState(
                            title = context.getString(R.string.browser_exit_encrypted_md),
                            onDelete = {
                                cleanMdZipCache(context, state.zipUri)
                                mdZipViewState = null
                                encryptedCacheExitDialog = null
                            },
                            onKeep = {
                                mdZipViewState = null
                                encryptedCacheExitDialog = null
                            }
                        )
                    } else {
                        mdZipViewState = null
                    }
                },
                logDebug = null
            )
        }
        htmlZipViewState != null -> {
            val state = htmlZipViewState!!
            LaunchedEffect(state.zipUri) {
                recordRecentOpen(
                    type = RECENT_TYPE_ZIP_VIEWER,
                    key = state.zipUri.toString(),
                    title = state.zipFileName,
                    uri = state.zipUri.toString()
                )
            }
            BackHandler {
                if (state.isEncrypted) {
                    encryptedCacheExitDialog = EncryptedCacheExitDialogState(
                        title = context.getString(R.string.browser_exit_encrypted_html),
                        onDelete = {
                            cleanHtmlZipCache(context, state.zipUri)
                            htmlZipViewState = null
                            encryptedCacheExitDialog = null
                        },
                        onKeep = {
                            htmlZipViewState = null
                            encryptedCacheExitDialog = null
                        }
                    )
                } else {
                    htmlZipViewState = null
                }
            }
            HtmlZipViewerScreen(
                initialIndexFile = state.indexFile,
                contentDir = state.contentDir,
                zipFileName = state.zipFileName,
                onBack = {
                    if (state.isEncrypted) {
                        encryptedCacheExitDialog = EncryptedCacheExitDialogState(
                            title = context.getString(R.string.browser_exit_encrypted_html),
                            onDelete = {
                                cleanHtmlZipCache(context, state.zipUri)
                                htmlZipViewState = null
                                encryptedCacheExitDialog = null
                            },
                            onKeep = {
                                htmlZipViewState = null
                                encryptedCacheExitDialog = null
                            }
                        )
                    } else {
                        htmlZipViewState = null
                    }
                },
                logDebug = null
            )
        }
        epubViewState != null -> {
            val state = epubViewState!!
            LaunchedEffect(state.epubUri) {
                recordRecentOpen(
                    type = RECENT_TYPE_EPUB_RENDERER,
                    key = state.epubUri.toString(),
                    title = state.zipFileName,
                    uri = state.epubUri.toString()
                )
            }
            BackHandler {
                if (state.isEncrypted) {
                    encryptedCacheExitDialog = EncryptedCacheExitDialogState(
                        title = context.getString(R.string.browser_exit_encrypted_book),
                        onDelete = {
                            cleanEpubCache(context, state.epubUri)
                            epubViewState = null
                            encryptedCacheExitDialog = null
                        },
                        onKeep = {
                            epubViewState = null
                            encryptedCacheExitDialog = null
                        }
                    )
                } else {
                    epubViewState = null
                }
            }
            EpubViewerScreen(
                extractResult = state.extractResult,
                zipFileName = state.zipFileName,
                epubUri = state.epubUri,
                bookNoteLoadedData = bookNoteController.data,
                bookNoteEntries = bookNoteController.entriesSnapshot,
                bookNoteInProgress = bookNoteController.inProgress,
                onRequestOpenBookNotes = bookNoteController.requestOpenWithCachedPassword,
                onBookNoteEntriesChanged = bookNoteController.updateEntries,
                onBack = {
                    if (state.isEncrypted) {
                        encryptedCacheExitDialog = EncryptedCacheExitDialogState(
                            title = context.getString(R.string.browser_exit_encrypted_book),
                            onDelete = {
                                cleanEpubCache(context, state.epubUri)
                                epubViewState = null
                                encryptedCacheExitDialog = null
                            },
                            onKeep = {
                                epubViewState = null
                                encryptedCacheExitDialog = null
                            }
                        )
                    } else {
                        epubViewState = null
                    }
                },
                logDebug = null
            )
        }
        picZipViewState != null -> {
            val state = picZipViewState!!
            LaunchedEffect(state.zipUri, state.recordRecent) {
                if (state.recordRecent) {
                    recordRecentOpen(
                        type = RECENT_TYPE_ZIP_VIEWER,
                        key = state.zipUri.toString(),
                        title = state.zipFileName,
                        uri = state.zipUri.toString()
                    )
                }
            }
            BackHandler {
                state.cleanupCacheDir?.deleteRecursively()
                picZipViewState = null
            }
            PicZipViewerScreen(
                contentDir = state.contentDir,
                imagePaths = state.imagePaths,
                zipFileName = state.zipFileName,
                isEncrypted = state.isEncrypted,
                password = state.password,
                initialIndex = state.initialIndex,
                onSaveCurrentImage = { sourceFile, fileName ->
                    savePicZipImageToRoot(sourceFile, fileName)
                },
                onBack = { doDelete ->
                    if (doDelete == true) cleanPicZipCache(context, state.zipUri)
                    state.cleanupCacheDir?.deleteRecursively()
                    picZipViewState = null
                }
            )
        }
        pdfViewState != null -> {
            val (pdfUri, pdfName) = pdfViewState!!
            LaunchedEffect(pdfUri) {
                recordRecentOpen(
                    type = RECENT_TYPE_PDF_VIEWER,
                    key = pdfUri,
                    title = pdfName,
                    uri = pdfUri
                )
            }
            BackHandler { pdfViewState = null }
            PdfViewerScreen(
                uri = pdfUri,
                fileName = pdfName,
                prefs = prefs,
                bookNoteLoadedData = bookNoteController.data,
                bookNoteEntries = bookNoteController.entriesSnapshot,
                bookNoteInProgress = bookNoteController.inProgress,
                onRequestOpenBookNotes = bookNoteController.requestOpenWithCachedPassword,
                onBookNoteEntriesChanged = bookNoteController.updateEntries,
                onBack = { pdfViewState = null }
            )
        }
        passContentView != null -> {
            val content = passContentView!!
            BackHandler { passContentView = null }
            PassContentViewerScreen(
                innerFileName = content.innerFileName,
                decryptedBytes = content.decryptedBytes,
                onBack = { passContentView = null }
            )
        }
        passEditState != null -> {
            val state = passEditState!!
            BackHandler { passEditState = null }
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
                        passEditState = null
                        refreshTrigger++
                        Toast.makeText(ctx, if (ok) ctx.getString(R.string.browser_saved_and_reencrypted) else ctx.getString(R.string.viewer_save_failed), Toast.LENGTH_SHORT).show()
                    }
                },
                onBack = { passEditState = null }
            )
        }
        viewingFile != null -> {
            val (uri, name, isEncrypted) = viewingFile!!
            BackHandler { viewingFile = null }
            ViewerScreen(
                fileUri = uri,
                fileName = name,
                isEncrypted = isEncrypted,
                onBack = { viewingFile = null },
                onOpenMarkdownView = if (name.endsWith(".md", ignoreCase = true) || name.endsWith(".rst", ignoreCase = true)) {
                    { markdownViewFile = viewingFile; viewingFile = null }
                } else null
            )
        }
        else -> MainTabsRootContent()
    }
    showOverwriteConfirm?.let { (sourceUriStr, fileName) ->
        val targetRoot = rootUri?.let { normalizeContentUriString(it) } ?: return@let
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
                            currentUri = dir
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

@Composable
private fun OperationProgressDialog(progress: OperationProgress) {
    Dialog(onDismissRequest = { }) {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, tonalElevation = 6.dp) {
            Column(Modifier.padding(24.dp)) {
                Text(stringResource(R.string.browser_progress_in_progress, progress.label), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(16.dp))
                if (progress.total != null && progress.total > 0) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), progress = { progress.current.toFloat() / progress.total })
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.browser_progress_items, progress.current, progress.total ?: 0), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.browser_processing), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** 密码保护解密后的内容，暂存于内存中供渲染。 */
private data class PassDecryptedContent(val innerFileName: String, val decryptedBytes: ByteArray)

/** 判断文件名是否为压缩 Markdown/RST 文件（.md.zip 或 .rst.zip）。 */
private fun isCompressedMarkdown(name: String): Boolean =
    name.endsWith(".md.zip", ignoreCase = true) || name.endsWith(".rst.zip", ignoreCase = true)

/** .md.zip / .rst.zip 查看器状态。 */
private data class MdZipViewState(
    val targetFile: java.io.File?,  // null 表示未找到可渲染文件，此时显示目录内容
    val contentDir: java.io.File,
    val zipFileName: String,
    val zipUri: Uri,
    val isEncrypted: Boolean
)

/** 判断文件名是否为压缩 HTML 文件（.html.zip）。 */
private fun isCompressedHtml(name: String): Boolean =
    name.endsWith(".html.zip", ignoreCase = true)

/** 判断文件名是否为压缩 LLM 包（.llm.zip）。 */
private fun isCompressedLlmZip(name: String): Boolean =
    name.endsWith(".llm.zip", ignoreCase = true)

/** 判断文件名是否为 .pic.zip 图片压缩包。 */
private fun isPicZip(name: String): Boolean =
    name.endsWith(".pic.zip", ignoreCase = true)

/** 判断文件名是否为可解压压缩包（.zip / .rar）。 */
private fun isExtractableArchive(name: String): Boolean =
    name.endsWith(".zip", ignoreCase = true) ||
        name.endsWith(".rar", ignoreCase = true) ||
        name.endsWith(".7z", ignoreCase = true)

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

/** 判断文件名是否为图片文件（目录图库查看用；不含 SVG）。 */
private fun isGalleryImageFile(name: String): Boolean =
    name.endsWith(".jpg", ignoreCase = true) ||
        name.endsWith(".jpeg", ignoreCase = true) ||
        name.endsWith(".png", ignoreCase = true) ||
        name.endsWith(".gif", ignoreCase = true) ||
        name.endsWith(".webp", ignoreCase = true) ||
        name.endsWith(".bmp", ignoreCase = true)

/** 判断文件名是否为 HTML 文件。 */
private fun isHtmlFile(name: String): Boolean =
    name.endsWith(".html", ignoreCase = true) || name.endsWith(".htm", ignoreCase = true)

/** .html.zip 查看器状态。 */
private data class HtmlZipViewState(
    val indexFile: java.io.File?,
    val contentDir: java.io.File,
    val zipFileName: String,
    val zipUri: Uri,
    val isEncrypted: Boolean
)

/** 通用 HTML 查看器状态：支持本地 HTML 文件或远程 URL。 */
private data class HtmlViewState(
    val location: HtmlViewerLocation,
    val recentKey: String,
    val recentUri: String,
    val recentTitle: String
)

/** EPUB 查看器状态。 */
private data class EpubViewState(
    val extractResult: EpubExtractResult,
    val zipFileName: String,
    val epubUri: Uri,
    val isEncrypted: Boolean
)

/** .pic.zip 查看器状态。 */
private data class PicZipViewState(
    val contentDir: java.io.File,
    val imagePaths: List<String>,
    val zipFileName: String,
    val zipUri: Uri,
    val isEncrypted: Boolean,
    val password: CharArray? = null,
    val initialIndex: Int = 0,
    val recordRecent: Boolean = true,
    val cleanupCacheDir: File? = null
)

private data class EncryptedCacheExitDialogState(
    val title: String,
    val onDelete: () -> Unit,
    val onKeep: () -> Unit
)

/** 直接编辑 .pass 时的状态：解密后的文件信息，用于编辑界面和存盘时重加密。 */
private data class PassEditState(
    val fileModel: DocumentFileModel,
    val dirUri: String,
    val treeUri: Uri?,
    val innerName: String,
    val decryptedBytes: ByteArray
) {
    override fun equals(other: Any?) = (other is PassEditState) && fileModel == other.fileModel && dirUri == other.dirUri && innerName == other.innerName && decryptedBytes.contentEquals(other.decryptedBytes)
    override fun hashCode() = 31 * (31 * fileModel.hashCode() + dirUri.hashCode()) + innerName.hashCode()
}

private sealed class GpgOpState {
    abstract val isDecrypt: Boolean
    abstract val dirUri: String
    abstract val displayName: String
    data class Decrypt(val fileModel: DocumentFileModel, override val dirUri: String) : GpgOpState() {
        override val isDecrypt = true
        override val displayName get() = fileModel.name
    }
    data class Encrypt(val fileModel: DocumentFileModel, override val dirUri: String) : GpgOpState() {
        override val isDecrypt = false
        override val displayName get() = fileModel.name
    }
    data class BatchDecrypt(val list: List<DocumentFileModel>, override val dirUri: String) : GpgOpState() {
        override val isDecrypt = true
        override val displayName get() = list.size.toString()
    }
    data class BatchEncrypt(val list: List<DocumentFileModel>, override val dirUri: String) : GpgOpState() {
        override val isDecrypt = false
        override val displayName get() = list.size.toString()
    }
}

private enum class GpgDecryptUiMode {
    SYMMETRIC,
    SECRET_KEY,
    MIXED
}

/** 加密/解密方式：对称(密码)、公钥加密、私钥解密 */
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
    onOpenWithOtherApp: (uri: String, name: String, packageName: String?, rememberChoice: Boolean) -> Boolean = { _, _, _, _ -> false },
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
    onRequestQuickObfuscate: ((DocumentFileModel) -> Unit)? = null,
    onRequestQuickDeobfuscate: ((DocumentFileModel) -> Unit)? = null,
    onConfirmDelete: ((DocumentFileModel, Boolean) -> Unit)? = null,
    onUnzipRequest: (DocumentFileModel) -> Unit = {},
    onRequestMdZipView: (DocumentFileModel) -> Unit = {},
    onRequestHtmlView: (DocumentFileModel) -> Unit = {},
    onRequestHtmlZipView: (DocumentFileModel) -> Unit = {},
    onRequestOpenUrl: (String) -> Unit = {},
    onRequestLlmZipView: (DocumentFileModel) -> Unit = {},
    onRequestEpubView: (DocumentFileModel) -> Unit = {},
    onRequestPicZipView: (DocumentFileModel) -> Unit = {},
    onRequestDirectoryImageView: (DocumentFileModel, String, List<DocumentFileModel>) -> Unit = { _, _, _ -> },
    onRequestPdfView: (DocumentFileModel) -> Unit = {},
    onCompressToZipRequest: (DocumentFileModel) -> Unit = {},
    onCompressTo7zRequest: (DocumentFileModel) -> Unit = {},
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
    val context = LocalContext.current
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
        val options = queryExternalOpenTargets(context, target.uri.toString())
        if (options.isEmpty()) {
            Toast.makeText(context, context.getString(R.string.browser_no_app_to_open), Toast.LENGTH_SHORT).show()
            return
        }
        externalOpenTarget = target
        externalOpenOptions = options
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
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
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
                                onClick = {
                                    if (item.isDirectory) {
                                        onNavigate(item.uri.toString())
                                    } else {
                                        when {
                                            isCompressedMarkdown(item.name) ->
                                                onRequestMdZipView(item)
                                            isHtmlFile(item.name) ->
                                                onRequestHtmlView(item)
                                            isCompressedHtml(item.name) ->
                                                onRequestHtmlZipView(item)
                                            isCompressedLlmZip(item.name) ->
                                                onRequestLlmZipView(item)
                                            isEpubFile(item.name) ->
                                                onRequestEpubView(item)
                                            isTxtFile(item.name) ->
                                                onRequestTxtView(item)
                                            isLlmFile(item.name) ->
                                                onRequestLlmView(item)
                                            isGalleryImageFile(item.name) ->
                                                onRequestDirectoryImageView(item, currentUri, galleryImageItems)
                                            isPicZip(item.name) ->
                                                onRequestPicZipView(item)
                                            isPdfFile(item.name) ->
                                                onRequestPdfView(item)
                                            isExtractableArchive(item.name) ->
                                                onUnzipRequest(item)
                                            item.name.endsWith(".gpg", ignoreCase = true) ->
                                                onRequestGpgDecrypt(item, currentUri)
                                            item.name.endsWith(".pass", ignoreCase = true) ->
                                                onRequestPassView(item)
                                            item.name.endsWith(".md", ignoreCase = true) || item.name.endsWith(".rst", ignoreCase = true) ->
                                                onOpenMarkdownView(item.uri.toString(), item.name, false)
                                            else -> {
                                                val preferredPackage = fileExtensionKey(item.name)?.let { preferredExternalPackages[it] }
                                                if (preferredPackage != null && onOpenWithOtherApp(item.uri.toString(), item.name, preferredPackage, false)) {
                                                    Unit
                                                } else {
                                                    val encrypted = item.name.endsWith(".gpg", ignoreCase = true)
                                                    onOpenFile(item.uri.toString(), item.name, encrypted)
                                                }
                                            }
                                        }
                                    }
                                },
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
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(16.dp))
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
                                ) { Text(context.getString(R.string.context_view_images), color = MaterialTheme.colorScheme.onSurface) }
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
                        if (isQuickObfuscatedFileName(menuTarget.name)) {
                            onRequestQuickDeobfuscate?.let { fn ->
                                TextButton(
                                    onClick = {
                                        showContextMenu = false
                                        fn(menuTarget)
                                        contextMenuTarget = null
                                    }
                                ) { Text(context.getString(R.string.context_quick_deobfuscate), color = MaterialTheme.colorScheme.onSurface) }
                            }
                        } else {
                            onRequestQuickObfuscate?.let { fn ->
                                TextButton(
                                    onClick = {
                                        showContextMenu = false
                                        fn(menuTarget)
                                        contextMenuTarget = null
                                    }
                                ) { Text(context.getString(R.string.context_quick_obfuscate), color = MaterialTheme.colorScheme.onSurface) }
                            }
                        }
                    }
                    TextButton(
                        onClick = {
                            showContextMenu = false
                            onCompressToZipRequest(menuTarget)
                            contextMenuTarget = null
                        }
                    ) { Text(context.getString(R.string.context_compress_zip), color = MaterialTheme.colorScheme.onSurface) }
                    TextButton(
                        onClick = {
                            showContextMenu = false
                            onCompressTo7zRequest(menuTarget)
                            contextMenuTarget = null
                        }
                    ) { Text(context.getString(R.string.context_compress_7z), color = MaterialTheme.colorScheme.onSurface) }
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
        Dialog(onDismissRequest = { externalOpenTarget = null; externalOpenOptions = emptyList() }) {
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
                    externalOpenOptions.forEach { option ->
                        TextButton(
                            onClick = {
                                val opened = onOpenWithOtherApp(target.uri.toString(), target.name, option.packageName, true)
                                if (opened) {
                                    externalOpenTarget = null
                                    externalOpenOptions = emptyList()
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
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { externalOpenTarget = null; externalOpenOptions = emptyList() }) {
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
            title = { Text(stringResource(R.string.browser_archive_operation, target.name)) },
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
        val detailScope = rememberCoroutineScope()

        LaunchedEffect(detailTarget.uri) {
            if (isArchiveFile) {
                zipDetailResult = withContext(Dispatchers.IO) {
                    getArchiveFirstLevelEntries(context, detailTarget.uri, detailTarget.name, null)
                }
            }
        }

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
                            Text(fullPath, style = MaterialTheme.typography.bodyMedium)
                        }
                        IconButton(onClick = {
                            clipboardManager?.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.browser_full_path), fullPath))
                            Toast.makeText(context, context.getString(R.string.browser_path_copied), Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.browser_copy_path), Modifier.size(20.dp))
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.browser_rename_title), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = detailRenameValue,
                            onValueChange = { detailRenameValue = it },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(detailRenameFocus),
                            textStyle = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.width(8.dp))
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

                    if (isArchiveFile) {
                        Spacer(Modifier.height(16.dp))
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

                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showFileDetail = null }) { Text(stringResource(R.string.common_close)) }
                    }
                }
            }
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
    val icon = when {
        model.isDirectory -> Icons.Default.Folder
        model.name.endsWith(".gpg", ignoreCase = true) -> Icons.Default.Lock
        model.name.endsWith(".pass", ignoreCase = true) -> Icons.Default.Lock
        model.name.endsWith(".qx", ignoreCase = true) -> Icons.Default.LockOpen
        isCompressedMarkdown(model.name) -> Icons.AutoMirrored.Filled.Article
        isCompressedHtml(model.name) -> Icons.AutoMirrored.Filled.Article
        isEpubFile(model.name) || isTxtFile(model.name) || isLlmFile(model.name) -> Icons.AutoMirrored.Filled.MenuBook
        isPdfFile(model.name) -> Icons.Default.PictureAsPdf
        isPicZip(model.name) -> Icons.Default.Archive
        isExtractableArchive(model.name) -> Icons.Default.Archive
        model.name.endsWith(".md", ignoreCase = true) || model.name.endsWith(".rst", ignoreCase = true) -> Icons.Default.Description
        else -> Icons.AutoMirrored.Filled.InsertDriveFile
    }
    val iconTint = when {
        model.isDirectory -> MaterialTheme.colorScheme.primary
        model.name.endsWith(".gpg", ignoreCase = true) -> Color.Red
        model.name.endsWith(".pass", ignoreCase = true) -> Color.Red
        model.name.endsWith(".qx", ignoreCase = true) -> Color.Red
        isEpubFile(model.name) -> Color(0xFF8B4513)  // 棕色，适合书籍
        isPdfFile(model.name) -> Color(0xFFD32F2F)  // 红色，PDF 标志色
        isPicZip(model.name) -> MaterialTheme.colorScheme.tertiary
        isExtractableArchive(model.name) -> MaterialTheme.colorScheme.tertiary
        model.name.endsWith(".md", ignoreCase = true) || model.name.endsWith(".rst", ignoreCase = true) -> Color.Blue
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
            BadgedBox(
                badge = {
                    if (hasPreferredExternalApp) {
                        Badge(containerColor = MaterialTheme.colorScheme.secondary)
                    }
                }
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    Modifier.size(32.dp),
                    tint = iconTint
                )
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
fun PendingListScreen(
    pendingList: List<DocumentFileModel>,
    currentDirPath: String = "",
    rootUri: String? = null,
    mode: PendingListMode = PendingListMode.NORMAL,
    title: String? = null,
    confirmLabel: String? = null,
    onConfirm: (() -> Unit)? = null,
    onRemove: (DocumentFileModel) -> Unit,
    onCopyHere: () -> Unit = {},
    onMoveHere: () -> Unit = {},
    onRequestDelete: () -> Unit = {},
    onRequestBatchObfuscate: () -> Unit = {},
    onRequestBatchDeobfuscate: () -> Unit = {},
    onRequestBatchGpgEncrypt: () -> Unit = {},
    onRequestBatchGpgDecrypt: () -> Unit = {},
    onRequestCompressToZip: () -> Unit = {},
    onRequestCompressTo7z: () -> Unit = {},
    onRequestForwardToBulletin: () -> Unit = {},
    onClearFilteredList: (List<DocumentFileModel>) -> Unit = {},
    onAddToPlayback: (List<DocumentFileModel>) -> Unit = {},
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isPickMode = mode == PendingListMode.DIRECTORY_PICK
    var filterText by remember { mutableStateOf("") }
    val filteredPendingItems = if (filterText.isBlank()) pendingList
    else runCatching { Regex(filterText) }.getOrNull()?.let { regex ->
        pendingList.filter { regex.containsMatchIn(it.name) }
    } ?: pendingList
    val screenTitle = title ?: if (isPickMode) {
        context.getString(R.string.directory_pick_list_title, pendingList.size)
    } else {
        stringResource(R.string.browser_pending_list_title, pendingList.size)
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(screenTitle) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        floatingActionButton = {}
    ) { padding ->
        if (pendingList.isEmpty()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(R.string.browser_list_empty), style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            Column(Modifier.fillMaxSize().padding(padding)) {
                if (!isPickMode) {
                Text(
                    stringResource(R.string.browser_current_dir, currentDirPath),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
                }
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
                        placeholder = { Text(stringResource(R.string.browser_filename_regex_hint)) },
                        label = null
                    )
                    if (filterText.isNotEmpty()) {
                        IconButton(onClick = { filterText = "" }) {
                            Icon(Icons.Default.RemoveCircle, contentDescription = stringResource(R.string.pending_list_action_clear_filter), Modifier.size(20.dp))
                        }
                    }
                }
                if (!isPickMode) {
                val audioFromFiltered = filteredPendingItems.filter { !it.isDirectory && isMusicFileName(it.name) }
                val pendingActions = listOf(
                    context.getString(R.string.pending_list_action_copy) to onCopyHere,
                    context.getString(R.string.pending_list_action_move) to onMoveHere,
                    context.getString(R.string.pending_list_action_delete) to onRequestDelete,
                    context.getString(R.string.pending_list_action_clear_filter) to {
                        onClearFilteredList(filteredPendingItems)
                    },
                    context.getString(R.string.pending_list_action_playback) to {
                        if (audioFromFiltered.isEmpty()) {
                            Toast.makeText(context, context.getString(R.string.browser_no_audio_in_list), Toast.LENGTH_SHORT).show()
                        } else {
                            onAddToPlayback(audioFromFiltered)
                        }
                    },
                    context.getString(R.string.pending_list_action_obfuscate) to onRequestBatchObfuscate,
                    context.getString(R.string.pending_list_action_deobfuscate) to onRequestBatchDeobfuscate,
                    context.getString(R.string.pending_list_action_gpg_encrypt) to onRequestBatchGpgEncrypt,
                    context.getString(R.string.pending_list_action_gpg_decrypt) to onRequestBatchGpgDecrypt,
                    context.getString(R.string.pending_list_action_zip) to onRequestCompressToZip,
                    context.getString(R.string.pending_list_action_7z) to onRequestCompressTo7z,
                    context.getString(R.string.pending_forward_to_bulletin) to onRequestForwardToBulletin,
                )
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    pendingActions.chunked(5).forEach { rowActions ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            rowActions.forEach { (label, action) ->
                                TextButton(
                                    onClick = action,
                                    shape = RectangleShape,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                    colors = ButtonDefaults.textButtonColors(),
                                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .defaultMinSize(minWidth = 0.dp, minHeight = 30.dp)
                                ) {
                                    Text(
                                        label,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            repeat(5 - rowActions.size) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
                }
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(filteredPendingItems) { item ->
                    val relativePath = remember(item.uri, rootUri) {
                        pathFromRoot(context, rootUri, normalizeContentUriString(item.uri.toString()))
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (item.isDirectory) Icons.Default.Folder else Icons.AutoMirrored.Filled.InsertDriveFile,
                            contentDescription = null,
                            Modifier.size(24.dp),
                            tint = if (item.isDirectory) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.size(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                item.name,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                relativePath,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = { onRemove(item) }) {
                            Icon(
                                Icons.Default.RemoveCircle,
                                contentDescription = stringResource(R.string.browser_remove_from_list),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
            if (isPickMode && onConfirm != null) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(context.getString(R.string.directory_pick_cancel))
                    }
                    Button(
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f),
                        enabled = pendingList.isNotEmpty()
                    ) {
                        Text(confirmLabel ?: context.getString(R.string.directory_pick_confirm_generic))
                    }
                }
            }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaybackScreen(
    prefs: Preferences,
    playbackState: PlaybackState?,
    onStopPlayback: () -> Unit,
    onPlayPrev: () -> Unit,
    onPlayNext: () -> Unit,
    onPlayPause: () -> Unit,
    onSeek: (positionMs: Int) -> Unit = {},
    showBackButton: Boolean = true,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var playlists by remember { mutableStateOf<List<Playlist>>(emptyList()) }
    var lastPlaylistId by remember { mutableStateOf<String?>(null) }
    var playerBookmarks by remember { mutableStateOf<List<PlayerListBookmark>>(emptyList()) }
    var selectedPlaylistId by remember { mutableStateOf<String?>(null) }
    var showRenamePlaylistDialog by remember { mutableStateOf(false) }
    var playlistRenameTarget by remember { mutableStateOf<Playlist?>(null) }
    var playlistRenameText by remember { mutableStateOf("") }
    var showSortPlaylistDialog by remember { mutableStateOf(false) }
    var playlistSortTarget by remember { mutableStateOf<Playlist?>(null) }
    var playlistSortField by remember { mutableStateOf(PlaylistTrackSortField.ARTIST) }
    var playlistSortOrder by remember { mutableStateOf(PlaylistSortOrder.ASCENDING) }
    var playlistSortInProgress by remember { mutableStateOf(false) }
    var showAddBookmarkDialog by remember { mutableStateOf(false) }
    var showBookmarkManagerDialog by remember { mutableStateOf(false) }
    var bookmarkNoteInput by remember { mutableStateOf("") }
    var bookmarkPendingPlaylistId by remember { mutableStateOf<String?>(null) }
    var bookmarkPendingDirUri by remember { mutableStateOf<String?>(null) }
    var bookmarkPendingTrackIndex by remember { mutableIntStateOf(0) }
    var bookmarkPendingPositionMs by remember { mutableLongStateOf(0L) }
    var bookmarkPendingTrackName by remember { mutableStateOf("") }
    var editingBookmarkId by remember { mutableStateOf<String?>(null) }
    var editingBookmarkNote by remember { mutableStateOf("") }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var trackActionMenuIndex by remember { mutableStateOf<Int?>(null) }
    var playlistActionMenuIndex by remember { mutableStateOf<Int?>(null) }
    var pendingTrackTransfer by remember { mutableStateOf<PlaylistTrackTransfer?>(null) }
    var playerAudioSettings by remember { mutableStateOf(PlayerAudioSettings()) }
    var showPlayerAudioSettingsDialog by remember { mutableStateOf(false) }
    var showPlaybackDiagnostics by remember { mutableStateOf(false) }
    var showPreviewSettingsDialog by remember { mutableStateOf(false) }
    var previewSettingsTarget by remember { mutableStateOf<Playlist?>(null) }
    var previewEnabledInput by remember { mutableStateOf(false) }
    var previewStartInput by remember { mutableStateOf("") }
    var previewMaxDurationInput by remember { mutableStateOf("") }
    var previewTimeError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(prefs) {
        prefs.playlists.collect { playlists = it }
    }
    LaunchedEffect(prefs) {
        prefs.playerLastPlaylistId.collect { lastPlaylistId = it }
    }
    LaunchedEffect(prefs) {
        prefs.playerListBookmarks.collect { playerBookmarks = it }
    }
    LaunchedEffect(prefs) {
        prefs.playerAudioSettings.collect { playerAudioSettings = it }
    }
    val playlistById = remember(playlists) { playlists.associateBy { it.id } }
    val selectedPlaylist = selectedPlaylistId?.let { id -> playlists.find { it.id == id } }
    if (selectedPlaylist == null && selectedPlaylistId != null) selectedPlaylistId = null

    BackHandler(enabled = selectedPlaylistId != null) {
        selectedPlaylistId = null
    }

    fun openRenamePlaylistDialog(pl: Playlist) {
        playlistRenameTarget = pl
        playlistRenameText = pl.displayName(context.getString(R.string.player_unnamed_playlist))
        showRenamePlaylistDialog = true
    }

    fun openSortPlaylistDialog(pl: Playlist) {
        playlistSortTarget = pl
        playlistSortField = PlaylistTrackSortField.ARTIST
        playlistSortOrder = PlaylistSortOrder.ASCENDING
        showSortPlaylistDialog = true
    }

    fun openPreviewSettingsDialog(pl: Playlist) {
        previewSettingsTarget = pl
        previewEnabledInput = pl.previewEnabled
        previewStartInput = if (pl.previewStartMs > 0L) PlaybackTimeFormat.formatMs(pl.previewStartMs) else ""
        previewMaxDurationInput = if (pl.previewMaxDurationMs > 0L) PlaybackTimeFormat.formatMs(pl.previewMaxDurationMs) else ""
        previewTimeError = null
        showPreviewSettingsDialog = true
    }

    fun previewListHint(pl: Playlist): String {
        val start = PlaybackTimeFormat.formatMs(pl.previewStartMs)
        return if (pl.previewMaxDurationMs > 0L) {
            context.getString(
                R.string.player_preview_list_hint,
                start,
                PlaybackTimeFormat.formatMs(pl.previewMaxDurationMs)
            )
        } else {
            context.getString(R.string.player_preview_list_hint_no_limit, start)
        }
    }

    fun recordPlayedPlaylist(pl: Playlist) {
        scope.launch {
            prefs.addRecentOpenItem(
                type = RECENT_TYPE_PLAYLIST,
                key = pl.id,
                title = pl.displayName(context.getString(R.string.player_unnamed_playlist)),
                playlistId = pl.id
            )
        }
    }

    fun startPlaylist(pl: Playlist) {
        recordPlayedPlaylist(pl)
        val intent = Intent(context, PlaybackService::class.java).apply {
            action = ACTION_PLAY
            putExtra(EXTRA_PLAYLIST_ID, pl.id)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
    }

    fun startPlaylistFromIndex(pl: Playlist, index: Int, startPositionMs: Int = 0) {
        recordPlayedPlaylist(pl)
        val intent = Intent(context, PlaybackService::class.java).apply {
            action = ACTION_PLAY
            putExtra(EXTRA_PLAYLIST_ID, pl.id)
            putExtra(EXTRA_START_INDEX, index)
            putExtra(EXTRA_START_POSITION_MS, startPositionMs.coerceAtLeast(0))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
    }

    fun resumeLastPlayback(playlist: Playlist?) {
        if (playlist != null) {
            startPlaylist(playlist)
            return
        }
        val intent = Intent(context, PlaybackService::class.java).apply {
            action = ACTION_RESUME
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
    }

    suspend fun createEmptyPlaylist(name: String) {
        val fallbackName = context.getString(
            R.string.player_playlist_default_name,
            java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
        )
        val playlist = Playlist(
            id = java.util.UUID.randomUUID().toString(),
            name = name.trim().ifBlank { fallbackName },
            uris = emptyList(),
            names = emptyList()
        )
        prefs.addPlaylist(playlist)
        selectedPlaylistId = playlist.id
        showCreatePlaylistDialog = false
        newPlaylistName = ""
        Toast.makeText(context, context.getString(R.string.browser_empty_playlist_created), Toast.LENGTH_SHORT).show()
    }

    suspend fun removeTrackEntryAfterMove(source: Playlist, trackIndex: Int) {
        removePlaylistTrackAt(
            context = context,
            prefs = prefs,
            pl = source,
            trackIndex = trackIndex,
            playbackState = playbackState,
            deleteSourceFile = false,
            onStopPlayback = onStopPlayback
        )
    }

    suspend fun transferTrackToPlaylist(transfer: PlaylistTrackTransfer, target: Playlist) {
        if (target.isDirectorySource) {
            Toast.makeText(context, context.getString(R.string.browser_playlist_dir_no_move_target), Toast.LENGTH_SHORT).show()
            return
        }
        if (target.id == transfer.source.id) return
        val trackUri = transfer.source.uris.getOrNull(transfer.trackIndex) ?: return
        val trackName = transfer.source.names.getOrElse(transfer.trackIndex) { trackUri.substringAfterLast('/') }
        val result = prefs.appendTracksToPlaylist(target.id, listOf(trackUri), listOf(trackName))
        if (!result.found) {
            Toast.makeText(context, context.getString(R.string.browser_playlist_target_missing), Toast.LENGTH_SHORT).show()
            return
        }
        if (transfer.move) {
            removeTrackEntryAfterMove(transfer.source, transfer.trackIndex)
        }
        pendingTrackTransfer = null
        val action = if (transfer.move) context.getString(R.string.pending_list_action_move) else context.getString(R.string.pending_list_action_copy)
        val duplicateHint = if (result.skippedCount > 0) context.getString(R.string.browser_playlist_duplicate_hint) else ""
        Toast.makeText(context, context.getString(R.string.browser_playlist_action_result, action, target.name, duplicateHint), Toast.LENGTH_SHORT).show()
    }

    fun promptSaveCurrentBookmark() {
        val state = playbackState
        if (state?.playlistId == null) {
            Toast.makeText(context, context.getString(R.string.player_bookmark_playlist_only), Toast.LENGTH_SHORT).show()
            return
        }
        bookmarkPendingPlaylistId = state.playlistId
        bookmarkPendingDirUri = null
        bookmarkPendingTrackIndex = state.trackIndex
        bookmarkPendingPositionMs = state.positionMs.toLong()
        bookmarkPendingTrackName = state.trackName
        bookmarkNoteInput = ""
        showAddBookmarkDialog = true
    }

    if (showPlayerAudioSettingsDialog) {
        val engines = listOf(
            PLAYER_AUDIO_ENGINE_MEDIA_PLAYER to context.getString(R.string.browser_system_media_player),
            PLAYER_AUDIO_ENGINE_EXO_PLAYER to "Media3 ExoPlayer"
        )
        val presets = listOf(
            PLAYER_AUDIO_PRESET_FLAT to context.getString(R.string.browser_flat_preset),
            PLAYER_AUDIO_PRESET_VOCAL to context.getString(R.string.browser_vocal_preset),
            PLAYER_AUDIO_PRESET_BASS to context.getString(R.string.browser_bass_preset),
            PLAYER_AUDIO_PRESET_CAR to context.getString(R.string.browser_car_preset),
            PLAYER_AUDIO_PRESET_HEADPHONE to context.getString(R.string.browser_headphone_preset)
        )
        fun updatePlayerAudioSettings(transform: (PlayerAudioSettings) -> PlayerAudioSettings) {
            scope.launch { prefs.updatePlayerAudioSettings(transform) }
        }
        AlertDialog(
            onDismissRequest = { showPlayerAudioSettingsDialog = false },
            title = { Text(stringResource(R.string.browser_player_config_title)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(stringResource(R.string.browser_player_settings_local_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.browser_player_engine), style = MaterialTheme.typography.titleSmall)
                    engines.forEach { (value, label) ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { updatePlayerAudioSettings { it.copy(engine = value) } }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = playerAudioSettings.engine == value,
                                onCheckedChange = { updatePlayerAudioSettings { it.copy(engine = value) } }
                            )
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Text(stringResource(R.string.browser_engine_switch_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.browser_wake_lock), style = MaterialTheme.typography.bodyLarge)
                            Text(stringResource(R.string.browser_wake_lock_detail), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = playerAudioSettings.keepAwake,
                            onCheckedChange = { enabled -> updatePlayerAudioSettings { it.copy(keepAwake = enabled) } }
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.browser_audio_effects), style = MaterialTheme.typography.bodyLarge)
                            Text(stringResource(R.string.browser_audio_effects_detail), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = playerAudioSettings.audioEffectsEnabled,
                            onCheckedChange = { enabled -> updatePlayerAudioSettings { it.copy(audioEffectsEnabled = enabled) } }
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.browser_audio_preset), style = MaterialTheme.typography.titleSmall)
                    presets.forEach { (value, label) ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { updatePlayerAudioSettings { it.copy(effectPreset = value, audioEffectsEnabled = true) } }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = playerAudioSettings.effectPreset == value,
                                onCheckedChange = { updatePlayerAudioSettings { it.copy(effectPreset = value, audioEffectsEnabled = true) } }
                            )
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.browser_high_quality_output), style = MaterialTheme.typography.bodyLarge)
                            Text(stringResource(R.string.browser_high_quality_output_detail), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = playerAudioSettings.highQualityOutput,
                            onCheckedChange = { enabled -> updatePlayerAudioSettings { it.copy(highQualityOutput = enabled) } }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPlayerAudioSettingsDialog = false }) { Text(stringResource(R.string.common_close)) }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        selectedPlaylist?.displayName(context.getString(R.string.player_unnamed_playlist))
                            ?: context.getString(R.string.player_screen_title)
                    )
                },
                actions = {
                    if (playbackState?.playlistId != null) {
                        TextButton(onClick = { promptSaveCurrentBookmark() }) {
                            Text(context.getString(R.string.player_mark_action))
                        }
                    }
                    TextButton(onClick = { showBookmarkManagerDialog = true }) {
                        Text(context.getString(R.string.player_bookmarks_action))
                    }
                    IconButton(onClick = { showPlayerAudioSettingsDialog = true }) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.browser_player_config_title))
                    }
                },
                navigationIcon = if (showBackButton) {
                    {
                        IconButton(onClick = {
                            if (selectedPlaylist != null) selectedPlaylistId = null
                            else onDismiss()
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = context.getString(R.string.common_back))
                        }
                    }
                } else {
                    { }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        val restorePlaylist = lastPlaylistId?.let { id -> playlists.find { it.id == id } }
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (playbackState != null) {
                val state = playbackState
                var seekSliderPosition by remember(state.trackIndex, state.trackName) { mutableStateOf(state.positionMs.toFloat()) }
                var seekDragging by remember { mutableStateOf(false) }
                LaunchedEffect(state.positionMs, state.durationMs) {
                    if (!seekDragging) {
                        seekSliderPosition = state.positionMs.toFloat().coerceIn(0f, maxOf(1f, state.durationMs.toFloat()))
                    }
                }
                Card(
                    Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            "${state.trackName} (${state.trackIndex + 1}/${state.totalTracks})",
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val metadataText = formatTrackMetadata(context, state.metadata)
                        if (metadataText.isNotEmpty()) {
                            Text(
                                metadataText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (state.previewActive) {
                            val previewPl = state.playlistId?.let { id -> playlistById[id] }
                            val previewStartText = formatPlaybackTime((previewPl?.previewStartMs ?: 0L).toInt())
                            val previewEndText = state.previewWindowEndMs?.let { formatPlaybackTime(it) }
                            Text(
                                if (previewEndText != null) {
                                    context.getString(R.string.player_preview_playing_hint, previewStartText, previewEndText)
                                } else {
                                    context.getString(R.string.player_preview_playing_hint_no_end, previewStartText)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                                maxLines = 2
                            )
                        }
                        if (state.durationMs > 0) {
                            Spacer(Modifier.height(4.dp))
                            Slider(
                                value = seekSliderPosition,
                                onValueChange = {
                                    seekDragging = true
                                    seekSliderPosition = it
                                },
                                onValueChangeFinished = {
                                    onSeek(seekSliderPosition.toInt().coerceIn(0, state.durationMs))
                                    seekDragging = false
                                },
                                valueRange = 0f..maxOf(1f, state.durationMs.toFloat()),
                                modifier = Modifier.fillMaxWidth(),
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    activeTrackColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    inactiveTrackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.3f)
                                )
                            )
                        }
                        Row(Modifier.fillMaxWidth()) {
                            Text(
                                formatPlaybackTime(state.positionMs),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                if (state.durationMs > 0) formatPlaybackTime(state.durationMs) else "--:--",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        val diagnostics = state.diagnostics
                        TextButton(onClick = { showPlaybackDiagnostics = !showPlaybackDiagnostics }) {
                            Text(if (showPlaybackDiagnostics) stringResource(R.string.browser_hide_diagnostics) else stringResource(R.string.browser_playback_diagnostics))
                        }
                        if (showPlaybackDiagnostics) {
                            val diagLines = listOfNotNull(
                                context.getString(R.string.browser_engine_value, diagnostics.engine.ifBlank { context.getString(R.string.common_unknown_error) }),
                                context.getString(R.string.browser_source_value, if (diagnostics.playbackSource == "direct") context.getString(R.string.browser_source_direct) else diagnostics.playbackSource),
                                context.getString(R.string.browser_output_value, diagnostics.outputDevice.ifBlank { context.getString(R.string.common_unknown_error) }),
                                diagnostics.outputDeviceSource.takeIf { it.isNotBlank() }?.let { context.getString(R.string.browser_output_based_on, it) },
                                context.getString(R.string.browser_offload_status, if (diagnostics.exoOffloadActive) context.getString(R.string.browser_offload_active) else context.getString(R.string.browser_offload_inactive)),
                                context.getString(R.string.browser_hq_output_value, diagnostics.highQualityOutput.ifBlank { context.getString(R.string.common_unknown_error) }),
                                context.getString(R.string.browser_effects_value, diagnostics.audioEffects.ifBlank { context.getString(R.string.common_unknown_error) }),
                                context.getString(R.string.browser_buffer_events, diagnostics.bufferEvents),
                                context.getString(R.string.browser_error_count, diagnostics.playerErrors),
                                diagnostics.lastError?.let { context.getString(R.string.browser_last_error, it) },
                                diagnostics.mimeType?.let { context.getString(R.string.browser_type_value, it) },
                                diagnostics.sourceQuality?.let { context.getString(R.string.browser_source_nature, it) },
                                diagnostics.bitrate?.let { context.getString(R.string.browser_bitrate, it) },
                                diagnostics.sampleRate?.let { context.getString(R.string.browser_sample_rate, it) },
                                diagnostics.bitsPerSample?.let { context.getString(R.string.browser_bit_depth, it) }
                            )
                            Text(
                                diagLines.joinToString("\n"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.82f)
                            )
                        }
                    }
                }
            } else {
                Card(
                    Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            context.getString(R.string.player_not_playing),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            if (restorePlaylist != null) context.getString(R.string.player_resume_hint, restorePlaylist.displayName()) else context.getString(R.string.player_select_playlist_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (restorePlaylist != null) {
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(onClick = { resumeLastPlayback(restorePlaylist) }) {
                                Text(context.getString(R.string.player_resume_last))
                            }
                        }
                    }
                }
            }
            if (selectedPlaylist != null) {
                val pl = selectedPlaylist
                val selectedPlaylistTrackListState = remember(pl.id) { LazyListState() }
                LaunchedEffect(pl.id, playbackState?.playlistId, playbackState?.trackIndex) {
                    val state = playbackState ?: return@LaunchedEffect
                    if (state.playlistId != pl.id) return@LaunchedEffect
                    val target = state.trackIndex.coerceIn(0, maxOf(0, pl.uris.lastIndex))
                    selectedPlaylistTrackListState.animateScrollToItem((target - 2).coerceAtLeast(0))
                }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (pl.isDirectorySource) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { openRenamePlaylistDialog(pl) }
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            pl.displayName(context.getString(R.string.player_unnamed_playlist)),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (pl.isDirectorySource) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (pl.isDirectorySource) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                context.getString(R.string.browser_playlist_dir_list_refresh_hint),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                    if (pl.isDirectorySource || pl.uris.size >= 2) {
                        IconButton(onClick = { openSortPlaylistDialog(pl) }) {
                            Icon(
                                Icons.AutoMirrored.Filled.Sort,
                                contentDescription = context.getString(R.string.player_resort_playlist),
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = context.getString(R.string.player_rename_playlist),
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                }
                if (pl.uris.isEmpty()) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(context.getString(R.string.player_list_empty), style = MaterialTheme.typography.bodyLarge)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        state = selectedPlaylistTrackListState,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                        verticalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        items(pl.uris.size) { i ->
                            val name = pl.names.getOrElse(i) { pl.uris[i].substringAfterLast('/') }
                            val isCurrentTrack = playbackState?.playlistId == pl.id && playbackState.trackIndex == i
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .background(
                                        if (isCurrentTrack) MaterialTheme.colorScheme.tertiaryContainer
                                        else MaterialTheme.colorScheme.surface
                                    )
                                    .clickable { startPlaylistFromIndex(pl, i) }
                                    .padding(start = 6.dp, end = 2.dp, top = 3.dp, bottom = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "${i + 1}.",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.width(22.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    name,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                    color = if (isCurrentTrack) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                                Box {
                                    IconButton(
                                        onClick = { trackActionMenuIndex = i },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.MoreVert,
                                            contentDescription = context.getString(R.string.main_menu),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = trackActionMenuIndex == i,
                                        onDismissRequest = { trackActionMenuIndex = null }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text(context.getString(R.string.player_play_from_here)) },
                                            leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                                            onClick = {
                                                trackActionMenuIndex = null
                                                startPlaylistFromIndex(pl, i)
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(context.getString(R.string.player_move_up)) },
                                            leadingIcon = { Icon(Icons.Default.ArrowUpward, contentDescription = null) },
                                            enabled = i > 0,
                                            onClick = {
                                                trackActionMenuIndex = null
                                                if (i > 0) scope.launch {
                                                    val uris = pl.uris.toMutableList()
                                                    val names = pl.names.toMutableList()
                                                    uris.add(i - 1, uris.removeAt(i))
                                                    names.add(i - 1, names.removeAt(i))
                                                    prefs.updatePlaylist(pl.copy(uris = uris, names = names))
                                                }
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(context.getString(R.string.player_move_down)) },
                                            leadingIcon = { Icon(Icons.Default.ArrowDownward, contentDescription = null) },
                                            enabled = i < pl.uris.size - 1,
                                            onClick = {
                                                trackActionMenuIndex = null
                                                if (i < pl.uris.size - 1) scope.launch {
                                                    val uris = pl.uris.toMutableList()
                                                    val names = pl.names.toMutableList()
                                                    uris.add(i + 1, uris.removeAt(i))
                                                    names.add(i + 1, names.removeAt(i))
                                                    prefs.updatePlaylist(pl.copy(uris = uris, names = names))
                                                }
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.browser_copy_to_list)) },
                                            leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                                            onClick = {
                                                pendingTrackTransfer = PlaylistTrackTransfer(pl, i, move = false)
                                                trackActionMenuIndex = null
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.browser_move_to_list)) },
                                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = null) },
                                            onClick = {
                                                pendingTrackTransfer = PlaylistTrackTransfer(pl, i, move = true)
                                                trackActionMenuIndex = null
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(context.getString(R.string.common_delete)) },
                                            leadingIcon = {
                                                Icon(
                                                    Icons.Default.Delete,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.error
                                                )
                                            },
                                            onClick = {
                                                trackActionMenuIndex = null
                                                scope.launch {
                                                    removePlaylistTrackAt(
                                                        context = context,
                                                        prefs = prefs,
                                                        pl = pl,
                                                        trackIndex = i,
                                                        playbackState = playbackState,
                                                        deleteSourceFile = pl.isDirectorySource,
                                                        onStopPlayback = onStopPlayback
                                                    )
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        context.getString(R.string.player_playlist_section),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { showCreatePlaylistDialog = true }) {
                        Text(context.getString(R.string.player_new_list))
                    }
                }
                if (playlists.isEmpty()) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            context.getString(R.string.player_no_playlists_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        Modifier.weight(1f),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                        verticalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        items(playlists.size) { i ->
                            val pl = playlists[i]
                            val isCurrent = playbackState?.playlistId == pl.id
                            val isDangerousPlaylist = pl.isDirectorySource
                            val rowBackground = when {
                                isDangerousPlaylist && isCurrent -> MaterialTheme.colorScheme.errorContainer
                                isDangerousPlaylist -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.82f)
                                isCurrent -> MaterialTheme.colorScheme.primaryContainer
                                else -> MaterialTheme.colorScheme.surface
                            }
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .background(rowBackground)
                                    .clickable { selectedPlaylistId = pl.id }
                                    .padding(start = 6.dp, end = 2.dp, top = 4.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        pl.displayName(context.getString(R.string.player_unnamed_playlist)),
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        color = if (isDangerousPlaylist) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface
                                    )
                                    val bookmarkCount = playerBookmarks.count { it.playlistId == pl.id }
                                    Text(
                                        context.getString(R.string.browser_playlist_track_bookmark, pl.trackCount, bookmarkCount),
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = if (isDangerousPlaylist) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (pl.previewEnabled) {
                                        Text(
                                            previewListHint(pl),
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            color = if (isDangerousPlaylist) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    if (isDangerousPlaylist) {
                                        Text(
                                            context.getString(R.string.browser_playlist_dir_list_hint),
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }
                                }
                                Box {
                                    IconButton(
                                        onClick = { playlistActionMenuIndex = i },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.MoreVert,
                                            contentDescription = context.getString(R.string.main_menu),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = playlistActionMenuIndex == i,
                                        onDismissRequest = { playlistActionMenuIndex = null }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text(context.getString(R.string.player_play_desc)) },
                                            leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                                            onClick = {
                                                playlistActionMenuIndex = null
                                                startPlaylist(pl)
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(context.getString(R.string.player_move_up)) },
                                            leadingIcon = { Icon(Icons.Default.ArrowUpward, contentDescription = null) },
                                            enabled = i > 0,
                                            onClick = {
                                                playlistActionMenuIndex = null
                                                if (i > 0) scope.launch {
                                                    val ids = playlists.map { it.id }.toMutableList()
                                                    ids.removeAt(i)
                                                    ids.add(i - 1, pl.id)
                                                    prefs.updatePlaylistOrder(ids)
                                                }
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(context.getString(R.string.player_move_down)) },
                                            leadingIcon = { Icon(Icons.Default.ArrowDownward, contentDescription = null) },
                                            enabled = i < playlists.size - 1,
                                            onClick = {
                                                playlistActionMenuIndex = null
                                                if (i < playlists.size - 1) scope.launch {
                                                    val ids = playlists.map { it.id }.toMutableList()
                                                    ids.removeAt(i)
                                                    ids.add(i + 1, pl.id)
                                                    prefs.updatePlaylistOrder(ids)
                                                }
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(context.getString(R.string.player_rename_playlist)) },
                                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                            onClick = {
                                                playlistActionMenuIndex = null
                                                openRenamePlaylistDialog(pl)
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(context.getString(R.string.player_resort_playlist)) },
                                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null) },
                                            enabled = pl.isDirectorySource || pl.uris.size >= 2,
                                            onClick = {
                                                playlistActionMenuIndex = null
                                                openSortPlaylistDialog(pl)
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(context.getString(R.string.player_preview_settings)) },
                                            leadingIcon = { Icon(Icons.Default.Timer, contentDescription = null) },
                                            onClick = {
                                                playlistActionMenuIndex = null
                                                openPreviewSettingsDialog(pl)
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(context.getString(R.string.common_delete)) },
                                            leadingIcon = {
                                                Icon(
                                                    Icons.Default.Delete,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.error
                                                )
                                            },
                                            onClick = {
                                                playlistActionMenuIndex = null
                                                scope.launch {
                                                    if (playbackState?.playlistId == pl.id) {
                                                        onStopPlayback()
                                                    }
                                                    prefs.removePlaylist(pl.id)
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            PlaybackBottomControlBar(
                isPlaying = playbackState?.isPlaying == true,
                enabled = playbackState != null || restorePlaylist != null,
                onPrev = {
                    if (playbackState != null) onPlayPrev()
                },
                onPlayPause = {
                    if (playbackState != null) onPlayPause()
                    else resumeLastPlayback(restorePlaylist)
                },
                onNext = {
                    if (playbackState != null) onPlayNext()
                }
            )
        }
        if (showCreatePlaylistDialog) {
            AlertDialog(
                onDismissRequest = {
                    showCreatePlaylistDialog = false
                    newPlaylistName = ""
                },
                title = { Text(stringResource(R.string.browser_new_empty_playlist)) },
                text = {
                    OutlinedTextField(
                        value = newPlaylistName,
                        onValueChange = { newPlaylistName = it },
                        label = { Text(stringResource(R.string.browser_playlist_name_label)) },
                        singleLine = true
                    )
                },
                confirmButton = {
                    Button(onClick = { scope.launch { createEmptyPlaylist(newPlaylistName) } }) {
                        Text(context.getString(R.string.common_ok))
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showCreatePlaylistDialog = false
                        newPlaylistName = ""
                    }) { Text(context.getString(R.string.common_cancel)) }
                }
            )
        }
        pendingTrackTransfer?.let { transfer ->
            val targetPlaylists = playlists.filter { it.id != transfer.source.id && !it.isDirectorySource }
            AlertDialog(
                onDismissRequest = { pendingTrackTransfer = null },
                title = { Text(if (transfer.move) stringResource(R.string.browser_move_to_playlist) else stringResource(R.string.browser_copy_to_playlist)) },
                text = {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    ) {
                        if (targetPlaylists.isEmpty()) {
                            Text(
                                context.getString(R.string.browser_playlist_no_normal_move_target),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            targetPlaylists.forEach { playlist ->
                                OutlinedButton(
                                    onClick = { scope.launch { transferTrackToPlaylist(transfer, playlist) } },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(Modifier.fillMaxWidth()) {
                                        Text(playlist.displayName(), color = MaterialTheme.colorScheme.onSurface)
                                        Text(
                                            context.getString(R.string.player_track_count, playlist.trackCount),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { pendingTrackTransfer = null }) {
                        Text(context.getString(R.string.common_cancel))
                    }
                }
            )
        }
        if (showPreviewSettingsDialog) {
            val target = previewSettingsTarget
            AlertDialog(
                onDismissRequest = {
                    showPreviewSettingsDialog = false
                    previewSettingsTarget = null
                    previewTimeError = null
                },
                title = { Text(context.getString(R.string.player_preview_settings_title)) },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(context.getString(R.string.player_preview_enabled), style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    context.getString(R.string.player_preview_enabled_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = previewEnabledInput,
                                onCheckedChange = { previewEnabledInput = it }
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = previewStartInput,
                            onValueChange = {
                                previewStartInput = it
                                previewTimeError = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(context.getString(R.string.player_preview_start_label)) },
                            supportingText = { Text(context.getString(R.string.player_preview_start_hint)) },
                            singleLine = true,
                            enabled = previewEnabledInput
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = previewMaxDurationInput,
                            onValueChange = {
                                previewMaxDurationInput = it
                                previewTimeError = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(context.getString(R.string.player_preview_max_duration_label)) },
                            supportingText = { Text(context.getString(R.string.player_preview_max_duration_hint)) },
                            singleLine = true,
                            enabled = previewEnabledInput
                        )
                        previewTimeError?.let { err ->
                            Spacer(Modifier.height(8.dp))
                            Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        val settingsTarget = target ?: return@Button
                        val startMs = if (previewEnabledInput) {
                            PlaybackTimeFormat.parseToMs(previewStartInput) ?: run {
                                previewTimeError = context.getString(R.string.player_preview_time_invalid)
                                return@Button
                            }
                        } else {
                            0L
                        }
                        val maxMs = if (previewEnabledInput && previewMaxDurationInput.isNotBlank()) {
                            PlaybackTimeFormat.parseToMs(previewMaxDurationInput) ?: run {
                                previewTimeError = context.getString(R.string.player_preview_time_invalid)
                                return@Button
                            }
                        } else {
                            0L
                        }
                        scope.launch {
                            prefs.updatePlaylist(
                                settingsTarget.copy(
                                    previewEnabled = previewEnabledInput,
                                    previewStartMs = startMs.coerceAtLeast(0L),
                                    previewMaxDurationMs = maxMs.coerceAtLeast(0L)
                                )
                            )
                            if (playbackState?.playlistId == settingsTarget.id) {
                                val reloadIntent = Intent(context, PlaybackService::class.java).apply {
                                    action = ACTION_RELOAD_PLAYLIST
                                    putExtra(EXTRA_PLAYLIST_ID, settingsTarget.id)
                                }
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    context.startForegroundService(reloadIntent)
                                } else {
                                    context.startService(reloadIntent)
                                }
                            }
                            showPreviewSettingsDialog = false
                            previewSettingsTarget = null
                            previewTimeError = null
                            Toast.makeText(context, context.getString(R.string.player_preview_saved), Toast.LENGTH_SHORT).show()
                        }
                    }) { Text(context.getString(R.string.common_save)) }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showPreviewSettingsDialog = false
                        previewSettingsTarget = null
                        previewTimeError = null
                    }) { Text(context.getString(R.string.common_cancel)) }
                }
            )
        }
        if (showRenamePlaylistDialog) {
            val target = playlistRenameTarget
            AlertDialog(
                onDismissRequest = {
                    showRenamePlaylistDialog = false
                    playlistRenameTarget = null
                    playlistRenameText = ""
                },
                title = { Text(context.getString(R.string.player_rename_playlist_title)) },
                text = {
                    OutlinedTextField(
                        value = playlistRenameText,
                        onValueChange = { playlistRenameText = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(context.getString(R.string.player_playlist_name_label)) },
                        singleLine = true
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        val newName = playlistRenameText.trim()
                        if (newName.isEmpty()) {
                            Toast.makeText(context, context.getString(R.string.player_playlist_name_required), Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        val renameTarget = target ?: return@Button
                        scope.launch {
                            prefs.updatePlaylist(renameTarget.copy(name = newName, note = ""))
                            showRenamePlaylistDialog = false
                            playlistRenameTarget = null
                            playlistRenameText = ""
                        }
                    }) { Text(context.getString(R.string.common_save)) }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showRenamePlaylistDialog = false
                        playlistRenameTarget = null
                        playlistRenameText = ""
                    }) { Text(context.getString(R.string.common_cancel)) }
                }
            )
        }
        if (showSortPlaylistDialog) {
            val sortTarget = playlistSortTarget
            val sortFieldOptions = listOf(
                PlaylistTrackSortField.ARTIST to R.string.player_resort_field_artist,
                PlaylistTrackSortField.ALBUM to R.string.player_resort_field_album,
                PlaylistTrackSortField.YEAR to R.string.player_resort_field_year,
                PlaylistTrackSortField.DIRECTORY to R.string.player_resort_field_directory,
                PlaylistTrackSortField.RANDOM to R.string.player_resort_field_random
            )
            AlertDialog(
                onDismissRequest = {
                    if (!playlistSortInProgress) {
                        showSortPlaylistDialog = false
                        playlistSortTarget = null
                    }
                },
                title = { Text(context.getString(R.string.player_resort_playlist_title)) },
                text = {
                    if (playlistSortInProgress) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(context.getString(R.string.player_resort_in_progress))
                        }
                    } else {
                        Column {
                            if (sortTarget?.isDirectorySource == true) {
                                Text(
                                    context.getString(R.string.player_resort_directory_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(8.dp))
                            }
                            sortFieldOptions.forEach { (field, labelRes) ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { playlistSortField = field }
                                        .padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    androidx.compose.material3.RadioButton(
                                        selected = playlistSortField == field,
                                        onClick = { playlistSortField = field }
                                    )
                                    Text(context.getString(labelRes))
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { playlistSortOrder = PlaylistSortOrder.ASCENDING }
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                androidx.compose.material3.RadioButton(
                                    selected = playlistSortOrder == PlaylistSortOrder.ASCENDING,
                                    onClick = { playlistSortOrder = PlaylistSortOrder.ASCENDING }
                                )
                                Text(context.getString(R.string.player_resort_order_asc))
                            }
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { playlistSortOrder = PlaylistSortOrder.DESCENDING }
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                androidx.compose.material3.RadioButton(
                                    selected = playlistSortOrder == PlaylistSortOrder.DESCENDING,
                                    onClick = { playlistSortOrder = PlaylistSortOrder.DESCENDING }
                                )
                                Text(context.getString(R.string.player_resort_order_desc))
                            }
                        }
                    }
                },
                confirmButton = {
                    val canResort = sortTarget != null && (sortTarget.isDirectorySource || sortTarget.uris.size >= 2)
                    Button(
                        enabled = !playlistSortInProgress && canResort,
                        onClick = {
                            val pl = sortTarget ?: return@Button
                            if (!pl.isDirectorySource && pl.uris.size < 2) {
                                Toast.makeText(context, context.getString(R.string.player_resort_too_few), Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            playlistSortInProgress = true
                            scope.launch {
                                try {
                                    val updated = resortPlaylistTracks(
                                        context = context,
                                        prefs = prefs,
                                        pl = pl,
                                        field = playlistSortField,
                                        order = playlistSortOrder,
                                        playbackState = playbackState,
                                        onStopPlayback = onStopPlayback
                                    )
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.player_resort_done, updated.trackCount),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } finally {
                                    playlistSortInProgress = false
                                    showSortPlaylistDialog = false
                                    playlistSortTarget = null
                                }
                            }
                        }
                    ) { Text(context.getString(R.string.common_ok)) }
                },
                dismissButton = {
                    TextButton(
                        enabled = !playlistSortInProgress,
                        onClick = {
                            showSortPlaylistDialog = false
                            playlistSortTarget = null
                        }
                    ) { Text(context.getString(R.string.common_cancel)) }
                }
            )
        }
        if (showAddBookmarkDialog) {
            AlertDialog(
                onDismissRequest = { showAddBookmarkDialog = false },
                title = { Text(context.getString(R.string.player_save_playback_bookmark_title)) },
                text = {
                    Column {
                        Text(
                            context.getString(
                                R.string.player_bookmark_position,
                                bookmarkPendingTrackIndex + 1,
                                formatPlaybackTime(bookmarkPendingPositionMs.toInt())
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (bookmarkPendingTrackName.isNotBlank()) {
                            Text(
                                bookmarkPendingTrackName,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = bookmarkNoteInput,
                            onValueChange = { bookmarkNoteInput = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(context.getString(R.string.player_bookmark_note_label)) },
                            placeholder = { Text(context.getString(R.string.player_bookmark_note_placeholder)) },
                            minLines = 2,
                            maxLines = 4
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val pid = bookmarkPendingPlaylistId
                            if (pid == null) return@Button
                            scope.launch {
                                prefs.addPlayerListBookmark(
                                    playlistId = pid,
                                    dirUri = bookmarkPendingDirUri,
                                    trackIndex = bookmarkPendingTrackIndex,
                                    positionMs = bookmarkPendingPositionMs,
                                    trackName = bookmarkPendingTrackName,
                                    note = bookmarkNoteInput
                                )
                                showAddBookmarkDialog = false
                                Toast.makeText(context, context.getString(R.string.player_bookmark_saved), Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) { Text(context.getString(R.string.common_save)) }
                },
                dismissButton = {
                    TextButton(onClick = { showAddBookmarkDialog = false }) { Text(context.getString(R.string.common_cancel)) }
                }
            )
        }
        editingBookmarkId?.let { bookmarkId ->
            AlertDialog(
                onDismissRequest = { editingBookmarkId = null },
                title = { Text(context.getString(R.string.player_edit_bookmark_note)) },
                text = {
                    OutlinedTextField(
                        value = editingBookmarkNote,
                        onValueChange = { editingBookmarkNote = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(context.getString(R.string.common_note)) },
                        minLines = 2,
                        maxLines = 4
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            scope.launch {
                                prefs.updatePlayerListBookmarkNote(bookmarkId, editingBookmarkNote)
                                editingBookmarkId = null
                            }
                        }
                    ) { Text(context.getString(R.string.common_save)) }
                },
                dismissButton = {
                    TextButton(onClick = { editingBookmarkId = null }) { Text(context.getString(R.string.common_cancel)) }
                }
            )
        }
        if (showBookmarkManagerDialog) {
            Dialog(onDismissRequest = { showBookmarkManagerDialog = false }) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    tonalElevation = 6.dp,
                    modifier = Modifier.fillMaxWidth().widthIn(max = 680.dp)
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .heightIn(max = 560.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                context.getString(R.string.player_bookmark_manager_title),
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { showBookmarkManagerDialog = false }) {
                                Text(context.getString(R.string.common_close))
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        if (playerBookmarks.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(context.getString(R.string.player_no_saved_bookmarks), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(playerBookmarks.sortedByDescending { it.savedAt }, key = { it.id }) { bm ->
                                    val playlist = bm.playlistId?.let { playlistById[it] }
                                    val jumpEnabled = playlist != null && bm.trackIndex in playlist.uris.indices
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                    ) {
                                        Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                            Text(
                                                playlist?.name ?: context.getString(R.string.player_playlist_missing),
                                                style = MaterialTheme.typography.titleSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Spacer(Modifier.height(4.dp))
                                            Text(
                                                context.getString(
                                                    R.string.player_bookmark_position_compact,
                                                    bm.trackIndex + 1,
                                                    formatPlaybackTime(bm.positionMs.toInt())
                                                ),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            if (bm.trackName.isNotBlank()) {
                                                Text(
                                                    bm.trackName,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            if (bm.note.isNotBlank()) {
                                                Spacer(Modifier.height(4.dp))
                                                Text(
                                                    bm.note,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 3,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            Spacer(Modifier.height(8.dp))
                                            Row(
                                                Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                OutlinedButton(
                                                    onClick = {
                                                        editingBookmarkId = bm.id
                                                        editingBookmarkNote = bm.note
                                                    }
                                                ) {
                                                    Text(context.getString(R.string.common_note))
                                                }
                                                OutlinedButton(
                                                    onClick = {
                                                        scope.launch {
                                                            prefs.deletePlayerListBookmark(bm.id)
                                                        }
                                                    }
                                                ) {
                                                    Text(context.getString(R.string.common_delete))
                                                }
                                                Button(
                                                    onClick = {
                                                        playlist?.let {
                                                            startPlaylistFromIndex(it, bm.trackIndex, bm.positionMs.toInt())
                                                            showBookmarkManagerDialog = false
                                                        }
                                                    },
                                                    enabled = jumpEnabled
                                                ) {
                                                    Text(context.getString(R.string.player_jump))
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
    }
}

@Composable
fun QuickObfuscatePasswordDialog(
    isObfuscate: Boolean,
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
    val passwordConfirmed = !isObfuscate || isRequiredPasswordConfirmed(password, confirmPassword)
    Dialog(onDismissRequest = { if (!inProgress) onDismiss() }) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(Modifier.padding(24.dp)) {
                Text(
                    if (isObfuscate) context.getString(R.string.context_quick_obfuscate) else context.getString(R.string.context_quick_deobfuscate),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(fileName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                if (isObfuscate) {
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
                if (inProgress) {
                    Spacer(Modifier.height(16.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    Text(if (isObfuscate) stringResource(R.string.browser_obfuscating) else stringResource(R.string.browser_deobfuscating), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(24.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss, enabled = !inProgress) { Text(stringResource(R.string.common_cancel)) }
                    Button(onClick = { onConfirm(password) }, enabled = !inProgress && passwordConfirmed && (!isObfuscate || password.isNotBlank())) { Text(stringResource(R.string.common_ok)) }
                }
            }
        }
    }
}

private fun isRequiredPasswordConfirmed(password: String, confirmPassword: String): Boolean {
    return password.isNotBlank() && password == confirmPassword
}

private fun isOptionalPasswordConfirmed(password: String, confirmPassword: String): Boolean {
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
private fun ReliablePasswordInputField(
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
fun AboutDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
        }.getOrElse { "" }
    }
    val versionWarn = remember(versionName) {
        versionName.replace(Regex("[^0-9.]"), "").takeIf { s -> s.isNotEmpty() }?.toFloatOrNull()?.let { v -> v <= 1.0f } == true
    }
    val tips = remember { context.resources.getStringArray(R.array.about_usage_tips) }
    val tip = remember { tips.random() }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(Modifier.padding(24.dp)) {
                Text("Local Manager", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.about_author), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.about_version, versionName), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (versionWarn) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.about_disclaimer),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.about_random_tips_title), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(4.dp))
                Text(tip, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(24.dp))
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) }
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

