package com.kenny.localmanager.file

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.security.spec.KeySpec
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

private const val EXTENSION = ".qx"
private const val HEADER_SIZE = 4096
private const val KDF_ITERATIONS = 1000  // 快速混淆仅需轻量拉伸，10000 次在大文件时明显卡顿
private val FIXED_SALT = "LocalManagerQuickObfuscation".toByteArray(Charsets.UTF_8)

/** 快速混淆使用的扩展名 */
val QUICK_OBFUSCATE_EXTENSION: String get() = EXTENSION

/** 判断文件是否已被快速混淆（根据扩展名） */
fun isQuickObfuscatedFileName(name: String): Boolean = name.endsWith(EXTENSION)

/** 去掉 .qx 扩展名得到原始文件名 */
fun stripQuickObfuscateExtension(name: String): String =
    if (name.endsWith(EXTENSION)) name.dropLast(EXTENSION.length) else name

/** 添加 .qx 扩展名 */
fun addQuickObfuscateExtension(name: String): String =
    if (name.endsWith(EXTENSION)) name else "$name$EXTENSION"

private fun deriveKey(password: CharArray, outputBytes: Int = HEADER_SIZE): ByteArray {
    val keySpec: KeySpec = PBEKeySpec(
        password,
        FIXED_SALT,
        KDF_ITERATIONS,
        outputBytes * 8
    )
    return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(keySpec).encoded
}

private fun xorInPlace(data: ByteArray, key: ByteArray) {
    require(data.size <= key.size)
    for (i in data.indices) {
        data[i] = (data[i].toInt() xor key[i].toInt()).toByte()
    }
}

/**
 * 对文件头部原地做快速混淆（XOR）。
 * 仅读写前 min(fileSize, 4096) 字节。
 * @return 成功返回 true；成功后需由调用方将文件重命名为添加 .qx 扩展名
 */
fun obfuscateFileHeader(context: Context, uri: Uri, password: CharArray): Boolean {
    if (password.isEmpty()) return false
    val doc = DocumentFile.fromSingleUri(context, uri) ?: return false
    if (doc.isDirectory) return false
    val fileSize = doc.length().coerceAtLeast(0)
    val len = minOf(HEADER_SIZE, fileSize.toInt().coerceAtLeast(0))
    if (len <= 0) return true // 空文件，无需处理
    val key = deriveKey(password, len)
    val data = context.contentResolver.readBytesFromOffset(uri, 0L, len) ?: return false
    xorInPlace(data, key)
    return writeBytesAtOffset(context, uri, 0L, data)
}

/**
 * 对文件头部原地做快速去混淆（XOR，与混淆为同一操作）。
 * 成功后需由调用方将文件重命名为去掉 .qx 扩展名
 */
fun deobfuscateFileHeader(context: Context, uri: Uri, password: CharArray): Boolean =
    obfuscateFileHeader(context, uri, password)

/**
 * 快速混淆：原地 XOR 头部并添加 .qx 扩展名。
 */
fun quickObfuscate(context: Context, uri: Uri, password: CharArray): Boolean {
    if (!obfuscateFileHeader(context, uri, password)) return false
    val doc = DocumentFile.fromSingleUri(context, uri) ?: return false
    val name = doc.name ?: return false
    val newName = addQuickObfuscateExtension(name)
    return context.contentResolver.renameDocument(uri, newName) != null
}

/**
 * 快速去混淆：原地 XOR 头部并去掉 .qx 扩展名。
 */
fun quickDeobfuscate(context: Context, uri: Uri, password: CharArray): Boolean {
    if (!deobfuscateFileHeader(context, uri, password)) return false
    val doc = DocumentFile.fromSingleUri(context, uri) ?: return false
    val name = doc.name ?: return false
    val newName = stripQuickObfuscateExtension(name)
    if (newName == name) return true // 无 .qx 扩展名，仅完成头部 XOR
    return context.contentResolver.renameDocument(uri, newName) != null
}
