package com.kenny.localmanager.gpg

import org.bouncycastle.openpgp.PGPPublicKey
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection
import org.bouncycastle.openpgp.PGPSecretKeyRingCollection
import org.bouncycastle.openpgp.PGPUtil
import org.bouncycastle.openpgp.operator.bc.BcKeyFingerprintCalculator
import java.io.FileInputStream

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
