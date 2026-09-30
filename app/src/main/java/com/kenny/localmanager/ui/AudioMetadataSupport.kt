package com.kenny.localmanager.ui

import android.content.Context
import android.icu.text.Transliterator
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.kenny.localmanager.R
import com.kenny.localmanager.file.DocumentFileModel
import com.kenny.localmanager.data.Playlist
import com.kenny.localmanager.player.TrackMetadata
import com.mpatric.mp3agic.ID3v24Tag
import com.mpatric.mp3agic.Mp3File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.File
import java.text.Normalizer
import java.util.Locale

/**
 * 音频元数据域共享符号：查重归一化、元数据读取（TrackMetadata）、音频注解（MP3 tag / Jaudiotagger）
 * 的读写与校验。播放器域（PlaybackScreen）与 browser 域（BrowserScreen 详细信息、BrowserTabHost
 * 批量编辑）共同消费，自 AppShell 平移（行为等价，token 不变）。
 */
internal fun dedupeAudioList(audioList: List<DocumentFileModel>): List<DocumentFileModel> {
    val seen = LinkedHashSet<String>()
    return audioList.filter { seen.add(it.uri.toString()) }
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
internal const val EDITABLE_EXIF_TEXT_MAX_LENGTH = 512
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
internal data class PlaylistTrackTransfer(
    val source: Playlist,
    val trackIndex: Int,
    val move: Boolean
)
