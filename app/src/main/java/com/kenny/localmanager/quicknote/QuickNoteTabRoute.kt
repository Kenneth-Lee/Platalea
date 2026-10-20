package com.kenny.localmanager.quicknote

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.kenny.localmanager.R
import com.kenny.localmanager.config.QuickNoteConfigView
import com.kenny.localmanager.ui.TabRouteContent

/**
 * 速记 tab 页面（功能独立化改造）。
 *
 * 页面生存期 = 功能运行期：进入组合时启动（ensureLoaded），离开组合时退出
 * （onDispose 触发"切换标签"持久化；收尾协程在 QuickNoteService 中完成，
 * 不依赖页面存活）。页面只做服务投影 + 页面私有 UI 态。
 */
@Composable
internal fun QuickNoteTabRoute(
    config: QuickNoteConfigView
) {
    val service = QuickNoteModule.service
    // 功能启动：会话未加载时带缓存密码自动打开
    LaunchedEffect(Unit) { service.ensureLoaded() }

    // startWithAddDialog 一次性意图的消费（原 isActive 触发链的页面等价）
    LaunchedEffect(service.data, service.startWithAddDialog) {
        if (service.data != null && service.startWithAddDialog) {
            service.startWithAddDialog = false
        }
    }

    // 功能退出协议：页面离开组合 = 功能退出（tab 切走或全屏 Route 顶替），
    // 触发持久化；无变化时 hash 守卫直接跳过
    val reasonTabSwitch = stringResource(R.string.quick_note_persist_reason_tab_switch)
    val reasonBackground = stringResource(R.string.quick_note_persist_reason_background)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, reasonTabSwitch, reasonBackground) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                service.persistIfNeeded(reasonBackground)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            service.persistIfNeeded(reasonTabSwitch)
        }
    }

    TabRouteContent(
        controller = service,
        loadingText = stringResource(R.string.quick_note_opening)
    ) { data ->
        QuickNoteScreen(
            config = config,
            loadedData = data,
            startWithAddDialog = service.startWithAddDialog,
            inProgress = service.inProgress,
            onEntriesChanged = service::updateEntries
        )
    }
}
