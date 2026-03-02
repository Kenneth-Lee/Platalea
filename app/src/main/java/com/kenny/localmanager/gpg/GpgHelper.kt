package com.kenny.localmanager.gpg

import org.bouncycastle.bcpg.ArmoredOutputStream
import org.bouncycastle.bcpg.SymmetricKeyAlgorithmTags
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openpgp.PGPCompressedData
import org.bouncycastle.openpgp.PGPEncryptedDataList
import org.bouncycastle.openpgp.PGPLiteralData
import org.bouncycastle.openpgp.PGPPublicKey
import org.bouncycastle.openpgp.PGPPublicKeyEncryptedData
import org.bouncycastle.openpgp.PGPUtil
import org.bouncycastle.openpgp.bc.BcPGPObjectFactory
import org.bouncycastle.openpgp.operator.jcajce.JcePGPDataEncryptorBuilder
import org.bouncycastle.openpgp.operator.jcajce.JcePublicKeyDataDecryptorFactoryBuilder
import org.bouncycastle.openpgp.operator.jcajce.JcePublicKeyKeyEncryptionMethodGenerator
import org.pgpainless.PGPainless
import org.pgpainless.decryption_verification.ConsumerOptions
import org.pgpainless.decryption_verification.DecryptionStream
import org.pgpainless.encryption_signing.EncryptionOptions
import org.pgpainless.encryption_signing.EncryptionStream
import org.pgpainless.encryption_signing.ProducerOptions
import org.pgpainless.util.Passphrase
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.Security
import java.util.Date

/**
 * GPG 加解密：对称加解密委托给 PGPainless（与 GnuPG 兼容）；
 * 公钥加密/私钥解密仍用 Bouncy Castle。
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
     * 统一解密：若提供 password 则先用 PGPainless 对称解密；否则尝试私钥解密。
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
        return try {
            val factory = PGPUtil.getDecoderStream(encrypted.inputStream())
            val pgpFactory = BcPGPObjectFactory(factory)
            var obj = pgpFactory.nextObject()
            while (obj != null) {
                when (obj) {
                    is PGPEncryptedDataList -> {
                        val encList = obj.iterator()
                        while (encList.hasNext()) {
                            val enc = encList.next()
                            when (enc) {
                                is PGPPublicKeyEncryptedData -> {
                                    if (secretKeyRings == null) continue
                                    val keyId = enc.keyID
                                    val secretKey = secretKeyRings.getSecretKey(keyId) ?: continue
                                    val keyDecryptor = org.bouncycastle.openpgp.operator.jcajce.JcePBESecretKeyDecryptorBuilder(
                                        org.bouncycastle.openpgp.operator.jcajce.JcaPGPDigestCalculatorProviderBuilder().build()
                                    ).setProvider(BouncyCastleProvider.PROVIDER_NAME).build(keyPassphrase ?: CharArray(0))
                                    val privateKey = secretKey.extractPrivateKey(keyDecryptor) ?: continue
                                    val dec = JcePublicKeyDataDecryptorFactoryBuilder()
                                        .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                                        .build(privateKey)
                                    val decryptedStream = enc.getDataStream(dec)
                                    return readLiteralStream(decryptedStream)
                                }
                                else -> { }
                            }
                        }
                    }
                }
                obj = pgpFactory.nextObject()
            }
            null
        } catch (e: Exception) {
            onError?.invoke(e)
            null
        }
    }

    private fun readLiteralStream(literalStream: InputStream): ByteArray {
        val litFactory = BcPGPObjectFactory(literalStream)
        var obj = litFactory.nextObject()
        while (obj != null) {
            when (obj) {
                is PGPLiteralData -> obj.dataStream.use { return it.readBytes() }
                is PGPCompressedData -> return readLiteralStream(obj.dataStream)
            }
            obj = litFactory.nextObject()
        }
        return byteArrayOf()
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
     * 使用指定公钥加密，输出为 ASCII 装甲的 OpenPGP 格式。
     * 仅持有对应私钥的接收方可解密。
     */
    fun encryptWithPublicKey(plain: ByteArray, publicKey: PGPPublicKey, literalFileName: String): ByteArray? {
        if (!publicKey.isEncryptionKey) return null
        return try {
            val out = ByteArrayOutputStream()
            ArmoredOutputStream(out).use { armored ->
                val encGen = org.bouncycastle.openpgp.PGPEncryptedDataGenerator(
                    JcePGPDataEncryptorBuilder(SymmetricKeyAlgorithmTags.AES_256)
                        .setWithIntegrityPacket(true)
                        .setSecureRandom(java.security.SecureRandom())
                        .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                )
                encGen.addMethod(JcePublicKeyKeyEncryptionMethodGenerator(publicKey))
                encGen.open(armored, plain.size.toLong()).use { encOut ->
                    val litGen = org.bouncycastle.openpgp.PGPLiteralDataGenerator()
                    litGen.open(encOut, org.bouncycastle.openpgp.PGPLiteralData.BINARY, literalFileName, plain.size.toLong(), Date()).use { litOut ->
                        litOut.write(plain)
                    }
                }
            }
            out.toByteArray()
        } catch (_: Exception) {
            null
        }
    }
}
