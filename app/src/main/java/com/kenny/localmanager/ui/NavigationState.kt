package com.kenny.localmanager.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * 页面（AppPage）：一个功能的一次完整显示（《主界面判断归位设计》§2.2）。
 *
 * 页面自绘整个屏幕（含自选装饰，如 tab 栏），是纯投影——业务状态在
 * 各域的全局对象中，页面组合时读对象显示，离开组合状态不丢。
 * 同一时刻只有页面栈栈顶一页在组合。
 */
internal interface AppPage {
    val content: @Composable () -> Unit
}

/** 页面工厂：包住一个整屏 composable。 */
internal fun appPage(content: @Composable () -> Unit): AppPage =
    object : AppPage {
        override val content = content
    }

/**
 * 导航服务（《主界面判断归位设计》§2.3）：主界面全部的显示状态。
 *
 * 栈分两层：
 * - tab 层（栈底）：switchTab 替换当前显示的 tab 页，tab 间状态保持靠各域
 *   对象而非页面；
 * - 功能页层（栈底之上）：push 进入 / pop 退出；功能页不带 tab 栏，用户
 *   无法从中途切走，流程完整性由导航语义保住。
 *
 * pop(page) 仅当栈顶仍是该页时生效，防止晚到的关闭误弹后入栈的页面。
 */
internal class NavigationState internal constructor(
    private val onTabSwitched: (tab: MainTab, previous: MainTab?) -> Unit
) {
    /** tab 层当前显示的 tab；null = 启动恢复未完成（宿主读取时回落 DIRECTORY）。 */
    var currentTab by mutableStateOf<MainTab?>(null)
        private set

    /** 功能页栈（头部为栈底）。 */
    val featurePages = mutableStateListOf<AppPage>()

    /** 栈顶功能页；空 = 显示 tab 层。 */
    val top: AppPage? get() = featurePages.lastOrNull()

    /** tab 层内切换（等值忽略）；持久化决策经构造回调交宿主。 */
    fun switchTab(tab: MainTab) {
        val previous = currentTab
        if (previous == tab) return
        currentTab = tab
        onTabSwitched(tab, previous)
    }

    /** 启动恢复：设置初始 tab，不触发持久化（恢复不改写已保存值）。 */
    fun restore(tab: MainTab) {
        currentTab = tab
    }

    /** 进入功能页（成为栈顶，整页渲染）。 */
    fun push(page: AppPage) {
        featurePages.add(page)
    }

    /** 退出自己 push 的功能页：仅当栈顶仍是该页时弹出。 */
    fun pop(page: AppPage) {
        if (featurePages.lastOrNull() === page) {
            featurePages.remove(page)
        }
    }
}

/** 导航服务唯一创建入口：持久化回调由宿主注入（写入时机归 AppShell）。 */
@Composable
internal fun rememberNavigationState(
    onTabSwitched: (tab: MainTab, previous: MainTab?) -> Unit
): NavigationState = remember { NavigationState(onTabSwitched) }
