package com.kenny.localmanager.browser

import android.content.Context
import androidx.annotation.StringRes
import com.kenny.localmanager.file.DocumentFileModel
import com.kenny.localmanager.ui.MainTab
import com.kenny.localmanager.ui.TransientRoute
import kotlinx.coroutines.CoroutineScope

/**
 * 文件动作（文件浏览功能拆解设计 §2.1）：功能域向浏览器声明的一项
 * 文件操作能力——适用判断、菜单标题、执行器。浏览器收集声明、
 * 展示、转发调用，不解释任何文件类型。
 */
internal interface BrowserFileAction {
    /** 稳定标识：菜单排序与调试定位（同 id 重复注册被拒绝）。 */
    val id: String

    /** 菜单标题资源；依赖会话状态的项（如待处理切换）按快照取文案。 */
    @StringRes
    fun labelRes(target: DocumentFileModel, snapshot: BrowserActionSnapshot): Int

    /** 适用判断：对目标文件是否提供此项操作。 */
    fun applies(target: DocumentFileModel, snapshot: BrowserActionSnapshot): Boolean

    /** UI 强调（主色文案，如回收站恢复）；默认普通色。 */
    val emphasized: Boolean get() = false

    /** 危险操作（错误色文案，如删除）；默认普通色。 */
    val destructive: Boolean get() = false

    /** 执行：内部直呼本域服务；会话命令经会话句柄。 */
    fun run(ctx: BrowserActionContext)
}

/**
 * 打开意图（§2.1）：参与单击默认打开的声明子集。单击取全部适用
 * 意图中槽位最小者执行；槽位分段见设计文档（100 压缩包阅读 …
 * 900 浏览器兜底）。
 */
internal interface BrowserOpenIntent {
    val id: String
    val slot: Int

    /** 菜单首项"默认打开（X）"的标题资源。 */
    @get:StringRes
    val labelRes: Int

    fun applies(target: DocumentFileModel): Boolean

    /** 执行：入参上下文（目标文件+会话句柄+快照），与文件动作对称。 */
    fun open(ctx: BrowserActionContext)
}

/**
 * 会话句柄（§2.1）：浏览器对外部动作开放的受限命令接口，浏览器
 * 实现。功能域只发命令不读状态；判断依据用 [BrowserActionSnapshot]。
 */
internal interface BrowserSessionHandle {
    fun refresh()
    fun addToPending(item: DocumentFileModel)
    fun removeFromPending(item: DocumentFileModel)
    fun switchTab(tab: MainTab)
    /** 打开全屏查看 Route（导航出口：PDF/Markdown/HTML 查看器）。 */
    fun openViewerRoute(route: TransientRoute)
    /** 打开 ZIP 管理页（zipedit 域会话入口）。 */
    fun openZipManager(uri: String, name: String, fromConfig: Boolean)
    /** 把文件设为 ZIP 编辑目标（目标配置写入与提示在宿主实现）。 */
    fun setZipEditTarget(item: DocumentFileModel)
    /** 把文件加入 ZIP 编辑目标（向导确认前的拦截与发起在宿主实现）。 */
    fun addToZipEditTarget(item: DocumentFileModel)
    /** 从回收站恢复到根目录（进度、提示与刷新在宿主实现）。 */
    fun restoreFromTrash(item: DocumentFileModel)
    /** 把待处理列表复制/移动到当前目录（批量执行在宿主实现）。 */
    fun copyPendingToDir()
    fun movePendingToDir()
    /** 打开/关闭待处理列表浮层。 */
    fun showPendingList(show: Boolean)
    /** 清除 ZIP 编辑目标。 */
    fun clearZipEditTarget()
    /** 打开播放页（底部标签切换）。 */
    fun openPlaybackScreen()
    // —— 第四批：对话框会话与宿主编排收编 ——
    /** 挂起操作会话对话框（域动作经此打开浏览器对话框链）。 */
    fun openDialog(dialog: DialogSession)
    /** 关闭当前操作会话对话框。 */
    fun closeDialog()
    /** 兜底内置查看器（槽 900 动作与内开菜单项的执行体）。 */
    fun openBuiltinViewer(uri: String, name: String, encrypted: Boolean)
    /** 外部应用打开（返回是否成功拉起）。 */
    fun openWithOtherApp(uri: String, name: String, packageName: String?, rememberChoice: Boolean, forcedMimeType: String?): Boolean
    /** 清除某扩展名的外部打开偏好。 */
    fun clearExternalOpenPreference(name: String)
    /** 确认删除（移入回收站或永久删除，进度与刷新在宿主实现）。 */
    fun confirmDelete(target: DocumentFileModel, permanently: Boolean)
    /** 清空回收站。 */
    fun emptyTrash()
    /** 打开 URL（阅读启动器）。 */
    fun openUrl(url: String)
    /** 打开快记创建入口。 */
    fun createQuickNote()
    /** 对当前目录生成音乐列表。 */
    fun createMusicPlaylist(currentUri: String, name: String)
    /** 从配置入口打开 ZIP 管理页（顶栏待处理目标条）。 */
    fun openZipManagerFromConfig()
}

/** 菜单构建/执行时的只读快照：动作声明所需的会话判断依据。 */
internal class BrowserActionSnapshot(
    /** 当前目录（解密/解压等"写回当前目录"型动作的落点）。 */
    val currentUri: String? = null,
    val pendingUris: Set<String> = emptySet(),
    val zipEditTargetUri: String? = null,
    val zipEditTargetIsZip: Boolean = false,
    val isViewingTrash: Boolean = false,
    /** 当前目录的图片项（目录图库意图的兄弟列表）。 */
    val gallerySiblings: List<DocumentFileModel> = emptyList(),
    /** 外部打开偏好（扩展名键，槽 900 兜底与内开项的判断依据）。 */
    val externalOpenPreferences: Map<String, String> = emptyMap(),
)

/** 菜单项点击时传递的上下文：目标文件 + 会话句柄 + 快照 + 执行环境。 */
internal class BrowserActionContext(
    val target: DocumentFileModel,
    val session: BrowserSessionHandle,
    val snapshot: BrowserActionSnapshot,
    /** 页面组装 scope（IO/Toast 编排型执行体用）。 */
    val scope: CoroutineScope,
    /** 应用 Context（Toast/ContentResolver，均来自浏览器页面组装处）。 */
    val appContext: Context,
)

/**
 * 动作注册表（§2.2）：进程级单例（生产）+ 可构造实例（测试）。
 * 功能域在模块 attach 时向 [registry] 注册；浏览器域自有动作走
 * 同一注册表。测试用 [resetForTest] 换入干净实例。
 */
internal object BrowserActions {
    @Volatile
    private var current: Registry = Registry()

    val registry: Registry get() = current

    fun resetForTest(registry: Registry = Registry()) {
        current = registry
    }

    class Registry {
        private val actions = mutableListOf<BrowserFileAction>()
        private val intents = mutableListOf<BrowserOpenIntent>()
        private val actionIds = mutableSetOf<String>()
        private val intentIds = mutableSetOf<String>()

        /** 注册文件动作；重复 id 返回 false 且不加入。 */
        fun register(action: BrowserFileAction): Boolean {
            if (!actionIds.add(action.id)) return false
            actions += action
            return true
        }

        /** 注册打开意图；重复 id 返回 false 且不加入。 */
        fun register(intent: BrowserOpenIntent): Boolean {
            if (!intentIds.add(intent.id)) return false
            intents += intent
            return true
        }

        /** 单击默认打开：全部适用意图中槽位最小者；无注册或全不适用为 null。 */
        fun matchIntent(target: DocumentFileModel): BrowserOpenIntent? =
            intents.asSequence().filter { it.applies(target) }.minByOrNull { it.slot }

        /** 上下文菜单：按注册序取适用动作。 */
        fun menuActions(
            target: DocumentFileModel,
            snapshot: BrowserActionSnapshot
        ): List<BrowserFileAction> = actions.filter { it.applies(target, snapshot) }
    }
}
