package com.kenny.localmanager.ftp

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
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
     * @param passiveExternalIp 被动模式时向客户端宣告的 IP（如本机 LAN IP），若为空则使用默认；LIST/ls 常用被动模式，不设可能导致无响应。
     */
    fun start(port: Int, rootUri: String?, password: String?, passiveExternalIp: String?, onLog: (String) -> Unit): Boolean {
        if (rootUri.isNullOrBlank()) return false
        val root = DocumentFile.fromTreeUri(context, Uri.parse(rootUri)) ?: run {
            addLog("启动失败: 根目录不可用")
            return false
        }
        if (!root.exists() || !root.isDirectory) {
            addLog("启动失败: 根目录无效")
            return false
        }
        stop()
        val pwd = password?.trim() ?: ""
        return try {
            val factory = FtpServerFactory()
            factory.setUserManager(createUserManager(pwd))
            factory.setFileSystem(createFileSystemFactory(root))
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
        override fun beforeCommand(session: FtpSession?, request: FtpRequest?): FtpletResult {
            when (request?.command?.uppercase()) {
                "LIST", "NLST" -> addLog("list ${request.argument?.takeIf { it.isNotBlank() } ?: "."}")
            }
            return FtpletResult.DEFAULT
        }
        override fun afterCommand(session: FtpSession?, request: FtpRequest?, reply: FtpReply?): FtpletResult {
            val cmd = request?.command?.uppercase()
            val arg = request?.argument ?: ""
            val ok = (reply?.code ?: 0) in 200..399
            when (cmd) {
                "RETR" -> addLog(if (ok) "get $arg" else "get $arg 失败 (${reply?.code ?: ""})")
                "STOR", "APPE" -> addLog(if (ok) "put $arg" else "put $arg 失败 (${reply?.code ?: ""})")
                "LIST", "NLST" -> addLog(if (ok) "list 完成" else "list 失败 (${reply?.code ?: ""})")
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

    private fun createFileSystemFactory(rootDoc: DocumentFile): FileSystemFactory {
        return object : FileSystemFactory {
            @Throws(FtpException::class)
            override fun createFileSystemView(user: org.apache.ftpserver.ftplet.User): FileSystemView {
                return DocumentFileSystemView(context, rootDoc)
            }
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
