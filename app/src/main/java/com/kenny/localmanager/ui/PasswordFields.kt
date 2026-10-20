package com.kenny.localmanager.ui

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.kenny.localmanager.R
import com.kenny.localmanager.crypto.CryptoDialogs
import com.kenny.localmanager.obfuscate.QuickObfuscateDialogs

/**
 * 密码输入共享件：校验、可靠输入框（自定义键盘，支持键盘内二次确认）。
 * GPG 弹窗族（CryptoDialogs）、快速混淆（QuickObfuscateDialogs）、
 * 解锁门（宿主）等多域共用，故独立成共享文件而非寄居主程序。
 */

/**
 * 密码 + 键盘内二次确认：输入一次进入确认轮，再次输入一致才回填，
 * 全程不出安全键盘；不一致在键盘内提示并从头重输。
 */
@Composable
internal fun PasswordConfirmationFields(
    password: String,
    onPasswordChange: (String) -> Unit,
    passwordLabel: String,
    enabled: Boolean
) {
    ReliablePasswordInputField(
        value = password,
        onValueChange = onPasswordChange,
        label = { Text(passwordLabel) },
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        requireConfirmation = true
    )
}


private fun readClipboardPassword(context: Context): String? {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
    val clip = clipboard.primaryClip ?: return null
    if (clip.itemCount <= 0) return null
    val text = clip.getItemAt(0).coerceToText(context)?.toString() ?: return null
    return text.ifBlank { null }
}

private enum class PasswordKeyCategory { DIGIT, UPPER, LOWER, SYMBOL }

private val digitKeys = "1234567890".toList()
private val upperKeys = ('A'..'Z').toList()
private val lowerKeys = ('a'..'z').toList()
private val symbolKeys = listOf(
    '~', '!', '@', '#', '$', '%', '^', '&', '*',
    '(', ')', '_', '+', '-', '=', '[', ']', '{', '}',
    '|', ';', ':', '\'', '"', ',', '.', '<', '>', '/', '?', '`', '\\'
)

private fun categoryPages(category: PasswordKeyCategory): List<List<Char>> {
    val keys = when (category) {
        PasswordKeyCategory.DIGIT -> digitKeys
        PasswordKeyCategory.UPPER -> upperKeys
        PasswordKeyCategory.LOWER -> lowerKeys
        PasswordKeyCategory.SYMBOL -> symbolKeys
    }
    return keys.chunked(30)
}

@Composable
private fun PasswordGridPage(
    keys: List<Char>,
    enabled: Boolean,
    onPress: (Char) -> Unit
) {
    val rows = keys.chunked(5)
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        rows.forEach { rowKeys ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                rowKeys.forEach { key ->
                    OutlinedButton(
                        onClick = { onPress(key) },
                        enabled = enabled,
                        modifier = Modifier
                            .padding(horizontal = 2.dp, vertical = 1.dp)
                            .width(50.dp)
                            .height(32.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(key.toString(), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}


@Composable
internal fun ReliablePasswordInputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: @Composable (() -> Unit),
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    supportingText: @Composable (() -> Unit)? = null,
    /** 键盘内二次确认：两轮一致才回填；空值直接提交（视为清除密码）。 */
    requireConfirmation: Boolean = false
) {
    val context = LocalContext.current
    var keyboardVisible by remember { mutableStateOf(false) }
    var category by remember { mutableStateOf(PasswordKeyCategory.DIGIT) }
    var pageIndex by remember { mutableIntStateOf(0) }
    // 确认轮草稿：打开键盘时从 value 起步；第一轮暂存 firstEntry，第二轮比对
    var buffer by remember { mutableStateOf(value) }
    var confirmStage by remember { mutableIntStateOf(1) }
    var firstEntry by remember { mutableStateOf<String?>(null) }
    var mismatch by remember { mutableStateOf(false) }
    LaunchedEffect(keyboardVisible) {
        if (keyboardVisible) {
            buffer = value
            confirmStage = 1
            firstEntry = null
            mismatch = false
        }
    }
    val pages = remember(category) { categoryPages(category) }
    if (pageIndex > pages.lastIndex) pageIndex = 0
    val pageKeys = pages.getOrElse(pageIndex) { emptyList() }
    val editingText = if (requireConfirmation) buffer else value
    val edit: (String) -> Unit = { newText ->
        if (requireConfirmation) buffer = newText else onValueChange(newText)
    }
    fun maskOf(s: String) = when {
        s.isEmpty() -> ""
        s.length <= 36 -> "*".repeat(s.length)
        else -> "${"*".repeat(36)}…(${s.length})"
    }
    val maskedValue = maskOf(value)
    val maskedEditing = maskOf(editingText)

    Column(modifier = modifier) {
        Box(modifier = Modifier.padding(bottom = 6.dp)) {
            label()
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .clickable(enabled = enabled) { keyboardVisible = true }
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Text(
                text = if (maskedValue.isEmpty()) stringResource(R.string.browser_empty_placeholder) else maskedValue,
                color = if (maskedValue.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        supportingText?.invoke()
    }

    if (keyboardVisible) {
        Dialog(
            onDismissRequest = { keyboardVisible = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.BottomCenter
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(430.dp),
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(56.dp)
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(999.dp))
                                    .border(0.dp, Color.Transparent, RoundedCornerShape(999.dp))
                            )
                            Box(
                                modifier = Modifier
                                    .width(56.dp)
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(MaterialTheme.colorScheme.outlineVariant)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier.height(32.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    text = if (maskedEditing.isEmpty()) stringResource(R.string.browser_empty_placeholder) else maskedEditing,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (maskedEditing.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(
                                    onClick = {
                                        val pasted = readClipboardPassword(context)
                                        if (pasted == null) {
                                            Toast.makeText(context, context.getString(R.string.pwd_kb_clipboard_empty), Toast.LENGTH_SHORT).show()
                                        } else {
                                            edit(pasted)
                                            Toast.makeText(context, context.getString(R.string.pwd_kb_pasted), Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    enabled = enabled,
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(stringResource(R.string.pwd_kb_paste))
                                }
                                TextButton(
                                    onClick = {
                                        if (!requireConfirmation) {
                                            keyboardVisible = false
                                        } else if (confirmStage == 1) {
                                            // 空值直接提交（清除密码）；非空进入确认轮
                                            if (buffer.isEmpty()) {
                                                onValueChange("")
                                                keyboardVisible = false
                                            } else {
                                                firstEntry = buffer
                                                buffer = ""
                                                confirmStage = 2
                                                mismatch = false
                                            }
                                        } else if (buffer == firstEntry) {
                                            onValueChange(buffer)
                                            keyboardVisible = false
                                            confirmStage = 1
                                            firstEntry = null
                                        } else {
                                            mismatch = true
                                            confirmStage = 1
                                            buffer = ""
                                            firstEntry = null
                                        }
                                    },
                                    enabled = enabled
                                ) {
                                    Text(stringResource(R.string.pwd_kb_done))
                                }
                            }
                        }
                        if (requireConfirmation) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(18.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                val statusText = when {
                                    mismatch -> stringResource(R.string.pwd_kb_confirm_mismatch)
                                    confirmStage == 2 -> stringResource(R.string.pwd_kb_confirm_stage)
                                    else -> ""
                                }
                                if (statusText.isNotEmpty()) {
                                    Text(
                                        text = statusText,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (mismatch) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            OutlinedButton(
                                onClick = { category = PasswordKeyCategory.DIGIT; pageIndex = 0 },
                                enabled = enabled,
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp)
                            ) { Text(stringResource(R.string.pwd_kb_digits), style = MaterialTheme.typography.labelSmall) }
                            OutlinedButton(
                                onClick = { category = PasswordKeyCategory.UPPER; pageIndex = 0 },
                                enabled = enabled,
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp)
                            ) { Text(stringResource(R.string.pwd_kb_upper), style = MaterialTheme.typography.labelSmall) }
                            OutlinedButton(
                                onClick = { category = PasswordKeyCategory.LOWER; pageIndex = 0 },
                                enabled = enabled,
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp)
                            ) { Text(stringResource(R.string.pwd_kb_lower), style = MaterialTheme.typography.labelSmall) }
                            OutlinedButton(
                                onClick = { category = PasswordKeyCategory.SYMBOL; pageIndex = 0 },
                                enabled = enabled,
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp)
                            ) { Text(stringResource(R.string.pwd_kb_symbols), style = MaterialTheme.typography.labelSmall) }
                        }

                        if (pages.size > 1) {
                            Spacer(Modifier.height(2.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                pages.indices.forEach { idx ->
                                    OutlinedButton(
                                        onClick = { pageIndex = idx },
                                        enabled = enabled,
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = if (idx == pageIndex) stringResource(R.string.pwd_kb_subclass_active, idx + 1) else stringResource(R.string.pwd_kb_subclass, idx + 1),
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(4.dp))
                        PasswordGridPage(
                            keys = pageKeys,
                            enabled = enabled,
                            onPress = { ch -> edit(editingText + ch) }
                        )

                        Spacer(Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            OutlinedButton(
                                onClick = { edit(editingText + " ") },
                                enabled = enabled,
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp)
                            ) { Text(stringResource(R.string.pwd_kb_space), style = MaterialTheme.typography.labelSmall) }
                            OutlinedButton(
                                onClick = { if (editingText.isNotEmpty()) edit(editingText.dropLast(1)) },
                                enabled = enabled && editingText.isNotEmpty(),
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp)
                            ) { Text(stringResource(R.string.pwd_kb_backspace), style = MaterialTheme.typography.labelSmall) }
                            OutlinedButton(
                                onClick = { edit("") },
                                enabled = enabled && editingText.isNotEmpty(),
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp)
                            ) { Text(stringResource(R.string.pwd_kb_clear), style = MaterialTheme.typography.labelSmall) }
                        }
                    }
                }
            }
        }
    }
}
