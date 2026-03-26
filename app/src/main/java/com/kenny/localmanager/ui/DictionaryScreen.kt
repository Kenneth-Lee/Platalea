package com.kenny.localmanager.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kenny.localmanager.dict.StarDictLoaded
import com.kenny.localmanager.dict.StarDictSummary
import com.kenny.localmanager.dict.StarDictWord
import com.kenny.localmanager.dict.deleteImportedStarDict
import com.kenny.localmanager.dict.listImportedStarDicts
import com.kenny.localmanager.dict.loadImportedStarDict
import com.kenny.localmanager.dict.readStarDictExplanation
import com.kenny.localmanager.dict.searchStarDictWords
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DictionaryScreen(
    showBackButton: Boolean = true,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var dictionaries by remember { mutableStateOf<List<StarDictSummary>>(emptyList()) }
    var selectedDictId by remember { mutableStateOf<String?>(null) }
    var loadedDict by remember { mutableStateOf<StarDictLoaded?>(null) }
    var loading by remember { mutableStateOf(false) }

    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<StarDictWord>>(emptyList()) }
    var searchError by remember { mutableStateOf<String?>(null) }

    var dropdownExpanded by remember { mutableStateOf(false) }
    var definitionWord by remember { mutableStateOf<StarDictWord?>(null) }
    var definitionText by remember { mutableStateOf("") }
    var definitionLoading by remember { mutableStateOf(false) }

    var showDeleteConfirm by remember { mutableStateOf(false) }

    suspend fun reloadDictList() {
        loading = true
        val list = withContext(Dispatchers.IO) { listImportedStarDicts(context) }
        dictionaries = list
        if (list.none { it.id == selectedDictId }) {
            selectedDictId = list.firstOrNull()?.id
        }
        loading = false
    }

    BackHandler(onBack = onBack)

    LaunchedEffect(Unit) {
        reloadDictList()
    }

    LaunchedEffect(selectedDictId) {
        val id = selectedDictId
        if (id == null) {
            loadedDict = null
            results = emptyList()
            searchError = null
            return@LaunchedEffect
        }
        loading = true
        loadedDict = withContext(Dispatchers.IO) { loadImportedStarDict(context, id) }
        results = emptyList()
        searchError = null
        loading = false
    }

    val selectedSummary = remember(dictionaries, selectedDictId) {
        dictionaries.firstOrNull { it.id == selectedDictId }
    }

    fun doSearch() {
        val dict = loadedDict ?: return
        val (hits, err) = searchStarDictWords(dict, query)
        results = hits
        searchError = err
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("词典") },
                navigationIcon = if (showBackButton) {
                    {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    }
                } else {
                    { }
                },
                actions = {
                    IconButton(onClick = {
                        scope.launch { reloadDictList() }
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                    IconButton(
                        onClick = { showDeleteConfirm = true },
                        enabled = selectedSummary != null
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "删除词典")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("已导入词典", style = MaterialTheme.typography.titleSmall)
            Box {
                OutlinedTextField(
                    value = selectedSummary?.name ?: "(无)",
                    onValueChange = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = dictionaries.isNotEmpty()) { dropdownExpanded = true },
                    readOnly = true,
                    enabled = dictionaries.isNotEmpty(),
                    placeholder = { Text("还没有导入词典，请在文件列表中长按 .zip/.ifo 导入") }
                )
                DropdownMenu(
                    expanded = dropdownExpanded,
                    onDismissRequest = { dropdownExpanded = false }
                ) {
                    dictionaries.forEach { dict ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(dict.name)
                                    Text(
                                        "词条 ${dict.wordCount}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            onClick = {
                                selectedDictId = dict.id
                                dropdownExpanded = false
                            }
                        )
                    }
                }
            }

            selectedSummary?.let { summary ->
                Text(
                    "导入时间：${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(summary.importedAt))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("正则表达式") },
                placeholder = { Text("例如 /^(ab|ac)/i 或 (?i)^test") },
                singleLine = true,
                enabled = loadedDict != null
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { doSearch() }, enabled = loadedDict != null) {
                    Text("查询")
                }
                TextButton(onClick = {
                    query = ""
                    results = emptyList()
                    searchError = null
                }, enabled = loadedDict != null) {
                    Text("清空")
                }
            }

            when {
                loading -> {
                    Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                loadedDict == null -> {
                    Text(
                        "没有可用词典。请先在文件列表里导入 StarDict（支持 .zip 或 .ifo）。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                searchError != null -> {
                    Text(searchError.orEmpty(), color = MaterialTheme.colorScheme.error)
                }
                results.isEmpty() && query.isNotBlank() -> {
                    Text("未找到匹配词条", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> {
                    Text(
                        if (results.isEmpty()) "输入正则后点击“查询”" else "匹配到 ${results.size} 个词条（最多展示 500）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(results, key = { "${it.word}_${it.offset}_${it.size}" }) { word ->
                            Surface(
                                tonalElevation = 1.dp,
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        definitionWord = word
                                        definitionText = ""
                                        definitionLoading = true
                                        scope.launch {
                                            val dict = loadedDict
                                            val id = selectedDictId
                                            if (dict == null || id == null) {
                                                definitionText = "词典未加载"
                                            } else {
                                                definitionText = withContext(Dispatchers.IO) {
                                                    readStarDictExplanation(context, id, dict, word)
                                                }
                                            }
                                            definitionLoading = false
                                        }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        word.word,
                                        style = MaterialTheme.typography.bodyLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (definitionWord != null) {
        AlertDialog(
            onDismissRequest = {
                definitionWord = null
                definitionText = ""
                definitionLoading = false
            },
            title = { Text(definitionWord!!.word) },
            text = {
                if (definitionLoading) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.width(18.dp).height(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("加载释义中…")
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(definitionText)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    definitionWord = null
                    definitionText = ""
                    definitionLoading = false
                }) { Text("关闭") }
            }
        )
    }

    if (showDeleteConfirm && selectedSummary != null) {
        val summary = selectedSummary
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除词典") },
            text = { Text("确定删除已导入词典「${summary.name}」吗？") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            deleteImportedStarDict(context, summary.id)
                        }
                        reloadDictList()
                    }
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") }
            }
        )
    }
}
