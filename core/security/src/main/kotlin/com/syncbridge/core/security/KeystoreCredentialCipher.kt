package com.syncbridge.core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * [CredentialCipher] backed by a hardware-attested (where available) AES-256-GCM key that never
 * leaves the Android Keystore. This satisfies spec section 16: passwords/private keys are never
 * stored in plaintext, and are unrecoverable without the device's Keystore (i.e. a stolen database
 * backup alone cannot be decrypted).
 *
 * Deliberately does *not* set `setUserAuthenticationRequired(true)`: that would tie the key to a
 * recent biometric/PIN unlock and make the WorkManager background-sync worker unable to decrypt
 * credentials while the device is locked. The app's optional biometric *app lock* (see
 * [BiometricAppLockGate]) is a separate, UI-level gate — it does not change what this key can do.
 */
class KeystoreCredentialCipher(
    private val keyAlias: String = DEFAULT_KEY_ALIAS,
) : CredentialCipher {

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun getOrCreateKey(): SecretKey {
        (keyStore.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(keyAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(false)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    override fun encrypt(plaintext: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(iv + ciphertext, Base64.NO_WRAP)
    }

    override fun decrypt(ciphertext: String): String {
        val combined = Base64.decode(ciphertext, Base64.NO_WRAP)
        require(combined.size > IV_LENGTH_BYTES) { "Malformed ciphertext" }
        val iv = combined.copyOfRange(0, IV_LENGTH_BYTES)
        val actualCiphertext = combined.copyOfRange(IV_LENGTH_BYTES, combined.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_LENGTH_BITS, iv))
        return String(cipher.doFinal(actualCiphertext), Charsets.UTF_8)
    }

    override fun deleteKey() {
        if (keyStore.containsAlias(keyAlias)) {
            keyStore.deleteEntry(keyAlias)
        }
    }

    companion object {
        private const val DEFAULT_KEY_ALIAS = "syncbridge_credentials_key"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_LENGTH_BYTES = 12
        private const val TAG_LENGTH_BITS = 128
    }
}
