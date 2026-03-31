package com.kenny.localmanager.gpg

import com.kenny.localmanager.R
import org.bouncycastle.bcpg.ArmoredOutputStream
import org.bouncycastle.openpgp.PGPPublicKeyRing
import org.bouncycastle.openpgp.PGPSecretKeyRing
import org.pgpainless.PGPainless
import org.pgpainless.key.generation.type.rsa.RsaLength
import org.pgpainless.util.Passphrase
import java.io.File
import java.io.FileOutputStream

/** 应用默认存储下的 GnuPG 密钥目录。 */
fun getGpgKeyDir(context: android.content.Context): File {
    return File(context.filesDir, "gnupg").also { if (!it.exists()) it.mkdirs() }
}

/**
 * 在默认存储下生成并保存 PGP 密钥对（公钥 + 私钥）。
 * 使用 PGPainless 生成密钥，可规避 Android P+ 对 Bouncy Castle KeyPairGenerator/KeyFactory 的限制。
 * @param identity 用户标识，如 "Name <email@example.com>"
 * @param passphrase 密钥保护密码，可为空（无密码密钥）
 * @return Pair(成功, 失败时的错误信息)
 */
fun generateDefaultKey(context: android.content.Context, identity: String, passphrase: CharArray): Pair<Boolean, String?> {
    if (identity.isBlank()) return Pair(false, context.getString(R.string.gpg_identity_required))
    return try {
        val keyDir = getGpgKeyDir(context)
        val pwd = if (passphrase.isEmpty()) Passphrase.emptyPassphrase() else Passphrase.fromPassword(String(passphrase))
        val secretKeys: PGPSecretKeyRing = PGPainless.generateKeyRing()
            .simpleRsaKeyRing(identity.trim(), RsaLength._2048, pwd)
        FileOutputStream(File(keyDir, "secring.gpg")).use { fOut ->
            ArmoredOutputStream(fOut).use { secretKeys.encode(it) }
        }
        val publicKeyList = secretKeys.publicKeys.asSequence().toMutableList()
        val publicKeys = PGPPublicKeyRing(publicKeyList)
        val (merged, err) = mergePublicKeyRing(context, publicKeys)
        if (!merged && err != context.getString(R.string.gpg_public_key_exists)) return Pair(false, context.getString(R.string.gpg_save_public_key_failed, err.orEmpty()))
        pwd.clear()
        Pair(true, null)
    } catch (e: Exception) {
        Pair(false, e.message ?: e.javaClass.simpleName)
    }
}
