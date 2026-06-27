package com.kenny.localmanager.family

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import com.kenny.localmanager.file.DocumentFileModel
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
import kotlinx.coroutines.CancellationException
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

    /** 列表展示用主机名：优先 TXT `host_name`，否则解析 mDNS 服务实例名。 */
    val displayHostName: String
        get() = FamilyNetworkHostNames.displayHostName(serviceName, attributes)

    /** mDNS TXT `auth=1` 表示对端要求接入密码。 */
    val requiresPasswordAuth: Boolean
        get() = attributes["auth"]?.trim() == "1"
}

enum class BulletinAttachmentUploadPhase {
    UPLOADING,
    POSTING
}

data class BulletinAttachmentUploadProgress(
    val itemName: String,
    val uploadedBytes: Long,
    val totalBytes: Long,
    val phase: BulletinAttachmentUploadPhase = BulletinAttachmentUploadPhase.UPLOADING
)

data class BulletinAttachmentDownloadProgress(
    val attachmentId: String,
    val itemName: String,
    val downloadedBytes: Long,
    val totalBytes: Long
)

data class FamilyNetworkState(
    val isRunning: Boolean = false,
    val isRegistered: Boolean = false,
    val isDiscovering: Boolean = false,
    val serviceType: String = FAMILY_SERVICE_TYPE,
    val serviceName: String = "",
    val localHostDisplayName: String = "",
    val port: Int = 0,
    val localIp: String? = null,
    val discoveredServices: List<FamilyDiscoveredService> = emptyList(),
    val localServiceEnabled: Boolean = true,
    val openBoardSession: BulletinBoardOpenSession? = null,
    val attachmentUpload: BulletinAttachmentUploadProgress? = null,
    val attachmentDownload: BulletinAttachmentDownloadProgress? = null,
    val rememberedBoardAccessCount: Int = 0,
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
    private val pendingResolveQueue = ArrayDeque<NsdServiceInfo>()
    private val pendingResolveNames = mutableSetOf<String>()
    private var resolveInProgress = false
    private val resolveQueueLock = Any()
    private var serverJob: Job? = null
    private var attachmentUploadJob: Job? = null
    private var attachmentDownloadJob: Job? = null
    private var currentInstanceId: String? = null
    private var started = false
    private var networkPassword: String? = null
    private var localServiceEnabled: Boolean = true
    private var familyNetworkUserName: String? = null
    private var familyNetworkHostName: String? = null
    private val boardAccessPasswordCache = mutableMapOf<String, String?>()

    fun hasRememberedBoardAccessPassword(deviceKey: String): Boolean =
        deviceKey in boardAccessPasswordCache

    fun getRememberedBoardAccessPassword(deviceKey: String): String? =
        boardAccessPasswordCache[deviceKey]

    fun rememberBoardAccessPassword(deviceKey: String, password: String?) {
        boardAccessPasswordCache[deviceKey] = password?.trim()?.ifEmpty { null }
        syncRememberedPasswordCount()
    }

    fun forgetBoardAccessPassword(deviceKey: String) {
        if (boardAccessPasswordCache.remove(deviceKey) != null) {
            syncRememberedPasswordCount()
        }
    }

    fun clearAllBoardAccessPasswords(): Int {
        val count = boardAccessPasswordCache.size
        boardAccessPasswordCache.clear()
        syncRememberedPasswordCount()
        return count
    }

    private fun syncRememberedPasswordCount() {
        _state.update { it.copy(rememberedBoardAccessCount = boardAccessPasswordCache.size) }
    }

    fun configure(
        networkPassword: String?,
        localServiceEnabled: Boolean,
        familyNetworkUserName: String? = null,
        familyNetworkHostName: String? = null
    ) {
        this.networkPassword = networkPassword?.trim()?.ifEmpty { null }
        val newHostName = familyNetworkHostName?.trim()?.ifEmpty { null }
        val hostNameChanged = this.familyNetworkHostName != newHostName
        this.familyNetworkHostName = newHostName
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
        } else if (hostNameChanged && localServiceEnabled && httpsServer != null) {
            reregisterMdnsService()
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
        val displayHostName = resolveLocalDisplayHostName(instanceId)
        val requestedServiceName = sanitizeMdnsServiceName(displayHostName)
        _state.update {
            it.copy(
                serviceName = requestedServiceName,
                localHostDisplayName = displayHostName,
                port = server.port,
                lastError = null
            )
        }
        val manager = nsdManager ?: return
        registerService(
            manager = manager,
            requestedServiceName = requestedServiceName,
            displayHostName = displayHostName,
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

    suspend fun fetchBoardList(
        service: FamilyDiscoveredService,
        accessPassword: String? = null
    ): Result<BulletinBoardListResult> = withContext(Dispatchers.IO) {
        if (service.isSelf) {
            runCatching {
                BulletinBoardListResult(
                    boards = boardStore.listBoards(),
                    canManage = true
                )
            }
        } else {
            boardApiRequest(
                service = service,
                method = "GET",
                path = "/boards",
                accessPassword = accessPassword?.trim()?.ifEmpty { null }
            ).mapCatching { text ->
                val json = JSONObject(text)
                if (!json.optBoolean("ok", false)) {
                    throw IllegalStateException(json.optString("message", "读取留言板列表失败"))
                }
                val boards = buildList {
                    val arr = json.optJSONArray("boards") ?: JSONArray()
                    for (i in 0 until arr.length()) {
                        add(BulletinBoardInfo.fromJson(arr.getJSONObject(i)))
                    }
                }
                BulletinBoardListResult(
                    boards = boards,
                    roleId = json.optString("role_id").takeIf { it.isNotBlank() },
                    roleLabel = json.optString("role_label").takeIf { it.isNotBlank() },
                    canManage = json.optBoolean("can_manage", false)
                )
            }
        }
    }

    suspend fun remoteCanManageBoard(
        service: FamilyDiscoveredService,
        accessPassword: String?,
        boardId: String = BulletinBoardDefaults.DEFAULT_BOARD_ID
    ): Boolean = withContext(Dispatchers.IO) {
        if (service.isSelf) return@withContext true
        fetchBoardList(service, accessPassword).getOrNull()?.canManage == true
    }

    fun createBoardEntry(
        service: FamilyDiscoveredService,
        accessPassword: String?,
        canManage: Boolean,
        name: String,
        onComplete: (Result<BulletinBoardInfo>) -> Unit = {}
    ) {
        if (!canManage) {
            appendError("只有宿主可以创建留言板。")
            onComplete(Result.failure(IllegalStateException("只有宿主可以创建留言板")))
            return
        }
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            appendError("创建留言板失败：名称不能为空。")
            onComplete(Result.failure(IllegalStateException("名称不能为空")))
            return
        }
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                if (service.isSelf) {
                    runCatching {
                        boardStore.createBoard(trimmed)
                            ?: throw IllegalStateException("创建留言板失败")
                    }
                } else {
                    boardApiRequest(
                        service = service,
                        method = "POST",
                        path = "/boards",
                        body = JSONObject().put("name", trimmed).toString(),
                        accessPassword = accessPassword?.trim()?.ifEmpty { null }
                    ).mapCatching { text ->
                        val json = JSONObject(text)
                        if (!json.optBoolean("ok", false)) {
                            throw IllegalStateException(json.optString("message", "创建留言板失败"))
                        }
                        BulletinBoardInfo.fromJson(json.getJSONObject("board"))
                    }
                }
            }
            result.onSuccess { board ->
                appendLog("留言板已创建：${board.name}")
            }.onFailure { error ->
                appendError("创建留言板失败：${error.message ?: error.javaClass.simpleName}")
            }
            onComplete(result)
        }
    }

    fun deleteBoardEntry(
        service: FamilyDiscoveredService,
        accessPassword: String?,
        canManage: Boolean,
        boardId: String,
        onComplete: (Result<Unit>) -> Unit = {}
    ) {
        if (!canManage) {
            appendError("只有宿主可以删除留言板。")
            onComplete(Result.failure(IllegalStateException("只有宿主可以删除留言板")))
            return
        }
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                if (service.isSelf) {
                    runCatching {
                        if (!boardStore.deleteBoard(boardId)) {
                            throw IllegalStateException("删除失败：留言板不存在，或这是最后一个留言板")
                        }
                    }
                } else {
                    boardApiRequest(
                        service = service,
                        method = "DELETE",
                        path = "/boards/$boardId",
                        accessPassword = accessPassword?.trim()?.ifEmpty { null }
                    ).mapCatching { text ->
                        val json = JSONObject(text)
                        if (!json.optBoolean("ok", false)) {
                            throw IllegalStateException(json.optString("message", "删除留言板失败"))
                        }
                    }
                }
            }
            result.onSuccess {
                appendLog("留言板已删除：$boardId")
                val open = _state.value.openBoardSession
                if (open?.service?.deviceKey == service.deviceKey && open.boardId == boardId) {
                    closeBulletinBoard()
                }
            }.onFailure { error ->
                appendError("删除留言板失败：${error.message ?: error.javaClass.simpleName}")
            }
            onComplete(result)
        }
    }

    fun exportOpenBoard(
        rootUri: String?,
        onComplete: ((Result<String>) -> Unit)? = null
    ) {
        val session = _state.value.openBoardSession ?: return
        if (rootUri.isNullOrBlank()) {
            val error = IllegalStateException("未设置根目录")
            appendError("导出失败：${error.message}")
            onComplete?.invoke(Result.failure(error))
            return
        }
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val content = if (session.isHost) {
                        val snapshot = boardStore.snapshot(session.boardId)
                            ?: throw IllegalStateException("留言板不存在：${session.boardId}")
                        BulletinBoardExporter.snapshotToMarkdown(snapshot)
                    } else {
                        boardApiTextRequest(
                            service = session.service,
                            method = "GET",
                            path = "/boards/${session.boardId}/export.md",
                            accessPassword = session.accessPassword
                        ).getOrThrow()
                    }
                    BulletinBoardExporter.saveMarkdownToRoot(
                        context = appContext,
                        rootUri = rootUri,
                        fileName = BulletinBoardExporter.defaultExportFileName(session.boardName),
                        markdown = content
                    ).getOrThrow()
                }
            }
            result.onSuccess { savedPath ->
                appendLog("已导出到 $savedPath")
            }.onFailure { error ->
                appendError("导出失败：${error.message ?: error.javaClass.simpleName}")
            }
            onComplete?.invoke(result)
        }
    }

    fun exportBoardpack(
        service: FamilyDiscoveredService,
        board: BulletinBoardInfo,
        accessPassword: String?,
        onComplete: (Result<ByteArray>) -> Unit = {}
    ) {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    if (service.isSelf) {
                        boardStore.exportBoardpack(board.id)
                            ?: throw IllegalStateException("留言板不存在：${board.id}")
                    } else {
                        boardApiBytesRequest(
                            service = service,
                            path = "/boards/${board.id}/export.boardpack",
                            accessPassword = accessPassword?.trim()?.ifEmpty { null }
                        ).getOrThrow()
                    }
                }
            }
            result.onFailure { error ->
                appendError("导出 boardpack 失败：${error.message ?: error.javaClass.simpleName}")
            }
            onComplete(result)
        }
    }

    fun saveBoardpack(
        uri: android.net.Uri,
        data: ByteArray,
        onComplete: (Result<Unit>) -> Unit = {}
    ) {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                BulletinBoardPack.writeToUri(appContext, uri, data)
            }
            result.onSuccess {
                appendLog("boardpack 已保存。")
            }.onFailure { error ->
                appendError("保存 boardpack 失败：${error.message ?: error.javaClass.simpleName}")
            }
            onComplete(result)
        }
    }

    fun saveBoardpackToRoot(
        rootUri: String,
        boardName: String,
        data: ByteArray,
        onComplete: (Result<String>) -> Unit = {}
    ) {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                BulletinBoardPack.saveToRoot(
                    context = appContext,
                    rootUri = rootUri,
                    fileName = BulletinBoardPack.defaultPackFileName(boardName),
                    data = data
                )
            }
            result.onSuccess { path ->
                appendLog("boardpack 已导出到 $path")
            }.onFailure { error ->
                appendError("导出 boardpack 失败：${error.message ?: error.javaClass.simpleName}")
            }
            onComplete(result)
        }
    }

    fun importBoardpackFromUri(
        uri: android.net.Uri,
        name: String? = null,
        roleIds: List<String>? = null,
        onComplete: (Result<BulletinBoardInfo>) -> Unit = {}
    ) {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = BulletinBoardPack.readFromUri(appContext, uri).getOrThrow()
                    boardStore.importBoardpack(bytes, name, roleIds)
                }
            }
            result.onSuccess { board ->
                appendLog("已导入留言板：${board.name}")
            }.onFailure { error ->
                appendError("导入 boardpack 失败：${error.message ?: error.javaClass.simpleName}")
            }
            onComplete(result)
        }
    }

    fun importBoardpackRemote(
        service: FamilyDiscoveredService,
        data: ByteArray,
        accessPassword: String?,
        name: String? = null,
        roleIds: List<String>? = null,
        onComplete: (Result<BulletinBoardInfo>) -> Unit = {}
    ) {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                if (service.isSelf) {
                    runCatching { boardStore.importBoardpack(data, name, roleIds) }
                } else {
                    importBoardpackViaApi(service, data, accessPassword, name, roleIds)
                }
            }
            result.onSuccess { board ->
                appendLog("已导入留言板：${board.name}")
            }.onFailure { error ->
                appendError("导入 boardpack 失败：${error.message ?: error.javaClass.simpleName}")
            }
            onComplete(result)
        }
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
                    val messagesUnchanged = open.revision == snapshot.revision &&
                        bulletinMessagesEqual(open.messages, snapshot.messages)
                    val metaUnchanged = messagesUnchanged &&
                        open.agents == snapshot.agents &&
                        open.participants == snapshot.participants &&
                        open.commands == snapshot.commands
                    if (metaUnchanged) {
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
                                agents = snapshot.agents,
                                participants = snapshot.participants,
                                commands = snapshot.commands,
                                canManageBoard = open.isHost || snapshot.canManage,
                                remoteRoleId = snapshot.roleId ?: open.remoteRoleId,
                                remoteRoleLabel = snapshot.roleLabel ?: open.remoteRoleLabel,
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

    fun postBoardMessage(content: String, attachments: List<BulletinAttachmentRef> = emptyList()) {
        val session = _state.value.openBoardSession ?: return
        val trimmed = content.trim()
        if (trimmed.isEmpty() && attachments.isEmpty()) {
            appendError("发送消息失败：消息内容与附件不能同时为空。")
            return
        }
        scope.launch {
            postBoardMessageInternal(session, trimmed, attachments)
        }
    }

    fun cancelAttachmentDownload() {
        attachmentDownloadJob?.cancel()
    }

    fun downloadBoardAttachment(
        rootUri: String,
        ref: BulletinAttachmentRef,
        conflict: BulletinAttachmentDownloadConflict = BulletinAttachmentDownloadConflict.OVERWRITE
    ) {
        val session = _state.value.openBoardSession ?: return
        if (rootUri.isBlank()) {
            appendError("下载失败：未设置根目录。")
            return
        }
        attachmentDownloadJob?.cancel()
        attachmentDownloadJob = scope.launch {
            val totalBytes = when (ref.kind) {
                BulletinAttachmentKind.FILE -> ref.size.coerceAtLeast(1L)
                BulletinAttachmentKind.DIRECTORY -> ref.totalSize.coerceAtLeast(ref.size).coerceAtLeast(1L)
            }
            try {
                _state.update {
                    it.copy(
                        attachmentDownload = BulletinAttachmentDownloadProgress(
                            attachmentId = ref.id,
                            itemName = ref.name,
                            downloadedBytes = 0L,
                            totalBytes = totalBytes
                        )
                    )
                }
                val transport = buildDownloadTransport(session)
                val result = withContext(Dispatchers.IO) {
                    BulletinAttachmentDownloader.downloadAttachment(
                        context = appContext,
                        rootUri = rootUri,
                        boardId = session.boardId,
                        ref = ref,
                        transport = transport,
                        conflict = conflict,
                        onProgress = { downloaded, total ->
                            _state.update { state ->
                                state.copy(
                                    attachmentDownload = BulletinAttachmentDownloadProgress(
                                        attachmentId = ref.id,
                                        itemName = ref.name,
                                        downloadedBytes = downloaded,
                                        totalBytes = total.coerceAtLeast(1L)
                                    )
                                )
                            }
                        }
                    )
                }
                result.onSuccess { saved ->
                    appendLog("已保存到 ${saved.savedPath}")
                }.onFailure { error ->
                    if (error is CancellationException) throw error
                    appendError("下载附件失败：${error.message ?: error.javaClass.simpleName}")
                }
            } catch (_: CancellationException) {
                appendLog("附件下载已取消")
            } finally {
                _state.update { it.copy(attachmentDownload = null) }
                attachmentDownloadJob = null
            }
        }
    }

    private fun buildDownloadTransport(session: BulletinBoardOpenSession): BulletinAttachmentDownloadTransport {
        return if (session.isHost) {
            LocalBulletinAttachmentDownloadTransport(boardStore)
        } else {
            RemoteBulletinAttachmentDownloadTransport(
                downloadBlobRequest = { path, rangeStart, rangeEnd ->
                    boardApiBlobDownload(session.service, path, rangeStart, rangeEnd, session.accessPassword)
                },
                fetchMeta = { boardId, attachmentId ->
                    boardApiRequest(
                        service = session.service,
                        method = "GET",
                        path = "/boards/$boardId/attachments/$attachmentId",
                        accessPassword = session.accessPassword
                    ).mapCatching { text ->
                        val json = JSONObject(text)
                        if (!json.has("id")) {
                            throw IllegalStateException(json.optString("message", "读取附件元数据失败"))
                        }
                        json
                    }
                }
            )
        }
    }

    fun uploadAttachmentAndPost(
        item: DocumentFileModel,
        textContent: String = ""
    ) {
        uploadAttachmentsAndPost(listOf(item), textContent)
    }

    fun uploadPickedAttachmentsAndPost(
        items: List<DocumentFileModel>,
        textContent: String = ""
    ) {
        uploadAttachmentsAndPost(items, textContent)
    }

    fun cancelAttachmentUpload() {
        attachmentUploadJob?.cancel()
    }

    private fun uploadAttachmentsAndPost(
        items: List<DocumentFileModel>,
        textContent: String = ""
    ) {
        val session = _state.value.openBoardSession ?: return
        if (items.isEmpty()) {
            appendError("上传失败：未选择任何文件或目录。")
            return
        }
        attachmentUploadJob?.cancel()
        attachmentUploadJob = scope.launch {
            var pendingAttachmentId: String? = null
            val primaryName = if (items.size == 1) items.first().name else "${items.size} 项附件"
            val totalBytes = items.sumOf { estimateUploadBytes(appContext, it) }
            try {
                _state.update {
                    it.copy(
                        attachmentUpload = BulletinAttachmentUploadProgress(
                            itemName = primaryName,
                            uploadedBytes = 0L,
                            totalBytes = totalBytes.coerceAtLeast(1L)
                        )
                    )
                }
                appendLog("开始上传附件到 ${session.service.displayHostName}…")
                val (_, device) = buildAuthorIdentity()
                val transport: BulletinAttachmentTransport = if (session.isHost) {
                    LocalBulletinAttachmentTransport(boardStore)
                } else {
                    RemoteBulletinAttachmentTransport(
                        service = session.service,
                        accessPassword = session.accessPassword,
                        requestJson = { method, path, body ->
                            boardApiRequest(session.service, method, path, body, session.accessPassword)
                        },
                        requestBinary = { method, path, bytes ->
                            boardApiBinaryRequest(session.service, method, path, bytes, session.accessPassword)
                        }
                    )
                }
                val uploadResult = withContext(Dispatchers.IO) {
                    BulletinAttachmentUploader.uploadItems(
                        context = appContext,
                        transport = transport,
                        boardId = session.boardId,
                        items = items,
                        uploaderDevice = device,
                        onAttachmentInit = { attachmentId -> pendingAttachmentId = attachmentId },
                        onProgress = { uploaded, total ->
                            _state.update { state ->
                                state.copy(
                                    attachmentUpload = BulletinAttachmentUploadProgress(
                                        itemName = primaryName,
                                        uploadedBytes = uploaded,
                                        totalBytes = total.coerceAtLeast(1L)
                                    )
                                )
                            }
                        }
                    )
                }
                _state.update { state ->
                    state.copy(
                        attachmentUpload = state.attachmentUpload?.copy(
                            phase = BulletinAttachmentUploadPhase.POSTING
                        )
                    )
                }
                uploadResult.onSuccess { refs ->
                    val body = textContent.trim().ifBlank { "[${refs.size} 个附件]" }
                    postBoardMessageInternal(session, body, refs)
                }.onFailure { error ->
                    if (error is CancellationException) throw error
                    appendError("上传附件失败：${error.message ?: error.javaClass.simpleName}")
                }
            } catch (_: CancellationException) {
                pendingAttachmentId?.let { attachmentId ->
                    abortIncompleteUpload(session, attachmentId)
                }
                appendLog("附件上传已取消")
            } finally {
                _state.update { it.copy(attachmentUpload = null) }
                attachmentUploadJob = null
            }
        }
    }

    private fun abortIncompleteUpload(session: BulletinBoardOpenSession, attachmentId: String) {
        scope.launch(Dispatchers.IO) {
            runCatching {
                if (session.isHost) {
                    boardStore.attachments.deleteAttachment(session.boardId, attachmentId)
                } else {
                    boardApiRequest(
                        service = session.service,
                        method = "DELETE",
                        path = "/boards/${session.boardId}/attachments/$attachmentId",
                        accessPassword = session.accessPassword
                    )
                }
            }.onFailure { error ->
                appendLog("清理未完成附件失败：${error.message ?: error.javaClass.simpleName}")
            }
        }
    }

    private fun estimateUploadBytes(context: android.content.Context, item: DocumentFileModel): Long {
        return if (item.isDirectory) {
            runCatching {
                BulletinAttachmentUploader.collectDirectoryEntries(context, item.uri.toString())
                    .sumOf { it.size }
            }.getOrDefault(0L).coerceAtLeast(1L)
        } else {
            item.size.coerceAtLeast(1L)
        }
    }

    private suspend fun postBoardMessageInternal(
        session: BulletinBoardOpenSession,
        content: String,
        attachments: List<BulletinAttachmentRef>
    ) {
        val (authorLabel, authorDevice) = buildAuthorIdentity()
        val result = withContext(Dispatchers.IO) {
            if (session.isHost) {
                runCatching {
                    boardStore.appendMessage(
                        session.boardId,
                        authorLabel,
                        content,
                        authorDevice,
                        attachments
                    ) ?: throw IllegalStateException(
                        if (attachments.isNotEmpty()) "消息无效或附件未就绪" else "消息内容不能为空或留言板不存在"
                    )
                }.map { }
            } else {
                boardApiRequest(
                    service = session.service,
                    method = "POST",
                    path = "/boards/${session.boardId}/messages",
                    body = JSONObject().apply {
                        put("content", content)
                        put("author_label", authorLabel)
                        put("author_device", authorDevice)
                        if (attachments.isNotEmpty()) {
                            put("attachments", JSONArray(attachments.map { it.toJson() }))
                        }
                    }.toString(),
                    accessPassword = session.accessPassword
                )
            }
        }
        result.onSuccess {
            appendLog("留言已发布到 ${session.service.displayHostName}/${session.boardId}")
            refreshOpenBoard()
        }.onFailure { error ->
            appendError("发布留言失败：${error.message ?: error.javaClass.simpleName}")
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
        displayHostName: String,
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
            setAttribute(FamilyNetworkHostNames.TXT_HOST_NAME, displayHostName)
            setAttribute("platform", "android")
            setAttribute("tls", "1")
            setAttribute(FAMILY_TLS_FINGERPRINT_ATTR, tlsFingerprint)
            if (!networkPassword.isNullOrBlank()) {
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
                        localHostDisplayName = displayHostName,
                        port = info.port.takeIf { value -> value > 0 } ?: port,
                        lastError = null
                    )
                }
                publishLocalSelfService(
                    host = _state.value.localIp,
                    port = info.port.takeIf { value -> value > 0 } ?: port,
                    instanceId = instanceId,
                    displayHostName = displayHostName,
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
                if (!serviceTypeMatches(serviceInfo.serviceType)) return
                val candidateName = serviceInfo.serviceName?.trim().orEmpty()
                if (candidateName.isEmpty()) return
                appendLog("发现候选服务：$candidateName")
                enqueueResolve(serviceInfo)
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                appendLog("服务离线通知：${serviceInfo.serviceName}。为避免 Android NSD 误报，这里暂时保留上次成功解析的入口。")
            }
        }
        discoveryListener = listener
        manager.discoverServices(FAMILY_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    private fun serviceTypeMatches(reportedType: String?): Boolean {
        val normalized = reportedType?.trim()?.trimEnd('.').orEmpty()
        val expected = FAMILY_SERVICE_TYPE.trimEnd('.')
        return normalized == expected || normalized == "$expected.local"
    }

    private fun enqueueResolve(serviceInfo: NsdServiceInfo) {
        val serviceName = serviceInfo.serviceName?.trim().orEmpty()
        if (serviceName.isEmpty()) return
        synchronized(resolveQueueLock) {
            if (serviceName in pendingResolveNames) return
            pendingResolveNames.add(serviceName)
            pendingResolveQueue.addLast(serviceInfo)
        }
        drainResolveQueue()
    }

    private fun drainResolveQueue() {
        val manager = nsdManager ?: return
        val next = synchronized(resolveQueueLock) {
            if (resolveInProgress || pendingResolveQueue.isEmpty()) return
            resolveInProgress = true
            pendingResolveQueue.removeFirst()
        }
        manager.resolveService(next, object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                val name = serviceInfo.serviceName?.trim().orEmpty()
                synchronized(resolveQueueLock) {
                    pendingResolveNames.remove(name)
                    resolveInProgress = false
                    if (errorCode == NsdManager.FAILURE_ALREADY_ACTIVE) {
                        pendingResolveNames.add(name)
                        pendingResolveQueue.addFirst(serviceInfo)
                    }
                }
                appendError("解析服务失败，name=${serviceInfo.serviceName}，错误码=$errorCode")
                drainResolveQueue()
            }

            override fun onServiceResolved(resolved: NsdServiceInfo) {
                val name = resolved.serviceName?.trim().orEmpty()
                synchronized(resolveQueueLock) {
                    pendingResolveNames.remove(name)
                    resolveInProgress = false
                }
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
                appendLog("解析到服务：${entry.displayHostName} -> ${entry.host}:${entry.port}${if (entry.isSelf) "（本机）" else ""}")
                upsertDiscoveredService(entry)
                drainResolveQueue()
            }
        })
    }

    private fun clearResolveQueue() {
        synchronized(resolveQueueLock) {
            pendingResolveQueue.clear()
            pendingResolveNames.clear()
            resolveInProgress = false
        }
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
                        .thenBy { it.displayHostName.lowercase() }
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
        val displayHostName = resolveLocalDisplayHostName(instanceId)
        publishLocalSelfService(_state.value.localIp, port, instanceId, displayHostName, tlsFingerprint)
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
        displayHostName: String,
        tlsFingerprint: String
    ) {
        if (!localServiceEnabled) return
        if (port <= 0) return
        val serviceName = _state.value.serviceName.ifBlank { sanitizeMdnsServiceName(displayHostName) }
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
                    FamilyNetworkHostNames.TXT_HOST_NAME to displayHostName,
                    "platform" to "android",
                    "tls" to "1",
                    FAMILY_TLS_FINGERPRINT_ATTR to tlsFingerprint
                ),
                isSelf = true
            )
        )
        appendLog("本机留言板服务已加入发现列表：$displayHostName（$resolvedHost:$port）")
    }

    private fun reregisterMdnsService() {
        if (!localServiceEnabled || httpsServer == null) return
        val instanceId = currentInstanceId ?: return
        val port = _state.value.port.takeIf { it > 0 } ?: httpsServer?.port ?: return
        val displayHostName = resolveLocalDisplayHostName(instanceId)
        val requestedServiceName = sanitizeMdnsServiceName(displayHostName)
        _state.update {
            it.copy(
                serviceName = requestedServiceName,
                localHostDisplayName = displayHostName
            )
        }
        unregisterServiceInternal()
        val manager = nsdManager ?: return
        val tlsFingerprint = tlsManager.localIdentity().fingerprintSha256
        registerService(
            manager = manager,
            requestedServiceName = requestedServiceName,
            displayHostName = displayHostName,
            port = port,
            instanceId = instanceId,
            tlsFingerprint = tlsFingerprint
        )
        appendLog("主机名已更新，重新广播 mDNS：$displayHostName")
    }

    private fun resolveLocalDisplayHostName(instanceId: String): String =
        FamilyNetworkHostNames.resolveDisplayHostName(appContext, familyNetworkHostName, instanceId)

    private fun sanitizeMdnsServiceName(displayHostName: String): String =
        FamilyNetworkHostNames.sanitizeMdnsServiceName(displayHostName)

    private fun handleHttpRequest(
        method: String,
        path: String,
        bodyBytes: ByteArray,
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
            return boardHttpHandler.handle(method, path, bodyBytes, authLevel, headers)
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
        if (method == "GET" && path == "/agent") {
            return FamilyHttpResponse(
                200,
                JSONObject().apply {
                    put("ok", true)
                    put("enabled", false)
                    put("models", JSONArray())
                    put("board_ids", JSONObject.NULL)
                    put("commands", JSONArray())
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
        if (guest.isNullOrBlank()) {
            return FamilyNetworkAuthLevel.OPEN
        }
        val provided = headers[FamilyNetworkAuth.PASSWORD_HEADER.lowercase(Locale.ROOT)]?.trim().orEmpty()
        if (provided.isEmpty()) return null
        if (provided == guest) {
            return FamilyNetworkAuthLevel.GUEST
        }
        return null
    }

    private fun loadLocalBoardSnapshot(boardId: String): Result<BulletinBoardSnapshot> {
        return runCatching {
            val snapshot = boardStore.snapshot(boardId)
                ?: throw IllegalStateException("留言板不存在：$boardId")
            snapshot.copy(
                canManage = true,
                agents = emptyList(),
                participants = BulletinBoardMention.collectParticipants(snapshot.messages),
                commands = emptyList()
            )
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
                canManage = json.optBoolean("can_manage", false),
                roleId = json.optString("role_id").takeIf { it.isNotBlank() },
                roleLabel = json.optString("role_label").takeIf { it.isNotBlank() },
                agents = BulletinBoardSnapshot.parseAgents(json),
                participants = BulletinBoardSnapshot.parseParticipants(json).ifEmpty {
                    BulletinBoardMention.collectParticipants(messages)
                },
                commands = BulletinBoardSnapshot.parseCommands(json)
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
                readApiResponse(connection)
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun boardApiTextRequest(
        service: FamilyDiscoveredService,
        method: String,
        path: String,
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
                connectTimeout = 15000
                readTimeout = 60000
                setRequestProperty("Accept", "text/markdown, text/plain, */*")
                accessPassword?.let {
                    setRequestProperty(FamilyNetworkAuth.PASSWORD_HEADER, it)
                }
            }
            try {
                readApiResponse(connection)
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun boardApiBlobDownload(
        service: FamilyDiscoveredService,
        path: String,
        rangeStart: Long,
        rangeEndInclusive: Long,
        accessPassword: String? = null
    ): Result<BlobDownloadResult> {
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
                requestMethod = "GET"
                connectTimeout = 15000
                readTimeout = 120000
                setRequestProperty("Accept", "application/octet-stream")
                setRequestProperty("Range", "bytes=$rangeStart-$rangeEndInclusive")
                accessPassword?.let {
                    setRequestProperty(FamilyNetworkAuth.PASSWORD_HEADER, it)
                }
            }
            try {
                val status = connection.responseCode
                if (status !in listOf(200, 206)) {
                    val detail = connection.errorStream?.use { readResponseText(it) }.orEmpty()
                    throw IllegalStateException(detail.ifBlank { "HTTP $status" })
                }
                val bytes = connection.inputStream.use { it.readBytes() }
                val contentRange = connection.getHeaderField("Content-Range")
                val totalSize = parseTotalSizeFromContentRange(contentRange)
                    ?: connection.contentLengthLong.takeIf { it > 0 }
                    ?: (rangeEndInclusive + 1)
                BlobDownloadResult(status, bytes, totalSize, contentRange)
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun parseTotalSizeFromContentRange(header: String?): Long? {
        if (header.isNullOrBlank()) return null
        val total = header.substringAfter('/', "").toLongOrNull()
        return total?.takeIf { it > 0 }
    }

    private fun boardApiBytesRequest(
        service: FamilyDiscoveredService,
        path: String,
        accessPassword: String? = null
    ): Result<ByteArray> {
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
                requestMethod = "GET"
                connectTimeout = 15000
                readTimeout = 300000
                setRequestProperty("Accept", "application/zip, application/octet-stream, */*")
                accessPassword?.let {
                    setRequestProperty(FamilyNetworkAuth.PASSWORD_HEADER, it)
                }
            }
            try {
                val status = connection.responseCode
                if (status !in 200..299) {
                    val detail = connection.errorStream?.use { readResponseText(it) }.orEmpty()
                    throw IllegalStateException(detail.ifBlank { "HTTP $status" })
                }
                connection.inputStream.use { it.readBytes() }
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun importBoardpackViaApi(
        service: FamilyDiscoveredService,
        data: ByteArray,
        accessPassword: String?,
        name: String?,
        roleIds: List<String>?
    ): Result<BulletinBoardInfo> {
        val query = buildList {
            name?.trim()?.takeIf { it.isNotEmpty() }?.let { add("name=${java.net.URLEncoder.encode(it, "UTF-8")}") }
            if (roleIds != null) {
                add(
                    "role_ids=${
                        java.net.URLEncoder.encode(roleIds.joinToString(","), "UTF-8")
                    }"
                )
            }
        }.joinToString("&")
        val path = if (query.isBlank()) "/boards/import" else "/boards/import?$query"
        return runCatching {
            val response = boardApiBinaryImportRequest(
                service = service,
                path = path,
                body = data,
                accessPassword = accessPassword?.trim()?.ifEmpty { null }
            ).getOrThrow()
            val json = JSONObject(response)
            if (!json.optBoolean("ok", false)) {
                throw IllegalStateException(json.optString("message", "导入留言板失败"))
            }
            BulletinBoardInfo.fromJson(json.getJSONObject("board"))
        }
    }

    private fun boardApiBinaryImportRequest(
        service: FamilyDiscoveredService,
        path: String,
        body: ByteArray,
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
                requestMethod = "POST"
                connectTimeout = 15000
                readTimeout = 300000
                doOutput = true
                setRequestProperty("Content-Type", "application/vnd.localmanager.boardpack+zip")
                accessPassword?.let {
                    setRequestProperty(FamilyNetworkAuth.PASSWORD_HEADER, it)
                }
            }
            try {
                connection.outputStream.use { output -> output.write(body) }
                readApiResponse(connection)
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun boardApiBinaryRequest(
        service: FamilyDiscoveredService,
        method: String,
        path: String,
        body: ByteArray,
        accessPassword: String? = null
    ): Result<Unit> {
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
                connectTimeout = 15000
                readTimeout = 15000
                doOutput = true
                setRequestProperty("Content-Type", "application/octet-stream")
                accessPassword?.let {
                    setRequestProperty(FamilyNetworkAuth.PASSWORD_HEADER, it)
                }
            }
            try {
                connection.outputStream.use { output -> output.write(body) }
                readApiResponse(connection)
                Unit
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun readApiResponse(connection: HttpURLConnection): String {
        val status = connection.responseCode
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        val responseText = stream?.use { readResponseText(it) }.orEmpty()
        if (status !in 200..299) {
            val detail = runCatching { JSONObject(responseText).optString("message") }.getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?: responseText.ifBlank { "HTTP $status" }
            throw IllegalStateException(detail)
        }
        return responseText
    }

    private fun stopDiscoveryInternal() {
        clearResolveQueue()
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

    private fun buildAuthorIdentity(): Pair<String, String> {
        val instanceId = currentInstanceId.orEmpty()
        val deviceLabel = _state.value.localHostDisplayName.ifBlank {
            resolveLocalDisplayHostName(instanceId)
        }
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
    private val handleRequest: (String, String, ByteArray, String, Map<String, String>) -> FamilyHttpResponse
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
                        bodyBytes,
                        client.inetAddress?.hostAddress ?: "",
                        headers
                    )
                }
                else -> FamilyHttpResponse(405, "{\"ok\":false,\"error\":\"method_not_allowed\"}")
            }
            val responseHead = buildString {
                append("HTTP/1.1 ${response.statusCode} ${httpStatusText(response.statusCode)}\r\n")
                append("Content-Type: ${response.contentType}\r\n")
                append("Content-Length: ${response.bodyBytes.size}\r\n")
                response.extraHeaders.forEach { (name, value) ->
                    append("$name: $value\r\n")
                }
                append("Connection: close\r\n")
                append("\r\n")
            }.toByteArray(StandardCharsets.ISO_8859_1)
            output.write(responseHead)
            output.write(response.bodyBytes)
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