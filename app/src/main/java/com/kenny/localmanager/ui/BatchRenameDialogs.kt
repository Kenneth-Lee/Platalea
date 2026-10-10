package com.kenny.localmanager.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import android.view.WindowManager
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.kenny.localmanager.R
import com.kenny.localmanager.file.BatchRenameCompileResult
import com.kenny.localmanager.file.BatchRenameItemError
import com.kenny.localmanager.file.BatchRenamePlan
import com.kenny.localmanager.file.BatchRenamePreviewItem
import com.kenny.localmanager.file.DocumentFileModel
import com.kenny.localmanager.file.buildBatchRenamePreview
// ============================================================================
// 批量重命名 —— 全屏对话框（正则 + 模板 + 逐文件预览）
//
// 渐进式预览：空正则视为匹配整个文件名、空模板视为保留匹配段，打开
// 即见完整文件名列表；输入正则后显示匹配段，输入模板后显示替换结果。
// 符号快捷排插入到"最近活跃"输入框的光标处（不能用当前焦点——按按钮
// 本身会抢走输入框焦点，曾导致符号插错框、正则被 \1 污染）。输入变化
// 即重算预览（纯函数），用户检查预览后才允许提交；提交动作（renameDocument
// / 进度 / 收尾）由调用方（BrowserTabHost 待处理列表路径）执行。
// ============================================================================

/** 快捷插入符号（正则元字符与模板组引用）。 */
private val symbolPalette = listOf(
    "\\", ".", "*", "+", "?", "^", "$", "(", ")", "[", "]", "{", "}", "|", "-", "_", "#", "#\\##", "\\0", "\\1", "\\2", "\\3", "&", "&&&"
)

/** 符号插入目标：最近获得焦点的输入框。 */
private enum class BatchRenameField { PATTERN, TEMPLATE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BatchRenameDialog(
    items: List<DocumentFileModel>,
    onDismiss: () -> Unit,
    onConfirm: (List<Pair<DocumentFileModel, String>>) -> Unit
) {
    var patternValue by remember { mutableStateOf(TextFieldValue("")) }
    var templateValue by remember { mutableStateOf(TextFieldValue("")) }
    // 插入目标用"最近活跃"而非"当前焦点"：点符号按钮会令输入框失焦，
    // 当前焦点在那一刻必然为空，符号就会插错框。
    var activeField by remember { mutableStateOf(BatchRenameField.PATTERN) }
    val patternFocus = remember { FocusRequester() }
    val templateFocus = remember { FocusRequester() }

    fun insertSymbol(symbol: String) {
        if (activeField == BatchRenameField.TEMPLATE) {
            val v = templateValue
            val pos = v.selection.start.coerceIn(0, v.text.length)
            templateValue = TextFieldValue(
                v.text.substring(0, pos) + symbol + v.text.substring(pos),
                TextRange(pos + symbol.length)
            )
            templateFocus.requestFocus()
        } else {
            val v = patternValue
            val pos = v.selection.start.coerceIn(0, v.text.length)
            patternValue = TextFieldValue(
                v.text.substring(0, pos) + symbol + v.text.substring(pos),
                TextRange(pos + symbol.length)
            )
            patternFocus.requestFocus()
        }
    }

    val compiled = remember(patternValue.text, templateValue.text) {
        BatchRenamePlan.compile(patternValue.text, templateValue.text)
    }
    val plan = (compiled as? BatchRenameCompileResult.Ok)?.plan
    val planOk = plan != null
    // 编译失败（非法正则 / 越界组引用）时仍列出文件名，便于对照排查。
    val preview = remember(plan, items) {
        if (plan != null) buildBatchRenamePreview(items.map { it.name }, plan)
        else items.map { BatchRenamePreviewItem(it.name, null, false, null) }
    }
    val hasFatalError = preview.any { it.error != null }
    val validCount = preview.count { it.newName != null && it.error == null }
    val applyEnabled = !hasFatalError && validCount > 0
    val applyRename: () -> Unit = {
        val pairs = preview.mapIndexedNotNull { index, item ->
            val name = item.newName
            if (name != null && item.error == null) items[index] to name else null
        }
        if (pairs.isNotEmpty()) onConfirm(pairs)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        // IME 避让前置条件：Dialog 窗口默认不透传 IME insets，
        // 需关闭 decor 自动消费 + ADJUST_RESIZE，bottomBar 的 imePadding 才能感知键盘/联想条
        val view = LocalView.current
        SideEffect {
            val window = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                stringResource(R.string.batch_rename_title, items.size),
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                            }
                        },
                        actions = {
                            // 本机 IME 下拉收起后常驻联想条（窗口未关），底部提交键被盖住点不到；
                            // 顶部动作位永远不被 IME 遮挡，作为可靠提交入口
                            val keyboard = LocalSoftwareKeyboardController.current
                            val focusManager = LocalFocusManager.current
                            TextButton(
                                onClick = {
                                    keyboard?.hide()
                                    focusManager.clearFocus()
                                    applyRename()
                                },
                                enabled = applyEnabled
                            ) { Text(stringResource(R.string.batch_rename_apply, validCount)) }
                        }
                    )
                },
                bottomBar = {
                    // imePadding：下拉收起键盘后 IME 常驻联想条（窗口未真正关闭），
                    // 按钮栏需避让否则被 IME 窗口盖住无法点击；decorFitsSystemWindows=false 后导航栏需自行补偿
                    Surface(
                        Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .imePadding(),
                        tonalElevation = 2.dp
                    ) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                            if (hasFatalError) {
                                Text(
                                    stringResource(R.string.batch_rename_has_error),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                            }
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Spacer(Modifier.weight(1f))
                                TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
                                Button(onClick = applyRename, enabled = applyEnabled) {
                                    Text(stringResource(R.string.batch_rename_apply, validCount))
                                }
                            }
                        }
                    }
                }
            ) { padding ->
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 12.dp)
                ) {
                    OutlinedTextField(
                        value = patternValue,
                        onValueChange = { patternValue = it },
                        label = { Text(stringResource(R.string.batch_rename_pattern)) },
                        isError = compiled is BatchRenameCompileResult.InvalidPattern,
                        supportingText = {
                            when (val c = compiled) {
                                is BatchRenameCompileResult.InvalidPattern ->
                                    Text(stringResource(R.string.batch_rename_invalid_pattern, c.message))
                                is BatchRenameCompileResult.BadGroupReference ->
                                    Text(stringResource(R.string.batch_rename_bad_group, c.group, c.available))
                                else -> Text(stringResource(R.string.batch_rename_help))
                            }
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .focusRequester(patternFocus)
                            .onFocusChanged { if (it.isFocused) activeField = BatchRenameField.PATTERN }
                    )
                    OutlinedTextField(
                        value = templateValue,
                        onValueChange = { templateValue = it },
                        label = { Text(stringResource(R.string.batch_rename_template)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                            .focusRequester(templateFocus)
                            .onFocusChanged { if (it.isFocused) activeField = BatchRenameField.TEMPLATE }
                    )
                    // 符号快捷排：插入最近活跃输入框的光标处（输入法敲正则元字符不便）
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        symbolPalette.forEach { sym ->
                            TextButton(
                                onClick = { insertSymbol(sym) },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                modifier = Modifier.defaultMinSize(minWidth = 0.dp, minHeight = 28.dp)
                            ) {
                                Text(
                                    sym,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                        Text(
                            stringResource(R.string.batch_rename_preview),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    LazyColumn(
                        Modifier.weight(1f),
                        contentPadding = PaddingValues(bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        itemsIndexed(preview) { _, item ->
                            BatchRenamePreviewRow(item, planOk)
                        }
                    }
                }
            }
        }
    }
}

/**
 * 预览行：第一行为原名（命中区间以主题色加粗高亮；各捕获组以轮换的
 * 前景色覆盖，便于对照 \1 \2 引用的内容；不匹配则整行灰色）。
 * 第二行为新名 / 无变化 / 错误原因。
 */
@Composable
private fun BatchRenamePreviewRow(item: BatchRenamePreviewItem, planOk: Boolean) {
    val skipColor = MaterialTheme.colorScheme.onSurfaceVariant
    val errorColor = MaterialTheme.colorScheme.error
    val highlightColor = MaterialTheme.colorScheme.primary
    val originalColor = if (item.matched) MaterialTheme.colorScheme.onSurface else skipColor
    val newNameColor = when {
        item.error != null -> errorColor
        item.newName == null -> skipColor
        else -> MaterialTheme.colorScheme.primary
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        // 捕获组轮换色（与整段高亮不同色，后加的 span 覆盖重叠段的
        // 前景色，组内即显示组色）
        val groupColors = listOf(
            MaterialTheme.colorScheme.tertiary,
            MaterialTheme.colorScheme.secondary,
            MaterialTheme.colorScheme.primary
        )
        val annotated = buildAnnotatedString {
            append(item.original)
            item.matchRanges.forEach { r ->
                addStyle(
                    SpanStyle(color = highlightColor, fontWeight = FontWeight.SemiBold),
                    r.first, r.last + 1
                )
            }
            item.groupRanges.forEachIndexed { i, r ->
                addStyle(
                    SpanStyle(color = groupColors[i % groupColors.size], fontWeight = FontWeight.Bold),
                    r.first, r.last + 1
                )
            }
        }
        Text(
            annotated,
            style = MaterialTheme.typography.bodySmall,
            color = originalColor,
            textDecoration = if (item.newName != null && item.error == null) TextDecoration.LineThrough else null,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        val targetText = when {
            item.error != null -> errorText(item.error)
            item.newName != null -> item.newName
            !planOk -> null // 编译失败：只列文件名，避免误导性的"不匹配"
            item.matched -> item.original // 匹配但无变化（空模板保留整名/匹配段等于原段）
            else -> stringResource(R.string.batch_rename_skip)
        }
        if (targetText != null) {
            Text(
                targetText,
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = if (item.matched && item.newName == null) FontStyle.Italic else FontStyle.Normal,
                color = newNameColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun errorText(error: BatchRenameItemError): String = when (error) {
    BatchRenameItemError.EMPTY -> stringResource(R.string.batch_rename_error_empty)
    BatchRenameItemError.SLASH -> stringResource(R.string.batch_rename_error_slash)
    BatchRenameItemError.CONFLICT -> stringResource(R.string.batch_rename_error_conflict)
}
