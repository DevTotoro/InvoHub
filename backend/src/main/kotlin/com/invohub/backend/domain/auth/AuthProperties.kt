package com.invohub.backend.domain.auth

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.DefaultValue
import java.net.URI
import java.time.Duration

@ConfigurationProperties("invohub.auth")
data class AuthProperties(
    /** Public URL of the frontend. Magic links open its `/auth/verify` page. */
    val frontendUrl: URI,
    /** How long a magic link stays valid. No new link is sent to the same email meanwhile. */
    @DefaultValue("1h") val magicLinkTtl: Duration,
)
