package com.kenny.localmanager.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.kenny.localmanager.data.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 网络服务域共享配置状态（FTP / WebUI / 家庭网络三个 Tab 共用）。
 *
 * 见 docs/主界面Tab与Route分层改造方案.md §5.3：网络服务参数的快照与写入
 * 属于网络服务域，不再散落在 AppShell 顶层。AppShell 只保留对
 * FamilyNetworkManager.configure(...) 的编排副作用。
 *
 * 状态来源：Preferences 中的各 Flow（读取）；
 * 写入：同步更新本地快照并异步落盘，ConfigTab 是唯一的编辑入口。
 */
@Stable
class NetworkServiceConfigState internal constructor(
    private val prefs: Preferences,
    private val scope: CoroutineScope
) {
    var ftpPort by mutableIntStateOf(2121)
        internal set
    var ftpPassword by mutableStateOf<String?>(null)
        internal set
    var networkServiceTimeoutMinutes by mutableIntStateOf(0)
        internal set
    var localNetworkServiceEnabled by mutableStateOf(true)
        internal set
    var familyNetworkUserName by mutableStateOf("")
        internal set
    var familyNetworkAvatarId by mutableStateOf("")
        internal set
    var familyNetworkHostName by mutableStateOf("")
        internal set

    fun setFtpPassword(password: String?) {
        ftpPassword = password
        scope.launch { prefs.setFtpPassword(password) }
    }

    fun setNetworkServiceTimeoutMinutes(minutes: Int) {
        networkServiceTimeoutMinutes = minutes
        scope.launch { prefs.setNetworkServiceTimeoutMinutes(minutes) }
    }

    fun setLocalNetworkServiceEnabled(enabled: Boolean) {
        localNetworkServiceEnabled = enabled
        scope.launch { prefs.setLocalNetworkServiceEnabled(enabled) }
    }

    fun setFamilyNetworkUserName(name: String) {
        familyNetworkUserName = name
        scope.launch { prefs.setFamilyNetworkUserName(name.ifBlank { null }) }
    }

    fun setFamilyNetworkAvatarId(avatarId: String) {
        familyNetworkAvatarId = avatarId
        scope.launch { prefs.setFamilyNetworkAvatarId(avatarId.ifBlank { null }) }
    }

    fun setFamilyNetworkHostName(name: String) {
        familyNetworkHostName = name
        scope.launch { prefs.setFamilyNetworkHostName(name.ifBlank { null }) }
    }
}

/** 创建网络服务配置状态并订阅 Preferences 变化。 */
@Composable
fun rememberNetworkServiceConfigState(prefs: Preferences): NetworkServiceConfigState {
    val scope = rememberCoroutineScope()
    val state = remember(prefs) { NetworkServiceConfigState(prefs, scope) }
    LaunchedEffect(state) { prefs.ftpPort.collect { state.ftpPort = it } }
    LaunchedEffect(state) { prefs.ftpPassword.collect { state.ftpPassword = it } }
    LaunchedEffect(state) { prefs.networkServiceTimeoutMinutes.collect { state.networkServiceTimeoutMinutes = it } }
    LaunchedEffect(state) { prefs.localNetworkServiceEnabled.collect { state.localNetworkServiceEnabled = it } }
    LaunchedEffect(state) { prefs.familyNetworkUserName.collect { state.familyNetworkUserName = it.orEmpty() } }
    LaunchedEffect(state) { prefs.familyNetworkAvatarId.collect { state.familyNetworkAvatarId = it.orEmpty() } }
    LaunchedEffect(state) { prefs.familyNetworkHostName.collect { state.familyNetworkHostName = it.orEmpty() } }
    return state
}
