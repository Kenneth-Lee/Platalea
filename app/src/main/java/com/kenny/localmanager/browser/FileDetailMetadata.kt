package com.kenny.localmanager.browser

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Build
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.kenny.localmanager.R
import com.kenny.localmanager.data.AudioMetaAnnotation
import com.kenny.localmanager.file.EpubParseResult
import com.kenny.localmanager.file.extractEpubToCache
import com.kenny.localmanager.file.getEpubCacheDir
import com.kenny.localmanager.file.loadEpubFromCache
import com.kenny.localmanager.player.DetailMetadataKind
import com.kenny.localmanager.player.formatPlaybackTime
import com.kenny.localmanager.player.inferDetailMetadataKind
import java.util.Locale

/**
 * 文件详情元数据加载与格式化（拆解设计 第四批：从 BrowserScreen 迁出）：
 * EXIF/音视频轨读取、时长/码率/分辨率等格式化，以及音频标注合并行。
 * 全部为纯函数或 IO 型加载器，无 Compose 依赖。
 */

internal data class DetailMetadataRow(
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

internal fun appendMetaIfNotBlank(
    rows: MutableList<DetailMetadataRow>,
    label: String,
    raw: String?
) {
    val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return
    rows += DetailMetadataRow(label, value)
}


internal fun appendMetaSection(
    rows: MutableList<DetailMetadataRow>,
    label: String
) {
    rows += DetailMetadataRow(label = label, value = "", isSection = true)
}


internal fun formatBitrate(raw: String?): String? {
    val bps = raw?.trim()?.toLongOrNull() ?: return raw?.trim()?.takeIf { it.isNotEmpty() }
    if (bps <= 0) return null
    return "${(bps / 1000.0).toInt()} kbps"
}


internal fun formatSampleRate(raw: String?): String? {
    val hz = raw?.trim()?.toLongOrNull() ?: return raw?.trim()?.takeIf { it.isNotEmpty() }
    if (hz <= 0) return null
    return "${hz} Hz"
}


internal fun formatDurationMs(raw: String?): String? {
    val ms = raw?.trim()?.toLongOrNull() ?: return raw?.trim()?.takeIf { it.isNotEmpty() }
    if (ms < 0) return null
    return formatPlaybackTime(ms.toInt())
}


internal fun formatDurationUs(rawUs: Long?): String? {
    val us = rawUs ?: return null
    if (us < 0) return null
    val ms = (us / 1000L).coerceAtMost(Int.MAX_VALUE.toLong())
    return formatPlaybackTime(ms.toInt())
}


internal fun formatVideoResolution(widthRaw: String?, heightRaw: String?): String? {
    val width = widthRaw?.trim()?.toIntOrNull()
    val height = heightRaw?.trim()?.toIntOrNull()
    if (width == null || height == null || width <= 0 || height <= 0) return null
    return "${width} x ${height}"
}


internal fun formatExifExposureTime(raw: String?): String? {
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


internal fun formatExifFocalLength(raw: String?): String? {
    val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val mm = value.toDoubleOrNull()
    return if (mm != null && mm > 0) {
        "${"%.1f".format(Locale.getDefault(), mm)} mm"
    } else {
        value
    }
}


internal fun formatExifAperture(raw: String?): String? {
    val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val fNumber = value.toDoubleOrNull()
    return if (fNumber != null && fNumber > 0) {
        "f/${"%.1f".format(Locale.getDefault(), fNumber)}"
    } else {
        value
    }
}


internal fun formatExifGps(exif: ExifInterface): String? {
    var lat = exif.getAttributeDouble(ExifInterface.TAG_GPS_LATITUDE, Double.NaN)
    var lon = exif.getAttributeDouble(ExifInterface.TAG_GPS_LONGITUDE, Double.NaN)
    if (lat.isNaN() || lon.isNaN()) return null
    val latRef = exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE_REF)?.trim()?.uppercase(Locale.ROOT)
    val lonRef = exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF)?.trim()?.uppercase(Locale.ROOT)
    if (latRef == "S") lat = -kotlin.math.abs(lat)
    if (lonRef == "W") lon = -kotlin.math.abs(lon)
    return "${"%.6f".format(Locale.getDefault(), lat)}, ${"%.6f".format(Locale.getDefault(), lon)}"
}


internal fun formatExifOrientation(raw: String?): String? {
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


internal fun formatExifWhiteBalance(raw: String?): String? {
    return when (raw?.trim()) {
        "0" -> "auto"
        "1" -> "manual"
        else -> raw?.trim()?.takeIf { it.isNotEmpty() }
    }
}


internal fun formatExifFlash(raw: String?): String? {
    val value = raw?.trim()?.toIntOrNull() ?: return raw?.trim()?.takeIf { it.isNotEmpty() }
    return if ((value and 0x1) == 1) "fired" else "not-fired"
}


internal fun formatExifMeteringMode(raw: String?): String? {
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


internal fun formatExifExposureCompensation(raw: String?): String? {
    val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val ev = value.toDoubleOrNull()
    return if (ev != null) {
        "${"%.2f".format(Locale.getDefault(), ev)} EV"
    } else {
        value
    }
}


internal fun formatExifColorSpace(raw: String?): String? {
    return when (raw?.trim()) {
        "1" -> "sRGB"
        "65535" -> "uncalibrated"
        else -> raw?.trim()?.takeIf { it.isNotEmpty() }
    }
}


internal fun formatExifAltitude(exif: ExifInterface): String? {
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


internal fun applyAudioMetaAnnotationRows(
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


internal fun MediaFormat.stringOrNull(key: String): String? {
    return if (containsKey(key)) getString(key)?.trim()?.takeIf { it.isNotEmpty() } else null
}

internal fun MediaFormat.intOrNull(key: String): Int? {
    return if (containsKey(key)) runCatching { getInteger(key) }.getOrNull() else null
}

internal fun MediaFormat.floatOrNull(key: String): Float? {
    return if (containsKey(key)) runCatching { getFloat(key) }.getOrNull() else null
}

internal fun MediaFormat.longOrNull(key: String): Long? {
    return if (containsKey(key)) runCatching { getLong(key) }.getOrNull() else null
}


internal fun loadVideoTrackMetadataRows(context: Context, uri: Uri): List<DetailMetadataRow> {
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


internal fun loadAudioTrackMetadataRows(context: Context, uri: Uri): List<DetailMetadataRow> {
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


internal fun loadTypeSpecificMetadataRows(
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




