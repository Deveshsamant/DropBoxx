package com.dropnest.platform

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import co.touchlab.kermit.Logger
import com.dropnest.engine.bt.BluetoothTransport
import com.dropnest.engine.bt.BtDevice
import com.dropnest.engine.bt.BtSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.InputStream
import java.io.OutputStream

/** Classic Bluetooth (RFCOMM) on Android: listens for peers and connects to them. */
class AndroidBluetoothTransport(private val context: Context) : BluetoothTransport {

    private val log = Logger.withTag("Bluetooth")
    private val adapter: BluetoothAdapter? = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    override val supported: Boolean = adapter != null && context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)

    // Declared before [_ready]: the initial readiness check reads it.
    private val permissions: Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
        else arrayOf(Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.ACCESS_FINE_LOCATION)

    private fun granted() = permissions.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
    private fun isReady() = supported && granted() && adapter?.isEnabled == true

    private val _ready = MutableStateFlow(isReady())
    override val ready: StateFlow<Boolean> get() = _ready

    init {
        // Track the adapter being switched on/off in system settings.
        val stateReceiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) { _ready.value = isReady() }
        }
        ContextCompat.registerReceiver(context, stateReceiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED), ContextCompat.RECEIVER_EXPORTED)
    }

    override suspend fun enable(): Boolean {
        if (!supported) return false
        if (!granted()) {
            val result = ActivityBridge.requestPermissions(permissions)
            if (!permissions.all { result[it] == true }) { _ready.value = false; return false }
        }
        if (adapter?.isEnabled != true) {
            // Ask the user to switch the radio on; the state receiver flips [ready] when they do.
            runCatching { context.startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            _ready.value = isReady()
            return false
        }
        _ready.value = true
        return true
    }

    // ---- server ----

    private var server: BluetoothServerSocket? = null
    private var acceptThread: Thread? = null

    override fun listen(onConnection: (BtSocket) -> Unit) {
        if (acceptThread != null || !isReady()) return
        acceptThread = Thread({
            while (!Thread.currentThread().isInterrupted) {
                val srv = try {
                    adapter!!.listenUsingInsecureRfcommWithServiceRecord(BluetoothTransport.SERVICE_NAME, BluetoothTransport.SERVICE_UUID)
                } catch (e: Exception) { log.w { "cannot listen: ${e.message}" }; Thread.sleep(5_000); continue }
                server = srv
                try {
                    while (true) {
                        val sock = srv.accept()
                        onConnection(AndroidBtSocket(sock))
                    }
                } catch (e: Exception) { if (!Thread.currentThread().isInterrupted) log.d { "accept ended: ${e.message}" } }
                finally { runCatching { srv.close() } }
                if (Thread.currentThread().isInterrupted) break
                Thread.sleep(1_000)
            }
        }, "dropnest-bt-accept").apply { isDaemon = true; start() }
    }

    override fun stopListening() {
        acceptThread?.interrupt(); acceptThread = null
        runCatching { server?.close() }; server = null
    }

    // ---- client ----

    override suspend fun candidates(): List<BtDevice> {
        val a = adapter ?: return emptyList()
        if (!isReady()) return emptyList()
        val found = LinkedHashMap<String, BtDevice>()
        runCatching { a.bondedDevices }.getOrNull()?.forEach { found[it.address] = BtDevice(it.address, runCatching { it.name }.getOrNull()) }
        // A short inquiry catches unpaired phones running DropNest (insecure RFCOMM needs no pairing on Android).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S || found.isEmpty()) runCatching { discover(a, found) }
        return found.values.toList()
    }

    private suspend fun discover(a: BluetoothAdapter, into: MutableMap<String, BtDevice>) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                if (i?.action != BluetoothDevice.ACTION_FOUND) return
                @Suppress("DEPRECATION") val d = i.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return
                // Phones and computers only; skip headphones, watches, cars.
                val major = d.bluetoothClass?.majorDeviceClass ?: 0
                if (major == android.bluetooth.BluetoothClass.Device.Major.PHONE || major == android.bluetooth.BluetoothClass.Device.Major.COMPUTER)
                    into[d.address] = BtDevice(d.address, runCatching { d.name }.getOrNull())
            }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(BluetoothDevice.ACTION_FOUND), ContextCompat.RECEIVER_EXPORTED)
        try {
            if (!a.startDiscovery()) return
            delay(DISCOVERY_MILLIS)
        } finally {
            runCatching { a.cancelDiscovery() }
            runCatching { context.unregisterReceiver(receiver) }
        }
    }

    override suspend fun connect(address: String): BtSocket? = withContext(Dispatchers.IO) {
        val a = adapter ?: return@withContext null
        if (!isReady()) return@withContext null
        runCatching { a.cancelDiscovery() }
        val device = runCatching { a.getRemoteDevice(address) }.getOrNull() ?: return@withContext null
        val sock = runCatching { device.createInsecureRfcommSocketToServiceRecord(BluetoothTransport.SERVICE_UUID) }.getOrNull() ?: return@withContext null
        val ok = withTimeoutOrNull(CONNECT_MILLIS) { runCatching { sock.connect(); true }.getOrDefault(false) } ?: false
        if (!ok) { runCatching { sock.close() }; return@withContext null }
        AndroidBtSocket(sock)
    }

    private class AndroidBtSocket(private val sock: BluetoothSocket) : BtSocket {
        override val remoteAddress: String get() = sock.remoteDevice.address
        override val input: InputStream get() = sock.inputStream
        override val output: OutputStream get() = sock.outputStream
        override fun close() { runCatching { sock.close() } }
    }

    private companion object {
        const val DISCOVERY_MILLIS = 8_000L
        const val CONNECT_MILLIS = 10_000L
    }
}
