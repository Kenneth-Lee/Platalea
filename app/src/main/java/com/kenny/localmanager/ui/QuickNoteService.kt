package com.kenny.localmanager.ui

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
    private val scope: CoroutineScope,
    private val markdownViewerSessionCache: ViewerSessions
) : BaseTabRouteController<QuickNoteLoadedData> {
    /** 当前根目录（换根时由组合根回填；会话数据继续指向打开时的文件，与原行为一致） */
    var rootUri: String? = null

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
     * 功能启动（页面进入组合时调用）：会话未加载时带缓存密码自动打开。
     * 原 isActive 触发链的条件收拢为方法内一次性判断。
     */
    fun ensureLoaded() {
        if (data != null || inProgress || passwordRequired || loadRequested) return
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
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    saveQuickNoteData(context, currentData, snapshot)
                }
                result.onSuccess { saved ->
                    markdownViewerSessionCache.invalidateByUri(saved.fileInfo.uri.toString())
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
        val root = rootUri?.let { normalizeContentUriString(it) }
        if (root == null) {
            Toast.makeText(context, context.getString(R.string.common_select_root_first), Toast.LENGTH_SHORT).show()
            return
        }
        loadRequested = true
        this.startWithAddDialog = startWithAddDialog
        inProgress = true
        scope.launch {
            try {
                when (val result = withContext(Dispatchers.IO) {
                    openQuickNoteData(context, root, password)
                }) {
                    is QuickNoteOpenResult.Success -> {
                        data = result.data
                        entriesSnapshot = result.data.entries
                        lastSavedHash = result.data.entries.hashCode()
                        passwordRequired = false
                        this@QuickNoteService.password = ""
                        loadRequested = false
                    }
                    QuickNoteOpenResult.RequiresPassword -> {
                        passwordRequired = true
                        loadRequested = false
                    }
                    is QuickNoteOpenResult.Error -> {
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
