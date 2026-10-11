package com.invohub.backend.infrastructure.adapter.outbound.email

import com.invohub.backend.domain.auth.EmailDeliveryException
import com.invohub.backend.domain.auth.EmailPort
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.mail.MailException
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

@Component
class SmtpEmailAdapter(
    private val mailSender: JavaMailSender,
) : EmailPort {
    override fun sendMagicLink(
        email: String,
        rawToken: String,
    ) {
        // TODO: make url var
        val magicLinkUrl = "http://localhost:5173/auth/verify?token=$rawToken"

        val message =
            SimpleMailMessage().apply {
                setTo(email)
                subject = "InvoHub - Sign In Link"
                text =
                    "Click the link below to sign in to Invohub:" +
                    "\n\n$magicLinkUrl\n\nThis link will expire in 1 hour."
                from = "no-reply@invohub.com"
            }

        try {
            mailSender.send(message)
        } catch (e: MailException) {
            logger.error(e) { "Failed to send magic link" }
            throw EmailDeliveryException("Failed to send magic link email", e)
        }
    }
}
