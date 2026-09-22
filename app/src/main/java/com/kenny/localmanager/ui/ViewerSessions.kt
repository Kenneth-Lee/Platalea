package com.kenny.localmanager.ui

import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember

/**
 * 查看器会话缓存契约：跨域共享的 Markdown/WebView 会话（HTML 渲染结果与 WebView 实例，按内容签名失效）。
 *
 * 非查看器域（速记/书摘/目录/打包阅读器/家庭网络）只依赖本接口——
 * 或透传给查看器屏，或调用失效/清空；WebView 生命周期细节封在实现内（MarkdownViewerScreen.kt）。
 */
internal interface ViewerSessions {
    /** 按内容签名取缓存 HTML（签名不符则失效并返回 null；命中会刷新 LRU 序）。 */
    fun getHtml(key: String, signature: String): String?

    fun putHtml(key: String, html: String, signature: String)

    /** 原样窥视缓存 HTML（不校验签名、不刷新 LRU 序），供多页返回栈存在性判断。 */
    fun peekHtml(key: String): String?

    fun getWebView(key: String): WebView?

    fun putWebView(key: String, webView: WebView)

    fun setPageReady(key: String, ready: Boolean)

    /** 页面就绪状态（缺失视为未就绪）。 */
    fun isPageReady(key: String): Boolean

    fun invalidate(key: String)

    /** 指定 URI 前缀的全部会话失效（保存覆盖后旧内容不再复用）。 */
    fun invalidateByUri(uri: String)

    /** 全部会话失效（根目录切换后 URI 语义整体改变）。 */
    fun clear()
}

/**
 * 查看器域共享会话缓存工厂：随组合创建，根目录切换时整体失效
 * （根变后 content URI 指向的文件集合改变，旧 WebView 会话不可复用）。
 */
@Composable
internal fun rememberViewerSessions(rootUri: String?): ViewerSessions {
    val cache = remember { MarkdownViewerSessionCache() }
    LaunchedEffect(rootUri) { cache.clear() }
    return cache
}

/** 查看器域局部会话缓存工厂（不挂根切换失效），供家庭网络等自持入口的域。 */
@Composable
internal fun rememberLocalViewerSessions(): ViewerSessions = remember { MarkdownViewerSessionCache() }
