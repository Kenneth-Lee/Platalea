package com.kenny.localmanager.ui

import android.content.Context
import android.graphics.Color
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.kenny.localmanager.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer

private const val ABOUT_README_ASSET = "README.md"
private const val ABOUT_TIPS_ASSET = "about_tips.txt"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutTabRoute() {
    val context = LocalContext.current
    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
        }.getOrDefault("")
    }
    val versionWarn = remember(versionName) {
        versionName.replace(Regex("[^0-9.]"), "").takeIf { it.isNotBlank() }?.toFloatOrNull()?.let { it <= 1.0f } == true
    }

    var readmeMarkdown by remember { mutableStateOf<String?>(null) }
    var readmeError by remember { mutableStateOf<String?>(null) }
    var tips by remember { mutableStateOf<List<String>>(emptyList()) }
    var tipsError by remember { mutableStateOf<String?>(null) }
    var showTipDialog by remember { mutableStateOf(false) }
    var currentTip by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val unknownError = context.getString(R.string.common_unknown_error)
        readmeError = null
        tipsError = null
        readmeMarkdown = null
        tips = emptyList()

        runCatching {
            withContext(Dispatchers.IO) { loadAssetText(context, ABOUT_README_ASSET) }
        }.onSuccess { readmeMarkdown = it }
            .onFailure {
                readmeError = context.getString(
                    R.string.about_readme_read_failed,
                    it.message ?: unknownError
                )
            }

        runCatching {
            withContext(Dispatchers.IO) { loadAssetText(context, ABOUT_TIPS_ASSET) }
        }.onSuccess { loadedTips ->
            tips = parseTips(loadedTips)
            if (tips.isEmpty()) {
                tipsError = context.getString(R.string.about_tips_empty, ABOUT_TIPS_ASSET)
            }
        }.onFailure {
            tipsError = context.getString(
                R.string.about_tips_read_failed,
                it.message ?: unknownError
            )
        }
    }

    val surfaceColor = MaterialTheme.colorScheme.surface
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val readmeHtml = remember(readmeMarkdown, surfaceColor, onSurfaceColor) {
        readmeMarkdown?.let { markdown ->
            markdownToHtml(
                markdown = markdown,
                backgroundColor = surfaceColor,
                foregroundColor = onSurfaceColor
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.main_menu_about)) },
                actions = {
                    TextButton(onClick = {
                        val pool = tips
                        currentTip = when {
                            pool.isNotEmpty() -> pool.random()
                            tipsError != null -> tipsError
                            else -> context.getString(R.string.about_no_tips)
                        }
                        showTipDialog = true
                    }) {
                        Text(stringResource(R.string.about_try_luck))
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Local Manager", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        stringResource(R.string.about_author),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        stringResource(R.string.about_version, versionName),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (versionWarn) {
                        Text(
                            stringResource(R.string.about_disclaimer),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            if (readmeError != null) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text(
                        readmeError!!,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.fillMaxSize()) {
                    Text(
                        stringResource(R.string.about_readme_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                    when {
                        readmeHtml != null -> {
                            AndroidView(
                                factory = { webViewContext ->
                                    WebView(webViewContext).apply {
                                        setBackgroundColor(Color.TRANSPARENT)
                                        settings.javaScriptEnabled = false
                                        webViewClient = WebViewClient()
                                        val html = readmeHtml.orEmpty()
                                        tag = html.hashCode()
                                        loadDataWithBaseURL("about:blank", html, "text/html", "UTF-8", null)
                                    }
                                },
                                modifier = Modifier.fillMaxSize(),
                                update = { webView ->
                                    val html = readmeHtml.orEmpty()
                                    val loadKey = html.hashCode()
                                    if (webView.tag != loadKey) {
                                        webView.tag = loadKey
                                        webView.loadDataWithBaseURL("about:blank", html, "text/html", "UTF-8", null)
                                    }
                                }
                            )
                        }
                        else -> {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                if (readmeError == null) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        CircularProgressIndicator()
                                        Spacer(Modifier.height(12.dp))
                                        Text(
                                            stringResource(R.string.about_readme_loading),
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

    if (showTipDialog) {
        AlertDialog(
            onDismissRequest = { showTipDialog = false },
            title = { Text(stringResource(R.string.about_tip_dialog_title)) },
            text = { Text(currentTip.orEmpty()) },
            confirmButton = {
                Button(onClick = { showTipDialog = false }) {
                    Text(stringResource(R.string.common_close))
                }
            }
        )
    }
}

private suspend fun loadAssetText(context: Context, assetName: String): String {
    return withContext(Dispatchers.IO) {
        context.assets.open(assetName).bufferedReader(Charsets.UTF_8).use { it.readText() }
    }
}

private fun parseTips(raw: String): List<String> {
    return raw.lineSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .toList()
}

private fun markdownToHtml(markdown: String, backgroundColor: ComposeColor, foregroundColor: ComposeColor): String {
    val parser = Parser.builder().build()
    val renderer = HtmlRenderer.builder().build()
    val body = renderer.render(parser.parse(markdown))
    val bg = toCssColor(backgroundColor)
    val fg = toCssColor(foregroundColor)
    return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <style>
                body {
                    margin: 0;
                    padding: 16px;
                    background: $bg;
                    color: $fg;
                    font-family: sans-serif;
                    font-size: 16px;
                    line-height: 1.7;
                    word-break: break-word;
                }
                h1, h2, h3, h4, h5, h6 {
                    line-height: 1.35;
                    margin: 1.1em 0 0.55em;
                }
                p { margin: 0.6em 0; }
                pre {
                    overflow-x: auto;
                    padding: 12px;
                    border-radius: 10px;
                    background: rgba(128, 128, 128, 0.12);
                }
                code {
                    font-family: monospace;
                    background: rgba(128, 128, 128, 0.12);
                    padding: 0.15em 0.35em;
                    border-radius: 4px;
                }
                pre code { background: transparent; padding: 0; }
                blockquote {
                    margin: 0.8em 0;
                    padding: 0.2em 0 0.2em 1em;
                    border-left: 4px solid rgba(128, 128, 128, 0.45);
                    color: rgba(128, 128, 128, 0.95);
                }
                img { max-width: 100%; height: auto; }
                table { border-collapse: collapse; width: 100%; margin: 0.8em 0; }
                th, td { border: 1px solid rgba(128, 128, 128, 0.35); padding: 6px 10px; }
                th { background: rgba(128, 128, 128, 0.1); }
                a { color: #2196F3; }
                ul, ol { padding-left: 1.6em; }
            </style>
        </head>
        <body>$body</body>
        </html>
    """.trimIndent()
}

private fun toCssColor(color: ComposeColor): String {
    val argb = color.toArgb()
    return "#%06X".format(argb and 0xFFFFFF)
}
