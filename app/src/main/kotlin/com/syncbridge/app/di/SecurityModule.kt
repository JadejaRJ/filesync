package com.syncbridge.app.di

import com.syncbridge.core.security.BiometricAppLockGate
import com.syncbridge.core.security.CredentialCipher
import com.syncbridge.core.security.KeystoreCredentialCipher
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SecurityModule {

    @Provides
    @Singleton
    fun provideCredentialCipher(): CredentialCipher = KeystoreCredentialCipher()

    @Provides
    @Singleton
    fun provideBiometricAppLockGate(): BiometricAppLockGate = BiometricAppLockGate()
}
