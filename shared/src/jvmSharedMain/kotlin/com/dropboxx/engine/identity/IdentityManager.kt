package com.dropboxx.engine.identity

import co.touchlab.kermit.Logger
import com.dropboxx.core.AppInfo
import com.dropboxx.domain.AppSettings
import com.dropboxx.domain.DeviceIdentity
import com.dropboxx.model.DeviceInfo
import com.dropboxx.model.DeviceType
import io.ktor.network.tls.certificates.buildKeyStore
import io.ktor.network.tls.certificates.saveToFile
import io.ktor.network.tls.extensions.HashAlgorithm
import io.ktor.network.tls.extensions.SignatureAlgorithm
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.X509Certificate
import javax.security.auth.x500.X500Principal

/**
 * Owns the self-signed TLS certificate that *is* this device's identity.
 * The certificate fingerprint doubles as the device id peers pin against.
 */
class IdentityManager(
    dataDirectory: String,
    private val settings: AppSettings,
    private val deviceType: DeviceType,
    scope: CoroutineScope,
) : DeviceIdentity {

    private val log = Logger.withTag("Identity")
    private val keyStoreFile = File(dataDirectory, "identity.keystore")

    val keyStore: KeyStore
    val certificate: X509Certificate
    val fingerprint: String

    private val _info: MutableStateFlow<DeviceInfo>
    override val info: StateFlow<DeviceInfo> get() = _info

    /** Port the server actually bound to; updated by the server after start. */
    @Volatile var boundPort: Int = settings.current.port
        private set

    init {
        keyStore = loadOrCreate()
        certificate = keyStore.getCertificate(ALIAS) as X509Certificate
        fingerprint = sha256Hex(certificate.encoded)
        _info = MutableStateFlow(build())
        settings.state.onEach { _info.value = build() }.launchIn(scope)
        log.i { "Device id ${_info.value.id} fingerprint $fingerprint" }
    }

    fun updateBoundPort(port: Int) {
        boundPort = port
        _info.value = build()
    }

    private fun build() = DeviceInfo(
        id = fingerprint.take(20),
        alias = settings.current.alias,
        deviceType = deviceType,
        fingerprint = fingerprint,
        port = boundPort,
        protocolVersion = AppInfo.PROTOCOL_VERSION,
        appVersion = AppInfo.VERSION,
    )

    private fun loadOrCreate(): KeyStore {
        if (keyStoreFile.exists()) {
            runCatching {
                val ks = KeyStore.getInstance(KeyStore.getDefaultType())
                keyStoreFile.inputStream().use { ks.load(it, PASSWORD.toCharArray()) }
                if (ks.containsAlias(ALIAS)) return ks
            }.onFailure { log.w(it) { "Keystore unreadable, regenerating" } }
        }
        val ks = buildKeyStore {
            certificate(ALIAS) {
                password = PASSWORD
                hash = HashAlgorithm.SHA256
                sign = SignatureAlgorithm.RSA
                keySizeInBits = 2048
                daysValid = 3650
                subject = X500Principal("CN=DropBoxx, O=DropBoxx")
                domains = listOf("localhost")
            }
        }
        keyStoreFile.parentFile?.mkdirs()
        ks.saveToFile(keyStoreFile, PASSWORD)
        log.i { "Generated new identity certificate" }
        return ks
    }

    companion object {
        const val ALIAS = "dropboxx"
        const val PASSWORD = "dropboxx-local"

        fun sha256Hex(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
