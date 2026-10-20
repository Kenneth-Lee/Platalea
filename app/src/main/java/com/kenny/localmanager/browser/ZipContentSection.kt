package com.kenny.localmanager.browser

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kenny.localmanager.R
import com.kenny.localmanager.ui.ReliablePasswordInputField
import com.kenny.localmanager.file.DocumentFileModel
import com.kenny.localmanager.file.ZipFirstLevelResult
import com.kenny.localmanager.file.getArchiveFirstLevelEntries
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 详情面板的"压缩包内容"区（拆解设计 第四批：从详情文件再拆出）。
 * 一级条目展示与加密包密码重试。
 */
@Composable
internal fun ZipContentSection(
    target: DocumentFileModel,
    result: ZipFirstLevelResult?,
    onResult: (ZipFirstLevelResult?) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showPasswordInput by remember(target.uri) { mutableStateOf(false) }
    var password by remember(target.uri) { mutableStateOf("") }

    Spacer(Modifier.height(12.dp))
    Text(stringResource(R.string.browser_archive_contents), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(8.dp))
    when (val r = result) {
        is ZipFirstLevelResult.Ok -> {
            if (r.entries.isEmpty()) {
                Text(stringResource(R.string.browser_empty_placeholder), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Column(Modifier.fillMaxWidth().heightIn(max = 200.dp).verticalScroll(rememberScrollState())) {
                    r.entries.forEach { entry ->
                        Text(entry, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
        is ZipFirstLevelResult.OkSingleDir -> {
            Column(Modifier.fillMaxWidth().heightIn(max = 200.dp).verticalScroll(rememberScrollState())) {
                Text("${r.rootDirName}/", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                if (r.children.isEmpty()) {
                    Text(stringResource(R.string.browser_empty_placeholder_indent), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    r.children.forEach { child ->
                        Text("  $child", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
        ZipFirstLevelResult.Encrypted -> {
            if (!showPasswordInput) {
                OutlinedButton(onClick = { showPasswordInput = true }) {
                    Text(stringResource(R.string.browser_enter_password_view))
                }
            } else {
                Column(Modifier.fillMaxWidth()) {
                    ReliablePasswordInputField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(stringResource(R.string.browser_enter_password)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showPasswordInput = false; password = "" }) { Text(stringResource(R.string.common_cancel)) }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                scope.launch {
                                    val res = withContext(Dispatchers.IO) {
                                        getArchiveFirstLevelEntries(context, target.uri, target.name, password.toCharArray())
                                    }
                                    onResult(res)
                                    if (res is ZipFirstLevelResult.Error) {
                                        Toast.makeText(context, context.getString(R.string.browser_password_wrong_or_unreadable), Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            enabled = password.isNotEmpty()
                        ) { Text(stringResource(R.string.common_ok)) }
                    }
                }
            }
        }
        ZipFirstLevelResult.Error -> {
            Text(stringResource(R.string.browser_cannot_read_archive), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        null -> {
            CircularProgressIndicator(Modifier.size(24.dp))
        }
    }
}
