package com.kenny.localmanager.gpg

import org.bouncycastle.bcpg.ArmoredOutputStream
import org.bouncycastle.openpgp.PGPPublicKey
import org.bouncycastle.openpgp.PGPPublicKeyRing
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection
import org.bouncycastle.openpgp.PGPSecretKeyRing
import org.bouncycastle.openpgp.PGPSecretKeyRingCollection
import org.bouncycastle.openpgp.PGPUtil
import org.bouncycastle.openpgp.operator.bc.BcKeyFingerprintCalculator
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream

/** 从应用 gnupg 目录加载公钥环集合，失败返回 null。 */
fun loadPublicKeyRings(context: android.content.Context): PGPPublicKeyRingCollection? {
    return try {
        val file = java.io.File(getGpgKeyDir(context), "pubring.gpg")
        if (!file.exists()) return null
        FileInputStream(file).use { input ->
            val decoder = PGPUtil.getDecoderStream(input)
            PGPPublicKeyRingCollection(decoder, BcKeyFingerprintCalculator())
        }
    } catch (_: Exception) {
        null
    }
}

/** 从应用 gnupg 目录加载私钥环集合，失败返回 null。 */
fun loadSecretKeyRings(context: android.content.Context): PGPSecretKeyRingCollection? {
    return try {
        val file = java.io.File(getGpgKeyDir(context), "secring.gpg")
        if (!file.exists()) return null
        FileInputStream(file).use { input ->
            val decoder = PGPUtil.getDecoderStream(input)
            PGPSecretKeyRingCollection(decoder, BcKeyFingerprintCalculator())
        }
    } catch (_: Exception) {
        null
    }
}

/** 枚举公钥环中可用于加密的密钥（加密用途），返回 (keyId, 简短描述)。 */
fun listEncryptionPublicKeys(rings: PGPPublicKeyRingCollection?): List<Pair<Long, String>> {
    if (rings == null) return emptyList()
    val list = mutableListOf<Pair<Long, String>>()
    rings.keyRings.forEach { ring ->
        ring.publicKeys.asSequence().forEach { key ->
            if (key.isEncryptionKey) {
                val id = key.keyID
                val uids = key.userIDs.asSequence().toList()
                val desc = uids.firstOrNull()?.toString()?.take(60) ?: "0x${id.toString(16)}"
                list.add(id to desc)
            }
        }
    }
    return list.distinctBy { it.first }
}

/** 根据 keyId 取公钥，若不存在返回 null。 */
fun findPublicKey(rings: PGPPublicKeyRingCollection?, keyId: Long): PGPPublicKey? {
    return rings?.getPublicKey(keyId)
}

/** 根据 keyId 取公钥环，若不存在返回 null。用于 PGPainless 公钥加密。 */
fun findPublicKeyRing(rings: PGPPublicKeyRingCollection?, keyId: Long): PGPPublicKeyRing? {
    return try { rings?.getPublicKeyRing(keyId) } catch (_: Exception) { null }
}

/** 用于展示的密钥信息：keyId、keyId(hex)、主用户 ID、是否加密钥、是否签名钥。 */
data class KeyInfo(val keyId: Long, val keyIdHex: String, val primaryUserId: String, val isEncryption: Boolean, val isSigning: Boolean)

/** Bouncy Castle PGPPublicKey 在部分版本无 isSigningKey，用算法判断是否可用于签名。 */
private fun PGPPublicKey.isSigningKeyCompat(): Boolean = when (algorithm) {
    PGPPublicKey.RSA_GENERAL, PGPPublicKey.RSA_SIGN, PGPPublicKey.DSA -> true
    else -> false
}

/** 枚举公钥环中所有主钥，返回展示用信息。 */
fun listPublicKeyInfos(rings: PGPPublicKeyRingCollection?): List<KeyInfo> {
    if (rings == null) return emptyList()
    val list = mutableListOf<KeyInfo>()
    rings.keyRings.forEach { ring ->
        val primary = ring.publicKey ?: return@forEach
        val id = primary.keyID
        val uids = primary.userIDs.asSequence().toList()
        val desc = uids.firstOrNull()?.toString()?.take(80) ?: "0x${id.toString(16)}"
        list.add(KeyInfo(id, "0x${id.toString(16).takeLast(8)}", desc, primary.isEncryptionKey, primary.isSigningKeyCompat()))
    }
    return list.distinctBy { it.keyIdHex }
}

/** 枚举私钥环中可用于解密的密钥（加密子钥），返回 (keyId, 描述)。 */
fun listDecryptionSecretKeys(rings: PGPSecretKeyRingCollection?): List<Pair<Long, String>> {
    if (rings == null) return emptyList()
    val list = mutableListOf<Pair<Long, String>>()
    rings.iterator().asSequence().forEach { ring ->
        ring.publicKeys.asSequence().forEach { key ->
            if (key.isEncryptionKey) {
                val id = key.keyID
                val uids = key.userIDs.asSequence().toList()
                val desc = uids.firstOrNull()?.toString()?.take(60) ?: "0x${id.toString(16)}"
                list.add(id to desc)
            }
        }
    }
    return list.distinctBy { it.first }
}

/** 枚举私钥环中可用于签名的密钥，返回 (keyId, 描述)。 */
fun listSigningSecretKeys(rings: PGPSecretKeyRingCollection?): List<Pair<Long, String>> {
    if (rings == null) return emptyList()
    val list = mutableListOf<Pair<Long, String>>()
    rings.iterator().asSequence().forEach { ring ->
        val pubKey = ring.publicKey ?: return@forEach
        if (!pubKey.isSigningKeyCompat()) return@forEach
        val id = pubKey.keyID
        val uids = pubKey.userIDs.asSequence().toList()
        val desc = uids.firstOrNull()?.toString()?.take(60) ?: "0x${id.toString(16)}"
        list.add(id to desc)
    }
    return list.distinctBy { it.first }
}

/** 根据 keyId 取私钥环，若不存在返回 null。 */
fun findSecretKeyRing(rings: PGPSecretKeyRingCollection?, keyId: Long): PGPSecretKeyRing? {
    return rings?.getSecretKeyRing(keyId)
}

/** 枚举私钥环中所有主钥，返回展示用信息。 */
fun listSecretKeyInfos(rings: PGPSecretKeyRingCollection?): List<KeyInfo> {
    if (rings == null) return emptyList()
    val list = mutableListOf<KeyInfo>()
    rings.iterator().asSequence().forEach { ring ->
        val pubKey = ring.publicKey ?: return@forEach
        val id = pubKey.keyID
        val uids = pubKey.userIDs.asSequence().toList()
        val desc = uids.firstOrNull()?.toString()?.take(80) ?: "0x${id.toString(16)}"
        list.add(KeyInfo(id, "0x${id.toString(16).takeLast(8)}", desc, pubKey.isEncryptionKey, pubKey.isSigningKeyCompat()))
    }
    return list.distinctBy { it.keyIdHex }
}

/** 应用 gnupg 目录下的密钥文件列表（供管理界面显示）。 */
fun listGpgKeyFiles(context: android.content.Context): List<Pair<String, Long>> {
    val dir = getGpgKeyDir(context)
    if (!dir.exists()) return emptyList()
    return dir.listFiles()?.filter { it.isFile && (it.name.endsWith(".gpg") || it.name.endsWith(".asc") || it.name.endsWith(".kbx")) }
        ?.map { it.name to it.length() }
        ?: emptyList()
}

/** 删除私钥文件 secring.gpg。返回是否成功。 */
fun deleteSecretKeys(context: android.content.Context): Boolean {
    val file = File(getGpgKeyDir(context), "secring.gpg")
    return file.exists() && file.delete()
}

/** 删除所有公钥（pubring.gpg）。返回是否成功。 */
fun deleteAllPublicKeys(context: android.content.Context): Boolean {
    val file = File(getGpgKeyDir(context), "pubring.gpg")
    return file.exists() && file.delete()
}

/** 从公钥环中移除指定 keyId 的 ring，保存回文件。返回 Pair(成功, 错误信息)。 */
fun deletePublicKeyById(context: android.content.Context, keyId: Long): Pair<Boolean, String?> {
    val rings = loadPublicKeyRings(context) ?: return Pair(false, "无公钥")
    val remaining = mutableListOf<PGPPublicKeyRing>()
    rings.keyRings.forEach { ring ->
        if (ring.publicKey?.keyID != keyId) {
            remaining.add(ring)
        }
    }
    if (remaining.isEmpty()) {
        return if (deleteAllPublicKeys(context)) Pair(true, null) else Pair(false, "删除失败")
    }
    return try {
        val outFile = File(getGpgKeyDir(context), "pubring.gpg")
        FileOutputStream(outFile).use { fOut ->
            ArmoredOutputStream(fOut).use { armored ->
                remaining.forEach { ring -> ring.encode(armored) }
            }
        }
        Pair(true, null)
    } catch (e: Exception) {
        Pair(false, e.message ?: e.javaClass.simpleName)
    }
}

/** 保存单个私钥环到 secring.gpg（覆盖）。应用仅保留一个私钥。返回 Pair(成功, 错误信息)。 */
fun saveSecretKeyRing(context: android.content.Context, ring: PGPSecretKeyRing): Pair<Boolean, String?> {
    return try {
        val file = File(getGpgKeyDir(context), "secring.gpg")
        FileOutputStream(file).use { fOut ->
            ArmoredOutputStream(fOut).use { ring.encode(it) }
        }
        Pair(true, null)
    } catch (e: Exception) {
        Pair(false, e.message ?: e.javaClass.simpleName)
    }
}

/** 将公钥环合并到 pubring.gpg。若文件不存在则创建。返回 Pair(成功, 错误信息)。 */
fun mergePublicKeyRing(context: android.content.Context, newRing: PGPPublicKeyRing): Pair<Boolean, String?> {
    return try {
        val existing = loadPublicKeyRings(context)
        val combined = if (existing == null) {
            listOf(newRing)
        } else {
            val list = mutableListOf<PGPPublicKeyRing>()
            existing.keyRings.forEach { list.add(it) }
            if (list.any { it.publicKey?.keyID == newRing.publicKey?.keyID }) return Pair(false, "公钥已存在")
            list.add(newRing)
            list
        }
        val outFile = File(getGpgKeyDir(context), "pubring.gpg")
        FileOutputStream(outFile).use { fOut ->
            ArmoredOutputStream(fOut).use { armored ->
                combined.forEach { ring -> ring.encode(armored) }
            }
        }
        Pair(true, null)
    } catch (e: Exception) {
        Pair(false, e.message ?: e.javaClass.simpleName)
    }
}

/** 从输入流解析私钥环，支持 ASCII 装甲或二进制。失败返回 null。 */
fun parseSecretKeyRingFromStream(input: InputStream): PGPSecretKeyRing? {
    return try {
        val decoder = PGPUtil.getDecoderStream(input)
        val pgpFactory = org.bouncycastle.openpgp.bc.BcPGPObjectFactory(decoder)
        var obj = pgpFactory.nextObject()
        while (obj != null) {
            if (obj is PGPSecretKeyRing) return obj
            obj = pgpFactory.nextObject()
        }
        null
    } catch (_: Exception) {
        null
    }
}

/** 从输入流解析公钥环，支持 ASCII 装甲或二进制。失败返回 null。 */
fun parsePublicKeyRingFromStream(input: InputStream): PGPPublicKeyRing? {
    return try {
        val decoder = PGPUtil.getDecoderStream(input)
        val pgpFactory = org.bouncycastle.openpgp.bc.BcPGPObjectFactory(decoder)
        var obj = pgpFactory.nextObject()
        while (obj != null) {
            if (obj is PGPPublicKeyRing) return obj
            obj = pgpFactory.nextObject()
        }
        null
    } catch (_: Exception) {
        null
    }
}
