package com.dropnest.engine.store

import com.dropnest.core.nowMillis
import com.dropnest.domain.TrustStore
import com.dropnest.domain.TrustedDevice
import com.dropnest.model.DeviceInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import java.io.File

@Serializable
private data class TrustedDeviceDto(val id: String, val alias: String, val fingerprint: String, val pairToken: String, val trustedAt: Long)

@Serializable
private data class TrustFile(
    val trusted: List<TrustedDeviceDto> = emptyList(),
    /** peerId -> token that peer gave us. */
    val outgoing: Map<String, String> = emptyMap(),
)

class TrustStoreImpl(dataDirectory: String) : TrustStore {

    private val store = JsonFileStore(File(dataDirectory, "trust.json"), TrustFile.serializer()) { TrustFile() }
    private var data = store.load()

    private val _devices = MutableStateFlow(data.trusted.map { it.toModel() })
    override val devices: StateFlow<List<TrustedDevice>> get() = _devices

    override fun find(id: String): TrustedDevice? = _devices.value.firstOrNull { it.id == id }

    @Synchronized
    override fun trust(device: DeviceInfo, pairToken: String) {
        val entry = TrustedDeviceDto(device.id, device.alias, device.fingerprint, pairToken, nowMillis())
        data = data.copy(trusted = data.trusted.filter { it.id != device.id } + entry)
        commit()
    }

    @Synchronized
    override fun revoke(id: String) {
        data = data.copy(trusted = data.trusted.filter { it.id != id }, outgoing = data.outgoing - id)
        commit()
    }

    override fun outgoingToken(peerId: String): String? = data.outgoing[peerId]

    @Synchronized
    override fun saveOutgoingToken(peerId: String, token: String) {
        data = data.copy(outgoing = data.outgoing + (peerId to token))
        commit()
    }

    private fun commit() {
        store.save(data)
        _devices.value = data.trusted.map { it.toModel() }
    }

    private fun TrustedDeviceDto.toModel() = TrustedDevice(id, alias, fingerprint, pairToken, trustedAt)
}
