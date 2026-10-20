package com.kenny.localmanager.archivereader

import com.kenny.localmanager.R
import com.kenny.localmanager.browser.BrowserActionContext
import com.kenny.localmanager.browser.BrowserActions
import com.kenny.localmanager.browser.BrowserOpenIntent
import com.kenny.localmanager.file.DocumentFileModel
import com.kenny.localmanager.file.isCompressedHtml
import com.kenny.localmanager.file.isCompressedMarkdown
import com.kenny.localmanager.file.isPicZip
import com.kenny.localmanager.file.isVidZip

/**
 * 归档查看器域的打开意图（文件浏览功能拆解设计 第一批）：压缩包
 * 阅读（.md.zip / .html.zip / .pic.zip / .vid.zip）的单击默认打开
 * 声明，槽位 100（压缩包阅读段）。在 [ArchiveReaderModule.attach]
 * 时注册；open 直呼本模块 service，不经浏览器参数表。
 */
internal object ArchiveReaderIntents {

    private object MdZipIntent : BrowserOpenIntent {
        override val id = "archivereader.mdzip"
        override val slot = 100
        override val labelRes = R.string.context_view_zip_markdown
        override fun applies(target: DocumentFileModel) = isCompressedMarkdown(target.name)
        override fun open(ctx: BrowserActionContext) {
            ArchiveReaderModule.service.open(ArchiveReaderKind.MD_ZIP, ctx.target)
        }
    }

    private object HtmlZipIntent : BrowserOpenIntent {
        override val id = "archivereader.htmlzip"
        override val slot = 100
        override val labelRes = R.string.context_view_zip_html
        override fun applies(target: DocumentFileModel) = isCompressedHtml(target.name)
        override fun open(ctx: BrowserActionContext) {
            ArchiveReaderModule.service.open(ArchiveReaderKind.HTML_ZIP, ctx.target)
        }
    }

    private object PicZipIntent : BrowserOpenIntent {
        override val id = "archivereader.piczip"
        override val slot = 100
        override val labelRes = R.string.context_view_compressed_images
        override fun applies(target: DocumentFileModel) = isPicZip(target.name)
        override fun open(ctx: BrowserActionContext) {
            ArchiveReaderModule.service.open(ArchiveReaderKind.PIC_ZIP, ctx.target)
        }
    }

    private object VidZipIntent : BrowserOpenIntent {
        override val id = "archivereader.vidzip"
        override val slot = 100
        override val labelRes = R.string.context_view_videos
        override fun applies(target: DocumentFileModel) = isVidZip(target.name)
        override fun open(ctx: BrowserActionContext) {
            ArchiveReaderModule.service.open(ArchiveReaderKind.VID_ZIP, ctx.target)
        }
    }

    fun registerAll(registry: BrowserActions.Registry) {
        registry.register(MdZipIntent)
        registry.register(HtmlZipIntent)
        registry.register(PicZipIntent)
        registry.register(VidZipIntent)
    }
}
