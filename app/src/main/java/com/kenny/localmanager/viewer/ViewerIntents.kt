package com.kenny.localmanager.viewer

import com.kenny.localmanager.R
import com.kenny.localmanager.browser.BrowserActionContext
import com.kenny.localmanager.browser.BrowserActions
import com.kenny.localmanager.browser.BrowserOpenIntent
import com.kenny.localmanager.file.DocumentFileModel
import com.kenny.localmanager.file.isHtmlFile
import com.kenny.localmanager.file.isMarkdownFile
import com.kenny.localmanager.file.isPdfFile
import com.kenny.localmanager.ui.TransientRoute
import com.kenny.localmanager.ui.htmlViewerRouteFromFile

/**
 * 查看器域的打开意图（文件浏览功能拆解设计 第二批）：HTML / Markdown /
 * PDF 文档的单击默认打开声明，槽位 200（文档段）。执行经会话句柄的
 * 查看器 Route 命令（导航出口），查看器域不接触浏览器参数表。
 */
internal object ViewerIntents {

    private object HtmlIntent : BrowserOpenIntent {
        override val id = "viewer.html"
        override val slot = 200
        override val labelRes = R.string.context_default_open_html
        override fun applies(target: DocumentFileModel) = isHtmlFile(target.name)
        override fun open(ctx: BrowserActionContext) {
            ctx.session.openViewerRoute(
                htmlViewerRouteFromFile(ctx.target.uri.toString(), ctx.target.name)
            )
        }
    }

    private object MarkdownIntent : BrowserOpenIntent {
        override val id = "viewer.markdown"
        override val slot = 200
        override val labelRes = R.string.context_render_markdown
        override fun applies(target: DocumentFileModel) = isMarkdownFile(target.name)
        override fun open(ctx: BrowserActionContext) {
            ctx.session.openViewerRoute(
                TransientRoute.MarkdownViewer(ctx.target.uri.toString(), ctx.target.name, false)
            )
        }
    }

    private object PdfIntent : BrowserOpenIntent {
        override val id = "viewer.pdf"
        override val slot = 200
        override val labelRes = R.string.context_default_open_pdf
        override fun applies(target: DocumentFileModel) = isPdfFile(target.name)
        override fun open(ctx: BrowserActionContext) {
            ctx.session.openViewerRoute(
                TransientRoute.PdfViewer(ctx.target.uri.toString(), ctx.target.name)
            )
        }
    }

    fun registerAll(registry: BrowserActions.Registry) {
        registry.register(HtmlIntent)
        registry.register(MarkdownIntent)
        registry.register(PdfIntent)
    }
}
