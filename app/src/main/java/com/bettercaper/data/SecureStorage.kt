package com.bettercaper.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

class SecureStorage(context: Context) {
    val keystoreUnavailable: Boolean
    private val prefs: SharedPreferences

    init {
        val encrypted = openEncrypted(context)
        if (encrypted != null) {
            prefs = encrypted
            keystoreUnavailable = false
        } else {
            prefs = context.getSharedPreferences("bettercaper_fallback", Context.MODE_PRIVATE)
            keystoreUnavailable = true
        }
    }

    private fun openEncrypted(context: Context): SharedPreferences? = try {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            "bettercaper_secure",
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        android.util.Log.e("SecureStorage", "Keystore unavailable; saved secrets will use a private fallback store", e)
        null
    }

    fun getConnections(): String? = prefs.getString(KEY_CONNECTIONS, null)
    fun putConnections(json: String) = prefs.edit().putString(KEY_CONNECTIONS, json).apply()

    fun getHostFingerprint(hostId: String): String? = prefs.getString("$KEY_HOST_PREFIX$hostId", null)
    fun putHostFingerprint(hostId: String, fp: String) = prefs.edit().putString("$KEY_HOST_PREFIX$hostId", fp).apply()
    fun clearHostFingerprint(hostId: String) = prefs.edit().remove("$KEY_HOST_PREFIX$hostId").apply()

    companion object {
        private const val KEY_CONNECTIONS = "saved_connections"
        private const val KEY_HOST_PREFIX = "hostkey_"
    }
}
