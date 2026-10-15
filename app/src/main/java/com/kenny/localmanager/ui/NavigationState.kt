package com.kenny.localmanager.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow

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
) : Navigator {
    /** tab 层当前显示的 tab；null = 启动恢复未完成（宿主读取时回落 DIRECTORY）。 */
    var currentTab by mutableStateOf<MainTab?>(null)
        private set

    /** 功能页栈（头部为栈底）。 */
    val featurePages = mutableStateListOf<AppPage>()

    /** 栈顶功能页；空 = 显示 tab 层。 */
    val top: AppPage? get() = featurePages.lastOrNull()

    /** tab 层内切换（等值忽略）；持久化决策经构造回调交宿主。 */
    override fun switchTab(tab: MainTab) {
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
    override fun push(page: AppPage) {
        featurePages.add(page)
    }

    /**
     * 退出自己 push 的功能页：从栈中移除自己，不动其他页面。
     *
     * 不限栈顶：向导确认时"清向导状态 + push 执行页"同帧发生，向导页
     * 自退时执行页可能已在其上，限定栈顶会把自己卡在栈中。
     */
    override fun pop(page: AppPage) {
        featurePages.remove(page)
    }
}

/** 导航服务唯一创建入口：持久化回调由宿主注入（写入时机归 AppShell）。 */
@Composable
internal fun rememberNavigationState(
    onTabSwitched: (tab: MainTab, previous: MainTab?) -> Unit
): NavigationState = remember { NavigationState(onTabSwitched) }

/**
 * 导航能力（《主界面判断归位设计》§4）：主界面暴露给各域的唯一一组显示接口。
 *
 * 实现：[NavigationState]（组合根创建，主界面全部的显示状态）。域经组合根
 * 接线拿到 Navigator 引用，不感知实现；tab 层模板（MainTabsScaffold）用它
 * 渲染 tab 栏与切换。
 */
internal interface Navigator {
    /** 在 tab 层内切换（tab 层页面经模板调用）。 */
    fun switchTab(tab: MainTab)

    /** 进入功能页（成为栈顶）。 */
    fun push(page: AppPage)

    /** 退出自己 push 的功能页（从栈中移除自己）。 */
    fun pop(page: AppPage)
}

/**
 * 对话框页绑定器（P2）：把"域状态非空"绑定为页面在栈。
 *
 * 适配向导域的置位即弹窗模型：域 open() 置状态 → push 页面；任意路径清
 * 状态（close / 完成 / 取消）→ pop。页面生存期是域状态的投影，宿主与域
 * 都不需要维护"状态与栈同步"。同一时刻只有一个向导可活跃（对话框模态），
 * 因此至多一个对话框页在栈且必为栈顶。
 */
@Composable
internal fun DialogPageBinder(
    nav: NavigationState,
    page: AppPage,
    active: () -> Boolean
) {
    LaunchedEffect(page) {
        snapshotFlow { active() }.collect { on ->
            if (on) {
                if (nav.top !== page) nav.push(page)
            } else {
                nav.pop(page)
            }
        }
    }
}
