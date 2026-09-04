package com.kenny.localmanager.ftp

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import com.kenny.localmanager.R
import com.kenny.localmanager.file.listFilesSafe
import net.lingala.zip4j.ZipFile
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
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

private data class HttpRequest(
    val method: String,
    val path: String,
    val query: Map<String, String>,
    val headers: Map<String, String>,
    val body: ByteArray
)

private data class UploadedFile(
    val fileName: String,
    val relativePath: String,
    val bytes: ByteArray
)

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
                client.soTimeout = 3000
                if (client is SSLSocket) {
                    client.useClientMode = false
                    client.startHandshake()
                }
                val input = BufferedInputStream(client.getInputStream())
                val output = client.getOutputStream()
                val request = readRequest(input) ?: return

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
                        val files = parseMultipartUploads(request.body, request.headers["content-type"] ?: "")
                        val overwrite = request.query["overwrite"] == "1"
                        val result = mutableListOf<Map<String, String>>()
                        for (file in files) {
                            val destination = createUploadTarget(parent, file.relativePath, overwrite)
                            if (destination != null) {
                                writeBytesToDocument(destination, file.bytes)
                                result.add(mapOf("name" to file.fileName, "path" to file.relativePath, "status" to "ok"))
                            } else {
                                val status = if (!overwrite) "conflict" else "failed"
                                result.add(mapOf("name" to file.fileName, "path" to file.relativePath, "status" to status))
                            }
                        }
                        val okCount = result.count { it["status"] == "ok" }
                        val conflictCount = result.count { it["status"] == "conflict" }
                        val tookMs = System.currentTimeMillis() - startedAt
                        writeJson(output, 200, mapOf(
                            "ok" to true,
                            "files" to result,
                            "okCount" to okCount,
                            "conflictCount" to conflictCount,
                            "tookMs" to tookMs
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

    private fun readRequest(input: InputStream): HttpRequest? {
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
        val body = if (contentLength > 0) {
            val buffer = ByteArrayOutputStream()
            val chunk = ByteArray(4096)
            var remaining = contentLength.toLong()
            while (remaining > 0) {
                val read = input.read(chunk, 0, minOf(chunk.size.toLong(), remaining).toInt())
                if (read <= 0) break
                buffer.write(chunk, 0, read)
                remaining -= read.toLong()
            }
            buffer.toByteArray()
        } else {
            byteArrayOf()
        }
        return HttpRequest(method, path, query, headers, body)
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

    private fun writeBytesToDocument(doc: DocumentFile, bytes: ByteArray) {
        val output = context.contentResolver.openOutputStream(doc.uri, "wt") ?: return
        output.use { it.write(bytes) }
    }

    private fun createUploadTarget(parentDoc: DocumentFile, relativePath: String, overwrite: Boolean): DocumentFile? {
        val fileName = relativePath.substringAfterLast('/')
        val dirSegments = relativePath.substringBeforeLast('/', missingDelimiterValue = "").split("/").filter { it.isNotEmpty() }
        var cursor = parentDoc
        for (segment in dirSegments) {
            val next = cursor.listFilesSafe().find { it.name == segment } ?: cursor.createDirectory(segment) ?: return null
            cursor = next
        }
        val existing = cursor.listFilesSafe().find { it.name == fileName }
        if (existing != null && existing.isFile) {
            if (!overwrite) return null
            existing.delete()
        }
        return cursor.createFile("application/octet-stream", fileName)
    }

    private fun parseMultipartUploads(body: ByteArray, contentType: String): List<UploadedFile> {
        val boundary = contentType.substringAfter("boundary=", "").trim().removePrefix("\"").removeSuffix("\"")
        if (boundary.isBlank()) return emptyList()
        val text = String(body, StandardCharsets.ISO_8859_1)
        val marker = "--$boundary"
        val chunks = text.split(marker)
        val result = mutableListOf<UploadedFile>()
        for (chunk in chunks) {
            if (chunk.isBlank() || chunk.contains("--")) continue
            val headerEnd = chunk.indexOf("\r\n\r\n")
            if (headerEnd < 0) continue
            val headers = chunk.substring(0, headerEnd)
            val payload = chunk.substring(headerEnd + 4).removeSuffix("\r\n")
            val filename = Regex("filename=\"([^\"]+)\"").find(headers)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() } ?: continue
            val field = Regex("name=\"([^\"]+)\"").find(headers)?.groupValues?.getOrNull(1) ?: "file"
            if (field != "file") continue
            result.add(UploadedFile(filename, filename, payload.toByteArray(StandardCharsets.ISO_8859_1)))
        }
        return result
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
    <title>Platalea 本地共享</title>
    <style>
      body { font-family: system-ui, sans-serif; background: #0f172a; color: #e2e8f0; margin: 0; }
      .container { max-width: 1024px; margin: 24px auto; padding: 0 16px; }
      .card { background: rgba(15, 23, 42, 0.8); border: 1px solid rgba(148, 163, 184, 0.3); border-radius: 12px; padding: 16px; margin-bottom: 16px; }
      .row { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; margin: 8px 0; }
    input, button { border-radius: 8px; border: 1px solid #475569; background: #0b1220; color: #e2e8f0; padding: 8px 10px; }
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
    </style>
  </head>
  <body>
    <div class="container">
      <div id="loginBox" class="card">
                <h2>本地共享连接</h2>
        <div class="row">
                    <input id="accessCode" class="secret-mask" type="text" inputmode="text" autocapitalize="off" autocorrect="off" spellcheck="false" autocomplete="off" name="access_code" placeholder="访问密钥">
                    <button id="connectBtn" type="button" autocomplete="off">连接</button>
        </div>
                <div class="muted">仅需输入密码，用户名不参与校验。</div>
      </div>

      <div id="appBox" class="card" style="display:none;">
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
    </div>

    <script>
            const state = { token: '', currentPath: '/' };
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
            let busy = false;
            let pendingOverwriteMode = false;

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

            function normalizeErrorText(error) {
                return (error && error.message) ? error.message : 'unknown';
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
                                    const response = await fetch('/api/download?path=' + encodeURIComponent(path), {
                                        method: 'GET',
                                        headers: authHeaders()
                                    });
                                    if (!response.ok) {
                                        alert('下载失败');
                                        return;
                                    }
                                    const blob = await response.blob();
                                    const url = URL.createObjectURL(blob);
                                    const a = document.createElement('a');
                                    a.href = url;
                                    a.download = entry.name;
                                    a.click();
                                    URL.revokeObjectURL(url);
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
                                    const response = await fetch('/api/download?path=' + encodeURIComponent(path), {
                                        method: 'GET',
                                        headers: authHeaders()
                                    });
                                    if (!response.ok) {
                                        alert('下载失败');
                                        return;
                                    }
                                    const blob = await response.blob();
                                    const url = URL.createObjectURL(blob);
                                    const a = document.createElement('a');
                                    a.href = url;
                                    a.download = entry.name + '.zip';
                                    a.click();
                                    URL.revokeObjectURL(url);
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
                    setStatus('登录成功，正在读取目录...');
                    setBusy(false);
                    loadList();
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
        const formData = new FormData();
        for (const file of files) {
                    formData.append('file', file, file.webkitRelativePath || file.name);
                }

                setBusy(true);
                pendingOverwriteMode = overwriteMode;
                setStatus((sourceType === 'dir' ? '目录' : '文件') + '上传中（传输阶段）...');
                showProgress(0);

                const xhr = new XMLHttpRequest();
                const overwriteQuery = pendingOverwriteMode ? '&overwrite=1' : '';
                xhr.open('POST', '/api/upload?path=' + encodeURIComponent(state.currentPath) + overwriteQuery, true);
                const headers = authHeaders();
                for (const key in headers) {
                    xhr.setRequestHeader(key, headers[key]);
                }

                xhr.upload.onprogress = (event) => {
                    if (!event.lengthComputable) return;
                    const percent = (event.loaded / event.total) * 100;
                    showProgress(percent);
                    setStatus('上传中 ' + percent.toFixed(1) + '%');
                };

                xhr.onload = () => {
                    if (xhr.status >= 200 && xhr.status < 300) {
                        showProgress(100);
                        setStatus('上传传输完成，正在处理服务器写入...');
                        let payload = {};
                        try {
                            payload = xhr.responseText ? JSON.parse(xhr.responseText) : {};
                        } catch (_) {
                            payload = {};
                        }
                        const conflictCount = payload.conflictCount || 0;
                        if (conflictCount > 0 && !pendingOverwriteMode) {
                            const okCount = payload.okCount || 0;
                            const answer = confirm('检测到 ' + conflictCount + ' 个同名文件。\n已上传成功 ' + okCount + ' 个。\n是否覆盖冲突文件后重试上传冲突项？');
                            if (answer) {
                                pendingOverwriteMode = true;
                                const conflictPathSet = new Set((payload.files || []).filter((x) => x.status === 'conflict').map((x) => x.path));
                                const retryFiles = files.filter((f) => conflictPathSet.has(f.webkitRelativePath || f.name));
                                if (retryFiles.length > 0) {
                                    setBusy(false);
                                    uploadSelected(retryFiles, sourceType, true);
                                    return;
                                }
                            }
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