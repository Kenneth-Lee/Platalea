package com.kenny.localmanager.ui

import androidx.compose.runtime.Composable

/**
 * 主界面（AppShell）对 tab 功能的唯一可见接口。
 *
 * 主程序对子功能的一切交互收敛为：持有一组 MainTabFeature，无条件组合它们的
 * content / overlay，不感知任何子功能内部结构（状态、屏幕、对话框如何实现）。
 *
 * 约定（见《主界面AppShell分层设计》）：
 * - content：tab 激活时组合（组合即进入，离开组合即离开，生命周期由功能内部
 *   DisposableEffect 自理，无需 enter/leave 钩子）
 * - overlay：全局覆盖投影（跨 tab 显示的对话框等），主程序无条件组合
 */
internal interface MainTabFeature {
    val tab: MainTab
    val content: @Composable () -> Unit
    val overlay: (@Composable () -> Unit)? get() = null
}

/** 轻量 tab 适配器：尚无独立域对象的 tab 用它包住现有 content lambda。 */
internal fun mainTabFeature(
    tab: MainTab,
    content: @Composable () -> Unit
): MainTabFeature = object : MainTabFeature {
    override val tab = tab
    override val content = content
}

/**
 * tab 内容宿主：从 features 中取当前 tab 的 content 组合。
 * 开发期校验 features 覆盖全部枚举，防止静默漏 tab。
 */
@Composable
internal fun MainTabContentHost(
    activeMainTab: MainTab,
    features: List<MainTabFeature>
) {
    features.forEach { it.overlay?.let { overlay -> overlay() } }
    features.firstOrNull { it.tab == activeMainTab }?.let { it.content() }
}
