package com.octopusllm.testsupport

import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
abstract class AbstractPostgresIntegrationTest {

    companion object {
        // Singleton container pattern: started once on first access and shared across every
        // integration test class. Not managed by @Testcontainers/@Container (which would stop it
        // after the first class and break the rest); Ryuk reaps it at JVM exit.
        @JvmStatic
        private val postgres: PostgreSQLContainer<*> =
            PostgreSQLContainer("postgres:16-alpine").apply { start() }

        @JvmStatic
        @DynamicPropertySource
        fun registerProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
            registry.add("app.jwt.secret") { "0123456789012345678901234567890123456789012345678901234567890123" }
            registry.add("app.jwt.expiry-seconds") { "3600" }
            registry.add("app.encryption.master-key") { "MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=" }
            registry.add("app.frontend.url") { "http://localhost:3000" }
            registry.add("app.admin.bootstrap-email") { "" }
            registry.add("app.network.trusted-proxies") { "127.0.0.1" }
            registry.add("app.anonymous-visitor.hmac-secret") {
                "test-anonymous-visitor-secret-that-is-long-enough"
            }
        }
    }
}
