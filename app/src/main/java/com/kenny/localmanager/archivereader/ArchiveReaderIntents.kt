package com.kenny.localmanager.archivereader

import android.content.Context
import android.widget.Toast
import androidx.documentfile.provider.DocumentFile
import com.kenny.localmanager.R
import com.kenny.localmanager.browser.BrowserActionContext
import com.kenny.localmanager.browser.BrowserActions
import com.kenny.localmanager.browser.BrowserOpenIntent
import com.kenny.localmanager.file.DocumentFileModel
import com.kenny.localmanager.file.isCompressedHtml
import com.kenny.localmanager.file.isCompressedMarkdown
import com.kenny.localmanager.file.isGalleryImageFile
import com.kenny.localmanager.file.isPicZip
import com.kenny.localmanager.file.isVidZip
import com.kenny.localmanager.file.openInputStreamSafe
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 归档查看器域的浏览器能力声明（拆解设计 第一批压缩包意图、第三批
 * 目录图库意图）：压缩包阅读（槽 100）与目录图片图库（槽 500，
 * 单击图片打开目录图集）。open 直呼本模块 service，不经浏览器参数表。
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

    private object DirectoryGalleryIntent : BrowserOpenIntent {
        override val id = "archivereader.dir.gallery"
        override val slot = 500
        override val labelRes = R.string.context_view_images
        override fun applies(target: DocumentFileModel) =
            !target.isDirectory && isGalleryImageFile(target.name)
        override fun open(ctx: BrowserActionContext) {
            val dirUri = ctx.snapshot.currentUri ?: return
            ctx.scope.launch {
                runCatching {
                    openDirectoryImageGallery(
                        ctx.appContext,
                        ctx.target,
                        dirUri,
                        ctx.snapshot.gallerySiblings
                    )
                }.onFailure { error ->
                    Toast.makeText(
                        ctx.appContext,
                        ctx.appContext.getString(
                            R.string.browser_open_dir_images_failed,
                            error.message ?: error.javaClass.simpleName
                        ),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    fun registerAll(registry: BrowserActions.Registry) {
        registry.register(MdZipIntent)
        registry.register(HtmlZipIntent)
        registry.register(PicZipIntent)
        registry.register(VidZipIntent)
        registry.register(DirectoryGalleryIntent)
    }
}

/**
 * 目录图片图库（原 BrowserTabHost 内联编排，随第三批图库意图迁入
 * 本域）：把目录图片复制到缓存目录后以图集视图打开。
 */
internal suspend fun openDirectoryImageGallery(
    context: Context,
    currentItem: DocumentFileModel,
    currentDirUri: String,
    siblingImages: List<DocumentFileModel>
) {
    val normalizedImages = siblingImages
        .filter { !it.isDirectory && isGalleryImageFile(it.name) }
        .distinctBy { it.uri.toString() }
    if (normalizedImages.isEmpty()) {
        Toast.makeText(context, context.getString(R.string.browser_no_images_in_dir), Toast.LENGTH_SHORT).show()
        return
    }
    val initialIndex = normalizedImages.indexOfFirst { it.uri == currentItem.uri }
    if (initialIndex < 0) {
        Toast.makeText(context, context.getString(R.string.browser_image_position_failed, currentItem.name), Toast.LENGTH_SHORT).show()
        return
    }
    val galleryRoot = File(context.cacheDir, "pic_dir_gallery")
    val galleryKey = currentDirUri.hashCode().toUInt().toString(16)
    val cacheDir = File(galleryRoot, galleryKey)
    val contentDir = File(cacheDir, "content")
    withContext(Dispatchers.IO) {
        if (cacheDir.exists()) {
            cacheDir.deleteRecursively()
        }
        contentDir.mkdirs()
        normalizedImages.forEachIndexed { index, model ->
            val targetFile = File(contentDir, model.name)
            val bytes = context.contentResolver.openInputStreamSafe(model.uri)?.use { it.readBytes() }
                ?: throw IllegalStateException(context.getString(R.string.browser_image_read_failed, index + 1, model.name, model.uri))
            targetFile.outputStream().use { it.write(bytes) }
        }
        File(cacheDir, ".last_index").writeText(initialIndex.toString())
    }
    val dirTitle = DocumentFile.fromTreeUri(context, android.net.Uri.parse(currentDirUri))?.name
        ?: DocumentFile.fromSingleUri(context, android.net.Uri.parse(currentDirUri))?.name
        ?: currentItem.name
    ArchiveReaderModule.service.openViewer(
        PicZipViewState(
            contentDir = contentDir,
            imagePaths = normalizedImages.map { it.name },
            zipFileName = dirTitle,
            zipUri = currentItem.uri,
            isEncrypted = false,
            password = null,
            initialIndex = initialIndex,
            recordRecent = false,
            cleanupCacheDir = cacheDir
        )
    )
}
