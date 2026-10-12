package com.invohub.backend.infrastructure.adapter.outbound.email

import com.invohub.backend.domain.auth.EmailDeliveryException
import com.invohub.backend.domain.auth.EmailPort
import com.invohub.backend.domain.user.Language
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.context.MessageSource
import org.springframework.mail.MailException
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.stereotype.Component
import java.net.URI
import java.time.Duration
import java.util.Locale

private val logger = KotlinLogging.logger {}

@Component
class SmtpEmailAdapter(
    private val mailSender: JavaMailSender,
    private val properties: MailProperties,
    private val messages: MessageSource,
) : EmailPort {
    override fun sendMagicLink(
        email: String,
        link: URI,
        validFor: Duration,
        language: Language,
    ) {
        val locale = language.locale
        val message =
            SimpleMailMessage().apply {
                setTo(email)
                subject = messages.getMessage("magic-link.subject", null, locale)
                text =
                    messages.getMessage("magic-link.body", arrayOf(link.toString(), humanize(validFor, locale)), locale)
                from = properties.from
            }

        try {
            mailSender.send(message)
        } catch (e: MailException) {
            logger.error(e) { "Failed to send magic link" }
            throw EmailDeliveryException("Failed to send magic link email", e)
        }
    }

    private fun humanize(
        duration: Duration,
        locale: Locale,
    ): String =
        if (duration.toMinutesPart() == 0 && duration.toHours() > 0) {
            messages.getMessage("duration.hours", arrayOf(duration.toHours()), locale)
        } else {
            messages.getMessage("duration.minutes", arrayOf(duration.toMinutes()), locale)
        }
}
