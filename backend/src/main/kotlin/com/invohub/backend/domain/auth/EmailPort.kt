package com.invohub.backend.domain.auth

interface EmailPort {
    /** @throws EmailDeliveryException if the email could not be handed off for delivery */
    fun sendMagicLink(
        email: String,
        rawToken: String,
    )
}

class EmailDeliveryException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
