package com.octopusllm.anonymous

import com.octopusllm.auth.AuthActionThrottleRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.time.Duration
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

data class AnonymousLease(
    private val repository: AnonymousRequestLeaseRepository,
    private val clientKeyHash: String,
    private val slotNo: Short,
    private val leaseId: UUID,
) {
    fun release(): Mono<Unit> = Mono.fromCallable {
        repository.release(clientKeyHash, slotNo, leaseId)
        Unit
    }.subscribeOn(Schedulers.boundedElastic())
}

@Service
class AnonymousThrottleService(
    private val throttleRepository: AuthActionThrottleRepository,
    private val leaseRepository: AnonymousRequestLeaseRepository,
    @Value("\${app.anonymous.rate-limit:20}") private val rateLimit: Int,
    @Value("\${app.anonymous.rate-window-seconds:60}") private val rateWindowSeconds: Long,
    @Value("\${app.anonymous.concurrency-limit:2}") private val concurrencyLimit: Int,
    @Value("\${app.anonymous.prompt-max-bytes:12000}") val promptMaxBytes: Int,
    @Value("\${app.anonymous.history-max-bytes:48000}") val historyMaxBytes: Int,
    @Value("\${app.anonymous.history-max-turns:20}") val historyMaxTurns: Int,
    @Value("\${app.anonymous.model-max-count:4}") val modelMaxCount: Int,
    @Value("\${app.anonymous.execution-timeout-seconds:120}") val executionTimeoutSeconds: Long,
    @Value("\${app.anonymous-visitor.hmac-secret}") private val hmacSecret: String,
) {
    companion object {
        private const val ACTION = "ANONYMOUS_CHAT"
    }

    fun acquire(
        clientIp: String?,
        prompt: String,
        historyBytes: Int,
        historyTurns: Int,
        modelCount: Int,
    ): Mono<AnonymousLease> = Mono.fromCallable {
        validate(prompt, historyBytes, historyTurns, modelCount)
        val now = Instant.now()
        val window = Duration.ofSeconds(rateWindowSeconds.coerceAtLeast(1))
        val windowStartedAt = Instant.ofEpochSecond((now.epochSecond / window.seconds) * window.seconds)
        val keyHash = hmac(clientIp?.takeIf { it.isNotBlank() } ?: "unknown")
        throttleRepository.increment(ACTION, keyHash, windowStartedAt, windowStartedAt.plus(window))
        val count = throttleRepository.requestCount(ACTION, keyHash, windowStartedAt) ?: 1
        if (count > rateLimit.coerceAtLeast(1)) {
            throw ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Anonymous request limit exceeded")
        }

        val leaseId = UUID.randomUUID()
        val expiresAt = now.plusSeconds(executionTimeoutSeconds.coerceAtLeast(1))
        val slot = (0 until concurrencyLimit.coerceAtLeast(1)).firstOrNull { slotNo ->
            leaseRepository.claim(keyHash, slotNo.toShort(), leaseId, expiresAt, now) == 1
        } ?: throw ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Anonymous stream limit exceeded")
        AnonymousLease(leaseRepository, keyHash, slot.toShort(), leaseId)
    }.subscribeOn(Schedulers.boundedElastic())

    private fun validate(prompt: String, historyBytes: Int, historyTurns: Int, modelCount: Int) {
        if (prompt.isBlank()) throw badRequest("Prompt is required")
        if (prompt.toByteArray(StandardCharsets.UTF_8).size > promptMaxBytes) {
            throw badRequest("Prompt exceeds the anonymous size limit")
        }
        if (historyTurns !in 0..historyMaxTurns || historyBytes > historyMaxBytes) {
            throw badRequest("Conversation history exceeds the anonymous size limit")
        }
        if (modelCount !in 1..modelMaxCount) throw badRequest("Too many models selected")
    }

    private fun badRequest(message: String) = ResponseStatusException(HttpStatus.BAD_REQUEST, message)

    private fun hmac(value: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(hmacSecret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(value.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
