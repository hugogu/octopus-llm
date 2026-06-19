package com.octopusllm.migration

import org.springframework.security.crypto.encrypt.Encryptors
import org.springframework.security.crypto.keygen.KeyGenerators
import org.springframework.stereotype.Component

/**
 * Authenticated password-based encryption for migration artifact entries (feature 008, T023).
 *
 * Uses Spring Security Crypto's "stronger" encryptor (PBKDF2-derived key + AES-256-GCM), so each
 * entry is both confidential and tamper-evident: a wrong passphrase or any modified byte fails the
 * GCM authentication tag and [decrypt] throws. One random salt is generated per artifact (stored in
 * the envelope, not secret). The passphrase and derived key live only in memory for the call — this
 * class never logs, returns, or stores them.
 */
@Component
class MigrationArtifactCrypto {

    /** A fresh hex-encoded salt to put in the artifact envelope (one per artifact). */
    fun newSaltHex(): String = SALT_GENERATOR.generateKey()

    fun encrypt(passphrase: CharSequence, saltHex: String, plaintext: ByteArray): ByteArray =
        Encryptors.stronger(passphrase, saltHex).encrypt(plaintext)

    /** @throws IllegalStateException (from the GCM tag check) on a wrong passphrase or tampering. */
    fun decrypt(passphrase: CharSequence, saltHex: String, ciphertext: ByteArray): ByteArray =
        Encryptors.stronger(passphrase, saltHex).decrypt(ciphertext)

    private companion object {
        // 8 random bytes, hex-encoded — the salt format Encryptors.stronger expects.
        val SALT_GENERATOR = KeyGenerators.string()
    }
}
