package com.invohub.backend.domain.magiclink

interface EmailPort {
    fun sendMagicLink(
        email: String,
        rawToken: String,
    )
}
