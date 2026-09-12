package com.dropboxx.engine.net

import com.dropboxx.engine.identity.IdentityManager
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

val ProtocolJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
}

/**
 * One Ktor/OkHttp client per peer, each pinned to that peer's certificate fingerprint.
 * A peer that presents a different certificate than it advertised simply fails the handshake.
 */
class PeerClients : AutoCloseable {

    private val pinned = ConcurrentHashMap<String, HttpClient>()

    /** Accepts any certificate. Used only for the `/info` probe of not-yet-known hosts. */
    val probe: HttpClient by lazy { build(trustManager = TrustAll, connectMillis = 700, requestMillis = 2_500) }

    fun forFingerprint(fingerprint: String): HttpClient =
        pinned.getOrPut(fingerprint.lowercase()) { build(PinnedTrust(fingerprint.lowercase()), connectMillis = 5_000, requestMillis = null) }

    fun drop(fingerprint: String) {
        pinned.remove(fingerprint.lowercase())?.close()
    }

    private fun build(trustManager: X509TrustManager, connectMillis: Long, requestMillis: Long?): HttpClient {
        val ssl = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trustManager), SecureRandom()) }
        return HttpClient(OkHttp) {
            expectSuccess = false
            engine {
                config {
                    sslSocketFactory(ssl.socketFactory, trustManager)
                    hostnameVerifier { _, _ -> true }
                    connectTimeout(connectMillis, TimeUnit.MILLISECONDS)
                    // Uploads are long-lived streams: no read/write timeouts, cancellation handles hangs.
                    readTimeout(0, TimeUnit.MILLISECONDS)
                    writeTimeout(0, TimeUnit.MILLISECONDS)
                    retryOnConnectionFailure(false)
                }
            }
            install(ContentNegotiation) { json(ProtocolJson) }
            install(HttpTimeout) {
                connectTimeoutMillis = connectMillis
                requestTimeoutMillis = requestMillis
                socketTimeoutMillis = null
            }
        }
    }

    override fun close() {
        pinned.values.forEach { runCatching { it.close() } }
        pinned.clear()
    }

    private object TrustAll : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) = Unit
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    private class PinnedTrust(private val expected: String) : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) =
            throw CertificateException("client auth not supported")

        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
            val leaf = chain.firstOrNull() ?: throw CertificateException("empty chain")
            val actual = IdentityManager.sha256Hex(leaf.encoded)
            if (actual != expected) throw CertificateException("certificate fingerprint mismatch: expected $expected got $actual")
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }
}
