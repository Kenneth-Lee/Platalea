package com.kenny.localmanager.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.collectAsState
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
import com.kenny.localmanager.data.Preferences
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DictionaryScreen(
    showBackButton: Boolean = true,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember(context) { Preferences(context) }
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    val queryHistory by prefs.dictQueryHistory.collectAsState(initial = emptyList())

    var dictionaries by remember { mutableStateOf<List<StarDictSummary>>(emptyList()) }
    var selectedDictId by remember { mutableStateOf<String?>(null) }
    var loadedDict by remember { mutableStateOf<StarDictLoaded?>(null) }
    var loading by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<StarDictWord>>(emptyList()) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var definitionWord by remember { mutableStateOf<StarDictWord?>(null) }
    var definitionText by remember { mutableStateOf("") }
    var definitionLoading by remember { mutableStateOf(false) }
    var showManageDialog by remember { mutableStateOf(false) }
    var pendingDeleteSummary by remember { mutableStateOf<StarDictSummary?>(null) }

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

    fun doSearch() {
        val dict = loadedDict ?: return
        val normalizedQuery = query.trim()
        if (normalizedQuery.isEmpty()) {
            results = emptyList()
            searchError = "查询内容不能为空"
            return
        }
        query = normalizedQuery
        val (hits, err) = searchStarDictWords(dict, normalizedQuery)
        results = hits
        searchError = err
        scope.launch(Dispatchers.IO) {
            prefs.recordDictQuery(normalizedQuery)
        }
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
                    IconButton(onClick = { scope.launch { reloadDictList() } }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                    IconButton(onClick = { showManageDialog = true }, enabled = dictionaries.isNotEmpty()) {
                        Icon(Icons.Default.MenuBook, contentDescription = "管理词典")
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("正则表达式") },
                    placeholder = { Text("例如 /^(ab|ac)/i 或 (?i)^test") },
                    singleLine = true,
                    enabled = loadedDict != null
                )
                Button(onClick = { doSearch() }, enabled = loadedDict != null) {
                    Text("查询")
                }
            }

            if (queryHistory.isNotEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "最近查询（最多10条）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(onClick = {
                            scope.launch(Dispatchers.IO) {
                                prefs.clearDictQueryHistory()
                            }
                        }) {
                            Text("清空历史")
                        }
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 140.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        queryHistory.forEach { historyItem ->
                            Surface(
                                tonalElevation = 1.dp,
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = loadedDict != null) {
                                        query = historyItem
                                        doSearch()
                                    }
                            ) {
                                Text(
                                    text = historyItem,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            if (loadedDict != null) {
                TextButton(onClick = {
                    query = ""
                    results = emptyList()
                    searchError = null
                }) {
                    Text("清空结果")
                }
            }

            when {
                loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                loadedDict == null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "没有可用词典。请先在文件列表里导入 StarDict（支持 .zip 或 .ifo）。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                searchError != null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(searchError.orEmpty(), color = MaterialTheme.colorScheme.error)
                    }
                }
                results.isEmpty() && query.isNotBlank() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("未找到匹配词条", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                else -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Text(
                            if (results.isEmpty()) "输入正则后点击“查询”" else "匹配到 ${results.size} 个词条（最多展示 500）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
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
    }

    if (showManageDialog) {
        AlertDialog(
            onDismissRequest = { showManageDialog = false },
            title = { Text("已导入词典") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (dictionaries.isEmpty()) {
                        Text("暂无已导入词典", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        dictionaries.forEach { dict ->
                            val isSelected = dict.id == selectedDictId
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            selectedDictId = dict.id
                                            showManageDialog = false
                                        }
                                        .padding(vertical = 6.dp)
                                ) {
                                    Text(
                                        dict.name,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        "词条 ${dict.wordCount}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (isSelected) {
                                    Text(
                                        "当前",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(end = 8.dp)
                                    )
                                }
                                IconButton(onClick = { pendingDeleteSummary = dict }) {
                                    Icon(Icons.Default.Delete, contentDescription = "删除词典", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showManageDialog = false }) { Text("关闭") }
            }
        )
    }

    if (definitionWord != null) {
        val currentWord = definitionWord!!
        AlertDialog(
            onDismissRequest = {
                definitionWord = null
                definitionText = ""
                definitionLoading = false
            },
            title = { Text(currentWord.word) },
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
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        val content = buildString {
                            append(currentWord.word)
                            if (definitionText.isNotBlank()) {
                                append("\n\n")
                                append(definitionText)
                            }
                        }
                        clipboardManager?.setPrimaryClip(ClipData.newPlainText("词典释义", content))
                    },
                    enabled = !definitionLoading && definitionText.isNotBlank()
                ) { Text("复制内容") }
            }
        )
    }

    pendingDeleteSummary?.let { summary ->
        AlertDialog(
            onDismissRequest = { pendingDeleteSummary = null },
            title = { Text("删除词典") },
            text = { Text("确定删除已导入词典「${summary.name}」吗？") },
            confirmButton = {
                TextButton(onClick = {
                    pendingDeleteSummary = null
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
                TextButton(onClick = { pendingDeleteSummary = null }) { Text("取消") }
            }
        )
    }
}
