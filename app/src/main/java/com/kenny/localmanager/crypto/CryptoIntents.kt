package com.kenny.localmanager.crypto

import com.kenny.localmanager.R
import com.kenny.localmanager.browser.BrowserActionContext
import com.kenny.localmanager.browser.BrowserActions
import com.kenny.localmanager.browser.BrowserOpenIntent
import com.kenny.localmanager.file.DocumentFileModel
import com.kenny.localmanager.file.isGpgEncryptedFile
import com.kenny.localmanager.file.isPassFile

/**
 * 加密域的打开意图（文件浏览功能拆解设计 第二批）：GPG 解密（.gpg，
 * 解密产物写回当前目录）与密码文件查看（.pass），槽位 400（归档与
 * 加密段）。open 直呼本模块 service；目录落点取快照 currentUri。
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

    fun registerAll(registry: BrowserActions.Registry) {
        registry.register(GpgDecryptIntent)
        registry.register(PassViewIntent)
    }
}
