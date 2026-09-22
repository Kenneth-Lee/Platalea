package com.kenny.localmanager.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import com.kenny.localmanager.R

/** 速记 tab 功能对象（接口面：呈现 + 跨域窄 API，实现不可见）。 */
internal interface QuickNoteTabFeature : MainTabFeature {
    /** 文件浏览"转到速记"出口：切换 tab 后带缓存密码打开。 */
    fun requestOpenWithCachedPassword()

    /** 退出 App 时的速记持久化：进行中则提示稍候，否则写盘后回调确认退出。 */
    fun requestExitApp(context: Context, onExitConfirmed: () -> Unit)
}

/** 书摘 tab 功能对象：呈现 + transient 域（PDF 阅读书摘集成）所需的书摘读写投影。 */
internal interface BookNoteTabFeature : MainTabFeature {
    val bookNoteData: BookNoteLoadedData?
    val bookNoteEntries: List<BookNoteEntry>
    val bookNoteInProgress: Boolean
    fun requestOpenBookNotes()
    fun updateBookNoteEntries(entries: List<BookNoteEntry>)
}

/**
 * 速记 tab 工厂：controller 创建（含 root/激活态生命周期）与密码对话框内聚于此。
 * controller 随 remember 键（rootUri/isActive）重建，State 为无状态薄壳随组合更新。
 */
@Composable
internal fun quickNoteTabFeature(
    config: QuickNoteConfigView,
    rootUri: String?,
    isActive: Boolean,
    markdownViewerSessionCache: ViewerSessions
): QuickNoteTabFeature {
    val controller = rememberQuickNoteTabController(
        rootUri = rootUri,
        isActive = isActive,
        markdownViewerSessionCache = markdownViewerSessionCache
    )
    return QuickNoteTabState(config, controller)
}

/** 书摘 tab 工厂。 */
@Composable
internal fun bookNoteTabFeature(
    rootUri: String?,
    isActive: Boolean,
    markdownViewerSessionCache: ViewerSessions
): BookNoteTabFeature {
    val controller = rememberBookNoteTabController(
        rootUri = rootUri,
        isActive = isActive,
        markdownViewerSessionCache = markdownViewerSessionCache
    )
    return BookNoteTabState(controller)
}

private class QuickNoteTabState(
    private val config: QuickNoteConfigView,
    private val controller: QuickNoteTabController
) : QuickNoteTabFeature {
    override val tab: MainTab get() = MainTab.QUICK_NOTE
    override val content: @Composable () -> Unit = {
        QuickNoteTabRoute(config = config, controller = controller)
    }
    override val overlay: (@Composable () -> Unit)? = {
        TabPasswordPromptDialog(
            state = controller.passwordPromptState,
            fileName = QUICK_NOTE_GPG_FILE_NAME
        )
    }

    override fun requestOpenWithCachedPassword() {
        controller.requestOpenWithCachedPassword(true)
    }

    override fun requestExitApp(context: Context, onExitConfirmed: () -> Unit) {
        if (controller.inProgress) {
            Toast.makeText(context, context.getString(R.string.browser_quick_note_busy), Toast.LENGTH_SHORT).show()
            return
        }
        controller.persistIfNeeded(context.getString(R.string.quick_note_persist_reason_exit)) { saved ->
            if (saved) {
                onExitConfirmed()
            }
        }
    }
}

private class BookNoteTabState(
    private val controller: BookNoteTabController
) : BookNoteTabFeature {
    override val tab: MainTab get() = MainTab.BOOK_NOTE
    override val content: @Composable () -> Unit = {
        BookNoteTabRoute(controller = controller)
    }
    override val overlay: (@Composable () -> Unit)? = {
        TabPasswordPromptDialog(
            state = controller.passwordPromptState,
            fileName = QUICK_NOTE_GPG_FILE_NAME
        )
    }

    override val bookNoteData: BookNoteLoadedData? get() = controller.data
    override val bookNoteEntries: List<BookNoteEntry> get() = controller.entriesSnapshot
    override val bookNoteInProgress: Boolean get() = controller.inProgress

    override fun requestOpenBookNotes() {
        controller.requestOpenWithCachedPassword()
    }

    override fun updateBookNoteEntries(entries: List<BookNoteEntry>) {
        controller.updateEntries(entries)
    }
}
