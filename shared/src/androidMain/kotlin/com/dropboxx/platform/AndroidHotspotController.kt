package com.dropboxx.platform

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import co.touchlab.kermit.Logger
import com.dropboxx.domain.HotspotController
import com.dropboxx.domain.HotspotState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * "No router" mode: the phone opens a local-only hotspot (Wi-Fi Direct group under the hood) and
 * shows the credentials. Any PC or phone that joins can then discover us the normal way.
 */
class AndroidHotspotController(private val context: Context) : HotspotController {

    private val log = Logger.withTag("Hotspot")
    private val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private var reservation: WifiManager.LocalOnlyHotspotReservation? = null

    private val _state = MutableStateFlow(HotspotState(supported = true))
    override val state: StateFlow<HotspotState> get() = _state

    private fun requiredPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) arrayOf(Manifest.permission.NEARBY_WIFI_DEVICES)
        else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)

    override suspend fun start() {
        if (_state.value.active || _state.value.starting) return
        _state.value = HotspotState(supported = true, starting = true)
        val perms = requiredPermissions()
        val missing = perms.filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) {
            val granted = ActivityBridge.requestPermissions(missing.toTypedArray())
            if (granted.values.any { !it }) {
                _state.value = HotspotState(supported = true, error = "Permission needed to create a hotspot"); return
            }
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER) && !lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                _state.value = HotspotState(supported = true, error = "Turn on Location - Android requires it for hotspots"); return
            }
        }
        try {
            wifi.startLocalOnlyHotspot(object : WifiManager.LocalOnlyHotspotCallback() {
                override fun onStarted(r: WifiManager.LocalOnlyHotspotReservation) {
                    reservation = r
                    val (ssid, pass) = credentials(r)
                    _state.value = HotspotState(supported = true, active = true, ssid = ssid, password = pass)
                    log.i { "Hotspot up: $ssid" }
                }
                override fun onFailed(reason: Int) {
                    _state.value = HotspotState(supported = true, error = "Could not start hotspot (code $reason). Turn Wi-Fi on and try again.")
                }
                override fun onStopped() {
                    reservation = null
                    _state.value = HotspotState(supported = true)
                }
            }, Handler(Looper.getMainLooper()))
        } catch (e: Exception) {
            log.w(e) { "startLocalOnlyHotspot threw" }
            _state.value = HotspotState(supported = true, error = e.message ?: "Hotspot unavailable on this device")
        }
    }

    override fun stop() {
        reservation?.close()
        reservation = null
        _state.value = HotspotState(supported = true)
    }

    @Suppress("DEPRECATION")
    private fun credentials(r: WifiManager.LocalOnlyHotspotReservation): Pair<String?, String?> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val cfg = r.softApConfiguration
            val ssid = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) cfg.wifiSsid?.toString()?.trim('"') else cfg.ssid
            return ssid to cfg.passphrase
        }
        val cfg = r.wifiConfiguration
        return cfg?.SSID to cfg?.preSharedKey
    }
}
