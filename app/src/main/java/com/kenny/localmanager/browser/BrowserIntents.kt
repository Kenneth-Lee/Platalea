package com.kenny.localmanager.browser

import com.kenny.localmanager.R
import com.kenny.localmanager.file.DocumentFileModel
import com.kenny.localmanager.file.fileExtensionKey
import com.kenny.localmanager.file.isGpgEncryptedFile

/**
 * 浏览器域自有的菜单动作声明（拆解设计 第三批/第四批）：回收站
 * 恢复、待处理切换、外开/内开/详情/删除对话框动作与槽 900 兜底
 * 意图。它们是浏览器自己的能力，走同一注册表与功能域动作同台
 * 展示（§2.1）。恢复依赖根目录（回收站恢复落点），无根目录时
 * 不注册（语义同现状可空回调）。
 */
internal object BrowserIntents {

    private object RestoreFromTrashAction : BrowserFileAction {
        override val id = "browser.restore.from.trash"
        override val emphasized = true
        override fun labelRes(target: DocumentFileModel, snapshot: BrowserActionSnapshot) =
            R.string.context_restore
        override fun applies(target: DocumentFileModel, snapshot: BrowserActionSnapshot) =
            snapshot.isViewingTrash
        override fun run(ctx: BrowserActionContext) {
            ctx.session.restoreFromTrash(ctx.target)
        }
    }

    private object PendingToggleAction : BrowserFileAction {
        override val id = "browser.pending.toggle"
        override fun labelRes(target: DocumentFileModel, snapshot: BrowserActionSnapshot) =
            if (target.uri.toString() in snapshot.pendingUris) R.string.context_remove_from_pending
            else R.string.context_add_to_pending
        override fun applies(target: DocumentFileModel, snapshot: BrowserActionSnapshot) = true
        override fun run(ctx: BrowserActionContext) {
            if (ctx.target.uri.toString() in ctx.snapshot.pendingUris) {
                ctx.session.removeFromPending(ctx.target)
            } else {
                ctx.session.addToPending(ctx.target)
            }
        }
    }

    /** 回收站恢复（注册序在遮罩前，需在组装段与待处理切换分开接线）。 */
    fun registerRestore(registry: BrowserActions.Registry) {
        registry.register(RestoreFromTrashAction)
    }

    /** 待处理切换（注册序在转发留言板后）。 */
    fun registerPendingToggle(registry: BrowserActions.Registry) {
        registry.register(PendingToggleAction)
    }

    private object OpenExternalAction : BrowserFileAction {
        override val id = "browser.open.external"
        override fun labelRes(target: DocumentFileModel, snapshot: BrowserActionSnapshot) =
            R.string.context_open_with_other_app
        override fun applies(target: DocumentFileModel, snapshot: BrowserActionSnapshot) =
            !target.isDirectory
        override fun run(ctx: BrowserActionContext) {
            ctx.session.openDialog(DialogSession.ExternalOpen(ctx.target))
        }
    }

    /** 兜底意图当前标题是否已是内置查看器（内开项不重复列出的判断）。 */
    private fun fallbackLabelIsInternal(target: DocumentFileModel, snapshot: BrowserActionSnapshot): Boolean {
        val preferred = fileExtensionKey(target.name)?.let { snapshot.externalOpenPreferences[it] }
        return preferred == null
    }

    private object OpenInternalAction : BrowserFileAction {
        override val id = "browser.open.internal"
        override fun labelRes(target: DocumentFileModel, snapshot: BrowserActionSnapshot) =
            R.string.context_open_with_internal_viewer
        override fun applies(target: DocumentFileModel, snapshot: BrowserActionSnapshot) =
            !target.isDirectory && !fallbackLabelIsInternal(target, snapshot)
        override fun run(ctx: BrowserActionContext) {
            ctx.session.clearExternalOpenPreference(ctx.target.name)
            ctx.session.openBuiltinViewer(ctx.target.uri.toString(), ctx.target.name, false)
        }
    }

    private object DetailAction : BrowserFileAction {
        override val id = "browser.detail"
        override fun labelRes(target: DocumentFileModel, snapshot: BrowserActionSnapshot) =
            R.string.context_details
        override fun applies(target: DocumentFileModel, snapshot: BrowserActionSnapshot) = true
        override fun run(ctx: BrowserActionContext) {
            ctx.session.openDialog(DialogSession.Detail(ctx.target))
        }
    }

    private object DeleteAction : BrowserFileAction {
        override val id = "browser.delete"
        override val destructive = true
        override fun labelRes(target: DocumentFileModel, snapshot: BrowserActionSnapshot) =
            R.string.common_delete
        override fun applies(target: DocumentFileModel, snapshot: BrowserActionSnapshot) = true
        override fun run(ctx: BrowserActionContext) {
            ctx.session.openDialog(DialogSession.Delete(ctx.target))
        }
    }

    /**
     * 槽 900 兜底意图（第四批动作化）：外开偏好或内置查看器，
     * 仅当无更低槽位意图适用时被选中（matchIntent 取槽位最小）。
     */
    private object FallbackOpenIntent : BrowserOpenIntent {
        override val id = "browser.open.fallback"
        override val slot = 900
        override val labelRes = R.string.context_open_with_internal_viewer
        override fun applies(target: DocumentFileModel) = !target.isDirectory
        override fun open(ctx: BrowserActionContext) {
            val preferred = fileExtensionKey(ctx.target.name)?.let { ctx.snapshot.externalOpenPreferences[it] }
            if (preferred != null && ctx.session.openWithOtherApp(ctx.target.uri.toString(), ctx.target.name, preferred, false, null)) {
                return
            }
            val encrypted = isGpgEncryptedFile(ctx.target.name)
            ctx.session.openBuiltinViewer(ctx.target.uri.toString(), ctx.target.name, encrypted)
        }
    }

    /** 兜底意图标题（单击分发取 matchIntent 结果，此函数给标题表）。 */
    fun fallbackPreferredLabelRes(target: DocumentFileModel, snapshot: BrowserActionSnapshot): Int? {
        val preferred = fileExtensionKey(target.name)?.let { snapshot.externalOpenPreferences[it] }
        return if (preferred != null) R.string.context_default_open_with_preferred else null
    }

    /** 外开/内开（注册序在功能域动作之前，同迁移前 if 链）。 */
    fun registerOpenEntries(registry: BrowserActions.Registry) {
        registry.register(OpenExternalAction)
        registry.register(OpenInternalAction)
    }

    /** 详情/删除（注册序在功能域动作之后、取消之前）。 */
    fun registerTailEntries(registry: BrowserActions.Registry) {
        registry.register(DetailAction)
        registry.register(DeleteAction)
        registry.register(FallbackOpenIntent)
    }
}
