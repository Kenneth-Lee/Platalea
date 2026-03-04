package com.kenny.localmanager.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun GitConfigDialog(
    onDismiss: () -> Unit,
    repoUrl: String,
    onRepoUrlChange: (String) -> Unit,
    userName: String,
    onUserNameChange: (String) -> Unit,
    userEmail: String,
    onUserEmailChange: (String) -> Unit,
    httpsPassword: String,
    onHttpsPasswordChange: (String) -> Unit
) {
    var localRepoUrl by remember { mutableStateOf(repoUrl) }
    var localUserName by remember { mutableStateOf(userName) }
    var localUserEmail by remember { mutableStateOf(userEmail) }
    var localHttpsPassword by remember { mutableStateOf(httpsPassword) }
    LaunchedEffect(repoUrl, userName, userEmail, httpsPassword) {
        localRepoUrl = repoUrl
        localUserName = userName
        localUserEmail = userEmail
        localHttpsPassword = httpsPassword
    }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text("Git 配置", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                Text("克隆/同步到根目录 .sysgit，仅支持 HTTPS。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("仓库地址", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.width(100.dp))
                    OutlinedTextField(
                        value = localRepoUrl,
                        onValueChange = { s -> localRepoUrl = s; onRepoUrlChange(s) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text("https://gitcode.com/用户/仓库.git") }
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("HTTPS 密码", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.width(100.dp))
                    OutlinedTextField(
                        value = localHttpsPassword,
                        onValueChange = { s -> localHttpsPassword = s; onHttpsPasswordChange(s) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text("私有库需填令牌/密码") }
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Git 用户名", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.width(100.dp))
                    OutlinedTextField(
                        value = localUserName,
                        onValueChange = { s -> localUserName = s; onUserNameChange(s) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text("本地 commit 显示名") }
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Git 邮箱", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.width(100.dp))
                    OutlinedTextField(
                        value = localUserEmail,
                        onValueChange = { s -> localUserEmail = s; onUserEmailChange(s) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text("本地 commit 邮箱") }
                    )
                }
                Spacer(Modifier.height(24.dp))
                TextButton(onClick = onDismiss) { Text("关闭") }
            }
        }
    }
}
