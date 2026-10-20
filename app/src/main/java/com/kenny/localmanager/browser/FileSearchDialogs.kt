package com.kenny.localmanager.browser

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.kenny.localmanager.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.kenny.localmanager.file.DocumentFileModel
import com.kenny.localmanager.file.RecursiveFileSearchCriteria
import com.kenny.localmanager.file.RecursiveFileSearchHit
import com.kenny.localmanager.file.parseSizeRange
import com.kenny.localmanager.file.parseTimeRange
import com.kenny.localmanager.file.searchFilesRecursively
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 查找会话状态（拆解设计 第四批）：输入条件与结果共享一份状态，
 * 结果对话框"调整条件"返回输入视图时输入不丢（现状行为保留）；
 * 会话关闭即整体销毁（输入残留清除为设计预期改进）。
 */
internal class SearchSessionState {
    var namePattern by mutableStateOf(TextFieldValue(""))
    var contentPattern by mutableStateOf("")
    var sizeRange by mutableStateOf("")
    var modified by mutableStateOf("")
    var results by mutableStateOf<List<RecursiveFileSearchHit>>(emptyList())
    var running by mutableStateOf(false)
    var cancelled by mutableStateOf(false)
    var progress by mutableStateOf<Pair<Int, Int>?>(null)
    var error by mutableStateOf<String?>(null)
    /** true 时渲染结果视图（同一会话内的两形态）。 */
    var resultsVisible by mutableStateOf(false)

    /** 会话重置：overflow 入口每次打开都从空白条件开始（现状行为）。 */
    fun reset() {
        namePattern = TextFieldValue("")
        contentPattern = ""
        sizeRange = ""
        modified = ""
        results = emptyList()
        error = null
        resultsVisible = false
    }
}

@Composable
internal fun rememberSearchSessionState(): SearchSessionState = remember { SearchSessionState() }

/**
 * 查找编排（从 BrowserScreen 迁出）：解析条件、后台递归搜索、
 * 结束后切换到结果视图。
 */
internal fun runFileSearch(
    state: SearchSessionState,
    context: Context,
    scope: CoroutineScope,
    currentUri: String,
) {
    val nameRegex = try {
        state.namePattern.text.trim().takeIf { it.isNotEmpty() }?.let(::Regex)
    } catch (error: Exception) {
        state.error = context.getString(R.string.browser_filename_regex_invalid, error.message ?: error.javaClass.simpleName)
        return
    }
    val contentRegex = try {
        state.contentPattern.trim().takeIf { it.isNotEmpty() }?.let(::Regex)
    } catch (error: Exception) {
        state.error = context.getString(R.string.browser_content_regex_invalid, error.message ?: error.javaClass.simpleName)
        return
    }
    val sizeRange = parseSizeRange(state.sizeRange)
    if (sizeRange == null) {
        state.error = context.getString(R.string.browser_size_range_invalid, state.sizeRange.trim())
        return
    }
    val timeRange = parseTimeRange(state.modified, System.currentTimeMillis())
    if (timeRange == null) {
        state.error = context.getString(R.string.browser_time_range_invalid, state.modified.trim())
        return
    }
    state.cancelled = false
    state.progress = null
    scope.launch {
        state.running = true
        state.error = null
        try {
            val criteria = RecursiveFileSearchCriteria(
                nameRegex = nameRegex,
                contentRegex = contentRegex,
                minSizeBytes = sizeRange.minBytes,
                maxSizeBytes = sizeRange.maxBytes,
                modifiedAfterMillis = timeRange.afterMillis,
                modifiedBeforeMillis = timeRange.beforeMillis
            )
            val results = withContext(Dispatchers.IO) {
                searchFilesRecursively(
                    context, currentUri, criteria,
                    isCancelled = { state.cancelled },
                    onProgress = { dirs, hits -> state.progress = dirs to hits }
                )
            }
            if (state.cancelled) {
                // 用户取消：丢弃结果，留在查找界面便于调整条件
                Toast.makeText(context, context.getString(R.string.browser_search_cancelled), Toast.LENGTH_SHORT).show()
            } else {
                state.results = results
                state.resultsVisible = true
                if (results.isEmpty()) {
                    Toast.makeText(context, context.getString(R.string.browser_no_matching_files), Toast.LENGTH_SHORT).show()
                }
            }
        } catch (error: Exception) {
            state.error = context.getString(R.string.browser_search_failed, error.message ?: error.javaClass.simpleName)
        } finally {
            state.running = false
            state.progress = null
        }
    }
}


@Composable
internal fun FileSearchDialog(
    namePattern: TextFieldValue,
    contentPattern: String,
    sizeRange: String,
    modified: String,
    searching: Boolean,
    searchProgress: Pair<Int, Int>?,
    error: String?,
    onNamePatternChange: (TextFieldValue) -> Unit,
    onContentPatternChange: (String) -> Unit,
    onSizeRangeChange: (String) -> Unit,
    onModifiedChange: (String) -> Unit,
    onSearch: () -> Unit,
    onSearchCancel: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(stringResource(R.string.browser_file_search_title), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                val namePatternFocus = remember { FocusRequester() }
                OutlinedTextField(
                    value = namePattern,
                    onValueChange = onNamePatternChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(namePatternFocus),
                    label = { Text(stringResource(R.string.browser_filename_regex_label)) },
                    placeholder = { Text(stringResource(R.string.browser_regex_example)) },
                    singleLine = true
                )
                Row(modifier = Modifier.fillMaxWidth()) {
                    listOf("\\", "+").forEach { sym ->
                        TextButton(
                            onClick = {
                                // 光标处插入（与批量改名一致）：取选区，插入后光标移到
                                // 插入内容之后并回焦输入框
                                val sel = namePattern.selection
                                val start = sel.start.coerceIn(0, namePattern.text.length)
                                val end = sel.end.coerceIn(0, namePattern.text.length)
                                val newText = namePattern.text.substring(0, start) + sym +
                                    namePattern.text.substring(end)
                                onNamePatternChange(
                                    TextFieldValue(newText, TextRange(start + sym.length))
                                )
                                namePatternFocus.requestFocus()
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp)
                        ) { Text(sym, style = MaterialTheme.typography.titleMedium) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = contentPattern,
                    onValueChange = onContentPatternChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.browser_content_regex_label)) },
                    supportingText = { Text(stringResource(R.string.browser_content_regex_hint)) },
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = sizeRange,
                    onValueChange = onSizeRangeChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.browser_size_range_label)) },
                    supportingText = { Text(stringResource(R.string.browser_size_range_hint)) },
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = modified,
                    onValueChange = onModifiedChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.browser_time_range_label)) },
                    supportingText = { Text(stringResource(R.string.browser_time_range_hint)) },
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.browser_recursive_search_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (searching) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    searchProgress?.let { (dirs, hits) ->
                        Text(
                            stringResource(R.string.browser_search_progress, dirs, hits),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    SelectionContainer {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss, enabled = !searching) { Text(stringResource(R.string.common_close)) }
                    if (searching) {
                        TextButton(onClick = onSearchCancel) { Text(stringResource(R.string.browser_search_cancel)) }
                    } else {
                        TextButton(onClick = onSearch) {
                            Text(stringResource(R.string.browser_start_search))
                        }
                    }
                }
            }
        }
    }
}


@Composable
internal fun FileSearchResultsDialog(
    results: List<RecursiveFileSearchHit>,
    pendingUris: Set<String>,
    onAddAll: () -> Unit,
    onRefine: () -> Unit
) {
    val context = LocalContext.current
    val timeFormatter = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    // 任何方式关闭（按钮/返回键/点外部）都回到查找界面，便于调整条件重查
    Dialog(onDismissRequest = onRefine) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .heightIn(max = 640.dp)
            ) {
                Text(stringResource(R.string.browser_search_results, results.size), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onRefine) { Text(stringResource(R.string.browser_search_refine)) }
                    TextButton(onClick = onAddAll, enabled = results.any { it.model.uri.toString() !in pendingUris }) {
                        Text(stringResource(R.string.browser_add_all_to_pending))
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (results.isEmpty()) {
                    Text(stringResource(R.string.browser_no_matching_files_dot), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(results, key = { it.model.uri.toString() }) { item ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(item.model.name, style = MaterialTheme.typography.titleSmall)
                                    Spacer(Modifier.height(4.dp))
                                    SelectionContainer {
                                        Text(
                                            item.relativePath,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        context.getString(R.string.browser_search_result_size_modified, item.model.displaySize.ifBlank { context.getString(R.string.browser_unknown) }, timeFormatter.format(Date(item.model.lastModified))),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

