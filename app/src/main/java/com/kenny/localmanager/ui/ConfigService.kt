package com.kenny.localmanager.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import android.content.Context as AndroidContext
import com.kenny.localmanager.data.AudioMetaAnnotation
import com.kenny.localmanager.data.ConfigExportCategory
import com.kenny.localmanager.data.ManagedGitProject
import com.kenny.localmanager.data.ConfigPlaylistImportMode
import com.kenny.localmanager.data.exportConfig
import com.kenny.localmanager.data.importConfig
import com.kenny.localmanager.data.DirectoryScrollMemory
import com.kenny.localmanager.data.PlayerAudioSettings
import com.kenny.localmanager.data.Playlist
import com.kenny.localmanager.data.PlayerListBookmark
import com.kenny.localmanager.data.Preferences
import com.kenny.localmanager.data.RecentOpenItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * 配置功能（域服务）：持久配置的唯一创建入口与各功能域的配置投影。
 *
 * 分层约定（见《主界面AppShell分层设计》）：
 * - 配置数据属于配置功能（域），CONFIG tab 只是它的一扇呈现窗口；
 * - 各功能域消费自己的窄投影（[player] 等），不感知存储实现（Preferences/DataStore）；
 * - 投影成员资格判据：进入统一导出文件的配置由配置功能管理；
 *   不导出的功能内部状态（后退栈、草稿缓存等）留在各功能域内部。
 */
internal class ConfigService(context: Context) {

    private val prefs = Preferences(context)

    /** 播放器功能域的配置投影：播放列表、最近列表 id、音频设置、列表书签。 */
    val player: PlayerConfigView = PlayerConfigViewImpl(prefs)

    /** 目录功能域的配置投影：根目录、过滤/隐藏偏好、外开包映射、目录滚动记忆、音频元数据注解。 */
    val browser: BrowserConfigView = BrowserConfigViewImpl(prefs)

    /** 速记功能域的配置投影：最近使用分类。 */
    val quickNote: QuickNoteConfigView = QuickNoteConfigViewImpl(prefs)

    /** 网络服务功能域（FTP/WebUI/家庭网络）的配置投影：端口、口令、超时、开关与家庭网络身份。 */
    val network: NetworkConfigView = NetworkConfigViewImpl(prefs)

    /** 配置功能自身的编辑投影：CONFIG tab 的设置项编辑面 + 统一导入导出入口（成员资格事实源）。 */
    val editing: ConfigEditingView = ConfigEditingViewImpl(context, prefs)

    /** Git 功能域的配置投影：仓库地址、身份、凭据与已配置标记。 */
    val git: GitConfigView = GitConfigViewImpl(prefs)

    /** 词典功能域的配置投影：查询历史。 */
    val dictionary: DictionaryConfigView = DictionaryConfigViewImpl(prefs)

    /** 阅读器功能域的配置投影：阅读器偏好、EPUB 朗读与词典、进度缩放（CONFIG tab 经此编辑 EPUB 朗读）。 */
    val reader: ReaderConfigView = ReaderConfigViewImpl(prefs)

    /** 主界面 AppShell 域的配置投影：Tab 记忆与最近打开记录（RECENT tab 数据源）。 */
    val shell: ShellConfigView = ShellConfigViewImpl(prefs)

    val operationTarget: OperationTargetConfigView = OperationTargetConfigViewImpl(prefs)
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
    val recentRootUris: Flow<List<String>>

    suspend fun setExternalOpenPackageForExtension(extension: String, packageName: String?)
    suspend fun clearExternalOpenPackageForExtension(extension: String)
    suspend fun setRootUri(uri: String?)
    suspend fun recordRecentRootSwitch(fromUri: String?, toUri: String?)
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
    override val recentRootUris get() = prefs.recentRootUris
    override suspend fun setExternalOpenPackageForExtension(extension: String, packageName: String?) =
        prefs.setExternalOpenPackageForExtension(extension, packageName)
    override suspend fun clearExternalOpenPackageForExtension(extension: String) =
        prefs.clearExternalOpenPackageForExtension(extension)
    override suspend fun setRootUri(uri: String?) = prefs.setRootUri(uri)
    override suspend fun recordRecentRootSwitch(fromUri: String?, toUri: String?) = prefs.recordRecentRootSwitch(fromUri, toUri)
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


/** 网络服务功能域的配置投影接口：由配置功能提供实现，网络域不感知存储。 */
internal interface NetworkConfigView {
    val ftpPort: Flow<Int>
    val ftpPassword: Flow<String?>
    val networkServiceTimeoutMinutes: Flow<Int>
    val localNetworkServiceEnabled: Flow<Boolean>
    val familyNetworkUserName: Flow<String?>
    val familyNetworkAvatarId: Flow<String?>
    val familyNetworkHostName: Flow<String?>
    val familyNetworkExtraRoles: Flow<String?>

    suspend fun setFtpPassword(password: String?)
    suspend fun setNetworkServiceTimeoutMinutes(minutes: Int)
    suspend fun setLocalNetworkServiceEnabled(enabled: Boolean)
    suspend fun setFamilyNetworkUserName(name: String?)
    suspend fun setFamilyNetworkAvatarId(avatarId: String?)
    suspend fun setFamilyNetworkHostName(name: String?)
    suspend fun setFamilyNetworkExtraRoles(config: String?)
}

private class NetworkConfigViewImpl(private val prefs: Preferences) : NetworkConfigView {
    override val ftpPort get() = prefs.ftpPort
    override val ftpPassword get() = prefs.ftpPassword
    override val networkServiceTimeoutMinutes get() = prefs.networkServiceTimeoutMinutes
    override val localNetworkServiceEnabled get() = prefs.localNetworkServiceEnabled
    override val familyNetworkUserName get() = prefs.familyNetworkUserName
    override val familyNetworkAvatarId get() = prefs.familyNetworkAvatarId
    override val familyNetworkHostName get() = prefs.familyNetworkHostName
    override val familyNetworkExtraRoles get() = prefs.familyNetworkExtraRoles
    override suspend fun setFtpPassword(password: String?) = prefs.setFtpPassword(password)
    override suspend fun setNetworkServiceTimeoutMinutes(minutes: Int) = prefs.setNetworkServiceTimeoutMinutes(minutes)
    override suspend fun setLocalNetworkServiceEnabled(enabled: Boolean) = prefs.setLocalNetworkServiceEnabled(enabled)
    override suspend fun setFamilyNetworkUserName(name: String?) = prefs.setFamilyNetworkUserName(name)
    override suspend fun setFamilyNetworkAvatarId(avatarId: String?) = prefs.setFamilyNetworkAvatarId(avatarId)
    override suspend fun setFamilyNetworkHostName(name: String?) = prefs.setFamilyNetworkHostName(name)
    override suspend fun setFamilyNetworkExtraRoles(config: String?) = prefs.setFamilyNetworkExtraRoles(config)
}


/**
 * 配置功能自身的编辑投影：CONFIG tab 是配置数据的一扇编辑窗口（各域消费自己的窄投影）。
 * 统一导出/导入在此收口——导出文件成员资格即配置域成员资格（见 ConfigExport.kt）。
 */
internal interface ConfigEditingView {
    // 通用设置（browser/阅读器域消费，CONFIG tab 编辑）
    val filterVisible: Flow<Boolean>
    val hideDotFiles: Flow<Boolean>
    val startupDecryptKey: Flow<Boolean>
    val viewerPreviewBytes: Flow<Int>
    suspend fun setFilterVisible(visible: Boolean)
    suspend fun setHideDotFiles(enabled: Boolean)
    suspend fun setStartupDecryptKey(enabled: Boolean)
    suspend fun setViewerPreviewBytes(bytes: Int)

    // 统一导入导出（配置域服务核心）
    suspend fun exportConfigJson(categories: Set<ConfigExportCategory>): String
    suspend fun importConfigJson(
        jsonString: String,
        importKeys: Boolean,
        playlistImportMode: ConfigPlaylistImportMode,
        categories: Set<ConfigExportCategory>
    ): Boolean
    suspend fun hasExistingPlaylists(): Boolean
}

private class ConfigEditingViewImpl(
    private val context: AndroidContext,
    private val prefs: Preferences
) : ConfigEditingView {
    override val filterVisible get() = prefs.filterVisible
    override val hideDotFiles get() = prefs.hideDotFiles
    override val startupDecryptKey get() = prefs.startupDecryptKey
    override val viewerPreviewBytes get() = prefs.viewerPreviewBytes
    override suspend fun setFilterVisible(visible: Boolean) = prefs.setFilterVisible(visible)
    override suspend fun setHideDotFiles(enabled: Boolean) = prefs.setHideDotFiles(enabled)
    override suspend fun setStartupDecryptKey(enabled: Boolean) = prefs.setStartupDecryptKey(enabled)
    override suspend fun setViewerPreviewBytes(bytes: Int) = prefs.setViewerPreviewBytes(bytes)

    override suspend fun exportConfigJson(categories: Set<ConfigExportCategory>): String =
        exportConfig(context, prefs, categories)
    override suspend fun importConfigJson(
        jsonString: String,
        importKeys: Boolean,
        playlistImportMode: ConfigPlaylistImportMode,
        categories: Set<ConfigExportCategory>
    ): Boolean = importConfig(context, prefs, jsonString, importKeys, playlistImportMode, categories)
    override suspend fun hasExistingPlaylists(): Boolean = prefs.playlists.first().isNotEmpty()
}

/** Git 功能域的配置投影接口：由配置功能提供实现，git 域不感知存储。 */
internal interface GitConfigView {
    val gitRepoUrl: Flow<String?>
    val gitUserName: Flow<String?>
    val gitUserEmail: Flow<String?>
    val gitHttpsPassword: Flow<String?>
    val gitConfigApplied: Flow<Boolean>
    val managedGitProjects: Flow<List<ManagedGitProject>>

    suspend fun setGitRepoUrl(url: String?)
    suspend fun setGitUserName(name: String?)
    suspend fun setGitUserEmail(email: String?)
    suspend fun setGitHttpsPassword(password: String?)
    suspend fun setGitConfigApplied(applied: Boolean)
    suspend fun addManagedGitProject(project: ManagedGitProject)
    suspend fun updateManagedGitProject(project: ManagedGitProject)
    suspend fun removeManagedGitProject(id: String)
}

private class GitConfigViewImpl(private val prefs: Preferences) : GitConfigView {
    override val gitRepoUrl get() = prefs.gitRepoUrl
    override val gitUserName get() = prefs.gitUserName
    override val gitUserEmail get() = prefs.gitUserEmail
    override val gitHttpsPassword get() = prefs.gitHttpsPassword
    override val gitConfigApplied get() = prefs.gitConfigApplied
    override val managedGitProjects get() = prefs.managedGitProjects
    override suspend fun setGitRepoUrl(url: String?) = prefs.setGitRepoUrl(url)
    override suspend fun setGitUserName(name: String?) = prefs.setGitUserName(name)
    override suspend fun setGitUserEmail(email: String?) = prefs.setGitUserEmail(email)
    override suspend fun setGitHttpsPassword(password: String?) = prefs.setGitHttpsPassword(password)
    override suspend fun setGitConfigApplied(applied: Boolean) = prefs.setGitConfigApplied(applied)
    override suspend fun addManagedGitProject(project: ManagedGitProject) = prefs.addManagedGitProject(project)
    override suspend fun updateManagedGitProject(project: ManagedGitProject) = prefs.updateManagedGitProject(project)
    override suspend fun removeManagedGitProject(id: String) = prefs.removeManagedGitProject(id)
}


/** 词典功能域的配置投影接口：查询历史记录。 */
internal interface DictionaryConfigView {
    val dictQueryHistory: Flow<List<String>>
    suspend fun recordDictQuery(query: String)
    suspend fun clearDictQueryHistory()
}

private class DictionaryConfigViewImpl(private val prefs: Preferences) : DictionaryConfigView {
    override val dictQueryHistory get() = prefs.dictQueryHistory
    override suspend fun recordDictQuery(query: String) = prefs.recordDictQuery(query)
    override suspend fun clearDictQueryHistory() = prefs.clearDictQueryHistory()
}


/** 阅读器功能域的配置投影接口：Markdown/打包阅读器/EPUB/PDF 全部读经此，不感知存储。 */
internal interface ReaderConfigView {
    // 阅读器通用偏好
    val readerBackgroundColor: Flow<String>
    val readerAutoScrollCountdownSeconds: Flow<Int>
    suspend fun setReaderBackgroundColor(colorKey: String?)
    suspend fun setReaderAutoScrollCountdownSeconds(seconds: Int)

    // 浮动下一页按钮（EPUB/PDF）
    val hideReaderFloatingNextButton: Flow<Boolean>
    val readerFloatingNextButtonXPercent: Flow<Int>
    val readerFloatingNextButtonYPercent: Flow<Int>
    suspend fun setHideReaderFloatingNextButton(enabled: Boolean)
    suspend fun setReaderFloatingNextButtonPositionPercent(xPercent: Int, yPercent: Int)

    // EPUB 离线朗读（CONFIG tab 编辑入口也经本投影）
    val epubTtsEnginePackage: Flow<String?>
    val epubTtsVoiceName: Flow<String?>
    val epubTtsSpeedPercent: Flow<Int>
    val epubTtsAutoNextChapter: Flow<Boolean>
    suspend fun setEpubTtsEnginePackage(packageName: String?)
    suspend fun setEpubTtsVoiceName(voiceName: String?)
    suspend fun setEpubTtsSpeedPercent(speedPercent: Int)
    suspend fun setEpubTtsAutoNextChapter(enabled: Boolean)

    // EPUB 内嵌词典
    val epubDictAreaExpanded: Flow<Boolean>
    val epubDictLookupWords: Flow<List<String>>
    suspend fun setEpubDictAreaExpanded(expanded: Boolean)
    suspend fun setEpubDictLookupWords(words: List<String>)

    // 阅读进度与缩放（URI 键控）
    suspend fun getEpubZoomPercentForUri(uri: String): Int?
    suspend fun setEpubZoomPercentForUri(uri: String, percent: Int)
    suspend fun getPdfLastPageForUri(uri: String): Int?
    suspend fun setPdfLastPageForUri(uri: String, pageIndex: Int)
    suspend fun getPdfZoomPercentForUri(uri: String): Int?
    suspend fun setPdfZoomPercentForUri(uri: String, percent: Int)
}

private class ReaderConfigViewImpl(private val prefs: Preferences) : ReaderConfigView {
    override val readerBackgroundColor get() = prefs.readerBackgroundColor
    override val readerAutoScrollCountdownSeconds get() = prefs.readerAutoScrollCountdownSeconds
    override suspend fun setReaderBackgroundColor(colorKey: String?) = prefs.setReaderBackgroundColor(colorKey)
    override suspend fun setReaderAutoScrollCountdownSeconds(seconds: Int) = prefs.setReaderAutoScrollCountdownSeconds(seconds)

    override val hideReaderFloatingNextButton get() = prefs.hideReaderFloatingNextButton
    override val readerFloatingNextButtonXPercent get() = prefs.readerFloatingNextButtonXPercent
    override val readerFloatingNextButtonYPercent get() = prefs.readerFloatingNextButtonYPercent
    override suspend fun setHideReaderFloatingNextButton(enabled: Boolean) = prefs.setHideReaderFloatingNextButton(enabled)
    override suspend fun setReaderFloatingNextButtonPositionPercent(xPercent: Int, yPercent: Int) = prefs.setReaderFloatingNextButtonPositionPercent(xPercent, yPercent)

    override val epubTtsEnginePackage get() = prefs.epubTtsEnginePackage
    override val epubTtsVoiceName get() = prefs.epubTtsVoiceName
    override val epubTtsSpeedPercent get() = prefs.epubTtsSpeedPercent
    override val epubTtsAutoNextChapter get() = prefs.epubTtsAutoNextChapter
    override suspend fun setEpubTtsEnginePackage(packageName: String?) = prefs.setEpubTtsEnginePackage(packageName)
    override suspend fun setEpubTtsVoiceName(voiceName: String?) = prefs.setEpubTtsVoiceName(voiceName)
    override suspend fun setEpubTtsSpeedPercent(speedPercent: Int) = prefs.setEpubTtsSpeedPercent(speedPercent)
    override suspend fun setEpubTtsAutoNextChapter(enabled: Boolean) = prefs.setEpubTtsAutoNextChapter(enabled)

    override val epubDictAreaExpanded get() = prefs.epubDictAreaExpanded
    override val epubDictLookupWords get() = prefs.epubDictLookupWords
    override suspend fun setEpubDictAreaExpanded(expanded: Boolean) = prefs.setEpubDictAreaExpanded(expanded)
    override suspend fun setEpubDictLookupWords(words: List<String>) = prefs.setEpubDictLookupWords(words)

    override suspend fun getEpubZoomPercentForUri(uri: String): Int? = prefs.getEpubZoomPercentForUri(uri)
    override suspend fun setEpubZoomPercentForUri(uri: String, percent: Int) = prefs.setEpubZoomPercentForUri(uri, percent)
    override suspend fun getPdfLastPageForUri(uri: String): Int? = prefs.getPdfLastPageForUri(uri)
    override suspend fun setPdfLastPageForUri(uri: String, pageIndex: Int) = prefs.setPdfLastPageForUri(uri, pageIndex)
    override suspend fun getPdfZoomPercentForUri(uri: String): Int? = prefs.getPdfZoomPercentForUri(uri)
    override suspend fun setPdfZoomPercentForUri(uri: String, percent: Int) = prefs.setPdfZoomPercentForUri(uri, percent)
}

/** 主界面 AppShell 域的配置投影接口：Tab 恢复记忆与最近打开记录，AppShell 不感知存储。 */
internal interface ShellConfigView {
    val lastMainTab: Flow<String>
    val lastNonServerMainTab: Flow<String>
    val recentOpenItems: Flow<List<RecentOpenItem>>

    suspend fun setLastMainTab(tab: String)
    suspend fun setLastNonServerMainTab(tab: String)
    suspend fun addRecentOpenItem(
        type: String,
        key: String,
        title: String,
        uri: String? = null,
        playlistId: String? = null
    )
    suspend fun removeRecentOpenItem(type: String, key: String)
    suspend fun clearRecentOpenItems()
}

private class ShellConfigViewImpl(private val prefs: Preferences) : ShellConfigView {
    override val lastMainTab get() = prefs.lastMainTab
    override val lastNonServerMainTab get() = prefs.lastNonServerMainTab
    override val recentOpenItems get() = prefs.recentOpenItems
    override suspend fun setLastMainTab(tab: String) = prefs.setLastMainTab(tab)
    override suspend fun setLastNonServerMainTab(tab: String) = prefs.setLastNonServerMainTab(tab)
    override suspend fun addRecentOpenItem(
        type: String, key: String, title: String, uri: String?, playlistId: String?
    ) = prefs.addRecentOpenItem(type = type, key = key, title = title, uri = uri, playlistId = playlistId)
    override suspend fun removeRecentOpenItem(type: String, key: String) = prefs.removeRecentOpenItem(type, key)
    override suspend fun clearRecentOpenItems() = prefs.clearRecentOpenItems()
}


/** 操作目标域的配置投影接口：目标文件的持久化指向（通用概念，zip 编辑是当前唯一使用者）。 */
internal interface OperationTargetConfigView {
    val operationTarget: Flow<String?>

    suspend fun setOperationTarget(uri: String?)
}

private class OperationTargetConfigViewImpl(private val prefs: Preferences) : OperationTargetConfigView {
    override val operationTarget get() = prefs.operationTargetUri
    override suspend fun setOperationTarget(uri: String?) = prefs.setOperationTargetUri(uri)
}
