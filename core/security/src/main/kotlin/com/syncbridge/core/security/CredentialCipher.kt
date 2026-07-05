package com.syncbridge.core.security

/**
 * Encrypts/decrypts a single secret string (a connection's password, private key, or key passphrase)
 * for storage as an opaque blob in a Room column. Never log or expose the plaintext this returns.
 */
interface CredentialCipher {
    fun encrypt(plaintext: String): String
    fun decrypt(ciphertext: String): String

    /** Destroys the underlying key, permanently making every previously-encrypted blob unrecoverable. */
    fun deleteKey()
}
