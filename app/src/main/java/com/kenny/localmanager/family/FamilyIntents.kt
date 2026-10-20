package com.kenny.localmanager.family

import com.kenny.localmanager.R
import com.kenny.localmanager.browser.BrowserActionContext
import com.kenny.localmanager.browser.BrowserActionSnapshot
import com.kenny.localmanager.browser.BrowserActions
import com.kenny.localmanager.browser.BrowserFileAction
import com.kenny.localmanager.file.DocumentFileModel

/**
 * 家人域的浏览器动作声明（拆解设计 第三批）：转发到留言板。转发
 * 出口由组装层注入——留言板功能未激活时不注入，本域无动作注册
 * （菜单项随之消失，语义同现状可空回调）。
 */
internal object FamilyIntents {

    private class ForwardBulletinAction(
        private val forward: (List<DocumentFileModel>) -> Unit
    ) : BrowserFileAction {
        override val id = "family.forward.bulletin"
        override fun labelRes(target: DocumentFileModel, snapshot: BrowserActionSnapshot) =
            R.string.context_forward_to_bulletin
        override fun applies(target: DocumentFileModel, snapshot: BrowserActionSnapshot) = true
        override fun run(ctx: BrowserActionContext) {
            forward(listOf(ctx.target))
        }
    }

    fun registerAll(
        registry: BrowserActions.Registry,
        forwardToBulletin: ((List<DocumentFileModel>) -> Unit)?
    ) {
        if (forwardToBulletin != null) {
            registry.register(ForwardBulletinAction(forwardToBulletin))
        }
    }
}
