package com.bettercaper.ssh

import android.util.Base64
import com.bettercaper.data.SecureStorage
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import java.security.MessageDigest
import java.security.PublicKey

class TrustOnFirstUseVerifier(private val storage: SecureStorage) : HostKeyVerifier {

    override fun verify(hostname: String, port: Int, key: PublicKey): Boolean {
        val hostId = "$hostname:$port"
        val fingerprint = fingerprint(key)
        val stored = storage.getHostFingerprint(hostId)

        if (stored == null) {
            storage.putHostFingerprint(hostId, fingerprint)
            return true
        }

        if (stored != fingerprint) {
            error(
                "Host key changed for $hostId — possible MITM attack.\n" +
                "Expected: $stored\n" +
                "Got:      $fingerprint\n" +
                "If the server was reinstalled, clear the saved fingerprint."
            )
        }

        return true
    }

    override fun findExistingAlgorithms(hostname: String, port: Int): List<String> = emptyList()

    private fun fingerprint(key: PublicKey): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(key.encoded)
        return "SHA256:" + Base64.encodeToString(digest, Base64.NO_WRAP)
    }
}
