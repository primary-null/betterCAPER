package com.bettercaper.api.model

import com.google.gson.annotations.SerializedName

data class SessionResponse(
    @SerializedName("interface") val iface: NetworkIface?,
    val gateway: Host?,
    val modules: List<BcModule>?,
    val started: String?,
    val active: Boolean?,
    val uptime: Long?,
    val lan: LanInfo?,
    val wifi: WifiInfo?,
    @SerializedName("ble") val bluetooth: BluetoothInfo?,
    val interfaces: List<NetworkIface>?
)

data class IfaceAddress(
    val address: String?,
    val type: String?
)

data class NetworkIface(
    val index: Int?,
    val mtu: Int?,
    val name: String?,
    val mac: String?,
    val vendor: String?,
    val flags: List<String>?,
    val addresses: List<IfaceAddress>?,
    // kept for any older API responses that include these directly
    val ipv4: String?,
    val ipv6: String?
) {
    val resolvedIpv4: String?
        get() = ipv4
            ?: addresses?.firstOrNull { it.type == "ip+net" && it.address?.contains(":") == false }
                ?.address?.substringBefore("/")

    val isLoopback: Boolean get() = flags?.contains("LOOPBACK") == true || name == "lo"
    val isUp: Boolean get() = flags?.contains("UP") == true
}

data class BcModule(
    val name: String?,
    val description: String?,
    val author: String?,
    val running: Boolean?,
    val parameters: Map<String, Any>?
)

data class LanInfo(
    val hosts: List<Host>?
)

data class WifiInfo(
    val aps: List<AccessPoint>?,
    val clients: List<WifiClient>?
)

data class BluetoothInfo(
    val devices: List<BtDevice>?
)

data class Host(
    val ipv4: String?,
    val ipv6: String?,
    val mac: String?,
    val hostname: String?,
    val vendor: String?,
    val aliases: List<String>?,
    @SerializedName("first_seen") val firstSeen: String?,
    @SerializedName("last_seen") val lastSeen: String?
)

data class AccessPoint(
    val mac: String?,
    @SerializedName("hostname") val ssid: String?,
    val rssi: Int?,
    val channel: Int?,
    val encryption: String?,
    val clients: List<WifiClient>?
)

data class WifiClient(
    val mac: String?,
    @SerializedName("access_point") val accessPoint: String?,
    val rssi: Int?,
    val vendor: String?
)

data class BtDevice(
    val mac: String?,
    val name: String?,
    val vendor: String?,
    val rssi: Int?,
    val connectable: Boolean?,
    @SerializedName("last_seen") val lastSeen: String?
)
