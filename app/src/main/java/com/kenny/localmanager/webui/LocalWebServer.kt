package com.kenny.localmanager.webui

import android.content.Context
import android.content.ClipData
import android.content.ClipboardManager
import androidx.documentfile.provider.DocumentFile
import com.kenny.localmanager.R
import com.kenny.localmanager.data.ConfigExportCategory
import com.kenny.localmanager.data.Preferences
import com.kenny.localmanager.data.exportConfig
import com.kenny.localmanager.file.listFilesSafe
import com.kenny.localmanager.data.importConfig
import net.lingala.zip4j.ZipFile
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.PushbackInputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.Base64
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import com.kenny.localmanager.file.formatSize

private const val MAX_IN_MEMORY_BODY_BYTES = 8L * 1024L * 1024L
private const val WEBUI_HEADER_READ_TIMEOUT_MS = 3000L
private const val WEBUI_BODY_READ_TIMEOUT_MS = 3000L
private const val WEBUI_UPLOAD_BODY_READ_TIMEOUT_MS = 0L

private data class HttpRequest(
    val method: String,
    val path: String,
    val query: Map<String, String>,
    val headers: Map<String, String>,
    val body: ByteArray,
    val bodyFile: File? = null
)

internal data class UploadedFile(
    val fileName: String,
    val relativePath: String,
    val bytes: ByteArray = byteArrayOf(),
    val tempFile: File? = null
)

internal fun normalizeUploadRelativePath(rawPath: String): String {
    val value = rawPath.replace('\\', '/').trim()
    if (value.isEmpty()) return ""
    val cleaned = value.replace(Regex("^([A-Za-z]:)?/+"), "")
    val segments = mutableListOf<String>()
    for (segment in cleaned.split('/')) {
        when {
            segment.isEmpty() || segment == "." -> Unit
            segment == ".." -> if (segments.isNotEmpty()) segments.removeAt(segments.lastIndex)
            else -> segments.add(segment)
        }
    }
    return segments.joinToString("/")
}

internal fun parseMultipartUploads(body: ByteArray, contentType: String): List<UploadedFile> {
    val boundary = contentType.substringAfter("boundary=", "").trim().removePrefix("\"").removeSuffix("\"")
    if (boundary.isBlank()) return emptyList()
    val marker = "--$boundary"
    val chunks = String(body, StandardCharsets.ISO_8859_1).split(marker)
    val result = mutableListOf<UploadedFile>()
    for (chunk in chunks) {
        if (chunk.isBlank() || chunk.startsWith("--")) continue
        val headerEnd = chunk.indexOf("\r\n\r\n")
        if (headerEnd < 0) continue
        val headers = chunk.substring(0, headerEnd)
        val payload = chunk.substring(headerEnd + 4)
        val rawFilename = parseMultipartFilename(headers) ?: continue
        val relativePath = normalizeUploadRelativePath(rawFilename)
        if (relativePath.isBlank()) continue
        val fileName = relativePath.substringAfterLast('/')
        if (fileName.isBlank()) continue
        val normalizedPayload = if (payload.endsWith("\r\n")) payload.dropLast(2) else payload
        result.add(UploadedFile(fileName, relativePath, normalizedPayload.toByteArray(StandardCharsets.ISO_8859_1)))
    }
    return result
}

internal fun parseMultipartUploadsFromFile(file: File, contentType: String): List<UploadedFile> {
    val boundary = contentType.substringAfter("boundary=", "").trim().removePrefix("\"").removeSuffix("\"")
    if (boundary.isBlank()) return emptyList()
    val boundaryLine = "--$boundary"
    val boundaryMarker = "\r\n$boundaryLine".toByteArray(StandardCharsets.ISO_8859_1)
    val input = PushbackInputStream(file.inputStream().buffered(), 16 * 1024)
    val result = mutableListOf<UploadedFile>()
    val firstLine = readMultipartLine(input) ?: return emptyList()
    if (firstLine != boundaryLine && !firstLine.startsWith(boundaryLine)) return emptyList()
    outerLoop@ while (true) {
        val headers = readMultipartHeaders(input) ?: break
        val rawFilename = parseMultipartFilename(headers)
        if (rawFilename == null) {
            when (skipMultipartPayload(input, boundaryMarker, boundaryLine)) {
                MultipartBoundaryState.FINAL -> break
                MultipartBoundaryState.NEXT_PART -> continue@outerLoop
                MultipartBoundaryState.END_OF_STREAM -> break
            }
            continue@outerLoop
        }
        val relativePath = normalizeUploadRelativePath(rawFilename)
        if (relativePath.isBlank()) {
            when (skipMultipartPayload(input, boundaryMarker, boundaryLine)) {
                MultipartBoundaryState.FINAL -> break
                MultipartBoundaryState.NEXT_PART -> continue@outerLoop
                MultipartBoundaryState.END_OF_STREAM -> break
            }
            continue@outerLoop
        }
        val fileName = relativePath.substringAfterLast('/')
        if (fileName.isBlank()) {
            when (skipMultipartPayload(input, boundaryMarker, boundaryLine)) {
                MultipartBoundaryState.FINAL -> break
                MultipartBoundaryState.NEXT_PART -> continue@outerLoop
                MultipartBoundaryState.END_OF_STREAM -> break
            }
            continue@outerLoop
        }
        val tempFile = File.createTempFile("platalea-upload-part-", ".bin")
        when (copyMultipartPayloadToFile(input, tempFile, boundaryMarker, boundaryLine)) {
            MultipartBoundaryState.END_OF_STREAM -> {
                tempFile.delete()
                break
            }
            MultipartBoundaryState.FINAL, MultipartBoundaryState.NEXT_PART -> Unit
        }
        val trimmedBytes = tempFile.readBytes()
        val boundaryTail = "\r\n".toByteArray(StandardCharsets.ISO_8859_1)
        val payloadBytes = if (trimmedBytes.size >= boundaryTail.size && trimmedBytes.copyOfRange(trimmedBytes.size - boundaryTail.size, trimmedBytes.size).contentEquals(boundaryTail)) {
            trimmedBytes.copyOf(trimmedBytes.size - boundaryTail.size)
        } else {
            trimmedBytes
        }
        if (payloadBytes != trimmedBytes) {
            tempFile.writeBytes(payloadBytes)
        }
        val bytes = if (payloadBytes.size <= MAX_IN_MEMORY_BODY_BYTES) payloadBytes else byteArrayOf()
        result.add(UploadedFile(fileName, relativePath, bytes = bytes, tempFile = tempFile))
    }
    return result
}

private fun parseMultipartFilename(headers: String): String? {
    val encoded = Regex("filename\\*=(?:UTF-8'')?([^;\\r\\n]+)", RegexOption.IGNORE_CASE)
        .find(headers)?.groupValues?.getOrNull(1)
    if (encoded != null) {
        return URLDecoder.decode(encoded.trim().trim('"'), StandardCharsets.UTF_8.name())
    }
    return Regex("filename=\"([^\"]+)\"", RegexOption.IGNORE_CASE)
        .find(headers)?.groupValues?.getOrNull(1)
        ?: Regex("filename=([^;\\r\\n]+)", RegexOption.IGNORE_CASE)
            .find(headers)?.groupValues?.getOrNull(1)?.trim()?.trim('"')
}

internal fun readMultipartHeaders(input: InputStream): String? {
    val lines = mutableListOf<String>()
    while (true) {
        val line = readMultipartLine(input) ?: return if (lines.isEmpty()) null else lines.joinToString("\r\n")
        if (line.isEmpty()) return lines.joinToString("\r\n")
        lines.add(line)
    }
}

internal enum class MultipartBoundaryState {
    NEXT_PART,
    FINAL,
    END_OF_STREAM
}

internal fun copyMultipartPayloadToFile(input: PushbackInputStream, outputFile: File, boundaryBytes: ByteArray, boundaryLine: String): MultipartBoundaryState {
    val output = outputFile.outputStream().buffered()
    val buffer = ByteArray(8192)
    var carry = ByteArray(0)
    try {
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) return MultipartBoundaryState.END_OF_STREAM
            val chunk = if (carry.isEmpty()) buffer.copyOf(read) else carry + buffer.copyOf(read)
            val boundaryIndex = indexOfBytes(chunk, boundaryBytes)
            if (boundaryIndex >= 0) {
                if (boundaryIndex > 0) {
                    output.write(chunk, 0, boundaryIndex)
                }
                val remaining = chunk.copyOfRange(boundaryIndex + boundaryBytes.size, chunk.size)
                if (remaining.isNotEmpty()) {
                    input.unread(remaining)
                }
                output.flush()
                return consumeMultipartBoundarySuffix(input, boundaryLine)
            }
            val flushLength = maxOf(0, chunk.size - boundaryBytes.size + 1)
            if (flushLength > 0) {
                output.write(chunk, 0, flushLength)
            }
            carry = if (flushLength < chunk.size) chunk.copyOfRange(flushLength, chunk.size) else ByteArray(0)
        }
        output.flush()
        return MultipartBoundaryState.END_OF_STREAM
    } finally {
        output.close()
    }
}

private fun skipMultipartPayload(input: PushbackInputStream, boundaryBytes: ByteArray, boundaryLine: String): MultipartBoundaryState {
    val buffer = ByteArray(8192)
    var carry = ByteArray(0)
    while (true) {
        val read = input.read(buffer)
        if (read <= 0) return MultipartBoundaryState.END_OF_STREAM
        val chunk = if (carry.isEmpty()) buffer.copyOf(read) else carry + buffer.copyOf(read)
        val boundaryIndex = indexOfBytes(chunk, boundaryBytes)
        if (boundaryIndex >= 0) {
            val remaining = chunk.copyOfRange(boundaryIndex + boundaryBytes.size, chunk.size)
            if (remaining.isNotEmpty()) {
                input.unread(remaining)
            }
            return consumeMultipartBoundarySuffix(input, boundaryLine)
        }
        val flushLength = maxOf(0, chunk.size - boundaryBytes.size + 1)
        carry = if (flushLength < chunk.size) chunk.copyOfRange(flushLength, chunk.size) else ByteArray(0)
    }
    return MultipartBoundaryState.END_OF_STREAM
}

private fun indexOfBytes(haystack: ByteArray, needle: ByteArray): Int {
    if (needle.isEmpty() || haystack.size < needle.size) return -1
    val limit = haystack.size - needle.size
    for (start in 0..limit) {
        var matched = true
        for (offset in needle.indices) {
            if (haystack[start + offset] != needle[offset]) {
                matched = false
                break
            }
        }
        if (matched) return start
    }
    return -1
}

private fun consumeMultipartBoundarySuffix(input: InputStream, boundaryLine: String): MultipartBoundaryState {
    val next1 = input.read()
    val next2 = input.read()
    return when {
        next1 == '-'.code && next2 == '-'.code -> {
            drainMultipartLineEnding(input)
            MultipartBoundaryState.FINAL
        }
        next1 == '\r'.code && next2 == '\n'.code -> MultipartBoundaryState.NEXT_PART
        next1 == '\n'.code -> MultipartBoundaryState.NEXT_PART
        next1 < 0 || next2 < 0 -> MultipartBoundaryState.END_OF_STREAM
        else -> {
            drainMultipartLineEnding(input)
            MultipartBoundaryState.NEXT_PART
        }
    }
}

private fun drainMultipartLineEnding(input: InputStream) {
    val first = input.read()
    if (first == '\r'.code) {
        input.read()
    }
}

internal fun readMultipartLine(input: InputStream): String? {
    val builder = StringBuilder()
    while (true) {
        val value = input.read()
        if (value < 0) {
            return if (builder.isEmpty()) null else builder.toString()
        }
        when (value.toChar()) {
            '\r' -> {
                val next = input.read()
                if (next == '\n'.code) return builder.toString()
                if (next >= 0) builder.append(next.toChar())
                return builder.toString()
            }
            '\n' -> return builder.toString()
            else -> builder.append(value.toChar())
        }
    }
}

private const val WEBUI_QUICK_NOTE_FILE = ".lm.note.md"

class LocalWebServer(
    private val context: Context,
    private val rootDoc: DocumentFile,
    private val configuredPassword: String,
    preferredPort: Int,
    private val sslContext: SSLContext? = null,
    private val onLog: (String) -> Unit = {}
) {
    private val running = AtomicBoolean(false)
    @Volatile
    private var activeSessionToken: String? = null
    private val secureRandom = SecureRandom()
    private val serverSocket: ServerSocket = createServerSocket(preferredPort)

    private fun createServerSocket(preferredPort: Int): ServerSocket {
        val socket = if (sslContext != null) {
            sslContext.serverSocketFactory.createServerSocket() as SSLServerSocket
        } else {
            ServerSocket()
        }
        socket.reuseAddress = true
        try {
            socket.bind(InetSocketAddress(preferredPort))
        } catch (_: Throwable) {
            socket.bind(InetSocketAddress(0))
        }
        socket.soTimeout = 2000
        return socket
    }

    val port: Int
        get() = serverSocket.localPort

    fun start() {
        if (!running.compareAndSet(false, true)) return
        Thread {
            while (running.get()) {
                try {
                    val socket = serverSocket.accept()
                    handleSocket(socket)
                } catch (_: SocketException) {
                } catch (_: Throwable) {
                }
            }
        }.apply {
            name = "LocalWebServer"
            isDaemon = true
        }.start()
    }

    fun stop() {
        running.set(false)
        try {
            serverSocket.close()
        } catch (_: Throwable) {
        }
    }

    private fun handleSocket(socket: Socket) {
        socket.use { client ->
            try {
                client.soTimeout = WEBUI_HEADER_READ_TIMEOUT_MS.toInt()
                if (client is SSLSocket) {
                    client.useClientMode = false
                    client.startHandshake()
                }
                val input = BufferedInputStream(client.getInputStream())
                val output = client.getOutputStream()
                val request = readRequest(client, input) ?: return
                val requestBodyFile = request.bodyFile

                if (request.method == "GET" && request.path == "/") {
                    writeResponse(output, 200, "text/html; charset=utf-8", buildIndexPage().toByteArray(StandardCharsets.UTF_8), emptyMap())
                    return
                }

                if (request.method == "POST" && request.path == "/api/auth") {
                    val fields = parseFormUrlEncoded(request.body)
                    val password = fields["secret"] ?: ""
                    if (checkPassword(password)) {
                        val token = issueSessionToken()
                        writeJson(output, 200, mapOf("ok" to true, "token" to token))
                    } else {
                        writeJson(output, 401, mapOf("ok" to false, "error" to "invalid_password"))
                    }
                    return
                }

                if (request.method == "POST" && request.path == "/api/logout") {
                    activeSessionToken = null
                    writeJson(output, 200, mapOf("ok" to true))
                    return
                }

                if (!isAuthorized(request.headers)) {
                    writeJson(output, 401, mapOf("ok" to false, "error" to "unauthorized"))
                    return
                }

                when {
                    request.method == "GET" && request.path == "/api/list" -> {
                        val normalizedPath = normalizePath(request.query["path"] ?: "/")
                        val target = resolveDocument(normalizedPath) ?: run {
                            writeJson(output, 404, mapOf("error" to "not_found"))
                            return
                        }
                        writeJson(output, 200, mapOf("path" to normalizedPath, "entries" to listEntries(target, normalizedPath)))
                    }
                    request.method == "GET" && request.path == "/api/download" -> {
                        val target = resolveDocument(request.query["path"] ?: "/") ?: run {
                            writeJson(output, 404, mapOf("error" to "not_found"))
                            return
                        }
                        if (target.isDirectory) {
                            val zip = zipDirectory(target)
                            val bytes = zip.readBytes()
                            zip.delete()
                            writeResponse(output, 200, "application/zip", bytes, mapOf("Content-Disposition" to "attachment; filename=\"${target.name ?: "download"}.zip\""))
                        } else {
                            val bytes = readDocumentBytes(target)
                            writeResponse(output, 200, guessMimeType(target.name ?: "download"), bytes, mapOf("Content-Disposition" to "attachment; filename=\"${target.name ?: "download"}\""))
                        }
                    }
                    request.method == "POST" && request.path == "/api/upload" -> {
                        val startedAt = System.currentTimeMillis()
                        val parent = resolveDocument(request.query["path"] ?: "/") ?: rootDoc
                        val files = when {
                            requestBodyFile != null -> parseMultipartUploadsFromFile(requestBodyFile, request.headers["content-type"] ?: "")
                            else -> parseMultipartUploads(request.body, request.headers["content-type"] ?: "")
                        }
                        val overwrite = request.query["overwrite"] == "1"
                        val uploadType = request.query["type"] ?: "file"
                        if (!overwrite && uploadType == "dir") {
                            val rootDirectoryNames = files.mapNotNull { uploadRootDirectoryName(it.relativePath) }.distinct()
                            val existingDirectoryNames = rootDirectoryNames.filter { rootName ->
                                parent.listFilesSafe().any { child -> child.isDirectory && child.name == rootName }
                            }
                            if (existingDirectoryNames.isNotEmpty()) {
                                val conflictFiles = files.map { file ->
                                    mapOf(
                                        "name" to file.fileName,
                                        "path" to file.relativePath,
                                        "status" to "conflict"
                                    )
                                }
                                writeJson(output, 200, mapOf(
                                    "ok" to false,
                                    "files" to conflictFiles,
                                    "okCount" to 0,
                                    "conflictCount" to conflictFiles.size,
                                    "failedCount" to 0,
                                    "tookMs" to (System.currentTimeMillis() - startedAt),
                                    "error" to "directory_exists"
                                ))
                                return
                            }
                        }
                        val result = mutableListOf<Map<String, String>>()
                        for (file in files) {
                            val destination = createUploadTarget(parent, file.relativePath, overwrite)
                            if (destination == null) {
                                val status = if (!overwrite) "conflict" else "failed"
                                result.add(mapOf("name" to file.fileName, "path" to file.relativePath, "status" to status))
                                continue
                            }
                            val wrote = writeUploadedFileToDocument(destination, file)
                            result.add(mapOf(
                                "name" to file.fileName,
                                "path" to file.relativePath,
                                "status" to if (wrote) "ok" else "failed"
                            ))
                        }
                        val okCount = result.count { it["status"] == "ok" }
                        val conflictCount = result.count { it["status"] == "conflict" }
                        val failedCount = result.count { it["status"] == "failed" }
                        val tookMs = System.currentTimeMillis() - startedAt
                        val allSucceeded = result.isNotEmpty() && failedCount == 0 && conflictCount == 0
                        writeJson(output, 200, mapOf(
                            "ok" to allSucceeded,
                            "files" to result,
                            "okCount" to okCount,
                            "conflictCount" to conflictCount,
                            "failedCount" to failedCount,
                            "tookMs" to tookMs
                        ))
                    }
                    request.method == "POST" && request.path == "/api/upload/precheck" -> {
                        val parent = resolveDocument(request.query["path"] ?: "/") ?: rootDoc
                        val uploadType = request.query["type"] ?: "file"
                        val overwrite = request.query["overwrite"] == "1"
                        val fields = parseFormUrlEncoded(request.body)
                        val paths = parseUploadPaths(fields["paths"] ?: "[]")
                        val conflictPaths = findUploadConflictPaths(parent, paths, uploadType, overwrite)
                        writeJson(output, 200, mapOf(
                            "ok" to conflictPaths.isEmpty(),
                            "conflictCount" to conflictPaths.size,
                            "conflictPaths" to conflictPaths
                        ))
                    }
                    request.method == "POST" && request.path == "/api/mkdir" -> {
                        val parent = resolveDocument(request.query["path"] ?: "/") ?: run {
                            writeJson(output, 404, mapOf("error" to "not_found"))
                            return
                        }
                        val name = request.query["name"] ?: ""
                        val created = if (name.isNotBlank()) parent.createDirectory(name) else null
                        writeJson(output, if (created != null) 200 else 400, mapOf("ok" to (created != null), "name" to name))
                    }
                    request.method == "POST" && request.path == "/api/delete" -> {
                        val path = normalizePath(request.query["path"] ?: "")
                        if (path == "/") {
                            writeJson(output, 400, mapOf("ok" to false, "error" to "cannot_delete_root"))
                            return
                        }
                        val target = resolveDocument(path) ?: run {
                            writeJson(output, 404, mapOf("ok" to false, "error" to "not_found"))
                            return
                        }
                        val deleted = target.delete()
                        writeJson(output, if (deleted) 200 else 400, mapOf("ok" to deleted, "path" to path))
                    }
                    request.method == "GET" && request.path == "/api/quick-note/load" -> {
                        val result = loadQuickNoteText()
                        result.onSuccess { text ->
                            writeJson(output, 200, mapOf("ok" to true, "text" to text))
                        }.onFailure { error ->
                            writeJson(output, 500, mapOf("ok" to false, "error" to "load_failed", "message" to (error.message ?: "unknown")))
                        }
                    }
                    request.method == "GET" && request.path == "/api/clipboard/text" -> {
                        val result = readClipboardText()
                        result.onSuccess { clipboard ->
                            writeJson(output, 200, mapOf(
                                "ok" to true,
                                "text" to clipboard.text,
                                "mimeType" to clipboard.mimeType,
                                "label" to clipboard.label,
                                "hasText" to clipboard.hasText,
                                "updatedAtMs" to clipboard.updatedAtMs
                            ))
                        }.onFailure { error ->
                            writeJson(output, 500, mapOf("ok" to false, "error" to "clipboard_load_failed", "message" to (error.message ?: "unknown")))
                        }
                    }
                    request.method == "POST" && request.path == "/api/clipboard/text" -> {
                        val fields = parseFormUrlEncoded(request.body)
                        val text = fields["text"] ?: ""
                        val result = writeClipboardText(text)
                        result.onSuccess {
                            writeJson(output, 200, mapOf("ok" to true, "length" to text.length))
                        }.onFailure { error ->
                            writeJson(output, 500, mapOf("ok" to false, "error" to "clipboard_save_failed", "message" to (error.message ?: "unknown")))
                        }
                    }
                    request.method == "POST" && request.path == "/api/quick-note/save" -> {
                        val fields = parseFormUrlEncoded(request.body)
                        val text = fields["text"] ?: ""
                        val result = saveQuickNoteText(text)
                        result.onSuccess {
                            writeJson(output, 200, mapOf("ok" to true, "length" to text.length))
                        }.onFailure { error ->
                            writeJson(output, 500, mapOf("ok" to false, "error" to "save_failed", "message" to (error.message ?: "unknown")))
                        }
                    }
                    request.method == "GET" && request.path == "/api/config/get" -> {
                        val result = readWebUiConfig()
                        result.onSuccess { config ->
                            writeJson(output, 200, mapOf("ok" to true, "config" to config))
                        }.onFailure { error ->
                            writeJson(output, 500, mapOf("ok" to false, "error" to "config_get_failed", "message" to (error.message ?: "unknown")))
                        }
                    }
                    request.method == "POST" && request.path == "/api/config/set" -> {
                        val fields = parseFormUrlEncoded(request.body)
                        val result = updateWebUiConfig(fields)
                        result.onSuccess {
                            writeJson(output, 200, mapOf("ok" to true))
                        }.onFailure { error ->
                            writeJson(output, 500, mapOf("ok" to false, "error" to "config_set_failed", "message" to (error.message ?: "unknown")))
                        }
                    }
                    request.method == "GET" && request.path == "/api/config/export" -> {
                        val categories = parseConfigCategories(request.query["categories"])
                        val result = exportConfigJson(categories)
                        result.onSuccess { jsonText ->
                            writeJson(output, 200, mapOf("ok" to true, "json" to jsonText))
                        }.onFailure { error ->
                            writeJson(output, 500, mapOf("ok" to false, "error" to "config_export_failed", "message" to (error.message ?: "unknown")))
                        }
                    }
                    request.method == "POST" && request.path == "/api/config/import" -> {
                        val fields = parseFormUrlEncoded(request.body)
                        val json = fields["json"] ?: ""
                        val importKeys = fields["import_keys"] != "0"
                        val categories = parseConfigCategories(fields["categories"])
                        val result = importConfigJson(json, importKeys, categories)
                        result.onSuccess { ok ->
                            writeJson(output, if (ok) 200 else 400, mapOf("ok" to ok))
                        }.onFailure { error ->
                            writeJson(output, 500, mapOf("ok" to false, "error" to "config_import_failed", "message" to (error.message ?: "unknown")))
                        }
                    }
                    else -> {
                        writeJson(output, 404, mapOf("error" to "not_found"))
                    }
                }
            } catch (t: Throwable) {
                onLog("Web request failed: ${t.message ?: t.javaClass.simpleName}")
                runCatching {
                    val output = client.getOutputStream()
                    writeJson(output, 500, mapOf("ok" to false, "error" to "server_error", "message" to (t.message ?: "unknown")))
                }
            }
        }
    }

    private fun isAuthorized(headers: Map<String, String>): Boolean {
        val token = headers["x-auth-token"]
        if (!token.isNullOrBlank() && token == activeSessionToken) {
            return true
        }
        val authHeader = headers["authorization"] ?: return false
        if (!authHeader.startsWith("Basic ")) return false
        val decoded = try {
            String(Base64.getDecoder().decode(authHeader.removePrefix("Basic ").trim()), StandardCharsets.UTF_8)
        } catch (_: Throwable) {
            return false
        }
        val pieces = decoded.split(":", limit = 2)
        val password = pieces.getOrNull(1) ?: ""
        return checkPassword(password)
    }

    private fun checkPassword(input: String): Boolean {
        return if (configuredPassword.isEmpty()) input.isEmpty() else input == configuredPassword
    }

    private fun issueSessionToken(): String {
        val random = ByteArray(24)
        secureRandom.nextBytes(random)
        val token = Base64.getUrlEncoder().withoutPadding().encodeToString(random)
        activeSessionToken = token
        return token
    }

    private fun parseFormUrlEncoded(body: ByteArray): Map<String, String> {
        val text = String(body, StandardCharsets.UTF_8)
        if (text.isBlank()) return emptyMap()
        val result = mutableMapOf<String, String>()
        for (segment in text.split("&")) {
            if (segment.isBlank()) continue
            val kv = segment.split("=", limit = 2)
            val key = URLDecoder.decode(kv.getOrNull(0) ?: "", StandardCharsets.UTF_8.name())
            val value = URLDecoder.decode(kv.getOrNull(1) ?: "", StandardCharsets.UTF_8.name())
            result[key] = value
        }
        return result
    }

    private fun readRequest(socket: Socket, input: InputStream): HttpRequest? {
        val headerBytes = readHeaderBytes(input) ?: return null
        val headerText = String(headerBytes, StandardCharsets.ISO_8859_1)
        val lines = headerText.split("\r\n")
        val requestLine = lines.firstOrNull()?.trim() ?: return null
        val parts = requestLine.split(" ")
        val method = parts.getOrNull(0)?.uppercase(Locale.US) ?: return null
        val rawTarget = parts.getOrNull(1) ?: "/"
        val targetParts = rawTarget.split("?", limit = 2)
        val path = targetParts[0].ifBlank { "/" }
        val query = mutableMapOf<String, String>()
        val queryText = if (targetParts.size > 1) targetParts[1] else ""
        if (queryText.isNotEmpty()) {
            for (segment in queryText.split("&")) {
                val kv = segment.split("=", limit = 2)
                val key = URLDecoder.decode(kv.getOrNull(0) ?: "", StandardCharsets.UTF_8.name())
                val value = if (kv.size > 1) URLDecoder.decode(kv[1], StandardCharsets.UTF_8.name()) else ""
                query[key] = value
            }
        }

        val headers = mutableMapOf<String, String>()
        for (line in lines.drop(1)) {
            if (line.isEmpty()) continue
            val idx = line.indexOf(':')
            if (idx > 0) {
                headers[line.substring(0, idx).trim().lowercase(Locale.US)] = line.substring(idx + 1).trim()
            }
        }

        val contentLength = headers["content-length"]?.toLongOrNull() ?: 0L
        socket.soTimeout = when {
            contentLength <= 0L -> WEBUI_HEADER_READ_TIMEOUT_MS.toInt()
            method == "POST" && path == "/api/upload" -> WEBUI_UPLOAD_BODY_READ_TIMEOUT_MS.toInt()
            else -> WEBUI_BODY_READ_TIMEOUT_MS.toInt()
        }
        val bodyFile = if (contentLength > 0L) {
            val tempFile = File.createTempFile("platalea-web-body-", ".bin")
            val output = FileOutputStream(tempFile)
            output.use { stream ->
                val chunk = ByteArray(4096)
                var remaining = contentLength
                while (remaining > 0L) {
                    val read = input.read(chunk, 0, minOf(chunk.size.toLong(), remaining).toInt())
                    if (read <= 0) break
                    stream.write(chunk, 0, read)
                    remaining -= read.toLong()
                }
            }
            tempFile
        } else {
            null
        }

        val body = if (bodyFile != null && bodyFile.length() <= MAX_IN_MEMORY_BODY_BYTES) {
            bodyFile.readBytes()
        } else if (bodyFile != null) {
            byteArrayOf()
        } else {
            byteArrayOf()
        }
        return HttpRequest(method, path, query, headers, body, bodyFile)
    }

    private fun readHeaderBytes(input: InputStream): ByteArray? {
        val buffer = ByteArrayOutputStream()
        val marker = "\r\n\r\n".toByteArray(StandardCharsets.ISO_8859_1)
        val tmp = ByteArray(1)
        while (true) {
            val read = input.read(tmp)
            if (read <= 0) return null
            buffer.write(tmp, 0, read)
            if (buffer.size() >= marker.size) {
                val bytes = buffer.toByteArray()
                if (bytes.size >= marker.size && bytes.copyOfRange(bytes.size - marker.size, bytes.size).contentEquals(marker)) {
                    return bytes
                }
            }
            if (buffer.size() > 64 * 1024) return null
        }
    }

    private fun resolveDocument(pathValue: String): DocumentFile? {
        val normalized = normalizePath(pathValue)
        if (normalized == "/") return rootDoc
        var cursor: DocumentFile = rootDoc
        for (segment in normalized.removePrefix("/").split("/").filter { it.isNotEmpty() }) {
            cursor = cursor.listFilesSafe().find { (it.name ?: "") == segment } ?: return null
        }
        return cursor
    }

    private fun normalizePath(pathValue: String): String {
        val raw = (pathValue.takeIf { it.isNotBlank() } ?: "/").replace("\\", "/")
        val segments = mutableListOf<String>()
        for (part in raw.split('/')) {
            when {
                part.isEmpty() || part == "." -> Unit
                part == ".." -> if (segments.isNotEmpty()) segments.removeAt(segments.lastIndex)
                else -> segments.add(part)
            }
        }
        return if (segments.isEmpty()) "/" else "/${segments.joinToString("/")}" 
    }

    private fun listEntries(doc: DocumentFile, currentPath: String): List<Map<String, Any>> {
        if (!doc.isDirectory) return emptyList()
        return doc.listFilesSafe().sortedBy { it.name?.lowercase(Locale.US) ?: "" }.map { child ->
            val childName = child.name ?: ""
            mapOf(
                "name" to childName,
                "type" to if (child.isDirectory) "directory" else "file",
                "size" to if (child.isFile) child.length() else 0L,
                "path" to if (currentPath == "/") "/$childName" else "$currentPath/$childName"
            )
        }
    }

    private fun readDocumentBytes(doc: DocumentFile): ByteArray {
        val input = context.contentResolver.openInputStream(doc.uri) ?: return byteArrayOf()
        return input.use { stream ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(4096)
            while (true) {
                val read = stream.read(buffer)
                if (read <= 0) break
                output.write(buffer, 0, read)
            }
            output.toByteArray()
        }
    }

    private fun writeBytesToDocument(doc: DocumentFile, bytes: ByteArray): Boolean {
        return try {
            val output = context.contentResolver.openOutputStream(doc.uri, "wt") ?: return false
            output.use { it.write(bytes) }
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun writeUploadedFileToDocument(doc: DocumentFile, uploadedFile: UploadedFile): Boolean {
        return try {
            val output = context.contentResolver.openOutputStream(doc.uri, "wt") ?: return false
            output.use { stream ->
                if (uploadedFile.tempFile != null) {
                    uploadedFile.tempFile.inputStream().buffered().use { it.copyTo(stream) }
                } else {
                    stream.write(uploadedFile.bytes)
                }
            }
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun loadQuickNoteText(): Result<String> {
        return runCatching {
            val noteDoc = ensureQuickNoteFile()
                ?: throw IllegalStateException("quick note file unavailable")
            val input = context.contentResolver.openInputStream(noteDoc.uri)
                ?: throw IllegalStateException("open quick note failed")
            input.use { String(it.readBytes(), Charsets.UTF_8) }
        }
    }

    private data class ClipboardSnapshot(
        val text: String?,
        val mimeType: String?,
        val label: String?,
        val hasText: Boolean,
        val updatedAtMs: Long
    )

    private fun readClipboardText(): Result<ClipboardSnapshot> {
        return runCatching {
            val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                ?: throw IllegalStateException("clipboard unavailable")
            val clip = clipboardManager.primaryClip ?: throw IllegalStateException("clipboard empty")
            if (clip.itemCount <= 0) throw IllegalStateException("clipboard empty")
            val description = clip.description
            val text = clip.getItemAt(0).coerceToText(context)?.toString()
            ClipboardSnapshot(
                text = text?.takeIf { it.isNotBlank() },
                mimeType = if (description.mimeTypeCount > 0) description.getMimeType(0) else null,
                label = description.label?.toString(),
                hasText = !text.isNullOrBlank(),
                updatedAtMs = System.currentTimeMillis()
            )
        }
    }

    private fun writeClipboardText(text: String): Result<Unit> {
        return runCatching {
            val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                ?: throw IllegalStateException("clipboard unavailable")
            clipboardManager.setPrimaryClip(ClipData.newPlainText("WebUI Clipboard", text))
        }
    }

    private fun saveQuickNoteText(text: String): Result<Unit> {
        return runCatching {
            val noteDoc = ensureQuickNoteFile()
                ?: throw IllegalStateException("quick note file unavailable")
            val output = context.contentResolver.openOutputStream(noteDoc.uri, "wt")
                ?: throw IllegalStateException("open quick note output failed")
            output.use { it.write(text.toByteArray(Charsets.UTF_8)) }
        }
    }

    private fun ensureQuickNoteFile(): DocumentFile? {
        val existing = rootDoc.listFilesSafe().find { it.name == WEBUI_QUICK_NOTE_FILE && it.isFile }
        if (existing != null) return existing
        return rootDoc.createFile("text/markdown", WEBUI_QUICK_NOTE_FILE)
    }

    private fun readWebUiConfig(): Result<Map<String, Any>> {
        return runCatching {
            val prefs = Preferences(context)
            runBlocking {
                mapOf(
                    "ftp_port" to prefs.ftpPort.first(),
                    "ftp_password" to (prefs.ftpPassword.first() ?: ""),
                    "ftp_timeout_minutes" to prefs.ftpTimeoutMinutes.first(),
                    "local_network_service_enabled" to prefs.localNetworkServiceEnabled.first(),
                    "filter_visible" to prefs.filterVisible.first(),
                    "hide_dot_files" to prefs.hideDotFiles.first()
                )
            }
        }
    }

    private fun updateWebUiConfig(fields: Map<String, String>): Result<Unit> {
        return runCatching {
            val prefs = Preferences(context)
            runBlocking {
                fields["ftp_port"]?.toIntOrNull()?.let { prefs.setFtpPort(it) }
                if (fields.containsKey("ftp_password")) {
                    prefs.setFtpPassword(fields["ftp_password"]?.takeIf { it.isNotBlank() })
                }
                fields["ftp_timeout_minutes"]?.toIntOrNull()?.let { prefs.setFtpTimeoutMinutes(it) }
                fields["local_network_service_enabled"]?.let { prefs.setLocalNetworkServiceEnabled(parseBooleanField(it)) }
                fields["filter_visible"]?.let { prefs.setFilterVisible(parseBooleanField(it)) }
                fields["hide_dot_files"]?.let { prefs.setHideDotFiles(parseBooleanField(it)) }
            }
        }
    }

    private fun exportConfigJson(categories: Set<ConfigExportCategory>): Result<String> {
        return runCatching {
            val prefs = Preferences(context)
            runBlocking {
                exportConfig(context, prefs, categories)
            }
        }
    }

    private fun importConfigJson(json: String, importKeys: Boolean, categories: Set<ConfigExportCategory>): Result<Boolean> {
        return runCatching {
            val prefs = Preferences(context)
            runBlocking {
                importConfig(
                    context = context,
                    prefs = prefs,
                    jsonString = json,
                    importKeys = importKeys,
                    categories = categories
                )
            }
        }
    }

    private fun parseBooleanField(value: String): Boolean {
        val normalized = value.trim().lowercase(Locale.US)
        return normalized == "1" || normalized == "true" || normalized == "yes" || normalized == "on"
    }

    private fun parseConfigCategories(raw: String?): Set<ConfigExportCategory> {
        if (raw.isNullOrBlank()) return ConfigExportCategory.entries.toSet()
        val tokens = raw.split(',').map { it.trim().uppercase(Locale.US) }.filter { it.isNotEmpty() }
        val picked = buildSet {
            for (token in tokens) {
                val matched = ConfigExportCategory.entries.firstOrNull { it.name == token }
                if (matched != null) add(matched)
            }
        }
        return if (picked.isEmpty()) ConfigExportCategory.entries.toSet() else picked
    }

    private fun parseUploadPaths(raw: String): List<String> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (index in 0 until arr.length()) {
                    val value = arr.optString(index, "")
                    if (value.isNotBlank()) add(value)
                }
            }
        }.getOrElse { emptyList() }
    }

    private fun findUploadConflictPaths(parentDoc: DocumentFile, relativePaths: List<String>, uploadType: String, overwrite: Boolean): List<String> {
        if (overwrite) return emptyList()
        val result = mutableListOf<String>()
        for (relativePath in relativePaths) {
            val sanitized = normalizeUploadRelativePath(relativePath)
            if (sanitized.isBlank()) continue
            val segments = sanitized.split('/').filter { it.isNotBlank() }
            if (segments.isEmpty()) continue
            val conflictPath = if (uploadType == "dir" && segments.size > 1) {
                segments.first()
            } else {
                sanitized
            }
            if (hasExistingTarget(parentDoc, conflictPath)) {
                result.add(conflictPath)
            }
        }
        return result.distinct()
    }

    private fun hasExistingTarget(parentDoc: DocumentFile, relativePath: String): Boolean {
        val sanitized = normalizeUploadRelativePath(relativePath)
        if (sanitized.isBlank()) return false
        val segments = sanitized.split('/').filter { it.isNotBlank() }
        if (segments.isEmpty()) return false
        var cursor = parentDoc
        for ((index, segment) in segments.withIndex()) {
            val child = cursor.listFilesSafe().find { it.name == segment } ?: return false
            if (index == segments.lastIndex) return true
            if (!child.isDirectory) return false
            cursor = child
        }
        return false
    }

    private fun uploadRootDirectoryName(relativePath: String): String? {
        val sanitized = normalizeUploadRelativePath(relativePath)
        if (sanitized.isBlank()) return null
        val segments = sanitized.split('/').filter { it.isNotBlank() }
        if (segments.size < 2) return null
        return segments.first()
    }

    private fun createUploadTarget(parentDoc: DocumentFile, relativePath: String, overwrite: Boolean): DocumentFile? {
        val sanitized = normalizeUploadRelativePath(relativePath)
        if (sanitized.isBlank()) return null
        val fileName = sanitized.substringAfterLast('/')
        if (fileName.isBlank()) return null
        val dirSegments = sanitized.substringBeforeLast('/', missingDelimiterValue = "").split("/").filter { it.isNotEmpty() }
        var cursor = parentDoc
        for (segment in dirSegments) {
            val next = cursor.listFilesSafe().find { it.name == segment }
            if (next != null) {
                if (!next.isDirectory) return null
                cursor = next
                continue
            }
            val created = cursor.createDirectory(segment) ?: return null
            cursor = created
        }
        val existing = cursor.listFilesSafe().find { it.name == fileName }
        if (existing != null) {
            if (existing.isDirectory) return null
            if (!overwrite) return null
            if (!existing.delete()) return null
        }
        return cursor.createFile("application/octet-stream", fileName)
    }


    private fun zipDirectory(dir: DocumentFile): File {
        val tempRoot = File.createTempFile("platalea-web-", "")
        tempRoot.delete()
        tempRoot.mkdirs()
        val archiveDir = File(tempRoot, dir.name ?: "download")
        archiveDir.mkdirs()
        copyTree(dir, archiveDir)
        val zipFile = File.createTempFile("platalea-web-", ".zip")
        val zip = ZipFile(zipFile)
        zip.addFolder(archiveDir)
        tempRoot.deleteRecursively()
        return zipFile
    }

    private fun copyTree(source: DocumentFile, destination: File) {
        for (child in source.listFilesSafe()) {
            val target = File(destination, child.name ?: "")
            if (child.isDirectory) {
                target.mkdirs()
                copyTree(child, target)
            } else {
                target.parentFile?.mkdirs()
                val output = FileOutputStream(target)
                context.contentResolver.openInputStream(child.uri)?.use { input ->
                    input.copyTo(output)
                }
                output.close()
            }
        }
    }

    private fun writeJson(output: OutputStream, status: Int, payload: Map<String, Any?>) {
        val jsonObject = mapToJsonObject(payload)
        writeResponse(
            output,
            status,
            "application/json; charset=utf-8",
            jsonObject.toString().toByteArray(StandardCharsets.UTF_8),
            emptyMap()
        )
    }

    private fun mapToJsonObject(value: Map<*, *>): JSONObject {
        val obj = JSONObject()
        for ((key, entry) in value) {
            if (key == null) continue
            obj.put(key.toString(), toJsonCompatible(entry))
        }
        return obj
    }

    private fun listToJsonArray(value: List<*>): JSONArray {
        val arr = JSONArray()
        for (entry in value) {
            arr.put(toJsonCompatible(entry))
        }
        return arr
    }

    private fun toJsonCompatible(value: Any?): Any? {
        return when (value) {
            null -> JSONObject.NULL
            is Map<*, *> -> mapToJsonObject(value)
            is List<*> -> listToJsonArray(value)
            is Number, is Boolean, is String -> value
            else -> value.toString()
        }
    }

    private fun writeResponse(output: OutputStream, status: Int, contentType: String, body: ByteArray, extraHeaders: Map<String, String>) {
        val reason = when (status) {
            200 -> "OK"
            400 -> "Bad Request"
            401 -> "Unauthorized"
            404 -> "Not Found"
            else -> "OK"
        }
        val header = buildString {
            append("HTTP/1.1 $status $reason\r\n")
            append("Content-Type: $contentType\r\n")
            append("Content-Length: ${body.size}\r\n")
            append("Connection: close\r\n")
            for ((key, value) in extraHeaders) {
                append("$key: $value\r\n")
            }
            append("\r\n")
        }
        output.write(header.toByteArray(StandardCharsets.ISO_8859_1))
        output.write(body)
        output.flush()
    }

    private fun guessMimeType(name: String): String = when {
        name.lowercase(Locale.US).endsWith(".html") -> "text/html; charset=utf-8"
        name.lowercase(Locale.US).endsWith(".json") -> "application/json; charset=utf-8"
        name.lowercase(Locale.US).endsWith(".txt") -> "text/plain; charset=utf-8"
        name.lowercase(Locale.US).endsWith(".zip") -> "application/zip"
        name.lowercase(Locale.US).endsWith(".png") -> "image/png"
        name.lowercase(Locale.US).endsWith(".jpg") || name.lowercase(Locale.US).endsWith(".jpeg") -> "image/jpeg"
        else -> "application/octet-stream"
    }

        private fun buildIndexPage(): String = """
<!doctype html>
<html lang="zh-CN">
  <head>
    <meta charset="utf-8"> 
    <meta name="viewport" content="width=device-width, initial-scale=1">
        <title>Platalea WebUI</title>
    <style>
      body { font-family: system-ui, sans-serif; background: #0f172a; color: #e2e8f0; margin: 0; }
      .container { max-width: 1024px; margin: 24px auto; padding: 0 16px; }
      .card { background: rgba(15, 23, 42, 0.8); border: 1px solid rgba(148, 163, 184, 0.3); border-radius: 12px; padding: 16px; margin-bottom: 16px; }
      .row { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; margin: 8px 0; }
            input, button, textarea { border-radius: 8px; border: 1px solid #475569; background: #0b1220; color: #e2e8f0; padding: 8px 10px; }
      button { cursor: pointer; }
      table { width: 100%; border-collapse: collapse; }
      th, td { padding: 10px; border-bottom: 1px solid rgba(148, 163, 184, 0.2); text-align: left; }
      .muted { opacity: 0.8; }
      code { font-family: monospace; }
            .name-link { color: #93c5fd; text-decoration: none; cursor: pointer; }
            .name-link:hover { text-decoration: underline; }
            .op-link { color: #a7f3d0; text-decoration: none; cursor: pointer; }
            .op-link:hover { text-decoration: underline; }
            .secret-mask { -webkit-text-security: disc; }
            .status { margin-top: 10px; color: #cbd5e1; font-size: 13px; }
            .progress-wrap { margin-top: 8px; width: 100%; height: 8px; background: rgba(148, 163, 184, 0.2); border-radius: 999px; overflow: hidden; display: none; }
            .progress-bar { height: 100%; width: 0%; background: linear-gradient(90deg, #38bdf8, #22d3ee); transition: width 0.12s linear; }
            .app-header { display: flex; justify-content: space-between; align-items: center; gap: 12px; margin-bottom: 12px; }
            .tabbar { display: flex; gap: 6px; padding: 6px; border: 1px solid rgba(148, 163, 184, 0.35); border-radius: 12px; background: rgba(15, 23, 42, 0.45); }
            .tab-button { border-radius: 10px 10px 6px 6px; border-color: transparent; background: transparent; color: #cbd5e1; font-weight: 600; }
            .tab-button:hover { border-color: rgba(125, 211, 252, 0.35); color: #e2e8f0; }
            .tab-button.active { border-color: #38bdf8; background: rgba(56, 189, 248, 0.18); color: #7dd3fc; box-shadow: inset 0 -2px 0 rgba(34, 211, 238, 0.75); }
            .panel { display: none; }
            .panel.active { display: block; }
            .note-editor { width: 100%; min-height: 360px; resize: vertical; line-height: 1.5; font-family: ui-monospace, SFMono-Regular, Menlo, monospace; }
            .note-toolbar { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; margin: 8px 0; }
            .small { font-size: 12px; opacity: 0.85; }
            .overview-list { margin: 10px 0; padding-left: 18px; color: #cbd5e1; line-height: 1.6; }
            .overview-list li { margin-bottom: 4px; }
            .overview-meta { margin-top: 8px; font-size: 12px; color: #94a3b8; }
            .config-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
            .config-item { display: flex; flex-direction: column; gap: 6px; }
            .config-item label { font-size: 12px; opacity: 0.85; }
            .config-json { width: 100%; min-height: 220px; resize: vertical; font-family: ui-monospace, SFMono-Regular, Menlo, monospace; }
            @media (max-width: 768px) {
                .config-grid { grid-template-columns: 1fr; }
                .note-toolbar button { flex: 1 1 auto; }
                .note-toolbar .small { width: 100%; }
                .tabbar { width: 100%; overflow-x: auto; }
            }
    </style>
  </head>
  <body>
    <div class="container">
      <div id="loginBox" class="card">
                <h2>WebUI 连接</h2>
        <div class="row">
                    <input id="accessCode" class="secret-mask" type="text" inputmode="text" autocapitalize="off" autocorrect="off" spellcheck="false" autocomplete="off" name="access_code" placeholder="访问密钥">
                    <button id="connectBtn" type="button" autocomplete="off">连接</button>
        </div>
                <div class="muted">登录后可在 WebUI 中切换文件共享与快速笔记。</div>
      </div>

      <div id="appBox" class="card" style="display:none;">
                <div class="app-header">
                    <div class="tabbar">
                        <button id="tab-overview" class="tab-button active" type="button">概览</button>
                        <button id="tab-files" class="tab-button" type="button">文件共享</button>
                        <button id="tab-clipboard" class="tab-button" type="button">剪贴板</button>
                        <button id="tab-note" class="tab-button" type="button">.lm.note.md 编辑</button>
                        <button id="tab-config" class="tab-button" type="button">配置</button>
                    </div>
                    <button id="logoutBtn" type="button">退出登录</button>
        </div>

                <div id="panel-overview" class="panel active">
                    <h3 style="margin: 4px 0 6px;">欢迎使用 Platalea WebUI</h3>
                    <ul class="overview-list">
                        <li>点击“文件共享”进入目录浏览并按需加载。</li>
                        <li>点击“剪贴板”可读取或写回手机剪贴板文本。</li>
                        <li>点击“.lm.note.md 编辑”可管理根目录快速笔记。</li>
                        <li>点击“配置”可读取、保存、导入导出服务设置。</li>
                    </ul>
                    <div class="row">
                        <button id="overviewOpenFilesBtn" type="button">进入文件共享</button>
                        <button id="overviewOpenClipboardBtn" type="button">进入剪贴板</button>
                    </div>
                    <div id="overviewEndpoint" class="overview-meta"></div>
                </div>

                <div id="panel-files" class="panel">
                    <div class="row">
                        <button id="backBtn">返回上级</button>
                        <button id="refreshBtn">刷新</button>
                        <input id="newDirName" placeholder="新目录名">
                        <button id="mkdirBtn">新建目录</button>
                    </div>
                    <div class="row">
                        <button id="pickFilesBtn" type="button">上传文件</button>
                        <button id="pickDirBtn" type="button">上传目录</button>
                        <input id="uploadFileInput" type="file" multiple style="display:none">
                        <input id="uploadDirInput" type="file" webkitdirectory directory multiple style="display:none">
                    </div>
                    <div id="statusText" class="status">准备就绪</div>
                    <div id="progressWrap" class="progress-wrap"><div id="progressBar" class="progress-bar"></div></div>
                    <div class="row"><code id="currentPath">/</code></div>
                    <table>
                        <thead><tr><th>名称</th><th>类型</th><th>大小</th><th>操作</th></tr></thead>
                        <tbody id="entries"></tbody>
                    </table>
        </div>

                <div id="panel-clipboard" class="panel">
                    <div class="note-toolbar">
                        <button id="clipboardReloadBtn" type="button">重新读取</button>
                        <button id="clipboardSaveBtn" type="button">写回手机</button>
                        <span id="clipboardStatus" class="small">未加载</span>
                    </div>
                    <div id="clipboardMeta" class="small muted">等待读取剪贴板内容</div>
                    <textarea id="clipboardText" class="note-editor" placeholder="可编辑：粘贴或输入后，点击“写回手机”同步到手机剪贴板"></textarea>
                </div>

                <div id="panel-note" class="panel">
                    <div class="note-toolbar">
                        <button id="noteReloadBtn" type="button">重新读取</button>
                        <button id="noteSaveBtn" type="button">保存笔记</button>
                        <span id="noteStatus" class="small">未加载</span>
                    </div>
                    <textarea id="noteEditor" class="note-editor" placeholder="这里编辑 .lm.note.md 的内容"></textarea>
                </div>

                <div id="panel-config" class="panel">
                    <div class="note-toolbar">
                        <button id="configLoadBtn" type="button">读取配置</button>
                        <button id="configSaveBtn" type="button">保存配置</button>
                        <button id="configExportBtn" type="button">导出配置 JSON</button>
                        <button id="configImportBtn" type="button">导入配置 JSON</button>
                        <span id="configStatus" class="small">未加载</span>
                    </div>
                    <div class="config-grid">
                        <div class="config-item">
                            <label>FTP 端口</label>
                            <input id="cfgFtpPort" type="number" min="1024" max="65535" placeholder="2121">
                        </div>
                        <div class="config-item">
                            <label>FTP 密码（留空表示不改）</label>
                            <input id="cfgFtpPassword" class="secret-mask" type="text" placeholder="输入新密码或留空">
                        </div>
                        <div class="config-item">
                            <label>自动停止分钟数</label>
                            <input id="cfgTimeout" type="number" min="0" max="1440" placeholder="0">
                        </div>
                        <div class="config-item">
                            <label>本地网络服务开关（true/false）</label>
                            <input id="cfgLocalService" type="text" placeholder="true">
                        </div>
                        <div class="config-item">
                            <label>显示过滤栏（true/false）</label>
                            <input id="cfgFilterVisible" type="text" placeholder="true">
                        </div>
                        <div class="config-item">
                            <label>隐藏点文件（true/false）</label>
                            <input id="cfgHideDot" type="text" placeholder="false">
                        </div>
                    </div>
                    <div class="row"><span class="small">导入导出 JSON（支持完整配置）</span></div>
                    <textarea id="configJsonEditor" class="config-json" placeholder="配置 JSON 会显示在这里"></textarea>
                </div>
      </div>
    </div>

    <script>
            const state = { token: '', currentPath: '/', activeTab: 'overview' };
      const loginBox = document.getElementById('loginBox');
      const appBox = document.getElementById('appBox');
      const currentPathEl = document.getElementById('currentPath');
      const entriesEl = document.getElementById('entries');
            const accessCodeEl = document.getElementById('accessCode');
            const uploadFileInput = document.getElementById('uploadFileInput');
            const uploadDirInput = document.getElementById('uploadDirInput');
            const pickFilesBtn = document.getElementById('pickFilesBtn');
            const pickDirBtn = document.getElementById('pickDirBtn');
            const statusText = document.getElementById('statusText');
            const progressWrap = document.getElementById('progressWrap');
            const progressBar = document.getElementById('progressBar');
            const tabOverviewBtn = document.getElementById('tab-overview');
            const tabFilesBtn = document.getElementById('tab-files');
            const tabClipboardBtn = document.getElementById('tab-clipboard');
            const tabNoteBtn = document.getElementById('tab-note');
            const tabConfigBtn = document.getElementById('tab-config');
            const panelOverview = document.getElementById('panel-overview');
            const panelFiles = document.getElementById('panel-files');
            const panelClipboard = document.getElementById('panel-clipboard');
            const panelNote = document.getElementById('panel-note');
            const panelConfig = document.getElementById('panel-config');
            const overviewEndpoint = document.getElementById('overviewEndpoint');
            const noteEditor = document.getElementById('noteEditor');
            const noteStatus = document.getElementById('noteStatus');
            const clipboardText = document.getElementById('clipboardText');
            const clipboardMeta = document.getElementById('clipboardMeta');
            const clipboardStatus = document.getElementById('clipboardStatus');
            const configStatus = document.getElementById('configStatus');
            const cfgFtpPort = document.getElementById('cfgFtpPort');
            const cfgFtpPassword = document.getElementById('cfgFtpPassword');
            const cfgTimeout = document.getElementById('cfgTimeout');
            const cfgLocalService = document.getElementById('cfgLocalService');
            const cfgFilterVisible = document.getElementById('cfgFilterVisible');
            const cfgHideDot = document.getElementById('cfgHideDot');
            const configJsonEditor = document.getElementById('configJsonEditor');
            let busy = false;
            let pendingOverwriteMode = false;
            let noteLoadedOnce = false;
            let configLoadedOnce = false;
            let filesLoadedOnce = false;

            overviewEndpoint.textContent = '当前访问地址: ' + window.location.origin;

            function setBusy(isBusy) {
                busy = isBusy;
                const disabled = !!isBusy;
                document.getElementById('backBtn').disabled = disabled;
                document.getElementById('refreshBtn').disabled = disabled;
                document.getElementById('mkdirBtn').disabled = disabled;
                pickFilesBtn.disabled = disabled;
                pickDirBtn.disabled = disabled;
            }

            function setStatus(text) {
                statusText.textContent = text;
            }

            function showProgress(percent) {
                progressWrap.style.display = 'block';
                progressBar.style.width = Math.max(0, Math.min(100, percent)).toFixed(1) + '%';
            }

            function hideProgress() {
                progressWrap.style.display = 'none';
                progressBar.style.width = '0%';
            }

            function formatDuration(ms) {
                const totalSeconds = Math.max(0, Math.ceil(ms / 1000));
                const minutes = Math.floor(totalSeconds / 60);
                const seconds = totalSeconds % 60;
                return minutes > 0 ? minutes + 'm ' + seconds + 's' : seconds + 's';
            }

            function timeDrivenPercent(startTimeMs, maxPercent = 98) {
                const elapsed = Math.max(0, performance.now() - startTimeMs);
                const ratio = elapsed / 15000;
                return Math.min(maxPercent, ratio * 100);
            }

            function normalizeErrorText(error) {
                return (error && error.message) ? error.message : 'unknown';
            }

            function extractUploadPaths(files) {
                return files.map((file) => file.webkitRelativePath || file.name).filter((path) => !!path);
            }

            function precheckUploadConflict(paths, sourceType, overwriteMode) {
                const form = new URLSearchParams();
                form.set('paths', JSON.stringify(paths));
                return fetch('/api/upload/precheck?path=' + encodeURIComponent(state.currentPath) + '&type=' + encodeURIComponent(sourceType) + (overwriteMode ? '&overwrite=1' : ''), {
                    method: 'POST',
                    headers: authHeaders({ 'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8' }),
                    body: form.toString()
                }).then(async (response) => {
                    const text = await response.text();
                    const payload = text ? JSON.parse(text) : {};
                    if (!response.ok) {
                        throw new Error(payload.error || 'precheck_failed');
                    }
                    return payload;
                });
            }

            function switchTab(tabName) {
                const previousTab = state.activeTab;
                state.activeTab = tabName;
                const isOverview = tabName === 'overview';
                const isFiles = tabName === 'files';
                const isClipboard = tabName === 'clipboard';
                const isNote = tabName === 'note';
                const isConfig = tabName === 'config';
                tabOverviewBtn.classList.toggle('active', isOverview);
                tabFilesBtn.classList.toggle('active', isFiles);
                tabClipboardBtn.classList.toggle('active', isClipboard);
                tabNoteBtn.classList.toggle('active', isNote);
                tabConfigBtn.classList.toggle('active', isConfig);
                panelOverview.classList.toggle('active', isOverview);
                panelFiles.classList.toggle('active', isFiles);
                panelClipboard.classList.toggle('active', isClipboard);
                panelNote.classList.toggle('active', isNote);
                panelConfig.classList.toggle('active', isConfig);
                if (isFiles && (previousTab !== 'files' || !filesLoadedOnce)) {
                    filesLoadedOnce = true;
                    loadList();
                }
                if (isClipboard) {
                    loadClipboardText();
                }
                if (isNote && !noteLoadedOnce) {
                    loadQuickNote();
                }
                if (isConfig && !configLoadedOnce) {
                    loadConfig();
                }
            }

            function authHeaders(extra = {}) {
                const headers = Object.assign({}, extra);
                if (state.token) {
                    headers['X-Auth-Token'] = state.token;
                }
                return headers;
      }

      function requestJson(url, options = {}) {
                const headers = authHeaders(options.headers || {});
        return fetch(url, Object.assign({}, options, { headers })).then(async (response) => {
          const text = await response.text();
                    const payload = text ? JSON.parse(text) : {};
          if (!response.ok) throw new Error(payload.error || 'request_failed');
          return payload;
        });
      }
      function formatSize(size) {
        if (size <= 0) return '0 B';
        const units = ['B', 'KB', 'MB', 'GB'];
        let value = size;
        let unitIndex = 0;
        while (value >= 1024 && unitIndex < units.length - 1) {
          value /= 1024;
          unitIndex += 1;
        }
        return value.toFixed(1) + ' ' + units[unitIndex];
      }

      function loadList() {
                if (busy) return;
                setBusy(true);
            setStatus('正在加载目录...');
                showProgress(10);
        requestJson('/api/list?path=' + encodeURIComponent(state.currentPath), { method: 'GET' })
          .then((data) => {
                        showProgress(80);
            state.currentPath = data.path || '/';
            currentPathEl.textContent = state.currentPath;
            entriesEl.innerHTML = '';
            for (const entry of data.entries || []) {
              const row = document.createElement('tr');

              const nameCell = document.createElement('td');
                            const nameLink = document.createElement('a');
                            nameLink.className = 'name-link';
                            nameLink.href = '#';
                            nameLink.textContent = entry.name;
              if (entry.type === 'directory') {
                                nameLink.onclick = (event) => {
                                    event.preventDefault();
                                    state.currentPath = entry.path || ('/' + entry.name);
                                    loadList();
                                };
                            } else {
                                nameLink.onclick = async (event) => {
                                    event.preventDefault();
                                    const path = entry.path || (state.currentPath === '/' ? '/' + entry.name : state.currentPath + '/' + entry.name);
                                    downloadFromWebUi(path, entry.name);
                                };
              }
                            nameCell.appendChild(nameLink);

              const typeCell = document.createElement('td');
              typeCell.textContent = entry.type === 'directory' ? '目录' : '文件';

              const sizeCell = document.createElement('td');
              sizeCell.textContent = entry.type === 'directory' ? '-' : formatSize(entry.size || 0);

              const actionCell = document.createElement('td');
                            if (entry.type === 'directory') {
                                const openLink = document.createElement('a');
                                openLink.className = 'op-link';
                                openLink.href = '#';
                                openLink.textContent = '进入';
                                openLink.onclick = (event) => {
                                    event.preventDefault();
                                    state.currentPath = entry.path || ('/' + entry.name);
                                    loadList();
                                };

                                const zipLink = document.createElement('a');
                                zipLink.className = 'op-link';
                                zipLink.href = '#';
                                zipLink.textContent = '下载.zip';
                                zipLink.onclick = async (event) => {
                                    event.preventDefault();
                                    const path = entry.path || (state.currentPath === '/' ? '/' + entry.name : state.currentPath + '/' + entry.name);
                                    downloadFromWebUi(path, entry.name + '.zip');
                                };
                                const deleteLink = document.createElement('a');
                                deleteLink.className = 'op-link';
                                deleteLink.href = '#';
                                deleteLink.textContent = '删除';
                                deleteLink.onclick = (event) => {
                                    event.preventDefault();
                                    requestDelete(entry.path || (state.currentPath === '/' ? '/' + entry.name : state.currentPath + '/' + entry.name), entry.name);
                                };
                                actionCell.appendChild(openLink);
                                actionCell.appendChild(document.createTextNode(' | '));
                                actionCell.appendChild(zipLink);
                                actionCell.appendChild(document.createTextNode(' | '));
                                actionCell.appendChild(deleteLink);
                            } else {
                                const deleteLink = document.createElement('a');
                                deleteLink.className = 'op-link';
                                deleteLink.href = '#';
                                deleteLink.textContent = '删除';
                                deleteLink.onclick = (event) => {
                                    event.preventDefault();
                                    requestDelete(entry.path || (state.currentPath === '/' ? '/' + entry.name : state.currentPath + '/' + entry.name), entry.name);
                                };
                                actionCell.appendChild(deleteLink);
                            }

              row.appendChild(nameCell);
              row.appendChild(typeCell);
              row.appendChild(sizeCell);
              row.appendChild(actionCell);
              entriesEl.appendChild(row);
            }
                        const count = (data.entries || []).length;
                        showProgress(100);
                        setStatus('目录加载完成，共 ' + count + ' 项');
                        setTimeout(() => hideProgress(), 150);
                        setBusy(false);
          })
                  .catch((error) => {
                    setBusy(false);
                    hideProgress();
                        if (error && error.message === 'unauthorized') {
                            alert('认证已失效，请重新输入密码登录。');
                            appBox.style.display = 'none';
                            loginBox.style.display = 'block';
                            setStatus('认证已失效');
                            return;
                        }
                        if (error && error.message === 'not_found') {
                            alert('当前目录不存在，可能已被移动或删除。');
                            setStatus('目录不存在');
                            return;
                        }
                        alert('加载目录失败: ' + normalizeErrorText(error));
                        setStatus('目录加载失败');
                    });
      }

            function loadQuickNote() {
                noteStatus.textContent = '正在读取 .lm.note.md ...';
                requestJson('/api/quick-note/load', { method: 'GET' })
                    .then((data) => {
                        noteEditor.value = data.text || '';
                        noteStatus.textContent = '读取完成';
                        noteLoadedOnce = true;
                    })
                    .catch((error) => {
                        noteStatus.textContent = '读取失败: ' + normalizeErrorText(error);
                    });
            }

            function downloadFromWebUi(path, filename) {
                if (busy) return;
                setBusy(true);
                const startedAt = performance.now();
                let lastPublishedPercent = 0;
                setStatus('正在下载 ' + filename + ' ...');
                showProgress(0);

                const xhr = new XMLHttpRequest();
                xhr.open('GET', '/api/download?path=' + encodeURIComponent(path), true);
                xhr.responseType = 'blob';
                const headers = authHeaders();
                for (const key in headers) {
                    xhr.setRequestHeader(key, headers[key]);
                }

                xhr.onprogress = (event) => {
                    const elapsedMs = performance.now() - startedAt;
                    const actualPercent = event.lengthComputable && event.total > 0
                        ? (event.loaded / event.total) * 100
                        : timeDrivenPercent(startedAt, 95);
                    const percent = Math.max(lastPublishedPercent, Math.min(99, actualPercent));
                    lastPublishedPercent = percent;
                    showProgress(percent);
                    setStatus('下载中 ' + percent.toFixed(1) + '%，已用时 ' + formatDuration(elapsedMs));
                };

                xhr.onload = () => {
                    setBusy(false);
                    if (xhr.status >= 200 && xhr.status < 300) {
                        showProgress(100);
                        const blob = xhr.response;
                        const url = URL.createObjectURL(blob);
                        const a = document.createElement('a');
                        a.href = url;
                        a.download = filename;
                        a.click();
                        URL.revokeObjectURL(url);
                        setStatus('下载完成');
                        setTimeout(() => hideProgress(), 250);
                    } else {
                        hideProgress();
                        setStatus('下载失败');
                        alert('下载失败: ' + xhr.status);
                    }
                };

                xhr.onerror = () => {
                    setBusy(false);
                    hideProgress();
                    setStatus('下载失败');
                    alert('下载失败: 网络异常');
                };

                xhr.send();
            }

            function loadClipboardText() {
                clipboardStatus.textContent = '正在读取手机剪贴板...';
                requestJson('/api/clipboard/text', { method: 'GET' })
                    .then((data) => {
                        clipboardText.value = data.hasText ? (data.text || '') : '';
                        const parts = [];
                        parts.push(data.hasText ? '文本' : '空剪贴板');
                        if (data.mimeType) parts.push(data.mimeType);
                        if (data.label) parts.push(data.label);
                        clipboardMeta.textContent = parts.join(' · ');
                        clipboardStatus.textContent = '读取完成';
                    })
                    .catch((error) => {
                        clipboardStatus.textContent = '读取失败: ' + normalizeErrorText(error);
                    });
            }

            function saveClipboardText() {
                const text = clipboardText.value || '';
                clipboardStatus.textContent = '正在写回手机剪贴板...';
                const form = new URLSearchParams();
                form.set('text', text);
                fetch('/api/clipboard/text', {
                    method: 'POST',
                    headers: authHeaders({ 'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8' }),
                    body: form.toString()
                }).then(async (response) => {
                    const bodyText = await response.text();
                    const payload = bodyText ? JSON.parse(bodyText) : {};
                    if (!response.ok || !payload.ok) {
                        throw new Error(payload.error || 'clipboard_save_failed');
                    }
                    clipboardStatus.textContent = '写回完成，长度 ' + (payload.length || 0);
                }).catch((error) => {
                    clipboardStatus.textContent = '写回失败: ' + normalizeErrorText(error);
                });
            }

            function saveQuickNote() {
                noteStatus.textContent = '保存中...';
                const form = new URLSearchParams();
                form.set('text', noteEditor.value || '');
                fetch('/api/quick-note/save', {
                    method: 'POST',
                    headers: authHeaders({ 'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8' }),
                    body: form.toString()
                }).then(async (response) => {
                    const text = await response.text();
                    const payload = text ? JSON.parse(text) : {};
                    if (!response.ok || !payload.ok) {
                        throw new Error(payload.error || 'save_failed');
                    }
                    noteStatus.textContent = '保存成功，长度 ' + (payload.length || 0);
                }).catch((error) => {
                    noteStatus.textContent = '保存失败: ' + normalizeErrorText(error);
                });
            }

            function loadConfig() {
                configStatus.textContent = '正在读取配置...';
                requestJson('/api/config/get', { method: 'GET' })
                    .then((data) => {
                        const cfg = data.config || {};
                        cfgFtpPort.value = (cfg.ftp_port ?? '').toString();
                        cfgFtpPassword.value = '';
                        cfgTimeout.value = (cfg.ftp_timeout_minutes ?? '').toString();
                        cfgLocalService.value = String(!!cfg.local_network_service_enabled);
                        cfgFilterVisible.value = String(!!cfg.filter_visible);
                        cfgHideDot.value = String(!!cfg.hide_dot_files);
                        configStatus.textContent = '配置读取完成';
                        configLoadedOnce = true;
                    })
                    .catch((error) => {
                        configStatus.textContent = '读取失败: ' + normalizeErrorText(error);
                    });
            }

            function saveConfig() {
                configStatus.textContent = '正在保存配置...';
                const form = new URLSearchParams();
                if (cfgFtpPort.value.trim()) form.set('ftp_port', cfgFtpPort.value.trim());
                form.set('ftp_password', cfgFtpPassword.value || '');
                if (cfgTimeout.value.trim()) form.set('ftp_timeout_minutes', cfgTimeout.value.trim());
                if (cfgLocalService.value.trim()) form.set('local_network_service_enabled', cfgLocalService.value.trim());
                if (cfgFilterVisible.value.trim()) form.set('filter_visible', cfgFilterVisible.value.trim());
                if (cfgHideDot.value.trim()) form.set('hide_dot_files', cfgHideDot.value.trim());
                fetch('/api/config/set', {
                    method: 'POST',
                    headers: authHeaders({ 'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8' }),
                    body: form.toString()
                }).then(async (response) => {
                    const text = await response.text();
                    const payload = text ? JSON.parse(text) : {};
                    if (!response.ok || !payload.ok) {
                        throw new Error(payload.error || 'config_set_failed');
                    }
                    cfgFtpPassword.value = '';
                    configStatus.textContent = '配置保存成功';
                }).catch((error) => {
                    configStatus.textContent = '保存失败: ' + normalizeErrorText(error);
                });
            }

            function exportConfigJson() {
                configStatus.textContent = '正在导出配置...';
                requestJson('/api/config/export', { method: 'GET' })
                    .then((data) => {
                        configJsonEditor.value = data.json || '';
                        configStatus.textContent = '配置已导出到下方 JSON 编辑框';
                    })
                    .catch((error) => {
                        configStatus.textContent = '导出失败: ' + normalizeErrorText(error);
                    });
            }

            function importConfigJson() {
                const jsonText = configJsonEditor.value || '';
                if (!jsonText.trim()) {
                    alert('请先粘贴配置 JSON');
                    return;
                }
                const importKeys = confirm('是否同时导入 GPG 密钥数据？\n选择“取消”将跳过密钥，仅导入其余配置。');
                configStatus.textContent = '正在导入配置...';
                const form = new URLSearchParams();
                form.set('json', jsonText);
                form.set('import_keys', importKeys ? '1' : '0');
                fetch('/api/config/import', {
                    method: 'POST',
                    headers: authHeaders({ 'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8' }),
                    body: form.toString()
                }).then(async (response) => {
                    const text = await response.text();
                    const payload = text ? JSON.parse(text) : {};
                    if (!response.ok || !payload.ok) {
                        throw new Error(payload.error || 'config_import_failed');
                    }
                    configStatus.textContent = '配置导入成功';
                    loadConfig();
                }).catch((error) => {
                    configStatus.textContent = '导入失败: ' + normalizeErrorText(error);
                });
            }

            function requestDelete(path, name) {
                if (busy) return;
                if (!confirm('确认删除: ' + name + ' ?')) return;
                setBusy(true);
                setStatus('正在删除 ' + name + ' ...');
                requestJson('/api/delete?path=' + encodeURIComponent(path), { method: 'POST' })
                    .then(() => {
                        setBusy(false);
                        setStatus('删除成功，正在刷新目录...');
                        loadList();
                    })
                    .catch((error) => {
                        setBusy(false);
                        alert('删除失败: ' + normalizeErrorText(error));
                        setStatus('删除失败');
                    });
            }

        document.getElementById('connectBtn').onclick = () => {
                if (busy) return;
                const secret = accessCodeEl.value || '';
                setBusy(true);
                setStatus('正在验证密钥...');
                showProgress(20);
                const form = new URLSearchParams();
                form.set('secret', secret);
                fetch('/api/auth', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8' },
                    body: form.toString()
                }).then(async (response) => {
                    const text = await response.text();
                    const payload = text ? JSON.parse(text) : {};
                    if (!response.ok || !payload.token) {
                        throw new Error('login_failed');
                    }
                    showProgress(65);
                    state.token = payload.token;
                    accessCodeEl.value = '';
                    loginBox.style.display = 'none';
                    appBox.style.display = 'block';
                    switchTab('overview');
                    setStatus('登录成功，请选择页面');
                    setBusy(false);
                }).catch(() => {
                    setBusy(false);
                    hideProgress();
                    alert('密码错误或服务不可用。');
                    setStatus('连接失败');
                });
      };

            accessCodeEl.addEventListener('keydown', (event) => {
                if (event.key === 'Enter') {
                    event.preventDefault();
                    document.getElementById('connectBtn').click();
                }
            });

            tabOverviewBtn.onclick = () => switchTab('overview');
            tabFilesBtn.onclick = () => switchTab('files');
            tabClipboardBtn.onclick = () => switchTab('clipboard');
            tabNoteBtn.onclick = () => switchTab('note');
            tabConfigBtn.onclick = () => switchTab('config');
            document.getElementById('overviewOpenFilesBtn').onclick = () => switchTab('files');
            document.getElementById('overviewOpenClipboardBtn').onclick = () => switchTab('clipboard');
            document.getElementById('clipboardReloadBtn').onclick = loadClipboardText;
            document.getElementById('clipboardSaveBtn').onclick = saveClipboardText;
            document.getElementById('noteReloadBtn').onclick = loadQuickNote;
            document.getElementById('noteSaveBtn').onclick = saveQuickNote;
            document.getElementById('configLoadBtn').onclick = loadConfig;
            document.getElementById('configSaveBtn').onclick = saveConfig;
            document.getElementById('configExportBtn').onclick = exportConfigJson;
            document.getElementById('configImportBtn').onclick = importConfigJson;

            document.getElementById('logoutBtn').onclick = () => {
                fetch('/api/logout', {
                    method: 'POST',
                    headers: authHeaders()
                }).finally(() => {
                    state.token = '';
                    noteLoadedOnce = false;
                    configLoadedOnce = false;
                    filesLoadedOnce = false;
                    noteEditor.value = '';
                    noteStatus.textContent = '未加载';
                    configStatus.textContent = '未加载';
                    configJsonEditor.value = '';
                    appBox.style.display = 'none';
                    loginBox.style.display = 'block';
                    hideProgress();
                    setStatus('已退出登录');
                });
            };

      document.getElementById('refreshBtn').onclick = loadList;
      document.getElementById('backBtn').onclick = () => {
                if (busy) return;
        const parts = state.currentPath.split('/').filter(Boolean);
        if (parts.length === 0) return;
        parts.pop();
        state.currentPath = '/' + parts.join('/');
        loadList();
      };

      document.getElementById('mkdirBtn').onclick = () => {
                if (busy) return;
        const name = document.getElementById('newDirName').value.trim();
        if (!name) return;
                setBusy(true);
                setStatus('正在创建目录 ' + name + ' ...');
        requestJson('/api/mkdir?path=' + encodeURIComponent(state.currentPath) + '&name=' + encodeURIComponent(name), { method: 'POST' })
                    .then(() => {
                        document.getElementById('newDirName').value = '';
                        setBusy(false);
                        setStatus('目录已创建，正在刷新...');
                        loadList();
                    })
                    .catch((error) => {
                        setBusy(false);
                        alert('创建目录失败: ' + normalizeErrorText(error));
                        setStatus('创建目录失败');
                    });
      };

            document.getElementById('newDirName').addEventListener('keydown', (event) => {
                if (event.key === 'Enter') {
                    event.preventDefault();
                    document.getElementById('mkdirBtn').click();
                }
            });

            function uploadSelected(files, sourceType, overwriteMode = false) {
                if (busy) return;
                if (!files.length) return;
                setBusy(true);
                const uploadPaths = extractUploadPaths(files);
                const uploadStartedAt = performance.now();
                let transferCompletedAt = 0;
                let lastPublishedPercent = 0;
                setStatus('正在检查是否会覆盖已有内容...');
                showProgress(3);

                precheckUploadConflict(uploadPaths, sourceType, overwriteMode).then((precheck) => {
                    const conflictCount = precheck.conflictCount || 0;
                    const conflictPaths = precheck.conflictPaths || [];
                    if (conflictCount > 0 && !overwriteMode) {
                        setBusy(false);
                        hideProgress();
                        const summary = conflictPaths.length > 0 ? ('\n冲突项：' + conflictPaths.join('\n')) : '';
                        const answer = confirm('检测到 ' + conflictCount + ' 个同名项，上传会覆盖它们。\n是否继续？' + summary);
                        if (answer) {
                            uploadSelected(files, sourceType, true);
                        }
                        return;
                    }

                    pendingOverwriteMode = overwriteMode;
                    const formData = new FormData();
                    for (const file of files) {
                        formData.append('file', file, file.webkitRelativePath || file.name);
                    }

                    setStatus((sourceType === 'dir' ? '目录' : '文件') + '上传中（按传输时间推进）...');
                    showProgress(0);

                    const xhr = new XMLHttpRequest();
                    const overwriteQuery = pendingOverwriteMode ? '&overwrite=1' : '';
                    const typeQuery = '&type=' + encodeURIComponent(sourceType);
                    xhr.open('POST', '/api/upload?path=' + encodeURIComponent(state.currentPath) + overwriteQuery + typeQuery, true);
                    const headers = authHeaders();
                    for (const key in headers) {
                        xhr.setRequestHeader(key, headers[key]);
                    }

                    xhr.upload.onprogress = (event) => {
                        const elapsedMs = performance.now() - uploadStartedAt;
                        const actualPercent = event.lengthComputable && event.total > 0
                            ? (event.loaded / event.total) * 100
                            : timeDrivenPercent(uploadStartedAt, 90);
                        const percent = Math.max(lastPublishedPercent, Math.min(99, actualPercent));
                        lastPublishedPercent = percent;
                        showProgress(percent);
                        setStatus('上传中 ' + percent.toFixed(1) + '%，已用时 ' + formatDuration(elapsedMs));
                    };

                    xhr.upload.onloadend = () => {
                        transferCompletedAt = performance.now();
                        showProgress(99);
                        setStatus('上传已发送完毕，服务器正在写入保存...');
                    };

                    xhr.onload = () => {
                        if (xhr.status >= 200 && xhr.status < 300) {
                            showProgress(100);
                            const serverWaitMs = transferCompletedAt > 0 ? Math.max(0, performance.now() - transferCompletedAt) : 0;
                            setStatus('服务器写入完成，用时 ' + formatDuration(serverWaitMs) + '，正在刷新目录...');
                            let payload = {};
                            try {
                                payload = xhr.responseText ? JSON.parse(xhr.responseText) : {};
                            } catch (_) {
                                payload = {};
                            }
                            const failedCount = payload.failedCount || 0;
                            const okCount = payload.okCount || 0;
                            if (failedCount > 0 || !payload.ok || okCount === 0) {
                                setBusy(false);
                                hideProgress();
                                setStatus('上传失败');
                                alert('上传失败: 服务器写入失败，已写入 ' + okCount + ' 个文件。');
                                return;
                            }
                            const tookMs = payload.tookMs || 0;
                            setStatus('服务器处理完成，用时 ' + tookMs + ' ms，正在刷新目录...');
                            setTimeout(() => hideProgress(), 350);
                            setBusy(false);
                            loadList();
                        } else {
                            setBusy(false);
                            hideProgress();
                            setStatus('上传失败');
                            alert('上传失败: ' + xhr.status);
                        }
                    };
                    xhr.onerror = () => {
                        setBusy(false);
                        hideProgress();
                        setStatus('上传失败');
                        alert('上传失败: 网络异常');
                    };

                    xhr.send(formData);
                }).catch((error) => {
                    setBusy(false);
                    hideProgress();
                    setStatus('上传失败');
                    alert('上传前检查失败: ' + normalizeErrorText(error));
                });
            }

            pickFilesBtn.onclick = () => {
                if (busy) return;
                uploadFileInput.click();
            };

            pickDirBtn.onclick = () => {
                if (busy) return;
                uploadDirInput.click();
            };

            uploadFileInput.onchange = () => {
                const files = Array.from(uploadFileInput.files || []);
                uploadSelected(files, 'file');
                uploadFileInput.value = '';
            };

            uploadDirInput.onchange = () => {
                const files = Array.from(uploadDirInput.files || []);
                uploadSelected(files, 'dir');
                uploadDirInput.value = '';
            };
    </script>
  </body>
</html>
""".trimIndent()
}
