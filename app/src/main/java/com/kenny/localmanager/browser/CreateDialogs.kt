package com.kenny.localmanager.browser

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.documentfile.provider.DocumentFile
import com.kenny.localmanager.R
import kotlinx.coroutines.delay

/**
 * 新建文件/文件夹对话框（拆解设计 第四批：会话化迁出）。
 * 名字输入随会话生命周期，关闭即销毁（每次打开空白）。
 */
@Composable
internal fun CreateFileDialog(
    currentUri: String,
    onCreated: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(150)
        focus.requestFocus()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.create_file_title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.create_file_name)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus),
                placeholder = { Text(stringResource(R.string.create_file_placeholder)) }
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmed = name.trim()
                    if (trimmed.isNotEmpty()) {
                        val dir = currentDirDocument(context, currentUri)
                        when {
                            dir == null || !dir.isDirectory -> Toast.makeText(context, context.getString(R.string.create_access_failed), Toast.LENGTH_SHORT).show()
                            dir.createFile("application/octet-stream", trimmed) == null -> Toast.makeText(context, context.getString(R.string.create_failed), Toast.LENGTH_SHORT).show()
                            else -> onCreated()
                        }
                    }
                }
            ) { Text(stringResource(R.string.common_create)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    )
}

/** 新建文件夹对话框（同上）。 */
@Composable
internal fun CreateDirDialog(
    currentUri: String,
    onCreated: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(150)
        focus.requestFocus()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.create_folder_title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.create_folder_name)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus),
                placeholder = { Text(stringResource(R.string.create_folder_placeholder)) }
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmed = name.trim()
                    if (trimmed.isNotEmpty()) {
                        val dir = currentDirDocument(context, currentUri)
                        when {
                            dir == null || !dir.isDirectory -> Toast.makeText(context, context.getString(R.string.create_access_failed), Toast.LENGTH_SHORT).show()
                            dir.createDirectory(trimmed) == null -> Toast.makeText(context, context.getString(R.string.create_failed), Toast.LENGTH_SHORT).show()
                            else -> onCreated()
                        }
                    }
                }
            ) { Text(stringResource(R.string.common_create)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel), color = MaterialTheme.colorScheme.onSurface) }
        }
    )
}

private fun currentDirDocument(context: android.content.Context, currentUri: String): DocumentFile? {
    val uri = Uri.parse(currentUri)
    return if (currentUri.contains("/tree/")) {
        DocumentFile.fromTreeUri(context, uri)
    } else {
        DocumentFile.fromSingleUri(context, uri)
    }
}
