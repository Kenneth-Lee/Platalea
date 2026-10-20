package com.kenny.localmanager.epub

import com.kenny.localmanager.R
import com.kenny.localmanager.browser.BrowserActionContext
import com.kenny.localmanager.browser.BrowserActions
import com.kenny.localmanager.browser.BrowserOpenIntent
import com.kenny.localmanager.file.DocumentFileModel
import com.kenny.localmanager.file.isCompressedLlmZip
import com.kenny.localmanager.file.isEpubFile
import com.kenny.localmanager.file.isLlmFile
import com.kenny.localmanager.file.isTxtFile

/**
 * EPUB/文本书域的打开意图（拆解设计 第一/二批）：压缩 LLM 包
 * （.llm.zip，槽 100）与 EPUB/TXT/LLM 文档（槽 200 文档段）。
 * open 直呼本模块 service。
 */
internal object EpubReaderIntents {

    private object LlmZipIntent : BrowserOpenIntent {
        override val id = "epub.llmzip"
        override val slot = 100
        override val labelRes = R.string.context_view_zip_llm
        override fun applies(target: DocumentFileModel) = isCompressedLlmZip(target.name)
        override fun open(ctx: BrowserActionContext) {
            EpubReaderModule.service.open(EpubReaderKind.LLM_ZIP, ctx.target)
        }
    }

    private object EpubIntent : BrowserOpenIntent {
        override val id = "epub.epub"
        override val slot = 200
        override val labelRes = R.string.context_default_open_epub
        override fun applies(target: DocumentFileModel) = isEpubFile(target.name)
        override fun open(ctx: BrowserActionContext) {
            EpubReaderModule.service.open(EpubReaderKind.EPUB, ctx.target)
        }
    }

    private object TxtIntent : BrowserOpenIntent {
        override val id = "epub.txt"
        override val slot = 200
        override val labelRes = R.string.context_default_open_txt
        override fun applies(target: DocumentFileModel) = isTxtFile(target.name)
        override fun open(ctx: BrowserActionContext) {
            EpubReaderModule.service.open(EpubReaderKind.TXT, ctx.target)
        }
    }

    private object LlmIntent : BrowserOpenIntent {
        override val id = "epub.llm"
        override val slot = 200
        override val labelRes = R.string.context_default_open_llm
        override fun applies(target: DocumentFileModel) = isLlmFile(target.name)
        override fun open(ctx: BrowserActionContext) {
            EpubReaderModule.service.open(EpubReaderKind.LLM, ctx.target)
        }
    }

    fun registerAll(registry: BrowserActions.Registry) {
        registry.register(LlmZipIntent)
        registry.register(EpubIntent)
        registry.register(TxtIntent)
        registry.register(LlmIntent)
    }
}
