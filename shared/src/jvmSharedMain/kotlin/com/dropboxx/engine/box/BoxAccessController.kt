package com.dropboxx.engine.box

import co.touchlab.kermit.Logger
import com.dropboxx.core.AppInfo
import com.dropboxx.core.nowMillis
import com.dropboxx.core.randomId
import com.dropboxx.core.secureToken
import com.dropboxx.domain.AppSettings
import com.dropboxx.domain.BoxAccess
import com.dropboxx.domain.BoxRepository
import com.dropboxx.domain.DeviceIdentity
import com.dropboxx.domain.PlatformServices
import com.dropboxx.domain.TrustStore
import com.dropboxx.model.AccessDecision
import com.dropboxx.model.AccessRequest
import com.dropboxx.model.BoxListRequest
import com.dropboxx.model.BoxListResponse
import com.dropboxx.model.PlatformFile
import com.dropboxx.model.toEntry
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

    suspend fun list(req: BoxListRequest, remoteAddress: String): ListResult {
        val pin = settings.current.pin
        if (pin.isNotEmpty() && req.pin != pin) return ListResult.PinRequired

        val trusted = trustStore.find(req.info.id)?.takeIf { it.fingerprint == req.info.fingerprint && it.pairToken == req.pairToken }
        var pairToken: String? = null
        val allowed = when {
            trusted != null -> true
            settings.current.boxAccess == BoxAccess.EVERYONE -> true
            settings.current.boxAccess == BoxAccess.TRUSTED_ONLY -> false
            else -> {
                if (accessRequest.value != null) return ListResult.Busy
                val id = randomId()
                val deferred = CompletableDeferred<AccessDecision>()
                pending[id] = deferred
                accessRequest.value = AccessRequest(id, req.info, remoteAddress, nowMillis())
                platform.notify("${req.info.alias} wants to open your box", "Open DropBoxx to allow or deny")
                val decision = try {
                    withTimeoutOrNull(AppInfo.ACCEPT_TIMEOUT_MILLIS) { deferred.await() } ?: AccessDecision(allow = false)
                } finally {
                    pending.remove(id)
                    if (accessRequest.value?.id == id) accessRequest.value = null
                }
                if (decision.allow && decision.always) {
                    pairToken = secureToken()
                    trustStore.trust(req.info, pairToken)
                }
                decision.allow
            }
        }
        if (!allowed) return ListResult.Denied

        val token = secureToken(24)
        grants[token] = Grant(req.info.id, nowMillis() + GRANT_TTL_MILLIS)
        sweep()
        log.i { "${req.info.alias} opened the box (${box.items.value.size} items)" }
        return ListResult.Ok(BoxListResponse(identity.info.value, box.items.value.filter { it.available }.map { it.toEntry() }, token, pairToken))
    }

    /** Downloads accept a visit token from [list] or a trusted device's pair token. */
    fun authorize(token: String?): Boolean {
        if (token.isNullOrEmpty()) return false
        grants[token]?.let { if (it.expiresAt > nowMillis()) return true else grants.remove(token) }
        return trustStore.devices.value.any { it.pairToken == token }
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
