package com.kenny.localmanager.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.kenny.localmanager.R

/**
 * 书摘 tab 页面（功能独立化改造，与速记同构）。
 *
 * 页面生存期 = 功能运行期。与速记的差异：条目更新即时自动保存
 * （BookNoteService.updateEntries），退出时不做"切换标签"持久化（原行为）。
 */
@Composable
internal fun BookNoteTabRoute(
    service: BookNoteService
) {
    // 功能启动：会话未加载时带缓存密码自动打开
    LaunchedEffect(Unit) { service.ensureLoaded() }

    // 后台持久化（app 退到后台时）
    val reasonBackground = stringResource(R.string.book_note_persist_reason_background)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, reasonBackground) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && service.data != null) {
                service.persistIfNeeded(reasonBackground)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    TabRouteContent(
        controller = service,
        loadingText = stringResource(R.string.book_note_opening)
    ) { data ->
        BookNoteScreen(
            loadedData = data,
            inProgress = service.inProgress,
            onEntriesChanged = service::updateEntries
        )
    }
}
