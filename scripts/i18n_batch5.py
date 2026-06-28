#!/usr/bin/env python3
"""Batch 5: MarkdownViewerScreen EPUB/PDF/ZIP UI i18n"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ZH = ROOT / "app/src/main/res/values/strings.xml"
EN = ROOT / "app/src/main/res/values-en/strings.xml"
MD = ROOT / "app/src/main/java/com/kenny/localmanager/ui/MarkdownViewerScreen.kt"

STRINGS_ZH = {
    "common_edit": "编辑",
    "common_add": "添加",
    "common_jump": "跳转",
    "common_expand": "展开",
    "common_collapse": "收起",
    "common_loading_ellipsis": "加载中...",
    "common_updated": "已更新",
    "common_file": "文件",
    "common_whole_book": "整本书",
    "common_current_page": "当前页面",
    "common_empty_directory": "（空目录）",
    "md_viewer_zip_no_openable": "未找到可打开的文件",
    "md_viewer_zip_no_index_md": "压缩包中未找到 index%1$s、README%1$s 文件，也没有其他 %1$s 文件可生成索引。",
    "md_viewer_zip_contents": "压缩包内容：",
    "md_viewer_web_load_failed": "网页加载失败：%1$s（错误码：%2$d）",
    "md_viewer_no_index_html": "未找到 index.html",
    "md_viewer_no_index_html_detail": "压缩包中未找到 index.html、README.html 或其它 .html 文件。",
    "epub_toc_title_with_book": "目录 - %1$s",
    "epub_toc_filter_label": "筛选章节",
    "epub_toc_filter_placeholder": "输入章节号、标题或文件名",
    "epub_toc_current_summary": "当前第 %1$d 章，共 %2$d 章",
    "epub_toc_match_summary": "匹配到 %1$d / %2$d 章",
    "epub_toc_no_match": "没有匹配的章节",
    "epub_chapter_number": "第 %1$d 章",
    "epub_chapter_number_compact": "第%1$d章",
    "epub_chapter_current_marker": " · 当前位置",
    "epub_chapter_with_title_fallback": "第%1$d章",
    "epub_chapter_location_label": "第%1$d章 - %2$s · %3$s",
    "epub_bookmark_chapter_info": "第%1$d章 - %2$s · %3$s",
    "epub_bookmarks_title": "收藏夹 - %1$s",
    "epub_bookmarks_empty": "暂无收藏",
    "epub_bookmarks_empty_hint": "点击下方“新增当前位置收藏”可创建带定位的读书笔记",
    "epub_bookmarks_empty_hint_pdf": "点击下方“新增当前位置收藏”可创建定位笔记",
    "epub_bookmarks_filter_label": "过滤当前书收藏",
    "epub_bookmarks_filter_placeholder": "输入章节、引文或感想",
    "epub_bookmarks_total": "共 %1$d 条定位笔记",
    "epub_bookmarks_match": "匹配到 %1$d / %2$d 条",
    "epub_bookmarks_no_match": "没有匹配的收藏",
    "epub_unnamed_chapter": "未命名章节",
    "epub_unbound_position": "未绑定具体位置",
    "epub_unbound_position_short": "未绑定位置",
    "epub_quote_prefix": "引文：%1$s",
    "epub_add_current_bookmark": "新增当前位置收藏",
    "epub_current_location": "当前位置：%1$s",
    "epub_quote_field_label": "引文（优先使用当前选中内容，否则抓取当前段落，长文本会截断）",
    "epub_note_field_optional": "感想（可选）",
    "epub_bookmark_exists_edit": "该位置已收藏，已转为编辑",
    "epub_bookmark_added": "已添加收藏",
    "epub_jump_position_title": "跳转位置（近似）",
    "epub_jump_position_hint": "仅在当前章节跳转，使用章节内进度%近似定位。",
    "epub_jump_current_chapter": "当前章节：第 %1$d 章",
    "epub_jump_target_progress": "目标进度：%1$d%%",
    "epub_no_jump_target": "这条读书笔记没有可跳转的位置",
    "epub_prev_chapter": "上一章",
    "epub_next_chapter": "下一章",
    "epub_chapter_load_failed": "无法加载章节内容",
    "epub_dict_lookup_result": "取词结果",
    "epub_dict_fallback_word": "回退到上一个单词",
    "epub_dict_select_copy_hint": "选择文字并复制到剪贴板查询",
    "epub_toc_menu_desc": "目录",
    "epub_next_page_desc": "下一页",
    "epub_section_start": "开头",
    "epub_section_early": "前段",
    "epub_section_early_mid": "前中段",
    "epub_section_mid": "中段",
    "epub_section_late_mid": "后中段",
    "epub_section_late": "后段",
    "epub_section_end": "末尾",
    "epub_position_in_chapter": "章节内%1$s (%2$d%%)",
    "epub_chapter_progress_compact": "本章%1$s %2$d%%",
    "epub_reading_progress_note": "上次阅读到这里",
    "epub_reading_progress_label": "上次",
    "epub_bookmark_default_note": "书签",
    "epub_bookmark_default_label": "签",
    "book_note_delete_confirm_entry": "确定删除这条读书笔记吗？",
    "pdf_empty": "PDF 文件为空",
    "pdf_empty_short": "PDF 为空",
    "pdf_go_to_page_title": "跳转到指定页",
    "pdf_current_page_summary": "当前第 %1$d 页，共 %2$d 页",
    "pdf_target_page_label": "目标页码（1 - %1$d）",
    "pdf_invalid_number": "请输入有效的数字",
    "pdf_page_range_error": "页码范围：1 - %1$d",
    "pdf_page_title": "第%1$d页",
    "pdf_page_location": "第%1$d页 / 共%2$d页",
    "pdf_page_desc": "第 %1$d 页",
    "pdf_rendering": "正在渲染页面...",
    "pdf_text_selection_hint": "提示：PDF 页面渲染为图像，暂不支持文字选择。\n如需选择文字，请使用专业 PDF 阅读器。",
    "pdf_prev_page_desc": "上一页",
}

STRINGS_EN = {
    "common_edit": "Edit",
    "common_add": "Add",
    "common_jump": "Jump",
    "common_expand": "Expand",
    "common_collapse": "Collapse",
    "common_loading_ellipsis": "Loading...",
    "common_updated": "Updated",
    "common_file": "File",
    "common_whole_book": "Whole book",
    "common_current_page": "Current page",
    "common_empty_directory": "(empty directory)",
    "md_viewer_zip_no_openable": "No openable file found",
    "md_viewer_zip_no_index_md": "No index%1$s or README%1$s found in the archive, and no other %1$s files to build an index.",
    "md_viewer_zip_contents": "Archive contents:",
    "md_viewer_web_load_failed": "Page load failed: %1$s (error code: %2$d)",
    "md_viewer_no_index_html": "index.html not found",
    "md_viewer_no_index_html_detail": "No index.html, README.html, or other .html file found in the archive.",
    "epub_toc_title_with_book": "Contents - %1$s",
    "epub_toc_filter_label": "Filter chapters",
    "epub_toc_filter_placeholder": "Enter chapter number, title, or file name",
    "epub_toc_current_summary": "Chapter %1$d of %2$d",
    "epub_toc_match_summary": "Matched %1$d / %2$d chapters",
    "epub_toc_no_match": "No matching chapters",
    "epub_chapter_number": "Chapter %1$d",
    "epub_chapter_number_compact": "Ch.%1$d",
    "epub_chapter_current_marker": " · current position",
    "epub_chapter_with_title_fallback": "Ch.%1$d",
    "epub_chapter_location_label": "Ch.%1$d - %2$s · %3$s",
    "epub_bookmark_chapter_info": "Ch.%1$d - %2$s · %3$s",
    "epub_bookmarks_title": "Bookmarks - %1$s",
    "epub_bookmarks_empty": "No bookmarks yet",
    "epub_bookmarks_empty_hint": "Tap \"Add bookmark at current position\" below to create a positioned reading note.",
    "epub_bookmarks_empty_hint_pdf": "Tap \"Add bookmark at current position\" below to create a positioned note.",
    "epub_bookmarks_filter_label": "Filter bookmarks in this book",
    "epub_bookmarks_filter_placeholder": "Enter chapter, quote, or note",
    "epub_bookmarks_total": "%1$d positioned notes in total",
    "epub_bookmarks_match": "Matched %1$d / %2$d",
    "epub_bookmarks_no_match": "No matching bookmarks",
    "epub_unnamed_chapter": "Untitled chapter",
    "epub_unbound_position": "No specific position bound",
    "epub_unbound_position_short": "No position bound",
    "epub_quote_prefix": "Quote: %1$s",
    "epub_add_current_bookmark": "Add bookmark at current position",
    "epub_current_location": "Current position: %1$s",
    "epub_quote_field_label": "Quote (uses current selection first, otherwise the current paragraph; long text is truncated)",
    "epub_note_field_optional": "Note (optional)",
    "epub_bookmark_exists_edit": "This position is already bookmarked; switched to edit",
    "epub_bookmark_added": "Bookmark added",
    "epub_jump_position_title": "Jump to position (approx.)",
    "epub_jump_position_hint": "Jump within the current chapter only, using in-chapter progress % as an approximate position.",
    "epub_jump_current_chapter": "Current chapter: %1$d",
    "epub_jump_target_progress": "Target progress: %1$d%%",
    "epub_no_jump_target": "This reading note has no jump target",
    "epub_prev_chapter": "Previous chapter",
    "epub_next_chapter": "Next chapter",
    "epub_chapter_load_failed": "Unable to load chapter content",
    "epub_dict_lookup_result": "Lookup result",
    "epub_dict_fallback_word": "Back to previous word",
    "epub_dict_select_copy_hint": "Select text and copy to clipboard to look up",
    "epub_toc_menu_desc": "Contents",
    "epub_next_page_desc": "Next page",
    "epub_section_start": "start",
    "epub_section_early": "early",
    "epub_section_early_mid": "early-mid",
    "epub_section_mid": "middle",
    "epub_section_late_mid": "late-mid",
    "epub_section_late": "late",
    "epub_section_end": "end",
    "epub_position_in_chapter": "In chapter: %1$s (%2$d%%)",
    "epub_chapter_progress_compact": "This chapter %1$s %2$d%%",
    "epub_reading_progress_note": "Last read here",
    "epub_reading_progress_label": "Last",
    "epub_bookmark_default_note": "Bookmark",
    "epub_bookmark_default_label": "Mark",
    "book_note_delete_confirm_entry": "Delete this reading note?",
    "pdf_empty": "PDF file is empty",
    "pdf_empty_short": "PDF is empty",
    "pdf_go_to_page_title": "Go to page",
    "pdf_current_page_summary": "Page %1$d of %2$d",
    "pdf_target_page_label": "Target page (1 - %1$d)",
    "pdf_invalid_number": "Enter a valid number",
    "pdf_page_range_error": "Page range: 1 - %1$d",
    "pdf_page_title": "Page %1$d",
    "pdf_page_location": "Page %1$d / %2$d",
    "pdf_page_desc": "Page %1$d",
    "pdf_rendering": "Rendering page...",
    "pdf_text_selection_hint": "Note: PDF pages are rendered as images; text selection is not supported yet.\nUse a dedicated PDF reader if you need to select text.",
    "pdf_prev_page_desc": "Previous page",
}


def add_strings(path: Path, strings: dict):
    content = path.read_text(encoding="utf-8")
    adds = []
    for k, v in strings.items():
        if f'name="{k}"' in content:
            continue
        val = v.replace("&", "&amp;").replace("<", "&lt;").replace("'", "\\'")
        adds.append(f'    <string name="{k}">{val}</string>')
    if adds:
        content = content.replace("</resources>", "\n".join(adds) + "\n</resources>")
        path.write_text(content, encoding="utf-8")
        print(f"Added {len(adds)} entries to {path.name}")


def apply_replacements(path: Path, reps: list[tuple[str, str]], replace_all: bool = False) -> int:
    text = path.read_text(encoding="utf-8")
    count = 0
    for old, new in reps:
        if old not in text:
            print(f"  MISSING: {old[:70]}...")
            continue
        if replace_all:
            n = text.count(old)
            text = text.replace(old, new)
            count += n
        else:
            text = text.replace(old, new, 1)
            count += 1
    path.write_text(text, encoding="utf-8")
    return count


FORMAT_EPUB_BOOKMARK = '''private fun formatEpubBookmarkPosition(context: Context, scrollRatio: Float): String {
    val percent = (scrollRatio.coerceIn(0f, 1f) * 100f).toInt().coerceIn(0, 100)
    val section = when {
        percent <= 5 -> context.getString(R.string.epub_section_start)
        percent <= 25 -> context.getString(R.string.epub_section_early)
        percent <= 45 -> context.getString(R.string.epub_section_early_mid)
        percent <= 65 -> context.getString(R.string.epub_section_mid)
        percent <= 85 -> context.getString(R.string.epub_section_late_mid)
        percent <= 97 -> context.getString(R.string.epub_section_late)
        else -> context.getString(R.string.epub_section_end)
    }
    return context.getString(R.string.epub_position_in_chapter, section, percent)
}'''

FORMAT_EPUB_PROGRESS = '''private fun formatEpubChapterProgressCompact(context: Context, scrollRatio: Float): String {
    val percent = (scrollRatio.coerceIn(0f, 1f) * 100f).toInt().coerceIn(0, 100)
    val section = when {
        percent <= 5 -> context.getString(R.string.epub_section_start)
        percent <= 25 -> context.getString(R.string.epub_section_early)
        percent <= 45 -> context.getString(R.string.epub_section_early_mid)
        percent <= 65 -> context.getString(R.string.epub_section_mid)
        percent <= 85 -> context.getString(R.string.epub_section_late_mid)
        percent <= 97 -> context.getString(R.string.epub_section_late)
        else -> context.getString(R.string.epub_section_end)
    }
    return context.getString(R.string.epub_chapter_progress_compact, section, percent)
}'''

MD_REPS = [
    ('val name = doc?.name ?: targetUri.lastPathSegment ?: "文件"', 'val name = doc?.name ?: targetUri.lastPathSegment ?: context.getString(R.string.common_file)'),
    ('private fun MdZipNoTargetScreen(\n    contentDir: File,\n    zipFileName: String,\n    onBack: () -> Unit,\n    logDebug: ((String) -> Unit)? = null\n) {\n    val isRstZip',
     'private fun MdZipNoTargetScreen(\n    contentDir: File,\n    zipFileName: String,\n    onBack: () -> Unit,\n    logDebug: ((String) -> Unit)? = null\n) {\n    val context = LocalContext.current\n    val isRstZip'),
    ('text = "未找到可打开的文件",', 'text = stringResource(R.string.md_viewer_zip_no_openable),'),
    ('text = "压缩包中未找到 index${ext}、README${ext} 文件，也没有其他 ${ext} 文件可生成索引。",',
     'text = stringResource(R.string.md_viewer_zip_no_index_md, ext, ext, ext),'),
    ('text = "压缩包内容：",', 'text = stringResource(R.string.md_viewer_zip_contents),'),
    ('text = "（空目录）",', 'text = stringResource(R.string.common_empty_directory),'),
    ('private class RemoteHtmlWebViewClient(\n    private val onExternalUrl: (String) -> Unit,\n    private val onError: (String) -> Unit,',
     'private class RemoteHtmlWebViewClient(\n    private val context: Context,\n    private val onExternalUrl: (String) -> Unit,\n    private val onError: (String) -> Unit,'),
    ('onError("网页加载失败：${description ?: "未知错误"}（错误码：$errorCode）")',
     'onError(context.getString(R.string.md_viewer_web_load_failed, description ?: context.getString(R.string.common_unknown_error), errorCode))'),
    ('webViewClient = RemoteHtmlWebViewClient(\n                                        onExternalUrl = { url -> pendingExternalUrl = url },',
     'webViewClient = RemoteHtmlWebViewClient(\n                                        context,\n                                        onExternalUrl = { url -> pendingExternalUrl = url },'),
    ('if (loading) "正在准备 HTML" else "正在准备页面"', 'if (loading) context.getString(R.string.md_viewer_preparing_html) else context.getString(R.string.md_viewer_preparing_page)'),
    ('text = "未找到 index.html",', 'text = stringResource(R.string.md_viewer_no_index_html),'),
    ('text = "压缩包中未找到 index.html、README.html 或其它 .html 文件。",', 'text = stringResource(R.string.md_viewer_no_index_html_detail),'),
    ('return DictLookupResult(word, null, "没有可用的词典，请先导入词典")', 'return DictLookupResult(word, null, context.getString(R.string.dict_no_dictionary))'),
    ('return DictLookupResult(word, null, "词典中未找到 \\"$word\\"")', 'return DictLookupResult(word, null, context.getString(R.string.dict_word_not_found, word))'),
    ('return "第${currentChapterIndex + 1}章 - ${chapter?.title ?: ""} · ${formatEpubChapterProgressCompact(currentScrollRatio)}"',
     'return context.getString(R.string.epub_chapter_location_label, currentChapterIndex + 1, chapter?.title ?: "", formatEpubChapterProgressCompact(context, currentScrollRatio))'),
    ('Toast.makeText(context, "这条读书笔记没有可跳转的位置", Toast.LENGTH_SHORT).show()',
     'Toast.makeText(context, context.getString(R.string.epub_no_jump_target), Toast.LENGTH_SHORT).show()'),
    ('chapterInfo = "第${bookmark.chapterIndex + 1}章 - ${bookmark.chapterTitle.ifBlank { "未命名章节" }} · ${formatEpubBookmarkPosition(bookmark.scrollRatio)}",',
     'chapterInfo = context.getString(R.string.epub_bookmark_chapter_info, bookmark.chapterIndex + 1, bookmark.chapterTitle.ifBlank { context.getString(R.string.epub_unnamed_chapter) }, formatEpubBookmarkPosition(context, bookmark.scrollRatio)),'),
    ('"第${index + 1}章".contains(query, ignoreCase = true)', 'context.getString(R.string.epub_chapter_number_compact, index + 1).contains(query, ignoreCase = true)'),
    ('val chapterTitle = currentChapter?.title ?: "第${targetChapterIndex + 1}章"',
     'val chapterTitle = currentChapter?.title ?: context.getString(R.string.epub_chapter_with_title_fallback, targetChapterIndex + 1)'),
    ('title = { Text("跳转位置（近似）") },', 'title = { Text(stringResource(R.string.epub_jump_position_title)) },'),
    ('text = "仅在当前章节跳转，使用章节内进度%近似定位。",', 'text = stringResource(R.string.epub_jump_position_hint),'),
    ('text = "当前章节：第 ${currentChapterIndex + 1} 章",', 'text = stringResource(R.string.epub_jump_current_chapter, currentChapterIndex + 1),'),
    ('text = "目标进度：${targetPercent.toInt()}%",', 'text = stringResource(R.string.epub_jump_target_progress, targetPercent.toInt()),'),
    ('title = { Text("目录 - ${extractResult.bookInfo.title}") },', 'title = { Text(stringResource(R.string.epub_toc_title_with_book, extractResult.bookInfo.title)) },'),
    ('label = { Text("筛选章节") },', 'label = { Text(stringResource(R.string.epub_toc_filter_label)) },'),
    ('placeholder = { Text("输入章节号、标题或文件名") },', 'placeholder = { Text(stringResource(R.string.epub_toc_filter_placeholder)) },'),
    ('"当前第 ${currentChapterIndex + 1} 章，共 ${chapters.size} 章"', 'context.getString(R.string.epub_toc_current_summary, currentChapterIndex + 1, chapters.size)'),
    ('"匹配到 ${filteredTocEntries.size} / ${chapters.size} 章"', 'context.getString(R.string.epub_toc_match_summary, filteredTocEntries.size, chapters.size)'),
    ('text = "没有匹配的章节",', 'text = stringResource(R.string.epub_toc_no_match),'),
    ('text = "第 ${index + 1} 章${if (index == currentChapterIndex) " · 当前位置" else ""}",',
     'text = context.getString(R.string.epub_chapter_number, index + 1) + if (index == currentChapterIndex) stringResource(R.string.epub_chapter_current_marker) else "",'),
    ('title = { Text("收藏夹 - $zipFileName") },', 'title = { Text(stringResource(R.string.epub_bookmarks_title, zipFileName)) },'),
    ('"暂无收藏",', 'stringResource(R.string.epub_bookmarks_empty),'),
    ('"点击下方“新增当前位置收藏”可创建带定位的读书笔记",', 'stringResource(R.string.epub_bookmarks_empty_hint),'),
    ('label = { Text("过滤当前书收藏") },', 'label = { Text(stringResource(R.string.epub_bookmarks_filter_label)) },'),
    ('placeholder = { Text("输入章节、引文或感想") },', 'placeholder = { Text(stringResource(R.string.epub_bookmarks_filter_placeholder)) },'),
    ('"共 ${currentBookBookmarkEntries.size} 条定位笔记"', 'context.getString(R.string.epub_bookmarks_total, currentBookBookmarkEntries.size)'),
    ('"匹配到 ${filteredBookmarkEntries.size} / ${currentBookBookmarkEntries.size} 条"', 'context.getString(R.string.epub_bookmarks_match, filteredBookmarkEntries.size, currentBookBookmarkEntries.size)'),
    ('text = "没有匹配的收藏",', 'text = stringResource(R.string.epub_bookmarks_no_match),'),
    ('text = bookmark.chapterIndex?.let { "第${it + 1}章" } ?: "整本书",', 'text = bookmark.chapterIndex?.let { context.getString(R.string.epub_chapter_number_compact, it + 1) } ?: stringResource(R.string.common_whole_book),'),
    ('text = bookmark.chapterTitle ?: bookmark.chapterInfo ?: "未命名章节",', 'text = bookmark.chapterTitle ?: bookmark.chapterInfo ?: stringResource(R.string.epub_unnamed_chapter),'),
    ('text = bookmark.scrollRatio?.let { formatEpubBookmarkPosition(it) } ?: "未绑定具体位置",',
     'text = bookmark.scrollRatio?.let { formatEpubBookmarkPosition(context, it) } ?: stringResource(R.string.epub_unbound_position),'),
    ('text = "引文：$quote",', 'text = stringResource(R.string.epub_quote_prefix, quote),'),
    ('}) { Text("新增当前位置收藏") }', '}) { Text(stringResource(R.string.epub_add_current_bookmark)) }'),
    ('title = { Text("添加收藏") },', 'title = { Text(stringResource(R.string.epub_action_add_bookmark)) },'),
    ('"当前位置：${currentChapterLocationLabel()}",', 'stringResource(R.string.epub_current_location, currentChapterLocationLabel()),'),
    ('label = { Text("引文（优先使用当前选中内容，否则抓取当前段落，长文本会截断）") },', 'label = { Text(stringResource(R.string.epub_quote_field_label)) },'),
    ('label = { Text("感想（可选）") },', 'label = { Text(stringResource(R.string.epub_note_field_optional)) },'),
    ('Toast.makeText(context, "该位置已收藏，已转为编辑", Toast.LENGTH_SHORT).show()', 'Toast.makeText(context, context.getString(R.string.epub_bookmark_exists_edit), Toast.LENGTH_SHORT).show()'),
    ('Toast.makeText(context, "已添加收藏", Toast.LENGTH_SHORT).show()', 'Toast.makeText(context, context.getString(R.string.epub_bookmark_added), Toast.LENGTH_SHORT).show()'),
    ('Toast.makeText(context, "已更新", Toast.LENGTH_SHORT).show()', 'Toast.makeText(context, context.getString(R.string.common_updated), Toast.LENGTH_SHORT).show()'),
    ('text = { Text("确定删除这条读书笔记吗？") }', 'text = { Text(stringResource(R.string.book_note_delete_confirm_entry)) }'),
    ('Icon(Icons.Default.Menu, contentDescription = "目录")', 'Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.epub_toc_menu_desc))'),
    ('currentChapter?.title ?: "第${currentChapterIndex + 1}章"', 'currentChapter?.title ?: context.getString(R.string.epub_chapter_with_title_fallback, currentChapterIndex + 1)'),
    ('text = { Text("全文查找") },', 'text = { Text(stringResource(R.string.md_viewer_fulltext_find_title)) },'),
    ('contentDescription = "上一章",', 'contentDescription = stringResource(R.string.epub_prev_chapter),'),
    ('"上一章",', 'stringResource(R.string.epub_prev_chapter),'),
    ('contentDescription = if (dictAreaExpanded) "收起" else "展开",', 'contentDescription = if (dictAreaExpanded) stringResource(R.string.common_collapse) else stringResource(R.string.common_expand),'),
    ('result.word.ifBlank { "取词结果" },', 'result.word.ifBlank { stringResource(R.string.epub_dict_lookup_result) },'),
    ('contentDescription = "回退到上一个单词",', 'contentDescription = stringResource(R.string.epub_dict_fallback_word),'),
    ('"选择文字并复制到剪贴板查询",', 'stringResource(R.string.epub_dict_select_copy_hint),'),
    ('contentDescription = "下一章",', 'contentDescription = stringResource(R.string.epub_next_chapter),'),
    ('"下一章",', 'stringResource(R.string.epub_next_chapter),'),
    ('Text("无法加载章节内容", color = MaterialTheme.colorScheme.error)', 'Text(stringResource(R.string.epub_chapter_load_failed), color = MaterialTheme.colorScheme.error)'),
    ('contentDescription = "下一页",', 'contentDescription = stringResource(R.string.epub_next_page_desc),'),
    ('val note = bookmark.note.ifBlank { bookmark.chapterTitle.ifBlank { "书签" } }', 'val note = bookmark.note.ifBlank { bookmark.chapterTitle.ifBlank { context.getString(R.string.epub_bookmark_default_note) } }'),
    ('val label = if (bookmark.note.isBlank()) "签" else bookmark.note.take(2)', 'val label = if (bookmark.note.isBlank()) context.getString(R.string.epub_bookmark_default_label) else bookmark.note.take(2)'),
    ('note:${org.json.JSONObject.quote("上次阅读到这里")},', 'note:${org.json.JSONObject.quote(context.getString(R.string.epub_reading_progress_note))},'),
    ('label:${org.json.JSONObject.quote("上次")},', 'label:${org.json.JSONObject.quote(context.getString(R.string.epub_reading_progress_label))},'),
    ('private var documentTitleProvider: () -> String = { "当前页面" }', 'private var documentTitleProvider: () -> String = { context.getString(R.string.common_current_page) }'),
    ('val title = documentTitleProvider().ifBlank { "当前页面" }', 'val title = documentTitleProvider().ifBlank { context.getString(R.string.common_current_page) }'),
    ('fun currentPageLocationLabel(): String = "第${currentPage + 1}页 / 共${pageCount}页"', 'fun currentPageLocationLabel(): String = context.getString(R.string.pdf_page_location, currentPage + 1, pageCount)'),
    ('errorMsg = "PDF 文件为空"', 'errorMsg = context.getString(R.string.pdf_empty)'),
    ('errorMsg = "加载失败: ${e.message}"', 'errorMsg = context.getString(R.string.md_viewer_load_failed_detail, e.message ?: "")'),
    ('title = { Text("收藏夹 - $fileName") },', 'title = { Text(stringResource(R.string.epub_bookmarks_title, fileName)) },'),
    ('"点击下方“新增当前位置收藏”可创建定位笔记",', 'stringResource(R.string.epub_bookmarks_empty_hint_pdf),'),
    ('text = bookmark.chapterIndex?.let { "第${it + 1}页" } ?: "整本书",', 'text = bookmark.chapterIndex?.let { context.getString(R.string.pdf_page_title, it + 1) } ?: stringResource(R.string.common_whole_book),'),
    ('text = bookmark.chapterInfo ?: "未绑定位置",', 'text = bookmark.chapterInfo ?: stringResource(R.string.epub_unbound_position_short),'),
    ('TextButton(onClick = { requestAddBookmark() }) { Text("新增当前位置收藏") }', 'TextButton(onClick = { requestAddBookmark() }) { Text(stringResource(R.string.epub_add_current_bookmark)) }'),
    ('title = { Text("跳转到指定页") },', 'title = { Text(stringResource(R.string.pdf_go_to_page_title)) },'),
    ('"当前第 ${currentPage + 1} 页，共 $pageCount 页",', 'context.getString(R.string.pdf_current_page_summary, currentPage + 1, pageCount),'),
    ('label = { Text("目标页码（1 - $pageCount）") },', 'label = { Text(stringResource(R.string.pdf_target_page_label, pageCount)) },'),
    ('inputError = "请输入有效的数字"', 'inputError = context.getString(R.string.pdf_invalid_number)'),
    ('inputError = "页码范围：1 - $pageCount"', 'inputError = context.getString(R.string.pdf_page_range_error, pageCount)'),
    ('chapterTitle = "第${currentPage + 1}页",', 'chapterTitle = context.getString(R.string.pdf_page_title, currentPage + 1),'),
    ('Icon(Icons.Default.MoreVert, contentDescription = "更多")', 'Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.main_tab_more))'),
    ('text = { Text("添加收藏") },', 'text = { Text(stringResource(R.string.epub_action_add_bookmark)) },'),
    ('text = { Text("查看收藏") },', 'text = { Text(stringResource(R.string.epub_action_view_bookmarks)) },'),
    ('text = { Text("全屏") },', 'text = { Text(stringResource(R.string.epub_action_fullscreen)) },'),
    ('Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "上一页")', 'Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.pdf_prev_page_desc))'),
    ('Text("加载中...", style = MaterialTheme.typography.bodyLarge)', 'Text(stringResource(R.string.common_loading_ellipsis), style = MaterialTheme.typography.bodyLarge)'),
    ('Text("正在渲染页面...", style = MaterialTheme.typography.bodyLarge)', 'Text(stringResource(R.string.pdf_rendering), style = MaterialTheme.typography.bodyLarge)'),
    ('"提示：PDF 页面渲染为图像，暂不支持文字选择。\\n如需选择文字，请使用专业 PDF 阅读器。",', 'stringResource(R.string.pdf_text_selection_hint),'),
    ('contentDescription = "第 ${currentPage + 1} 页",', 'contentDescription = stringResource(R.string.pdf_page_desc, currentPage + 1),'),
    ('private fun HtmlZipNoIndexScreen(\n    contentDir: File,\n    zipFileName: String,\n    onBack: () -> Unit,\n    logDebug: ((String) -> Unit)? = null\n) {\n    val fileList',
     'private fun HtmlZipNoIndexScreen(\n    contentDir: File,\n    zipFileName: String,\n    onBack: () -> Unit,\n    logDebug: ((String) -> Unit)? = null\n) {\n    val context = LocalContext.current\n    val fileList'),
    ('Text("PDF 为空", style = MaterialTheme.typography.bodyLarge)', 'Text(stringResource(R.string.pdf_empty_short), style = MaterialTheme.typography.bodyLarge)'),
    ('formatEpubChapterProgressCompact(currentScrollRatio)', 'formatEpubChapterProgressCompact(context, currentScrollRatio)'),
]

MD_REPS_COMMON = [
    ('Text("取消")', 'Text(stringResource(R.string.common_cancel))'),
    ('Text("关闭")', 'Text(stringResource(R.string.common_close))'),
    ('Text("删除")', 'Text(stringResource(R.string.common_delete))'),
    ('Text("编辑")', 'Text(stringResource(R.string.common_edit))'),
    ('Text("添加")', 'Text(stringResource(R.string.common_add))'),
    ('Text("跳转")', 'Text(stringResource(R.string.common_jump))'),
    ('title = { Text("删除读书笔记") },', 'title = { Text(stringResource(R.string.book_note_delete_title)) },'),
    ('Icon(Icons.Default.Edit, contentDescription = "编辑", modifier = Modifier.size(18.dp))', 'Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.common_edit), modifier = Modifier.size(18.dp))'),
    ('Icon(Icons.Default.Delete, contentDescription = "删除", modifier = Modifier.size(18.dp))', 'Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.common_delete), modifier = Modifier.size(18.dp))'),
]

OLD_FORMAT_BOOKMARK = '''private fun formatEpubBookmarkPosition(scrollRatio: Float): String {
    val percent = (scrollRatio.coerceIn(0f, 1f) * 100f).toInt().coerceIn(0, 100)
    val section = when {
        percent <= 5 -> "开头"
        percent <= 25 -> "前段"
        percent <= 45 -> "前中段"
        percent <= 65 -> "中段"
        percent <= 85 -> "后中段"
        percent <= 97 -> "后段"
        else -> "末尾"
    }
    return "章节内$section ($percent%)"
}'''

OLD_FORMAT_PROGRESS = '''private fun formatEpubChapterProgressCompact(scrollRatio: Float): String {
    val percent = (scrollRatio.coerceIn(0f, 1f) * 100f).toInt().coerceIn(0, 100)
    val section = when {
        percent <= 5 -> "开头"
        percent <= 25 -> "前段"
        percent <= 45 -> "前中段"
        percent <= 65 -> "中段"
        percent <= 85 -> "后中段"
        percent <= 97 -> "后段"
        else -> "末尾"
    }
    return "本章$section $percent%"
}'''

if __name__ == "__main__":
    add_strings(ZH, STRINGS_ZH)
    add_strings(EN, STRINGS_EN)
    text = MD.read_text(encoding="utf-8")
    text = text.replace(OLD_FORMAT_BOOKMARK, FORMAT_EPUB_BOOKMARK)
    text = text.replace(OLD_FORMAT_PROGRESS, FORMAT_EPUB_PROGRESS)
    MD.write_text(text, encoding="utf-8")
    n1 = apply_replacements(MD, MD_REPS)
    # second pass for patterns that appear in both MD and HTML zip screens
    n1b = apply_replacements(MD, [
        ('text = "压缩包内容：",', 'text = stringResource(R.string.md_viewer_zip_contents),'),
        ('text = "（空目录）",', 'text = stringResource(R.string.common_empty_directory),'),
    ], replace_all=True)
    n2 = apply_replacements(MD, MD_REPS_COMMON, replace_all=True)
    print(f"MarkdownViewerScreen: {n1}/{len(MD_REPS)} specific, {n1b} dup, {n2} common")
