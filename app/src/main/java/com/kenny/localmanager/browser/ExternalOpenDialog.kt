package com.kenny.localmanager.browser

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import android.content.pm.PackageManager
import android.net.Uri
import com.kenny.localmanager.R
import com.kenny.localmanager.file.buildExternalOpenIntent
import com.kenny.localmanager.file.guessExternalOpenMimeType
import com.kenny.localmanager.file.toExternalLaunchUri
import com.kenny.localmanager.file.DocumentFileModel
import java.util.Locale

internal data class ExternalAppTarget(
    val packageName: String,
    val label: String
)

private enum class ExternalOpenTypeHint(val mimeType: String, val labelRes: Int) {
    VIDEO("video/*", R.string.browser_external_type_video),
    AUDIO("audio/*", R.string.browser_external_type_audio),
    TEXT("text/plain", R.string.browser_external_type_text),
    OTHER("*/*", R.string.browser_external_type_other)
}

private fun isExternalMimeLowConfidence(mimeType: String): Boolean {
    val normalized = mimeType.trim().lowercase(Locale.ROOT)
    return normalized.isBlank() || normalized == "*/*" || normalized == "application/octet-stream"
}

internal fun queryExternalOpenTargets(
    context: Context,
    uriString: String,
    fileNameHint: String? = null,
    forcedMimeType: String? = null
): List<ExternalAppTarget> {
    val packageManager = context.packageManager
    return packageManager.queryIntentActivities(
        buildExternalOpenIntent(context, Uri.parse(uriString), fileNameHint = fileNameHint, forcedMimeType = forcedMimeType),
        PackageManager.MATCH_DEFAULT_ONLY
    ).asSequence()
        .mapNotNull { resolveInfo ->
            val packageName = resolveInfo.activityInfo?.packageName?.trim().orEmpty()
            if (packageName.isBlank() || packageName == context.packageName) return@mapNotNull null
            val label = runCatching { resolveInfo.loadLabel(packageManager)?.toString() }.getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?: packageName
            ExternalAppTarget(packageName = packageName, label = label)
        }
        .distinctBy { it.packageName }
        .sortedBy { it.label.lowercase(Locale.getDefault()) }
        .toList()
}


/**
 * 外部打开选择对话框（拆解设计 第四批：会话化迁出）。
 * 候选查询随会话打开触发；选中即记偏好（rememberChoice 现状行为）。
 */
@Composable
internal fun ExternalOpenDialog(
    target: DocumentFileModel,
    session: BrowserSessionHandle,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var options by remember { mutableStateOf<List<ExternalAppTarget>>(emptyList()) }
    var showTypeChooser by remember { mutableStateOf(false) }
    var forcedMimeType by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(target.uri) {
        val autoMime = guessExternalOpenMimeType(context, toExternalLaunchUri(context, target.uri), target.name)
        options = queryExternalOpenTargets(context, target.uri.toString(), target.name)
        showTypeChooser = options.isEmpty() || isExternalMimeLowConfidence(autoMime)
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(stringResource(R.string.context_choose_open_app), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(8.dp))
                Text(
                    target.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(16.dp))
                if (showTypeChooser) {
                    Text(
                        stringResource(R.string.browser_external_type_prompt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    ExternalOpenTypeHint.entries.forEach { hint ->
                        TextButton(
                            onClick = {
                                forcedMimeType = hint.mimeType
                                options = queryExternalOpenTargets(
                                    context,
                                    target.uri.toString(),
                                    target.name,
                                    hint.mimeType
                                )
                                if (options.isEmpty()) {
                                    Toast.makeText(context, context.getString(R.string.browser_no_app_to_open), Toast.LENGTH_SHORT).show()
                                    showTypeChooser = true
                                } else {
                                    showTypeChooser = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(hint.labelRes), color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                    if (options.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        TextButton(
                            onClick = {
                                forcedMimeType = null
                                showTypeChooser = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.browser_external_use_auto_list), color = MaterialTheme.colorScheme.primary)
                        }
                    }
                } else {
                    TextButton(
                        onClick = { showTypeChooser = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.browser_external_filter_by_type), color = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.height(8.dp))
                    options.forEach { option ->
                        TextButton(
                            onClick = {
                                val opened = session.openWithOtherApp(
                                    target.uri.toString(),
                                    target.name,
                                    option.packageName,
                                    true,
                                    forcedMimeType
                                )
                                if (opened) onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.fillMaxWidth()) {
                                Text(option.label, color = MaterialTheme.colorScheme.onSurface)
                                Text(
                                    option.packageName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.common_cancel))
                    }
                }
            }
        }
    }
}
