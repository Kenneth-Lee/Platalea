package com.kenny.localmanager.crypto

import com.kenny.localmanager.R
import com.kenny.localmanager.browser.BrowserActionContext
import com.kenny.localmanager.browser.BrowserActionSnapshot
import com.kenny.localmanager.browser.BrowserActions
import com.kenny.localmanager.browser.BrowserFileAction
import com.kenny.localmanager.browser.BrowserOpenIntent
import com.kenny.localmanager.file.DocumentFileModel
import com.kenny.localmanager.file.isGpgEncryptedFile
import com.kenny.localmanager.file.isMarkdownFile
import com.kenny.localmanager.file.isPassFile
import com.kenny.localmanager.file.isTxtFile

/**
 * 加密域的浏览器能力声明（文件浏览功能拆解设计 第二批意图、第三批
 * 动作）：打开意图（GPG 解密、密码文件查看，槽 400）与菜单动作
 * （GPG 加密、密码保护、直接编辑）。open/run 直呼本模块 service；
 * 目录落点取快照 currentUri。
 */
internal object CryptoIntents {

    private object GpgDecryptIntent : BrowserOpenIntent {
        override val id = "crypto.gpg.decrypt"
        override val slot = 400
        override val labelRes = R.string.context_gpg_decrypt
        override fun applies(target: DocumentFileModel) = isGpgEncryptedFile(target.name)
        override fun open(ctx: BrowserActionContext) {
            val dirUri = ctx.snapshot.currentUri ?: return
            CryptoModule.service.open(CryptoRequest.Gpg(GpgOpState.Decrypt(ctx.target, dirUri)))
        }
    }

    private object PassViewIntent : BrowserOpenIntent {
        override val id = "crypto.pass.view"
        override val slot = 400
        override val labelRes = R.string.context_view_password
        override fun applies(target: DocumentFileModel) = isPassFile(target.name)
        override fun open(ctx: BrowserActionContext) {
            CryptoModule.service.open(CryptoRequest.PassView(ctx.target))
        }
    }

    private object GpgEncryptAction : BrowserFileAction {
        override val id = "crypto.gpg.encrypt"
        override fun labelRes(target: DocumentFileModel, snapshot: BrowserActionSnapshot) =
            R.string.context_gpg_encrypt
        override fun applies(target: DocumentFileModel, snapshot: BrowserActionSnapshot) =
            !target.isDirectory && !isPassFile(target.name)
        override fun run(ctx: BrowserActionContext) {
            val dirUri = ctx.snapshot.currentUri ?: return
            CryptoModule.service.open(CryptoRequest.Gpg(GpgOpState.Encrypt(ctx.target, dirUri)))
        }
    }

    private object PassProtectAction : BrowserFileAction {
        override val id = "crypto.pass.protect"
        override fun labelRes(target: DocumentFileModel, snapshot: BrowserActionSnapshot) =
            R.string.context_pass_protect
        override fun applies(target: DocumentFileModel, snapshot: BrowserActionSnapshot) =
            !target.isDirectory && (isMarkdownFile(target.name) || isTxtFile(target.name))
        override fun run(ctx: BrowserActionContext) {
            CryptoModule.service.open(CryptoRequest.PassProtect(ctx.target))
        }
    }

    private object PassEditAction : BrowserFileAction {
        override val id = "crypto.pass.edit"
        override fun labelRes(target: DocumentFileModel, snapshot: BrowserActionSnapshot) =
            R.string.context_edit_directly
        override fun applies(target: DocumentFileModel, snapshot: BrowserActionSnapshot) =
            !target.isDirectory && isPassFile(target.name)
        override fun run(ctx: BrowserActionContext) {
            val dirUri = ctx.snapshot.currentUri ?: return
            CryptoModule.service.open(CryptoRequest.PassEdit(ctx.target, dirUri))
        }
    }

    fun registerAll(registry: BrowserActions.Registry) {
        registry.register(GpgDecryptIntent)
        registry.register(PassViewIntent)
        registry.register(GpgEncryptAction)
        registry.register(PassProtectAction)
        registry.register(PassEditAction)
    }
}
