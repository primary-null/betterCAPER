package com.bettercaper.ssh

import com.bettercaper.data.SecureStorage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.connection.channel.direct.Parameters
import net.schmizz.sshj.transport.DisconnectListener
import net.schmizz.sshj.common.DisconnectReason
import java.net.ServerSocket
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

data class OpenedTunnel(val localPort: Int, val epoch: Int)

class SshTunnelManager(private val storage: SecureStorage) {
    private var sshClient: SSHClient? = null
    private var serverSocket: ServerSocket? = null
    private var gpsServerSocket: ServerSocket? = null
    private var uiServerSocket: ServerSocket? = null
    private var tunnelThread: Thread? = null
    private var gpsTunnelThread: Thread? = null
    private var uiTunnelThread: Thread? = null

    private var _localGpsPort: Int = -1
    val localGpsPort: Int get() = _localGpsPort

    private var _localUiPort: Int = -1
    val localUiPort: Int get() = _localUiPort

    private val sessionEpoch = AtomicInteger(0)
    private val gate = Any()

    suspend fun connect(
        host: String,
        sshPort: Int = 22,
        username: String,
        password: String? = null,
        privateKey: String? = null,
        remoteApiPort: Int = 8081,
        onDisconnect: (() -> Unit)? = null
    ): OpenedTunnel = withContext(Dispatchers.IO) {
        val epoch = synchronized(gate) {
            val next = sessionEpoch.incrementAndGet()
            releaseInside()
            next
        }
        val ssh = SSHClient()
        var apiSocket: ServerSocket? = null
        var gpsSocket: ServerSocket? = null
        var uiSocket: ServerSocket? = null
        var published = false
        try {
            ssh.addHostKeyVerifier(TrustOnFirstUseVerifier(storage))
            ssh.connect(host, sshPort)
            try {
                if (!privateKey.isNullOrBlank()) {
                    val tempKey = java.io.File.createTempFile("ssh_key", ".pem")
                    try {
                        tempKey.writeText(privateKey)
                        val keyProvider = ssh.loadKeys(tempKey.absolutePath)
                        ssh.authPublickey(username, keyProvider)
                    } finally {
                        tempKey.delete()
                    }
                } else if (!password.isNullOrEmpty()) {
                    ssh.authPassword(username, password)
                } else {
                    throw Exception("No authentication method provided.")
                }
            } catch (e: net.schmizz.sshj.userauth.UserAuthException) {
                throw Exception("Authentication failed: Invalid credentials.")
            }

            ssh.connection.keepAlive.keepAliveInterval = 60
            ssh.transport.setDisconnectListener(object : DisconnectListener {
                override fun notifyDisconnect(reason: DisconnectReason?, message: String?) {
                    onDisconnect?.invoke()
                }
            })

            val ss = ServerSocket(0)
            apiSocket = ss
            val localPort = ss.localPort
            val gpsSs = ServerSocket(0)
            gpsSocket = gpsSs
            val uiSs = ServerSocket(0)
            uiSocket = uiSs

            val forwarder = ssh.newLocalPortForwarder(
                Parameters("127.0.0.1", localPort, "127.0.0.1", remoteApiPort),
                ss
            )
            val gpsForwarder = ssh.newLocalPortForwarder(
                Parameters("127.0.0.1", gpsSs.localPort, "127.0.0.1", 2948),
                gpsSs
            )
            val uiForwarder = ssh.newLocalPortForwarder(
                Parameters("127.0.0.1", uiSs.localPort, "127.0.0.1", 80),
                uiSs
            )

            synchronized(gate) {
                if (sessionEpoch.get() != epoch) {
                    throw CancellationException("SSH connect superseded")
                }
                sshClient = ssh
                serverSocket = ss
                gpsServerSocket = gpsSs
                uiServerSocket = uiSs
                _localGpsPort = gpsSs.localPort
                _localUiPort = uiSs.localPort
                published = true
                tunnelThread = Thread({
                    try { forwarder.listen() } catch (_: Exception) {}
                }, "bettercaper-tunnel").also {
                    it.isDaemon = true
                    it.start()
                }
                gpsTunnelThread = Thread({
                    try { gpsForwarder.listen() } catch (_: Exception) {}
                }, "bettercaper-gps-tunnel").also {
                    it.isDaemon = true
                    it.start()
                }
                uiTunnelThread = Thread({
                    try { uiForwarder.listen() } catch (_: Exception) {}
                }, "bettercaper-ui-tunnel").also {
                    it.isDaemon = true
                    it.start()
                }
            }
            OpenedTunnel(localPort, epoch)
        } catch (cancelled: CancellationException) {
            if (!published) closeQuietly(ssh, apiSocket, gpsSocket, uiSocket)
            throw cancelled
        } catch (e: Exception) {
            if (!published) closeQuietly(ssh, apiSocket, gpsSocket, uiSocket)
            throw e
        }
    }

    private fun closeQuietly(
        ssh: SSHClient?,
        apiSocket: ServerSocket?,
        gpsSocket: ServerSocket?,
        uiSocket: ServerSocket?
    ) {
        runCatching { apiSocket?.close() }
        runCatching { gpsSocket?.close() }
        runCatching { uiSocket?.close() }
        runCatching { ssh?.disconnect() }
    }

    /** Caller holds [gate]. */
    private fun releaseInside() {
        runCatching { serverSocket?.close() }
        runCatching { gpsServerSocket?.close() }
        runCatching { uiServerSocket?.close() }
        runCatching { tunnelThread?.interrupt() }
        runCatching { gpsTunnelThread?.interrupt() }
        runCatching { uiTunnelThread?.interrupt() }
        runCatching { sshClient?.disconnect() }
        sshClient = null
        serverSocket = null
        gpsServerSocket = null
        uiServerSocket = null
        tunnelThread = null
        gpsTunnelThread = null
        uiTunnelThread = null
        _localGpsPort = -1
        _localUiPort = -1
    }

    /** Closes the tunnel only when [epoch] is still the published session. */
    fun releaseEpoch(epoch: Int) {
        synchronized(gate) {
            if (sessionEpoch.get() != epoch) return
            sessionEpoch.incrementAndGet()
            releaseInside()
        }
    }

    fun exec(command: String): String {
        val ssh = sshClient ?: error("Not connected")
        return ssh.startSession().use { session ->
            session.exec(command).use { cmd ->
                val stdout = cmd.inputStream.bufferedReader().readText()
                cmd.join(3600, TimeUnit.SECONDS)
                stdout.trim()
            }
        }
    }

    fun downloadFile(remotePath: String, localDir: java.io.File): java.io.File {
        val ssh = sshClient ?: error("Not connected")
        val destFile = java.io.File(localDir, remotePath.substringAfterLast("/"))
        ssh.newSCPFileTransfer().download(remotePath, net.schmizz.sshj.xfer.FileSystemFile(destFile))
        return destFile
    }

    fun uploadContent(remotePath: String, content: String) {
        val ssh = sshClient ?: error("Not connected")
        val tempFile = java.io.File.createTempFile("bettercaper_upload", ".tmp")
        try {
            tempFile.writeBytes(content.toByteArray(Charsets.UTF_8))
            ssh.newSCPFileTransfer().upload(net.schmizz.sshj.xfer.FileSystemFile(tempFile), remotePath)
        } finally {
            tempFile.delete()
        }
    }

    fun disconnect() {
        synchronized(gate) {
            sessionEpoch.incrementAndGet()
            releaseInside()
        }
    }
}
