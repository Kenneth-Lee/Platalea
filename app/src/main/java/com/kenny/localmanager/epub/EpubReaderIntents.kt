package com.kenny.localmanager.epub

import com.kenny.localmanager.R
import com.kenny.localmanager.browser.BrowserActions
import com.kenny.localmanager.browser.BrowserOpenIntent
import com.kenny.localmanager.browser.BrowserSessionHandle
import com.kenny.localmanager.file.DocumentFileModel
import com.kenny.localmanager.file.isCompressedLlmZip

/**
 * EPUB/文本书域的打开意图（文件浏览功能拆解设计 第一批）：压缩
 * LLM 包（.llm.zip）的单击默认打开声明，槽位 100（压缩包阅读段）。
 * 在 [EpubReaderModule.attach] 时注册；open 直呼本模块 service。
 */
internal object EpubReaderIntents {

    private object LlmZipIntent : BrowserOpenIntent {
        override val id = "epub.llmzip"
        override val slot = 100
        override val labelRes = R.string.context_view_zip_llm
        override fun applies(target: DocumentFileModel) = isCompressedLlmZip(target.name)
        override fun open(target: DocumentFileModel, session: BrowserSessionHandle) {
            EpubReaderModule.service.open(EpubReaderKind.LLM_ZIP, target)
        }
    }

    fun registerAll(registry: BrowserActions.Registry) {
        registry.register(LlmZipIntent)
    }
}
