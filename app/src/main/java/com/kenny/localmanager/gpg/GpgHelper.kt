package com.kenny.localmanager.gpg

import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openpgp.PGPCompressedData
import org.bouncycastle.openpgp.PGPEncryptedDataList
import org.bouncycastle.openpgp.PGPLiteralData
import org.bouncycastle.openpgp.PGPPBEEncryptedData
import org.bouncycastle.openpgp.PGPUtil
import org.bouncycastle.openpgp.bc.BcPGPObjectFactory
import org.bouncycastle.openpgp.operator.jcajce.JcePBEDataDecryptorFactoryBuilder
import java.io.InputStream
import java.security.Security

object GpgHelper {

    init {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(BouncyCastleProvider())
        }
    }

    /** 使用密码解密 GPG 对称加密流；password 为 null 时返回 null。 */
    fun decryptStream(input: InputStream, password: CharArray?): ByteArray? {
        if (password == null) return null
        return try {
            decryptBytes(input.readBytes(), password)
        } catch (_: Exception) {
            null
        }
    }

    /** 无密码解密（仅适用于未加密或公钥加密；对称加密需用 decryptStream(input, password)）。 */
    fun decryptStream(input: InputStream): ByteArray? {
        return try {
            decryptBytes(input.readBytes(), null)
        } catch (_: Exception) {
            null
        }
    }

    private fun decryptBytes(encrypted: ByteArray, password: CharArray?): ByteArray? {
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
                            if (enc is PGPPBEEncryptedData) {
                                if (password == null) return null
                                val dec = JcePBEDataDecryptorFactoryBuilder()
                                    .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                                    .build(password)
                                val decryptedStream = enc.getDataStream(dec)
                                return readLiteralStream(decryptedStream)
                            }
                        }
                    }
                }
                obj = pgpFactory.nextObject()
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun readLiteralStream(literalStream: InputStream): ByteArray {
        val litFactory = BcPGPObjectFactory(literalStream)
        var obj = litFactory.nextObject()
        while (obj != null) {
            when (obj) {
                is PGPLiteralData -> return obj.inputStream.readBytes()
                is PGPCompressedData -> return readLiteralStream(obj.dataStream)
            }
            obj = litFactory.nextObject()
        }
        return byteArrayOf()
    }

    private fun InputStream.readBytes(): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        copyTo(out)
        return out.toByteArray()
    }
}
