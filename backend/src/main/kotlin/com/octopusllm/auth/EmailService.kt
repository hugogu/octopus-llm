package com.octopusllm.auth

import org.springframework.beans.factory.annotation.Value
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.stereotype.Service

@Service
class EmailService(
    private val mailSender: JavaMailSender,
    @Value("\${app.frontend.url}") private val frontendUrl: String,
) {

    fun sendVerificationEmail(toEmail: String, token: String) {
        val verifyUrl = "$frontendUrl/auth/verify-email?token=$token"
        val message = mailSender.createMimeMessage()
        MimeMessageHelper(message, false, "UTF-8").apply {
            setTo(toEmail)
            setSubject("Verify your Octopus LLM account")
            setText(
                """
                <p>Welcome to Octopus LLM!</p>
                <p>Click the link below to verify your email address:</p>
                <p><a href="$verifyUrl">$verifyUrl</a></p>
                <p>This link expires in 24 hours.</p>
                """.trimIndent(),
                true,
            )
        }
        mailSender.send(message)
    }
}
