package com.octopusllm.anonymous

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.ObjectMapper
import com.octopusllm.auth.User
import com.octopusllm.auth.UserRepository
import com.octopusllm.chat.ChatSession
import com.octopusllm.chat.ChatTurn
import com.octopusllm.chat.ChatSessionRepository
import com.octopusllm.chat.ChatTurnRepository
import com.octopusllm.chat.ProviderResponse
import com.octopusllm.chat.ProviderResponseRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.TreeMap
import java.util.UUID

@JsonIgnoreProperties(ignoreUnknown = false)
data class AnonymousSyncResponseInput(
    val configuredModelId: UUID,
    val modelId: String,
    val modelDisplayName: String,
    val protocol: String,
    val status: String,
    val responseText: String? = null,
    val reasoningText: String? = null,
    val errorMessage: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = false)
data class AnonymousSyncTurnInput(
    val sourceTurnId: UUID,
    val clientRequestId: String,
    val promptText: String,
    val createdAt: Instant,
    val responses: List<AnonymousSyncResponseInput>,
)

@JsonIgnoreProperties(ignoreUnknown = false)
data class AnonymousSyncConversationInput(
    val sourceConversationId: UUID,
    val sourceDigest: String,
    val title: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
    val turns: List<AnonymousSyncTurnInput>,
)

@JsonIgnoreProperties(ignoreUnknown = false)
data class AnonymousSyncRequest(val conversations: List<AnonymousSyncConversationInput>)

data class AnonymousSyncItemResult(
    val sourceConversationId: UUID,
    val status: String,
    val sessionId: UUID? = null,
    val reasonCode: String? = null,
    val message: String,
)

data class AnonymousSyncResponse(val items: List<AnonymousSyncItemResult>)

@Service
class AnonymousConversationSyncService(
    private val userRepository: UserRepository,
    private val importRepository: AnonymousConversationImportRepository,
    private val transactionService: AnonymousConversationSyncTransactionService,
    private val objectMapper: ObjectMapper,
    @Value("\${app.anonymous.sync-max-conversations:20}") private val maxConversations: Int,
    @Value("\${app.anonymous.sync-max-body-bytes:5242880}") private val maxBodyBytes: Int,
) {
    fun sync(userId: UUID, request: AnonymousSyncRequest): Mono<AnonymousSyncResponse> = Mono.fromCallable {
        if (request.conversations.size > maxConversations.coerceAtLeast(1)) {
            throw badRequest("Too many conversations in one synchronization request")
        }
        if (objectMapper.writeValueAsBytes(request).size > maxBodyBytes.coerceAtLeast(1)) {
            throw ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Synchronization payload is too large")
        }
        userRepository.findById(userId).orElseThrow { ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required") }
        request.conversations.map { conversation -> importOne(userId, conversation) }
    }.subscribeOn(Schedulers.boundedElastic()).map(::AnonymousSyncResponse)

    private fun importOne(userId: UUID, conversation: AnonymousSyncConversationInput): AnonymousSyncItemResult {
        val validation = validate(conversation)
        if (validation != null) return skipped(conversation.sourceConversationId, validation)
        val expectedDigest = digest(conversation)
        if (!MessageDigest.isEqual(
                expectedDigest.toByteArray(StandardCharsets.US_ASCII),
                conversation.sourceDigest.lowercase().toByteArray(StandardCharsets.US_ASCII),
            )
        ) return skipped(conversation.sourceConversationId, "DIGEST_MISMATCH")

        val existing = importRepository.findByUserIdAndSourceConversationId(userId, conversation.sourceConversationId)
        if (existing != null) {
            return if (existing.sourceDigest == conversation.sourceDigest.lowercase() && existing.session != null) {
                AnonymousSyncItemResult(
                    conversation.sourceConversationId,
                    "ALREADY_IMPORTED",
                    existing.session.id,
                    message = "Conversation was already imported.",
                )
            } else {
                skipped(conversation.sourceConversationId, "SOURCE_DIGEST_CONFLICT", "Local conversation changed after synchronization.")
            }
        }

        return try {
            val session = transactionService.importConversation(userId, conversation, expectedDigest)
            AnonymousSyncItemResult(
                conversation.sourceConversationId,
                "IMPORTED",
                session.id,
                message = "Conversation imported.",
            )
        } catch (_: DataIntegrityViolationException) {
            val winner = importRepository.findByUserIdAndSourceConversationId(userId, conversation.sourceConversationId)
            if (winner?.sourceDigest == conversation.sourceDigest.lowercase() && winner.session != null) {
                AnonymousSyncItemResult(conversation.sourceConversationId, "ALREADY_IMPORTED", winner.session.id, message = "Conversation was already imported.")
            } else {
                skipped(conversation.sourceConversationId, "IMPORT_CONFLICT", "The conversation changed during synchronization.")
            }
        } catch (_: Exception) {
            AnonymousSyncItemResult(conversation.sourceConversationId, "FAILED", reasonCode = "IMPORT_FAILED", message = "Conversation could not be imported; local data was retained.")
        }
    }

    private fun validate(conversation: AnonymousSyncConversationInput): String? {
        if (conversation.sourceDigest.length != 64 || !conversation.sourceDigest.all { it in "0123456789abcdefABCDEF" }) return "INVALID_DIGEST"
        if (conversation.turns.isEmpty() || conversation.turns.size > 100) return "INVALID_TURN_COUNT"
        if (conversation.title.orEmpty().length > 500) return "INVALID_TITLE"
        if (conversation.turns.any { it.promptText.isBlank() || it.promptText.length > 100_000 || it.responses.isEmpty() }) return "INVALID_TURN"
        if (conversation.turns.flatMap { it.responses }.any { response ->
                response.status !in setOf("COMPLETE", "ERROR") ||
                    response.modelId.length > 255 || response.modelDisplayName.length > 255 ||
                    response.protocol.length > 50 || (response.responseText?.length ?: 0) > 1_000_000
            }) return "UNSUPPORTED_RESPONSE_STATE"
        return null
    }

    private fun skipped(id: UUID, reason: String, message: String = "This conversation remains on this device.") =
        AnonymousSyncItemResult(id, "SKIPPED", reasonCode = reason, message = message)

    private fun digest(conversation: AnonymousSyncConversationInput): String {
        val payload = mapOf(
            "id" to conversation.sourceConversationId.toString(),
            "title" to conversation.title,
            "createdAt" to canonicalInstant(conversation.createdAt),
            "updatedAt" to canonicalInstant(conversation.updatedAt),
            "turns" to conversation.turns.map { turn ->
                mapOf(
                    "id" to turn.sourceTurnId.toString(),
                    "clientRequestId" to turn.clientRequestId,
                    "promptText" to turn.promptText,
                    "createdAt" to canonicalInstant(turn.createdAt),
                    "responses" to turn.responses.map { response ->
                        buildMap {
                            put("configuredModelId", response.configuredModelId.toString())
                            put("modelId", response.modelId)
                            put("modelDisplayName", response.modelDisplayName)
                            put("protocol", response.protocol)
                            put("status", response.status)
                            put("responseText", response.responseText ?: "")
                            response.reasoningText?.let { put("reasoningText", it) }
                            response.errorMessage?.let { put("errorMessage", it) }
                        }
                    },
                )
            },
        )
        val canonical = canonicalize(payload)
        return MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private fun canonicalInstant(value: Instant): String = CANONICAL_INSTANT.format(value)

    private fun canonicalize(value: Any?): String = when (value) {
        null -> "null"
        is String -> objectMapper.writeValueAsString(value)
        is Number, is Boolean -> value.toString()
        is Map<*, *> -> value.entries.associate { it.key.toString() to it.value }.toSortedMap().entries.joinToString(",", "{", "}") {
            "${objectMapper.writeValueAsString(it.key)}:${canonicalize(it.value)}"
        }
        is Iterable<*> -> value.joinToString(",", "[", "]", transform = ::canonicalize)
        else -> objectMapper.writeValueAsString(value)
    }

    private fun badRequest(message: String) = ResponseStatusException(HttpStatus.BAD_REQUEST, message)

    private companion object {
        val CANONICAL_INSTANT: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSX").withZone(ZoneOffset.UTC)
    }
}

@Service
class AnonymousConversationSyncTransactionService(
    private val userRepository: UserRepository,
    private val importRepository: AnonymousConversationImportRepository,
    private val sessionRepository: ChatSessionRepository,
    private val turnRepository: ChatTurnRepository,
    private val responseRepository: ProviderResponseRepository,
) {
    @Transactional
    fun importConversation(
        userId: UUID,
        conversation: AnonymousSyncConversationInput,
        digest: String,
    ): ChatSession {
        val user: User = userRepository.findById(userId).orElseThrow { ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required") }
        val session = sessionRepository.save(
            ChatSession(
                user = user,
                title = conversation.title,
                createdAt = conversation.createdAt,
                updatedAt = conversation.updatedAt,
            ),
        )
        conversation.turns.forEachIndexed { index, sourceTurn ->
            val turn = turnRepository.save(
                ChatTurn(
                    session = session,
                    sequenceNum = index + 1,
                    promptText = sourceTurn.promptText,
                    attachments = null,
                    selectedModelIds = sourceTurn.responses.map { it.modelId }.toTypedArray(),
                    selectedConfiguredModelIds = sourceTurn.responses.map { it.configuredModelId }.toTypedArray(),
                    clientRequestId = sourceTurn.clientRequestId,
                    createdAt = sourceTurn.createdAt,
                ),
            )
            sourceTurn.responses.forEach { sourceResponse ->
                responseRepository.save(
                    ProviderResponse(
                        turn = turn,
                        configuredModelId = sourceResponse.configuredModelId,
                        modelId = sourceResponse.modelId,
                        modelDisplayName = sourceResponse.modelDisplayName,
                        protocol = sourceResponse.protocol,
                        status = sourceResponse.status.lowercase(),
                        responseText = sourceResponse.responseText,
                        reasoningText = sourceResponse.reasoningText,
                        errorMessage = sourceResponse.errorMessage,
                        latencyMs = 0,
                        createdAt = sourceTurn.createdAt,
                    ),
                )
            }
        }
        importRepository.save(
            AnonymousConversationImport(
                user = user,
                sourceConversationId = conversation.sourceConversationId,
                session = session,
                sourceDigest = digest,
                status = "IMPORTED",
                syncedAt = Instant.now(),
            ),
        )
        return session
    }
}
