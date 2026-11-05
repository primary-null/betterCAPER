package com.bettercaper.api

import com.bettercaper.api.model.BcEvent
import com.bettercaper.api.model.SessionResponse
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

class BettercapApiClient(
    private val localPort: Int,
    private val apiUsername: String,
    private val apiPassword: String
) {
    private val gson = Gson()
    private val baseUrl = "http://127.0.0.1:$localPort"
    private val credentials = Credentials.basic(apiUsername, apiPassword)

    private val client = OkHttpClient.Builder()
        .connectTimeout(0, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val req = chain.request().newBuilder()
                .header("Authorization", credentials)
                .build()
            chain.proceed(req)
        }
        .build()

    fun getSession(): SessionResponse {
        val req = Request.Builder().url("$baseUrl/api/session").build()
        client.newCall(req).execute().use { resp ->
            val bodyStream = resp.body?.charStream() ?: error("Empty response body")
            return gson.fromJson(bodyStream, SessionResponse::class.java)
        }
    }

    fun runCommand(cmd: String, isRetry: Boolean = false): String {
        val body = gson.toJson(mapOf("cmd" to cmd))
            .toRequestBody("application/json".toMediaType())
        val req = Request.Builder().url("$baseUrl/api/session").post(body).build()
        client.newCall(req).execute().use { resp ->
            val raw = resp.body?.string() ?: ""
            if (resp.isSuccessful) return raw.ifBlank { "OK" }

            // Auto-fix 1: ignore error if turning off an already stopped module
            if (cmd.endsWith(" off") && raw.contains("not running", ignoreCase = true)) {
                return "OK"
            }

            // Auto-fix 2: interface is down
            if (!isRetry && raw.contains("down", ignoreCase = true)) {
                val ifaceMatch = Regex("interface ([\\w\\d]+)", RegexOption.IGNORE_CASE).find(raw)
                val iface = ifaceMatch?.groupValues?.get(1) ?: "wlan0"
                
                // Try to bring the interface up via bettercap shell execution
                runCommand("!ip link set $iface up", isRetry = true)
                runCommand("!ifconfig $iface up", isRetry = true)
                
                // Retry the original command
                return runCommand(cmd, isRetry = true)
            }

            return "Error ${resp.code}: $raw"
        }
    }

    fun getEvents(n: Int = 100): List<BcEvent> {
        val req = Request.Builder().url("$baseUrl/api/events?n=$n").build()
        client.newCall(req).execute().use { resp ->
            val type = object : TypeToken<List<BcEvent>>() {}.type
            val bodyStream = resp.body?.charStream() ?: return emptyList()
            return gson.fromJson(bodyStream, type) ?: emptyList()
        }
    }

    fun connectWebSocket(listener: WebSocketListener): WebSocket {
        val req = Request.Builder()
            .url("ws://127.0.0.1:$localPort/api/events")
            .header("Authorization", credentials)
            .build()
        return client.newWebSocket(req, listener)
    }
}
