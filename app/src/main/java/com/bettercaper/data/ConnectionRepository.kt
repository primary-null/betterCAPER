package com.bettercaper.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

class ConnectionRepository(private val storage: SecureStorage, context: Context) {
    private val gson = Gson()
    private val listType = object : TypeToken<List<SavedConnection>>() {}.type
    private val legacyFile = File(context.filesDir, "saved_connections.json")

    init {
        migrateLegacyFile()
    }

    fun load(): List<SavedConnection> {
        val json = storage.getConnections() ?: return emptyList()
        return runCatching {
            gson.fromJson<List<SavedConnection>>(json, listType) ?: emptyList()
        }.getOrElse { emptyList() }
    }

    fun save(connections: List<SavedConnection>) {
        storage.putConnections(gson.toJson(connections))
    }

    private fun migrateLegacyFile() {
        if (!legacyFile.exists()) return
        runCatching {
            val json = legacyFile.readText()
            val existing = storage.getConnections()
            if (existing == null) storage.putConnections(json)
            legacyFile.delete()
        }
    }
}
