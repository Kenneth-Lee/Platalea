package com.kenny.localmanager.ui

import com.kenny.localmanager.R

/**
 * tab 的退出恢复策略（AppShell 编排约定）：
 * - PERSISTENT：常规 tab，退出时保存、下次启动恢复到它
 * - TRANSIENT：会话型 tab（网络服务等），退出不作为恢复目标，
 *   启动恢复时回落到最近的 PERSISTENT tab
 */
internal enum class TabExitRestorePolicy { PERSISTENT, TRANSIENT }

/**
 * 主界面 tab 层的页面集合（导航服务 Vocabulary）。
 *
 * 为什么引入：tab 是 tab 层页面模板渲染的单位；导航服务（NavigationState）
 * 与各域都以它指称页面，词汇须住在一个中立文件，避免任何一方反向依赖。
 * 新增 tab：在此登记枚举项（key 持久化用、labelRes 模板渲染用）。
 */
internal enum class MainTab(
    val key: String,
    val labelRes: Int,
    val exitRestorePolicy: TabExitRestorePolicy = TabExitRestorePolicy.PERSISTENT
) {
    DIRECTORY("directory", R.string.main_tab_directory),
    RECENT("recent", R.string.main_tab_recent),
    PLAYER("player", R.string.main_tab_player),
    FTP("ftp", R.string.main_tab_ftp, TabExitRestorePolicy.TRANSIENT),
    LOCAL_SHARE("local_share", R.string.main_tab_local_share, TabExitRestorePolicy.TRANSIENT),
    FAMILY_NETWORK("family_network", R.string.main_tab_family_network, TabExitRestorePolicy.TRANSIENT),
    CONFIG("config", R.string.main_tab_config),
    GIT_PROJECTS("git_projects", R.string.main_tab_git_projects),
    BOOK_NOTE("book_note", R.string.main_tab_book_note),
    QUICK_NOTE("quick_note", R.string.main_tab_quick_note),
    QUICK_CRYPTO("quick_crypto", R.string.main_tab_quick_crypto),
    DICTIONARY("dictionary", R.string.main_tab_dictionary),
    ABOUT("about", R.string.main_menu_about);

    companion object {
        fun fromKey(key: String?): MainTab {
            return values().firstOrNull { it.key == key } ?: DIRECTORY
        }

        fun fromLaunchTarget(target: String?): MainTab? {
            if (target.isNullOrBlank()) return null
            return values().firstOrNull { it.key == target }
        }
    }
}
