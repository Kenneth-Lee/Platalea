package com.kenny.localmanager.ui

import android.net.Uri
import com.kenny.localmanager.file.DocumentFileModel
import com.kenny.localmanager.file.EpubExtractResult
import java.io.File

// ============================================================================
// Archive 阅读器域（Phase 3 / 方案 §7）—— 数据类型层
//
// 本域由 5 个文件组成（自底向上）：
//   ArchiveReaderRoute.kt     纯数据类型：请求参数与查看器输入状态（本文件，无逻辑无 UI）
//   ArchiveReaderWorkflow.kt  状态机：请求阶段（检查加密 -> 密码 -> 解压）的 8 组逻辑
//   ArchiveReaderDialogs.kt   请求阶段 UI：密码 / 进度 / 日志 / 错误对话框
//   VidZipViewerScreen.kt     .vid.zip 查看器 UI（其余 4 种查看器在各自 Screen 文件中）
//   ArchiveViewerRouteHost.kt 查看器阶段宿主：when 分派 + 最近打开记录 + 加密退出善后
//
// 覆盖 8 种"从文件进入阅读"的临时 Route，同一时刻只允许一个阅读请求在处理。
// ============================================================================

/** 压缩包 / 文档阅读器的种类。 */
internal enum class ArchiveReaderKind {
    MD_ZIP,    // .md.zip / .rst.zip
    HTML_ZIP,  // .html.zip
    PIC_ZIP,   // .pic.zip
    VID_ZIP    // .vid.zip
}

/** 返回键可直接取消的请求种类（与原 BackHandler 链一致：不含 VID_ZIP / TXT / LLM）。 */
internal val ArchiveReaderCancelableKinds: Set<ArchiveReaderKind> = setOf(
    ArchiveReaderKind.MD_ZIP,
    ArchiveReaderKind.HTML_ZIP,
    ArchiveReaderKind.PIC_ZIP
)

/** 待处理阅读请求：检查加密 → 密码 → 解压阶段的输入参数。 */
internal data class ArchiveReaderRequest(
    val kind: ArchiveReaderKind,
    val target: DocumentFileModel
)

/** 解压完成后的查看器输入状态（*ViewState 系列，sealed 统一）。供宿主分派到对应 Screen。 */
internal sealed interface ArchiveViewerState {
    /** 是否记入"最近打开"。目录图片画廊直接进入时为 false。 */
    val recordRecent: Boolean get() = true
}

/** .md.zip / .rst.zip 查看器状态。 */
internal data class MdZipViewState(
    val targetFile: File?,  // null 表示未找到可渲染文件，此时显示目录内容
    val contentDir: File,
    val zipFileName: String,
    val zipUri: Uri,
    val isEncrypted: Boolean
) : ArchiveViewerState

/** .html.zip 查看器状态。 */
internal data class HtmlZipViewState(
    val indexFile: File?,
    val contentDir: File,
    val zipFileName: String,
    val zipUri: Uri,
    val isEncrypted: Boolean
) : ArchiveViewerState

/** EPUB 渲染器查看器状态（来源 .epub / .llm.zip / .txt / .llm，状态机在 EpubReaderWorkflowState）。 */
internal data class EpubViewState(
    val extractResult: EpubExtractResult,
    val zipFileName: String,
    val epubUri: Uri,
    val isEncrypted: Boolean
) : ArchiveViewerState

/** .pic.zip 查看器状态。 */
internal data class PicZipViewState(
    val contentDir: File,
    val imagePaths: List<String>,
    val zipFileName: String,
    val zipUri: Uri,
    val isEncrypted: Boolean,
    val password: CharArray? = null,
    val initialIndex: Int = 0,
    override val recordRecent: Boolean = true,
    val cleanupCacheDir: File? = null
) : ArchiveViewerState

/** .vid.zip 查看器状态。 */
internal data class VidZipViewState(
    val contentDir: File,
    val videoPaths: List<String>,
    val zipFileName: String,
    val zipUri: Uri,
    val isEncrypted: Boolean,
    val password: CharArray? = null,
    override val recordRecent: Boolean = true,
    val cleanupCacheDir: File? = null
) : ArchiveViewerState

/** 加密缓存退出确认对话框（删除缓存退出 / 保留缓存退出）。 */
internal data class EncryptedCacheExitDialogState(
    val title: String,
    val onDelete: () -> Unit,
    val onKeep: () -> Unit
)
