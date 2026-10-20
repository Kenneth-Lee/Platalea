package com.kenny.localmanager.browser

import com.kenny.localmanager.file.DocumentFileModel

/**
 * 操作会话对话框的启动参数（拆解设计 第四批）：BrowserScreen 持一个
 * 可空值，非空即挂起对应对话框。每个对话框是独立文件内的 Composable，
 * 输入状态随对话框内聚（remember 于会话），关闭即销毁——对话框文本
 * 不再残留到下一次打开。
 */
internal sealed interface DialogSession {
    /** 删除确认（含移入回收站/永久删除选择）。 */
    data class Delete(val target: DocumentFileModel) : DialogSession

    /** 文件详情（元数据、压缩包内容、重命名、EXIF/音频标注编辑）。 */
    data class Detail(val target: DocumentFileModel) : DialogSession

    /** 外部应用打开选择（含按类型筛选）。 */
    data class ExternalOpen(val target: DocumentFileModel) : DialogSession

    /** 打开 URL。 */
    data object OpenUrl : DialogSession

    /** 新建文件。 */
    data object CreateFile : DialogSession

    /** 新建目录。 */
    data object CreateDir : DialogSession

    /** 待处理列表快速拷贝到当前目录确认。 */
    data object QuickCopy : DialogSession

    /** 待处理列表快速移动到当前目录确认。 */
    data object QuickMove : DialogSession

    /** 递归查找（输入与结果两个对话框共享同一会话状态）。 */
    data object Search : DialogSession
}
