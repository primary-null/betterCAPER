package com.bettercaper.data

import android.util.Base64

const val WIFI_BLE_CAPLET_PATH = "\${HOME}/.bettercap/bettercaper-local.cap"

private val CAPLET_CONTENT = """
# betterCAPER local WiFi & BLE hook — triggered manually from the app
wifi.recon on
ble.recon on
""".trimIndent()

fun wifiBleCapletBase64(): String =
    Base64.encodeToString(CAPLET_CONTENT.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
