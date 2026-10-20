package com.kenny.localmanager.zipedit

import com.kenny.localmanager.R
import com.kenny.localmanager.browser.BrowserActionContext
import com.kenny.localmanager.browser.BrowserActionSnapshot
import com.kenny.localmanager.browser.BrowserActions
import com.kenny.localmanager.browser.BrowserFileAction
import com.kenny.localmanager.file.DocumentFileModel

/**
 * ZIP 编辑域的浏览器动作声明（拆解设计 第三批）：设为编辑目标、
 * 添加到编辑目标（需快照证明已有 zip 目标）、编辑 ZIP 内容。
 * 前两者经会话句柄（宿主持有目标配置与向导状态）；后者直接打开
 * ZIP 管理页会话。
 */
internal object ZipEditIntents {

    private object SetTargetAction : BrowserFileAction {
        override val id = "zipedit.set.target"
        override fun labelRes(target: DocumentFileModel, snapshot: BrowserActionSnapshot) =
            R.string.zip_edit_menu_set_target
        override fun applies(target: DocumentFileModel, snapshot: BrowserActionSnapshot) =
            !target.isDirectory
        override fun run(ctx: BrowserActionContext) {
            ctx.session.setZipEditTarget(ctx.target)
        }
    }

    private object AddToTargetAction : BrowserFileAction {
        override val id = "zipedit.add.to.target"
        override fun labelRes(target: DocumentFileModel, snapshot: BrowserActionSnapshot) =
            R.string.zip_edit_menu_add_to_target
        override fun applies(target: DocumentFileModel, snapshot: BrowserActionSnapshot) =
            snapshot.zipEditTargetIsZip &&
                snapshot.zipEditTargetUri != null &&
                target.uri.toString() != snapshot.zipEditTargetUri
        override fun run(ctx: BrowserActionContext) {
            ctx.session.addToZipEditTarget(ctx.target)
        }
    }

    private object EditContentsAction : BrowserFileAction {
        override val id = "zipedit.edit.contents"
        override fun labelRes(target: DocumentFileModel, snapshot: BrowserActionSnapshot) =
            R.string.zip_edit_menu_edit_contents
        override fun applies(target: DocumentFileModel, snapshot: BrowserActionSnapshot) =
            !target.isDirectory && target.name.endsWith(".zip", ignoreCase = true)
        override fun run(ctx: BrowserActionContext) {
            ctx.session.openZipManager(ctx.target.uri.toString(), ctx.target.name, fromConfig = false)
        }
    }

    fun registerAll(registry: BrowserActions.Registry) {
        registry.register(SetTargetAction)
        registry.register(AddToTargetAction)
        registry.register(EditContentsAction)
    }
}
