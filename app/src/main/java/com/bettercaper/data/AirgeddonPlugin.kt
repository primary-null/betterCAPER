package com.bettercaper.data

import android.util.Base64

data class AirgeddonEvent(
    val timestamp: String,
    val category: String,
    val detail: String
)

fun parseAirgeddonEvents(raw: String): List<AirgeddonEvent> =
    raw.lines()
        .filter { it.contains("|") }
        .mapNotNull { line ->
            val parts = line.split("|", limit = 3)
            if (parts.size == 3) AirgeddonEvent(parts[0].trim(), parts[1].trim(), parts[2].trim())
            else null
        }
        .reversed()

const val AIRGEDDON_LOG_PATH = "\${HOME}/.airgeddon/.bettercaper_ag.log"
const val AIRGEDDON_PLUGIN_PATH = "\${HOME}/.airgeddon/plugins/bettercaper.sh"

// The plugin script deployed to the remote host.
// Hooks confirmed-hookable functions and common wireless workflow functions.
private val PLUGIN_CONTENT = """
plugin_name="betterCAPER"
plugin_description="Remote event logging for the betterCAPER Android app"
plugin_author="betterCAPER"
plugin_enabled=1
plugin_minimum_ag_affected_version="10.0"
plugin_maximum_ag_affected_version=""
plugin_distros_supported=("*")

readonly BETTERCAPER_LOG="$AIRGEDDON_LOG_PATH"

_bettercaper_log() {
    touch "${'$'}{BETTERCAPER_LOG}" 2>/dev/null && chmod 600 "${'$'}{BETTERCAPER_LOG}" 2>/dev/null
    echo "${'$'}(date -Iseconds)|${'$'}{1}|${'$'}{2}" >> "${'$'}{BETTERCAPER_LOG}"
}

# Confirmed hookable — fires whenever menus rebuild
bettercaper_posthook_hookable_for_menus() {
    _bettercaper_log "MENU" "menu_refreshed"
}

# Confirmed hookable — fires during language/string initialisation
bettercaper_posthook_hookable_for_languages() {
    _bettercaper_log "PLUGIN" "betterCAPER_plugin_active"
}

# Capture / handshake workflow
bettercaper_posthook_capture_handshake() {
    _bettercaper_log "HANDSHAKE" "capture_completed rc=${'$'}{1}"
}

bettercaper_posthook_check_capture_handshake() {
    _bettercaper_log "HANDSHAKE" "check_completed rc=${'$'}{1}"
}

# Evil twin / rogue AP
bettercaper_posthook_launch_fake_ap() {
    _bettercaper_log "EVIL_TWIN" "fake_ap_launched rc=${'$'}{1}"
}

bettercaper_posthook_stop_evil_twin() {
    _bettercaper_log "EVIL_TWIN" "stopped rc=${'$'}{1}"
}

# Deauth / client disconnection
bettercaper_posthook_deauth_attack() {
    _bettercaper_log "DEAUTH" "attack_completed rc=${'$'}{1}"
}

# WPA/WPA2 cracking
bettercaper_posthook_crack_wpa() {
    _bettercaper_log "CRACK" "wpa_crack_completed rc=${'$'}{1}"
}

bettercaper_posthook_crack_wpa2() {
    _bettercaper_log "CRACK" "wpa2_crack_completed rc=${'$'}{1}"
}

# WPA3 menu (confirmed hookable)
bettercaper_posthook_hookable_wpa3_attacks_menu() {
    _bettercaper_log "MENU" "wpa3_menu_shown"
}

# Interface / monitor mode
bettercaper_posthook_enable_monitor_mode() {
    _bettercaper_log "IFACE" "monitor_mode_enabled rc=${'$'}{1}"
}

bettercaper_posthook_disable_monitor_mode() {
    _bettercaper_log "IFACE" "monitor_mode_disabled rc=${'$'}{1}"
}

# Network scan
bettercaper_posthook_explore_for_targets() {
    _bettercaper_log "SCAN" "target_scan_completed rc=${'$'}{1}"
}

_bettercaper_log "PLUGIN" "betterCAPER_plugin_loaded"
""".trimIndent()

fun pluginBase64(): String =
    Base64.encodeToString(PLUGIN_CONTENT.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
