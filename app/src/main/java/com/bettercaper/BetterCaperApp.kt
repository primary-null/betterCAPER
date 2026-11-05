package com.bettercaper

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

@HiltAndroidApp
class BetterCaperApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Replace Android's stripped-down BC provider with the full one required by SSHJ
        Security.removeProvider("BC")
        Security.insertProviderAt(BouncyCastleProvider(), 1)
    }
}
