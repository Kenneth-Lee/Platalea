package com.kenny.localmanager.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class BatchMetadataEditTest {
    @Test
    fun imageBatchMerge_keepsUntouchedFields() {
        val current = EditableImageExif(
            takenTime = "2024:01:02 03:04:05",
            artist = "Alice",
            description = "Old note",
            software = "CamApp",
            copyright = "2024"
        )
        val patch = EditableImageExif(
            takenTime = "",
            artist = "Bob",
            description = "",
            software = "LensApp",
            copyright = ""
        )

        val merged = mergeEditableImageExifForBatch(current, patch)

        assertEquals("2024:01:02 03:04:05", merged.takenTime)
        assertEquals("Bob", merged.artist)
        assertEquals("Old note", merged.description)
        assertEquals("LensApp", merged.software)
        assertEquals("2024", merged.copyright)
    }

    @Test
    fun audioBatchMerge_keepsUntouchedFields() {
        val current = EditableAudioAnnotation(
            title = "Old title",
            artist = "Old artist",
            album = "Old album"
        )
        val patch = EditableAudioAnnotation(
            title = "",
            artist = "New artist",
            album = ""
        )

        val merged = mergeEditableAudioAnnotationForBatch(current, patch)

        assertEquals("Old title", merged.title)
        assertEquals("New artist", merged.artist)
        assertEquals("Old album", merged.album)
    }

    @Test
    fun imageSaveVerification_onlyChecksChangedFields() {
        val before = EditableImageExif(
            takenTime = "2024:01:02 03:04:05",
            artist = "Alice",
            description = "Old note",
            software = "CamApp",
            copyright = "2024"
        )
        val expected = EditableImageExif(
            takenTime = "2024:01:02 03:04:05",
            artist = "Bob",
            description = "Old note",
            software = "CamApp",
            copyright = "2024"
        )
        val actual = EditableImageExif(
            takenTime = "2024:01:02 03:04:05",
            artist = "Bob",
            description = "Old note",
            software = "CamApp",
            copyright = "2024"
        )

        assertEquals(listOf("artist"), compareChangedImageExifFields(before, expected, actual))
    }

    @Test
    fun audioSaveVerification_onlyChecksChangedFields() {
        val before = EditableAudioAnnotation(
            title = "Old title",
            artist = "Old artist",
            album = "Old album"
        )
        val expected = EditableAudioAnnotation(
            title = "Old title",
            artist = "Old artist",
            album = "New album"
        )
        val actual = EditableAudioAnnotation(
            title = "Old title",
            artist = "Old artist",
            album = "New album"
        )

        assertEquals(listOf("album"), compareChangedAudioAnnotationFields(before, expected, actual))
    }

    @Test
    fun archiveDetection_supportsTarCompressedFormats() {
        assertEquals(true, isExtractableArchive("backup.tar.gz"))
        assertEquals(true, isExtractableArchive("backup.tar.bz2"))
        assertEquals(true, isExtractableArchive("backup.tar.xz"))
        assertEquals(true, isExtractableArchive("backup.tgz"))
        assertEquals(true, isExtractableArchive("backup.tbz2"))
        assertEquals(true, isExtractableArchive("backup.txz"))
    }

    @Test
    fun vidZipDetection_supportsVideoArchiveNames() {
        assertEquals(true, isVidZip("clip.vid.zip"))
        assertEquals(true, isVidZip("movie.VID.ZIP"))
        assertEquals(false, isVidZip("movie.mp4"))
    }
}
