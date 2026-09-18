package com.dropnest.engine.box

import co.touchlab.kermit.Logger
import com.dropnest.core.AppInfo
import com.dropnest.core.nowMillis
import com.dropnest.core.randomId
import com.dropnest.core.secureToken
import com.dropnest.domain.AppSettings
import com.dropnest.domain.BoxAccess
import com.dropnest.domain.BoxRepository
import com.dropnest.domain.DeviceIdentity
import com.dropnest.domain.PlatformServices
import com.dropnest.domain.TrustStore
import com.dropnest.model.AccessDecision
import com.dropnest.model.AccessPurpose
import com.dropnest.model.DeviceInfo
import com.dropnest.model.AccessRequest
import com.dropnest.model.BoxListRequest
import com.dropnest.model.BoxListResponse
import com.dropnest.model.PlatformFile
import com.dropnest.model.toEntry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap

sealed interface ListResult {
    data class Ok(val response: BoxListResponse) : ListResult
    data object PinRequired : ListResult
    data object Denied : ListResult
    data object Busy : ListResult
}

/** Owner side of the box: decides who may list and download, hands out access tokens. */
class BoxAccessController(
    private val box: BoxRepository,
    private val trustStore: TrustStore,
    private val settings: AppSettings,
    private val identity: DeviceIdentity,
    private val platform: PlatformServices,
) {
    private val log = Logger.withTag("BoxAccess")

    val accessRequest = MutableStateFlow<AccessRequest?>(null)

    private class Grant(val deviceId: String, val expiresAt: Long)

    private val pending = ConcurrentHashMap<String, CompletableDeferred<AccessDecision>>()
    private val grants = ConcurrentHashMap<String, Grant>()

    /** Outcome of asking whether a device may use this box / message us. */
    sealed interface Admission {
        /** [pairToken] is set when the owner just chose "always allow" - send it back so the peer keeps it. */
        data class Allowed(val pairToken: String?) : Admission
        data object PinRequired : Admission
        data object Denied : Admission
        data object Busy : Admission
    }

    /**
     * The one approval path for everything a peer may ask of this device: trusted devices and
     * "anyone nearby" pass straight through, otherwise the owner is prompted (once / always / deny).
     */
    suspend fun admit(info: DeviceInfo, pairToken: String?, pin: String?, remoteAddress: String, purpose: AccessPurpose): Admission {
        val requiredPin = settings.current.pin
        if (requiredPin.isNotEmpty() && pin != requiredPin) return Admission.PinRequired
        val trusted = trustStore.find(info.id)?.takeIf { it.fingerprint == info.fingerprint && it.pairToken == pairToken }
        if (trusted != null) return Admission.Allowed(null)
        return when (settings.current.boxAccess) {
            BoxAccess.EVERYONE -> Admission.Allowed(null)
            BoxAccess.TRUSTED_ONLY -> Admission.Denied
            BoxAccess.ASK -> {
                if (accessRequest.value != null) return Admission.Busy
                val id = randomId()
                val deferred = CompletableDeferred<AccessDecision>()
                pending[id] = deferred
                accessRequest.value = AccessRequest(id, info, remoteAddress, nowMillis(), purpose)
                platform.notify(
                    if (purpose == AccessPurpose.CHAT) "${info.alias} wants to message you" else "${info.alias} wants to open your box",
                    "Open DropNest to allow or deny",
                )
                val decision = try {
                    withTimeoutOrNull(AppInfo.ACCEPT_TIMEOUT_MILLIS) { deferred.await() } ?: AccessDecision(allow = false)
                } finally {
                    pending.remove(id)
                    if (accessRequest.value?.id == id) accessRequest.value = null
                }
                when {
                    !decision.allow -> Admission.Denied
                    decision.always -> { val t = secureToken(); trustStore.trust(info, t); Admission.Allowed(t) }
                    else -> Admission.Allowed(null)
                }
            }
        }
    }

    suspend fun list(req: BoxListRequest, remoteAddress: String): ListResult {
        // A refresh within the same visit reuses the grant the owner already gave; no second prompt.
        val revisit = req.visitToken?.let { t -> grants[t]?.takeIf { it.deviceId == req.info.id && it.expiresAt > nowMillis() } } != null
        var pairToken: String? = null
        if (!revisit) {
            when (val a = admit(req.info, req.pairToken, req.pin, remoteAddress, AccessPurpose.BOX)) {
                is Admission.Allowed -> pairToken = a.pairToken
                Admission.PinRequired -> return ListResult.PinRequired
                Admission.Denied -> return ListResult.Denied
                Admission.Busy -> return ListResult.Busy
            }
        }
        val token = if (revisit) req.visitToken!! else secureToken(24)
        grants[token] = Grant(req.info.id, nowMillis() + GRANT_TTL_MILLIS)
        sweep()
        if (!revisit) log.i { "${req.info.alias} opened the box (${box.items.value.size} items)" }
        val visible = box.items.value.filter { it.available && (it.forPeerId == null || it.forPeerId == req.info.id) }
        return ListResult.Ok(BoxListResponse(identity.info.value, visible.map { it.toEntry() }, token, pairToken))
    }

    /** Downloads accept a visit token from [list] or a trusted device's pair token; returns the device id or null. */
    fun authorize(token: String?): String? {
        if (token.isNullOrEmpty()) return null
        grants[token]?.let { if (it.expiresAt > nowMillis()) return it.deviceId else grants.remove(token) }
        return trustStore.devices.value.firstOrNull { it.pairToken == token }?.id
    }

    /** Private drops are served only to the device they were dropped for. */
    fun mayDownload(itemId: String, deviceId: String): Boolean {
        val item = box.items.value.firstOrNull { it.id == itemId } ?: return false
        return item.forPeerId == null || item.forPeerId == deviceId
    }

    fun openItem(id: String): PlatformFile? = box.open(id)

    fun respond(requestId: String, decision: AccessDecision) {
        pending[requestId]?.complete(decision)
    }

    private fun sweep() {
        val now = nowMillis()
        grants.entries.removeIf { it.value.expiresAt < now }
    }

    private companion object {
        const val GRANT_TTL_MILLIS = 6 * 60 * 60 * 1000L
    }
}
