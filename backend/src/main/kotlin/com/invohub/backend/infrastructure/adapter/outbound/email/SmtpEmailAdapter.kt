package com.invohub.backend.infrastructure.adapter.outbound.email

import com.invohub.backend.domain.auth.EmailDeliveryException
import com.invohub.backend.domain.auth.EmailPort
import com.invohub.backend.domain.user.Language
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.mail.MailException
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.stereotype.Component
import java.net.URI
import java.time.Duration

private val logger = KotlinLogging.logger {}

@Component
class SmtpEmailAdapter(
    private val mailSender: JavaMailSender,
    private val properties: MailProperties,
) : EmailPort {
    override fun sendMagicLink(
        email: String,
        link: URI,
        validFor: Duration,
        language: Language,
    ) {
        val message =
            SimpleMailMessage().apply {
                setTo(email)
                subject = "InvoHub - Sign In Link"
                text =
                    "Click the link below to sign in to Invohub:" +
                    "\n\n$link\n\nThis link will expire in ${humanize(validFor)}."
                from = properties.from
            }

        try {
            mailSender.send(message)
        } catch (e: MailException) {
            logger.error(e) { "Failed to send magic link" }
            throw EmailDeliveryException("Failed to send magic link email", e)
        }
    }

    private fun humanize(duration: Duration): String {
        val (amount, unit) =
            if (duration.toMinutesPart() == 0 && duration.toHours() > 0) {
                duration.toHours() to "hour"
            } else {
                duration.toMinutes() to "minute"
            }
        return if (amount == 1L) "1 $unit" else "$amount ${unit}s"
    }
}
