package com.kenny.localmanager.gpg

import org.bouncycastle.bcpg.ArmoredOutputStream
import org.bouncycastle.bcpg.CompressionAlgorithmTags
import org.bouncycastle.bcpg.HashAlgorithmTags
import org.bouncycastle.bcpg.PublicKeyAlgorithmTags
import org.bouncycastle.bcpg.SymmetricKeyAlgorithmTags
import org.bouncycastle.bcpg.sig.Features
import org.bouncycastle.bcpg.sig.KeyFlags
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openpgp.PGPException
import org.bouncycastle.openpgp.PGPKeyPair
import org.bouncycastle.openpgp.PGPKeyRingGenerator
import org.bouncycastle.openpgp.PGPPublicKey
import org.bouncycastle.openpgp.PGPPublicKeyRing
import org.bouncycastle.openpgp.PGPSecretKeyRing
import org.bouncycastle.openpgp.PGPSignature
import org.bouncycastle.openpgp.PGPSignatureSubpacketGenerator
import org.bouncycastle.openpgp.operator.PBESecretKeyEncryptor
import org.bouncycastle.openpgp.operator.PGPContentSignerBuilder
import org.bouncycastle.openpgp.operator.PGPDigestCalculator
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPContentSignerBuilder
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPDigestCalculatorProviderBuilder
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPKeyPair
import org.bouncycastle.openpgp.operator.jcajce.JcePBESecretKeyEncryptorBuilder
import java.io.File
import java.io.FileOutputStream
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Security
import java.util.Date

private const val KEY_SIZE = 2048
private const val SIG_HASH = HashAlgorithmTags.SHA512

private val HASH_PREFS = intArrayOf(
    HashAlgorithmTags.SHA512, HashAlgorithmTags.SHA384, HashAlgorithmTags.SHA256, HashAlgorithmTags.SHA224
)
private val SYM_PREFS = intArrayOf(
    SymmetricKeyAlgorithmTags.AES_256, SymmetricKeyAlgorithmTags.AES_192, SymmetricKeyAlgorithmTags.AES_128
)
private val COMP_PREFS = intArrayOf(
    CompressionAlgorithmTags.ZLIB, CompressionAlgorithmTags.BZIP2, CompressionAlgorithmTags.UNCOMPRESSED
)

/** 应用默认存储下的 GnuPG 密钥目录。 */
fun getGpgKeyDir(context: android.content.Context): File {
    return File(context.filesDir, "gnupg").also { if (!it.exists()) it.mkdirs() }
}

/**
 * 在默认存储下生成并保存 PGP 密钥对（公钥 + 私钥）。
 * @param identity 用户标识，如 "Name <email@example.com>"
 * @return 成功返回 true
 */
fun generateDefaultKey(context: android.content.Context, identity: String, passphrase: CharArray): Boolean {
    if (passphrase.isEmpty() || identity.isBlank()) return false
    return try {
        Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) ?: Security.addProvider(BouncyCastleProvider())
        val keyDir = getGpgKeyDir(context)
        val sha1Calc = JcaPGPDigestCalculatorProviderBuilder().build().get(HashAlgorithmTags.SHA1)
        val kpg = KeyPairGenerator.getInstance("RSA", BouncyCastleProvider.PROVIDER_NAME)
        kpg.initialize(KEY_SIZE)
        val contentSignerBuilder = JcaPGPContentSignerBuilder(PublicKeyAlgorithmTags.RSA_GENERAL, SIG_HASH)
            .setProvider(BouncyCastleProvider.PROVIDER_NAME)
        val secretKeyEncryptor = JcePBESecretKeyEncryptorBuilder(SymmetricKeyAlgorithmTags.AES_256, sha1Calc)
            .setProvider(BouncyCastleProvider.PROVIDER_NAME)
            .build(passphrase)
        val now = Date()
        val primaryKP: KeyPair = kpg.generateKeyPair()
        val primaryKey: PGPKeyPair = JcaPGPKeyPair(PGPPublicKey.RSA_GENERAL, primaryKP, now)
        val primarySubpackets = PGPSignatureSubpacketGenerator().apply {
            setKeyFlags(true, KeyFlags.CERTIFY_OTHER)
            setPreferredHashAlgorithms(false, HASH_PREFS)
            setPreferredSymmetricAlgorithms(false, SYM_PREFS)
            setPreferredCompressionAlgorithms(false, COMP_PREFS)
            setFeature(false, Features.FEATURE_MODIFICATION_DETECTION)
            setIssuerFingerprint(false, primaryKey.publicKey)
        }
        val signingKP: KeyPair = kpg.generateKeyPair()
        val signingKey = JcaPGPKeyPair(PGPPublicKey.RSA_GENERAL, signingKP, now)
        val signingSubpacket = PGPSignatureSubpacketGenerator().apply {
            setKeyFlags(true, KeyFlags.SIGN_DATA)
            setIssuerFingerprint(false, primaryKey.publicKey)
        }
        val encryptionKP: KeyPair = kpg.generateKeyPair()
        val encryptionKey = JcaPGPKeyPair(PGPPublicKey.RSA_GENERAL, encryptionKP, now)
        val encryptionSubpackets = PGPSignatureSubpacketGenerator().apply {
            setKeyFlags(true, KeyFlags.ENCRYPT_COMMS or KeyFlags.ENCRYPT_STORAGE)
            setIssuerFingerprint(false, primaryKey.publicKey)
        }
        val gen = PGPKeyRingGenerator(
            PGPSignature.POSITIVE_CERTIFICATION,
            primaryKey,
            identity,
            sha1Calc,
            primarySubpackets.generate(),
            null,
            contentSignerBuilder,
            secretKeyEncryptor
        )
        gen.addSubKey(signingKey, signingSubpacket.generate(), null, contentSignerBuilder)
        gen.addSubKey(encryptionKey, encryptionSubpackets.generate(), null)
        val secretKeys = gen.generateSecretKeyRing()
        FileOutputStream(File(keyDir, "secring.gpg")).use { fOut ->
            ArmoredOutputStream(fOut).use { secretKeys.encode(it) }
        }
        val publicKeyList = secretKeys.publicKeys.asSequence().toMutableList()
        val publicKeys = PGPPublicKeyRing(publicKeyList)
        FileOutputStream(File(keyDir, "pubring.gpg")).use { fOut ->
            ArmoredOutputStream(fOut).use { publicKeys.encode(it) }
        }
        true
    } catch (_: Exception) {
        false
    }
}
