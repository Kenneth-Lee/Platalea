package com.kenny.localmanager.ui

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
