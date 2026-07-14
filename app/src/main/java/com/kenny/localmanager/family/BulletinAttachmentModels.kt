package com.kenny.localmanager.family

import org.json.JSONArray
import org.json.JSONObject

object BulletinAttachmentDefaults {
    const val CHUNK_SIZE = 1048576
}

enum class BulletinAttachmentKind(val wire: String) {
    FILE("file"),
    DIRECTORY("directory");

    companion object {
        fun fromWire(value: String): BulletinAttachmentKind? =
            entries.firstOrNull { it.wire == value }
    }
}

enum class BulletinAttachmentStatus(val wire: String) {
    UPLOADING("uploading"),
    READY("ready"),
    FAILED("failed"),
    EXPIRED("expired");

    companion object {
        fun fromWire(value: String): BulletinAttachmentStatus? =
            entries.firstOrNull { it.wire == value }
    }
}

data class BulletinAttachmentRef(
    val id: String,
    val kind: BulletinAttachmentKind,
    val name: String,
    val size: Long = 0L,
    val totalSize: Long = 0L,
    val fileCount: Int = 0,
    val sha256: String? = null,
    val mime: String? = null
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("kind", kind.wire)
        put("name", name)
        when (kind) {
            BulletinAttachmentKind.FILE -> {
                put("size", size)
                sha256?.let { put("sha256", it) }
                mime?.let { put("mime", it) }
            }
            BulletinAttachmentKind.DIRECTORY -> {
                put("file_count", fileCount)
                put("total_size", if (totalSize > 0) totalSize else size)
                sha256?.let { put("sha256", it) }
            }
        }
    }

    companion object {
        fun fromJson(obj: JSONObject): BulletinAttachmentRef {
            val kind = BulletinAttachmentKind.fromWire(obj.optString("kind", "file"))
                ?: BulletinAttachmentKind.FILE
            return BulletinAttachmentRef(
                id = obj.getString("id"),
                kind = kind,
                name = obj.optString("name", ""),
                size = obj.optLong("size"),
                totalSize = obj.optLong("total_size", obj.optLong("size")),
                fileCount = obj.optInt("file_count"),
                sha256 = obj.optString("sha256").takeIf { it.isNotBlank() },
                mime = obj.optString("mime").takeIf { it.isNotBlank() }
            )
        }
    }
}

data class BulletinDirectoryEntry(
    val path: String,
    val size: Long,
    val sha256: String? = null
)

data class BulletinDirectoryFileSlot(
    val fileId: String,
    val path: String,
    val size: Long,
    val sha256: String? = null,
    val uploadedChunks: Set<Int> = emptySet()
)
