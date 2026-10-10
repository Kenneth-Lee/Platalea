package com.kenny.localmanager.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.graphics.pdf.PdfRenderer
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.documentfile.provider.DocumentFile
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.kenny.localmanager.R
import com.kenny.localmanager.data.AudioMetaAnnotation
import com.kenny.localmanager.data.DirectoryScrollMemory
import com.kenny.localmanager.dict.isStarDictImportCandidate
import com.kenny.localmanager.file.DocumentFileModel
import com.kenny.localmanager.file.EpubParseResult
import com.kenny.localmanager.file.RecursiveFileSearchCriteria
import com.kenny.localmanager.file.RecursiveFileSearchHit
import com.kenny.localmanager.file.ZipFirstLevelResult
import com.kenny.localmanager.file.buildExternalOpenIntent
import com.kenny.localmanager.file.extractEpubToCache
import com.kenny.localmanager.file.fileExtensionKey
import com.kenny.localmanager.file.getArchiveFirstLevelEntries
import com.kenny.localmanager.file.getEpubCacheDir
import com.kenny.localmanager.file.guessExternalOpenMimeType
import com.kenny.localmanager.file.isGalleryImageFile
import com.kenny.localmanager.file.isHtmlFile
import com.kenny.localmanager.file.listChildrenFast
import com.kenny.localmanager.file.loadEpubFromCache
import com.kenny.localmanager.file.renameDocument
import com.kenny.localmanager.file.searchFilesRecursively
import com.kenny.localmanager.file.parseSizeRange
import com.kenny.localmanager.file.parseTimeRange
import com.kenny.localmanager.file.toExternalLaunchUri
import com.kenny.localmanager.player.PlaybackState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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


@Composable
private fun fileItemSubtitle(model: DocumentFileModel): String {
    val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(model.lastModified))
    return if (model.displaySize.isNotEmpty()) "${model.displaySize}  $dateStr" else dateStr
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


private fun getCompressedPayloadExtension(name: String): String? {
    if (!name.endsWith(".zip", ignoreCase = true)) return null
    val withoutZip = name.dropLast(4)
    val dotIndex = withoutZip.lastIndexOf('.')
    if (dotIndex <= 0 || dotIndex >= withoutZip.length - 1) return null
    return withoutZip.substring(dotIndex + 1)
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


@Composable
private fun FileSearchDialog(
    namePattern: TextFieldValue,
    contentPattern: String,
    sizeRange: String,
    modified: String,
    searching: Boolean,
    searchProgress: Pair<Int, Int>?,
    error: String?,
    onNamePatternChange: (TextFieldValue) -> Unit,
    onContentPatternChange: (String) -> Unit,
    onSizeRangeChange: (String) -> Unit,
    onModifiedChange: (String) -> Unit,
    onSearch: () -> Unit,
    onSearchCancel: () -> Unit,
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
                val namePatternFocus = remember { FocusRequester() }
                OutlinedTextField(
                    value = namePattern,
                    onValueChange = onNamePatternChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(namePatternFocus),
                    label = { Text(stringResource(R.string.browser_filename_regex_label)) },
                    placeholder = { Text(stringResource(R.string.browser_regex_example)) },
                    singleLine = true
                )
                Row(modifier = Modifier.fillMaxWidth()) {
                    listOf("\\", "+").forEach { sym ->
                        TextButton(
                            onClick = {
                                // 光标处插入（与批量改名一致）：取选区，插入后光标移到
                                // 插入内容之后并回焦输入框
                                val sel = namePattern.selection
                                val start = sel.start.coerceIn(0, namePattern.text.length)
                                val end = sel.end.coerceIn(0, namePattern.text.length)
                                val newText = namePattern.text.substring(0, start) + sym +
                                    namePattern.text.substring(end)
                                onNamePatternChange(
                                    TextFieldValue(newText, TextRange(start + sym.length))
                                )
                                namePatternFocus.requestFocus()
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp)
                        ) { Text(sym, style = MaterialTheme.typography.titleMedium) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = contentPattern,
                    onValueChange = onContentPatternChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.browser_content_regex_label)) },
                    supportingText = { Text(stringResource(R.string.browser_content_regex_hint)) },
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = sizeRange,
                    onValueChange = onSizeRangeChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.browser_size_range_label)) },
                    supportingText = { Text(stringResource(R.string.browser_size_range_hint)) },
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = modified,
                    onValueChange = onModifiedChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.browser_time_range_label)) },
                    supportingText = { Text(stringResource(R.string.browser_time_range_hint)) },
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.browser_recursive_search_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (searching) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    searchProgress?.let { (dirs, hits) ->
                        Text(
                            stringResource(R.string.browser_search_progress, dirs, hits),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
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
                    TextButton(onClick = onDismiss, enabled = !searching) { Text(stringResource(R.string.common_close)) }
                    if (searching) {
                        TextButton(onClick = onSearchCancel) { Text(stringResource(R.string.browser_search_cancel)) }
                    } else {
                        TextButton(onClick = onSearch) {
                            Text(stringResource(R.string.browser_start_search))
                        }
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
    onRefine: () -> Unit
) {
    val context = LocalContext.current
    val timeFormatter = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    // 任何方式关闭（按钮/返回键/点外部）都回到查找界面，便于调整条件重查
    Dialog(onDismissRequest = onRefine) {
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
                    TextButton(onClick = onRefine) { Text(stringResource(R.string.browser_search_refine)) }
                    TextButton(onClick = onAddAll, enabled = results.any { it.model.uri.toString() !in pendingUris }) {
                        Text(stringResource(R.string.browser_add_all_to_pending))
                    }
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
internal fun BrowserScreen(
    modifier: Modifier = Modifier,
    config: BrowserConfigView,
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
    zipEditTargetUri: String? = null,
    zipEditTargetName: String? = null,
    zipEditTargetIsZip: Boolean = false,
    onSetZipEditTarget: (DocumentFileModel) -> Unit = {},
    onAddToZipEditTarget: (DocumentFileModel) -> Unit = {},
    onEditZipContents: (DocumentFileModel) -> Unit = {},
    onOpenZipManager: () -> Unit = {},
    onClearZipEditTarget: () -> Unit = {},
    playbackState: PlaybackState? = null,
    onOpenPlaybackScreen: () -> Unit = {}
) {
    val directoryScrollMinItems = 30
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
    var externalOpenForcedMimeType by remember { mutableStateOf<String?>(null) }
    var externalOpenShowTypeChooser by remember { mutableStateOf(false) }
    var showOpenUrlDialog by remember { mutableStateOf(false) }
    var openUrlInput by remember { mutableStateOf("") }
    var openUrlError by remember { mutableStateOf<String?>(null) }
    var showFileSearchDialog by remember { mutableStateOf(false) }
    var showFileSearchResultsDialog by remember { mutableStateOf(false) }
    var fileSearchNamePattern by remember { mutableStateOf(TextFieldValue("")) }
    var fileSearchContentPattern by remember { mutableStateOf("") }
    var fileSearchSizeRange by remember { mutableStateOf("") }
    var fileSearchModified by remember { mutableStateOf("") }
    var fileSearchResults by remember { mutableStateOf<List<RecursiveFileSearchHit>>(emptyList()) }
    var fileSearchRunning by remember { mutableStateOf(false) }
    var fileSearchCancelled by remember { mutableStateOf(false) }
    var fileSearchProgress by remember { mutableStateOf<Pair<Int, Int>?>(null) }
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
        fileSearchNamePattern = TextFieldValue("")
        fileSearchContentPattern = ""
        fileSearchSizeRange = ""
        fileSearchModified = ""
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
            fileSearchNamePattern.text.trim().takeIf { it.isNotEmpty() }?.let(::Regex)
        } catch (error: Exception) {
            fileSearchError = context.getString(R.string.browser_filename_regex_invalid, error.message ?: error.javaClass.simpleName)
            return
        }
        val contentRegex = try {
            fileSearchContentPattern.trim().takeIf { it.isNotEmpty() }?.let(::Regex)
        } catch (error: Exception) {
            fileSearchError = context.getString(R.string.browser_content_regex_invalid, error.message ?: error.javaClass.simpleName)
            return
        }
        val sizeRange = parseSizeRange(fileSearchSizeRange)
        if (sizeRange == null) {
            fileSearchError = context.getString(R.string.browser_size_range_invalid, fileSearchSizeRange.trim())
            return
        }
        val timeRange = parseTimeRange(fileSearchModified, System.currentTimeMillis())
        if (timeRange == null) {
            fileSearchError = context.getString(R.string.browser_time_range_invalid, fileSearchModified.trim())
            return
        }
        fileSearchCancelled = false
        fileSearchProgress = null
        scope.launch {
            fileSearchRunning = true
            fileSearchError = null
            try {
                val criteria = RecursiveFileSearchCriteria(
                    nameRegex = nameRegex,
                    contentRegex = contentRegex,
                    minSizeBytes = sizeRange.minBytes,
                    maxSizeBytes = sizeRange.maxBytes,
                    modifiedAfterMillis = timeRange.afterMillis,
                    modifiedBeforeMillis = timeRange.beforeMillis
                )
                val results = withContext(Dispatchers.IO) {
                    searchFilesRecursively(
                        context, currentUri, criteria,
                        isCancelled = { fileSearchCancelled },
                        onProgress = { dirs, hits -> fileSearchProgress = dirs to hits }
                    )
                }
                if (fileSearchCancelled) {
                    // 用户取消：丢弃结果，留在查找界面便于调整条件
                    Toast.makeText(context, context.getString(R.string.browser_search_cancelled), Toast.LENGTH_SHORT).show()
                } else {
                    fileSearchResults = results
                    showFileSearchDialog = false
                    showFileSearchResultsDialog = true
                    if (results.isEmpty()) {
                        Toast.makeText(context, context.getString(R.string.browser_no_matching_files), Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (error: Exception) {
                fileSearchError = context.getString(R.string.browser_search_failed, error.message ?: error.javaClass.simpleName)
            } finally {
                fileSearchRunning = false
                fileSearchProgress = null
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
            isGpgEncryptedFile(target.name) -> context.getString(R.string.context_gpg_decrypt)
            isPassFile(target.name) -> context.getString(R.string.context_view_password)
            isMarkdownFile(target.name) -> context.getString(R.string.context_render_markdown)
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
            isGpgEncryptedFile(target.name) ->
                onRequestGpgDecrypt(target, currentUri)
            isPassFile(target.name) ->
                onRequestPassView(target)
            isMarkdownFile(target.name) ->
                onOpenMarkdownView(target.uri.toString(), target.name, false)
            else -> {
                val preferredPackage = fileExtensionKey(target.name)?.let { preferredExternalPackages[it] }
                if (preferredPackage != null && onOpenWithOtherApp(target.uri.toString(), target.name, preferredPackage, false, null)) {
                    Unit
                } else {
                    val encrypted = isGpgEncryptedFile(target.name)
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
            config.removeDirectoryScrollMemory(uriToSave)
            return
        }
        val maxIndex = (itemsToSave.size - 1).coerceAtLeast(0)
        val index = listState.firstVisibleItemIndex.coerceIn(0, maxIndex)
        val anchorItem = itemsToSave.getOrNull(index)
        val now = System.currentTimeMillis()
        config.setDirectoryScrollMemory(
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
        val memory = withContext(Dispatchers.IO) { config.getDirectoryScrollMemory(currentUri) } ?: return@LaunchedEffect
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
            // [死锁修复] 原实现 runBlocking{ withContext(IO){ DataStore.edit } } 在主线程
            // dispose 路径上同步等待 DataStore，与 Main 队列上的挂起协程互等（DataStore 官方
            // 明确禁止主线程 runBlocking）。改用 NonCancellable 的独立 IO 协程 fire-and-forget：
            // 不依赖已随组合取消的 scope，也不阻塞主线程；仅丢「dispose 后进程立即死亡」的
            // 极端窗口（滚动位置记忆，非关键数据，ON_STOP 兜底同路径）。
            CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
                persistDirectoryScrollMemory(uriToSave, itemsToSave, wasLoading)
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
                                    clearFileSearchInputs()
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
                                    throw IllegalStateException("debug crash trigger from PlataleaApp")
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
                        if (zipEditTargetName != null) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AssistChip(
                                    onClick = onOpenZipManager,
                                    label = { Text(stringResource(R.string.zip_edit_bar_label, zipEditTargetName), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false)) },
                                    leadingIcon = { Icon(Icons.Default.Archive, contentDescription = null, Modifier.size(18.dp)) }
                                )
                                TextButton(onClick = onClearZipEditTarget) {
                                    Text(stringResource(R.string.zip_edit_bar_clear))
                                }
                            }
                        }
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
            contentPattern = fileSearchContentPattern,
            sizeRange = fileSearchSizeRange,
            modified = fileSearchModified,
            searching = fileSearchRunning,
            error = fileSearchError,
            onNamePatternChange = {
                fileSearchNamePattern = it
                if (fileSearchError != null) fileSearchError = null
            },
            onContentPatternChange = {
                fileSearchContentPattern = it
                if (fileSearchError != null) fileSearchError = null
            },
            onSizeRangeChange = {
                fileSearchSizeRange = it
                if (fileSearchError != null) fileSearchError = null
            },
            onModifiedChange = {
                fileSearchModified = it
                if (fileSearchError != null) fileSearchError = null
            },
            searchProgress = fileSearchProgress,
            onSearch = { runFileSearch() },
            onSearchCancel = { fileSearchCancelled = true },
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
            onRefine = {
                showFileSearchResultsDialog = false
                showFileSearchDialog = true
            }
        )
    }

    if (showContextMenu && contextMenuTarget != null) {
        val menuTarget = contextMenuTarget!!
        val openMenuLabel = defaultOpenMenuLabel(menuTarget)
        val menuMaxHeight = (LocalConfiguration.current.screenHeightDp * 0.9f).dp
        Dialog(onDismissRequest = { showContextMenu = false; contextMenuTarget = null }) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = menuMaxHeight)
                        .verticalScroll(rememberScrollState())
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
                            openMenuLabel,
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
                            // 默认打开项已是内置查看器时不再重复列出
                            if (openMenuLabel != context.getString(R.string.context_open_with_internal_viewer)) {
                                TextButton(
                                    onClick = {
                                        showContextMenu = false
                                        onClearExternalOpenPreference(menuTarget.name)
                                        onOpenFile(menuTarget.uri.toString(), menuTarget.name, false)
                                        contextMenuTarget = null
                                    }
                                ) { Text(context.getString(R.string.context_open_with_internal_viewer), color = MaterialTheme.colorScheme.onSurface) }
                            }
                        if (!isPassFile(menuTarget.name)) {
                            TextButton(
                                onClick = {
                                    showContextMenu = false
                                    onRequestGpgEncrypt(menuTarget, currentUri)
                                    contextMenuTarget = null
                                }
                            ) { Text(context.getString(R.string.context_gpg_encrypt), color = MaterialTheme.colorScheme.onSurface) }
                        }
                        if ((isMarkdownFile(menuTarget.name) || isTxtFile(menuTarget.name))
                            && onRequestPassProtect != null) {
                            TextButton(
                                onClick = {
                                    showContextMenu = false
                                    onRequestPassProtect(menuTarget)
                                    contextMenuTarget = null
                                }
                            ) { Text(context.getString(R.string.context_pass_protect), color = MaterialTheme.colorScheme.onSurface) }
                        }
                        if (isPassFile(menuTarget.name)) {
                            TextButton(
                                onClick = {
                                    showContextMenu = false
                                    onRequestPassEdit(menuTarget, currentUri)
                                    contextMenuTarget = null
                                }
                            ) { Text(context.getString(R.string.context_edit_directly), color = MaterialTheme.colorScheme.onSurface) }
                        }
                        if (isConfigImportFile(menuTarget.name) && onRequestImportConfig != null) {
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
                    if (!menuTarget.isDirectory) {
                        TextButton(
                            onClick = {
                                showContextMenu = false
                                onSetZipEditTarget(menuTarget)
                                contextMenuTarget = null
                            }
                        ) { Text(context.getString(R.string.zip_edit_menu_set_target), color = MaterialTheme.colorScheme.onSurface) }
                    }
                    if (zipEditTargetIsZip && zipEditTargetUri != null && menuTarget.uri.toString() != zipEditTargetUri) {
                        TextButton(
                            onClick = {
                                showContextMenu = false
                                onAddToZipEditTarget(menuTarget)
                                contextMenuTarget = null
                            }
                        ) { Text(context.getString(R.string.zip_edit_menu_add_to_target), color = MaterialTheme.colorScheme.onSurface) }
                    }
                    if (!menuTarget.isDirectory && menuTarget.name.endsWith(".zip", ignoreCase = true)) {
                        TextButton(
                            onClick = {
                                showContextMenu = false
                                onEditZipContents(menuTarget)
                                contextMenuTarget = null
                            }
                        ) { Text(context.getString(R.string.zip_edit_menu_edit_contents), color = MaterialTheme.colorScheme.onSurface) }
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
        // 详情面板的“压缩包内容”区覆盖所有 zip（含 pic/vid 阅读器形态）；其余压缩格式同解压判定
        val isArchiveFile = isExtractableArchive(detailTarget.name) || isPicZip(detailTarget.name) || isVidZip(detailTarget.name)
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
                    config.getAudioMetaAnnotation(detailTarget.uri.toString())
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
                                                val loaded = withContext(Dispatchers.IO) {
                                                    readEditableImageExif(context, detailTarget.uri)
                                                }
                                                imageExifLoading = false
                                                if (loaded == null) {
                                                    Toast.makeText(
                                                        context,
                                                        context.getString(R.string.browser_meta_read_failed),
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                } else {
                                                    imageExifForm = loaded
                                                    showImageExifEditDialog = true
                                                }
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
                                                    config.getAudioMetaAnnotation(detailTarget.uri.toString())
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
                                            config.setAudioMetaAnnotation(detailTarget.uri.toString(), null)
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
                                            config.setAudioMetaAnnotation(detailTarget.uri.toString(), null)
                                        }
                                        writeError
                                    } else {
                                        runCatching {
                                            config.setAudioMetaAnnotation(
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

private fun MediaFormat.stringOrNull(key: String): String? {
    return if (containsKey(key)) getString(key)?.trim()?.takeIf { it.isNotEmpty() } else null
}

private fun MediaFormat.intOrNull(key: String): Int? {
    return if (containsKey(key)) runCatching { getInteger(key) }.getOrNull() else null
}

private fun MediaFormat.floatOrNull(key: String): Float? {
    return if (containsKey(key)) runCatching { getFloat(key) }.getOrNull() else null
}

private enum class FileSortOrder(@androidx.annotation.StringRes val labelRes: Int) {
    NAME(R.string.browser_name_field),
    TIME(R.string.browser_modified_field),
    SIZE(R.string.browser_size_field)
}



internal data class CachedDir(val items: List<DocumentFileModel>)
