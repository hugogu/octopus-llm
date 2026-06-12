package com.octopusllm.testsupport

import com.octopusllm.auth.User
import com.octopusllm.connection.ConfiguredModel
import com.octopusllm.connection.Connection
import com.octopusllm.userconfig.ApiKeyEncryptionService

object Feature003Fixtures {
    private const val TEST_MASTER_KEY = "MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE="

    fun user(email: String = "feature003@example.com") =
        User(email = email, passwordHash = "hash", emailVerified = true)

    fun encryptionService() = ApiKeyEncryptionService(TEST_MASTER_KEY)

    fun connection(
        user: User = user(),
        protocol: String = "openai-compatible",
        label: String = "Test connection",
        baseUrl: String = "https://8.8.8.8/v1",
        apiKey: String = "test-secret",
    ): Connection {
        val encrypted = encryptionService().encrypt(apiKey)
        return Connection(
            user = user,
            protocol = protocol,
            label = label,
            baseUrl = baseUrl,
            encryptedKey = encrypted.ciphertext,
            keyIv = encrypted.iv,
        )
    }

    fun configuredModel(
        user: User = user(),
        connection: Connection = connection(user),
        modelId: String = "same-model",
        displayName: String = "Configured model",
    ) = ConfiguredModel(
        user = user,
        connection = connection,
        modelId = modelId,
        displayName = displayName,
    )
}
