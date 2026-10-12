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
    /** How long after that an old link can still ask for a fresh one, sent to the same address */
    @DefaultValue("7d") val magicLinkRenewableFor: Duration,
    /**
     * Sessions end this long after sign-in, however active they are. The inactivity timeout is separate:
     * server.servlet.session.timeout.
     */
    @DefaultValue("7d") val sessionMaxAge: Duration,
)
