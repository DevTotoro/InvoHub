package com.invohub.backend.domain.auth

import java.net.URI
import java.time.Duration

interface EmailPort {
    /** @throws EmailDeliveryException if the email could not be handed off for delivery */
    fun sendMagicLink(
        email: String,
        link: URI,
        validFor: Duration,
    )
}

class EmailDeliveryException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
