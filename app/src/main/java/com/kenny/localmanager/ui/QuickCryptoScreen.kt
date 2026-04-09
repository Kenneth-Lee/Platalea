package com.kenny.localmanager.ui

import android.util.Base64
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kenny.localmanager.R
import com.kenny.localmanager.gpg.GpgHelper
import com.kenny.localmanager.gpg.GpgHelper.GpgEncryptedKind
import com.kenny.localmanager.gpg.SecretKeyPasswordCache
import com.kenny.localmanager.gpg.findPublicKeyRing
import com.kenny.localmanager.gpg.loadPublicKeyRings
import com.kenny.localmanager.gpg.loadSecretKeyRings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream

private data class QuickCryptoResult(
    val outputText: String,
    val message: String
)

private enum class QuickCryptoPendingAction {
    ENCRYPT_TO_CIPHERTEXT,
    DECRYPT_TO_PLAINTEXT
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickCryptoScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    var plainText by remember { mutableStateOf("") }
    var cipherText by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var statusIsError by remember { mutableStateOf(false) }
    var inProgress by remember { mutableStateOf(false) }
    var keyPasswordDialogVisible by remember { mutableStateOf(false) }
    var keyPassword by remember { mutableStateOf("") }
    var pendingOverwriteAction by remember { mutableStateOf<QuickCryptoPendingAction?>(null) }

    fun updateStatus(message: String, isError: Boolean) {
        statusMessage = message
        statusIsError = isError
    }

    fun readClipboardText(): String? {
        return clipboardManager?.primaryClip
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.coerceToText(context)
            ?.toString()
            ?.takeIf { it.isNotEmpty() }
    }

    fun pasteToPlainText() {
        val clipText = readClipboardText()
        if (clipText == null) {
            Toast.makeText(context, context.getString(R.string.quick_crypto_clipboard_empty), Toast.LENGTH_SHORT).show()
            return
        }
        plainText = clipText
    }

    fun pasteToCipherText() {
        val clipText = readClipboardText()
        if (clipText == null) {
            Toast.makeText(context, context.getString(R.string.quick_crypto_clipboard_empty), Toast.LENGTH_SHORT).show()
            return
        }
        cipherText = clipText
    }

    fun copyPlainText() {
        if (plainText.isBlank()) {
            Toast.makeText(context, context.getString(R.string.quick_crypto_plaintext_empty), Toast.LENGTH_SHORT).show()
            return
        }
        clipboardManager?.setPrimaryClip(
            ClipData.newPlainText(context.getString(R.string.quick_crypto_plaintext_clip_label), plainText)
        )
        Toast.makeText(context, context.getString(R.string.quick_crypto_plaintext_copied), Toast.LENGTH_SHORT).show()
    }

    fun copyCipherText() {
        if (cipherText.isBlank()) {
            Toast.makeText(context, context.getString(R.string.quick_crypto_output_empty), Toast.LENGTH_SHORT).show()
            return
        }
        clipboardManager?.setPrimaryClip(
            ClipData.newPlainText(context.getString(R.string.quick_crypto_clip_label), cipherText)
        )
        Toast.makeText(context, context.getString(R.string.quick_crypto_output_copied), Toast.LENGTH_SHORT).show()
    }

    fun runEncrypt() {
        val currentPlainText = plainText.trimEnd()
        if (currentPlainText.isBlank()) {
            updateStatus(context.getString(R.string.quick_crypto_input_required_encrypt), true)
            return
        }
        inProgress = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                encryptQuickCryptoText(context, currentPlainText)
            }
            inProgress = false
            result.onSuccess { value ->
                cipherText = value.outputText
                updateStatus(value.message, false)
            }.onFailure { throwable ->
                updateStatus(throwable.message ?: context.getString(R.string.quick_crypto_encrypt_failed), true)
            }
        }
    }

    fun requestEncrypt() {
        if (cipherText.isNotBlank()) {
            pendingOverwriteAction = QuickCryptoPendingAction.ENCRYPT_TO_CIPHERTEXT
        } else {
            runEncrypt()
        }
    }

    fun runDecrypt(password: CharArray?) {
        val currentCipherText = cipherText.trim()
        if (currentCipherText.isBlank()) {
            updateStatus(context.getString(R.string.quick_crypto_input_required_decrypt), true)
            return
        }
        inProgress = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                decryptQuickCryptoText(context, currentCipherText, password)
            }
            inProgress = false
            result.onSuccess { value ->
                if (password != null) {
                    SecretKeyPasswordCache.set(password)
                }
                plainText = value.outputText
                keyPassword = ""
                keyPasswordDialogVisible = false
                updateStatus(value.message, false)
            }.onFailure { throwable ->
                if (password == null && SecretKeyPasswordCache.get() != null) {
                    keyPasswordDialogVisible = true
                    updateStatus(context.getString(R.string.quick_crypto_cached_password_failed), true)
                } else {
                    updateStatus(throwable.message ?: context.getString(R.string.quick_crypto_decrypt_failed), true)
                }
            }
        }
    }

    fun requestDecrypt() {
        if (plainText.isNotBlank()) {
            pendingOverwriteAction = QuickCryptoPendingAction.DECRYPT_TO_PLAINTEXT
            return
        }
        val cachedPassword = SecretKeyPasswordCache.get()
        if (cachedPassword != null) {
            runDecrypt(cachedPassword)
        } else {
            keyPasswordDialogVisible = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(R.string.quick_crypto_title))
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
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .imePadding()
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = plainText,
                onValueChange = { plainText = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(136.dp),
                label = { Text(stringResource(R.string.quick_crypto_plaintext_label)) },
                enabled = !inProgress
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(onClick = { pasteToPlainText() }, enabled = !inProgress) {
                    Text(stringResource(R.string.quick_crypto_paste_plain))
                }
                TextButton(onClick = { copyPlainText() }, enabled = !inProgress) {
                    Text(stringResource(R.string.quick_crypto_copy_plain))
                }
                TextButton(
                    onClick = {
                        plainText = ""
                        if (statusMessage == context.getString(R.string.quick_crypto_decrypt_success)) {
                            statusMessage = null
                        }
                    },
                    enabled = !inProgress
                ) {
                    Text(stringResource(R.string.quick_crypto_clear_plain))
                }
                Button(
                    onClick = { requestEncrypt() },
                    enabled = !inProgress,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    if (inProgress) {
                        CircularProgressIndicator(modifier = Modifier.height(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(R.string.quick_crypto_action_encrypt_short))
                    }
                }
                Button(
                    onClick = { requestDecrypt() },
                    enabled = !inProgress,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    if (inProgress) {
                        CircularProgressIndicator(modifier = Modifier.height(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(R.string.quick_crypto_action_decrypt_short))
                    }
                }
            }

            statusMessage?.let { message ->
                Text(
                    text = message,
                    color = if (statusIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (statusIsError) FontWeight.Medium else FontWeight.Normal
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(onClick = { pasteToCipherText() }, enabled = !inProgress) {
                    Text(stringResource(R.string.quick_crypto_paste_ciphertext))
                }
                TextButton(onClick = { copyCipherText() }, enabled = !inProgress) {
                    Text(stringResource(R.string.quick_crypto_copy_output))
                }
                TextButton(
                    onClick = {
                        cipherText = ""
                        if (statusMessage == context.getString(R.string.quick_crypto_encrypt_success)) {
                            statusMessage = null
                        }
                    },
                    enabled = !inProgress
                ) {
                    Text(stringResource(R.string.quick_crypto_clear_ciphertext))
                }
            }

            OutlinedTextField(
                value = cipherText,
                onValueChange = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .height(136.dp),
                label = { Text(stringResource(R.string.quick_crypto_ciphertext_label)) },
                enabled = !inProgress,
                readOnly = true
            )
        }
    }

    pendingOverwriteAction?.let { action ->
        AlertDialog(
            onDismissRequest = { if (!inProgress) pendingOverwriteAction = null },
            title = { Text(stringResource(R.string.quick_crypto_overwrite_title)) },
            text = {
                Text(
                    when (action) {
                        QuickCryptoPendingAction.ENCRYPT_TO_CIPHERTEXT -> stringResource(R.string.quick_crypto_overwrite_ciphertext_confirm)
                        QuickCryptoPendingAction.DECRYPT_TO_PLAINTEXT -> stringResource(R.string.quick_crypto_overwrite_plaintext_confirm)
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingOverwriteAction = null
                        when (action) {
                            QuickCryptoPendingAction.ENCRYPT_TO_CIPHERTEXT -> runEncrypt()
                            QuickCryptoPendingAction.DECRYPT_TO_PLAINTEXT -> {
                                val cachedPassword = SecretKeyPasswordCache.get()
                                if (cachedPassword != null) {
                                    runDecrypt(cachedPassword)
                                } else {
                                    keyPasswordDialogVisible = true
                                }
                            }
                        }
                    },
                    enabled = !inProgress
                ) {
                    Text(stringResource(R.string.quick_crypto_overwrite_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingOverwriteAction = null }, enabled = !inProgress) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    if (keyPasswordDialogVisible) {
        GpgPasswordDialog(
            isDecrypt = true,
            fileName = context.getString(R.string.quick_crypto_password_dialog_file_name),
            password = keyPassword,
            passwordLabel = context.getString(R.string.quick_crypto_key_password_label),
            inProgress = inProgress,
            onPasswordChange = { if (!inProgress) keyPassword = it },
            onConfirm = { pwd -> runDecrypt(pwd.toCharArray()) },
            onDismiss = {
                if (!inProgress) {
                    keyPasswordDialogVisible = false
                    keyPassword = ""
                }
            }
        )
    }
}

private fun encryptQuickCryptoText(context: Context, plainText: String): Result<QuickCryptoResult> {
    val secretKeys = loadSecretKeyRings(context)
        ?: return Result.failure(IllegalStateException(context.getString(R.string.quick_crypto_missing_default_secret_key)))
    val defaultKeyId = secretKeys.iterator().asSequence().firstOrNull()?.publicKey?.keyID
        ?: return Result.failure(IllegalStateException(context.getString(R.string.quick_crypto_missing_default_key)))
    val publicKeys = loadPublicKeyRings(context)
        ?: return Result.failure(IllegalStateException(context.getString(R.string.quick_crypto_missing_default_public_key)))
    val publicKeyRing = findPublicKeyRing(publicKeys, defaultKeyId)
        ?: return Result.failure(IllegalStateException(context.getString(R.string.quick_crypto_missing_default_public_key)))
    val encrypted = GpgHelper.encryptWithPublicKeyBinary(
        plainText.toByteArray(Charsets.UTF_8),
        publicKeyRing,
        "quick-text.txt"
    ) ?: return Result.failure(IllegalStateException(context.getString(R.string.quick_crypto_encrypt_failed)))
    return Result.success(
        QuickCryptoResult(
            outputText = Base64.encodeToString(encrypted, Base64.NO_WRAP),
            message = context.getString(R.string.quick_crypto_encrypt_success)
        )
    )
}

private fun decryptQuickCryptoText(
    context: Context,
    cipherText: String,
    password: CharArray?
): Result<QuickCryptoResult> {
    val secretKeys = loadSecretKeyRings(context)
        ?: return Result.failure(IllegalStateException(context.getString(R.string.quick_crypto_missing_default_secret_key)))
    val effectivePassword = password
        ?: return Result.failure(IllegalStateException(context.getString(R.string.quick_crypto_missing_key_password)))
    val encryptedBytes = decodeQuickCryptoCipherText(cipherText)
        ?: return Result.failure(IllegalStateException(context.getString(R.string.quick_crypto_invalid_ciphertext)))
    return when (GpgHelper.detectEncryptedKind(ByteArrayInputStream(encryptedBytes))) {
        GpgEncryptedKind.SYMMETRIC -> Result.failure(IllegalStateException(context.getString(R.string.quick_crypto_not_default_key_ciphertext)))
        GpgEncryptedKind.UNKNOWN -> Result.failure(IllegalStateException(context.getString(R.string.quick_crypto_invalid_ciphertext)))
        GpgEncryptedKind.PUBLIC_KEY -> {
            val decrypted = GpgHelper.decryptWithSecretKey(
                ByteArrayInputStream(encryptedBytes),
                secretKeys,
                effectivePassword
            ) { }
                ?: return Result.failure(IllegalStateException(context.getString(R.string.quick_crypto_decrypt_failed_detail)))
            Result.success(
                QuickCryptoResult(
                    outputText = decrypted.toString(Charsets.UTF_8),
                    message = context.getString(R.string.quick_crypto_decrypt_success)
                )
            )
        }
    }
}

private fun decodeQuickCryptoCipherText(cipherText: String): ByteArray? {
    val normalized = cipherText.trim()
    if (normalized.isEmpty()) return null
    if (normalized.contains("-----BEGIN PGP MESSAGE-----")) {
        return normalized.toByteArray(Charsets.UTF_8)
    }
    return try {
        Base64.decode(normalized.replace(Regex("\\s+"), ""), Base64.DEFAULT)
    } catch (_: IllegalArgumentException) {
        null
    }
}