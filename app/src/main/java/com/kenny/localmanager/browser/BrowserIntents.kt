package com.kenny.localmanager.browser

import com.kenny.localmanager.R
import com.kenny.localmanager.file.DocumentFileModel

/**
 * 浏览器域自有的菜单动作声明（拆解设计 第三批）：回收站恢复与
 * 待处理切换。它们是浏览器自己的能力，走同一注册表与功能域动作
 * 同台展示（§2.1）。恢复依赖根目录（回收站恢复落点），无根目录时
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
}
