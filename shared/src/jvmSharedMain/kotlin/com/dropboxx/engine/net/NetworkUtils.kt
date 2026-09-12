package com.dropboxx.engine.net

import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface

data class LocalAddress(val address: String, val prefixLength: Short, val interfaceName: String)

object NetworkUtils {

    /** Usable IPv4 addresses on up, non-loopback interfaces (Wi-Fi, Ethernet, hotspot AP). */
    fun localIpv4(): List<LocalAddress> {
        val result = mutableListOf<LocalAddress>()
        val interfaces = runCatching { NetworkInterface.getNetworkInterfaces()?.toList() }.getOrNull() ?: return result
        for (iface in interfaces) {
            if (!runCatching { iface.isUp && !iface.isLoopback }.getOrDefault(false)) continue
            val name = iface.name.lowercase()
            // Skip virtual adapters that never carry peers (VPN tunnels, VirtualBox, Docker...).
            // Also skip mobile-data (rmnet/ccmni), 464XLAT and VPN interfaces: peers are never reachable there.
            if (listOf("docker", "veth", "vbox", "vmnet", "tun", "rmnet", "ccmni", "clat", "dummy", "utun", "ppp").any { name.startsWith(it) }) continue
            for (ia in iface.interfaceAddresses) {
                val addr = ia.address
                // 192.0.0.0/24 is carrier-grade NAT plumbing (RFC 7335), not a LAN.
                if (addr is Inet4Address && !addr.isLoopbackAddress && !addr.isLinkLocalAddress && !(addr.hostAddress ?: "").startsWith("192.0.0.")) {
                    val host = addr.hostAddress ?: continue
                    result += LocalAddress(host, ia.networkPrefixLength, iface.name)
                }
            }
        }
        return result.sortedBy { if (it.address.startsWith("192.168.") || it.address.startsWith("10.")) 0 else 1 }
    }

    fun multicastCapableInterfaces(): List<NetworkInterface> =
        runCatching { NetworkInterface.getNetworkInterfaces()?.toList() }.getOrNull().orEmpty().filter { iface ->
            runCatching { iface.isUp && !iface.isLoopback && iface.supportsMulticast() && iface.interfaceAddresses.any { it.address is Inet4Address } }.getOrDefault(false)
        }

    /** All host addresses of a /24 around [address] (only /24 is scanned regardless of the real prefix, to bound the work). */
    fun subnetHosts(address: String): List<String> {
        val parts = address.split('.')
        if (parts.size != 4) return emptyList()
        val prefix = parts.take(3).joinToString(".")
        return (1..254).map { "$prefix.$it" }.filter { it != address }
    }

    fun isValidIpv4(s: String): Boolean = runCatching { InetAddress.getByName(s) is Inet4Address }.getOrDefault(false)
}
