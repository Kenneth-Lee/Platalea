package com.kenny.localmanager.gpg

import org.bouncycastle.bcpg.ArmoredOutputStream
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openpgp.PGPPublicKey
import org.bouncycastle.openpgp.PGPPublicKeyRing
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection
import org.pgpainless.PGPainless
import org.pgpainless.decryption_verification.ConsumerOptions
import org.pgpainless.decryption_verification.DecryptionStream
import org.pgpainless.encryption_signing.EncryptionOptions
import org.pgpainless.encryption_signing.EncryptionStream
import org.pgpainless.encryption_signing.ProducerOptions
import org.pgpainless.key.protection.SecretKeyRingProtector
import org.pgpainless.util.Passphrase
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.Security

/**
 * GPG 加解密：对称加解密委托给 PGPainless（与 GnuPG 兼容）；
 * 公钥加密仍用 Bouncy Castle；私钥解密改用 PGPainless 规避 Android P+ 限制。
 */
object GpgHelper {

    init {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(BouncyCastleProvider())
        }
    }

    // ---------- 解密 ----------

    /**
     * 使用密码解密对称加密的 GPG 数据（经由 PGPainless，与 GnuPG 兼容）。
     * @param onError 失败时回调，传入异常，用于调试
     */
    fun decryptSymmetric(input: InputStream, password: CharArray, onError: ((Throwable) -> Unit)? = null): ByteArray? {
        if (password.isEmpty()) return null
        val passphrase = Passphrase(password)
        return try {
            val encrypted = input.readBytes()
            val opts = ConsumerOptions.get().addMessagePassphrase(passphrase)
            val decStream: DecryptionStream = PGPainless.decryptAndOrVerify()
                .onInputStream(encrypted.inputStream())
                .withOptions(opts)
            decStream.use { it.readBytes() }
        } catch (e: Exception) {
            onError?.invoke(e)
            null
        } finally {
            passphrase.clear()
        }
    }

    /** 兼容旧调用：使用密码解密（对称）。 */
    fun decryptStream(input: InputStream, password: CharArray?): ByteArray? {
        if (password == null) return null
        return decryptSymmetric(input, password)
    }

    /** 无密码解密：仅能解密公钥加密且提供私钥环时有效；对称加密返回 null。 */
    fun decryptStream(input: InputStream): ByteArray? {
        return try {
            decryptBytes(input.readBytes(), password = null, secretKeyRings = null, keyPassphrase = null)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 使用本地私钥解密（公钥加密的数据）。
     * 通过 PGPainless 解密，可规避 Android P+ 对 BC KeyFactory.RSA 的限制。
     * @param keyPassphrase 私钥保护密码，无密码密钥可传空数组
     * @param onError 失败时回调，传入异常，用于调试
     */
    fun decryptWithSecretKey(
        input: InputStream,
        secretKeyRings: org.bouncycastle.openpgp.PGPSecretKeyRingCollection,
        keyPassphrase: CharArray,
        onError: ((Throwable) -> Unit)? = null
    ): ByteArray? {
        return try {
            decryptBytes(input.readBytes(), password = null, secretKeyRings = secretKeyRings, keyPassphrase = keyPassphrase, onError = onError)
        } catch (e: Exception) {
            onError?.invoke(e)
            null
        }
    }

    /**
     * 统一解密：若提供 password 则用 PGPainless 对称解密；否则用 PGPainless 私钥解密。
     */
    private fun decryptBytes(
        encrypted: ByteArray,
        password: CharArray?,
        secretKeyRings: org.bouncycastle.openpgp.PGPSecretKeyRingCollection?,
        keyPassphrase: CharArray?,
        onError: ((Throwable) -> Unit)? = null
    ): ByteArray? {
        if (password != null && password.isNotEmpty()) {
            val passphrase = Passphrase(password)
            try {
                val opts = ConsumerOptions.get().addMessagePassphrase(passphrase)
                val decStream: DecryptionStream = PGPainless.decryptAndOrVerify()
                    .onInputStream(encrypted.inputStream())
                    .withOptions(opts)
                return decStream.use { it.readBytes() }
            } catch (e: Exception) {
                onError?.invoke(e)
                return null
            } finally {
                passphrase.clear()
            }
        }
        if (secretKeyRings != null) {
            val pwd = Passphrase(keyPassphrase ?: CharArray(0))
            try {
                val protector = SecretKeyRingProtector.unlockAnyKeyWith(pwd)
                val opts = ConsumerOptions.get().addDecryptionKeys(secretKeyRings, protector)
                val decStream: DecryptionStream = PGPainless.decryptAndOrVerify()
                    .onInputStream(encrypted.inputStream())
                    .withOptions(opts)
                return decStream.use { it.readBytes() }
            } catch (e: Exception) {
                onError?.invoke(e)
                return null
            } finally {
                pwd.clear()
            }
        }
        return null
    }

    private fun InputStream.readBytes(): ByteArray {
        val out = ByteArrayOutputStream()
        copyTo(out)
        return out.toByteArray()
    }

    // ---------- 加密 ----------

    /**
     * 使用密码对称加密（经由 PGPainless，与 GnuPG 兼容）。
     * @param literalFileName 写入 literal 时的文件名（仅元数据）
     * @param onError 失败时回调，传入异常，用于调试
     */
    fun encryptSymmetric(plain: ByteArray, password: CharArray, literalFileName: String, onError: ((Throwable) -> Unit)? = null): ByteArray? {
        if (password.isEmpty()) return null
        val passphrase = Passphrase(password)
        return try {
            val encOpts = EncryptionOptions.get()
                .addMessagePassphrase(passphrase)
            val producerOpts = ProducerOptions.encrypt(encOpts)
                .setAsciiArmor(true)
                .setFileName(literalFileName)
            val out = ByteArrayOutputStream()
            PGPainless.encryptAndOrSign()
                .onOutputStream(out)
                .withOptions(producerOpts)
                .use { encStream -> encStream.write(plain) }
            out.toByteArray()
        } catch (e: Exception) {
            onError?.invoke(e)
            null
        } finally {
            passphrase.clear()
        }
    }

    /**
     * 使用指定公钥环加密，输出为 ASCII 装甲的 OpenPGP 格式。
     * 使用 PGPainless 与私钥解密格式一致，避免 BC 加密 + PGPainless 解密时的流格式不兼容。
     */
    fun encryptWithPublicKey(plain: ByteArray, publicKeyRing: PGPPublicKeyRing, literalFileName: String): ByteArray? {
        return try {
            val encOpts = EncryptionOptions.get().addRecipient(publicKeyRing)
            val producerOpts = ProducerOptions.encrypt(encOpts)
                .setAsciiArmor(true)
                .setFileName(literalFileName)
            val out = ByteArrayOutputStream()
            PGPainless.encryptAndOrSign()
                .onOutputStream(out)
                .withOptions(producerOpts)
                .use { encStream -> encStream.write(plain) }
            out.toByteArray()
        } catch (_: Exception) {
            null
        }
    }

}
