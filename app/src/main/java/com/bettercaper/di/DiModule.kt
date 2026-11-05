package com.bettercaper.di

import android.app.Application
import com.bettercaper.data.ConnectionRepository
import com.bettercaper.data.SecureStorage
import com.bettercaper.ssh.SshTunnelManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DiModule {

    @Provides
    @Singleton
    fun provideSecureStorage(app: Application): SecureStorage {
        return SecureStorage(app)
    }

    @Provides
    @Singleton
    fun provideConnectionRepository(storage: SecureStorage, app: Application): ConnectionRepository {
        return ConnectionRepository(storage, app)
    }

    @Provides
    @Singleton
    fun provideSshTunnelManager(storage: SecureStorage): SshTunnelManager {
        return SshTunnelManager(storage)
    }
}
