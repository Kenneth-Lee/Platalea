package com.kenny.localmanager.ui

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.kenny.localmanager.file.DocumentFileModel
import com.kenny.localmanager.file.createFileWithBytes
import com.kenny.localmanager.file.deleteDocument
import com.kenny.localmanager.gpg.GpgHelper
import com.kenny.localmanager.gpg.findPublicKeyRing
import com.kenny.localmanager.gpg.loadPublicKeyRings
import com.kenny.localmanager.gpg.loadSecretKeyRings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Crypto 域（GPG / 密码保护文件）的工作流状态。
 *
 * 域范围：单文件与待处理列表的 GPG 加密/解密向导、.pass 文件的加密保护 / 查看 / 直接编辑。
 * 与 ArchiveReaderWorkflowState 相同的单槽模型：同一时刻至多一个请求，
 * 互斥性由类型系统保证，散落在宿主的十几个布尔/可空目标状态收敛于此。
 *
 * 边界：本类只管表单状态与结果快照；文件操作执行编排在 CryptoDialogs 的
 * 确认回调中（通过宿主注入的 scope / runWithProgress / onRefresh 完成）。
 */
internal class CryptoWorkflowState {
    /** 当前请求；null 表示无 crypto 向导。 */
    var request by mutableStateOf<CryptoRequest?>(null)

    /** 密码输入（GPG 对称密码 / 私钥口令 / .pass 查看与编辑口令共用，互斥单槽）。 */
    var password by mutableStateOf("")

    /** GPG 对称加密的确认密码。 */
    var passwordConfirm by mutableStateOf("")

    /** GPG 解密方式（首次检测，null = 尚未检测完）。 */
    var decryptMode by mutableStateOf<GpgDecryptUiMode?>(null)

    /** 是否已尝试过用缓存口令自动解密（GPG 私钥模式 / .pass 查看 / .pass 编辑各自使用）。 */
    var autoTried by mutableStateOf(false)

    /** GPG 公钥加密时选中的密钥 id；null = 使用对称密码。 */
    var encryptSelectedKeyId by mutableStateOf<Long?>(null)

    /** GPG 公钥加密对话框加载/执行中（同时驱动宿主顶部保存进度条）。 */
    var pubEncryptInProgress by mutableStateOf(false)

    /** 当前请求执行中（protect 确认 / 解密 / 加密 / .pass 口令验证共用）。 */
    var inProgress by mutableStateOf(false)

    /** .pass 查看结果（解密成功后进入全屏查看器）。 */
    var passContent by mutableStateOf<PassDecryptedContent?>(null)

    /** .pass 直接编辑状态（口令验证成功后进入全屏编辑器）。 */
    var passEdit by mutableStateOf<PassEditState?>(null)

    /** 打开一个新请求：重置全部表单状态后设置请求。 */
    fun open(request: CryptoRequest) {
        this.request = request
        password = ""
        passwordConfirm = ""
        decryptMode = null
        autoTried = false
        encryptSelectedKeyId = null
        pubEncryptInProgress = false
        inProgress = false
        passContent = null
        passEdit = null
    }

    /** 关闭当前请求：全量重置（结果槽一并清除）。 */
    fun close() {
        request = null
        password = ""
        passwordConfirm = ""
        decryptMode = null
        autoTried = false
        encryptSelectedKeyId = null
        pubEncryptInProgress = false
        inProgress = false
    }

    /** .pass 查看口令通过：关闭请求并进入查看结果。 */
    fun showPassContent(content: PassDecryptedContent) {
        request = null
        password = ""
        passContent = content
    }

    /** .pass 编辑口令通过：关闭请求并进入编辑器。 */
    fun startPassEdit(state: PassEditState) {
        request = null
        passEdit = state
    }

    /** 退出查看器。 */
    fun closePassContent() {
        passContent = null
    }

    /** 退出编辑器。 */
    fun closePassEdit() {
        passEdit = null
    }
}

@Composable
internal fun rememberCryptoWorkflow(): CryptoWorkflowState = remember { CryptoWorkflowState() }

/**
 * PassEdit 保存执行：以默认密钥对的公钥重新加密新内容并写回（先删旧文件再建新文件）。
 * 供宿主在 PassEditScreen 的 onSave 回调中调用（scope 由宿主持有）。
 */
internal suspend fun savePassEditReencrypted(
    context: Context,
    state: PassEditState,
    newBytes: ByteArray,
): Boolean = withContext(Dispatchers.IO) {
    val secRings = loadSecretKeyRings(context) ?: return@withContext false
    val defaultKeyId = secRings.iterator().asSequence().firstOrNull()?.publicKey?.keyID ?: return@withContext false
    val pubRings = loadPublicKeyRings(context) ?: return@withContext false
    val pubKeyRing = findPublicKeyRing(pubRings, defaultKeyId) ?: return@withContext false
    val encrypted = GpgHelper.encryptWithPublicKey(newBytes, pubKeyRing, state.fileModel.name) ?: return@withContext false
    try { context.contentResolver.deleteDocument(state.fileModel.uri) } catch (_: Exception) { }
    createFileWithBytes(
        context, Uri.parse(state.dirUri), state.treeUri, state.fileModel.name, "application/octet-stream", encrypted)
}

/** Crypto 域请求：pass 三入口 + GPG 操作（单文件/批量 × 加密/解密）。 */
internal sealed class CryptoRequest {
    /** .pass 加密保护：用默认密钥公钥加密目标文件为 .pass 并删除原文件。 */
    data class PassProtect(val target: DocumentFileModel) : CryptoRequest()

    /** .pass 查看：口令验证后进入全屏查看器。 */
    data class PassView(val target: DocumentFileModel) : CryptoRequest()

    /** .pass 直接编辑：口令验证后进入全屏编辑器。 */
    data class PassEdit(val target: DocumentFileModel, val dirUri: String) : CryptoRequest()

    /** GPG 加密/解密向导（单文件或待处理列表批量）。 */
    data class Gpg(val op: GpgOpState) : CryptoRequest()
}

/** GPG 操作目标：单文件或批量列表。 */
internal sealed class GpgOpState {
    abstract val isDecrypt: Boolean
    abstract val dirUri: String
    abstract val displayName: String
    data class Decrypt(val fileModel: DocumentFileModel, override val dirUri: String) : GpgOpState() {
        override val isDecrypt = true
        override val displayName get() = fileModel.name
    }
    data class Encrypt(val fileModel: DocumentFileModel, override val dirUri: String) : GpgOpState() {
        override val isDecrypt = false
        override val displayName get() = fileModel.name
    }
    data class BatchDecrypt(val list: List<DocumentFileModel>, override val dirUri: String) : GpgOpState() {
        override val isDecrypt = true
        override val displayName get() = list.size.toString()
    }
    data class BatchEncrypt(val list: List<DocumentFileModel>, override val dirUri: String) : GpgOpState() {
        override val isDecrypt = false
        override val displayName get() = list.size.toString()
    }
}

/** GPG 解密方式（决定密码框文案与口令传递策略）。 */
internal enum class GpgDecryptUiMode {
    SYMMETRIC,
    SECRET_KEY,
    MIXED
}

/** .pass 查看结果快照（内层文件名 + 解密字节）。 */
internal data class PassDecryptedContent(val innerFileName: String, val decryptedBytes: ByteArray)

/** .pass 编辑状态快照（口令验证通过后交给 PassEditScreen）。 */
internal data class PassEditState(
    val fileModel: DocumentFileModel,
    val dirUri: String,
    val treeUri: Uri?,
    val innerName: String,
    val decryptedBytes: ByteArray
) {
    override fun equals(other: Any?) = (other is PassEditState) && fileModel == other.fileModel && dirUri == other.dirUri && innerName == other.innerName && decryptedBytes.contentEquals(other.decryptedBytes)
    override fun hashCode() = 31 * (31 * fileModel.hashCode() + dirUri.hashCode()) + innerName.hashCode()
}
