package com.kenny.localmanager.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.kenny.localmanager.R

// ============================================================================
// 批量操作执行页（E2 样板：GPG 批量加解密先行）
//
// 用户模型的"执行段"：决策对话框确认后，本页全屏接管，逐项展示执行状态；
// 结束（完成 / 取消 / 全部失败）停留展示结果，关闭后退回前一页面。
// 状态由宿主创建，以功能页形态入导航栈（NavigationState.push）。
// ============================================================================

/** 单项执行状态。 */
internal enum class OperationItemStatus { PENDING, RUNNING, OK, FAILED }

/**
 * 执行页状态：宿主创建，协程编排与 UI 共享读写。
 *
 * [onClose] 由宿主在入栈时接线（pop 导航栈），页面关闭按钮与返回键触发。
 */
internal class OperationRunState(
    /** 操作标题（如"加密"、"解密"）。 */
    val label: String,
    /** 逐项展示名（与执行列表一一对应）。 */
    names: List<String>
) {
    internal class Item(val name: String) {
        var status by mutableStateOf(OperationItemStatus.PENDING)
        /** 失败原因（可空 / 可空串 = 无具体原因）。 */
        var failReason by mutableStateOf<String?>(null)
        /** 项内子进度文本（如字节进度；执行中显示，结束清空）。 */
        var progressText by mutableStateOf<String?>(null)
    }

    val items: List<Item> = names.map { Item(it) }

    /** 用户请求取消（执行协程在下一项前响应）。 */
    var cancelRequested by mutableStateOf(false)

    /** 执行协程结束（正常完成或取消后收尾完毕）。 */
    var finished by mutableStateOf(false)

    /** 关闭本页：宿主入栈时接线（pop 导航栈）。 */
    var onClose: () -> Unit = {}

    val total: Int get() = items.size
    val doneCount: Int get() = items.count { it.status == OperationItemStatus.OK || it.status == OperationItemStatus.FAILED }
    val okCount: Int get() = items.count { it.status == OperationItemStatus.OK }
    val failCount: Int get() = items.count { it.status == OperationItemStatus.FAILED }

    /**
     * 逐项执行：处理状态翻转与取消检查。
     * 单项返回 null = 成功；返回非空 = 失败原因（空串 = 无具体原因，仅标记失败）。
     * 取消时停在当前项之前（不回滚已完成项）。
     */
    suspend fun <T> runEach(models: List<T>, block: suspend (T) -> String?) {
        items.forEachIndexed { index, item ->
            if (cancelRequested) return
            item.status = OperationItemStatus.RUNNING
            val reason = block(models[index])
            if (reason == null) {
                item.status = OperationItemStatus.OK
            } else {
                item.status = OperationItemStatus.FAILED
                item.failReason = reason
            }
        }
    }
}

/**
 * 批量操作执行页。
 *
 * 进行中：标题 + 进度条 + 逐项状态列表 + 取消；
 * 结束后：结果摘要（完成 / 已取消）+ 失败明细 + 关闭。
 */
@Composable
internal fun OperationScreen(state: OperationRunState) {
    BackHandler { if (state.finished) state.onClose() else state.cancelRequested = true }
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(20.dp)
    ) {
        Text(
            text = state.label,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = 28.dp, bottom = 12.dp)
        )
        if (!state.finished) {
            LinearProgressIndicator(
                progress = { if (state.total == 0) 1f else state.doneCount.toFloat() / state.total },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.op_batch_progress, state.doneCount, state.total),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            val summary = if (state.cancelRequested) {
                stringResource(R.string.op_batch_cancelled, state.doneCount, state.total)
            } else {
                stringResource(R.string.op_batch_done, state.okCount, state.failCount)
            }
            Text(
                text = summary,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.weight(1f)) {
            itemsIndexed(state.items) { _, item ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val mark = when (item.status) {
                        OperationItemStatus.PENDING -> "·"
                        OperationItemStatus.RUNNING -> "▶"
                        OperationItemStatus.OK -> "✓"
                        OperationItemStatus.FAILED -> "✗"
                    }
                    Text(
                        text = mark,
                        style = MaterialTheme.typography.bodyLarge,
                        color = when (item.status) {
                            OperationItemStatus.OK -> MaterialTheme.colorScheme.primary
                            OperationItemStatus.FAILED -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(end = 12.dp)
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                            color = if (item.status == OperationItemStatus.FAILED) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                        if (item.status == OperationItemStatus.RUNNING) {
                            item.progressText?.let { progress ->
                                Text(
                                    text = progress,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        item.failReason?.takeIf { it.isNotBlank() }?.let { reason ->
                            Text(
                                text = reason,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.End
        ) {
            if (!state.finished) {
                OutlinedButton(
                    onClick = { state.cancelRequested = true },
                    enabled = !state.cancelRequested
                ) { Text(stringResource(R.string.common_cancel)) }
            } else {
                OutlinedButton(onClick = state.onClose) { Text(stringResource(R.string.common_close)) }
            }
        }
    }
}
