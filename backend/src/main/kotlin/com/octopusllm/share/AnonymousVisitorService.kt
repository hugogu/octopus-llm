package com.octopusllm.share

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseCookie
import org.springframework.stereotype.Service
import java.security.SecureRandom
import java.time.Duration
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

data class AnonymousVisitor(
    val rawKey: String,
    val digest: String,
    val cookie: ResponseCookie?,
)

@Service
class AnonymousVisitorService(
    @Value("\${app.anonymous-visitor.hmac-secret}") private val secret: String,
    @Value("\${spring.profiles.active:}") private val activeProfiles: String,
) {
    companion object {
        const val COOKIE_NAME = "octopus_anon_visitor"
    }

    private val random = SecureRandom()

    fun resolve(shareToken: String, cookieValue: String?): AnonymousVisitor {
        val raw = cookieValue?.takeIf { it.length in 32..128 } ?: randomKey()
        val cookie = if (cookieValue == null) {
            ResponseCookie.from(COOKIE_NAME, raw)
                .httpOnly(true)
                .sameSite("Lax")
                .secure(activeProfiles.split(",").any { it.trim() == "prod" })
                .path("/api/v2/shared")
                .maxAge(Duration.ofDays(365))
                .build()
        } else {
            null
        }
        return AnonymousVisitor(raw, digest(shareToken, raw), cookie)
    }

    private fun digest(shareToken: String, raw: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal("$shareToken:$raw".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private fun randomKey(): String {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
