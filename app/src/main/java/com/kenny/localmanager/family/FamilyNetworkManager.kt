package com.kenny.localmanager.family

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import com.kenny.localmanager.util.getLocalIpAddress
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

private const val TAG = "FamilyNetworkManager"
private const val FAMILY_SERVICE_TYPE = "_localmanager._tcp."
private const val FAMILY_VERSION = "0.1"
private const val FAMILY_PREFERRED_PORT = 8765
private const val FAMILY_LOG_LIMIT = 200

data class FamilyDiscoveredService(
    val serviceName: String,
    val serviceType: String,
    val host: String,
    val port: Int,
    val attributes: Map<String, String>,
    val isSelf: Boolean
) {
    val conversationKey: String
        get() = attributes["instance_id"]?.takeIf { it.isNotBlank() }
            ?: "$serviceName@$host:$port"
}

data class FamilyChatMessage(
    val id: String,
    val conversationKey: String,
    val senderName: String,
    val senderInstanceId: String?,
    val content: String,
    val timestamp: Long,
    val incoming: Boolean,
    val deliveryError: String? = null
)

data class FamilyNetworkState(
    val isRunning: Boolean = false,
    val isRegistered: Boolean = false,
    val isDiscovering: Boolean = false,
    val serviceType: String = FAMILY_SERVICE_TYPE,
    val serviceName: String = "",
    val port: Int = 0,
    val localIp: String? = null,
    val discoveredServices: List<FamilyDiscoveredService> = emptyList(),
    val messagesByConversation: Map<String, List<FamilyChatMessage>> = emptyMap(),
    val logLines: List<String> = emptyList(),
    val lastError: String? = null
)

class FamilyNetworkManager(context: Context) {
    private val appContext = context.applicationContext
    private val nsdManager = appContext.getSystemService(Context.NSD_SERVICE) as? NsdManager
    private val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private val tlsManager = FamilyTlsManager(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val logBuffer = CopyOnWriteArrayList<String>()
    private val _state = MutableStateFlow(FamilyNetworkState())
    val state: StateFlow<FamilyNetworkState> = _state

    private var multicastLock: WifiManager.MulticastLock? = null
    private var httpsServer: EmbeddedHttpServer? = null
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var serverJob: Job? = null
    private var currentInstanceId: String? = null
    private var started = false

    fun start() {
        if (started) return
        started = true
        appendLog("启动家庭网络服务。")
        val manager = nsdManager
        if (manager == null) {
            val message = "系统未提供 NsdManager，无法进行 mDNS 注册和发现。"
            appendError(message)
            started = false
            return
        }
        acquireMulticastLock()
        val localIp = try {
            getLocalIpAddress()
        } catch (e: Throwable) {
            appendLog("获取本机 IP 失败：${e.message ?: e.javaClass.simpleName}")
            null
        }
        _state.update { it.copy(localIp = localIp) }
        try {
            val localIdentity = tlsManager.localIdentity()
            val server = EmbeddedHttpServer(
                preferredPort = FAMILY_PREFERRED_PORT,
                sslContext = localIdentity.sslContext,
                log = { appendLog(it) },
                handleMessage = { payloadText, remoteAddress ->
                    handleIncomingMessage(payloadText, remoteAddress)
                }
            )
            server.start()
            httpsServer = server
            serverJob = scope.launch(Dispatchers.IO) { server.acceptLoop() }
            appendLog("本地 HTTPS 服务已监听端口 ${server.port}，证书指纹=${localIdentity.fingerprintSha256}。")
            val instanceId = UUID.randomUUID().toString()
            currentInstanceId = instanceId
            val requestedServiceName = buildServiceName()
            _state.update {
                it.copy(
                    isRunning = true,
                    isRegistered = false,
                    serviceName = requestedServiceName,
                    port = server.port,
                    lastError = null
                )
            }
            registerService(
                manager = manager,
                requestedServiceName = requestedServiceName,
                port = server.port,
                instanceId = instanceId,
                tlsFingerprint = localIdentity.fingerprintSha256
            )
            startDiscovery(clearDiscovered = true)
        } catch (e: Throwable) {
            appendError("启动家庭网络服务失败：${e.message ?: e.javaClass.simpleName}")
            Log.e(TAG, "start failed", e)
            stop()
        }
    }

    fun refresh() {
        if (!started) {
            appendLog("家庭网络尚未启动，先执行启动。")
            start()
            return
        }
        appendLog("手工刷新服务发现。")
        startDiscovery(clearDiscovered = true)
    }

    fun sendMessage(service: FamilyDiscoveredService, message: String) {
        val trimmed = message.trim()
        if (trimmed.isEmpty()) {
            appendError("发送消息失败：消息内容不能为空。")
            return
        }
        val senderName = _state.value.serviceName.ifBlank { buildServiceName() }
        val senderInstanceId = currentInstanceId
        val conversationKey = service.conversationKey
        val outgoingMessageId = UUID.randomUUID().toString()
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                postMessage(
                    service = service,
                    senderName = senderName,
                    senderInstanceId = senderInstanceId,
                    content = trimmed,
                    messageId = outgoingMessageId
                )
            }
            val timestamp = System.currentTimeMillis()
            result.onSuccess {
                appendConversationMessage(
                    FamilyChatMessage(
                        id = outgoingMessageId,
                        conversationKey = conversationKey,
                        senderName = senderName,
                        senderInstanceId = senderInstanceId,
                        content = trimmed,
                        timestamp = timestamp,
                        incoming = false
                    )
                )
                appendLog("消息已收到对端回执：${service.serviceName} ${service.host}:${service.port} message_id=$outgoingMessageId")
            }.onFailure { error ->
                val detail = error.message ?: error.javaClass.simpleName
                appendConversationMessage(
                    FamilyChatMessage(
                        id = outgoingMessageId,
                        conversationKey = conversationKey,
                        senderName = senderName,
                        senderInstanceId = senderInstanceId,
                        content = trimmed,
                        timestamp = timestamp,
                        incoming = false,
                        deliveryError = detail
                    )
                )
                appendError("发送消息到 ${service.serviceName} 失败：$detail")
            }
        }
    }

    fun getMessages(conversationKey: String): List<FamilyChatMessage> {
        return _state.value.messagesByConversation[conversationKey].orEmpty()
    }

    fun stop() {
        if (!started && httpsServer == null && registrationListener == null && discoveryListener == null) return
        appendLog("停止家庭网络服务。")
        stopDiscoveryInternal()
        unregisterServiceInternal()
        serverJob?.cancel()
        serverJob = null
        httpsServer?.stop()
        httpsServer = null
        releaseMulticastLock()
        currentInstanceId = null
        started = false
        _state.update {
            it.copy(
                isRunning = false,
                isRegistered = false,
                isDiscovering = false,
                serviceName = "",
                port = 0,
                messagesByConversation = emptyMap()
            )
        }
    }

    fun destroy() {
        stop()
        scope.cancel()
    }

    private fun registerService(
        manager: NsdManager,
        requestedServiceName: String,
        port: Int,
        instanceId: String,
        tlsFingerprint: String
    ) {
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = requestedServiceName
            serviceType = FAMILY_SERVICE_TYPE
            setPort(port)
            setAttribute("app", "LocalManager")
            setAttribute("proto", FAMILY_TLS_PROTOCOL)
            setAttribute("version", FAMILY_VERSION)
            setAttribute("instance_id", instanceId)
            setAttribute("platform", "android")
            setAttribute("tls", "1")
            setAttribute(FAMILY_TLS_FINGERPRINT_ATTR, tlsFingerprint)
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {
                appendLog("mDNS 服务已注册：${info.serviceName} ${info.serviceType} 端口 ${info.port}")
                _state.update {
                    it.copy(
                        isRegistered = true,
                        serviceName = info.serviceName ?: requestedServiceName,
                        port = info.port.takeIf { value -> value > 0 } ?: port,
                        lastError = null
                    )
                }
            }

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                appendError("mDNS 服务注册失败，错误码=$errorCode，service=${serviceInfo.serviceName}")
                _state.update { it.copy(isRegistered = false) }
            }

            override fun onServiceUnregistered(info: NsdServiceInfo) {
                appendLog("mDNS 服务已注销：${info.serviceName}")
                _state.update { it.copy(isRegistered = false) }
            }

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                appendError("mDNS 服务注销失败，错误码=$errorCode，service=${serviceInfo.serviceName}")
            }
        }
        registrationListener = listener
        manager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    private fun startDiscovery(clearDiscovered: Boolean) {
        val manager = nsdManager
        if (manager == null) {
            appendError("系统未提供 NsdManager，无法开始服务发现。")
            return
        }
        stopDiscoveryInternal()
        if (clearDiscovered) {
            _state.update { it.copy(discoveredServices = emptyList()) }
        }
        val listener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                appendError("启动服务发现失败，类型=$serviceType，错误码=$errorCode")
                _state.update { it.copy(isDiscovering = false) }
                try {
                    manager.stopServiceDiscovery(this)
                } catch (_: Throwable) {
                }
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                appendError("停止服务发现失败，类型=$serviceType，错误码=$errorCode")
                _state.update { it.copy(isDiscovering = false) }
                try {
                    manager.stopServiceDiscovery(this)
                } catch (_: Throwable) {
                }
            }

            override fun onDiscoveryStarted(serviceType: String) {
                appendLog("开始发现服务：$serviceType")
                _state.update { it.copy(isDiscovering = true, lastError = null) }
            }

            override fun onDiscoveryStopped(serviceType: String) {
                appendLog("已停止发现服务：$serviceType")
                _state.update { it.copy(isDiscovering = false) }
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (serviceInfo.serviceType != FAMILY_SERVICE_TYPE) return
                val localServiceName = _state.value.serviceName.trim()
                val candidateName = serviceInfo.serviceName?.trim().orEmpty()
                if (localServiceName.isNotEmpty() && candidateName == localServiceName) {
                    appendLog("跳过解析本机服务：$candidateName")
                    return
                }
                appendLog("发现候选服务：${serviceInfo.serviceName}")
                resolveService(serviceInfo)
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                appendLog("服务离线通知：${serviceInfo.serviceName}。为避免 Android NSD 误报，这里暂时保留上次成功解析的入口。")
            }
        }
        discoveryListener = listener
        manager.discoverServices(FAMILY_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    private fun resolveService(serviceInfo: NsdServiceInfo) {
        val manager = nsdManager ?: return
        manager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                appendError("解析服务失败，name=${serviceInfo.serviceName}，错误码=$errorCode")
            }

            override fun onServiceResolved(resolved: NsdServiceInfo) {
                val host = resolved.host?.hostAddress ?: resolved.host?.hostName ?: ""
                val attributes = decodeAttributes(resolved)
                val instanceId = currentInstanceId
                val entry = FamilyDiscoveredService(
                    serviceName = resolved.serviceName ?: "",
                    serviceType = resolved.serviceType ?: FAMILY_SERVICE_TYPE,
                    host = host,
                    port = resolved.port,
                    attributes = attributes,
                    isSelf = instanceId != null && attributes["instance_id"] == instanceId
                )
                appendLog("解析到服务：${entry.serviceName} -> ${entry.host}:${entry.port}")
                _state.update { current ->
                    val updated = current.discoveredServices
                        .filterNot { it.serviceName == entry.serviceName }
                        .plus(entry)
                        .sortedWith(
                            compareByDescending<FamilyDiscoveredService> { it.isSelf }
                                .thenBy { it.serviceName.lowercase() }
                        )
                    current.copy(discoveredServices = updated, lastError = null)
                }
            }
        })
    }

    private fun stopDiscoveryInternal() {
        val manager = nsdManager ?: return
        val listener = discoveryListener ?: return
        try {
            manager.stopServiceDiscovery(listener)
        } catch (e: IllegalArgumentException) {
            appendLog("停止发现时监听器不存在：${e.message ?: e.javaClass.simpleName}")
        } catch (e: Throwable) {
            appendError("停止服务发现失败：${e.message ?: e.javaClass.simpleName}")
        } finally {
            discoveryListener = null
            _state.update { it.copy(isDiscovering = false) }
        }
    }

    private fun postMessage(
        service: FamilyDiscoveredService,
        senderName: String,
        senderInstanceId: String?,
        content: String,
        messageId: String
    ): Result<Unit> {
        return runCatching {
            val protocol = service.attributes["proto"]?.trim()?.lowercase()
            if (protocol != FAMILY_TLS_PROTOCOL) {
                throw IllegalStateException("对端 ${service.serviceName} 未声明 HTTPS，当前仅允许加密通道。")
            }
            val fingerprint = service.attributes[FAMILY_TLS_FINGERPRINT_ATTR]
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: throw IllegalStateException("对端 ${service.serviceName} 缺少 TLS 指纹，无法验证证书身份。")
            val body = JSONObject().apply {
                put("message_id", messageId)
                put("sender_name", senderName)
                put("sender_instance_id", senderInstanceId ?: "")
                put("sender_platform", "android")
                put("content", content)
                put("timestamp", System.currentTimeMillis())
            }.toString()
            val endpoint = URL("https://${service.host}:${service.port}/message")
            val connection = tlsManager.openHttpsConnection(endpoint, fingerprint).apply {
                requestMethod = "POST"
                connectTimeout = 5000
                readTimeout = 5000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
            }
            connection.useJsonRequest(body, expectedAckMessageId = messageId)
        }
    }

    private fun unregisterServiceInternal() {
        val manager = nsdManager ?: return
        val listener = registrationListener ?: return
        try {
            manager.unregisterService(listener)
        } catch (e: IllegalArgumentException) {
            appendLog("注销服务时监听器不存在：${e.message ?: e.javaClass.simpleName}")
        } catch (e: Throwable) {
            appendError("注销 mDNS 服务失败：${e.message ?: e.javaClass.simpleName}")
        } finally {
            registrationListener = null
            _state.update { it.copy(isRegistered = false) }
        }
    }

    private fun acquireMulticastLock() {
        if (multicastLock?.isHeld == true) return
        val manager = wifiManager ?: run {
            appendLog("未获取到 WifiManager，跳过 MulticastLock。")
            return
        }
        try {
            multicastLock = manager.createMulticastLock("LocalManagerFamilyNetwork").apply {
                setReferenceCounted(false)
                acquire()
            }
            appendLog("已申请 Wi-Fi MulticastLock。")
        } catch (e: Throwable) {
            appendError("申请 Wi-Fi MulticastLock 失败：${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun releaseMulticastLock() {
        val lock = multicastLock ?: return
        try {
            if (lock.isHeld) {
                lock.release()
                appendLog("已释放 Wi-Fi MulticastLock。")
            }
        } catch (e: Throwable) {
            appendError("释放 Wi-Fi MulticastLock 失败：${e.message ?: e.javaClass.simpleName}")
        } finally {
            multicastLock = null
        }
    }

    private fun appendLog(message: String) {
        val line = message.trim()
        if (line.isEmpty()) return
        logBuffer += line
        while (logBuffer.size > FAMILY_LOG_LIMIT) {
            logBuffer.removeAt(0)
        }
        _state.update { it.copy(logLines = logBuffer.toList()) }
        Log.i(TAG, line)
    }

    private fun appendError(message: String) {
        appendLog(message)
        _state.update { it.copy(lastError = message) }
        Log.e(TAG, message)
    }

    private fun buildServiceName(): String {
        val model = Build.MODEL?.trim()?.replace(Regex("\\s+"), "-")?.takeIf { it.isNotEmpty() } ?: "Android"
        return "LocalManager-$model"
    }

    private fun decodeAttributes(info: NsdServiceInfo): Map<String, String> {
        val rawAttributes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) info.attributes else emptyMap()
        return rawAttributes.mapValues { (_, value) ->
            value?.toString(StandardCharsets.UTF_8) ?: ""
        }
    }

    private fun appendConversationMessage(message: FamilyChatMessage) {
        _state.update { current ->
            val updatedConversation = current.messagesByConversation[message.conversationKey]
                .orEmpty()
                .plus(message)
                .sortedBy { it.timestamp }
                .takeLast(100)
            current.copy(
                messagesByConversation = current.messagesByConversation +
                    (message.conversationKey to updatedConversation)
            )
        }
    }

    private fun handleIncomingMessage(payloadText: String, remoteAddress: String): HttpResponse {
        val payload = try {
            JSONObject(payloadText)
        } catch (e: Throwable) {
            val detail = e.message ?: e.javaClass.simpleName
            appendError("收到非法消息 JSON：$detail")
            return HttpResponse(400, jsonError("invalid_json", detail))
        }
        val messageId = payload.optString("message_id").trim().ifEmpty { UUID.randomUUID().toString() }
        val senderName = payload.optString("sender_name").trim().ifEmpty { remoteAddress }
        val senderInstanceId = payload.optString("sender_instance_id").trim().ifEmpty { null }
        val content = payload.optString("content").trim()
        if (content.isEmpty()) {
            appendError("收到空消息，来源=$senderName")
            return HttpResponse(400, jsonError("empty_content", "消息内容不能为空"))
        }
        val conversationKey = senderInstanceId ?: senderName
        val message = FamilyChatMessage(
            id = messageId,
            conversationKey = conversationKey,
            senderName = senderName,
            senderInstanceId = senderInstanceId,
            content = content,
            timestamp = payload.optLong("timestamp").takeIf { it > 0L } ?: System.currentTimeMillis(),
            incoming = true
        )
        appendConversationMessage(message)
        appendLog("收到消息：$senderName -> $content")
        return HttpResponse(
            200,
            JSONObject().apply {
                put("ok", true)
                put("acknowledged", true)
                put("message_id", messageId)
                put("conversation_key", conversationKey)
            }.toString()
        )
    }

    private fun jsonError(code: String, message: String): String {
        return JSONObject().apply {
            put("ok", false)
            put("error", code)
            put("message", message)
        }.toString()
    }

    private fun HttpURLConnection.useJsonRequest(body: String, expectedAckMessageId: String) {
        try {
            outputStream.use { output ->
                output.write(body.toByteArray(StandardCharsets.UTF_8))
            }
            val status = responseCode
            if (status !in 200..299) {
                val errorText = readResponseText(errorStream)
                throw IllegalStateException(
                    "HTTP $status${if (errorText.isNotBlank()) ": $errorText" else ""}"
                )
            }
            val responseText = inputStream?.use { stream ->
                readResponseText(stream)
            }.orEmpty()
            val responseJson = try {
                JSONObject(responseText)
            } catch (error: Throwable) {
                throw IllegalStateException(
                    "对端返回了 2xx，但回执不是合法 JSON：${error.message ?: error.javaClass.simpleName}"
                )
            }
            if (!responseJson.optBoolean("ok", false)) {
                throw IllegalStateException(
                    "对端返回了失败回执：${responseJson.optString("message").ifBlank { responseText.ifBlank { "unknown_error" } }}"
                )
            }
            if (!responseJson.optBoolean("acknowledged", false)) {
                throw IllegalStateException("对端未明确确认已接收消息。")
            }
            val ackMessageId = responseJson.optString("message_id").trim()
            if (ackMessageId != expectedAckMessageId) {
                throw IllegalStateException(
                    "对端回执 message_id 不匹配，expected=$expectedAckMessageId actual=${ackMessageId.ifBlank { "<empty>" }}"
                )
            }
        } finally {
            disconnect()
        }
    }

    private fun readResponseText(stream: java.io.InputStream?): String {
        if (stream == null) return ""
        return BufferedInputStream(stream).use { input ->
            val buffer = ByteArrayOutputStream()
            val chunk = ByteArray(4096)
            while (true) {
                val read = input.read(chunk)
                if (read <= 0) break
                buffer.write(chunk, 0, read)
            }
            String(buffer.toByteArray(), StandardCharsets.UTF_8)
        }
    }
}

private data class HttpResponse(
    val statusCode: Int,
    val body: String,
    val contentType: String = "application/json; charset=utf-8"
)

private class EmbeddedHttpServer(
    preferredPort: Int,
    sslContext: SSLContext,
    private val log: (String) -> Unit,
    private val handleMessage: (String, String) -> HttpResponse
) {
    private val running = AtomicBoolean(false)
    private val serverSocket: SSLServerSocket = (sslContext.serverSocketFactory.createServerSocket() as SSLServerSocket).apply {
        reuseAddress = true
        try {
            bind(InetSocketAddress(preferredPort))
        } catch (firstError: Throwable) {
            log("端口 $preferredPort 监听失败：${firstError.message ?: firstError.javaClass.simpleName}；改用系统分配端口。")
            bind(InetSocketAddress(0))
        }
        soTimeout = 2000
    }

    val port: Int
        get() = serverSocket.localPort

    fun start() {
        running.set(true)
    }

    suspend fun acceptLoop() = withContext(Dispatchers.IO) {
        while (running.get()) {
            try {
                val socket = serverSocket.accept()
                handleClient(socket)
            } catch (_: java.net.SocketTimeoutException) {
            } catch (e: Throwable) {
                if (running.get()) {
                    log("HTTPS 服务 accept 失败：${e.message ?: e.javaClass.simpleName}")
                }
            }
        }
    }

    fun stop() {
        running.set(false)
        try {
            serverSocket.close()
        } catch (_: Throwable) {
        }
    }

    private fun handleClient(socket: Socket) {
        socket.use { client ->
            client.soTimeout = 3000
            if (client is SSLSocket) {
                client.useClientMode = false
                client.startHandshake()
            }
            val input = BufferedInputStream(client.getInputStream())
            val output = client.getOutputStream()
            val headerBytes = readHeaderBytes(input) ?: return
            val headerText = headerBytes.toString(StandardCharsets.ISO_8859_1)
            val headerLines = headerText.split("\r\n")
            val requestLine = headerLines.firstOrNull()?.takeIf { it.isNotBlank() } ?: return
            val requestParts = requestLine.split(" ")
            val method = requestParts.getOrNull(0)?.uppercase() ?: "GET"
            val path = requestParts.getOrNull(1) ?: "/"
            var contentLength = 0
            for (line in headerLines.drop(1)) {
                if (line.isEmpty()) break
                val separator = line.indexOf(':')
                if (separator > 0) {
                    val headerName = line.substring(0, separator).trim()
                    val headerValue = line.substring(separator + 1).trim()
                    if (headerName.equals("Content-Length", ignoreCase = true)) {
                        contentLength = headerValue.toIntOrNull() ?: 0
                    }
                }
            }
            val response = when {
                method == "POST" && path == "/message" -> {
                    val bodyBytes = readBodyBytes(input, contentLength.coerceAtLeast(0))
                    handleMessage(
                        bodyBytes.toString(StandardCharsets.UTF_8),
                        client.inetAddress?.hostAddress ?: ""
                    )
                }
                method == "GET" -> HttpResponse(
                    200,
                    "{\"service\":\"LocalManager Android mDNS prototype\",\"request\":${jsonString(requestLine)},\"port\":$port}"
                )
                else -> HttpResponse(404, "{\"ok\":false,\"error\":\"not_found\"}")
            }
            val responseBody = response.body.toByteArray(StandardCharsets.UTF_8)
            val responseHead = buildString {
                append("HTTP/1.1 ${response.statusCode} ${httpStatusText(response.statusCode)}\r\n")
                append("Content-Type: ${response.contentType}\r\n")
                append("Content-Length: ${responseBody.size}\r\n")
                append("Connection: close\r\n")
                append("\r\n")
            }.toByteArray(StandardCharsets.ISO_8859_1)
            output.write(responseHead)
            output.write(responseBody)
            output.flush()
        }
    }

    private fun readHeaderBytes(input: BufferedInputStream): ByteArray? {
        val headerBuffer = ByteArrayOutputStream()
        var matched = 0
        while (true) {
            val next = input.read()
            if (next < 0) {
                return if (headerBuffer.size() > 0) headerBuffer.toByteArray() else null
            }
            headerBuffer.write(next)
            matched = when {
                matched == 0 && next == '\r'.code -> 1
                matched == 1 && next == '\n'.code -> 2
                matched == 2 && next == '\r'.code -> 3
                matched == 3 && next == '\n'.code -> break
                next == '\r'.code -> 1
                else -> 0
            }
            if (headerBuffer.size() > 64 * 1024) {
                throw IllegalStateException("HTTP 请求头过大，超过 64 KiB")
            }
        }
        return headerBuffer.toByteArray()
    }

    private fun readBodyBytes(input: BufferedInputStream, contentLength: Int): ByteArray {
        if (contentLength <= 0) return ByteArray(0)
        val body = ByteArray(contentLength)
        var totalRead = 0
        while (totalRead < contentLength) {
            val read = input.read(body, totalRead, contentLength - totalRead)
            if (read <= 0) break
            totalRead += read
        }
        return if (totalRead == contentLength) body else body.copyOf(totalRead)
    }

    private fun httpStatusText(statusCode: Int): String {
        return when (statusCode) {
            200 -> "OK"
            400 -> "Bad Request"
            404 -> "Not Found"
            else -> "OK"
        }
    }

    private fun jsonString(value: String): String {
        return buildString {
            append('"')
            value.forEach { ch ->
                when (ch) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(ch)
                }
            }
            append('"')
        }
    }
}