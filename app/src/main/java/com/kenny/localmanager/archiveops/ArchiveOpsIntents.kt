package com.kenny.localmanager.archiveops

import com.kenny.localmanager.R
import com.kenny.localmanager.browser.BrowserActionContext
import com.kenny.localmanager.browser.BrowserActionSnapshot
import com.kenny.localmanager.browser.BrowserActions
import com.kenny.localmanager.browser.BrowserFileAction
import com.kenny.localmanager.browser.BrowserOpenIntent
import com.kenny.localmanager.file.DocumentFileModel
import com.kenny.localmanager.file.isExtractableArchive

/**
 * 解压域的浏览器能力声明（拆解设计 第二批意图、第三批动作）：单击
 * 可解压压缩包（槽 400）与菜单压缩动作（文件与目录均可）。
 */
internal object ArchiveOpsIntents {

    private object UnzipIntent : BrowserOpenIntent {
        override val id = "archiveops.unzip"
        override val slot = 400
        override val labelRes = R.string.browser_extract_action
        override fun applies(target: DocumentFileModel) = isExtractableArchive(target.name)
        override fun open(ctx: BrowserActionContext) {
            ArchiveOpsModule.service.open(ArchiveOperationRequest.Unzip(ctx.target))
        }
    }

    private object CompressAction : BrowserFileAction {
        override val id = "archiveops.compress"
        override fun labelRes(target: DocumentFileModel, snapshot: BrowserActionSnapshot) =
            R.string.context_compress_archive
        override fun applies(target: DocumentFileModel, snapshot: BrowserActionSnapshot) = true
        override fun run(ctx: BrowserActionContext) {
            ArchiveOpsModule.service.open(ArchiveOperationRequest.CompressFile(ctx.target))
        }
    }

    fun registerAll(registry: BrowserActions.Registry) {
        registry.register(UnzipIntent)
        registry.register(CompressAction)
    }
}
