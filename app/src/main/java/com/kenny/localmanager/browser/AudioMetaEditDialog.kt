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
import com.kenny.localmanager.config.BrowserConfigView
import com.kenny.localmanager.data.AudioMetaAnnotation
import com.kenny.localmanager.file.DocumentFileModel
import com.kenny.localmanager.player.EditableAudioAnnotationValidation
import com.kenny.localmanager.player.isMp3AudioFile
import com.kenny.localmanager.player.readEditableAudioAnnotation
import com.kenny.localmanager.player.saveEditableAudioAnnotationToMp3
import com.kenny.localmanager.player.saveEditableAudioAnnotationWithJaudiotagger
import com.kenny.localmanager.player.supportsJaudiotaggerWrite
import com.kenny.localmanager.player.validateEditableAudioAnnotationForm
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 详情面板的音频标注编辑对话框（拆解设计 第四批：从详情文件再拆出，
 * 状态随会话打开即加载，关闭即销毁）。保存优先 mp3/可写格式回写，
 * 否则落应用内标注存储。
 */
@Composable
internal fun AudioMetaEditDialog(
    target: DocumentFileModel,
    mimeType: String,
    config: BrowserConfigView,
    scope: CoroutineScope,
    onSaved: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var loading by remember(target.uri) { mutableStateOf(true) }
    var saving by remember(target.uri) { mutableStateOf(false) }
    var saveError by remember(target.uri) { mutableStateOf<String?>(null) }
    var form by remember(target.uri) { mutableStateOf(EditableAudioAnnotation()) }
    var validation by remember(target.uri) { mutableStateOf(EditableAudioAnnotationValidation()) }

    LaunchedEffect(target.uri) {
        val annotation = withContext(Dispatchers.IO) {
            config.getAudioMetaAnnotation(target.uri.toString())
        }
        val fromFile = withContext(Dispatchers.IO) {
            readEditableAudioAnnotation(context, target.uri)
        }
        form = EditableAudioAnnotation(
            title = annotation?.title?.takeIf { it.isNotBlank() } ?: fromFile.title,
            artist = annotation?.artist?.takeIf { it.isNotBlank() } ?: fromFile.artist,
            album = annotation?.album?.takeIf { it.isNotBlank() } ?: fromFile.album
        )
        loading = false
    }

    AlertDialog(
        onDismissRequest = {
            if (!saving) onDismiss()
        },
        title = { Text(stringResource(R.string.browser_type_specific_edit_audio_meta_title)) },
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
                        value = form.title,
                        onValueChange = {
                            form = form.copy(title = it)
                            validation = validation.copy(titleError = null)
                        },
                        label = { Text(stringResource(R.string.browser_meta_title_label)) },
                        isError = !validation.titleError.isNullOrBlank(),
                        supportingText = {
                            validation.titleError?.let { Text(it) }
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
                        value = form.album,
                        onValueChange = {
                            form = form.copy(album = it)
                            validation = validation.copy(albumError = null)
                        },
                        label = { Text(stringResource(R.string.browser_meta_album_label)) },
                        isError = !validation.albumError.isNullOrBlank(),
                        supportingText = {
                            validation.albumError?.let { Text(it) }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    val errorText = saveError
                    if (!errorText.isNullOrBlank()) {
                        Text(errorText, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (loading || saving) return@TextButton
                    validation = validateEditableAudioAnnotationForm(context, form)
                    if (validation.hasError) {
                        saveError = context.getString(R.string.browser_meta_fix_form_errors)
                        return@TextButton
                    }
                    scope.launch {
                        saving = true
                        saveError = null
                        val result = withContext(Dispatchers.IO) {
                            if (isMp3AudioFile(target.name, mimeType)) {
                                val writeError = saveEditableAudioAnnotationToMp3(
                                    context,
                                    target.uri,
                                    form
                                )
                                if (writeError == null) {
                                    config.setAudioMetaAnnotation(target.uri.toString(), null)
                                }
                                writeError
                            } else if (supportsJaudiotaggerWrite(target.name, mimeType)) {
                                val writeError = saveEditableAudioAnnotationWithJaudiotagger(
                                    context,
                                    target.uri,
                                    form,
                                    target.name
                                )
                                if (writeError == null) {
                                    config.setAudioMetaAnnotation(target.uri.toString(), null)
                                }
                                writeError
                            } else {
                                runCatching {
                                    config.setAudioMetaAnnotation(
                                        target.uri.toString(),
                                        AudioMetaAnnotation(
                                            title = form.title,
                                            artist = form.artist,
                                            album = form.album
                                        )
                                    )
                                }.exceptionOrNull()?.let { e ->
                                    "${e.javaClass.simpleName}: ${e.message ?: "unknown error"}"
                                }
                            }
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
