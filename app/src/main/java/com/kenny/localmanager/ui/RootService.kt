package com.kenny.localmanager.ui

import androidx.compose.runtime.getValue
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 根功能的服务（功能模块化第一批样板）：当前根目录这一会话参数的唯一属地。
 *
 * 为什么引入：此前"当前根是什么"的内存镜像挂在目录域（BrowserTabState），
 * 速记/书摘靠回填 effect 订阅目录属性——目录域成为根路径服务器是历史偶然。
 * 按属地公理归位到根功能：任何域（目录、速记、配置导出、查看器缓存）
 * 获取根路径的方式完全对称（查询本服务）。
 *
 * 生命周期：进程级存活，不自持作用域——restore/change 为挂起函数，
 * 由调用方（启动流程、换根向导）在自己的作用域执行，取消权在调用方。
 *
 * 换根立即生效：修改根必然发生在文件浏览功能上，change 立即更新内存态；
 * 其他功能在下一次被显示或被调用时读取最新值（速记/书摘显示时核对
 * sessionRoot，不一致则收尾保存旧会话后按新根重载）。后台持续运行的
 * 服务（如音乐播放）不依赖根，直接使用绝对 uri。
 */
internal object RootService {
    var rootUri by mutableStateOf<String?>(null)
        private set

    /** 启动时从配置存储恢复（幂等：已有值不覆盖）。 */
    suspend fun restore(config: BrowserConfigView) {
        if (rootUri != null) return
        rootUri = config.rootUri.first()?.let { normalizeContentUriString(it) }
    }

    /** 换根：立即更新内存态并持久化到配置存储。 */
    suspend fun change(uri: String, config: BrowserConfigView) {
        val normalized = normalizeContentUriString(uri)
        rootUri = normalized
        config.setRootUri(normalized)
    }

    /** 仅测试用：重置内存态。 */
    fun resetForTest() {
        rootUri = null
    }
}
