package com.kenny.localmanager.archiveops

import com.kenny.localmanager.R
import com.kenny.localmanager.browser.BrowserActionContext
import com.kenny.localmanager.browser.BrowserActions
import com.kenny.localmanager.browser.BrowserOpenIntent
import com.kenny.localmanager.file.DocumentFileModel
import com.kenny.localmanager.file.isExtractableArchive

/**
 * 解压域的打开意图（文件浏览功能拆解设计 第二批）：可解压压缩包
 * （zip/rar/7z/tar…，.pic.zip/.vid.zip 除外——归压缩包阅读段）的单击
 * 默认解压，槽位 400（归档与加密段）。open 直呼本模块 service。
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

    fun registerAll(registry: BrowserActions.Registry) {
        registry.register(UnzipIntent)
    }
}
