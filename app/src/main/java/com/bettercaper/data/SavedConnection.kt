package com.bettercaper.data

import java.util.UUID

data class SavedConnection(
    val id: String = UUID.randomUUID().toString(),
    val label: String,
    val sshHost: String,
    val sshPort: Int = 22,
    val sshUsername: String,
    val sshPassword: String = "",
    val sshKey: String? = null,
    val remoteApiPort: Int = 8081,
    val apiUsername: String = "user",
    val apiPassword: String = "user",
    val defaultIface: String? = null
)
