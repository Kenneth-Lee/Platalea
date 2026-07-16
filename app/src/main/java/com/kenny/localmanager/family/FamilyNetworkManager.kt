package com.kenny.localmanager.family

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import com.kenny.localmanager.file.DocumentFileModel
import com.kenny.localmanager.R
import com.kenny.localmanager.util.getLocalIpAddress
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
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
import kotlinx.coroutines.delay
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
private const val FAMILY_IPV4_LIST_ATTR = "ipv4_list"

const val DEFAULT_DOWNLOAD_CHUNK_SIZE_BYTES = 1024L * 1024L

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

    /** mDNS TXT `power_shutdown=1` 表示对端支持远程关机。 */
    val supportsPowerShutdown: Boolean
        get() = attributes["power_shutdown"]?.trim() == "1"
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
    val totalBytes: Long,
    val speedBytesPerSecond: Long = 0L
)

data class BulletinForwardTarget(
    val service: FamilyDiscoveredService,
    val boardId: String,
    val boardName: String,
    val accessPassword: String?
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
    private val userRolesStore = FamilyUserRolesStore(appContext)
    private val boardHttpHandler = BulletinBoardHttpHandler(appContext, boardStore)
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
    private var stopIfIdleJob: Job? = null
    private var currentInstanceId: String? = null
    private var started = false
    /** 家庭网络 Tab 是否在前台。 */
    private var familyTabActive = false
    /** 转发等临时操作持有会话（如文件浏览器转发留言板）。 */
    private var ephemeralSessionCount = 0
    private var networkPassword: String? = null
    private var userRoles: List<FamilyUserRole> = emptyList()
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

    init {
        reloadUserRoles()
    }

    fun listUserRoles(): List<FamilyUserRole> = userRolesStore.listRoles()

    fun saveUserRoles(roles: List<FamilyUserRole>): Result<Unit> {
        val validated = FamilyNetworkRoles.validateUserRolesAgainstAdmin(networkPassword, roles)
        if (validated.isFailure) {
            return Result.failure(validated.exceptionOrNull() ?: IllegalStateException("invalid roles"))
        }
        return userRolesStore.replaceRoles(roles).also { result ->
            if (result.isSuccess) reloadUserRoles()
        }
    }

    fun migrateUserRolesFromLegacyConfig(legacyExtraRolesText: String?) {
        if (userRolesStore.migrateFromLegacyExtraRolesText(legacyExtraRolesText)) {
            reloadUserRoles()
        }
    }

    private fun reloadUserRoles() {
        userRoles = userRolesStore.listRoles()
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

    /** 由 UI 在家庭网络 Tab 显示/隐藏时调用，替代直接 start/stop。 */
    fun setFamilyTabActive(active: Boolean) {
        if (familyTabActive == active) return
        familyTabActive = active
        if (active) {
            stopIfIdleJob?.cancel()
            ensureStarted()
            refreshDiscoveryAfterActivation()
        } else {
            if (_state.value.openBoardSession != null) {
                closeBulletinBoard()
            }
            stopIfIdle()
        }
    }

    /** 临时启动发现/本机服务（如文件浏览器转发留言板），与 Tab 生命周期独立。 */
    fun beginEphemeralSession() {
        ephemeralSessionCount++
        ensureStarted()
    }

    fun endEphemeralSession() {
        ephemeralSessionCount = maxOf(0, ephemeralSessionCount - 1)
        stopIfIdle()
    }

    /** 本机留言板在发现列表中的条目；服务未启动时返回 null。 */
    fun resolveLocalSelfService(): FamilyDiscoveredService? {
        if (!localServiceEnabled) return null
        _state.value.discoveredServices.firstOrNull { it.isSelf }?.let { return it }
        val instanceId = currentInstanceId ?: return null
        val port = httpsServer?.port?.takeIf { it > 0 } ?: _state.value.port.takeIf { it > 0 } ?: return null
        val displayHostName = resolveLocalDisplayHostName(instanceId)
        val serviceName = _state.value.serviceName.ifBlank { sanitizeMdnsServiceName(displayHostName) }
        val resolvedHost = _state.value.localIp?.trim()?.takeIf { it.isNotEmpty() } ?: "127.0.0.1"
        val tlsFingerprint = tlsManager.localIdentity().fingerprintSha256
        return FamilyDiscoveredService(
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
    }

    private fun ensureStarted() {
        if (!started) start()
    }

    private fun stopIfIdle() {
        stopIfIdleJob?.cancel()
        if (familyTabActive || ephemeralSessionCount > 0) return
        if (attachmentUploadJob?.isActive == true) return
        stopIfIdleJob = scope.launch {
            delay(400)
            if (familyTabActive || ephemeralSessionCount > 0) return@launch
            if (attachmentUploadJob?.isActive == true) return@launch
            stop()
        }
    }

    /** 从其他 Tab 切回家庭网络时，若远端设备未出现在列表中则重新发现。 */
    private fun refreshDiscoveryAfterActivation() {
        if (!started) return
        val snapshot = _state.value
        if (snapshot.isDiscovering && snapshot.discoveredServices.any { !it.isSelf }) return
        scope.launch {
            delay(150)
            if (!familyTabActive || !started) return@launch
            refresh()
        }
    }

    fun start() {
        if (started) return
        started = true
        appendLog(appContext.getString(R.string.family_msg_68492))
        val manager = nsdManager
        if (manager == null) {
            val message = appContext.getString(R.string.family_msg_58365)
            appendError(message)
            started = false
            return
        }
        acquireMulticastLock()
        val localIp = try {
            getLocalIpAddress()
        } catch (e: Throwable) {
            appendLog(appContext.getString(R.string.family_msg_55245, e.message ?: e.javaClass.simpleName))
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
                appendLog(appContext.getString(R.string.family_msg_40781))
            }
            startDiscovery(clearDiscovered = false)
        } catch (e: Throwable) {
            appendError(appContext.getString(R.string.family_msg_93246, e.message ?: e.javaClass.simpleName))
            Log.e(TAG, "start failed", e)
            stop()
        }
    }

    private fun startLocalServerStack(localIp: String?) {
        if (!localServiceEnabled || httpsServer != null) return
        val localIdentity = tlsManager.localIdentity()
        val server = EmbeddedHttpServer(
            context = appContext,
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
        appendLog(appContext.getString(R.string.family_msg_55407, server.port, localIdentity.fingerprintSha256))
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
            tlsFingerprint = localIdentity.fingerprintSha256,
            localIp = localIp
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
        appendLog(appContext.getString(R.string.family_msg_39692))
    }

    fun refresh() {
        if (!started) {
            appendLog(appContext.getString(R.string.family_msg_00574))
            start()
            return
        }
        appendLog(appContext.getString(R.string.family_msg_84496))
        _state.update { current ->
            current.copy(discoveredServices = current.discoveredServices.filter { it.isSelf }.take(1))
        }
        startDiscovery(clearDiscovered = false)
        republishLocalSelfServiceIfRunning()
    }

    fun openBulletinBoard(
        service: FamilyDiscoveredService,
        boardId: String = BulletinBoardDefaults.DEFAULT_BOARD_ID,
        boardName: String = BulletinBoardDefaults.DEFAULT_BOARD_NAME,
        boardRoleIds: List<String>? = null,
        accessPassword: String? = null
    ) {
        if (service.isSelf && !localServiceEnabled) {
            appendError(appContext.getString(R.string.family_msg_83716))
            return
        }
        val isHost = service.isSelf
        _state.update {
            it.copy(
                openBoardSession = BulletinBoardOpenSession(
                    service = service,
                    boardId = boardId,
                    boardName = boardName,
                    boardRoleIds = boardRoleIds,
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
            return Result.failure(IllegalStateException(appContext.getString(R.string.family_msg_18983)))
        }
        if (service.isSelf) {
            return withContext(Dispatchers.IO) {
                if (boardStore.snapshot(boardId) != null) {
                    Result.success(Unit)
                } else {
                    Result.failure(IllegalStateException(appContext.getString(R.string.family_msg_71757)))
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
                    canManage = true,
                    roleId = "admin",
                    roleClass = "admin"
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
                    throw IllegalStateException(json.optString("message", appContext.getString(R.string.family_msg_80883)))
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
                    roleClass = json.optString("role_class").takeIf { it.isNotBlank() },
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

    fun requestRemotePowerShutdown(
        service: FamilyDiscoveredService,
        accessPassword: String?,
        onComplete: (Result<String>) -> Unit = {}
    ) {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                if (service.isSelf) {
                    Result.failure(IllegalStateException(appContext.getString(R.string.family_msg_18983)))
                } else {
                    boardApiRequest(
                        service = service,
                        method = "POST",
                        path = "/power/shutdown",
                        accessPassword = accessPassword?.trim()?.ifEmpty { null }
                    ).mapCatching { text ->
                        val json = JSONObject(text)
                        if (!json.optBoolean("ok", false)) {
                            throw IllegalStateException(json.optString("message", appContext.getString(R.string.family_msg_19687)))
                        }
                        json.optString("message", appContext.getString(R.string.family_msg_19687))
                    }
                }
            }
            result.onSuccess {
                appendLog(appContext.getString(R.string.family_board_remote_shutdown_requested, service.displayHostName) + "：" + it)
            }.onFailure { error ->
                appendError(appContext.getString(R.string.family_board_remote_shutdown_failed, error.message ?: error.javaClass.simpleName))
            }
            onComplete(result)
        }
    }

    /** 判断失败是否更可能是密码/权限问题，而非网络或 TLS 问题。 */
    fun isLikelyAuthFailure(error: Throwable): Boolean {
        var current: Throwable? = error
        while (current != null) {
            if (current is FamilyApiException) {
                if (current.statusCode == 401 || current.statusCode == 403) {
                    return true
                }
                val detailLower = current.detail.lowercase(Locale.ROOT)
                if (
                    detailLower.contains("unauthorized") ||
                    detailLower.contains("forbidden") ||
                    current.detail.contains("需要正确的网络服务密码") ||
                    current.detail.contains("管理员权限")
                ) {
                    return true
                }
            }
            val message = current.message.orEmpty()
            val messageLower = message.lowercase(Locale.ROOT)
            if (
                messageLower.contains("unauthorized") ||
                messageLower.contains("forbidden") ||
                message.contains("需要正确的网络服务密码") ||
                message.contains("管理员权限")
            ) {
                return true
            }
            current = current.cause
        }
        return false
    }

    fun createBoardEntry(
        service: FamilyDiscoveredService,
        accessPassword: String?,
        canManage: Boolean,
        name: String,
        roleIds: List<String> = emptyList(),
        onComplete: (Result<BulletinBoardInfo>) -> Unit = {}
    ) {
        if (!canManage) {
            appendError(appContext.getString(R.string.family_msg_64323))
            onComplete(Result.failure(IllegalStateException(appContext.getString(R.string.family_msg_24175))))
            return
        }
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            appendError(appContext.getString(R.string.family_msg_99038))
            onComplete(Result.failure(IllegalStateException(appContext.getString(R.string.family_msg_57102))))
            return
        }
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                if (service.isSelf) {
                    runCatching {
                        if (boardStore.isBoardNameTaken(trimmed)) {
                            throw IllegalStateException(appContext.getString(R.string.family_msg_21357))
                        }
                        boardStore.createBoard(trimmed, roleIds)
                            ?: throw IllegalStateException(appContext.getString(R.string.family_msg_64304))
                    }
                } else {
                    boardApiRequest(
                        service = service,
                        method = "POST",
                        path = "/boards",
                        body = JSONObject().apply {
                            put("name", trimmed)
                            put("role_ids", JSONArray(roleIds))
                        }.toString(),
                        accessPassword = accessPassword?.trim()?.ifEmpty { null }
                    ).mapCatching { text ->
                        val json = JSONObject(text)
                        if (!json.optBoolean("ok", false)) {
                            throw IllegalStateException(json.optString("message", appContext.getString(R.string.family_msg_64304)))
                        }
                        BulletinBoardInfo.fromJson(json.getJSONObject("board"))
                    }
                }
            }
            result.onSuccess { board ->
                appendLog(appContext.getString(R.string.family_msg_41249, board.name))
            }.onFailure { error ->
                appendError(appContext.getString(R.string.family_msg_36032, error.message ?: error.javaClass.simpleName))
            }
            onComplete(result)
        }
    }

    fun renameBoardEntry(
        service: FamilyDiscoveredService,
        accessPassword: String?,
        canManage: Boolean,
        boardId: String,
        newName: String,
        onComplete: (Result<BulletinBoardInfo>) -> Unit = {}
    ) {
        if (!canManage) {
            appendError(appContext.getString(R.string.family_msg_34578))
            onComplete(Result.failure(IllegalStateException(appContext.getString(R.string.family_msg_02158))))
            return
        }
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) {
            appendError(appContext.getString(R.string.family_msg_90252))
            onComplete(Result.failure(IllegalStateException(appContext.getString(R.string.family_msg_57102))))
            return
        }
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                if (service.isSelf) {
                    runCatching {
                        if (boardStore.isBoardNameTaken(trimmed, excludeBoardId = boardId)) {
                            throw IllegalStateException(appContext.getString(R.string.family_msg_21357))
                        }
                        boardStore.renameBoard(boardId, trimmed)
                            ?: throw IllegalStateException(appContext.getString(R.string.family_msg_90260))
                    }
                } else {
                    boardApiRequest(
                        service = service,
                        method = "PATCH",
                        path = "/boards/$boardId",
                        body = JSONObject().put("name", trimmed).toString(),
                        accessPassword = accessPassword?.trim()?.ifEmpty { null }
                    ).mapCatching { text ->
                        val json = JSONObject(text)
                        if (!json.optBoolean("ok", false)) {
                            throw IllegalStateException(json.optString("message", appContext.getString(R.string.family_msg_81118)))
                        }
                        BulletinBoardInfo.fromJson(json.getJSONObject("board"))
                    }
                }
            }
            result.onSuccess { board ->
                appendLog(appContext.getString(R.string.family_msg_15134, board.name))
                val open = _state.value.openBoardSession
                if (open?.service?.deviceKey == service.deviceKey && open.boardId == boardId) {
                    _state.update { current ->
                        current.copy(
                            openBoardSession = open.copy(boardName = board.name)
                        )
                    }
                }
            }.onFailure { error ->
                appendError(appContext.getString(R.string.family_msg_18847, error.message ?: error.javaClass.simpleName))
            }
            onComplete(result)
        }
    }

    fun updateBoardAccessEntry(
        service: FamilyDiscoveredService,
        accessPassword: String?,
        canManage: Boolean,
        boardId: String,
        roleIds: List<String>,
        onComplete: (Result<BulletinBoardInfo>) -> Unit = {}
    ) {
        if (!canManage) {
            appendError(appContext.getString(R.string.family_msg_34578))
            onComplete(Result.failure(IllegalStateException(appContext.getString(R.string.family_msg_02158))))
            return
        }
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                if (service.isSelf) {
                    runCatching {
                        boardStore.updateBoard(boardId, roleIds = roleIds)
                            ?: throw IllegalStateException(appContext.getString(R.string.family_msg_81118))
                    }
                } else {
                    boardApiRequest(
                        service = service,
                        method = "PATCH",
                        path = "/boards/$boardId",
                        body = JSONObject().apply {
                            put("role_ids", JSONArray(roleIds))
                        }.toString(),
                        accessPassword = accessPassword?.trim()?.ifEmpty { null }
                    ).mapCatching { text ->
                        val json = JSONObject(text)
                        if (!json.optBoolean("ok", false)) {
                            throw IllegalStateException(json.optString("message", appContext.getString(R.string.family_msg_81118)))
                        }
                        BulletinBoardInfo.fromJson(json.getJSONObject("board"))
                    }
                }
            }
            result.onSuccess { board ->
                appendLog(appContext.getString(R.string.family_msg_15134, board.name))
                val open = _state.value.openBoardSession
                if (open?.service?.deviceKey == service.deviceKey && open.boardId == boardId) {
                    _state.update { current ->
                        current.copy(
                            openBoardSession = open.copy(boardRoleIds = board.roleIds)
                        )
                    }
                }
            }.onFailure { error ->
                appendError(appContext.getString(R.string.family_msg_18847, error.message ?: error.javaClass.simpleName))
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
            appendError(appContext.getString(R.string.family_msg_63710))
            onComplete(Result.failure(IllegalStateException(appContext.getString(R.string.family_msg_22771))))
            return
        }
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                if (service.isSelf) {
                    runCatching {
                        if (!boardStore.deleteBoard(boardId)) {
                            throw IllegalStateException(appContext.getString(R.string.family_msg_96259))
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
                            throw IllegalStateException(json.optString("message", appContext.getString(R.string.family_msg_86488)))
                        }
                    }
                }
            }
            result.onSuccess {
                appendLog(appContext.getString(R.string.family_msg_83171))
                val open = _state.value.openBoardSession
                if (open?.service?.deviceKey == service.deviceKey && open.boardId == boardId) {
                    closeBulletinBoard()
                }
            }.onFailure { error ->
                appendError(appContext.getString(R.string.family_msg_13838, error.message ?: error.javaClass.simpleName))
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
            val error = IllegalStateException(appContext.getString(R.string.family_msg_74491))
            appendError(appContext.getString(R.string.family_msg_88483, error.message))
            onComplete?.invoke(Result.failure(error))
            return
        }
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val content = if (session.isHost) {
                        val snapshot = boardStore.snapshot(session.boardId)
                            ?: throw IllegalStateException(appContext.getString(R.string.family_msg_17263, session.boardId))
                        BulletinBoardExporter.snapshotToMarkdown(appContext, snapshot)
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
                appendLog(appContext.getString(R.string.family_msg_17812))
            }.onFailure { error ->
                appendError(appContext.getString(R.string.family_msg_12658, error.message ?: error.javaClass.simpleName))
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
                            ?: throw IllegalStateException(appContext.getString(R.string.family_msg_65793, board.id))
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
                appendError(appContext.getString(R.string.family_msg_60609, error.message ?: error.javaClass.simpleName))
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
                appendLog(appContext.getString(R.string.family_msg_42003))
            }.onFailure { error ->
                appendError(appContext.getString(R.string.family_msg_11426, error.message ?: error.javaClass.simpleName))
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
                appendLog(appContext.getString(R.string.family_msg_60034))
            }.onFailure { error ->
                appendError(appContext.getString(R.string.family_msg_60609, error.message ?: error.javaClass.simpleName))
            }
            onComplete(result)
        }
    }

    fun importBoardpackFromFile(
        file: DocumentFileModel,
        service: FamilyDiscoveredService,
        accessPassword: String?,
        importName: String? = null,
        onComplete: (Result<BulletinBoardInfo>) -> Unit = {}
    ) {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = BulletinBoardPack.readFromDocumentFile(appContext, file).getOrThrow()
                    val trimmedName = importName?.trim()?.takeIf { it.isNotEmpty() }
                    if (service.isSelf) {
                        boardStore.importBoardpack(bytes, name = trimmedName)
                    } else {
                        importBoardpackViaApi(
                            service = service,
                            data = bytes,
                            accessPassword = accessPassword?.trim()?.ifEmpty { null },
                            name = trimmedName,
                            roleIds = null,
                        ).getOrThrow()
                    }
                }
            }
            result.onSuccess { board ->
                appendLog(appContext.getString(R.string.family_msg_56747, board.name))
            }.onFailure { error ->
                appendError(appContext.getString(R.string.family_msg_99856, error.message ?: error.javaClass.simpleName))
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
                appendError(appContext.getString(R.string.family_msg_19687))
            }
        }
    }

    fun postBoardMessage(content: String, attachments: List<BulletinAttachmentRef> = emptyList()) {
        val session = _state.value.openBoardSession ?: return
        val trimmed = content.trim()
        if (trimmed.isEmpty() && attachments.isEmpty()) {
            appendError(appContext.getString(R.string.family_msg_78865))
            return
        }
        scope.launch {
            postBoardMessageInternal(session, trimmed, attachments)
        }
    }

    /**
     * 将文件/目录作为**单个目录附件**转发到指定留言板。
     * 调用方应在非家庭网络 Tab 时通过 [beginEphemeralSession]/[endEphemeralSession] 维持临时服务。
     */
    fun forwardFilesAsDirectoryMessage(
        target: BulletinForwardTarget,
        items: List<DocumentFileModel>,
        textContent: String = "",
        onComplete: (Result<Unit>) -> Unit = {}
    ) {
        if (items.isEmpty()) {
            onComplete(Result.failure(IllegalStateException(appContext.getString(R.string.family_msg_98275))))
            return
        }
        if (target.service.isSelf && !localServiceEnabled) {
            onComplete(Result.failure(IllegalStateException(appContext.getString(R.string.family_msg_78904))))
            return
        }
        attachmentUploadJob?.cancel()
        attachmentUploadJob = scope.launch {
            var pendingAttachmentId: String? = null
            val attachmentName = when (items.size) {
                1 -> items.first().name
                else -> appContext.getString(R.string.family_msg_33626, items.size)
            }
            val totalBytes = items.sumOf { estimateUploadBytes(appContext, it) }.coerceAtLeast(1L)
            try {
                _state.update {
                    it.copy(
                        attachmentUpload = BulletinAttachmentUploadProgress(
                            itemName = attachmentName,
                            uploadedBytes = 0L,
                            totalBytes = totalBytes
                        )
                    )
                }
                appendLog(appContext.getString(R.string.family_msg_15756, items.size, target.service.displayHostName, target.boardName))
                val (_, device) = buildAuthorIdentity()
                val transport = buildAttachmentTransport(target)
                val uploadResult = withContext(Dispatchers.IO) {
                    BulletinAttachmentUploader.uploadItemsAsSingleDirectory(
                        context = appContext,
                        transport = transport,
                        boardId = target.boardId,
                        items = items,
                        attachmentName = attachmentName,
                        uploaderDevice = device,
                        onAttachmentInit = { attachmentId -> pendingAttachmentId = attachmentId },
                        onProgress = { uploaded, total ->
                            _state.update { state ->
                                state.copy(
                                    attachmentUpload = BulletinAttachmentUploadProgress(
                                        itemName = attachmentName,
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
                val postResult = uploadResult.fold(
                    onSuccess = { refs ->
                        val body = textContent.trim().ifBlank { "[${refs.firstOrNull()?.name ?: attachmentName}]" }
                        postMessageToTarget(target, body, refs)
                    },
                    onFailure = { Result.failure(it) }
                )
                postResult.onSuccess {
                    appendLog(appContext.getString(R.string.family_msg_26185, target.service.displayHostName, target.boardName))
                }.onFailure { error ->
                    appendError(appContext.getString(R.string.family_msg_37653, error.message ?: error.javaClass.simpleName))
                }
                onComplete(postResult)
            } catch (e: CancellationException) {
                pendingAttachmentId?.let { abortIncompleteUploadForTarget(target, it) }
                appendLog(appContext.getString(R.string.family_msg_69636))
                onComplete(Result.failure(e))
            } catch (e: Throwable) {
                pendingAttachmentId?.let { abortIncompleteUploadForTarget(target, it) }
                appendError(appContext.getString(R.string.family_msg_64576, e.message ?: e.javaClass.simpleName))
                onComplete(Result.failure(e))
            } finally {
                _state.update { it.copy(attachmentUpload = null) }
                attachmentUploadJob = null
            }
        }
    }

    /**
     * 将留言板消息转发到另一块留言板（逐条发送，附件在可能时本地复制或重新上传）。
     */
    fun forwardMessagesToBoard(
        sourceSession: BulletinBoardOpenSession,
        target: BulletinForwardTarget,
        messages: List<BulletinMessage>,
        onComplete: (Result<Int>) -> Unit = {}
    ) {
        if (messages.isEmpty()) {
            onComplete(Result.failure(IllegalStateException(appContext.getString(R.string.family_msg_76264))))
            return
        }
        if (target.service.isSelf && !localServiceEnabled) {
            onComplete(Result.failure(IllegalStateException(appContext.getString(R.string.family_msg_78904))))
            return
        }
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    var sent = 0
                    val (_, device) = buildAuthorIdentity()
                    val targetTransport = buildAttachmentTransport(target)
                    val sourceDownload = buildDownloadTransport(sourceSession)
                    for (message in messages) {
                        if (!message.isConversationMessage) continue
                        val attachmentRefs =                         BulletinMessageForwarder.relayAttachments(
                            context = appContext,
                            sourceSession = sourceSession,
                            sourceDownload = sourceDownload,
                            target = target,
                            targetTransport = targetTransport,
                            message = message,
                            uploaderDevice = device,
                            boardStore = boardStore
                        ).getOrThrow()
                        val prefix = appContext.getString(R.string.family_msg_22277, sourceSession.boardName)
                        val content = if (message.content.isBlank()) {
                            if (attachmentRefs.isEmpty()) continue
                            appContext.getString(R.string.family_msg_21188, attachmentRefs.size)
                        } else {
                            prefix + message.content
                        }
                        postMessageToTarget(target, content, attachmentRefs).getOrThrow()
                        sent++
                    }
                    if (sent == 0) {
                        throw IllegalStateException(appContext.getString(R.string.family_msg_57135))
                    }
                    sent
                }
            }
            result.onSuccess { count ->
                appendLog(
                    appContext.getString(
                        R.string.family_msg_97773,
                        count,
                        target.service.displayHostName,
                        target.boardName
                    )
                )
                if (_state.value.openBoardSession?.service?.deviceKey == target.service.deviceKey &&
                    _state.value.openBoardSession?.boardId == target.boardId
                ) {
                    refreshOpenBoard()
                }
            }.onFailure { error ->
                appendError(appContext.getString(R.string.family_msg_80952, error.message ?: error.javaClass.simpleName))
            }
            onComplete(result)
        }
    }

    suspend fun fetchAttachmentMeta(
        session: BulletinBoardOpenSession,
        attachmentId: String
    ): Result<org.json.JSONObject> = withContext(Dispatchers.IO) {
        buildDownloadTransport(session).getAttachmentMeta(session.boardId, attachmentId)
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
            appendError(appContext.getString(R.string.family_msg_90274))
            return
        }
        attachmentDownloadJob?.cancel()
        attachmentDownloadJob = scope.launch {
            val totalBytes = when (ref.kind) {
                BulletinAttachmentKind.FILE -> ref.size.coerceAtLeast(1L)
                BulletinAttachmentKind.DIRECTORY -> ref.totalSize.coerceAtLeast(ref.size).coerceAtLeast(1L)
            }
            val startedAtMs = System.currentTimeMillis()
            try {
                _state.update {
                    it.copy(
                        attachmentDownload = BulletinAttachmentDownloadProgress(
                            attachmentId = ref.id,
                            itemName = ref.name,
                            downloadedBytes = 0L,
                            totalBytes = totalBytes,
                            speedBytesPerSecond = 0L
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
                            val elapsedMs = (System.currentTimeMillis() - startedAtMs).coerceAtLeast(1L)
                            val speedBytesPerSecond = (downloaded * 1000L / elapsedMs).coerceAtLeast(0L)
                            _state.update { state ->
                                state.copy(
                                    attachmentDownload = BulletinAttachmentDownloadProgress(
                                        attachmentId = ref.id,
                                        itemName = ref.name,
                                        downloadedBytes = downloaded,
                                        totalBytes = total.coerceAtLeast(1L),
                                        speedBytesPerSecond = speedBytesPerSecond
                                    )
                                )
                            }
                        }
                    )
                }
                result.onSuccess { saved ->
                    appendLog(appContext.getString(R.string.family_msg_69047, saved.savedPath))
                }.onFailure { error ->
                    if (error is CancellationException) throw error
                    appendError(appContext.getString(R.string.family_msg_62914, error.message ?: error.javaClass.simpleName))
                }
            } catch (_: CancellationException) {
                appendLog(appContext.getString(R.string.family_msg_02521))
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
                service = session.service,
                openBlobConnection = { endpoint, fingerprint ->
                    openHttpsConnectionForBlob(endpoint, fingerprint, session.accessPassword)
                },
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
                            throw IllegalStateException(json.optString("message", appContext.getString(R.string.family_msg_02317)))
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
            appendError(appContext.getString(R.string.family_msg_74313))
            return
        }
        attachmentUploadJob?.cancel()
        attachmentUploadJob = scope.launch {
            var pendingAttachmentId: String? = null
            val primaryName = if (items.size == 1) items.first().name else appContext.getString(R.string.family_msg_14178, items.size)
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
                appendLog(appContext.getString(R.string.family_msg_48713, session.service.displayHostName))
                val (_, device) = buildAuthorIdentity()
                val transport: BulletinAttachmentTransport = if (session.isHost) {
                    LocalBulletinAttachmentTransport(boardStore)
                } else {
                    RemoteBulletinAttachmentTransport(
                        context = appContext,
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
                    val body = textContent.trim().ifBlank { appContext.getString(R.string.family_msg_30844, refs.size) }
                    postBoardMessageInternal(session, body, refs)
                }.onFailure { error ->
                    if (error is CancellationException) throw error
                    appendError(appContext.getString(R.string.family_msg_05106, error.message ?: error.javaClass.simpleName))
                }
            } catch (_: CancellationException) {
                pendingAttachmentId?.let { attachmentId ->
                    abortIncompleteUpload(session, attachmentId)
                }
                appendLog(appContext.getString(R.string.family_msg_16695))
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
                appendLog(appContext.getString(R.string.family_msg_20236, error.message ?: error.javaClass.simpleName))
            }
        }
    }

    private fun abortIncompleteUploadForTarget(target: BulletinForwardTarget, attachmentId: String) {
        scope.launch(Dispatchers.IO) {
            runCatching {
                if (target.service.isSelf) {
                    boardStore.attachments.deleteAttachment(target.boardId, attachmentId)
                } else {
                    boardApiRequest(
                        service = target.service,
                        method = "DELETE",
                        path = "/boards/${target.boardId}/attachments/$attachmentId",
                        accessPassword = target.accessPassword
                    )
                }
            }.onFailure { error ->
                appendLog(appContext.getString(R.string.family_msg_20236, error.message ?: error.javaClass.simpleName))
            }
        }
    }

    private fun buildAttachmentTransport(target: BulletinForwardTarget): BulletinAttachmentTransport {
        return if (target.service.isSelf) {
            LocalBulletinAttachmentTransport(boardStore)
        } else {
            RemoteBulletinAttachmentTransport(
                context = appContext,
                service = target.service,
                accessPassword = target.accessPassword,
                requestJson = { method, path, body ->
                    boardApiRequest(target.service, method, path, body, target.accessPassword)
                },
                requestBinary = { method, path, bytes ->
                    boardApiBinaryRequest(target.service, method, path, bytes, target.accessPassword)
                }
            )
        }
    }

    private suspend fun postMessageToTarget(
        target: BulletinForwardTarget,
        content: String,
        attachments: List<BulletinAttachmentRef>
    ): Result<Unit> {
        val (authorLabel, authorDevice) = buildAuthorIdentity()
        return withContext(Dispatchers.IO) {
            if (target.service.isSelf) {
                runCatching {
                    boardStore.appendMessage(
                        target.boardId,
                        authorLabel,
                        content,
                        authorDevice,
                        attachments
                    ) ?: throw IllegalStateException(
                        if (attachments.isNotEmpty()) appContext.getString(R.string.family_msg_55517) else appContext.getString(R.string.family_msg_84872)
                    )
                }.map { }
            } else {
                boardApiRequest(
                    service = target.service,
                    method = "POST",
                    path = "/boards/${target.boardId}/messages",
                    body = JSONObject().apply {
                        put("content", content)
                        put("author_label", authorLabel)
                        put("author_device", authorDevice)
                        if (attachments.isNotEmpty()) {
                            put("attachments", JSONArray(attachments.map { it.toJson() }))
                        }
                    }.toString(),
                    accessPassword = target.accessPassword
                ).map { }
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
                        if (attachments.isNotEmpty()) appContext.getString(R.string.family_msg_55517) else appContext.getString(R.string.family_msg_84872)
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
            appendLog(appContext.getString(R.string.family_msg_66183, session.service.displayHostName, session.boardId))
            refreshOpenBoard()
        }.onFailure { error ->
            appendError(appContext.getString(R.string.family_msg_34685, error.message ?: error.javaClass.simpleName))
        }
    }

    fun updateBoardMessage(messageId: String, content: String) {
        val session = _state.value.openBoardSession ?: return
        if (!session.canManageBoard) {
            appendError(appContext.getString(R.string.family_msg_68617))
            return
        }
        val trimmed = content.trim()
        if (trimmed.isEmpty()) {
            appendError(appContext.getString(R.string.family_msg_41301))
            return
        }
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                if (session.isHost) {
                    runCatching {
                        boardStore.updateMessage(session.boardId, messageId, trimmed)
                            ?: throw IllegalStateException(appContext.getString(R.string.family_msg_44812))
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
                appendLog(appContext.getString(R.string.family_msg_02548))
                refreshOpenBoard()
            }.onFailure { error ->
                appendError(appContext.getString(R.string.family_msg_20886, error.message ?: error.javaClass.simpleName))
            }
        }
    }

    fun deleteBoardMessage(messageId: String) {
        val session = _state.value.openBoardSession ?: return
        if (!session.canManageBoard) {
            appendError(appContext.getString(R.string.family_msg_05464))
            return
        }
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                if (session.isHost) {
                    runCatching {
                        if (!boardStore.deleteMessage(session.boardId, messageId)) {
                            throw IllegalStateException(appContext.getString(R.string.family_msg_74922))
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
                appendLog(appContext.getString(R.string.family_msg_72725))
                refreshOpenBoard()
            }.onFailure { error ->
                appendError(appContext.getString(R.string.family_msg_10024, error.message ?: error.javaClass.simpleName))
            }
        }
    }

    fun stop() {
        stopIfIdleJob?.cancel()
        if (!started && httpsServer == null && registrationListener == null && discoveryListener == null) return
        appendLog(appContext.getString(R.string.family_msg_95798))
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
        tlsFingerprint: String,
        localIp: String?
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
            localIp?.trim()?.takeIf { it.isNotEmpty() }?.let { setAttribute(FAMILY_IPV4_LIST_ATTR, it) }
            if (!networkPassword.isNullOrBlank()) {
                setAttribute("auth", "1")
            }
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {
                appendLog(appContext.getString(R.string.family_msg_38541, info.serviceName, info.serviceType, info.port))
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
                appendError(appContext.getString(R.string.family_msg_61384, serviceInfo.serviceName))
                _state.update { it.copy(isRegistered = false) }
            }

            override fun onServiceUnregistered(info: NsdServiceInfo) {
                appendLog(appContext.getString(R.string.family_msg_41707, info.serviceName))
                _state.update { it.copy(isRegistered = false) }
            }

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                appendError(appContext.getString(R.string.family_msg_11672, serviceInfo.serviceName))
            }
        }
        registrationListener = listener
        manager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    private fun startDiscovery(clearDiscovered: Boolean) {
        val manager = nsdManager
        if (manager == null) {
            appendError(appContext.getString(R.string.family_msg_25347))
            return
        }
        stopDiscoveryInternal()
        if (clearDiscovered) {
            _state.update { it.copy(discoveredServices = emptyList()) }
        }
        val listener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                if (errorCode == NsdManager.FAILURE_ALREADY_ACTIVE) {
                    appendLog(appContext.getString(R.string.family_msg_20665))
                    scope.launch {
                        delay(500)
                        if (started && (familyTabActive || ephemeralSessionCount > 0)) {
                            startDiscovery(clearDiscovered = false)
                        }
                    }
                    return
                }
                appendError(appContext.getString(R.string.family_msg_41010))
                _state.update { it.copy(isDiscovering = false) }
                try {
                    manager.stopServiceDiscovery(this)
                } catch (_: Throwable) {
                }
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                appendError(appContext.getString(R.string.family_msg_28896))
                _state.update { it.copy(isDiscovering = false) }
                try {
                    manager.stopServiceDiscovery(this)
                } catch (_: Throwable) {
                }
            }

            override fun onDiscoveryStarted(serviceType: String) {
                appendLog(appContext.getString(R.string.family_msg_94230, serviceType))
                _state.update { it.copy(isDiscovering = true, lastError = null) }
            }

            override fun onDiscoveryStopped(serviceType: String) {
                appendLog(appContext.getString(R.string.family_msg_01436, serviceType))
                _state.update { it.copy(isDiscovering = false) }
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (!serviceTypeMatches(serviceInfo.serviceType)) return
                val candidateName = serviceInfo.serviceName?.trim().orEmpty()
                if (candidateName.isEmpty()) return
                appendLog(appContext.getString(R.string.family_msg_81223, candidateName))
                enqueueResolve(serviceInfo)
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                appendLog(appContext.getString(R.string.family_msg_24100, serviceInfo.serviceName))
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
                appendError(appContext.getString(R.string.family_msg_57086, name, errorCode))
                drainResolveQueue()
            }

            override fun onServiceResolved(resolved: NsdServiceInfo) {
                val name = resolved.serviceName?.trim().orEmpty()
                synchronized(resolveQueueLock) {
                    pendingResolveNames.remove(name)
                    resolveInProgress = false
                }
                val attributes = decodeAttributes(resolved)
                val resolvedHost = resolved.host?.hostAddress ?: resolved.host?.hostName ?: ""
                val candidateHosts = attributes[FAMILY_IPV4_LIST_ATTR]
                    ?.split(',')
                    ?.map { it.trim() }
                    ?.filter { isIpv4Address(it) }
                    .orEmpty()
                val host = selectPreferredServiceHost(resolvedHost, attributes, _state.value.localIp)
                val instanceId = currentInstanceId
                val entry = FamilyDiscoveredService(
                    serviceName = resolved.serviceName ?: "",
                    serviceType = resolved.serviceType ?: FAMILY_SERVICE_TYPE,
                    host = host,
                    port = resolved.port,
                    attributes = attributes,
                    isSelf = instanceId != null && attributes["instance_id"] == instanceId
                )
                appendLog(
                    appContext.getString(R.string.family_msg_83175, entry.displayHostName, entry.host, entry.port) +
                        if (entry.isSelf) appContext.getString(R.string.family_self_suffix) else ""
                )
                appendLog(
                    "mDNS 解析详情: name=${entry.displayHostName} resolved=$resolvedHost candidates=${formatRecognizedEndpoints(candidateHosts, entry.port)} local=${_state.value.localIp.orEmpty()}"
                )
                if (host != resolvedHost) {
                    appendLog("mDNS 地址已改选: ${entry.displayHostName} resolved=$resolvedHost selected=$host local=${_state.value.localIp.orEmpty()}")
                }
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
        appendLog(appContext.getString(R.string.family_msg_74498, displayHostName, resolvedHost, port))
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
            tlsFingerprint = tlsFingerprint,
            localIp = _state.value.localIp
        )
        appendLog(appContext.getString(R.string.family_msg_17804, requestedServiceName))
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
            val auth = resolveSessionAuth(headers)
                ?: return FamilyHttpResponse(
                    401,
                    JSONObject().apply {
                        put("ok", false)
                        put("error", "unauthorized")
                        put("message", appContext.getString(R.string.family_msg_06885))
                    }.toString()
                )
            return boardHttpHandler.handle(method, path, bodyBytes, auth, headers)
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

    private fun resolveSessionAuth(headers: Map<String, String>): FamilyNetworkSessionAuth? {
        if (!FamilyNetworkRoles.hasAnyPassword(networkPassword, userRoles)) {
            return FamilyNetworkSessionAuth.OPEN
        }
        val provided = headers[FamilyNetworkAuth.PASSWORD_HEADER.lowercase(Locale.ROOT)]?.trim().orEmpty()
        if (provided.isEmpty()) return null
        return FamilyNetworkRoles.resolveAuth(
            providedPassword = provided,
            adminPassword = networkPassword,
            userRoles = userRoles,
            adminLabel = appContext.getString(R.string.family_role_admin),
        )
    }

    private fun loadLocalBoardSnapshot(boardId: String): Result<BulletinBoardSnapshot> {
        return runCatching {
            val snapshot = boardStore.snapshot(boardId)
                ?: throw IllegalStateException(appContext.getString(R.string.family_msg_71757))
            snapshot.copy(
                canManage = true,
                roleId = "admin",
                roleClass = "admin",
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
                throw IllegalStateException(json.optString("message", appContext.getString(R.string.family_msg_50818)))
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
                roleClass = json.optString("role_class").takeIf { it.isNotBlank() },
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
        var attemptedHost = service.host.trim().ifEmpty { service.host }
        val result = runCatching {
            val protocol = service.attributes["proto"]?.trim()?.lowercase()
            if (protocol != FAMILY_TLS_PROTOCOL) {
                throw IllegalStateException(appContext.getString(R.string.family_msg_66390, service.serviceName))
            }
            val fingerprint = service.attributes[FAMILY_TLS_FINGERPRINT_ATTR]
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: throw IllegalStateException(appContext.getString(R.string.family_msg_46688, service.serviceName))
            val localIp = _state.value.localIp?.trim().orEmpty()
            val recognizedHosts = collectRecognizedServiceHosts(service)
            val sameSubnetHosts = if (isIpv4Address(localIp)) {
                recognizedHosts.filter { isSameSubnet(localIp, it) }
            } else {
                emptyList()
            }
            appendLog(
                "留言板连接预检: service=${service.displayHostName} local=${localIp.ifEmpty { "unknown" }} recognized=${formatRecognizedEndpoints(recognizedHosts, service.port)} sameSubnet=${formatRecognizedEndpoints(sameSubnetHosts, service.port)}"
            )
            if (isIpv4Address(localIp) && isPrivateIpv4(localIp) && sameSubnetHosts.isEmpty()) {
                val recognized = formatRecognizedEndpoints(recognizedHosts, service.port)
                appendLog(
                    "留言板连接已跳过: service=${service.displayHostName} reason=no_same_subnet local=$localIp recognized=$recognized"
                )
                throw IllegalStateException(
                    appContext.getString(
                        R.string.family_board_probe_no_same_subnet,
                        localIp,
                        "skipped",
                        recognized
                    )
                )
            }
            val targetHost = sameSubnetHosts.firstOrNull() ?: service.host
            attemptedHost = targetHost
            appendLog(
                "留言板连接尝试: method=$method path=$path remote=${service.host}:${service.port} attempted=$attemptedHost:${service.port}"
            )
            val endpoint = URL("https://$targetHost:${service.port}$path")
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
        return result.fold(
            onSuccess = { Result.success(it) },
            onFailure = { error ->
                val enrichedError = enrichBoardApiFailure(service, attemptedHost, error)
                appendLog(
                    "留言板连接失败: method=$method path=$path remote=${service.host}:${service.port} attempted=$attemptedHost:${service.port} error=${summarizeThrowable(enrichedError)}"
                )
                Log.w(
                    TAG,
                    "boardApiRequest failed method=$method path=$path host=${service.host}:${service.port} service=${service.serviceName} error=${enrichedError.javaClass.simpleName}: ${enrichedError.message}",
                    enrichedError
                )
                Result.failure(enrichedError)
            }
        )
    }

    private fun enrichBoardApiFailure(service: FamilyDiscoveredService, attemptedHost: String, error: Throwable): Throwable {
        if (isLikelyAuthFailure(error)) return error
        val root = rootCause(error)
        val rootMessage = root.message.orEmpty()
        val lower = rootMessage.lowercase(Locale.ROOT)
        val isConnectivityIssue =
            root is SocketTimeoutException ||
                root is java.net.ConnectException ||
                root is java.net.UnknownHostException ||
                lower.contains("failed to connect") ||
                lower.contains("faled to connect") ||
                (lower.contains("to connect") && lower.contains("after")) ||
                lower.contains("timed out")
        if (!isConnectivityIssue) return error

        val localIp = _state.value.localIp?.trim().takeUnless { it.isNullOrEmpty() } ?: "unknown"
        val remoteIp = service.host.trim()
        val recognized = formatRecognizedEndpoints(collectRecognizedServiceHosts(service), service.port)
        val localIsPrivate = isIpv4Address(localIp) && isPrivateIpv4(localIp)
        val remoteIsPublic = isIpv4Address(remoteIp) && !isPrivateIpv4(remoteIp)
        val sameSubnet =
            if (isIpv4Address(localIp) && isIpv4Address(remoteIp)) isSameSubnet(localIp, remoteIp) else null
        val hint = when {
            remoteIsPublic && localIsPrivate -> appContext.getString(R.string.family_board_probe_hint_public_remote)
            sameSubnet == false -> appContext.getString(R.string.family_board_probe_hint_diff_subnet)
            else -> appContext.getString(R.string.family_board_probe_hint_service_unreachable, service.port)
        }
        val detail = rootMessage.ifBlank { root.javaClass.simpleName }
        val message = appContext.getString(
            R.string.family_board_probe_network_failure,
            detail,
            localIp,
            "${service.host}:${service.port}",
            "$attemptedHost:${service.port}",
            recognized,
            hint
        )
        return IllegalStateException(message, error)
    }

    private fun rootCause(error: Throwable): Throwable {
        var current = error
        while (current.cause != null && current.cause !== current) {
            current = current.cause!!
        }
        return current
    }

    private fun summarizeThrowable(error: Throwable): String {
        val root = rootCause(error)
        val detail = root.message?.trim().takeUnless { it.isNullOrEmpty() } ?: root.javaClass.simpleName
        return "${root.javaClass.simpleName}: $detail"
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
                throw IllegalStateException(appContext.getString(R.string.family_msg_66390, service.serviceName))
            }
            val fingerprint = service.attributes[FAMILY_TLS_FINGERPRINT_ATTR]
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: throw IllegalStateException(appContext.getString(R.string.family_msg_46688, service.serviceName))
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
                throw IllegalStateException(appContext.getString(R.string.family_msg_66390, service.serviceName))
            }
            val fingerprint = service.attributes[FAMILY_TLS_FINGERPRINT_ATTR]
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: throw IllegalStateException(appContext.getString(R.string.family_msg_46688, service.serviceName))
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

    internal fun openHttpsConnectionForBlob(endpoint: URL, fingerprint: String, accessPassword: String?): HttpURLConnection {
        return tlsManager.openHttpsConnection(endpoint, fingerprint).apply {
            requestMethod = "GET"
            connectTimeout = 15000
            readTimeout = 120000
            setRequestProperty("Accept", "application/octet-stream")
            accessPassword?.let {
                setRequestProperty(FamilyNetworkAuth.PASSWORD_HEADER, it)
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
                throw IllegalStateException(appContext.getString(R.string.family_msg_66390, service.serviceName))
            }
            val fingerprint = service.attributes[FAMILY_TLS_FINGERPRINT_ATTR]
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: throw IllegalStateException(appContext.getString(R.string.family_msg_46688, service.serviceName))
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
                throw IllegalStateException(json.optString("message", appContext.getString(R.string.family_msg_35200)))
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
                throw IllegalStateException(appContext.getString(R.string.family_msg_66390, service.serviceName))
            }
            val fingerprint = service.attributes[FAMILY_TLS_FINGERPRINT_ATTR]
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: throw IllegalStateException(appContext.getString(R.string.family_msg_46688, service.serviceName))
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
                throw IllegalStateException(appContext.getString(R.string.family_msg_66390, service.serviceName))
            }
            val fingerprint = service.attributes[FAMILY_TLS_FINGERPRINT_ATTR]
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: throw IllegalStateException(appContext.getString(R.string.family_msg_46688, service.serviceName))
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
            throw FamilyApiException(statusCode = status, detail = detail)
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
            appendLog(appContext.getString(R.string.family_msg_46190, e.message ?: e.javaClass.simpleName))
        } catch (e: Throwable) {
            appendError(appContext.getString(R.string.family_msg_42441, e.message ?: e.javaClass.simpleName))
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
            appendLog(appContext.getString(R.string.family_msg_70371, e.message ?: e.javaClass.simpleName))
        } catch (e: Throwable) {
            appendError(appContext.getString(R.string.family_msg_37052, e.message ?: e.javaClass.simpleName))
        } finally {
            registrationListener = null
            _state.update { it.copy(isRegistered = false) }
        }
    }

    private fun acquireMulticastLock() {
        if (multicastLock?.isHeld == true) return
        val manager = wifiManager ?: run {
            appendLog(appContext.getString(R.string.family_msg_64542))
            return
        }
        try {
            multicastLock = manager.createMulticastLock("LocalManagerFamilyNetwork").apply {
                setReferenceCounted(false)
                acquire()
            }
            appendLog(appContext.getString(R.string.family_msg_29648))
        } catch (e: Throwable) {
            appendError(appContext.getString(R.string.family_msg_60264, e.message ?: e.javaClass.simpleName))
        }
    }

    private fun releaseMulticastLock() {
        val lock = multicastLock ?: return
        try {
            if (lock.isHeld) {
                lock.release()
                appendLog(appContext.getString(R.string.family_msg_17052))
            }
        } catch (e: Throwable) {
            appendError(appContext.getString(R.string.family_msg_21921, e.message ?: e.javaClass.simpleName))
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

    private fun selectPreferredServiceHost(
        resolvedHost: String,
        attributes: Map<String, String>,
        localIp: String?
    ): String {
        val candidates = attributes[FAMILY_IPV4_LIST_ATTR]
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { isIpv4Address(it) }
            .orEmpty()
        if (candidates.isEmpty()) return resolvedHost

        val local = localIp?.trim().orEmpty()
        if (local.isNotEmpty()) {
            candidates.firstOrNull { isSameSubnet(local, it) }?.let { return it }
        }
        if (isIpv4Address(resolvedHost)) {
            candidates.firstOrNull { it == resolvedHost }?.let { return it }
        }
        return candidates.firstOrNull() ?: resolvedHost
    }

    private fun collectRecognizedServiceHosts(service: FamilyDiscoveredService): List<String> {
        val ordered = linkedSetOf<String>()
        val primary = service.host.trim()
        if (isIpv4Address(primary)) {
            ordered += primary
        }
        service.attributes[FAMILY_IPV4_LIST_ATTR]
            ?.split(',')
            ?.asSequence()
            ?.map { it.trim() }
            ?.filter { isIpv4Address(it) }
            ?.forEach { ordered += it }
        return ordered.toList()
    }

    private fun formatRecognizedEndpoints(hosts: List<String>, port: Int): String {
        if (hosts.isEmpty()) return appContext.getString(R.string.family_board_probe_recognized_none)
        return hosts.joinToString(", ") { "$it:$port" }
    }

    private fun isSameSubnet(localIp: String, remoteIp: String): Boolean {
        if (!isIpv4Address(localIp) || !isIpv4Address(remoteIp)) return false
        val localInt = ipv4ToInt(localIp) ?: return false
        val remoteInt = ipv4ToInt(remoteIp) ?: return false
        val rawMask = wifiManager?.dhcpInfo?.netmask ?: 0
        if (rawMask != 0) {
            val mask = normalizeDhcpNetmask(rawMask)
            if (mask != 0) {
                return (localInt and mask) == (remoteInt and mask)
            }
        }
        val localParts = localIp.split('.')
        val remoteParts = remoteIp.split('.')
        return localParts.take(3) == remoteParts.take(3)
    }

    private fun normalizeDhcpNetmask(rawMask: Int): Int {
        // DhcpInfo usually stores IPv4 integers in little-endian form on Android.
        val reversed = Integer.reverseBytes(rawMask)
        return when {
            reversed != 0 -> reversed
            else -> rawMask
        }
    }

    private fun isIpv4Address(value: String): Boolean {
        val parts = value.split('.')
        if (parts.size != 4) return false
        return parts.all { part -> part.toIntOrNull()?.let { it in 0..255 } == true }
    }

    private fun isPrivateIpv4(ip: String): Boolean {
        val parts = ip.split('.')
        if (parts.size != 4) return false
        val a = parts[0].toIntOrNull() ?: return false
        val b = parts[1].toIntOrNull() ?: return false
        if (a !in 0..255 || b !in 0..255) return false
        return when {
            a == 10 -> true
            a == 172 && b in 16..31 -> true
            a == 192 && b == 168 -> true
            a == 127 -> true
            a == 169 && b == 254 -> true
            else -> false
        }
    }

    private fun ipv4ToInt(ip: String): Int? {
        val parts = ip.split('.')
        if (parts.size != 4) return null
        var value = 0
        for (part in parts) {
            val octet = part.toIntOrNull() ?: return null
            if (octet !in 0..255) return null
            value = (value shl 8) or octet
        }
        return value
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
    private val context: Context,
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
            log(context.getString(R.string.family_msg_66267, preferredPort, firstError.message ?: firstError.javaClass.simpleName))
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
                    log(context.getString(R.string.family_msg_08788, e.message ?: e.javaClass.simpleName))
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
                append("Content-Length: ${response.bodyLength ?: response.bodyBytes.size.toLong()}\r\n")
                response.extraHeaders.forEach { (name, value) ->
                    append("$name: $value\r\n")
                }
                append("Connection: close\r\n")
                append("\r\n")
            }.toByteArray(StandardCharsets.ISO_8859_1)
            output.write(responseHead)
            response.bodyStream?.use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                }
            } ?: output.write(response.bodyBytes)
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
                throw IllegalStateException(context.getString(R.string.family_msg_52047))
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

    private class FamilyApiException(
        val statusCode: Int,
        val detail: String
    ) : IllegalStateException(detail)