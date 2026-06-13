package com.octopusllm.connection

import com.octopusllm.api.v2.boundedPageRequest
import com.octopusllm.auth.UserRepository
import com.octopusllm.llm.ProtocolAdapterRegistry
import com.octopusllm.model.ProtocolDefinitions
import com.octopusllm.userconfig.ApiKeyEncryptionService
import org.springframework.data.domain.Page
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeoutException

@Service
class ConnectionService(
    private val userRepository: UserRepository,
    private val connectionRepository: ConnectionRepository,
    private val configuredModelRepository: ConfiguredModelRepository,
    private val encryptionService: ApiKeyEncryptionService,
    private val endpointPolicy: ConnectionEndpointPolicy,
    private val adapterRegistry: ProtocolAdapterRegistry,
) {
    private val listModelsTimeout: Duration = Duration.ofSeconds(20)

    fun list(userId: UUID, page: Int, size: Int): Mono<Page<Connection>> =
        blocking {
            connectionRepository.findByUserId(
                userId,
                boundedPageRequest(
                    page,
                    size,
                    Sort.Order.asc("createdAt"),
                    Sort.Order.asc("id"),
                ),
            )
        }

    fun add(
        userId: UUID,
        protocol: String,
        label: String?,
        baseUrl: String,
        apiKey: String,
    ): Mono<Connection> = blocking {
        ProtocolDefinitions.require(protocol)
        val user = userRepository.findById(userId).orElseThrow { notFound() }
        val encrypted = encryptionService.encrypt(apiKey)
        connectionRepository.save(
            Connection(
                user = user,
                protocol = protocol,
                label = normalizeLabel(label),
                baseUrl = endpointPolicy.normalizeAndValidate(baseUrl),
                encryptedKey = encrypted.ciphertext,
                keyIv = encrypted.iv,
            ),
        )
    }

    fun patch(userId: UUID, id: UUID, label: String?, baseUrl: String?): Mono<Connection> =
        blocking {
            val connection = requireOwned(userId, id)
            if (label != null) connection.label = normalizeLabel(label)
            if (baseUrl != null) connection.baseUrl = endpointPolicy.normalizeAndValidate(baseUrl)
            connection.updatedAt = Instant.now()
            connectionRepository.save(connection)
        }

    fun rotateKey(userId: UUID, id: UUID, apiKey: String): Mono<Unit> =
        blocking {
            val connection = requireOwned(userId, id)
            val encrypted = encryptionService.encrypt(apiKey)
            connection.encryptedKey = encrypted.ciphertext
            connection.keyIv = encrypted.iv
            connection.updatedAt = Instant.now()
            connectionRepository.save(connection)
            Unit
        }

    fun delete(userId: UUID, id: UUID): Mono<Unit> =
        blocking {
            connectionRepository.delete(requireOwned(userId, id))
            Unit
        }

    fun requireOwned(userId: UUID, id: UUID): Connection =
        connectionRepository.findByIdAndUserId(id, userId) ?: throw notFound()

    fun decryptAndValidate(connection: Connection): String {
        endpointPolicy.normalizeAndValidate(connection.baseUrl)
        return encryptionService.decrypt(connection.encryptedKey, connection.keyIv)
    }

    fun listEndpointModels(userId: UUID, id: UUID): Mono<List<String>> =
        blocking {
            val connection = requireOwned(userId, id)
            val apiKey = decryptAndValidate(connection)
            try {
                adapterRegistry.getAdapter(connection.protocol).listModels(apiKey, connection.baseUrl)
            } catch (cause: UnsupportedOperationException) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, cause.message)
            } catch (cause: ResponseStatusException) {
                throw cause
            } catch (cause: Exception) {
                throw ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Failed to load models from the endpoint: ${cause.message ?: "provider request failed"}",
                    cause,
                )
            }
        }
            .timeout(listModelsTimeout)
            .onErrorMap(TimeoutException::class.java) {
                ResponseStatusException(
                    HttpStatus.GATEWAY_TIMEOUT,
                    "Loading models timed out after ${listModelsTimeout.seconds}s",
                )
            }

    fun modelCount(connectionId: UUID): Long = configuredModelRepository.countByConnectionId(connectionId)

    private fun normalizeLabel(label: String?): String? = label?.trim()?.takeIf { it.isNotEmpty() }

    private fun notFound() = ResponseStatusException(HttpStatus.NOT_FOUND, "Connection not found")

    private fun <T> blocking(block: () -> T): Mono<T> =
        Mono.fromCallable(block).subscribeOn(Schedulers.boundedElastic())
}
