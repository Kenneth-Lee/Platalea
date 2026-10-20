package com.kenny.localmanager.note

import android.content.Context
import android.net.Uri
import com.kenny.localmanager.file.createFileWithBytes
import com.kenny.localmanager.file.findChildByName
import com.kenny.localmanager.file.openInputStreamSafe
import com.kenny.localmanager.file.writeBytesFull
import com.kenny.localmanager.gpg.GpgHelper
import com.kenny.localmanager.gpg.findPublicKeyRing
import com.kenny.localmanager.gpg.loadPublicKeyRings
import com.kenny.localmanager.gpg.loadSecretKeyRings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import com.kenny.localmanager.quicknote.QuickNoteFileInfo

data class NoteStorageMessageSet(
    val missingDefaultSecretKey: String,
    val missingKeyPassword: String,
    val decryptFailed: String,
    val readFailed: String,
    val missingDefaultKeypair: String,
    val missingDefaultKey: String,
    val missingDefaultPublicKey: String,
    val encryptFailed: String,
    val saveFailed: String
)

private const val NOTE_DECRYPT_TIMEOUT_MS = 20_000L

private suspend fun decryptWithTimeout(block: () -> ByteArray?): ByteArray? {
    return withTimeoutOrNull(NOTE_DECRYPT_TIMEOUT_MS) {
        withContext(Dispatchers.IO) {
            runInterruptible {
                block()
            }
        }
    }
}

fun resolveOrCreateMarkdownNoteFile(
    context: Context,
    rootUri: Uri,
    plainFileName: String,
    encryptedFileName: String,
    mimeType: String = "text/markdown"
): QuickNoteFileInfo? {
    val gpgUri = findChildByName(context, rootUri, encryptedFileName)
    if (gpgUri != null) {
        return QuickNoteFileInfo(gpgUri, encryptedFileName, true)
    }
    val plainUri = findChildByName(context, rootUri, plainFileName)
    if (plainUri != null) {
        return QuickNoteFileInfo(plainUri, plainFileName, false)
    }
    val created = createFileWithBytes(
        context = context,
        targetParentUri = rootUri,
        treeUri = rootUri,
        fileName = plainFileName,
        mimeType = mimeType,
        bytes = ByteArray(0)
    )
    if (!created) return null
    val createdUri = findChildByName(context, rootUri, plainFileName) ?: return null
    return QuickNoteFileInfo(createdUri, plainFileName, false)
}

suspend fun readNoteRawText(
    context: Context,
    fileInfo: QuickNoteFileInfo,
    password: String?,
    messages: NoteStorageMessageSet
): Result<String> {
    val plainBytes = if (fileInfo.isEncrypted) {
        val encryptedBytes = context.contentResolver.openInputStreamSafe(fileInfo.uri)?.use { it.readBytes() }
            ?: return Result.failure(IllegalStateException(messages.readFailed))
        when (GpgHelper.detectEncryptedKind(encryptedBytes.inputStream())) {
            GpgHelper.GpgEncryptedKind.SYMMETRIC -> {
                val symmetricPassword = password?.toCharArray()
                if (symmetricPassword == null || symmetricPassword.isEmpty()) {
                    return Result.failure(IllegalStateException(messages.missingKeyPassword))
                }
                val decrypted = decryptWithTimeout {
                    GpgHelper.decryptSymmetric(encryptedBytes.inputStream(), symmetricPassword) { }
                }
                symmetricPassword.fill(' ')
                decrypted ?: return Result.failure(IllegalStateException(messages.decryptFailed))
            }
            GpgHelper.GpgEncryptedKind.PUBLIC_KEY,
            GpgHelper.GpgEncryptedKind.UNKNOWN -> {
                val secretKeys = loadSecretKeyRings(context)
                    ?: return Result.failure(IllegalStateException(messages.missingDefaultSecretKey))
                val passphrase = password?.toCharArray() ?: CharArray(0)
                val decrypted = decryptWithTimeout {
                    GpgHelper.decryptWithSecretKey(encryptedBytes.inputStream(), secretKeys, passphrase) { }
                }
                passphrase.fill(' ')
                if (decrypted == null && password == null) {
                    return Result.failure(IllegalStateException(messages.missingKeyPassword))
                }
                decrypted ?: return Result.failure(IllegalStateException(messages.decryptFailed))
            }
        }
    } else {
        context.contentResolver.openInputStreamSafe(fileInfo.uri)?.use { it.readBytes() }
            ?: return Result.failure(IllegalStateException(messages.readFailed))
    }
    return Result.success(String(plainBytes, Charsets.UTF_8))
}

suspend fun writeNoteRawText(
    context: Context,
    fileInfo: QuickNoteFileInfo,
    rawText: String,
    messages: NoteStorageMessageSet
): Result<Unit> {
    val outBytes = rawText.toByteArray(Charsets.UTF_8)
    val ok = if (fileInfo.isEncrypted) {
        val secretKeys = loadSecretKeyRings(context)
            ?: return Result.failure(IllegalStateException(messages.missingDefaultKeypair))
        val defaultKeyId = secretKeys.iterator().asSequence().firstOrNull()?.publicKey?.keyID
            ?: return Result.failure(IllegalStateException(messages.missingDefaultKey))
        val publicKeys = loadPublicKeyRings(context)
        val publicKeyRing = findPublicKeyRing(publicKeys, defaultKeyId)
            ?: return Result.failure(IllegalStateException(messages.missingDefaultPublicKey))
        val encrypted = GpgHelper.encryptWithPublicKey(outBytes, publicKeyRing, fileInfo.fileName)
            ?: return Result.failure(IllegalStateException(messages.encryptFailed))
        context.contentResolver.writeBytesFull(fileInfo.uri, encrypted)
    } else {
        context.contentResolver.writeBytesFull(fileInfo.uri, outBytes)
    }
    return if (ok) {
        Result.success(Unit)
    } else {
        Result.failure(IllegalStateException(messages.saveFailed))
    }
}
