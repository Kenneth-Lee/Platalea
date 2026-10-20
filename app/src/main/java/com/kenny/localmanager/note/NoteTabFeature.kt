package com.kenny.localmanager.note

import androidx.compose.runtime.Composable
import com.kenny.localmanager.booknote.BookNoteModule
import com.kenny.localmanager.booknote.BookNoteService
import com.kenny.localmanager.booknote.BookNoteTabRoute
import com.kenny.localmanager.config.QuickNoteConfigView
import com.kenny.localmanager.quicknote.QuickNoteModule
import com.kenny.localmanager.quicknote.QuickNoteService
import com.kenny.localmanager.quicknote.QuickNoteTabRoute
import com.kenny.localmanager.ui.MainTab
import com.kenny.localmanager.ui.MainTabFeature
import com.kenny.localmanager.quicknote.QUICK_NOTE_GPG_FILE_NAME
import com.kenny.localmanager.ui.TabPasswordPromptDialog

/**
 * 速记/书摘 tab 装配（功能独立化改造后薄壳）。
 *
 * 域数据与收尾协程住 QuickNoteService/BookNoteService（组合根创建）；
 * 此处只把 service 接到 tab 层：content = 功能页面，overlay = 密码提示
 * 对话框（跨 tab 显示：从其他 tab 触发的打开请求需要密码时弹出）。
 * 跨域出口不再经 feature 接口转发——调用方直呼 service（如
 * quickNoteService.requestOpenWithCachedPassword / requestExitApp）。
 */

/** 速记 tab 装配。 */
internal fun quickNoteTabFeature(
    config: QuickNoteConfigView
): MainTabFeature = object : MainTabFeature {
    override val tab: MainTab get() = MainTab.QUICK_NOTE
    override val content: @Composable () -> Unit = {
        QuickNoteTabRoute(config = config)
    }
    override val overlay: (@Composable () -> Unit)? = {
        TabPasswordPromptDialog(
            state = QuickNoteModule.service.passwordPromptState,
            fileName = QUICK_NOTE_GPG_FILE_NAME
        )
    }
}

/** 书摘 tab 装配。 */
internal fun bookNoteTabFeature(): MainTabFeature = object : MainTabFeature {
    override val tab: MainTab get() = MainTab.BOOK_NOTE
    override val content: @Composable () -> Unit = {
        BookNoteTabRoute()
    }
    override val overlay: (@Composable () -> Unit)? = {
        TabPasswordPromptDialog(
            state = BookNoteModule.service.passwordPromptState,
            fileName = QUICK_NOTE_GPG_FILE_NAME
        )
    }
}
