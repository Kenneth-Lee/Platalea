package com.kenny.localmanager.player

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.kenny.localmanager.data.Playlist
import com.kenny.localmanager.file.collectMusicFilesRecursively
import java.util.Locale
import kotlin.random.Random

enum class PlaylistTrackSortField {
    ARTIST,
    ALBUM,
    YEAR,
    DIRECTORY,
    RANDOM
}

enum class PlaylistSortOrder {
    ASCENDING,
    DESCENDING
}

object PlaylistTrackSorter {

    fun sortTracks(
        context: Context,
        playlist: Playlist,
        field: PlaylistTrackSortField,
        order: PlaylistSortOrder
    ): Playlist {
        if (playlist.uris.size < 2) return playlist
        val indices = playlist.uris.indices.toList()
        val sortedIndices = when (field) {
            PlaylistTrackSortField.RANDOM -> {
                val shuffled = indices.shuffled(Random.Default)
                if (order == PlaylistSortOrder.DESCENDING) shuffled.reversed() else shuffled
            }
            else -> {
                val metadata = playlist.uris.map { readTrackMetadata(context, it) }
                val directoryKeys = buildDirectoryKeys(context, playlist)
                val comparator = Comparator<Int> { a, b ->
                    compareByField(field, a, b, metadata, directoryKeys, playlist.names)
                }
                val sorted = indices.sortedWith(comparator)
                if (order == PlaylistSortOrder.DESCENDING) sorted.reversed() else sorted
            }
        }
        val newUris = sortedIndices.map { playlist.uris[it] }
        val newNames = sortedIndices.map { index ->
            playlist.names.getOrElse(index) { playlist.uris[index].substringAfterLast('/') }
        }
        return playlist.copy(uris = newUris, names = newNames)
    }

    private fun compareByField(
        field: PlaylistTrackSortField,
        a: Int,
        b: Int,
        metadata: List<TrackMetadata>,
        directoryKeys: List<String>,
        names: List<String>
    ): Int {
        val tieNameA = names.getOrElse(a) { "" }.lowercase(Locale.ROOT)
        val tieNameB = names.getOrElse(b) { "" }.lowercase(Locale.ROOT)
        val primary = when (field) {
            PlaylistTrackSortField.ARTIST -> {
                val va = sortableText(metadata[a].artist ?: metadata[a].albumArtist, tieNameA)
                val vb = sortableText(metadata[b].artist ?: metadata[b].albumArtist, tieNameB)
                va.compareTo(vb, ignoreCase = true)
            }
            PlaylistTrackSortField.ALBUM -> {
                val va = sortableText(metadata[a].album, tieNameA)
                val vb = sortableText(metadata[b].album, tieNameB)
                va.compareTo(vb, ignoreCase = true)
            }
            PlaylistTrackSortField.YEAR -> {
                val ya = metadata[a].year?.toIntOrNull()
                val yb = metadata[b].year?.toIntOrNull()
                when {
                    ya == null && yb == null -> 0
                    ya == null -> 1
                    yb == null -> -1
                    else -> ya.compareTo(yb)
                }
            }
            PlaylistTrackSortField.DIRECTORY -> {
                directoryKeys[a].compareTo(directoryKeys[b], ignoreCase = true)
            }
            PlaylistTrackSortField.RANDOM -> 0
        }
        if (primary != 0) return primary
        val secondary = tieNameA.compareTo(tieNameB, ignoreCase = true)
        if (secondary != 0) return secondary
        return a.compareTo(b)
    }

    private fun sortableText(value: String?, fallback: String): String =
        value?.trim()?.takeIf { it.isNotEmpty() } ?: "\uFFFF$fallback"

    private fun buildDirectoryKeys(context: Context, playlist: Playlist): List<String> {
        val pathByUri = if (playlist.isDirectorySource && !playlist.sourceUri.isNullOrBlank()) {
            collectMusicFilesRecursively(context, playlist.sourceUri)
                .associate { it.model.uri.toString() to it.relativePath }
        } else {
            emptyMap()
        }
        return playlist.uris.mapIndexed { index, uri ->
            pathByUri[uri] ?: derivePathFromUri(uri, playlist.names.getOrElse(index) { "" })
        }
    }

    private fun derivePathFromUri(uriStr: String, name: String): String {
        val uri = Uri.parse(uriStr)
        val path = uri.path?.trim('/') ?: uriStr
        return if (name.isNotBlank() && path.endsWith(name, ignoreCase = true)) {
            path
        } else if (name.isNotBlank()) {
            "$path/$name"
        } else {
            path
        }
    }

    private fun readTrackMetadata(context: Context, uriStr: String): TrackMetadata {
        return try {
            MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource(context, Uri.parse(uriStr))
                TrackMetadata(
                    title = retriever.extractTrimmedMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE),
                    artist = retriever.extractTrimmedMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST),
                    album = retriever.extractTrimmedMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM),
                    albumArtist = retriever.extractTrimmedMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST),
                    genre = retriever.extractTrimmedMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE),
                    year = retriever.extractTrimmedMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)
                )
            }
        } catch (_: Exception) {
            TrackMetadata()
        }
    }

    private fun MediaMetadataRetriever.extractTrimmedMetadata(keyCode: Int): String? =
        extractMetadata(keyCode)?.trim()?.takeIf { it.isNotEmpty() }
}
