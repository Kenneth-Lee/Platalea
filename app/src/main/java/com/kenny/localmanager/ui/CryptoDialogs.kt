package com.kenny.localmanager.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.kenny.localmanager.R
import com.kenny.localmanager.file.DocumentFileModel
import com.kenny.localmanager.file.createFileWithBytes
import com.kenny.localmanager.gpg.GpgHelper
import com.kenny.localmanager.gpg.GpgHelper.GpgEncryptedKind
import com.kenny.localmanager.gpg.SecretKeyPasswordCache
import com.kenny.localmanager.gpg.findPublicKeyRing
import com.kenny.localmanager.gpg.listEncryptionPublicKeyRings
import com.kenny.localmanager.gpg.loadPublicKeyRings
import com.kenny.localmanager.gpg.loadSecretKeyRings
import com.kenny.localmanager.file.deleteDocument
import com.kenny.localmanager.file.openInputStreamSafe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/**
 * Crypto 域对话框：.pass 保护确认、.pass 查看/编辑口令框、GPG 单文件与批量加解密向导。
 *
 * 状态在 [CryptoWorkflowState]；文件操作执行编排平移自宿主，
 * 通过注入的 scope / runWithProgress / onRefresh / onBatchFinished 与宿主协作。
 */
@Composable
internal fun CryptoDialogs(
    workflow: CryptoWorkflowState,
    scope: CoroutineScope,
    /** 根目录 uri（tree uri 解析用）。 */
    rootUri: String?,
    /** 当前目录 uri（pass 保护写出到该目录）。 */
    currentDirUri: String,
    runWithProgress: suspend (String, Int?, suspend ((Int) -> Unit) -> Unit) -> Unit,
    onRefresh: () -> Unit,
    /** 待处理列表批量操作成功后的收尾（清列表并关闭面板）。 */
    onBatchFinished: () -> Unit
) {
    val context = LocalContext.current

        // ---- 密码保护：加密 md/rst -> .pass ----
        (workflow.request as? CryptoRequest.PassProtect)?.let { req ->
            val model = req.target
            AlertDialog(
                onDismissRequest = { if (!workflow.inProgress) workflow.close() },
                title = { Text(stringResource(R.string.context_pass_protect)) },
                text = { Text(stringResource(R.string.browser_pass_protect_confirm, model.name, model.name)) },
                confirmButton = {
                    Button(
                        onClick = {
                            workflow.inProgress = true
                            val ctx = context
                            val dirUri = normalizeContentUriString(currentDirUri)
                            val treeUri = rootUri?.let { Uri.parse(normalizeContentUriString(it)) }
                            scope.launch {
                                val ok = withContext(Dispatchers.IO) {
                                    val secRings = loadSecretKeyRings(ctx)
                                    if (secRings == null) {
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(ctx, ctx.getString(R.string.browser_no_default_key_generate), Toast.LENGTH_LONG).show()
                                        }
                                        return@withContext false
                                    }
                                    val defaultKeyId = secRings.iterator().asSequence().firstOrNull()?.publicKey?.keyID
                                    if (defaultKeyId == null) {
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(ctx, ctx.getString(R.string.browser_no_default_key), Toast.LENGTH_LONG).show()
                                        }
                                        return@withContext false
                                    }
                                    val pubRings = loadPublicKeyRings(ctx)
                                    val pubKeyRing = findPublicKeyRing(pubRings, defaultKeyId)
                                    if (pubKeyRing == null) {
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(ctx, ctx.getString(R.string.browser_no_default_public_key), Toast.LENGTH_LONG).show()
                                        }
                                        return@withContext false
                                    }
                                    val plain = ctx.contentResolver.openInputStreamSafe(model.uri)?.use { it.readBytes() }
                                        ?: return@withContext false
                                    val encrypted = GpgHelper.encryptWithPublicKey(plain, pubKeyRing, model.name)
                                        ?: return@withContext false
                                    val outName = model.name + ".pass"
                                    val created = createFileWithBytes(ctx, Uri.parse(dirUri), treeUri, outName, "application/octet-stream", encrypted)
                                    if (created) {
                                        ctx.contentResolver.deleteDocument(model.uri)
                                    }
                                    created
                                }
                                workflow.inProgress = false
                                workflow.close()
                                if (ok) {
                                    Toast.makeText(ctx, ctx.getString(R.string.browser_encrypted_as_pass, model.name), Toast.LENGTH_SHORT).show()
                                    onRefresh()
                                } else {
                                    Toast.makeText(ctx, ctx.getString(R.string.browser_pass_protect_failed), Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        enabled = !workflow.inProgress
                    ) { Text(if (workflow.inProgress) stringResource(R.string.browser_encrypting) else stringResource(R.string.common_ok)) }
                },
                dismissButton = {
                    TextButton(
                        onClick = { workflow.close() },
                        enabled = !workflow.inProgress
                    ) { Text(stringResource(R.string.common_cancel)) }
                }
            )
        }

        // ---- 密码保护：查看 .pass 文件 ----
        (workflow.request as? CryptoRequest.PassView)?.let { req ->
            val model = req.target
            val secRings = loadSecretKeyRings(context)
            if (secRings == null) {
                AlertDialog(
                    onDismissRequest = { workflow.close() },
                    title = { Text(stringResource(R.string.context_view_password)) },
                    text = { Text(stringResource(R.string.browser_no_default_secret_key)) },
                    confirmButton = {
                        Button(onClick = { workflow.close() }) { Text(stringResource(R.string.common_ok)) }
                    }
                )
            } else {
                LaunchedEffect(model) {
                    if (workflow.autoTried) return@LaunchedEffect
                    val cached = SecretKeyPasswordCache.get() ?: run { workflow.autoTried = true; return@LaunchedEffect }
                    workflow.autoTried = true
                    workflow.inProgress = true
                    val decrypted = withContext(Dispatchers.IO) {
                        val rings = loadSecretKeyRings(context) ?: return@withContext null
                        context.contentResolver.openInputStreamSafe(model.uri)?.use { input ->
                            GpgHelper.decryptWithSecretKey(input, rings, cached) { _ -> }
                        }
                    }
                    workflow.inProgress = false
                    if (decrypted != null) {
                        workflow.close()
                        val innerName = model.name.removeSuffix(".pass").removeSuffix(".PASS")
                        workflow.showPassContent(PassDecryptedContent(innerName, decrypted))
                    } else {
                        Toast.makeText(context, context.getString(R.string.browser_decrypt_check_password), Toast.LENGTH_SHORT).show()
                    }
                }
                if (SecretKeyPasswordCache.get() == null || workflow.autoTried) {
                GpgPasswordDialog(
                    isDecrypt = true,
                    fileName = model.name,
                    password = workflow.password,
                    passwordLabel = context.getString(R.string.browser_key_password_label),
                    inProgress = workflow.inProgress,
                    onPasswordChange = { if (!workflow.inProgress) workflow.password = it },
                    onConfirm = { pwd ->
                        if (workflow.inProgress) return@GpgPasswordDialog
                        workflow.inProgress = true
                        val ctx = context
                        scope.launch {
                            val decrypted = withContext(Dispatchers.IO) {
                                val rings = loadSecretKeyRings(ctx) ?: return@withContext null
                                ctx.contentResolver.openInputStreamSafe(model.uri)?.use { input ->
                                    GpgHelper.decryptWithSecretKey(
                                        input, rings, pwd.toCharArray()
                                    ) { _ -> }
                                }
                            }
                            workflow.inProgress = false
                            if (decrypted != null) {
                                workflow.close()
                                workflow.password = ""
                                // 根据内层扩展名确定渲染方式
                                val innerName = model.name.removeSuffix(".pass").removeSuffix(".PASS")
                                workflow.showPassContent(PassDecryptedContent(innerName, decrypted))
                            } else {
                                Toast.makeText(ctx, ctx.getString(R.string.browser_decrypt_check_password), Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    onDismiss = {
                        if (!workflow.inProgress) {
                            workflow.close()
                            workflow.password = ""
                        }
                    }
                )
                }
            }
        }

        // ---- 密码保护：直接编辑 .pass 文件（解密后进入编辑界面） ----
        (workflow.request as? CryptoRequest.PassEdit)?.let { req ->
            val model = req.target
            val dirUri = req.dirUri
            val secRings = loadSecretKeyRings(context)
            if (secRings == null) {
                AlertDialog(
                    onDismissRequest = { workflow.close() },
                    title = { Text(stringResource(R.string.context_edit_directly)) },
                    text = { Text(stringResource(R.string.browser_no_default_secret_key)) },
                    confirmButton = {
                        Button(onClick = { workflow.close() }) { Text(stringResource(R.string.common_ok)) }
                    }
                )
            } else {
                LaunchedEffect(model) {
                    if (workflow.autoTried) return@LaunchedEffect
                    val cached = SecretKeyPasswordCache.get() ?: run { workflow.autoTried = true; return@LaunchedEffect }
                    workflow.autoTried = true
                    workflow.inProgress = true
                    val decrypted = withContext(Dispatchers.IO) {
                        val rings = loadSecretKeyRings(context) ?: return@withContext null
                        context.contentResolver.openInputStreamSafe(model.uri)?.use { input ->
                            GpgHelper.decryptWithSecretKey(input, rings, cached) { _ -> }
                        }
                    }
                    workflow.inProgress = false
                    if (decrypted != null) {
                        workflow.close()
                        val innerName = model.name.removeSuffix(".pass").removeSuffix(".PASS")
                        val tree = rootUri?.let { Uri.parse(normalizeContentUriString(it)) }
                        workflow.startPassEdit(PassEditState(model, dirUri, tree, innerName, decrypted))
                    } else {
                        Toast.makeText(context, context.getString(R.string.browser_decrypt_check_password), Toast.LENGTH_SHORT).show()
                    }
                }
                if (SecretKeyPasswordCache.get() == null || workflow.autoTried) {
                GpgPasswordDialog(
                    isDecrypt = true,
                    fileName = model.name,
                    password = workflow.password,
                    passwordLabel = context.getString(R.string.browser_key_password_label),
                    inProgress = workflow.inProgress,
                    onPasswordChange = { if (!workflow.inProgress) workflow.password = it },
                    onConfirm = { pwd ->
                        if (workflow.inProgress) return@GpgPasswordDialog
                        workflow.inProgress = true
                        val ctx = context
                        scope.launch {
                            val decrypted = withContext(Dispatchers.IO) {
                                val rings = loadSecretKeyRings(ctx) ?: return@withContext null
                                ctx.contentResolver.openInputStreamSafe(model.uri)?.use { input ->
                                    GpgHelper.decryptWithSecretKey(
                                        input, rings, pwd.toCharArray()
                                    ) { _ -> }
                                }
                            }
                            workflow.inProgress = false
                            if (decrypted != null) {
                                workflow.close()
                                val innerName = model.name.removeSuffix(".pass").removeSuffix(".PASS")
                                val tree = rootUri?.let { Uri.parse(normalizeContentUriString(it)) }
                                workflow.startPassEdit(PassEditState(model, dirUri, tree, innerName, decrypted))
                            } else {
                                Toast.makeText(ctx, ctx.getString(R.string.browser_decrypt_check_password), Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    onDismiss = {
                        if (!workflow.inProgress) {
                            workflow.close()
                        }
                    }
                )
                }
            }
        }
        (workflow.request as? CryptoRequest.Gpg)?.let { gpgReq ->
            val op = gpgReq.op
            val ctx = context
            val dirUri = normalizeContentUriString(op.dirUri)
            val treeUri = rootUri?.let { Uri.parse(normalizeContentUriString(it)) }

            if (op.isDecrypt) {
                LaunchedEffect(op) {
                    workflow.decryptMode = detectGpgDecryptModeForOp(context, op)
                    workflow.autoTried = SecretKeyPasswordCache.get() == null
                }

                if (workflow.decryptMode == GpgDecryptUiMode.SECRET_KEY && !workflow.autoTried && SecretKeyPasswordCache.get() != null) {
                    LaunchedEffect(op, workflow.decryptMode) {
                        workflow.autoTried = true
                        workflow.inProgress = true
                        val autoKeyPass = SecretKeyPasswordCache.get() ?: return@LaunchedEffect
                        val ok = when (op) {
                            is GpgOpState.BatchDecrypt -> {
                                var allOk = true
                                runWithProgress(context.getString(R.string.quick_crypto_action_decrypt_short), op.list.size) { setProgress ->
                                    op.list.forEachIndexed { index, fileModel ->
                                        if (!decryptGpgFileToDir(context, fileModel, dirUri, treeUri, symmetricPassword = null, keyPassphrase = autoKeyPass)) {
                                            allOk = false
                                        }
                                        setProgress(index + 1)
                                    }
                                }
                                allOk
                            }
                            is GpgOpState.Decrypt -> decryptGpgFileToDir(context, op.fileModel, dirUri, treeUri, symmetricPassword = null, keyPassphrase = autoKeyPass)
                            else -> false
                        }
                        workflow.inProgress = false
                        if (ok) {
                            workflow.close()
                            onRefresh()
                            if (op is GpgOpState.BatchDecrypt) {
                                onBatchFinished()
                                Toast.makeText(ctx, ctx.getString(R.string.browser_decrypted_count, op.list.size), Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(ctx, ctx.getString(R.string.browser_decrypt_complete), Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }

                val decryptMode = workflow.decryptMode
                if (decryptMode != null && (decryptMode != GpgDecryptUiMode.SECRET_KEY || workflow.autoTried)) {
                    GpgPasswordDialog(
                        isDecrypt = true,
                        fileName = op.displayName,
                        password = workflow.password,
                        passwordLabel = when (decryptMode) {
                            GpgDecryptUiMode.SECRET_KEY -> context.getString(R.string.browser_key_password_label)
                            GpgDecryptUiMode.MIXED -> context.getString(R.string.browser_password_gpg_auto)
                            GpgDecryptUiMode.SYMMETRIC -> context.getString(R.string.browser_enter_password)
                        },
                        inProgress = workflow.inProgress,
                        onPasswordChange = { if (!workflow.inProgress) workflow.password = it },
                        onConfirm = { pwd ->
                            if (workflow.inProgress) return@GpgPasswordDialog
                            workflow.inProgress = true
                            scope.launch {
                                try {
                                    val pwdChars = pwd.toCharArray()
                                    val ok = when (op) {
                                        is GpgOpState.BatchDecrypt -> {
                                            runWithProgress(context.getString(R.string.quick_crypto_action_decrypt_short), op.list.size) { setProgress ->
                                                op.list.forEachIndexed { index, fileModel ->
                                                    decryptGpgFileToDir(
                                                        context,
                                                        fileModel,
                                                        dirUri,
                                                        treeUri,
                                                        symmetricPassword = if (decryptMode == GpgDecryptUiMode.SECRET_KEY) null else pwdChars,
                                                        keyPassphrase = when (decryptMode) {
                                                            GpgDecryptUiMode.SYMMETRIC -> null
                                                            GpgDecryptUiMode.SECRET_KEY -> pwdChars
                                                            GpgDecryptUiMode.MIXED -> SecretKeyPasswordCache.get() ?: pwdChars
                                                        }
                                                    )
                                                    setProgress(index + 1)
                                                }
                                            }
                                            true
                                        }
                                        is GpgOpState.Decrypt -> decryptGpgFileToDir(
                                            context,
                                            op.fileModel,
                                            dirUri,
                                            treeUri,
                                            symmetricPassword = if (decryptMode == GpgDecryptUiMode.SECRET_KEY) null else pwdChars,
                                            keyPassphrase = when (decryptMode) {
                                                GpgDecryptUiMode.SYMMETRIC -> null
                                                GpgDecryptUiMode.SECRET_KEY -> pwdChars
                                                GpgDecryptUiMode.MIXED -> SecretKeyPasswordCache.get() ?: pwdChars
                                            }
                                        )
                                        else -> false
                                    }
                                    if (ok) {
                                        if (op is GpgOpState.BatchDecrypt) {
                                            onBatchFinished()
                                            Toast.makeText(ctx, ctx.getString(R.string.browser_decrypted_count, op.list.size), Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(ctx, ctx.getString(R.string.browser_decrypt_complete), Toast.LENGTH_SHORT).show()
                                        }
                                        workflow.close()
                                        onRefresh()
                                    } else {
                                        Toast.makeText(ctx, ctx.getString(R.string.browser_decrypt_failed), Toast.LENGTH_LONG).show()
                                    }
                                } finally {
                                    workflow.inProgress = false
                                }
                            }
                        },
                        onDismiss = { if (!workflow.inProgress) workflow.close() }
                    )
                }
            } else {
                val pubRings = loadPublicKeyRings(context)
                val keys = listEncryptionPublicKeyRings(pubRings)
                GpgEncryptDialog(
                    fileName = op.displayName,
                    keys = keys,
                    selectedKeyId = workflow.encryptSelectedKeyId,
                    password = workflow.password,
                    confirmPassword = workflow.passwordConfirm,
                    inProgress = workflow.inProgress || workflow.pubEncryptInProgress,
                    onSelectPassword = {
                        if (!workflow.inProgress && !workflow.pubEncryptInProgress) {
                            workflow.encryptSelectedKeyId = null
                            workflow.passwordConfirm = ""
                        }
                    },
                    onSelectKey = { keyId ->
                        if (!workflow.inProgress && !workflow.pubEncryptInProgress) {
                            workflow.encryptSelectedKeyId = keyId
                            workflow.passwordConfirm = ""
                        }
                    },
                    onPasswordChange = {
                        if (!workflow.inProgress && !workflow.pubEncryptInProgress) {
                            workflow.password = it
                            workflow.passwordConfirm = ""
                            if (it.isNotEmpty()) workflow.encryptSelectedKeyId = null
                        }
                    },
                    onConfirmPasswordChange = {
                        if (!workflow.inProgress && !workflow.pubEncryptInProgress) {
                            workflow.passwordConfirm = it
                        }
                    },
                    onConfirm = { password, keyId ->
                        if (workflow.inProgress || workflow.pubEncryptInProgress) return@GpgEncryptDialog
                        if (keyId == null && password.isBlank()) return@GpgEncryptDialog
                        workflow.inProgress = keyId == null
                        workflow.pubEncryptInProgress = keyId != null
                        scope.launch {
                            try {
                                val pwdChars = password.toCharArray()
                                val ok = when (op) {
                                    is GpgOpState.BatchEncrypt -> {
                                        runWithProgress(if (keyId != null) context.getString(R.string.browser_op_public_key_encrypt) else context.getString(R.string.quick_crypto_action_encrypt_short), op.list.size) { setProgress ->
                                            op.list.forEachIndexed { index, fileModel ->
                                                encryptGpgFileToDir(
                                                    context,
                                                    fileModel,
                                                    dirUri,
                                                    treeUri,
                                                    symmetricPassword = if (keyId == null) pwdChars else null,
                                                    publicKeyId = keyId
                                                )
                                                setProgress(index + 1)
                                            }
                                        }
                                        true
                                    }
                                    is GpgOpState.Encrypt -> encryptGpgFileToDir(
                                        context,
                                        op.fileModel,
                                        dirUri,
                                        treeUri,
                                        symmetricPassword = if (keyId == null) pwdChars else null,
                                        publicKeyId = keyId
                                    )
                                    else -> false
                                }
                                if (ok) {
                                    if (op is GpgOpState.BatchEncrypt) {
                                        onBatchFinished()
                                        Toast.makeText(ctx, if (keyId != null) ctx.getString(R.string.browser_public_key_encrypted_count, op.list.size) else ctx.getString(R.string.browser_encrypted_count, op.list.size), Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(ctx, if (keyId != null) ctx.getString(R.string.browser_op_public_key_encrypt) else ctx.getString(R.string.browser_encrypt_complete), Toast.LENGTH_SHORT).show()
                                    }
                                    workflow.close()
                                    onRefresh()
                                } else {
                                    Toast.makeText(ctx, ctx.getString(R.string.browser_encrypt_failed), Toast.LENGTH_LONG).show()
                                }
                            } finally {
                                workflow.inProgress = false
                                workflow.pubEncryptInProgress = false
                            }
                        }
                    },
                    onDismiss = { if (!workflow.inProgress && !workflow.pubEncryptInProgress) workflow.close() }
                )
            }
        }
}

internal suspend fun detectGpgDecryptModeForOp(context: Context, op: GpgOpState): GpgDecryptUiMode = withContext(Dispatchers.IO) {
    val files = when (op) {
        is GpgOpState.Decrypt -> listOf(op.fileModel)
        is GpgOpState.BatchDecrypt -> op.list
        else -> emptyList()
    }
    var hasSymmetric = false
    var hasPublicKey = false
    files.forEach { fileModel ->
        val kind = context.contentResolver.openInputStreamSafe(fileModel.uri)?.use { input ->
            GpgHelper.detectEncryptedKind(input)
        } ?: GpgEncryptedKind.UNKNOWN
        when (kind) {
            GpgEncryptedKind.PUBLIC_KEY -> hasPublicKey = true
            GpgEncryptedKind.SYMMETRIC, GpgEncryptedKind.UNKNOWN -> hasSymmetric = true
        }
    }
    when {
        hasSymmetric && hasPublicKey -> GpgDecryptUiMode.MIXED
        hasPublicKey -> GpgDecryptUiMode.SECRET_KEY
        else -> GpgDecryptUiMode.SYMMETRIC
    }
}

internal suspend fun decryptGpgFileToDir(
    context: Context,
    fileModel: DocumentFileModel,
    targetDirUri: String,
    treeUri: Uri?,
    symmetricPassword: CharArray?,
    keyPassphrase: CharArray?
): Boolean = withContext(Dispatchers.IO) {
    val encBytes = context.contentResolver.openInputStreamSafe(fileModel.uri)?.use { it.readBytes() } ?: return@withContext false
    val kind = GpgHelper.detectEncryptedKind(java.io.ByteArrayInputStream(encBytes))
    val decrypted = when (kind) {
        GpgEncryptedKind.PUBLIC_KEY -> {
            val secretRings = loadSecretKeyRings(context) ?: return@withContext false
            val effectiveKeyPass = keyPassphrase ?: SecretKeyPasswordCache.get() ?: CharArray(0)
            GpgHelper.decryptWithSecretKey(
                java.io.ByteArrayInputStream(encBytes),
                secretRings,
                effectiveKeyPass
            ) { }
        }
        GpgEncryptedKind.SYMMETRIC, GpgEncryptedKind.UNKNOWN -> {
            val pwd = symmetricPassword ?: return@withContext false
            GpgHelper.decryptSymmetric(
                java.io.ByteArrayInputStream(encBytes),
                pwd
            ) { }
        }
    } ?: return@withContext false
    val outName = fileModel.name.removeSuffix(".gpg").ifEmpty { fileModel.name + ".dec" }
    createFileWithBytes(context, Uri.parse(targetDirUri), treeUri, outName, "application/octet-stream", decrypted)
}

internal suspend fun encryptGpgFileToDir(
    context: Context,
    fileModel: DocumentFileModel,
    targetDirUri: String,
    treeUri: Uri?,
    symmetricPassword: CharArray?,
    publicKeyId: Long?
): Boolean = withContext(Dispatchers.IO) {
    val plain = context.contentResolver.openInputStreamSafe(fileModel.uri)?.use { it.readBytes() } ?: return@withContext false
    if (publicKeyId != null) {
        val pubRings = loadPublicKeyRings(context) ?: return@withContext false
        val pubKeyRing = findPublicKeyRing(pubRings, publicKeyId) ?: return@withContext false
        val keyDesc = listEncryptionPublicKeyRings(pubRings).find { it.first == publicKeyId }?.second.orEmpty()
        val safeName = keyDesc.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(40).trim()
        val baseName = fileModel.name.removeSuffix(".gpg")
        val outName = if (safeName.isNotEmpty()) "${baseName}_${safeName}.gpg" else "${baseName}.gpg"
        val encrypted = GpgHelper.encryptWithPublicKey(plain, pubKeyRing, fileModel.name) ?: return@withContext false
        createFileWithBytes(context, Uri.parse(targetDirUri), treeUri, outName, "application/octet-stream", encrypted)
    } else {
        val pwd = symmetricPassword ?: return@withContext false
        val encrypted = GpgHelper.encryptSymmetric(plain, pwd, fileModel.name) { } ?: return@withContext false
        createFileWithBytes(context, Uri.parse(targetDirUri), treeUri, fileModel.name + ".gpg", "application/octet-stream", encrypted)
    }
}

/** GPG 口令输入弹窗（加/解密单文件，多屏共用）。状态在调用方。 */
@Composable
internal fun GpgPasswordDialog(
    isDecrypt: Boolean,
    fileName: String,
    password: String,
    passwordLabel: String? = null,
    inProgress: Boolean = false,
    onPasswordChange: (String) -> Unit,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val resolvedPasswordLabel = passwordLabel ?: stringResource(R.string.browser_enter_password)
    Dialog(onDismissRequest = { if (!inProgress) onDismiss() }) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(Modifier.padding(24.dp)) {
                Text(
                    if (isDecrypt) stringResource(R.string.context_gpg_decrypt) else stringResource(R.string.context_gpg_encrypt),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(fileName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                ReliablePasswordInputField(
                    value = password,
                    onValueChange = onPasswordChange,
                    label = { Text(resolvedPasswordLabel) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !inProgress
                )
                if (inProgress) {
                    Spacer(Modifier.height(16.dp))
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (isDecrypt) stringResource(R.string.browser_decrypting) else stringResource(R.string.browser_encrypting),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(24.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss, enabled = !inProgress) { Text(stringResource(R.string.common_cancel)) }
                    Button(
                        onClick = { onConfirm(password) },
                        enabled = !inProgress
                    ) { Text(stringResource(R.string.common_ok)) }
                }
            }
        }
    }
}


/** GPG 加密方式弹窗：对称口令或公钥；有私钥时解密可选私钥。 */
@Composable
internal fun GpgEncryptDialog(
    fileName: String,
    keys: List<Pair<Long, String>>,
    selectedKeyId: Long?,
    password: String,
    confirmPassword: String,
    inProgress: Boolean = false,
    onSelectPassword: () -> Unit,
    onSelectKey: (Long) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onConfirm: (String, Long?) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val passwordLabelText = stringResource(R.string.browser_enter_password)
    val useSymmetric = selectedKeyId == null
    val symmetricPasswordReady = isRequiredPasswordConfirmed(password, confirmPassword)
    Dialog(onDismissRequest = { if (!inProgress) onDismiss() }) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(Modifier.padding(24.dp)) {
                Text(
                    context.getString(R.string.context_gpg_encrypt),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(fileName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !inProgress) { onSelectPassword() },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.material3.RadioButton(
                        selected = selectedKeyId == null,
                        onClick = if (inProgress) null else onSelectPassword
                    )
                    Text(stringResource(R.string.browser_gpg_use_symmetric_encrypt), style = MaterialTheme.typography.bodyMedium)
                }
                if (useSymmetric) {
                    Spacer(Modifier.height(12.dp))
                    PasswordConfirmationFields(
                        password = password,
                        confirmPassword = confirmPassword,
                        onPasswordChange = onPasswordChange,
                        onConfirmPasswordChange = onConfirmPasswordChange,
                        passwordLabel = passwordLabelText,
                        confirmLabel = context.getString(R.string.browser_password_confirm_label),
                        enabled = !inProgress,
                        allowBlank = false
                    )
                } else {
                    ReliablePasswordInputField(
                        value = password,
                        onValueChange = onPasswordChange,
                        label = { Text(stringResource(R.string.browser_enter_password)) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !inProgress
                    )
                }
                if (keys.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.browser_gpg_or_select_public_key), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(8.dp))
                    keys.forEach { (keyId, desc) ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !inProgress) { onSelectKey(keyId) },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.material3.RadioButton(
                                selected = selectedKeyId == keyId,
                                onClick = if (inProgress) null else { { onSelectKey(keyId) } }
                            )
                            Text(desc, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
                if (inProgress) {
                    Spacer(Modifier.height(16.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.browser_encrypting), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(24.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss, enabled = !inProgress) { Text(stringResource(R.string.common_cancel)) }
                    Button(
                        onClick = { onConfirm(password, selectedKeyId) },
                        enabled = !inProgress && (selectedKeyId != null || symmetricPasswordReady)
                    ) { Text(stringResource(R.string.common_ok)) }
                }
            }
        }
    }
}


/** GPG 解密方式选择（口令/私钥）。 */
@Composable
internal fun GpgMethodDialog(
    isDecrypt: Boolean,
    fileName: String,
    hasPublicKeys: Boolean,
    hasSecretKeys: Boolean,
    onSymmetric: () -> Unit,
    onPublicKey: () -> Unit,
    onSecretKey: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(Modifier.padding(24.dp)) {
                Text(
                    if (isDecrypt) context.getString(R.string.context_gpg_decrypt) else context.getString(R.string.context_gpg_encrypt),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(fileName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                if (isDecrypt) {
                    Button(onClick = onSymmetric, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.browser_gpg_password_decrypt)) }
                    if (hasSecretKeys) {
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = onSecretKey, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.browser_gpg_secret_key_decrypt)) }
                    }
                } else {
                    Button(onClick = onSymmetric, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.browser_gpg_symmetric_encrypt)) }
                    if (hasPublicKeys) {
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = onPublicKey, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.browser_op_public_key_encrypt)) }
                    }
                }
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
            }
        }
    }
}


/** GPG 公钥选择弹窗。 */
@Composable
internal fun GpgPublicKeyPickerDialog(
    keys: List<Pair<Long, String>>,
    fileName: String,
    onConfirm: (keyId: Long, keyDesc: String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedKeyId by remember { mutableStateOf<Long?>(keys.firstOrNull()?.first) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(Modifier.padding(24.dp)) {
                Text(stringResource(R.string.browser_op_public_key_encrypt), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                Text(fileName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                keys.forEach { (keyId, desc) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { selectedKeyId = keyId },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.RadioButton(
                            selected = selectedKeyId == keyId,
                            onClick = { selectedKeyId = keyId }
                        )
                        Text(desc, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp))
                    }
                }
                Spacer(Modifier.height(24.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
                    Button(onClick = {
                        val kid = selectedKeyId
                        val desc = keys.find { it.first == kid }?.second ?: ""
                        if (kid != null) onConfirm(kid, desc)
                    }) { Text(stringResource(R.string.quick_crypto_action_encrypt_short)) }
                }
            }
        }
    }
}

/** 直接编辑 .pass 文件：解密后显示可编辑文本，存盘时若已修改则重新加密写回。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PassEditScreen(
    fileName: String,
    initialDecryptedBytes: ByteArray,
    onSave: (ByteArray) -> Unit,
    onBack: () -> Unit
) {
    val scrollState = rememberScrollState()
    var textContent by remember(initialDecryptedBytes) {
        mutableStateOf(initialDecryptedBytes.decodeToString())
    }
    var saveInProgress by remember { mutableStateOf(false) }

    BackHandler { if (!saveInProgress) onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(fileName, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = { if (!saveInProgress) onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                    if (saveInProgress) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    TextButton(
                        onClick = {
                            if (saveInProgress) return@TextButton
                            val newBytes = textContent.encodeToByteArray()
                            if (newBytes.contentEquals(initialDecryptedBytes)) {
                                onBack()
                            } else {
                                saveInProgress = true
                                onSave(newBytes)
                            }
                        },
                        enabled = !saveInProgress
                    ) { Text(stringResource(R.string.common_save)) }
                }
            )
        }
    ) { padding ->
        OutlinedTextField(
            value = textContent,
            onValueChange = { textContent = it },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState),
            minLines = 20,
            maxLines = Int.MAX_VALUE
        )
    }
}
