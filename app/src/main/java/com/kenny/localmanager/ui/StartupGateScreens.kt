package com.kenny.localmanager.ui

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kenny.localmanager.R
import com.kenny.localmanager.gpg.GpgHelper
import com.kenny.localmanager.gpg.SecretKeyPasswordCache
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 启动门页面（P5 从主界面顶层 when 分支抽出的整页显示）。
 *
 * 为什么是页面而非对话框：门在导航分派之前（见 PlataeaApp 顶层 when），
 * 是"不满足前提时占据整屏"的独立显示原子——解锁前提（私钥密码）与
 * 根目录前提（SAF 授权）各自有完整的页面级 UI 与返回语义。
 */

/** 解锁门：启动解密开启且存在私钥时占据整屏，解锁成功才放行主界面。 */
@Composable
internal fun StartupUnlockGateScreen(
    onUnlocked: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    BackHandler { (context as? Activity)?.finish() }
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        var unlockPwd by remember { mutableStateOf("") }
        var unlockInProgress by remember { mutableStateOf(false) }
        var unlockError by remember { mutableStateOf<String?>(null) }
        Column(
            modifier = Modifier.widthIn(max = 320.dp).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(Icons.Default.Lock, contentDescription = null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
            Text(
                stringResource(R.string.browser_startup_decrypt_enabled),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                stringResource(R.string.browser_enter_private_key_unlock),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ReliablePasswordInputField(
                value = unlockPwd,
                onValueChange = { unlockPwd = it; unlockError = null },
                label = { Text(stringResource(R.string.browser_private_key_password_label)) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !unlockInProgress
            )
            unlockError?.let { err ->
                Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Button(
                onClick = {
                    if (unlockPwd.isEmpty() || unlockInProgress) return@Button
                    unlockInProgress = true
                    unlockError = null
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) {
                            GpgHelper.tryUnlockSecretKey(context, unlockPwd.toCharArray())
                        }
                        unlockInProgress = false
                        if (ok) {
                            SecretKeyPasswordCache.set(unlockPwd.toCharArray())
                            onUnlocked()
                        } else {
                            unlockError = context.getString(R.string.browser_password_wrong_or_decrypt_failed)
                        }
                    }
                },
                enabled = unlockPwd.isNotEmpty() && !unlockInProgress,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (unlockInProgress) {
                    CircularProgressIndicator(Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text(stringResource(R.string.browser_unlock_action))
                }
            }
        }
    }
}

/** 无根门：根目录未恢复且无外源保存流程时占据整屏，引导选择根目录。 */
@Composable
internal fun NoRootGateScreen(
    externalSavePending: Boolean,
    treeLauncher: ManagedActivityResultLauncher<android.net.Uri?, android.net.Uri?>
) {
    val context = LocalContext.current
    var lastBackPressTime by remember { mutableStateOf(0L) }
    BackHandler {
        val now = System.currentTimeMillis()
        if (now - lastBackPressTime < 2000) (context as? Activity)?.finish()
        else {
            lastBackPressTime = now
            Toast.makeText(context, context.getString(R.string.browser_press_again_to_exit), Toast.LENGTH_SHORT).show()
        }
    }
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (externalSavePending) stringResource(R.string.browser_external_open_save_prompt) else stringResource(R.string.browser_select_root_prompt),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = { treeLauncher.launch(null) }) {
                Icon(Icons.Default.FolderOpen, contentDescription = null, Modifier.size(20.dp))
                Spacer(Modifier.size(8.dp))
                Text(if (externalSavePending) stringResource(R.string.browser_select_save_location) else stringResource(R.string.browser_select_root_button))
            }
        }
    }
}
