package com.octopusllm.connection

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.junit.jupiter.api.Test
import org.springframework.web.server.ResponseStatusException

class ConnectionEndpointPolicyTest {
    private val production = ConnectionEndpointPolicy(allowLocalHttp = false)

    @Test
    fun `normalizes public HTTPS endpoint`() {
        assertEquals("https://8.8.8.8:8443/v1", production.normalizeAndValidate(" HTTPS://8.8.8.8:8443/v1/ "))
    }

    @ParameterizedTest
    @ValueSource(strings = [
        "http://8.8.8.8/v1",
        "https://127.0.0.1/v1",
        "https://10.0.0.1/v1",
        "https://169.254.169.254/latest/meta-data",
        "https://192.168.1.1/v1",
        "https://[::1]/v1",
        "https://[fc00::1]/v1",
        "https://user:pass@8.8.8.8/v1",
        "https://8.8.8.8/v1?redirect=https://127.0.0.1",
        "https://8.8.8.8/v1#fragment",
    ])
    fun `rejects unsafe endpoint forms`(url: String) {
        assertThrows(ResponseStatusException::class.java) {
            production.normalizeAndValidate(url)
        }
    }

    @Test
    fun `development mode permits only loopback HTTP`() {
        val development = ConnectionEndpointPolicy(allowLocalHttp = true)
        assertEquals("http://localhost:8080/v1", development.normalizeAndValidate("http://localhost:8080/v1/"))
        assertThrows(ResponseStatusException::class.java) {
            development.normalizeAndValidate("http://8.8.8.8/v1")
        }
    }
}
