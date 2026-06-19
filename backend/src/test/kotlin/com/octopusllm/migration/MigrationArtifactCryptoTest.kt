package com.octopusllm.migration

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MigrationArtifactCryptoTest {

    private val crypto = MigrationArtifactCrypto()
    private val passphrase = "correct-horse-battery-staple-16+"
    private val plaintext = "provider-key=sk-secret-value and Quest text".toByteArray()

    @Test
    fun `round-trips with the same passphrase and salt`() {
        val salt = crypto.newSaltHex()
        val cipher = crypto.encrypt(passphrase, salt, plaintext)
        assertArrayEquals(plaintext, crypto.decrypt(passphrase, salt, cipher))
    }

    @Test
    fun `ciphertext is not the plaintext and looks random`() {
        val salt = crypto.newSaltHex()
        val cipher = crypto.encrypt(passphrase, salt, plaintext)
        assertFalse(cipher.contentEquals(plaintext))
        // The secret substring must not appear verbatim in the ciphertext.
        val needle = "sk-secret-value".toByteArray()
        assertFalse(indexOf(cipher, needle) >= 0, "ciphertext leaked plaintext")
        // Two encryptions of the same input differ (random IV/salt usage).
        assertFalse(cipher.contentEquals(crypto.encrypt(passphrase, salt, plaintext)))
    }

    @Test
    fun `wrong passphrase fails authentication without echoing the passphrase`() {
        val salt = crypto.newSaltHex()
        val cipher = crypto.encrypt(passphrase, salt, plaintext)
        val error = assertThrows(Throwable::class.java) {
            crypto.decrypt("a-totally-different-passphrase!!", salt, cipher)
        }
        assertFalse(error.message?.contains("battery") == true, "exception leaked the passphrase")
    }

    @Test
    fun `tampered ciphertext is rejected`() {
        val salt = crypto.newSaltHex()
        val cipher = crypto.encrypt(passphrase, salt, plaintext)
        cipher[cipher.size / 2] = (cipher[cipher.size / 2].toInt() xor 0x01).toByte()
        assertThrows(Throwable::class.java) { crypto.decrypt(passphrase, salt, cipher) }
    }

    private fun indexOf(haystack: ByteArray, needle: ByteArray): Int {
        if (needle.isEmpty() || haystack.size < needle.size) return -1
        outer@ for (i in 0..haystack.size - needle.size) {
            for (j in needle.indices) if (haystack[i + j] != needle[j]) continue@outer
            return i
        }
        return -1
    }

    @Test
    fun `salts are unique per call`() {
        assertTrue(crypto.newSaltHex() != crypto.newSaltHex())
    }
}
