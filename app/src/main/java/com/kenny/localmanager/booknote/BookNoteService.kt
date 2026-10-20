package com.kenny.localmanager.booknote

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
import com.kenny.localmanager.quicknote.QuickNoteService
import com.kenny.localmanager.ui.RootService
import com.kenny.localmanager.ui.normalizeContentUriString
import com.kenny.localmanager.viewer.ViewerSessionsModule
import com.kenny.localmanager.ui.BaseTabRouteController
import com.kenny.localmanager.ui.TabPasswordPromptState

/**
 * 书摘功能域服务（功能独立化改造引入，与 QuickNoteService 同构）。
 *
 * 为什么引入：阅读器全屏（transient Route）需要书摘读写投影，tab 层页面销毁
 * 重建不影响书摘会话。数据态与自动保存协程住本服务，页面只是投影。
 *
 * 与速记的差异：书摘的条目更新即时自动保存（ensurePersistedOnUpdate），
 * 无"切走时保存"需求（原行为如此，保持）。
 */
internal class BookNoteService(
    private val context: Context,
    private val scope: CoroutineScope
) : BaseTabRouteController<BookNoteLoadedData> {
    /** 会话打开时的根（快照）：显示时与根服务核对，不一致即换根重载（换根立即生效）。 */
    private var sessionRoot: String? = null

    /** 会话代数：换根重载/重新打开时递增；飞行中的收尾与加载协程结果过期即丢弃。 */
    private var sessionGeneration = 0

    override var data by mutableStateOf<BookNoteLoadedData?>(null)
        internal set
    var entriesSnapshot by mutableStateOf<List<BookNoteEntry>>(emptyList())
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

    /** 功能启动（页面进入组合时调用）：未加载时自动打开；已加载则核对会话根
     *  与根服务——换根立即生效（收尾保存旧会话后按新根重载）。 */
    fun ensureLoaded() {
        if (data != null) {
            val currentRoot = RootService.rootUri?.let { normalizeContentUriString(it) }
            if (sessionRoot != currentRoot) reloadForRootChange()
            return
        }
        if (inProgress || passwordRequired || loadRequested) return
        requestOpen(SecretKeyPasswordCache.get()?.let { String(it) })
    }

    /** 换根重载：旧会话收尾保存（写旧根，修改不丢），清空后按新根重新打开。 */
    private fun reloadForRootChange() {
        persistIfNeeded(context.getString(R.string.book_note_persist_reason_update), null)
        data = null
        entriesSnapshot = emptyList()
        requestOpen(SecretKeyPasswordCache.get()?.let { String(it) })
    }

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
                    saveBookNoteData(context, currentData, snapshot)
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
                // 保存期间条目又变化：闭环再评估（原状态驱动 effect 的等价收拢）
                maybePersistOnUpdate()
            }
        }
    }

    fun requestOpen(password: String? = null) {
        val root = RootService.rootUri?.let { normalizeContentUriString(it) }
        if (root == null) {
            Toast.makeText(context, context.getString(R.string.common_select_root_first), Toast.LENGTH_SHORT).show()
            return
        }
        loadRequested = true
        inProgress = true
        val generation = ++sessionGeneration
        scope.launch {
            try {
                when (val result = withContext(Dispatchers.IO) {
                    openBookNoteData(context, root, password)
                }) {
                    is BookNoteOpenResult.Success -> {
                        if (generation != sessionGeneration) return@launch
                        sessionRoot = root
                        data = result.data
                        entriesSnapshot = result.data.entries
                        lastSavedHash = result.data.entries.hashCode()
                        passwordRequired = false
                        this@BookNoteService.password = ""
                        loadRequested = false
                    }
                    BookNoteOpenResult.RequiresPassword -> {
                        if (generation != sessionGeneration) return@launch
                        passwordRequired = true
                        loadRequested = false
                    }
                    is BookNoteOpenResult.Error -> {
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

    /** 阅读器"打开书摘"出口：带缓存密码打开。 */
    fun requestOpenWithCachedPassword() {
        requestOpen(SecretKeyPasswordCache.get()?.let { String(it) })
    }

    /** 页面/阅读器编辑回调：条目更新并即时评估自动保存。 */
    fun updateEntries(entries: List<BookNoteEntry>) {
        entriesSnapshot = entries
        maybePersistOnUpdate()
    }

    /** 原状态驱动自动保存（变化即保存）的等价收拢。 */
    private fun maybePersistOnUpdate() {
        if (data == null || inProgress) return
        if (lastSavedHash == entriesSnapshot.hashCode()) return
        persistIfNeeded(context.getString(R.string.book_note_persist_reason_update), null)
    }

    override val passwordPromptState: TabPasswordPromptState
        get() = TabPasswordPromptState(
            required = passwordRequired,
            password = password,
            inProgress = inProgress,
            onPasswordChange = { if (!inProgress) password = it },
            onConfirm = { pwd ->
                if (!inProgress) {
                    requestOpen(pwd)
                }
            },
            onDismiss = {
                if (!inProgress) {
                    resetPromptState()
                    loadRequested = false
                }
            }
        )

    private fun toastSaveFailed(reason: String, message: String?) {
        Toast.makeText(
            context,
            context.getString(
                R.string.book_note_auto_save_failed,
                reason,
                message ?: context.getString(R.string.common_unknown_error)
            ),
            Toast.LENGTH_LONG
        ).show()
    }
}

/**
 * BookNoteService 模块（功能模块化）：组合根 attach 一行接线；跨域出口
 * （查看器投影、退出收尾、跨 tab 打开请求）直呼 service。
 */
internal object BookNoteModule {
    private var instance: BookNoteService? = null

    val service: BookNoteService
        get() = instance ?: error("BookNoteModule 未接线：组合根需先 attach")

    fun attach(context: Context, scope: CoroutineScope) {
        instance = BookNoteService(context, scope)
    }

    fun resetForTest() {
        instance = null
    }
}
