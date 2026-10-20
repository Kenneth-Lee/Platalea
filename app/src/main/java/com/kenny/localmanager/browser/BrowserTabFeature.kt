package com.kenny.localmanager.browser

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.kenny.localmanager.config.BrowserConfigView
import com.kenny.localmanager.ui.MainTab
import com.kenny.localmanager.ui.MainTabFeature
import com.kenny.localmanager.ui.RootService

/**
 * BrowserTab 域呈现接口（MainTabFeature §4）：
 * 主程序持此对象组合 DIRECTORY tab，目录域内部结构（状态类、浏览器屏幕）不可见。
 *
 * [state] 为目录域共享数据对象（AppShell 分层设计 §2.4）：
 * rootUri / currentUri / refresh / 后退栈 / 待处理列表，供宿主与各功能域命令式直呼。
 */
internal interface BrowserTabFeature : MainTabFeature {
    val state: BrowserTabState
}

/**
 * 目录域共享数据对象的唯一创建入口：
 * 创建 BrowserTabState 并内聚 prefs → 状态镜像接线
 * （root 恢复、初始路径对齐、过滤/隐藏/外开偏好镜像）。
 */
@Composable
internal fun rememberBrowserTabState(
    context: Context,
    config: BrowserConfigView,
    initialDirUri: String?
): BrowserTabState {
    val state = remember { BrowserTabState(context, config) }
    LaunchedEffect(Unit) {
        RootService.restore(config)
    }
    LaunchedEffect(state.rootUri, initialDirUri) {
        state.ensureCurrentUri(initialDirUri)
    }
    LaunchedEffect(config) {
        config.filterVisible.collect { state.filterVisible = it }
    }
    LaunchedEffect(config) {
        config.hideDotFiles.collect { state.hideDotFiles = it }
    }
    LaunchedEffect(config) {
        config.externalOpenByExtension.collect { state.preferredExternalPackages = it }
    }
    return state
}

/**
 * BrowserTab 域功能对象工厂：DIRECTORY tab 的呈现入口。
 * BrowserTabHostDependencies 的构造（跨域出口接线）留宿主——那是显示协调职责。
 */
@Composable
internal fun browserTabFeature(
    state: BrowserTabState,
    deps: BrowserTabHostDependencies
): BrowserTabFeature {
    return remember(state, deps) {
        object : BrowserTabFeature {
            override val tab = MainTab.DIRECTORY
            override val state = state
            override val content: @Composable () -> Unit = { BrowserTabHost(state = state, deps = deps) }
        }
    }
}
