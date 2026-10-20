package com.kenny.localmanager.obfuscate

import com.kenny.localmanager.R
import com.kenny.localmanager.browser.BrowserActionContext
import com.kenny.localmanager.browser.BrowserActionSnapshot
import com.kenny.localmanager.browser.BrowserActions
import com.kenny.localmanager.browser.BrowserFileAction
import com.kenny.localmanager.file.DocumentFileModel

/**
 * 快速遮罩域的浏览器动作声明（拆解设计 第三批）：长按非目录文件
 * 的"遮罩"菜单项，run 直呼本模块 service。
 */
internal object QuickObfuscateIntents {

    private object QuickMaskAction : BrowserFileAction {
        override val id = "obfuscate.quick.mask"
        override fun labelRes(target: DocumentFileModel, snapshot: BrowserActionSnapshot) =
            R.string.context_quick_mask
        override fun applies(target: DocumentFileModel, snapshot: BrowserActionSnapshot) =
            !target.isDirectory
        override fun run(ctx: BrowserActionContext) {
            QuickObfuscateModule.service.requestQuick(ctx.target)
        }
    }

    fun registerAll(registry: BrowserActions.Registry) {
        registry.register(QuickMaskAction)
    }
}
