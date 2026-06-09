package com.octopusllm.auth

import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.time.Instant
import java.util.Date
import java.util.UUID
import javax.crypto.SecretKey

data class JwtClaims(
    val userId: UUID,
    val jti: String,
    val exp: Instant,
)

@Service
class JwtTokenService(
    private val revokedTokenRepository: RevokedTokenRepository,
    @Value("\${app.jwt.secret}") secret: String,
    @Value("\${app.jwt.expiry-seconds}") private val expirySeconds: Long,
) {
    private val signingKey: SecretKey = Keys.hmacShaKeyFor(secret.toByteArray(Charsets.UTF_8))

    fun issue(userId: UUID): String {
        val jti = UUID.randomUUID().toString()
        val now = Instant.now()
        val exp = now.plusSeconds(expirySeconds)
        return Jwts.builder()
            .id(jti)
            .subject(userId.toString())
            .issuedAt(Date.from(now))
            .expiration(Date.from(exp))
            .signWith(signingKey)
            .compact()
    }

    fun validate(token: String): Mono<JwtClaims> = Mono.fromCallable {
        val claims = Jwts.parser()
            .verifyWith(signingKey)
            .build()
            .parseSignedClaims(token)
            .payload
        JwtClaims(
            userId = UUID.fromString(claims.subject),
            jti = claims.id,
            exp = claims.expiration.toInstant(),
        )
    }.subscribeOn(Schedulers.boundedElastic()).flatMap { claims ->
        Mono.fromCallable {
            revokedTokenRepository.existsByJti(claims.jti)
        }.subscribeOn(Schedulers.boundedElastic())
            .flatMap { revoked ->
                if (revoked) Mono.error(JwtException("Token has been revoked"))
                else Mono.just(claims)
            }
    }
}
