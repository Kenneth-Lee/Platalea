package com.kenny.localmanager.browser

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
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
import com.kenny.localmanager.file.DocumentFileModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 详情面板的照片 EXIF 编辑对话框（拆解设计 第四批：从详情文件再拆出，
 * 状态随会话打开即加载，关闭即销毁）。
 */
@Composable
internal fun ImageExifEditDialog(
    target: DocumentFileModel,
    scope: CoroutineScope,
    onSaved: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var loading by remember(target.uri) { mutableStateOf(true) }
    var saving by remember(target.uri) { mutableStateOf(false) }
    var saveError by remember(target.uri) { mutableStateOf<String?>(null) }
    var form by remember(target.uri) { mutableStateOf(EditableImageExif()) }
    var validation by remember(target.uri) { mutableStateOf(EditableImageExifValidation()) }

    LaunchedEffect(target.uri) {
        val loaded = withContext(Dispatchers.IO) {
            readEditableImageExif(context, target.uri)
        }
        if (loaded == null) {
            Toast.makeText(context, context.getString(R.string.browser_meta_read_failed), Toast.LENGTH_SHORT).show()
            onDismiss()
        } else {
            form = loaded
        }
        loading = false
    }

    AlertDialog(
        onDismissRequest = {
            if (!saving) onDismiss()
        },
        title = { Text(stringResource(R.string.browser_type_specific_edit_image_meta_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                if (loading) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.browser_type_specific_loading))
                    }
                } else {
                    OutlinedTextField(
                        value = form.takenTime,
                        onValueChange = {
                            form = form.copy(takenTime = it)
                            validation = validation.copy(takenTimeError = null)
                        },
                        label = { Text(stringResource(R.string.browser_meta_taken_time_label)) },
                        isError = !validation.takenTimeError.isNullOrBlank(),
                        supportingText = {
                            Text(
                                validation.takenTimeError
                                    ?: stringResource(R.string.browser_meta_taken_time_hint)
                            )
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = form.artist,
                        onValueChange = {
                            form = form.copy(artist = it)
                            validation = validation.copy(artistError = null)
                        },
                        label = { Text(stringResource(R.string.browser_meta_artist_label)) },
                        isError = !validation.artistError.isNullOrBlank(),
                        supportingText = {
                            validation.artistError?.let { Text(it) }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = form.description,
                        onValueChange = {
                            form = form.copy(description = it)
                            validation = validation.copy(descriptionError = null)
                        },
                        label = { Text(stringResource(R.string.browser_meta_description_label)) },
                        isError = !validation.descriptionError.isNullOrBlank(),
                        supportingText = {
                            validation.descriptionError?.let { Text(it) }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = form.software,
                        onValueChange = {
                            form = form.copy(software = it)
                            validation = validation.copy(softwareError = null)
                        },
                        label = { Text(stringResource(R.string.browser_meta_software_label)) },
                        isError = !validation.softwareError.isNullOrBlank(),
                        supportingText = {
                            validation.softwareError?.let { Text(it) }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = form.copyright,
                        onValueChange = {
                            form = form.copy(copyright = it)
                            validation = validation.copy(copyrightError = null)
                        },
                        label = { Text(stringResource(R.string.browser_meta_copyright_label)) },
                        isError = !validation.copyrightError.isNullOrBlank(),
                        supportingText = {
                            validation.copyrightError?.let { Text(it) }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    saveError?.takeIf { it.isNotBlank() }?.let { errorText ->
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.browser_type_specific_save_error, errorText),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (loading || saving) return@TextButton
                    validation = validateEditableImageExifForm(context, form)
                    if (validation.hasError) {
                        saveError = context.getString(R.string.browser_meta_fix_form_errors)
                        return@TextButton
                    }
                    scope.launch {
                        saving = true
                        saveError = null
                        val result = withContext(Dispatchers.IO) {
                            saveEditableImageExif(context, target.uri, form)
                        }
                        saving = false
                        if (result == null) {
                            onDismiss()
                            Toast.makeText(context, context.getString(R.string.browser_type_specific_save_success), Toast.LENGTH_SHORT).show()
                            onSaved()
                        } else {
                            saveError = result
                        }
                    }
                },
                enabled = !loading && !saving
            ) {
                Text(if (saving) stringResource(R.string.browser_type_specific_saving) else stringResource(R.string.common_save))
            }
        },
        dismissButton = {
            TextButton(
                onClick = { if (!saving) onDismiss() },
                enabled = !saving
            ) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}
