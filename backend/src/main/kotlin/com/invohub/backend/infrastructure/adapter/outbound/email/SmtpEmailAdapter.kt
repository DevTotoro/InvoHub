package com.invohub.backend.infrastructure.adapter.outbound.email

import com.invohub.backend.domain.auth.EmailDeliveryException
import com.invohub.backend.domain.auth.EmailPort
import com.invohub.backend.domain.auth.InvitationNotice
import com.invohub.backend.domain.user.Language
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.context.MessageSource
import org.springframework.mail.MailException
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.stereotype.Component
import java.net.URI
import java.time.Duration
import java.util.Locale

private val logger = KotlinLogging.logger {}

/** Sends every email as HTML with a button, plus the same in plain text (see EmailLayout) */
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
        val content =
            EmailContent(
                paragraphs = listOf(text("magic-link.intro", locale)),
                button = text("magic-link.button", locale),
                link = link,
                note = text("email.link-expiry", locale, humanize(validFor, locale)),
                fallback = text("email.fallback", locale),
            )
        send(email, text("magic-link.subject", locale), content, "magic link")
    }

    override fun sendInvitations(
        email: String,
        invitations: List<InvitationNotice>,
        link: URI,
        validFor: Duration,
        language: Language,
    ) {
        val locale = language.locale
        val items =
            invitations.map {
                text(
                    "invitations.line",
                    locale,
                    it.companyName,
                    text("role.${it.role.name}", locale),
                    it.invitedByEmail,
                )
            }
        val content =
            EmailContent(
                paragraphs = listOf(text("invitations.intro", locale)),
                items = items,
                button = text("invitations.button", locale),
                link = link,
                note =
                    text("invitations.outro", locale) + " " +
                        text("email.link-expiry", locale, humanize(validFor, locale)),
                fallback = text("email.fallback", locale),
                footer = text("invitations.ignore", locale),
            )
        send(email, text("invitations.subject", locale), content, "invitations")
    }

    private fun send(
        to: String,
        subject: String,
        content: EmailContent,
        what: String,
    ) {
        try {
            val message = mailSender.createMimeMessage()
            MimeMessageHelper(message, true, "UTF-8").apply {
                setTo(to)
                setFrom(properties.from)
                setSubject(subject)
                setText(EmailLayout.text(content), EmailLayout.html(content))
            }
            mailSender.send(message)
        } catch (e: MailException) {
            logger.error(e) { "Failed to send $what" }
            throw EmailDeliveryException("Failed to send $what email", e)
        }
    }

    /** Texts with arguments go through MessageFormat (where ' is written ''); texts without don't */
    private fun text(
        key: String,
        locale: Locale,
        vararg args: Any,
    ): String = messages.getMessage(key, if (args.isEmpty()) null else arrayOf(*args), locale)

    private fun humanize(
        duration: Duration,
        locale: Locale,
    ): String =
        if (duration.toMinutesPart() == 0 && duration.toHours() > 0) {
            text("duration.hours", locale, duration.toHours())
        } else {
            text("duration.minutes", locale, duration.toMinutes())
        }
}
