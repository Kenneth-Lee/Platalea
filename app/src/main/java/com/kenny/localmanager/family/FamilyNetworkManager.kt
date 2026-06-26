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
import java.io.File
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Locale
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
import org.json.JSONArray
import org.json.JSONObject

private const val TAG = "FamilyNetworkManager"
private const val FAMILY_SERVICE_TYPE = "_localmanager._tcp."
private const val FAMILY_VERSION = "0.2"
private const val FAMILY_PREFERRED_PORT = 8765
private const val FAMILY_LOG_LIMIT = 200
private const val INSTANCE_ID_FILE = "family_network_instance_id"

data class FamilyDiscoveredService(
    val serviceName: String,
    val serviceType: String,
    val host: String,
    val port: Int,
    val attributes: Map<String, String>,
    val isSelf: Boolean
) {
    val deviceKey: String
        get() = attributes["instance_id"]?.takeIf { it.isNotBlank() }
            ?: "$serviceName@$host:$port"

    /** mDNS TXT `auth=1` 表示对端要求接入密码。 */
    val requiresPasswordAuth: Boolean
        get() = attributes["auth"]?.trim() == "1"
}

data class FamilyNetworkState(
    val isRunning: Boolean = false,
    val isRegistered: Boolean = false,
    val isDiscovering: Boolean = false,
    val serviceType: String = FAMILY_SERVICE_TYPE,
    val serviceName: String = "",
    val port: Int = 0,
    val localIp: String? = null,
    val discoveredServices: List<FamilyDiscoveredService> = emptyList(),
    val localServiceEnabled: Boolean = true,
    val openBoardSession: BulletinBoardOpenSession? = null,
    val logLines: List<String> = emptyList(),
    val lastError: String? = null
)

class FamilyNetworkManager(context: Context) {
    private val appContext = context.applicationContext
    private val nsdManager = appContext.getSystemService(Context.NSD_SERVICE) as? NsdManager
    private val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private val tlsManager = FamilyTlsManager(appContext)
    private val boardStore = BulletinBoardStore(appContext)
    private val boardHttpHandler = BulletinBoardHttpHandler(boardStore)
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
    private var networkPassword: String? = null
    private var networkHostPassword: String? = null
    private var localServiceEnabled: Boolean = true
    private var familyNetworkUserName: String? = null

    fun configure(
        networkPassword: String?,
        localServiceEnabled: Boolean,
        familyNetworkUserName: String? = null,
        networkHostPassword: String? = null
    ) {
        this.networkPassword = networkPassword?.trim()?.ifEmpty { null }
        this.networkHostPassword = networkHostPassword?.trim()?.ifEmpty { null }
        this.familyNetworkUserName = familyNetworkUserName?.trim()?.ifEmpty { null }
        val enabledChanged = this.localServiceEnabled != localServiceEnabled
        this.localServiceEnabled = localServiceEnabled
        _state.update { it.copy(localServiceEnabled = localServiceEnabled) }
        if (!started) return
        if (enabledChanged) {
            if (localServiceEnabled) {
                startLocalServerStack(_state.value.localIp)
            } else {
                stopLocalServerStack()
            }
        }
    }

    fun start() {
        if (started) return
        started = true
        appendLog("启动家庭网络客户端。")
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
        _state.update {
            it.copy(
                localIp = localIp,
                isRunning = true,
                localServiceEnabled = localServiceEnabled,
                lastError = null
            )
        }
        try {
            if (localServiceEnabled) {
                startLocalServerStack(localIp)
            } else {
                appendLog("本机留言板服务已关闭，仅发现局域网内其他设备。")
            }
            startDiscovery(clearDiscovered = false)
        } catch (e: Throwable) {
            appendError("启动家庭网络失败：${e.message ?: e.javaClass.simpleName}")
            Log.e(TAG, "start failed", e)
            stop()
        }
    }

    private fun startLocalServerStack(localIp: String?) {
        if (!localServiceEnabled || httpsServer != null) return
        val localIdentity = tlsManager.localIdentity()
        val server = EmbeddedHttpServer(
            preferredPort = FAMILY_PREFERRED_PORT,
            sslContext = localIdentity.sslContext,
            log = { appendLog(it) },
            handleRequest = { method, path, body, remoteAddress, headers ->
                handleHttpRequest(method, path, body, remoteAddress, headers)
            }
        )
        server.start()
        httpsServer = server
        serverJob = scope.launch(Dispatchers.IO) { server.acceptLoop() }
        appendLog("本地 HTTPS 留言板已监听端口 ${server.port}，证书指纹=${localIdentity.fingerprintSha256}。")
        val instanceId = loadOrCreateInstanceId()
        currentInstanceId = instanceId
        val requestedServiceName = buildServiceName()
        _state.update {
            it.copy(
                serviceName = requestedServiceName,
                port = server.port,
                lastError = null
            )
        }
        val manager = nsdManager ?: return
        registerService(
            manager = manager,
            requestedServiceName = requestedServiceName,
            port = server.port,
            instanceId = instanceId,
            tlsFingerprint = localIdentity.fingerprintSha256
        )
    }

    private fun stopLocalServerStack() {
        unregisterServiceInternal()
        serverJob?.cancel()
        serverJob = null
        httpsServer?.stop()
        httpsServer = null
        currentInstanceId = null
        _state.update { current ->
            current.copy(
                isRegistered = false,
                serviceName = "",
                port = 0,
                discoveredServices = current.discoveredServices.filterNot { it.isSelf }
            )
        }
        if (_state.value.openBoardSession?.service?.isSelf == true) {
            closeBulletinBoard()
        }
        appendLog("本机留言板 HTTPS 服务已停止。")
    }

    fun refresh() {
        if (!started) {
            appendLog("家庭网络尚未启动，先执行启动。")
            start()
            return
        }
        appendLog("手工刷新服务发现。")
        _state.update { current ->
            current.copy(discoveredServices = current.discoveredServices.filter { it.isSelf }.take(1))
        }
        startDiscovery(clearDiscovered = false)
        republishLocalSelfServiceIfRunning()
    }

    fun openBulletinBoard(
        service: FamilyDiscoveredService,
        boardId: String = BulletinBoardDefaults.DEFAULT_BOARD_ID,
        accessPassword: String? = null
    ) {
        if (service.isSelf && !localServiceEnabled) {
            appendError("本机留言板服务已在配置中关闭，无法进入本机留言板。")
            return
        }
        val isHost = service.isSelf
        _state.update {
            it.copy(
                openBoardSession = BulletinBoardOpenSession(
                    service = service,
                    boardId = boardId,
                    boardName = BulletinBoardDefaults.DEFAULT_BOARD_NAME,
                    isHost = isHost,
                    canManageBoard = isHost,
                    accessPassword = accessPassword?.trim()?.ifEmpty { null },
                    loading = true
                )
            )
        }
        scope.launch { refreshOpenBoard() }
    }

    suspend fun probeBoardAccess(
        service: FamilyDiscoveredService,
        boardId: String = BulletinBoardDefaults.DEFAULT_BOARD_ID,
        accessPassword: String? = null
    ): Result<Unit> {
        if (service.isSelf && !localServiceEnabled) {
            return Result.failure(IllegalStateException("本机留言板服务已关闭"))
        }
        if (service.isSelf) {
            return withContext(Dispatchers.IO) {
                if (boardStore.snapshot(boardId) != null) {
                    Result.success(Unit)
                } else {
                    Result.failure(IllegalStateException("留言板不存在：$boardId"))
                }
            }
        }
        return withContext(Dispatchers.IO) {
            fetchBoardSnapshot(service, boardId, accessPassword?.trim()?.ifEmpty { null }).map { }
        }
    }

    fun closeBulletinBoard() {
        _state.update { it.copy(openBoardSession = null) }
    }

    fun refreshOpenBoard(showLoadingIndicator: Boolean = false) {
        val session = _state.value.openBoardSession ?: return
        scope.launch {
            val shouldShowLoading = showLoadingIndicator && session.messages.isEmpty()
            if (shouldShowLoading) {
                _state.update { current ->
                    current.copy(openBoardSession = current.openBoardSession?.copy(loading = true, lastError = null))
                }
            }
            val result = withContext(Dispatchers.IO) {
                if (session.isHost) {
                    loadLocalBoardSnapshot(session.boardId)
                } else {
                    fetchBoardSnapshot(session.service, session.boardId, session.accessPassword)
                }
            }
            result.onSuccess { snapshot ->
                _state.update { current ->
                    val open = current.openBoardSession ?: return@update current
                    val unchanged = open.revision == snapshot.revision &&
                        bulletinMessagesEqual(open.messages, snapshot.messages)
                    if (unchanged) {
                        if (open.loading) {
                            current.copy(openBoardSession = open.copy(loading = false))
                        } else {
                            current
                        }
                    } else {
                        current.copy(
                            openBoardSession = open.copy(
                                boardName = snapshot.boardName,
                                revision = snapshot.revision,
                                messages = snapshot.messages,
                                canManageBoard = open.isHost || snapshot.canManage,
                                loading = false,
                                lastError = null
                            )
                        )
                    }
                }
            }.onFailure { error ->
                val detail = error.message ?: error.javaClass.simpleName
                _state.update { current ->
                    val open = current.openBoardSession ?: return@update current
                    current.copy(
                        openBoardSession = open.copy(loading = false, lastError = detail)
                    )
                }
                appendError("同步留言板失败：$detail")
            }
        }
    }

    fun postBoardMessage(content: String) {
        val session = _state.value.openBoardSession ?: return
        val trimmed = content.trim()
        if (trimmed.isEmpty()) {
            appendError("发送消息失败：消息内容不能为空。")
            return
        }
        val (authorLabel, authorDevice) = buildAuthorIdentity()
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                if (session.isHost) {
                    runCatching {
                        boardStore.appendMessage(session.boardId, authorLabel, trimmed, authorDevice)
                            ?: throw IllegalStateException("消息内容不能为空或留言板不存在")
                    }.map { }
                } else {
                    boardApiRequest(
                        service = session.service,
                        method = "POST",
                        path = "/boards/${session.boardId}/messages",
                        body = JSONObject().apply {
                            put("content", trimmed)
                            put("author_label", authorLabel)
                            put("author_device", authorDevice)
                        }.toString(),
                        accessPassword = session.accessPassword
                    )
                }
            }
            result.onSuccess {
                appendLog("留言已发布到 ${session.service.serviceName}/${session.boardId}")
                refreshOpenBoard()
            }.onFailure { error ->
                appendError("发布留言失败：${error.message ?: error.javaClass.simpleName}")
            }
        }
    }

    fun updateBoardMessage(messageId: String, content: String) {
        val session = _state.value.openBoardSession ?: return
        if (!session.canManageBoard) {
            appendError("只有宿主可以修改留言。")
            return
        }
        val trimmed = content.trim()
        if (trimmed.isEmpty()) {
            appendError("修改留言失败：内容不能为空。")
            return
        }
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                if (session.isHost) {
                    runCatching {
                        boardStore.updateMessage(session.boardId, messageId, trimmed)
                            ?: throw IllegalStateException("留言不存在或内容为空")
                    }.map { }
                } else {
                    boardApiRequest(
                        service = session.service,
                        method = "PUT",
                        path = "/boards/${session.boardId}/messages/$messageId",
                        body = JSONObject().apply { put("content", trimmed) }.toString(),
                        accessPassword = session.accessPassword
                    )
                }
            }
            result.onSuccess {
                appendLog("留言已更新：$messageId")
                refreshOpenBoard()
            }.onFailure { error ->
                appendError("修改留言失败：${error.message ?: error.javaClass.simpleName}")
            }
        }
    }

    fun deleteBoardMessage(messageId: String) {
        val session = _state.value.openBoardSession ?: return
        if (!session.canManageBoard) {
            appendError("只有宿主可以删除留言。")
            return
        }
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                if (session.isHost) {
                    runCatching {
                        if (!boardStore.deleteMessage(session.boardId, messageId)) {
                            throw IllegalStateException("留言不存在")
                        }
                    }.map { }
                } else {
                    boardApiRequest(
                        service = session.service,
                        method = "DELETE",
                        path = "/boards/${session.boardId}/messages/$messageId",
                        accessPassword = session.accessPassword
                    )
                }
            }
            result.onSuccess {
                appendLog("留言已删除：$messageId")
                refreshOpenBoard()
            }.onFailure { error ->
                appendError("删除留言失败：${error.message ?: error.javaClass.simpleName}")
            }
        }
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
                discoveredServices = emptyList(),
                openBoardSession = null
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
            if (!networkPassword.isNullOrBlank() || !networkHostPassword.isNullOrBlank()) {
                setAttribute("auth", "1")
            }
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
                publishLocalSelfService(
                    host = _state.value.localIp,
                    port = info.port.takeIf { value -> value > 0 } ?: port,
                    instanceId = instanceId,
                    tlsFingerprint = tlsFingerprint
                )
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
                appendLog("解析到服务：${entry.serviceName} -> ${entry.host}:${entry.port}${if (entry.isSelf) "（本机）" else ""}")
                upsertDiscoveredService(entry)
            }
        })
    }

    private fun upsertDiscoveredService(entry: FamilyDiscoveredService) {
        if (entry.isSelf && !localServiceEnabled) return
        _state.update { current ->
            val withoutDuplicate = if (entry.isSelf) {
                current.discoveredServices.filterNot { it.isSelf }
            } else {
                current.discoveredServices.filterNot { it.deviceKey == entry.deviceKey }
            }
            val updated = withoutDuplicate
                .plus(entry)
                .sortedWith(
                    compareByDescending<FamilyDiscoveredService> { it.isSelf }
                        .thenBy { it.serviceName.lowercase() }
                )
            current.copy(discoveredServices = updated, lastError = null)
        }
    }

    private fun republishLocalSelfServiceIfRunning() {
        if (!localServiceEnabled) return
        val instanceId = currentInstanceId ?: return
        val port = _state.value.port
        if (port <= 0) return
        val tlsFingerprint = tlsManager.localIdentity().fingerprintSha256
        publishLocalSelfService(_state.value.localIp, port, instanceId, tlsFingerprint)
    }

    private fun loadOrCreateInstanceId(): String {
        val file = File(appContext.filesDir, INSTANCE_ID_FILE)
        if (file.exists()) {
            file.readText().trim().takeIf { it.isNotEmpty() }?.let { return it }
        }
        val id = UUID.randomUUID().toString()
        file.writeText(id)
        return id
    }

    private fun publishLocalSelfService(
        host: String?,
        port: Int,
        instanceId: String,
        tlsFingerprint: String
    ) {
        if (!localServiceEnabled) return
        if (port <= 0) return
        val serviceName = _state.value.serviceName.ifBlank { buildServiceName() }
        val resolvedHost = host?.trim()?.takeIf { it.isNotEmpty() } ?: "127.0.0.1"
        upsertDiscoveredService(
            FamilyDiscoveredService(
                serviceName = serviceName,
                serviceType = FAMILY_SERVICE_TYPE,
                host = resolvedHost,
                port = port,
                attributes = mapOf(
                    "app" to "LocalManager",
                    "proto" to FAMILY_TLS_PROTOCOL,
                    "version" to FAMILY_VERSION,
                    "instance_id" to instanceId,
                    "platform" to "android",
                    "tls" to "1",
                    FAMILY_TLS_FINGERPRINT_ATTR to tlsFingerprint
                ),
                isSelf = true
            )
        )
        appendLog("本机留言板服务已加入发现列表：$resolvedHost:$port")
    }

    private fun handleHttpRequest(
        method: String,
        path: String,
        body: String,
        remoteAddress: String,
        headers: Map<String, String>
    ): FamilyHttpResponse {
        if (path.startsWith("/boards")) {
            val authLevel = resolveAuthLevel(headers)
                ?: return FamilyHttpResponse(
                    401,
                    JSONObject().apply {
                        put("ok", false)
                        put("error", "unauthorized")
                        put("message", "需要正确的网络服务密码")
                    }.toString()
                )
            return boardHttpHandler.handle(method, path, body, authLevel)
        }
        if (method == "GET" && (path == "/" || path.isEmpty())) {
            val boards = boardStore.listBoards()
            return FamilyHttpResponse(
                200,
                JSONObject().apply {
                    put("service", "LocalManager Bulletin Board")
                    put("version", FAMILY_VERSION)
                    put("board_count", boards.size)
                }.toString()
            )
        }
        return FamilyHttpResponse(404, JSONObject().apply {
            put("ok", false)
            put("error", "not_found")
        }.toString())
    }

    private fun resolveAuthLevel(headers: Map<String, String>): FamilyNetworkAuthLevel? {
        val guest = networkPassword
        val host = networkHostPassword
        if (guest.isNullOrBlank() && host.isNullOrBlank()) {
            return FamilyNetworkAuthLevel.OPEN
        }
        val provided = headers[FamilyNetworkAuth.PASSWORD_HEADER.lowercase(Locale.ROOT)]?.trim().orEmpty()
        if (provided.isEmpty()) return null
        if (!host.isNullOrBlank() && provided == host) {
            return FamilyNetworkAuthLevel.HOST
        }
        if (!guest.isNullOrBlank() && provided == guest) {
            return FamilyNetworkAuthLevel.GUEST
        }
        return null
    }

    private fun loadLocalBoardSnapshot(boardId: String): Result<BulletinBoardSnapshot> {
        return runCatching {
            boardStore.snapshot(boardId)
                ?.copy(canManage = true)
                ?: throw IllegalStateException("留言板不存在：$boardId")
        }
    }

    private fun fetchBoardSnapshot(
        service: FamilyDiscoveredService,
        boardId: String,
        accessPassword: String? = null
    ): Result<BulletinBoardSnapshot> {
        return boardApiRequest(
            service = service,
            method = "GET",
            path = "/boards/$boardId/messages",
            body = null,
            accessPassword = accessPassword
        ).mapCatching { text ->
            val json = JSONObject(text)
            if (!json.optBoolean("ok", false)) {
                throw IllegalStateException(json.optString("message", "读取留言板失败"))
            }
            val messages = buildList {
                val arr = json.optJSONArray("messages") ?: JSONArray()
                for (i in 0 until arr.length()) {
                    add(BulletinMessage.fromJson(arr.getJSONObject(i)))
                }
            }
            BulletinBoardSnapshot(
                boardId = json.optString("board_id", boardId),
                boardName = json.optString("board_name", boardId),
                revision = json.optLong("revision"),
                messages = messages,
                canManage = json.optBoolean("can_manage", false)
            )
        }
    }

    private fun boardApiRequest(
        service: FamilyDiscoveredService,
        method: String,
        path: String,
        body: String? = null,
        accessPassword: String? = null
    ): Result<String> {
        return runCatching {
            val protocol = service.attributes["proto"]?.trim()?.lowercase()
            if (protocol != FAMILY_TLS_PROTOCOL) {
                throw IllegalStateException("对端 ${service.serviceName} 未声明 HTTPS。")
            }
            val fingerprint = service.attributes[FAMILY_TLS_FINGERPRINT_ATTR]
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: throw IllegalStateException("对端 ${service.serviceName} 缺少 TLS 指纹。")
            val endpoint = URL("https://${service.host}:${service.port}$path")
            val connection = tlsManager.openHttpsConnection(endpoint, fingerprint).apply {
                requestMethod = method
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("Accept", "application/json")
                accessPassword?.let {
                    setRequestProperty(FamilyNetworkAuth.PASSWORD_HEADER, it)
                }
                if (body != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                }
            }
            try {
                if (body != null) {
                    connection.outputStream.use { output ->
                        output.write(body.toByteArray(StandardCharsets.UTF_8))
                    }
                }
                val status = connection.responseCode
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                val responseText = stream?.use { readResponseText(it) }.orEmpty()
                if (status !in 200..299) {
                    val detail = runCatching { JSONObject(responseText).optString("message") }.getOrNull()
                        ?.takeIf { it.isNotBlank() }
                        ?: responseText.ifBlank { "HTTP $status" }
                    throw IllegalStateException(detail)
                }
                responseText
            } finally {
                connection.disconnect()
            }
        }
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

    private fun buildAuthorIdentity(): Pair<String, String> {
        val deviceLabel = _state.value.serviceName.ifBlank { buildServiceName() }
        val displayLabel = familyNetworkUserName?.takeIf { it.isNotBlank() } ?: deviceLabel
        return displayLabel to deviceLabel
    }

    private fun bulletinMessagesEqual(a: List<BulletinMessage>, b: List<BulletinMessage>): Boolean {
        if (a.size != b.size) return false
        return a.indices.all { index ->
            val left = a[index]
            val right = b[index]
            left.id == right.id &&
                left.seq == right.seq &&
                left.content == right.content &&
                left.updatedAt == right.updatedAt &&
                left.authorLabel == right.authorLabel
        }
    }

    private fun decodeAttributes(info: NsdServiceInfo): Map<String, String> {
        val rawAttributes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) info.attributes else emptyMap()
        return rawAttributes.mapValues { (_, value) ->
            value?.toString(StandardCharsets.UTF_8) ?: ""
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

private class EmbeddedHttpServer(
    preferredPort: Int,
    sslContext: SSLContext,
    private val log: (String) -> Unit,
    private val handleRequest: (String, String, String, String, Map<String, String>) -> FamilyHttpResponse
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
            val headers = mutableMapOf<String, String>()
            for (line in headerLines.drop(1)) {
                if (line.isEmpty()) break
                val separator = line.indexOf(':')
                if (separator > 0) {
                    val headerName = line.substring(0, separator).trim()
                    val headerValue = line.substring(separator + 1).trim()
                    headers[headerName.lowercase(Locale.ROOT)] = headerValue
                    if (headerName.equals("Content-Length", ignoreCase = true)) {
                        contentLength = headerValue.toIntOrNull() ?: 0
                    }
                }
            }
            val response = when {
                method == "POST" || method == "GET" || method == "PUT" || method == "DELETE" -> {
                    val bodyBytes = if (method == "POST" || method == "PUT") {
                        readBodyBytes(input, contentLength.coerceAtLeast(0))
                    } else {
                        ByteArray(0)
                    }
                    handleRequest(
                        method,
                        path,
                        bodyBytes.toString(StandardCharsets.UTF_8),
                        client.inetAddress?.hostAddress ?: "",
                        headers
                    )
                }
                else -> FamilyHttpResponse(405, "{\"ok\":false,\"error\":\"method_not_allowed\"}")
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
            401 -> "Unauthorized"
            405 -> "Method Not Allowed"
            else -> "OK"
        }
    }
}