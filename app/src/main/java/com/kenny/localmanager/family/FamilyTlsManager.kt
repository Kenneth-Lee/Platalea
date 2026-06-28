package com.kenny.localmanager.family

import android.content.Context
import com.kenny.localmanager.R
import java.net.URL
import java.security.KeyFactory
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSession
import javax.net.ssl.TrustManagerFactory

const val FAMILY_TLS_PROTOCOL = "https"
const val FAMILY_TLS_FINGERPRINT_ATTR = "tls_fp_sha256"

private const val FAMILY_TLS_CA_ASSET = "family_tls/ca_cert.pem"
private const val FAMILY_TLS_LOCAL_CERT_ASSET = "family_tls/android_dev_cert.pem"
private const val FAMILY_TLS_LOCAL_KEY_ASSET = "family_tls/android_dev_key_pkcs8.pem"
private const val FAMILY_TLS_KEYSTORE_PASSWORD = "localmanager-family-dev"

data class FamilyLocalTlsIdentity(
    val sslContext: SSLContext,
    val certificate: X509Certificate,
    val fingerprintSha256: String
)

class FamilyTlsManager(private val context: Context) {
    private val caCertificate: X509Certificate by lazy { loadCaCertificate() }
    private val clientSslContext: SSLContext by lazy { buildClientSslContext() }
    private val localIdentity: FamilyLocalTlsIdentity by lazy { loadLocalIdentity() }

    fun localIdentity(): FamilyLocalTlsIdentity = localIdentity

    fun openHttpsConnection(url: URL, expectedFingerprintSha256: String): HttpsURLConnection {
        val connection = url.openConnection() as? HttpsURLConnection
            ?: throw IllegalStateException(context.getString(R.string.family_msg_07788))
        val normalizedFingerprint = normalizeFingerprint(expectedFingerprintSha256)
        if (normalizedFingerprint.isBlank()) {
            throw IllegalStateException(context.getString(R.string.family_msg_22248))
        }
        connection.sslSocketFactory = clientSslContext.socketFactory
        connection.hostnameVerifier = fingerprintHostnameVerifier(normalizedFingerprint)
        return connection
    }

    private fun buildClientSslContext(): SSLContext {
        val trustStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null, null)
            setCertificateEntry("family_ca", caCertificate)
        }
        val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
            init(trustStore)
        }
        return SSLContext.getInstance("TLS").apply {
            init(null, trustManagerFactory.trustManagers, null)
        }
    }

    private fun loadLocalIdentity(): FamilyLocalTlsIdentity {
        val password = FAMILY_TLS_KEYSTORE_PASSWORD.toCharArray()
        val certificate = loadCertificate(FAMILY_TLS_LOCAL_CERT_ASSET)
        val privateKey = loadPrivateKey(FAMILY_TLS_LOCAL_KEY_ASSET)
        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null, null)
            setKeyEntry("family_local", privateKey, password, arrayOf(certificate, caCertificate))
        }
        val keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
            init(keyStore, password)
        }
        val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
            val trustStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
                load(null, null)
                setCertificateEntry("family_ca", caCertificate)
            }
            init(trustStore)
        }
        val sslContext = SSLContext.getInstance("TLS").apply {
            init(keyManagerFactory.keyManagers, trustManagerFactory.trustManagers, null)
        }
        return FamilyLocalTlsIdentity(
            sslContext = sslContext,
            certificate = certificate,
            fingerprintSha256 = sha256Fingerprint(certificate)
        )
    }

    private fun loadCaCertificate(): X509Certificate {
        return loadCertificate(FAMILY_TLS_CA_ASSET)
    }

    private fun loadCertificate(assetPath: String): X509Certificate {
        val certificateFactory = CertificateFactory.getInstance("X.509")
        context.assets.open(assetPath).use { input ->
            return certificateFactory.generateCertificate(input) as X509Certificate
        }
    }

    private fun loadPrivateKey(assetPath: String): PrivateKey {
        val pemText = context.assets.open(assetPath).bufferedReader(Charsets.UTF_8).use { it.readText() }
        val normalized = pemText
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace(Regex("\\s+"), "")
        val keyBytes = Base64.getDecoder().decode(normalized)
        val keySpec = PKCS8EncodedKeySpec(keyBytes)
        val algorithms = listOf("RSA", "EC")
        var lastError: Throwable? = null
        for (algorithm in algorithms) {
            try {
                return KeyFactory.getInstance(algorithm).generatePrivate(keySpec)
            } catch (error: Throwable) {
                lastError = error
            }
        }
        throw IllegalStateException(
            context.getString(
                R.string.family_msg_44400,
                algorithms.joinToString(),
                lastError?.message ?: context.getString(R.string.common_unknown_error)
            ),
            lastError
        )
    }

    private fun fingerprintHostnameVerifier(expectedFingerprint: String): HostnameVerifier {
        return HostnameVerifier { _: String?, session: SSLSession ->
            val peerCertificate = session.peerCertificates.firstOrNull() as? X509Certificate ?: return@HostnameVerifier false
            val actualFingerprint = sha256Fingerprint(peerCertificate)
            actualFingerprint == expectedFingerprint
        }
    }
}

private fun sha256Fingerprint(certificate: X509Certificate): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(certificate.encoded)
    return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
}

private fun normalizeFingerprint(value: String): String {
    return value.trim().lowercase().replace(":", "")
}
