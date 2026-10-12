package com.invohub.backend.infrastructure.adapter.outbound.email

import com.invohub.backend.domain.auth.EmailDeliveryException
import com.invohub.backend.domain.auth.EmailPort
import com.invohub.backend.domain.auth.InvitationNotice
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

        send(message, "magic link")
    }

    private fun send(
        message: SimpleMailMessage,
        what: String,
    ) {
        try {
            mailSender.send(message)
        } catch (e: MailException) {
            logger.error(e) { "Failed to send $what" }
            throw EmailDeliveryException("Failed to send $what email", e)
        }
    }

    override fun sendInvitations(
        email: String,
        invitations: List<InvitationNotice>,
        link: URI,
        language: Language,
    ) {
        val locale = language.locale
        // Plain text: the company name and the inviter's email are other people's words, never markup
        val lines =
            invitations.joinToString("\n") {
                val role = messages.getMessage("role.${it.role.name}", null, locale)
                messages.getMessage("invitations.line", arrayOf(it.companyName, role, it.invitedByEmail), locale)
            }
        val message =
            SimpleMailMessage().apply {
                setTo(email)
                subject = messages.getMessage("invitations.subject", null, locale)
                text =
                    messages.getMessage("invitations.intro", null, locale) + "\n\n" + lines + "\n\n" +
                    messages.getMessage("invitations.outro", arrayOf(link.toString()), locale)
                from = properties.from
            }
        send(message, "invitations")
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
