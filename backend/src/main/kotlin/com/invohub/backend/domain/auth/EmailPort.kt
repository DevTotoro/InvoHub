package com.invohub.backend.domain.auth

import com.invohub.backend.domain.user.Language
import java.net.URI
import java.time.Duration

interface EmailPort {
    /** @throws EmailDeliveryException if the email could not be handed off for delivery */
    fun sendMagicLink(
        email: String,
        link: URI,
        validFor: Duration,
        language: Language,
    )
}

class EmailDeliveryException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
