package com.kenny.localmanager.quicknote

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kenny.localmanager.R
import com.kenny.localmanager.gpg.SecretKeyPasswordCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.kenny.localmanager.ui.RootService
import com.kenny.localmanager.ui.normalizeContentUriString
import com.kenny.localmanager.viewer.ViewerSessionsModule
import com.kenny.localmanager.ui.BaseTabRouteController
import com.kenny.localmanager.ui.TabPasswordPromptState

/**
 * 速记功能域服务（功能独立化改造引入）。
 *
 * 为什么引入：tab 切换的语义是"前一个功能退出、下一个功能启动"——速记页面的
 * 组合生存期就是功能的运行期；但速记的会话数据与异步收尾（切走 tab 时保存）
 * 必须在页面销毁后继续存活。所以数据态与收尾协程住本服务（组合根创建），
 * [QuickNoteTabRoute] 页面只是服务的投影 + 页面私有 UI 态。
 *
 * 什么属于：速记会话数据（已加载内容、条目快照、保存指纹）、密码打开态、
 * 加载/保存收尾、跨域出口（requestOpenWithCachedPassword / requestExitApp）。
 * 什么不属于：任何 Compose 组合片段（页面/对话框渲染归 Route 与 overlay）。
 */
internal class QuickNoteService(
    private val context: Context,
    private val scope: CoroutineScope
) : BaseTabRouteController<QuickNoteLoadedData> {
    /** 会话打开时的根（快照）：显示时与根服务核对，不一致即换根重载（换根立即生效）。 */
    private var sessionRoot: String? = null

    /** 会话代数：换根重载/重新打开时递增；飞行中的收尾与加载协程结果过期即丢弃。 */
    private var sessionGeneration = 0

    override var data by mutableStateOf<QuickNoteLoadedData?>(null)
        internal set
    var entriesSnapshot by mutableStateOf<List<QuickNoteEntry>>(emptyList())
        internal set
    var startWithAddDialog by mutableStateOf(false)
        internal set
    override var inProgress by mutableStateOf(false)
        internal set
    var password by mutableStateOf("")
        internal set
    var passwordRequired by mutableStateOf(false)
        internal set

    private var lastSavedHash: Int? = null
    private var loadRequested = false

    fun resetPromptState() {
        password = ""
        passwordRequired = false
        inProgress = false
    }

    /**
     * 功能启动（页面进入组合时调用）：会话未加载时带缓存密码自动打开；
     * 已加载则核对会话根与根服务——换根立即生效（收尾保存旧会话后按新根重载）。
     */
    fun ensureLoaded() {
        if (data != null) {
            val currentRoot = RootService.rootUri?.let { normalizeContentUriString(it) }
            if (sessionRoot != currentRoot) reloadForRootChange()
            return
        }
        if (inProgress || passwordRequired || loadRequested) return
        requestOpen(startWithAddDialog = false, password = SecretKeyPasswordCache.get()?.let { String(it) })
    }

    /** 换根重载：旧会话收尾保存（写旧根，修改不丢），清空后按新根重新打开。 */
    private fun reloadForRootChange() {
        persistIfNeeded(context.getString(R.string.quick_note_persist_reason_tab_switch), null)
        data = null
        entriesSnapshot = emptyList()
        requestOpen(startWithAddDialog = false, password = SecretKeyPasswordCache.get()?.let { String(it) })
    }

    /** 持久化（若有未保存变化）；收尾在服务协程中完成，不依赖页面存活。 */
    override fun persistIfNeeded(reason: String, onFinished: ((Boolean) -> Unit)?) {
        val currentData = data ?: run {
            onFinished?.invoke(true)
            return
        }
        if (inProgress) {
            onFinished?.invoke(false)
            return
        }
        val snapshot = entriesSnapshot
        val snapshotHash = snapshot.hashCode()
        if (lastSavedHash == snapshotHash) {
            onFinished?.invoke(true)
            return
        }
        inProgress = true
        val generation = sessionGeneration
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    saveQuickNoteData(context, currentData, snapshot)
                }
                result.onSuccess { saved ->
                    // 会话已换代（换根重载）：文件已写入旧根（修改不丢），状态归新会话
                    if (generation != sessionGeneration) return@onSuccess
                    ViewerSessionsModule.service.invalidateByUri(saved.fileInfo.uri.toString())
                    data = saved
                    entriesSnapshot = saved.entries
                    lastSavedHash = saved.entries.hashCode()
                    onFinished?.invoke(true)
                }.onFailure { throwable ->
                    toastSaveFailed(reason, throwable.message)
                    onFinished?.invoke(false)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                toastSaveFailed(reason, error.message)
                onFinished?.invoke(false)
            } finally {
                inProgress = false
            }
        }
    }

    /** 打开速记会话（密码缺省走缓存）。RequiresPassword 时置密码提示态。 */
    fun requestOpen(startWithAddDialog: Boolean, password: String? = null) {
        val root = RootService.rootUri?.let { normalizeContentUriString(it) }
        if (root == null) {
            Toast.makeText(context, context.getString(R.string.common_select_root_first), Toast.LENGTH_SHORT).show()
            return
        }
        loadRequested = true
        this.startWithAddDialog = startWithAddDialog
        inProgress = true
        val generation = ++sessionGeneration
        scope.launch {
            try {
                when (val result = withContext(Dispatchers.IO) {
                    openQuickNoteData(context, root, password)
                }) {
                    is QuickNoteOpenResult.Success -> {
                        if (generation != sessionGeneration) return@launch
                        sessionRoot = root
                        data = result.data
                        entriesSnapshot = result.data.entries
                        lastSavedHash = result.data.entries.hashCode()
                        passwordRequired = false
                        this@QuickNoteService.password = ""
                        loadRequested = false
                    }
                    QuickNoteOpenResult.RequiresPassword -> {
                        if (generation != sessionGeneration) return@launch
                        passwordRequired = true
                        loadRequested = false
                    }
                    is QuickNoteOpenResult.Error -> {
                        if (generation != sessionGeneration) return@launch
                        loadRequested = false
                        Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                loadRequested = false
                passwordRequired = false
                Toast.makeText(
                    context,
                    error.message ?: context.getString(R.string.common_unknown_error),
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                inProgress = false
            }
        }
    }

    /** 文件浏览"转到速记"出口：带缓存密码打开。 */
    fun requestOpenWithCachedPassword(startWithAddDialog: Boolean) {
        requestOpen(startWithAddDialog, SecretKeyPasswordCache.get()?.let { String(it) })
    }

    /** 页面编辑回调：会话条目快照更新（持久化时机由退出协议触发）。 */
    fun updateEntries(entries: List<QuickNoteEntry>) {
        entriesSnapshot = entries
    }

    /** 退出 App 时的速记持久化：进行中则提示稍候，否则写盘后回调确认退出。 */
    fun requestExitApp(onExitConfirmed: () -> Unit) {
        if (inProgress) {
            Toast.makeText(context, context.getString(R.string.browser_quick_note_busy), Toast.LENGTH_SHORT).show()
            return
        }
        persistIfNeeded(context.getString(R.string.quick_note_persist_reason_exit)) { saved ->
            if (saved) {
                onExitConfirmed()
            }
        }
    }

    /** 密码提示态（overlay 对话框消费）。 */
    override val passwordPromptState: TabPasswordPromptState
        get() = TabPasswordPromptState(
            required = passwordRequired,
            password = password,
            inProgress = inProgress,
            onPasswordChange = { if (!inProgress) password = it },
            onConfirm = { pwd ->
                if (!inProgress) {
                    requestOpen(startWithAddDialog, pwd)
                }
            },
            onDismiss = {
                if (!inProgress) {
                    resetPromptState()
                    startWithAddDialog = false
                    loadRequested = false
                }
            }
        )

    private fun toastSaveFailed(reason: String, message: String?) {
        Toast.makeText(
            context,
            context.getString(
                R.string.quick_note_auto_save_failed,
                reason,
                message ?: context.getString(R.string.common_unknown_error)
            ),
            Toast.LENGTH_LONG
        ).show()
    }
}

/**
 * QuickNoteService 模块（功能模块化）：组合根 attach 一行接线；跨域出口
 * （查看器投影、退出收尾、跨 tab 打开请求）直呼 service。
 */
internal object QuickNoteModule {
    private var instance: QuickNoteService? = null

    val service: QuickNoteService
        get() = instance ?: error("QuickNoteModule 未接线：组合根需先 attach")

    fun attach(context: Context, scope: CoroutineScope) {
        instance = QuickNoteService(context, scope)
    }

    fun resetForTest() {
        instance = null
    }
}
