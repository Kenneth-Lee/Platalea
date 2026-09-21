package com.kenny.localmanager.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.kenny.localmanager.family.FamilyNetworkManager
import com.kenny.localmanager.ftp.FtpServerManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** FTP tab 功能对象（呈现接口；服务启停/配置同步生命周期在域工厂内聚）。 */
internal interface FtpTabFeature : MainTabFeature

/** 本地分享（WebUI）tab 功能对象。 */
internal interface LocalShareTabFeature : MainTabFeature

/** 家庭网络 tab 功能对象：呈现 + 宿主返回键协调所需的公告板投影。 */
internal interface FamilyNetworkTabFeature : MainTabFeature {
    val isBulletinBoardOpen: Boolean
    fun closeBulletinBoard()
}

/** 三个网络服务 tab 的打包返回。 */
internal class NetworkServicesTabs(
    val config: NetworkServiceConfigState,
    /** 家庭网络服务对象：公告转发流（browser 域出口）跨域消费的共享服务。 */
    val familyNetworkManager: FamilyNetworkManager,
    /** 家庭网络 tab 是否处于前台（域自持判定：非前台时公告转发保留临时会话）。 */
    val familyNetworkForeground: Boolean,
    val ftp: FtpTabFeature,
    val webUi: LocalShareTabFeature,
    val family: FamilyNetworkTabFeature
)

/**
 * 网络服务域工厂：三服务 manager、共享配置与全部生命周期接线
 * （legacy 配置迁移、离开 tab 停服务、配置同步、家庭 tab 激活态、销毁清理）。
 * browser 域输入（rootUri/displayUri/hideDotFiles）为组合期快照，由宿主提供。
 */
@Composable
internal fun networkServicesTabs(
    context: android.content.Context,
    config: NetworkConfigView,
    activeMainTab: MainTab,
    rootUri: String?,
    displayUri: String,
    familyRootUri: String?,
    hideDotFiles: Boolean,
    onRequestExitApp: () -> Unit
): NetworkServicesTabs {
    val ftpManager = remember { FtpServerManager(context) }
    val localShareManager = remember { FtpServerManager(context) }
    val familyNetworkManager = remember { FamilyNetworkManager(context) }
    val networkServiceConfig = rememberNetworkServiceConfigState(config)

    LaunchedEffect(config) {
        val legacyExtra = config.familyNetworkExtraRoles.first()
        if (!legacyExtra.isNullOrBlank()) {
            familyNetworkManager.migrateUserRolesFromLegacyConfig(legacyExtra)
            config.setFamilyNetworkExtraRoles(null)
        }
    }
    LaunchedEffect(activeMainTab) {
        withContext(Dispatchers.IO) {
            if (activeMainTab != MainTab.FTP) {
                ftpManager.stop()
            }
            if (activeMainTab != MainTab.LOCAL_SHARE) {
                localShareManager.stop()
            }
        }
    }
    // AppShell 编排：网络服务配置变化时同步到家庭网络服务
    LaunchedEffect(
        networkServiceConfig.ftpPassword,
        networkServiceConfig.localNetworkServiceEnabled,
        networkServiceConfig.familyNetworkUserName,
        networkServiceConfig.familyNetworkAvatarId,
        networkServiceConfig.familyNetworkHostName
    ) {
        familyNetworkManager.configure(
            networkServiceConfig.ftpPassword,
            networkServiceConfig.localNetworkServiceEnabled,
            networkServiceConfig.familyNetworkUserName.ifBlank { null },
            networkServiceConfig.familyNetworkAvatarId.ifBlank { null },
            networkServiceConfig.familyNetworkHostName.ifBlank { null }
        )
    }
    LaunchedEffect(activeMainTab) {
        familyNetworkManager.setFamilyTabActive(activeMainTab == MainTab.FAMILY_NETWORK)
    }
    DisposableEffect(familyNetworkManager) {
        onDispose {
            familyNetworkManager.destroy()
        }
    }

    return NetworkServicesTabs(
        config = networkServiceConfig,
        familyNetworkManager = familyNetworkManager,
        familyNetworkForeground = activeMainTab == MainTab.FAMILY_NETWORK,
        ftp = object : FtpTabFeature {
            override val tab: MainTab get() = MainTab.FTP
            override val content: @Composable () -> Unit = {
                FtpTabRoute(
                    FtpTabRouteState(
                        rootUri = rootUri,
                        displayUri = displayUri,
                        ftpManager = ftpManager,
                        ftpPort = networkServiceConfig.ftpPort,
                        ftpPassword = networkServiceConfig.ftpPassword,
                        networkServiceTimeoutMinutes = networkServiceConfig.networkServiceTimeoutMinutes,
                        onRequestExitApp = onRequestExitApp
                    )
                )
            }
        },
        webUi = object : LocalShareTabFeature {
            override val tab: MainTab get() = MainTab.LOCAL_SHARE
            override val content: @Composable () -> Unit = {
                WebUiTabRoute(
                    WebUiTabRouteState(
                        rootUri = rootUri,
                        displayUri = displayUri,
                        manager = localShareManager,
                        port = networkServiceConfig.ftpPort,
                        password = networkServiceConfig.ftpPassword,
                        networkServiceTimeoutMinutes = networkServiceConfig.networkServiceTimeoutMinutes,
                        onRequestExitApp = onRequestExitApp
                    )
                )
            }
        },
        family = object : FamilyNetworkTabFeature {
            override val tab: MainTab get() = MainTab.FAMILY_NETWORK
            override val content: @Composable () -> Unit = {
                FamilyNetworkScreen(
                    manager = familyNetworkManager,
                    networkPassword = networkServiceConfig.ftpPassword,
                    localServiceEnabled = networkServiceConfig.localNetworkServiceEnabled,
                    familyNetworkUserName = networkServiceConfig.familyNetworkUserName,
                    familyNetworkAvatarId = networkServiceConfig.familyNetworkAvatarId,
                    familyNetworkHostName = networkServiceConfig.familyNetworkHostName,
                    timeoutMinutes = networkServiceConfig.networkServiceTimeoutMinutes,
                    rootUri = familyRootUri,
                    hideDotFiles = hideDotFiles,
                    onDismiss = onRequestExitApp
                )
            }
            override val isBulletinBoardOpen: Boolean
                get() = familyNetworkManager.state.value.openBoardSession != null
            override fun closeBulletinBoard() {
                familyNetworkManager.closeBulletinBoard()
            }
        }
    )
}
