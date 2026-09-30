package com.kenny.localmanager.ui

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.exifinterface.media.ExifInterface
import com.kenny.localmanager.R
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * 图片 EXIF 域共享符号：XP 标签 UTF-16 编解码、LmExifPayload token 编解码、
 * 详细信息表单（EditableImageExif）的读取/校验/合并/写回。browser 域（BrowserScreen
 * 详细信息、BrowserTabHost 批量编辑）消费，自 AppShell 平移（行为等价，token 不变）。
 */
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

internal fun mergeEditableImageExifForBatch(current: EditableImageExif, patch: EditableImageExif): EditableImageExif {
    return current.copy(
        takenTime = patch.takenTime.trim().ifBlank { current.takenTime },
        artist = patch.artist.trim().ifBlank { current.artist },
        description = patch.description.trim().ifBlank { current.description },
        software = patch.software.trim().ifBlank { current.software },
        copyright = patch.copyright.trim().ifBlank { current.copyright }
    )
}

internal fun readEditableImageExif(context: Context, uri: Uri): EditableImageExif? {
    // SAF 对已被外部删除/移动的文档会抛 IllegalArgumentException（FileNotFoundException 被包装），
    // 这里统一兜底为 null，由调用方决定提示方式（避免协程未捕获异常导致进程崩溃）。
    return try {
        readEditableImageExifUnchecked(context, uri)
    } catch (_: Exception) {
        null
    }
}

private fun readEditableImageExifUnchecked(context: Context, uri: Uri): EditableImageExif? {
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
            ?: return "read source failed (file missing or unreadable)"
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
            ?: return "post-save read-back failed (file missing or unreadable)"
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
