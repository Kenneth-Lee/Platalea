package com.kenny.localmanager.player

import com.kenny.localmanager.R
import com.kenny.localmanager.browser.BrowserActionContext
import com.kenny.localmanager.browser.BrowserActionSnapshot
import com.kenny.localmanager.browser.BrowserActions
import com.kenny.localmanager.browser.BrowserFileAction
import com.kenny.localmanager.file.DocumentFileModel

/**
 * 播放器域的浏览器动作声明（拆解设计 第三批）：目录长按的"生成
 * 音乐播放列表"。播放器 tab 状态是页面组装对象，声明类在域内、
 * 依赖在组装层注入（register 入参）。
 */
internal object PlayerIntents {

    private class MusicPlaylistAction(
        private val playerTab: PlayerTabFeature
    ) : BrowserFileAction {
        override val id = "player.music.playlist"
        override fun labelRes(target: DocumentFileModel, snapshot: BrowserActionSnapshot) =
            R.string.browser_generate_music_playlist
        override fun applies(target: DocumentFileModel, snapshot: BrowserActionSnapshot) =
            target.isDirectory
        override fun run(ctx: BrowserActionContext) {
            playerTab.requestCreateDirectoryPlaylist(ctx.target.uri.toString(), ctx.target.name)
        }
    }

    fun registerAll(registry: BrowserActions.Registry, playerTab: PlayerTabFeature) {
        registry.register(MusicPlaylistAction(playerTab))
    }
}
