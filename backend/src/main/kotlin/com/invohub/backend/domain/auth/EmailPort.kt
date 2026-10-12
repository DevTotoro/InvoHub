package com.invohub.backend.domain.auth

import com.invohub.backend.domain.company.CompanyRole
import com.invohub.backend.domain.user.Language
import java.net.URI
import java.time.Duration

/** One invitation, as the email about it describes it */
data class InvitationNotice(
    val companyName: String,
    val invitedByEmail: String,
    val role: CompanyRole,
)

interface EmailPort {
    /** @throws EmailDeliveryException if the email could not be handed off for delivery */
    fun sendMagicLink(
        email: String,
        link: URI,
        validFor: Duration,
        language: Language,
    )

    /**
     * Tells [email] it has been invited to companies. [link] signs them in on their Invitations page, where they
     * accept or decline, and works for [validFor].
     * @throws EmailDeliveryException if the email could not be handed off for delivery
     */
    fun sendInvitations(
        email: String,
        invitations: List<InvitationNotice>,
        link: URI,
        validFor: Duration,
        language: Language,
    )
}

class EmailDeliveryException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
