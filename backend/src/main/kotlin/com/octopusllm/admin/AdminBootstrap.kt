package com.octopusllm.admin

import com.octopusllm.auth.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * Promotes the configured bootstrap account to administrator on startup, so an initial admin can exist
 * without a pre-existing admin (FR-002). Idempotent and a no-op when unset or already satisfied; the
 * account must have registered normally first (to have a password hash).
 */
@Component
class AdminBootstrap(
    private val userRepository: UserRepository,
    @Value("\${app.admin.bootstrap-email:}") private val bootstrapEmail: String,
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(AdminBootstrap::class.java)

    override fun run(args: ApplicationArguments) {
        val email = bootstrapEmail.trim().lowercase()
        if (email.isEmpty()) return

        val user = userRepository.findByEmail(email)
        if (user == null) {
            log.warn("Admin bootstrap email {} is not a registered account yet; skipping promotion", email)
            return
        }
        if (user.isAdmin && user.isActive) return

        user.isAdmin = true
        user.isActive = true
        user.updatedAt = Instant.now()
        userRepository.save(user)
        log.info("Promoted {} to administrator via bootstrap configuration", email)
    }
}
