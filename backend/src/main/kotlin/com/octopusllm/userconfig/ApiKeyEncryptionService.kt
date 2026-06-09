package com.octopusllm.userconfig

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

@Service
class ApiKeyEncryptionService(
    @Value("\${app.encryption.master-key}") masterKeyBase64: String,
) {
    private val masterKey: SecretKeySpec = SecretKeySpec(
        Base64.getDecoder().decode(masterKeyBase64),
        "AES",
    )

    data class EncryptedData(val ciphertext: ByteArray, val iv: ByteArray)

    fun encrypt(plaintext: String): EncryptedData {
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, masterKey, GCMParameterSpec(128, iv))
        }
        return EncryptedData(cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8)), iv)
    }

    fun decrypt(ciphertext: ByteArray, iv: ByteArray): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, masterKey, GCMParameterSpec(128, iv))
        }
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }
}
