package com.kenny.localmanager.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.kenny.localmanager.data.AudioMetaAnnotation
import com.kenny.localmanager.data.DirectoryScrollMemory
import com.kenny.localmanager.data.PlayerAudioSettings
import com.kenny.localmanager.data.Playlist
import com.kenny.localmanager.data.PlayerListBookmark
import com.kenny.localmanager.data.Preferences
import kotlinx.coroutines.flow.Flow

/**
 * 配置功能（域服务）：持久配置的唯一创建入口与各功能域的配置投影。
 *
 * 分层约定（见《主界面AppShell分层设计》）：
 * - 配置数据属于配置功能（域），CONFIG tab 只是它的一扇呈现窗口；
 * - 各功能域消费自己的窄投影（[player] 等），不感知存储实现（Preferences/DataStore）；
 * - 投影成员资格判据：进入统一导出文件的配置由配置功能管理；
 *   不导出的功能内部状态（后退栈、草稿缓存等）留在各功能域内部。
 *
 * 迁移期（F4 批次）：尚未换投影的功能域经 [legacy] 取用原始 Preferences，
 * 逐域替换完成后删除该过渡面。
 */
internal class ConfigService(context: Context) {

    private val prefs = Preferences(context)

    /** 播放器功能域的配置投影：播放列表、最近列表 id、音频设置、列表书签。 */
    val player: PlayerConfigView = PlayerConfigViewImpl(prefs)

    /** 目录功能域的配置投影：根目录、过滤/隐藏偏好、外开包映射、目录滚动记忆、音频元数据注解。 */
    val browser: BrowserConfigView = BrowserConfigViewImpl(prefs)

    /** 速记功能域的配置投影：最近使用分类。 */
    val quickNote: QuickNoteConfigView = QuickNoteConfigViewImpl(prefs)

    /** 过渡面（F4 移除）：未迁移功能域的原始访问。 */
    val legacy: Preferences = prefs
}

@Composable
internal fun rememberConfigService(context: Context): ConfigService {
    return remember { ConfigService(context) }
}

/** 播放器功能域的配置投影接口：由配置功能提供实现，播放器域不感知存储。 */
internal interface PlayerConfigView {
    val playlists: Flow<List<Playlist>>
    val playerLastPlaylistId: Flow<String?>
    val playerAudioSettings: Flow<PlayerAudioSettings>
    val playerListBookmarks: Flow<List<PlayerListBookmark>>

    suspend fun addPlaylist(playlist: Playlist)
    suspend fun appendTracksToPlaylist(playlistId: String, uris: List<String>, names: List<String>): com.kenny.localmanager.data.PlaylistAppendResult
    suspend fun updatePlaylist(playlist: Playlist)
    suspend fun updatePlaylistOrder(orderedIds: List<String>)
    suspend fun removePlaylist(id: String)
    suspend fun updatePlayerAudioSettings(transform: (PlayerAudioSettings) -> PlayerAudioSettings)
    suspend fun addPlayerListBookmark(
        playlistId: String?,
        dirUri: String?,
        trackIndex: Int,
        positionMs: Long,
        trackName: String,
        note: String
    )
    suspend fun updatePlayerListBookmarkNote(bookmarkId: String, note: String)
    suspend fun deletePlayerListBookmark(bookmarkId: String)
}

private class PlayerConfigViewImpl(private val prefs: Preferences) : PlayerConfigView {
    override val playlists get() = prefs.playlists
    override val playerLastPlaylistId get() = prefs.playerLastPlaylistId
    override val playerAudioSettings get() = prefs.playerAudioSettings
    override val playerListBookmarks get() = prefs.playerListBookmarks
    override suspend fun addPlaylist(playlist: Playlist) = prefs.addPlaylist(playlist)
    override suspend fun appendTracksToPlaylist(playlistId: String, uris: List<String>, names: List<String>) =
        prefs.appendTracksToPlaylist(playlistId, uris, names)
    override suspend fun updatePlaylist(playlist: Playlist) = prefs.updatePlaylist(playlist)
    override suspend fun updatePlaylistOrder(orderedIds: List<String>) = prefs.updatePlaylistOrder(orderedIds)
    override suspend fun removePlaylist(id: String) = prefs.removePlaylist(id)
    override suspend fun updatePlayerAudioSettings(transform: (PlayerAudioSettings) -> PlayerAudioSettings) =
        prefs.updatePlayerAudioSettings(transform)
    override suspend fun addPlayerListBookmark(
        playlistId: String?, dirUri: String?, trackIndex: Int, positionMs: Long, trackName: String, note: String
    ) = prefs.addPlayerListBookmark(playlistId, dirUri, trackIndex, positionMs, trackName, note)

    override suspend fun updatePlayerListBookmarkNote(bookmarkId: String, note: String) =
        prefs.updatePlayerListBookmarkNote(bookmarkId, note)
    override suspend fun deletePlayerListBookmark(bookmarkId: String) = prefs.deletePlayerListBookmark(bookmarkId)
}

/** 目录功能域的配置投影接口：由配置功能提供实现，目录域不感知存储。 */
internal interface BrowserConfigView {
    val rootUri: Flow<String?>
    val filterVisible: Flow<Boolean>
    val hideDotFiles: Flow<Boolean>
    val externalOpenByExtension: Flow<Map<String, String>>

    suspend fun setExternalOpenPackageForExtension(extension: String, packageName: String?)
    suspend fun clearExternalOpenPackageForExtension(extension: String)
    suspend fun getDirectoryScrollMemory(uri: String): DirectoryScrollMemory?
    suspend fun setDirectoryScrollMemory(memory: DirectoryScrollMemory)
    suspend fun removeDirectoryScrollMemory(uri: String)
    suspend fun getAudioMetaAnnotation(uri: String): AudioMetaAnnotation?
    suspend fun setAudioMetaAnnotation(uri: String, annotation: AudioMetaAnnotation?)
}

private class BrowserConfigViewImpl(private val prefs: Preferences) : BrowserConfigView {
    override val rootUri get() = prefs.rootUri
    override val filterVisible get() = prefs.filterVisible
    override val hideDotFiles get() = prefs.hideDotFiles
    override val externalOpenByExtension get() = prefs.externalOpenByExtension
    override suspend fun setExternalOpenPackageForExtension(extension: String, packageName: String?) =
        prefs.setExternalOpenPackageForExtension(extension, packageName)
    override suspend fun clearExternalOpenPackageForExtension(extension: String) =
        prefs.clearExternalOpenPackageForExtension(extension)
    override suspend fun getDirectoryScrollMemory(uri: String): DirectoryScrollMemory? = prefs.getDirectoryScrollMemory(uri)
    override suspend fun setDirectoryScrollMemory(memory: DirectoryScrollMemory) = prefs.setDirectoryScrollMemory(memory)
    override suspend fun removeDirectoryScrollMemory(uri: String) = prefs.removeDirectoryScrollMemory(uri)
    override suspend fun getAudioMetaAnnotation(uri: String): AudioMetaAnnotation? = prefs.getAudioMetaAnnotation(uri)
    override suspend fun setAudioMetaAnnotation(uri: String, annotation: AudioMetaAnnotation?) =
        prefs.setAudioMetaAnnotation(uri, annotation)
}


/** 速记功能域的配置投影接口：由配置功能提供实现，速记域不感知存储。 */
internal interface QuickNoteConfigView {
    val quickNoteLastCategory: Flow<String?>

    suspend fun setQuickNoteLastCategory(category: String?)
}

private class QuickNoteConfigViewImpl(private val prefs: Preferences) : QuickNoteConfigView {
    override val quickNoteLastCategory get() = prefs.quickNoteLastCategory
    override suspend fun setQuickNoteLastCategory(category: String?) = prefs.setQuickNoteLastCategory(category)
}
