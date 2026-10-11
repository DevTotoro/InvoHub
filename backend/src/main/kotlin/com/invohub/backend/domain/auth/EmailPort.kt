package com.invohub.backend.domain.auth

interface EmailPort {
    fun sendMagicLink(
        email: String,
        rawToken: String,
    )
}
