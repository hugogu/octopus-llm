package com.octopusllm.share

import org.springframework.stereotype.Service
import java.security.SecureRandom
import java.util.Base64

@Service
class ShareTokenService {
    private val random = SecureRandom()

    fun create(): String {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
