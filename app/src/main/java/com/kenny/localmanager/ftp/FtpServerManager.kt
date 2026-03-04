package com.kenny.localmanager.ftp

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.kenny.localmanager.file.listFilesSafe
import org.apache.ftpserver.FtpServer
import org.apache.ftpserver.FtpServerFactory
import org.apache.ftpserver.ftplet.FileSystemFactory
import org.apache.ftpserver.ftplet.FileSystemView
import org.apache.ftpserver.ftplet.FtpException
import org.apache.ftpserver.ftplet.FtpReply
import org.apache.ftpserver.ftplet.FtpRequest
import org.apache.ftpserver.ftplet.FtpSession
import org.apache.ftpserver.ftplet.Ftplet
import org.apache.ftpserver.ftplet.FtpletContext
import org.apache.ftpserver.ftplet.FtpletResult
import org.apache.ftpserver.ftplet.UserManager
import org.apache.ftpserver.usermanager.impl.BaseUser
import org.apache.ftpserver.usermanager.impl.ConcurrentLoginPermission
import org.apache.ftpserver.usermanager.impl.WritePermission
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * FTP 服务端管理，根目录为 DocumentFile（当前应用的 rootUri）。
 * 单用户 lm；密码在配置中设置，为空则无需密码。
 */
class FtpServerManager(private val context: Context) {

    private var server: FtpServer? = null
    private val connectionCount = AtomicInteger(0)
    private val logLines = CopyOnWriteArrayList<String>()

    /**
     * @param treeRootUri 应用已选文档树根 URI（必须为 tree URI），LIST 等依赖树下 DocumentFile。
     * @param currentDirUri 当前浏览目录 URI；若与 treeRootUri 相同或解析失败则 FTP 初始工作目录为树根。
     * @param passiveExternalIp 被动模式时向客户端宣告的 IP（如本机 LAN IP），若为空则使用默认；LIST/ls 常用被动模式，不设可能导致无响应。
     */
    fun start(port: Int, treeRootUri: String?, currentDirUri: String?, password: String?, passiveExternalIp: String?, onLog: (String) -> Unit): Boolean {
        if (treeRootUri.isNullOrBlank()) return false
        val treeUri = Uri.parse(treeRootUri)
        val ctx = context.applicationContext
        val root = DocumentFile.fromTreeUri(ctx, treeUri) ?: run {
            addLog("启动失败: 根目录不可用（请使用文档树根）")
            return false
        }
        if (!root.exists() || !root.isDirectory) {
            addLog("启动失败: 根目录无效")
            return false
        }
        val (initialWorkingDoc, initialWorkingPath) = resolveInitialWorkingDir(ctx, root, treeUri, currentDirUri)
        stop()
        val pwd = password?.trim() ?: ""
        return try {
            val factory = FtpServerFactory()
            factory.setUserManager(createUserManager(pwd))
            factory.setFileSystem(createFileSystemFactory(ctx, root, initialWorkingDoc, initialWorkingPath, this::addLog))
            factory.setFtplets(mapOf("log" to createLoggingFtplet()))
            val listenerFactory = org.apache.ftpserver.listener.ListenerFactory()
            listenerFactory.port = port
            listenerFactory.serverAddress = "0.0.0.0"
            val extIp = passiveExternalIp?.trim()?.takeIf { it.isNotEmpty() }
            if (extIp != null) {
                val dataConfigFactory = org.apache.ftpserver.DataConnectionConfigurationFactory()
                dataConfigFactory.passiveExternalAddress = extIp
                listenerFactory.dataConnectionConfiguration = dataConfigFactory.createDataConnectionConfiguration()
            }
            factory.addListener("default", listenerFactory.createListener())
            val ftpServer = factory.createServer()
            ftpServer.start()
            server = ftpServer
            addLog("FTP 服务器已启动 端口=$port 监听 0.0.0.0")
            true
        } catch (e: Throwable) {
            Log.e("FtpServer", "start failed", e)
            addLog("启动失败: ${e.message ?: e.javaClass.simpleName}")
            false
        }
    }

    /**
     * 在树下解析当前目录，得到可作为 LIST 工作目录的 DocumentFile（来自 tree，listFiles 可靠）。
     * 若 currentDirUri 为空、与树根相同，或 API<26 解析失败，则返回 (null, "/") 表示使用树根。
     */
    private fun resolveInitialWorkingDir(
        context: Context,
        rootDoc: DocumentFile,
        treeUri: Uri,
        currentDirUri: String?
    ): Pair<DocumentFile?, String> {
        val cur = currentDirUri?.trim()?.takeIf { it.isNotEmpty() } ?: return Pair(null, "/")
        if (cur == treeUri.toString()) return Pair(null, "/")
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return Pair(null, "/")
        return try {
            val pathResult = DocumentsContract.findDocumentPath(context.contentResolver, Uri.parse(cur))
                ?: return Pair(null, "/")
            val segmentIds = pathResult.path ?: return Pair(null, "/")
            if (segmentIds.isEmpty()) return Pair(null, "/")
            val rootId = DocumentsContract.getDocumentId(rootDoc.uri)
            if (pathResult.rootId != null && pathResult.rootId != rootId) return Pair(null, "/")
            var current: DocumentFile? = rootDoc
            val pathNames = mutableListOf<String>().apply { add(rootDoc.name ?: "") }
            val startIndex = if (segmentIds.isNotEmpty() && segmentIds[0] == rootId) 1 else 0
            for (i in startIndex until segmentIds.size) {
                val docId = segmentIds[i]
                current = current?.listFilesSafe()?.find { DocumentsContract.getDocumentId(it.uri) == docId }
                    ?: return Pair(null, "/")
                pathNames.add(current.name ?: "")
            }
            if (current == null || !current.isDirectory || pathNames.size <= 1) return Pair(null, "/")
            val workingPath = "/" + pathNames.drop(1).joinToString("/")
            Pair(current, workingPath)
        } catch (e: Exception) {
            addLog("初始工作目录使用根目录（解析异常: ${e.message ?: e.javaClass.simpleName}）")
            Pair(null, "/")
        }
    }

    private fun createUserManager(configuredPassword: String): UserManager {
        val user = BaseUser().apply {
            name = "lm"
            password = configuredPassword
            homeDirectory = "/"
            authorities = listOf(
                WritePermission(),
                ConcurrentLoginPermission(10, 10)
            )
        }
        return object : UserManager {
            override fun getAdminName(): String = "lm"
            override fun isAdmin(name: String?): Boolean = name == "lm"
            override fun getUserByName(name: String?) = if (name == "lm") user else null
            override fun getAllUserNames(): Array<String> = arrayOf("lm")
            override fun delete(name: String?) {}
            override fun save(user: org.apache.ftpserver.ftplet.User?) {}
            override fun doesExist(name: String?): Boolean = name == "lm"
            override fun authenticate(authentication: org.apache.ftpserver.ftplet.Authentication?): org.apache.ftpserver.ftplet.User? {
                val up = authentication as? org.apache.ftpserver.usermanager.UsernamePasswordAuthentication
                    ?: return null
                if (up.username != "lm") return null
                val clientPwd = up.password ?: ""
                val ok = if (configuredPassword.isEmpty()) clientPwd.isEmpty() else clientPwd == configuredPassword
                return if (ok) user else null
            }
        }
    }

    private fun createLoggingFtplet(): Ftplet = object : Ftplet {
        override fun init(ftpletContext: FtpletContext?) {}
        override fun destroy() {}
        override fun beforeCommand(session: FtpSession?, request: FtpRequest?): FtpletResult = FtpletResult.DEFAULT
        override fun afterCommand(session: FtpSession?, request: FtpRequest?, reply: FtpReply?): FtpletResult {
            val cmd = request?.command?.uppercase()
            val arg = request?.argument ?: ""
            val ok = (reply?.code ?: 0) in 200..399
            when (cmd) {
                "RETR" -> addLog(if (ok) "get $arg" else "get $arg 失败 (${reply?.code ?: ""})")
                "STOR", "APPE" -> addLog(if (ok) "put $arg" else "put $arg 失败 (${reply?.code ?: ""})")
                "LIST", "NLST" -> addLog(if (ok) "list 完成" else "list 失败 (${reply?.code ?: ""} ${reply?.message?.take(100) ?: ""})")
            }
            return FtpletResult.DEFAULT
        }
        override fun onConnect(session: FtpSession?): FtpletResult {
            val addr = session?.clientAddress?.toString() ?: "未知"
            addLog("收到连接: $addr")
            connectionCount.incrementAndGet()
            return FtpletResult.DEFAULT
        }
        override fun onDisconnect(session: FtpSession?): FtpletResult {
            connectionCount.decrementAndGet()
            addLog("连接已关闭")
            return FtpletResult.DEFAULT
        }
    }

    private fun createFileSystemFactory(
        ctx: Context,
        rootDoc: DocumentFile,
        initialWorkingDoc: DocumentFile?,
        initialWorkingPath: String,
        onLog: (String) -> Unit
    ): FileSystemFactory {
        return object : FileSystemFactory {
            @Throws(FtpException::class)
            override fun createFileSystemView(user: org.apache.ftpserver.ftplet.User): FileSystemView =
                DocumentFileSystemView(ctx, rootDoc, initialWorkingDoc, initialWorkingPath, onLog)
        }
    }

    fun stop() {
        try {
            server?.stop()
        } catch (_: Exception) {}
        server = null
        addLog("FTP 服务器已停止")
    }

    fun isRunning(): Boolean = server != null

    fun getConnectionCount(): Int = connectionCount.get()

    fun addLog(line: String) {
        val ts = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        logLines.add("[$ts] $line")
        while (logLines.size > 500) logLines.removeAt(0)
    }

    fun getLogLines(): List<String> = logLines.toList()

    fun clearLog() { logLines.clear() }
}
