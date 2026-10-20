package com.kenny.localmanager.ui

import com.kenny.localmanager.file.DocumentFileModel

/**
 * 阅读启动路由表（批次 B1）：文件模型 / URL → 阅读终态的唯一分流点。
 *
 * 宿主与各 tab（浏览器、最近记录等）只经此启动阅读功能，不直接感知
 * Archive 解包 / EPUB 渲染 workflow 与 transient 路由的实现细节。
 * 纪律：新增阅读格式 = 加一个方法 + 分发处加表项，消费方接口面保持稳定。
 */
internal class ReadingLauncher(
    private val setTransientRoute: (TransientRoute?) -> Unit
) {
    fun mdZip(model: DocumentFileModel) = ArchiveReaderModule.service.open(ArchiveReaderKind.MD_ZIP, model)
    fun htmlZip(model: DocumentFileModel) = ArchiveReaderModule.service.open(ArchiveReaderKind.HTML_ZIP, model)
    fun picZip(model: DocumentFileModel) = ArchiveReaderModule.service.open(ArchiveReaderKind.PIC_ZIP, model)
    fun vidZip(model: DocumentFileModel) = ArchiveReaderModule.service.open(ArchiveReaderKind.VID_ZIP, model)
    fun llmZip(model: DocumentFileModel) = EpubReaderModule.service.open(EpubReaderKind.LLM_ZIP, model)
    fun txt(model: DocumentFileModel) = EpubReaderModule.service.open(EpubReaderKind.TXT, model)
    fun llm(model: DocumentFileModel) = EpubReaderModule.service.open(EpubReaderKind.LLM, model)
    fun epub(model: DocumentFileModel) = EpubReaderModule.service.open(EpubReaderKind.EPUB, model)
    fun html(model: DocumentFileModel) =
        setTransientRoute(htmlViewerRouteFromFile(model.uri.toString(), model.name))
    fun htmlUrl(url: String) = setTransientRoute(htmlViewerRouteFromUrl(url))
    fun pdf(model: DocumentFileModel) =
        setTransientRoute(TransientRoute.PdfViewer(model.uri.toString(), model.name))
}
